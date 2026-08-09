@file:Suppress("unused")
package net.swzo.brass.ui.kit.node

import kotlin.math.hypot

/**
 * Shared world-space hit testing. The canvas controller delegates here, and plugin-owned overlays can
 * use the same geometry instead of approximating port or wire targets with a second set of constants.
 */
class NodeHitTester(private val graph: NodeGraph) {
    fun nodeAt(wx: Float, wy: Float): GraphNode? =
        graph.nodes.asReversed().firstOrNull { node ->
            !node.closing && wx >= node.x && wx <= node.x + node.width &&
                wy >= node.y && wy <= node.y + NodeLayout.height(node)
        }

    fun portAt(wx: Float, wy: Float): Triple<GraphNode, Int, Boolean>? {
        for (node in graph.nodes.asReversed()) {
            if (node.closing) continue
            node.type.inputs.forEachIndexed { index, port ->
                if (!port.hidden && hypot(
                        wx - NodeLayout.inputX(node),
                        wy - NodeLayout.inputY(node, index),
                    ) <= NodeLayout.PORT_HIT * port.size
                ) return Triple(node, index, false)
            }
            node.type.outputs.forEachIndexed { index, port ->
                if (!port.hidden && hypot(
                        wx - NodeLayout.outputX(node),
                        wy - NodeLayout.outputY(node, index),
                    ) <= NodeLayout.PORT_HIT * port.size
                ) return Triple(node, index, true)
            }
        }
        return null
    }

    fun wireAt(wx: Float, wy: Float, threshold: Float = 6f): Link? {
        var best: Link? = null
        var bestDistance = threshold
        graph.links.filterNot { it.closing }.forEach { link ->
            val distance = points(link).zipWithNext().minOfOrNull { (from, to) ->
                NodeWire.distanceTo(wx, wy, from.first, from.second, to.first, to.second)
            } ?: Float.MAX_VALUE
            if (distance < bestDistance) {
                best = link
                bestDistance = distance
            }
        }
        return best
    }

    fun rerouteAt(wx: Float, wy: Float, radius: Float = 7f): Pair<Link, Int>? {
        graph.links.asReversed().filterNot { it.closing }.forEach { link ->
            link.reroutes.indices.reversed().forEach { index ->
                val point = link.reroutes[index]
                if (hypot(wx - point.x, wy - point.y) <= radius) return link to index
            }
        }
        return null
    }

    fun commentAt(wx: Float, wy: Float): GraphComment? =
        graph.comments.asReversed().firstOrNull {
            wx in it.x..(it.x + it.width) && wy in it.y..(it.y + it.height)
        }

    fun frameHeaderAt(wx: Float, wy: Float, height: Float): GraphFrame? =
        graph.frames.asReversed().firstOrNull {
            wx in it.x..(it.x + it.width) && wy in it.y..(it.y + height)
        }

    private fun points(link: Link): List<Pair<Float, Float>> = buildList {
        add(NodeLayout.outputX(link.from) to NodeLayout.outputY(link.from, link.fromPort))
        link.reroutes.forEach { add(it.x to it.y) }
        add(NodeLayout.inputX(link.to) to NodeLayout.inputY(link.to, link.toPort))
    }
}

/**
 * Selection is model state, not input state. Keeping these operations outside the editor means a host
 * inspector, command palette or collaboration adapter can produce the same selection semantics as the
 * canvas without reaching into its mouse controller.
 */
class NodeSelectionController(private val graph: NodeGraph) {
    fun clear() {
        graph.nodes.forEach { it.selected = false }
        graph.links.forEach { it.selected = false }
        graph.comments.forEach { it.selected = false }
    }

    fun select(node: GraphNode, additive: Boolean = false, toggle: Boolean = false) {
        if (!additive) clear()
        node.selected = if (toggle) !node.selected else true
    }

    fun selectComment(comment: GraphComment, additive: Boolean = false, toggle: Boolean = false) {
        if (!additive) clear()
        comment.selected = if (toggle) !comment.selected else true
    }

    fun all() {
        graph.nodes.filterNot { it.closing }.forEach { it.selected = true }
        graph.links.filterNot { it.closing }.forEach { it.selected = true }
        graph.comments.forEach { it.selected = true }
    }

    fun invert() {
        graph.nodes.filterNot { it.closing }.forEach { it.selected = !it.selected }
        graph.links.filterNot { it.closing }.forEach { it.selected = !it.selected }
        graph.comments.forEach { it.selected = !it.selected }
    }

    fun inBox(x1: Float, y1: Float, x2: Float, y2: Float) {
        val left = minOf(x1, x2)
        val right = maxOf(x1, x2)
        val top = minOf(y1, y2)
        val bottom = maxOf(y1, y2)
        graph.nodes.filterNot { it.closing }.forEach { node ->
            val nodeRight = node.x + node.width
            val nodeBottom = node.y + NodeLayout.height(node)
            if (node.x < right && nodeRight > left && node.y < bottom && nodeBottom > top)
                node.selected = true
        }
        graph.comments.forEach { comment ->
            val commentRight = comment.x + comment.width
            val commentBottom = comment.y + comment.height
            if (comment.x < right && commentRight > left && comment.y < bottom && commentBottom > top)
                comment.selected = true
        }
    }
}

enum class NodeDiagnosticSeverity { INFO, WARNING, ERROR }

data class NodeDiagnostic(
    val severity: NodeDiagnosticSeverity,
    val code: String,
    val message: String,
    val nodeId: Int? = null,
    val port: Int? = null,
)

/**
 * Fast, side-effect-free validation intended for editor feedback and CI. Execution remains the final
 * authority, but these diagnostics catch structural problems before a run is started.
 */
object NodeGraphDiagnostics {
    fun inspect(graph: NodeGraph): List<NodeDiagnostic> = buildList {
        val liveNodes = graph.nodes.filterNot { it.closing }
        val liveLinks = graph.links.filterNot { it.closing }
        val incoming = liveLinks.groupBy { it.to.id }
        for (node in liveNodes) {
            node.type.inputs.forEachIndexed { index, input ->
                if (!input.optional && input.type != PortType.FLOW &&
                    incoming[node.id].orEmpty().none { it.toPort == index }
                ) add(NodeDiagnostic(
                    NodeDiagnosticSeverity.ERROR,
                    "missing-required-input",
                    "${node.type.title}.${input.name} requires a connection",
                    node.id,
                    index,
                ))
            }
        }
        for (link in liveLinks) {
            val output = link.from.type.outputs.getOrNull(link.fromPort)
            val input = link.to.type.inputs.getOrNull(link.toPort)
            when {
                output == null || input == null -> add(NodeDiagnostic(
                    NodeDiagnosticSeverity.ERROR, "missing-port", "A wire references a missing port",
                    link.to.id, link.toPort,
                ))
                !input.type.accepts(output.type) -> add(NodeDiagnostic(
                    NodeDiagnosticSeverity.ERROR, "incompatible-wire",
                    "${output.type.id} cannot connect to ${input.type.id}", link.to.id, link.toPort,
                ))
            }
        }
        cycleNodes(liveNodes, liveLinks).forEach { nodeId ->
            add(NodeDiagnostic(
                NodeDiagnosticSeverity.ERROR, "cycle", "Node is part of an execution cycle", nodeId,
            ))
        }
        graph.frames.forEach { frame ->
            val missing = frame.nodeIds.count { graph.byId(it) == null }
            if (missing > 0) add(NodeDiagnostic(
                NodeDiagnosticSeverity.WARNING, "stale-group-members",
                "${frame.title} contains $missing missing node reference${if (missing == 1) "" else "s"}",
            ))
            if (frame.parentFrameId != null && graph.frames.none { it.id == frame.parentFrameId })
                add(NodeDiagnostic(
                    NodeDiagnosticSeverity.WARNING, "missing-parent-group",
                    "${frame.title} references a missing parent group",
                ))
        }
    }

    private fun cycleNodes(nodes: List<GraphNode>, links: List<Link>): Set<Int> {
        val adjacency = links.groupBy { it.from.id }.mapValues { (_, outgoing) -> outgoing.map { it.to.id } }
        val visiting = HashSet<Int>()
        val visited = HashSet<Int>()
        val cyclic = LinkedHashSet<Int>()
        val path = ArrayList<Int>()
        fun visit(id: Int) {
            if (id in visited) return
            if (!visiting.add(id)) {
                val start = path.indexOf(id).coerceAtLeast(0)
                cyclic += path.subList(start, path.size)
                cyclic += id
                return
            }
            path += id
            adjacency[id].orEmpty().forEach(::visit)
            path.removeAt(path.lastIndex)
            visiting -= id
            visited += id
        }
        nodes.forEach { visit(it.id) }
        return cyclic
    }
}

data class NodeNavigatorItem(
    val nodeId: Int,
    val title: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val selected: Boolean,
)

data class NodeNavigatorSnapshot(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val nodes: List<NodeNavigatorItem>,
)

object NodeGraphNavigator {
    fun snapshot(graph: NodeGraph): NodeNavigatorSnapshot {
        val nodes = graph.nodes.filterNot { it.closing }
        if (nodes.isEmpty()) return NodeNavigatorSnapshot(0f, 0f, 1f, 1f, emptyList())
        return NodeNavigatorSnapshot(
            nodes.minOf { it.x },
            nodes.minOf { it.y },
            nodes.maxOf { it.x + it.width },
            nodes.maxOf { it.y + NodeLayout.height(it) },
            nodes.map {
                NodeNavigatorItem(it.id, it.type.title, it.x, it.y, it.width, NodeLayout.height(it), it.selected)
            },
        )
    }
}

data class NodeInspectorSnapshot(
    val nodeId: Int,
    val title: String,
    val typeId: String,
    val fields: Map<String, Any?>,
    val incoming: Int,
    val outgoing: Int,
    val runState: NodeRunState?,
    val diagnostics: List<NodeDiagnostic>,
)

object NodeGraphInspector {
    fun inspect(graph: NodeGraph, scheduler: GraphScheduler, node: GraphNode): NodeInspectorSnapshot {
        val links = graph.links.filterNot { it.closing }
        return NodeInspectorSnapshot(
            node.id,
            node.type.title,
            node.type.id,
            node.fields.associate { it.key to it.encode() },
            links.count { it.to === node },
            links.count { it.from === node },
            scheduler.nodeStates[node.id],
            NodeGraphDiagnostics.inspect(graph).filter { it.nodeId == node.id },
        )
    }
}
