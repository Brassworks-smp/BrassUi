package net.swzo.brass.ui.kit.node

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class PortRef(val nodeId: Int, val port: Int)

/**
 * Values presented to one node invocation. Multiple values are retained for multi-connect inputs;
 * [first] is the convenient single-input path.
 */
class NodeInputs internal constructor(private val values: Map<Int, List<Any?>>) {
    fun all(port: Int): List<Any?> = values[port].orEmpty()
    fun first(port: Int): Any? = values[port]?.firstOrNull()
    fun contains(port: Int): Boolean = values[port]?.isNotEmpty() == true
}

data class NodeResult(
    val outputs: Map<Int, Any?> = emptyMap(),
    /** FLOW output indices fired by this invocation. Data outputs do not need to be listed. */
    val eventOutputs: Set<Int> = emptySet(),
)

class NodeExecutionContext internal constructor(
    val node: GraphNode,
    val inputs: NodeInputs,
    private val cancelled: () -> Boolean,
) {
    fun field(key: String): Any? = node.field(key)?.encode()
    fun isCancelled(): Boolean = cancelled()
}

fun interface NodeExecutor {
    fun execute(context: NodeExecutionContext): CompletionStage<NodeResult>
}

enum class ExecutionState { IDLE, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }
enum class NodeRunState { WAITING, RUNNING, COMPLETED, SKIPPED, FAILED }

data class NodeTrace(
    val nodeId: Int,
    val state: NodeRunState,
    val elapsedNanos: Long = 0L,
    val error: Throwable? = null,
)

data class ExecutionReport(
    val state: ExecutionState,
    val order: List<Int>,
    val results: Map<Int, NodeResult>,
    val traces: List<NodeTrace>,
    val watched: Map<PortRef, Any?>,
    val error: Throwable? = null,
)

data class ExecutionUpdate(
    val state: ExecutionState,
    val nodeId: Int? = null,
    val nodeState: NodeRunState? = null,
    val watched: Map<PortRef, Any?> = emptyMap(),
    val error: Throwable? = null,
)

/**
 * Deterministic graph scheduler with data flow, event-flow gating, asynchronous node support and a
 * small debugger. Nodes are topologically ordered by every live link; executor stages may complete on
 * any thread, while graph order and debugger notifications remain serialized on the scheduler worker.
 *
 * Breakpoints pause before a node. [step] executes exactly one node and pauses before the next;
 * [continueExecution] resumes freely. A FLOW input only runs after an upstream executor emits its port
 * index in [NodeResult.eventOutputs].
 */
class GraphScheduler(
    private val graph: NodeGraph,
    private val worker: Executor = ForkJoinPool.commonPool(),
) {
    val breakpoints = LinkedHashSet<Int>()
    val watches = LinkedHashSet<PortRef>()
    val nodeStates: Map<Int, NodeRunState> get() = liveNodeStates.toMap()
    val watchedValues: Map<PortRef, Any?> get() = liveWatched.mapValues { (_, value) ->
        value.takeUnless { it === NullValue }
    }

    @Volatile var state: ExecutionState = ExecutionState.IDLE
        private set
    @Volatile var pausedAt: Int? = null
        private set
    @Volatile var lastExecutedNodeId: Int? = null
        private set
    @Volatile var lastReport: ExecutionReport? = null
        private set

    private val gate = Object()
    private var cancelled = false
    private var stepBudget = Int.MAX_VALUE
    private var skipBreakpointOnce: Int? = null
    private val liveNodeStates = ConcurrentHashMap<Int, NodeRunState>()
    private val liveWatched = ConcurrentHashMap<PortRef, Any>()
    private val listeners = CopyOnWriteArrayList<(ExecutionUpdate) -> Unit>()

    fun onUpdate(listener: (ExecutionUpdate) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    fun run(
        initial: Map<PortRef, Any?> = emptyMap(),
        paused: Boolean = false,
    ): CompletableFuture<ExecutionReport> =
        start(initial, if (paused) 0 else Int.MAX_VALUE)

    /** Start a fresh run, execute its first available node, then pause before the next one. */
    fun runStep(initial: Map<PortRef, Any?> = emptyMap()): CompletableFuture<ExecutionReport> =
        start(initial, 1)

    private fun start(
        initial: Map<PortRef, Any?>,
        initialStepBudget: Int,
    ): CompletableFuture<ExecutionReport> {
        synchronized(gate) {
            check(state != ExecutionState.RUNNING && state != ExecutionState.PAUSED) {
                "scheduler is already running"
            }
            cancelled = false
            stepBudget = initialStepBudget
            skipBreakpointOnce = null
            pausedAt = null
            lastExecutedNodeId = null
            state = ExecutionState.RUNNING
            liveNodeStates.clear()
            graph.nodes.filterNot { it.closing }.forEach { liveNodeStates[it.id] = NodeRunState.WAITING }
            liveWatched.clear()
        }
        emit()
        return CompletableFuture.supplyAsync({ execute(initial) }, worker).whenComplete { report, _ ->
            if (report != null) lastReport = report
        }
    }

    fun continueExecution() {
        synchronized(gate) {
            if (state != ExecutionState.PAUSED) return
            skipBreakpointOnce = pausedAt
            stepBudget = Int.MAX_VALUE
            state = ExecutionState.RUNNING
            gate.notifyAll()
        }
        emit()
    }

    fun step() {
        synchronized(gate) {
            if (state != ExecutionState.PAUSED) return
            skipBreakpointOnce = pausedAt
            stepBudget = 1
            state = ExecutionState.RUNNING
            gate.notifyAll()
        }
        emit()
    }

    fun cancel() {
        synchronized(gate) {
            cancelled = true
            state = ExecutionState.CANCELLED
            gate.notifyAll()
        }
        emit()
    }

    private fun execute(initial: Map<PortRef, Any?>): ExecutionReport {
        val traces = ArrayList<NodeTrace>()
        val results = LinkedHashMap<Int, NodeResult>()
        val watched = LinkedHashMap<PortRef, Any?>()
        val liveLinks = graph.links.filterNot { it.closing }
        val order = topologicalOrder(liveLinks)
        if (order == null) {
            val error = IllegalStateException("graph contains a cycle")
            state = ExecutionState.FAILED
            emit(error = error)
            return ExecutionReport(state, emptyList(), results, traces, watched, error)
        }

        val incomingData = liveLinks.filter { it.portType() != PortType.FLOW }.groupBy { it.to.id }
        val incomingFlow = liveLinks.filter { it.portType() == PortType.FLOW }.groupBy { it.to.id }
        val flowTargets = liveLinks.filter { it.portType() == PortType.FLOW }
            .groupBy { it.from.id to it.fromPort }
        val activated = graph.nodes.filter { node ->
            incomingFlow[node.id].isNullOrEmpty()
        }.mapTo(HashSet()) { it.id }

        try {
            for (node in order) {
                if (cancelled) break
                if (!incomingFlow[node.id].isNullOrEmpty() && node.id !in activated) {
                    traces += NodeTrace(node.id, NodeRunState.SKIPPED)
                    setNodeState(node.id, NodeRunState.SKIPPED)
                    continue
                }
                awaitDebugger(node.id)
                if (cancelled) break

                val inputValues = LinkedHashMap<Int, MutableList<Any?>>()
                for ((ref, value) in initial) {
                    if (ref.nodeId == node.id) inputValues.getOrPut(ref.port) { ArrayList() }.add(value)
                }
                for (link in incomingData[node.id].orEmpty()) {
                    if (results.containsKey(link.from.id)) {
                        val value = results[link.from.id]?.outputs?.get(link.fromPort)
                        inputValues.getOrPut(link.toPort) { ArrayList() }.add(value)
                    }
                }

                val missing = node.type.inputs.withIndex().firstOrNull { (index, port) ->
                    !port.optional && port.type != PortType.FLOW && inputValues[index].isNullOrEmpty()
                }
                if (missing != null) {
                    val error = IllegalStateException(
                        "${node.type.title}.${missing.value.name} is required but has no value",
                    )
                    traces += NodeTrace(node.id, NodeRunState.FAILED, error = error)
                    setNodeState(node.id, NodeRunState.FAILED, error)
                    throw error
                }

                val started = System.nanoTime()
                setNodeState(node.id, NodeRunState.RUNNING)
                val result = try {
                    val executor = node.type.executor
                    if (executor == null) {
                        NodeResult()
                    } else {
                        executor.execute(
                            NodeExecutionContext(node, NodeInputs(inputValues), { cancelled }),
                        ).toCompletableFuture().join()
                    }
                } catch (error: Throwable) {
                    val cause = unwrap(error)
                    traces += NodeTrace(node.id, NodeRunState.FAILED, System.nanoTime() - started, cause)
                    setNodeState(node.id, NodeRunState.FAILED, cause)
                    throw cause
                }
                results[node.id] = result
                traces += NodeTrace(node.id, NodeRunState.COMPLETED, System.nanoTime() - started)
                lastExecutedNodeId = node.id
                setNodeState(node.id, NodeRunState.COMPLETED)

                for (port in result.eventOutputs) {
                    flowTargets[node.id to port].orEmpty().forEach { activated += it.to.id }
                }
                for (watch in watches.filter { it.nodeId == node.id }) {
                    watched[watch] = result.outputs[watch.port]
                    liveWatched[watch] = result.outputs[watch.port] ?: NullValue
                }
                if (watches.any { it.nodeId == node.id }) emit(node.id, NodeRunState.COMPLETED)
                consumeStep()
            }
        } catch (error: Throwable) {
            if (!cancelled) {
                state = ExecutionState.FAILED
                val cause = unwrap(error)
                emit(error = cause)
                return ExecutionReport(state, order.map { it.id }, results, traces, watched, cause)
            }
        }

        state = if (cancelled) ExecutionState.CANCELLED else ExecutionState.COMPLETED
        pausedAt = null
        emit()
        return ExecutionReport(state, order.map { it.id }, results, traces, watched)
    }

    private fun awaitDebugger(nodeId: Int) {
        synchronized(gate) {
            val shouldBreak = nodeId in breakpoints && skipBreakpointOnce != nodeId
            if (shouldBreak || stepBudget == 0) {
                state = ExecutionState.PAUSED
                pausedAt = nodeId
                emit(nodeId, NodeRunState.WAITING)
                while (state == ExecutionState.PAUSED && !cancelled) gate.wait()
            }
            if (skipBreakpointOnce == nodeId) skipBreakpointOnce = null
        }
    }

    private fun consumeStep() {
        synchronized(gate) {
            if (stepBudget != Int.MAX_VALUE && stepBudget > 0) stepBudget--
        }
    }

    private fun topologicalOrder(live: List<Link>): List<GraphNode>? {
        val nodes = graph.nodes.filterNot { it.closing }
        val degree = nodes.associate { it.id to 0 }.toMutableMap()
        val outgoing = live.groupBy { it.from.id }
        for (link in live) degree[link.to.id] = (degree[link.to.id] ?: 0) + 1
        val ready = java.util.PriorityQueue<GraphNode>(compareBy { it.id })
        nodes.filter { degree[it.id] == 0 }.forEach(ready::add)
        val order = ArrayList<GraphNode>(nodes.size)
        while (ready.isNotEmpty()) {
            val node = ready.remove()
            order += node
            for (link in outgoing[node.id].orEmpty()) {
                val next = (degree[link.to.id] ?: 0) - 1
                degree[link.to.id] = next
                if (next == 0) ready += link.to
            }
        }
        return order.takeIf { it.size == nodes.size }
    }

    private fun unwrap(error: Throwable): Throwable =
        if (error is java.util.concurrent.CompletionException && error.cause != null) error.cause!! else error

    private fun setNodeState(nodeId: Int, nodeState: NodeRunState, error: Throwable? = null) {
        liveNodeStates[nodeId] = nodeState
        emit(nodeId, nodeState, error)
    }

    private fun emit(
        nodeId: Int? = null,
        nodeState: NodeRunState? = null,
        error: Throwable? = null,
    ) {
        val update = ExecutionUpdate(state, nodeId, nodeState, watchedValues, error)
        listeners.forEach { it(update) }
    }

    private object NullValue
}
