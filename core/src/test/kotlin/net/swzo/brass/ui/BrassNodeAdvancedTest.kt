package net.swzo.brass.ui

import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.node.ExecutionState
import net.swzo.brass.ui.kit.node.FrameTone
import net.swzo.brass.ui.kit.node.GraphScheduler
import net.swzo.brass.ui.kit.node.InMemoryNodeCollaborationHub
import net.swzo.brass.ui.kit.node.LinkRejection
import net.swzo.brass.ui.kit.node.NodeCollaborationSession
import net.swzo.brass.ui.kit.node.NodeCollaborativeDocument
import net.swzo.brass.ui.kit.node.NodeDiagnosticSeverity
import net.swzo.brass.ui.kit.node.NodeExecutor
import net.swzo.brass.ui.kit.node.NodeGraph
import net.swzo.brass.ui.kit.node.NodeGraphDiagnostics
import net.swzo.brass.ui.kit.node.NodeGraphExport
import net.swzo.brass.ui.kit.node.NodeGraphNavigator
import net.swzo.brass.ui.kit.node.NodeIO
import net.swzo.brass.ui.kit.node.NodeRegistry
import net.swzo.brass.ui.kit.node.NodeResult
import net.swzo.brass.ui.kit.node.NodeRunState
import net.swzo.brass.ui.kit.node.NodeSelectionController
import net.swzo.brass.ui.kit.node.NodeWorkflowService
import net.swzo.brass.ui.kit.node.NodeType
import net.swzo.brass.ui.kit.node.Port
import net.swzo.brass.ui.kit.node.PortRef
import net.swzo.brass.ui.kit.node.PortShape
import net.swzo.brass.ui.kit.node.PortType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertTimeout

class BrassNodeAdvancedTest {

    @Test
    fun `port capacities compatibility and hidden metadata are enforced`() {
        val anyNumber = PortType("any-number", accepts = { it == PortType.NUMBER }) { Color.WHITE }
        val registry = NodeRegistry()
            .register(NodeType("source", "Source", BrassAccent.DEFAULT,
                outputs = listOf(Port("out", PortType.NUMBER, maxConnections = 1))))
            .register(NodeType("sink", "Sink", BrassAccent.DEFAULT,
                inputs = listOf(Port("in", anyNumber, hidden = true, dynamic = true, shape = PortShape.SQUARE))))
        val graph = NodeGraph(registry)
        val source = graph.spawn("source", 0f, 0f)!!
        val first = graph.spawn("sink", 100f, 0f)!!
        val second = graph.spawn("sink", 100f, 80f)!!

        assertNotNull(graph.link(source, 0, first, 0))
        val rejected = graph.validateLink(source, 0, second, 0)
        assertFalse(rejected.allowed)
        assertEquals(LinkRejection.OUTPUT_FULL, rejected.rejection)
        assertNull(graph.link(source, 0, second, 0))
        assertTrue(first.type.inputs[0].hidden)
        assertTrue(first.type.inputs[0].dynamic)
    }

    @Test
    fun `reroutes groups comments and bookmarks round trip`() {
        val registry = NodeRegistry()
            .register(NodeType("a", "A", BrassAccent.DEFAULT,
                outputs = listOf(Port("out", PortType.NUMBER))))
            .register(NodeType("b", "B", BrassAccent.DEFAULT,
                inputs = listOf(Port("in", PortType.NUMBER, optional = true))))
        val graph = NodeGraph(registry)
        val a = graph.spawn("a", 10f, 20f)!!
        val b = graph.spawn("b", 200f, 60f)!!
        graph.reroute(graph.link(a, 0, b, 0)!!, 120f, 42f)
        graph.frame("Math", listOf(a.id, b.id), FrameTone.PATINA).also {
            it.customColor = Color(0x80, 0x45, 0xB5).rgb
        }
        graph.comment("Keep this readable", 50f, 8f).also {
            it.tone = FrameTone.DANGER
            it.height = 62f
            it.customColor = Color(0x31, 0x82, 0xce).rgb
        }
        graph.bookmark("overview", 12f, 18f, 0.8f)

        val restored = NodeGraph.fromJson(registry, graph.toJson())
        assertEquals(1, restored.links.single().reroutes.size)
        assertEquals("Math", restored.frames.single().title)
        assertEquals(FrameTone.PATINA, restored.frames.single().tone)
        assertEquals(Color(0x80, 0x45, 0xB5).rgb, restored.frames.single().customColor)
        assertEquals("Keep this readable", restored.comments.single().text)
        assertEquals(FrameTone.DANGER, restored.comments.single().tone)
        assertEquals(62f, restored.comments.single().height)
        assertEquals(Color(0x31, 0x82, 0xce).rgb, restored.comments.single().customColor)
        assertEquals(0.8f, restored.bookmarks.single().zoom)
    }

    @Test
    fun `scheduler carries async data and captures watched values`() {
        val registry = NodeRegistry()
            .register(NodeType("source", "Source", BrassAccent.DEFAULT,
                outputs = listOf(Port("value", PortType.NUMBER)),
                executor = NodeExecutor {
                    CompletableFuture.completedFuture(NodeResult(outputs = mapOf(0 to 21)))
                }))
            .register(NodeType("double", "Double", BrassAccent.DEFAULT,
                inputs = listOf(Port("value", PortType.NUMBER)),
                outputs = listOf(Port("value", PortType.NUMBER)),
                executor = NodeExecutor { ctx ->
                    CompletableFuture.supplyAsync {
                        NodeResult(outputs = mapOf(0 to (ctx.inputs.first(0) as Int) * 2))
                    }
                }))
        val graph = NodeGraph(registry)
        val source = graph.spawn("source", 0f, 0f)!!
        val double = graph.spawn("double", 100f, 0f)!!
        graph.link(source, 0, double, 0)
        val scheduler = GraphScheduler(graph)
        scheduler.watches += PortRef(double.id, 0)

        val report = scheduler.run().get(2, TimeUnit.SECONDS)
        assertEquals(ExecutionState.COMPLETED, report.state)
        assertEquals(42, report.watched[PortRef(double.id, 0)])
        assertEquals(listOf(source.id, double.id), report.order)
    }

    @Test
    fun `flow nodes wait for events and debugger can step`() {
        val registry = NodeRegistry()
            .register(NodeType("start", "Start", BrassAccent.DEFAULT,
                outputs = listOf(Port("then", PortType.FLOW)),
                executor = NodeExecutor {
                    CompletableFuture.completedFuture(NodeResult(eventOutputs = setOf(0)))
                }))
            .register(NodeType("middle", "Middle", BrassAccent.DEFAULT,
                inputs = listOf(Port("in", PortType.FLOW)),
                outputs = listOf(Port("then", PortType.FLOW)),
                executor = NodeExecutor {
                    CompletableFuture.completedFuture(NodeResult(eventOutputs = setOf(0)))
                }))
            .register(NodeType("end", "End", BrassAccent.DEFAULT,
                inputs = listOf(Port("in", PortType.FLOW)),
                executor = NodeExecutor { CompletableFuture.completedFuture(NodeResult()) }))
        val graph = NodeGraph(registry)
        val start = graph.spawn("start", 0f, 0f)!!
        val middle = graph.spawn("middle", 100f, 0f)!!
        val end = graph.spawn("end", 200f, 0f)!!
        graph.link(start, 0, middle, 0)
        graph.link(middle, 0, end, 0)

        val scheduler = GraphScheduler(graph)
        scheduler.breakpoints += middle.id
        val future = scheduler.run()
        waitFor { scheduler.state == ExecutionState.PAUSED }
        assertEquals(middle.id, scheduler.pausedAt)
        scheduler.step()
        waitFor { scheduler.state == ExecutionState.PAUSED && scheduler.pausedAt == end.id }
        scheduler.continueExecution()
        assertEquals(ExecutionState.COMPLETED, future.get(2, TimeUnit.SECONDS).state)
    }

    @Test
    fun `step from idle executes one node before pausing`() {
        val executed = mutableListOf<String>()
        val registry = NodeRegistry()
            .register(NodeType("first", "First", BrassAccent.DEFAULT,
                outputs = listOf(Port("value", PortType.NUMBER)),
                executor = NodeExecutor {
                    executed += "first"
                    CompletableFuture.completedFuture(NodeResult(mapOf(0 to 1)))
                }))
            .register(NodeType("second", "Second", BrassAccent.DEFAULT,
                inputs = listOf(Port("value", PortType.NUMBER)),
                executor = NodeExecutor {
                    executed += "second"
                    CompletableFuture.completedFuture(NodeResult())
                }))
        val graph = NodeGraph(registry)
        val first = graph.spawn("first", 0f, 0f)!!
        val second = graph.spawn("second", 100f, 0f)!!
        graph.link(first, 0, second, 0)

        val scheduler = GraphScheduler(graph)
        val future = scheduler.runStep()
        waitFor { scheduler.state == ExecutionState.PAUSED }

        assertEquals(listOf("first"), executed)
        assertEquals(first.id, scheduler.lastExecutedNodeId)
        assertEquals(second.id, scheduler.pausedAt)
        scheduler.continueExecution()
        assertEquals(ExecutionState.COMPLETED, future.get(2, TimeUnit.SECONDS).state)
        assertEquals(listOf("first", "second"), executed)
    }

    @Test
    fun `scheduler publishes live state watched nulls and failure traces`() {
        val registry = NodeRegistry()
            .register(NodeType("null-source", "Null source", BrassAccent.DEFAULT,
                outputs = listOf(Port("value", PortType.NUMBER)),
                executor = NodeExecutor {
                    CompletableFuture.completedFuture(NodeResult(outputs = mapOf(0 to null)))
                }))
            .register(NodeType("failure", "Failure", BrassAccent.DEFAULT,
                executor = NodeExecutor {
                    CompletableFuture.failedFuture(IllegalArgumentException("intentional"))
                }))
        val graph = NodeGraph(registry)
        val source = graph.spawn("null-source", 0f, 0f)!!
        val failure = graph.spawn("failure", 100f, 0f)!!
        val scheduler = GraphScheduler(graph)
        val updates = mutableListOf<NodeRunState>()
        scheduler.watches += PortRef(source.id, 0)
        scheduler.onUpdate { it.nodeState?.let(updates::add) }

        val report = scheduler.run().get(2, TimeUnit.SECONDS)

        assertEquals(ExecutionState.FAILED, report.state)
        assertTrue(scheduler.watchedValues.containsKey(PortRef(source.id, 0)))
        assertNull(scheduler.watchedValues[PortRef(source.id, 0)])
        assertEquals(NodeRunState.FAILED, scheduler.nodeStates[failure.id])
        assertTrue(report.traces.any { it.nodeId == failure.id && it.state == NodeRunState.FAILED })
        assertTrue(NodeRunState.RUNNING in updates)
        assertTrue(NodeRunState.FAILED in updates)
    }

    @Test
    fun `svg export includes routed graph organization and escaped labels`() {
        val registry = NodeRegistry()
            .register(NodeType("a", "A & B", BrassAccent.DEFAULT,
                outputs = listOf(Port("out", PortType.NUMBER))))
            .register(NodeType("b", "Sink", BrassAccent.DEFAULT,
                inputs = listOf(Port("in", PortType.NUMBER, optional = true))))
        val graph = NodeGraph(registry)
        val a = graph.spawn("a", 10f, 20f)!!
        val b = graph.spawn("b", 180f, 20f)!!
        graph.reroute(graph.link(a, 0, b, 0)!!, 120f, 55f)
        graph.frame("Export", listOf(a.id, b.id))
        graph.comment("Review <this>", 20f, 4f)

        val svg = NodeGraphExport.toSvg(graph)

        assertTrue(svg.startsWith("<svg"))
        assertTrue(svg.contains("<polyline"))
        assertTrue(svg.contains("120.0,55.0"))
        assertTrue(svg.contains("A &amp; B"))
        assertTrue(svg.contains("Review &lt;this&gt;"))
    }

    @Test
    fun `selection diagnostics and navigator are reusable without editor input`() {
        val registry = NodeRegistry()
            .register(NodeType("pass", "Pass", BrassAccent.DEFAULT,
                inputs = listOf(Port("in", PortType.NUMBER)),
                outputs = listOf(Port("out", PortType.NUMBER))))
        val graph = NodeGraph(registry)
        val first = graph.spawn("pass", 10f, 20f)!!
        val second = graph.spawn("pass", 180f, 20f)!!
        graph.link(first, 0, second, 0)
        graph.link(second, 0, first, 0)
        val selection = NodeSelectionController(graph)
        selection.select(first)

        val diagnostics = NodeGraphDiagnostics.inspect(graph)
        val navigator = NodeGraphNavigator.snapshot(graph)

        assertTrue(first.selected)
        assertTrue(diagnostics.any {
            it.code == "cycle" && it.nodeId == first.id && it.severity == NodeDiagnosticSeverity.ERROR
        })
        assertEquals(2, navigator.nodes.size)
        assertTrue(navigator.maxX > navigator.minX)
    }

    @Test
    fun `format compatibility distinguishes legacy current future and invalid`() {
        val graph = NodeGraph(NodeRegistry())
        val current = graph.toJson()

        assertEquals(NodeIO.Compatibility.CURRENT, NodeIO.compatibility(current))
        assertEquals(
            NodeIO.Compatibility.LEGACY,
            NodeIO.compatibility(current.replace("\"version\": 5", "\"version\": 1")),
        )
        assertEquals(
            NodeIO.Compatibility.FUTURE,
            NodeIO.compatibility(current.replace("\"version\": 5", "\"version\": 99")),
        )
        assertEquals(NodeIO.Compatibility.INVALID, NodeIO.compatibility("{}"))
        assertEquals(NodeIO.Compatibility.INVALID, NodeIO.compatibility("""{"version":"x","nodes":[]}"""))
    }

    @Test
    fun `invalid load preserves the graph already being edited`() {
        val registry = NodeRegistry().register(NodeType("node", "Node", BrassAccent.DEFAULT))
        val graph = NodeGraph(registry)
        graph.spawn("node", 4f, 8f)

        assertFalse(graph.load("""{"notNodes":true}"""))
        assertEquals(1, graph.nodes.size)
        assertEquals(4f, graph.nodes.single().x)
    }

    @Test
    fun `workflow service owns favorites recents and reusable selected subgraphs`() {
        val registry = NodeRegistry().register(NodeType("node", "Node", BrassAccent.DEFAULT))
        val graph = NodeGraph(registry)
        val selection = NodeSelectionController(graph)
        val workflow = NodeWorkflowService(registry, graph, selection)
        val node = graph.spawn("node", 20f, 30f)!!
        selection.select(node)

        workflow.remember("node")
        workflow.setFavorite("node")
        val template = workflow.createTemplate("Single")!!
        val created = workflow.instantiate(template.name, 100f, 120f)

        assertEquals(listOf("node"), workflow.recentIds())
        assertTrue("node" in workflow.favoriteTypeIds)
        assertEquals(1, created.size)
        assertEquals(100f, created.single().x)
        assertEquals(120f, created.single().y)
        assertTrue(created.single().selected)
    }

    @Test
    fun `collaboration sessions replicate edits once without echo`() {
        class Document : NodeCollaborativeDocument {
            private val listeners = mutableListOf<(net.swzo.brass.ui.kit.node.GraphChange) -> Unit>()
            var bytes = ByteArray(0)
            var revision = 0L
            override fun onGraphChange(
                listener: (net.swzo.brass.ui.kit.node.GraphChange) -> Unit,
            ): () -> Unit {
                listeners += listener
                return { listeners -= listener }
            }
            override fun applyRemoteSnapshot(bytes: ByteArray, label: String) {
                this.bytes = bytes
                listeners.toList().forEach { it(net.swzo.brass.ui.kit.node.GraphChange(++revision, label, bytes)) }
            }
            fun edit(next: ByteArray) {
                bytes = next
                listeners.toList().forEach { it(net.swzo.brass.ui.kit.node.GraphChange(++revision, "Edit", bytes)) }
            }
        }
        val left = Document()
        val right = Document()
        val hub = InMemoryNodeCollaborationHub()
        val leftSession = NodeCollaborationSession(left, "left", hub.transport())
        val rightSession = NodeCollaborationSession(right, "right", hub.transport())
        var rightChanges = 0
        val stop = right.onGraphChange { rightChanges++ }

        left.edit(byteArrayOf(1, 2, 3, 4))

        assertTrue(left.bytes.contentEquals(right.bytes))
        assertEquals(1, rightChanges)
        stop()
        leftSession.close()
        rightSession.close()
    }

    @Test
    fun `scheduler handles a thousand-node chain within its regression budget`() {
        val registry = NodeRegistry()
            .register(NodeType("source", "Source", BrassAccent.DEFAULT,
                outputs = listOf(Port("out", PortType.NUMBER)),
                executor = NodeExecutor {
                    CompletableFuture.completedFuture(NodeResult(mapOf(0 to 1)))
                }))
            .register(NodeType("pass", "Pass", BrassAccent.DEFAULT,
                inputs = listOf(Port("in", PortType.NUMBER)),
                outputs = listOf(Port("out", PortType.NUMBER)),
                executor = NodeExecutor { context ->
                    CompletableFuture.completedFuture(NodeResult(mapOf(0 to context.inputs.first(0))))
                }))
        val graph = NodeGraph(registry)
        var previous = graph.spawn("source", 0f, 0f)!!
        repeat(999) { index ->
            val next = graph.spawn("pass", (index % 20) * 16f, (index / 20) * 12f)!!
            graph.link(previous, 0, next, 0)
            previous = next
        }

        assertTimeout(Duration.ofSeconds(3)) {
            val report = GraphScheduler(graph).run().get(3, TimeUnit.SECONDS)
            assertEquals(ExecutionState.COMPLETED, report.state)
            assertEquals(1000, report.order.size)
        }
    }

    private fun waitFor(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (!predicate() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue(predicate(), "condition was not reached before timeout")
    }
}
