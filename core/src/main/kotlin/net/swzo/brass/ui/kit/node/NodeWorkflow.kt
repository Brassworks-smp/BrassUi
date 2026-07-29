package net.swzo.brass.ui.kit.node

import net.swzo.brass.ui.Colors
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max

data class NodeTemplate(val name: String, val graphJson: String)

/**
 * User workflow memory and reusable subgraphs. It is deliberately independent of rendering and input:
 * a host can seed favorites/templates before the editor is shown or persist them in its own settings.
 */
class NodeWorkflowService(
    private val registry: NodeRegistry,
    private val graph: NodeGraph,
    private val selection: NodeSelectionController,
) {
    private val recentTypeIds = ArrayDeque<String>()
    val favoriteTypeIds = LinkedHashSet<String>()
    val templates = LinkedHashMap<String, NodeTemplate>()

    fun remember(typeId: String) {
        recentTypeIds.remove(typeId)
        recentTypeIds.addFirst(typeId)
        while (recentTypeIds.size > RECENT_LIMIT) recentTypeIds.removeLast()
    }

    fun recentTypes(): List<NodeType> = recentTypeIds.mapNotNull(registry::get)
    fun recentIds(): List<String> = recentTypeIds.toList()

    fun setFavorite(typeId: String, favorite: Boolean = true): Boolean {
        if (registry[typeId] == null) return false
        if (favorite) favoriteTypeIds += typeId else favoriteTypeIds -= typeId
        return typeId in favoriteTypeIds
    }

    fun createTemplate(name: String): NodeTemplate? {
        val json = selectedSubgraphJson() ?: return null
        return NodeTemplate(name, json).also { templates[name] = it }
    }

    fun instantiate(name: String, wx: Float, wy: Float): List<GraphNode> {
        val template = templates[name] ?: return emptyList()
        return pasteGraph(template.graphJson, wx, wy)
    }

    private fun selectedSubgraphJson(): String? {
        val selected = graph.nodes.filter { it.selected && !it.closing }
        if (selected.isEmpty()) return null
        val temporary = NodeGraph(registry)
        val copies = LinkedHashMap<Int, GraphNode>()
        selected.forEach { node ->
            temporary.spawn(node.type.id, node.x, node.y)?.let { copy ->
                node.copyValuesTo(copy)
                copies[node.id] = copy
            }
        }
        graph.links.filter { !it.closing && it.from in selected && it.to in selected }.forEach { link ->
            val from = copies[link.from.id] ?: return@forEach
            val to = copies[link.to.id] ?: return@forEach
            temporary.link(from, link.fromPort, to, link.toPort)?.reroutes?.addAll(
                link.reroutes.map { ReroutePoint(it.x, it.y) },
            )
        }
        return temporary.toJson()
    }

    private fun pasteGraph(json: String, wx: Float, wy: Float): List<GraphNode> {
        val temporary = NodeGraph.fromJson(registry, json)
        if (temporary.nodes.isEmpty()) return emptyList()
        val dx = wx - temporary.nodes.minOf { it.x }
        val dy = wy - temporary.nodes.minOf { it.y }
        selection.clear()
        val copies = LinkedHashMap<Int, GraphNode>()
        temporary.nodes.forEach { node ->
            graph.spawn(node.type.id, node.x + dx, node.y + dy)?.let { copy ->
                node.copyValuesTo(copy)
                copy.selected = true
                copies[node.id] = copy
                remember(node.type.id)
            }
        }
        temporary.links.forEach { link ->
            val from = copies[link.from.id] ?: return@forEach
            val to = copies[link.to.id] ?: return@forEach
            graph.link(from, link.fromPort, to, link.toPort)?.reroutes?.addAll(
                link.reroutes.map { ReroutePoint(it.x + dx, it.y + dy) },
            )
        }
        return copies.values.toList()
    }

    private companion object {
        const val RECENT_LIMIT = 8
    }
}

data class GraphChange(
    val revision: Long,
    val label: String,
    val graphJson: String,
)

data class NodeAccessibilityEntry(
    val nodeId: Int,
    val title: String,
    val description: String,
    val selected: Boolean,
)

sealed interface NodeImportResult {
    data class Imported(
        val path: Path,
        val nodes: Int,
        val links: Int,
        val compatibility: NodeIO.Compatibility = NodeIO.Compatibility.CURRENT,
    ) : NodeImportResult
    data class Rejected(val path: Path?, val reason: String) : NodeImportResult
}

/**
 * Portable vector export for docs, issue reports and collaboration previews. It intentionally exports
 * graph content rather than the editor chrome: nodes, labels, frames, notes and routed wires remain
 * sharp at any scale and need no GL context.
 */
object NodeGraphExport {
    fun toSvg(graph: NodeGraph, padding: Float = 24f): String {
        val bounds = bounds(graph)
        val minX = bounds[0] - padding
        val minY = bounds[1] - padding
        val width = max(1f, bounds[2] - bounds[0] + padding * 2f)
        val height = max(1f, bounds[3] - bounds[1] + padding * 2f)
        val background = hex(Colors.INK_950)
        val panel = hex(Colors.UI_ELEMENT_BG)
        val edge = hex(Colors.EDGE)
        val text = hex(Colors.UI_TEXT)
        return buildString {
            append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="$minX $minY $width $height">""")
            append("""<rect x="$minX" y="$minY" width="$width" height="$height" fill="$background"/>""")
            for (frame in graph.frames) {
                val color = hex(frame.color())
                append("""<rect x="${frame.x}" y="${frame.y}" width="${frame.width}" height="${frame.height}" fill="none" stroke="$color"/>""")
                append("""<text x="${frame.x + 6f}" y="${frame.y + 12f}" fill="$color" font-size="9">${escape(frame.title)}</text>""")
            }
            for (link in graph.links.filterNot { it.closing }) {
                val points = buildList {
                    add(NodeLayout.outputX(link.from) to NodeLayout.outputY(link.from, link.fromPort))
                    link.reroutes.forEach { add(it.x to it.y) }
                    add(NodeLayout.inputX(link.to) to NodeLayout.inputY(link.to, link.toPort))
                }
                append("""<polyline points="${points.joinToString(" ") { "${it.first},${it.second}" }}" fill="none" stroke="${hex(link.portType().color())}" stroke-width="2"/>""")
            }
            for (node in graph.nodes.filterNot { it.closing }) {
                append("""<rect x="${node.x}" y="${node.y}" width="${node.width}" height="${NodeLayout.height(node)}" fill="$panel" stroke="$edge"/>""")
                append("""<text x="${node.x + 8f}" y="${node.y + 13f}" fill="$text" font-size="10">${escape(node.type.title)}</text>""")
            }
            for (comment in graph.comments) {
                val color = hex(comment.color())
                append("""<rect x="${comment.x}" y="${comment.y}" width="${comment.width}" height="${comment.height}" fill="$panel" stroke="$color"/>""")
                append("""<text x="${comment.x + 5f}" y="${comment.y + 12f}" fill="$text" font-size="9">${escape(comment.text)}</text>""")
            }
            append("</svg>")
        }
    }

    fun writeSvg(graph: NodeGraph, path: Path): Path {
        path.parent?.let(Files::createDirectories)
        return Files.writeString(path, toSvg(graph))
    }

    private fun bounds(graph: NodeGraph): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var found = false
        for (node in graph.nodes.filterNot { it.closing }) {
            found = true
            minX = minOf(minX, node.x); minY = minOf(minY, node.y)
            maxX = maxOf(maxX, node.x + node.width)
            maxY = maxOf(maxY, node.y + NodeLayout.height(node))
        }
        for (frame in graph.frames) {
            found = true
            minX = minOf(minX, frame.x); minY = minOf(minY, frame.y)
            maxX = maxOf(maxX, frame.x + frame.width); maxY = maxOf(maxY, frame.y + frame.height)
        }
        for (comment in graph.comments) {
            found = true
            minX = minOf(minX, comment.x); minY = minOf(minY, comment.y)
            maxX = maxOf(maxX, comment.x + comment.width)
            maxY = maxOf(maxY, comment.y + comment.height)
        }
        if (!found) return floatArrayOf(0f, 0f, 1f, 1f)
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    private fun hex(color: java.awt.Color): String =
        "#%02x%02x%02x".format(color.red, color.green, color.blue)

    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
