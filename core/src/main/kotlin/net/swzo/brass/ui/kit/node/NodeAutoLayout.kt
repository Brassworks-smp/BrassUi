package net.swzo.brass.ui.kit.node

import java.util.ArrayDeque
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Pure, deterministic auto-layout for a node graph.
 *
 * Connected components are arranged as column flows reading left to right - the way a hand-made
 * graph reads: each layer is a column, the nodes inside it stack vertically, and columns advance
 * sideways at a fixed pitch. Longest-path layering collapses strongly-connected cycles into a
 * single column (so a feedback loop stacks in place instead of spiralling), and iterative
 * barycenter sweeps order the rows inside each column to cut wire crossings. Isolated nodes are
 * parked in a tidy grid below everything connected. Components are arranged in a **balanced grid** -
 * rows wrap at roughly sqrt(component-count) columns, so a handful of subnetworks land in a
 * squarish footprint instead of one endless vertical strip or one endless horizontal row - and
 * nothing ever overlaps.
 *
 * The algorithm takes geometry and edges only and returns coordinates - no UI, no animation - which
 * is what makes it testable off-game and lets [BrassNodeEditor.autoLayout] animate the move.
 */
object NodeAutoLayout {

    /** A node's geometry. Width/height are the *rendered* card size, not a grid cell. */
    data class LayoutNode(val id: Int, val width: Float, val height: Float)

    /** A directed wire: [from]'s output feeds [to]'s input (flow runs left to right). */
    data class LayoutEdge(val from: Int, val to: Int)

    /** Final node positions (top-left) and the union bounds `[minX, minY, maxX, maxY]`. */
    class Layout(
        val positions: Map<Int, Pair<Float, Float>>,
        val bounds: FloatArray,
    )

    private const val X_GAP = 44f
    private const val Y_GAP = 34f
    private const val COMPONENT_GAP = 130f
    private const val ISOLATED_GAP_X = 44f
    private const val ISOLATED_GAP_Y = 30f
    private const val ISOLATED_COLUMNS = 3
    private const val ISOLATED_OFFSET_Y = 160f

    fun layout(nodes: List<LayoutNode>, edges: List<LayoutEdge>): Layout {
        if (nodes.isEmpty()) return Layout(emptyMap(), floatArrayOf(0f, 0f, 0f, 0f))
        val byId = nodes.associateBy { it.id }
        val positions = HashMap<Int, Pair<Float, Float>>()

        val isolated = ArrayList<LayoutNode>()
        val connected = ArrayList<List<Int>>()
        for (component in components(nodes, edges)) {
            if (component.size == 1) {
                isolated += byId.getValue(component.single())
            } else {
                connected += component
            }
        }

        // Connected components: a balanced grid. Rows wrap at roughly sqrt(n) columns, so many
        // subnetworks land in a squarish footprint instead of one long strip.
        val placed = connected.map { it to placeComponent(it, byId, edges) }
        var blockBottom = 0f
        if (placed.isNotEmpty()) {
            val totalW = placed.sumOf { (it.second.bounds[2] - it.second.bounds[0]).toDouble() }
            val cols = ceil(sqrt(placed.size.toDouble())).toInt().coerceAtLeast(1)
            val targetRowW = (totalW / cols).toFloat()
            val sorted = placed.sortedByDescending { it.second.bounds[3] - it.second.bounds[1] }
            var rowX = 0f
            var rowY = 0f
            var rowH = 0f
            for ((_, p) in sorted) {
                val w = p.bounds[2] - p.bounds[0]
                val h = p.bounds[3] - p.bounds[1]
                if (rowX > 0f && rowX + w > targetRowW) {
                    rowX = 0f
                    rowY += rowH + COMPONENT_GAP
                    rowH = 0f
                }
                val dx = rowX - p.bounds[0]
                val dy = rowY - p.bounds[1]
                p.positions.forEach { (id, pos) -> positions[id] = (pos.first + dx) to (pos.second + dy) }
                rowX += w + COMPONENT_GAP
                rowH = maxOf(rowH, h)
                blockBottom = maxOf(blockBottom, rowY + h)
            }
        }

        // Isolated nodes: a tidy grid below every connected component, so "wired" and "not wired
        // yet" never mix.
        if (isolated.isNotEmpty()) {
            var gx = 0f
            var gy = blockBottom + ISOLATED_OFFSET_Y
            var column = 0
            var rowH = 0f
            for (n in isolated) {
                positions[n.id] = gx to gy
                rowH = maxOf(rowH, n.height)
                column++
                if (column >= ISOLATED_COLUMNS) {
                    column = 0
                    gx = 0f
                    gy += rowH + ISOLATED_GAP_Y
                    rowH = 0f
                } else {
                    gx += n.width + ISOLATED_GAP_X
                }
            }
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for ((id, p) in positions) {
            val n = byId.getValue(id)
            minX = minOf(minX, p.first); minY = minOf(minY, p.second)
            maxX = maxOf(maxX, p.first + n.width); maxY = maxOf(maxY, p.second + n.height)
        }
        return Layout(positions, floatArrayOf(minX, minY, maxX, maxY))
    }

    // ---- connected components -------------------------------------------------------------------

    private fun components(nodes: List<LayoutNode>, edges: List<LayoutEdge>): List<List<Int>> {
        val adj = HashMap<Int, MutableList<Int>>()
        nodes.forEach { adj[it.id] = ArrayList() }
        for (e in edges) {
            if (e.from in adj && e.to in adj && e.from != e.to) {
                adj.getValue(e.from).add(e.to)
                adj.getValue(e.to).add(e.from)
            }
        }
        val seen = HashSet<Int>()
        val out = ArrayList<List<Int>>()
        for (n in nodes) {
            if (n.id in seen) continue
            val stack = ArrayDeque<Int>()
            stack.add(n.id)
            seen.add(n.id)
            val comp = ArrayList<Int>()
            while (stack.isNotEmpty()) {
                val v = stack.removeLast()
                comp.add(v)
                for (u in adj.getValue(v)) if (seen.add(u)) stack.add(u)
            }
            out.add(comp)
        }
        return out
    }

    // ---- one connected component -----------------------------------------------------------------

    private class Placed(val positions: Map<Int, Pair<Float, Float>>, val bounds: FloatArray)

    private fun placeComponent(
        ids: List<Int>,
        byId: Map<Int, LayoutNode>,
        edges: List<LayoutEdge>,
    ): Placed {
        val idSet = ids.toSet()
        val directed = edges.filter { it.from in idSet && it.to in idSet && it.from != it.to }

        // Collapse strongly-connected cycles into one band each, so feedback loops lay out side by
        // side rather than climbing diagonally forever.
        val scc = stronglyConnected(ids, directed)
        val sccOf = HashMap<Int, Int>()
        scc.forEachIndexed { i, group -> group.forEach { sccOf[it] = i } }

        // Longest-path layering over the super-graph (SCCs), which is acyclic.
        val preds = HashMap<Int, MutableSet<Int>>()
        scc.indices.forEach { preds[it] = HashSet() }
        for (e in directed) {
            val a = sccOf.getValue(e.from)
            val b = sccOf.getValue(e.to)
            if (a != b) preds.getValue(b).add(a)
        }
        val superLayer = HashMap<Int, Int>()
        scc.indices.forEach { superLayer[it] = 0 }
        repeat(scc.size) {
            var changed = false
            for (s in scc.indices) {
                for (p in preds.getValue(s)) {
                    if (superLayer.getValue(s) < superLayer.getValue(p) + 1) {
                        superLayer[s] = superLayer.getValue(p) + 1
                        changed = true
                    }
                }
            }
            if (!changed) return@repeat
        }
        val base = superLayer.values.min()
        superLayer.replaceAll { _, v -> v - base }

        // Every node takes its SCC's layer.
        val layers = HashMap<Int, MutableList<Int>>()
        for (id in ids) layers.getOrPut(superLayer.getValue(sccOf.getValue(id))) { ArrayList() }.add(id)
        val layerKeys = layers.keys.sorted()

        // Crossing reduction: iterative barycenter sweeps (downward then upward), stable by id.
        val order = HashMap<Int, MutableList<Int>>()
        layers.forEach { (l, list) -> order[l] = list.sorted().toMutableList() }
        val indexIn = HashMap<Int, Int>()
        fun refreshIndex() {
            indexIn.clear()
            for (l in layerKeys) order.getValue(l).forEachIndexed { i, id -> indexIn[id] = i }
        }
        fun barycenter(v: Int, neighborLayer: List<Int>): Float {
            val ns = neighborLayer.filter { u ->
                directed.any { (it.from == v && it.to == u) || (it.from == u && it.to == v) }
            }
            if (ns.isEmpty()) return indexIn.getValue(v).toFloat()
            return ns.sumOf { indexIn.getValue(it).toDouble() }.div(ns.size).toFloat()
        }

        refreshIndex()
        repeat(6) {
            for (direction in 0..1) {
                val seq = if (direction == 0) layerKeys else layerKeys.reversed()
                for (l in seq) {
                    val li = layerKeys.indexOf(l)
                    val neighborLayer = (if (direction == 0) layerKeys.getOrNull(li - 1) else layerKeys.getOrNull(li + 1))
                        ?: continue
                    val nl = order.getValue(neighborLayer)
                    val scored = order.getValue(l).map { v -> v to barycenter(v, nl) }
                    order[l] = scored.sortedWith(compareBy({ it.second }, { it.first }))
                        .map { it.first }.toMutableList()
                    refreshIndex()
                }
            }
        }

        // Coordinates: every layer is one *column*; its nodes stack top-to-bottom and the column
        // block is vertically centred on the component's tallest column, so wires between columns
        // run horizontally instead of fanning diagonally.
        fun columnHeight(list: List<Int>): Float =
            list.fold(0f) { acc, nid -> acc + byId.getValue(nid).height + Y_GAP } - Y_GAP

        val maxColW = layerKeys.maxOf { l -> order.getValue(l).maxOf { byId.getValue(it).width } }
        val maxColH = layerKeys.maxOf { l -> columnHeight(order.getValue(l)) }
        val positions = HashMap<Int, Pair<Float, Float>>()
        var colX = 0f
        for (l in layerKeys) {
            val list = order.getValue(l)
            var y = (maxColH - columnHeight(list)) / 2f
            for (id in list) {
                val n = byId.getValue(id)
                positions[id] = colX to y
                y += n.height + Y_GAP
            }
            colX += maxColW + X_GAP
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for ((id, p) in positions) {
            val n = byId.getValue(id)
            minX = minOf(minX, p.first); minY = minOf(minY, p.second)
            maxX = maxOf(maxX, p.first + n.width); maxY = maxOf(maxY, p.second + n.height)
        }
        return Placed(positions, floatArrayOf(minX, minY, maxX, maxY))
    }

    /** Tarjan's strongly-connected components; groups are returned in reverse topological order. */
    private fun stronglyConnected(ids: List<Int>, edges: List<LayoutEdge>): List<List<Int>> {
        val succ = HashMap<Int, MutableList<Int>>()
        ids.forEach { succ[it] = ArrayList() }
        for (e in edges) if (e.from != e.to) succ.getValue(e.from).add(e.to)

        val index = HashMap<Int, Int>()
        val low = HashMap<Int, Int>()
        val onStack = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        val out = ArrayList<List<Int>>()
        var counter = 0

        fun strong(v: Int) {
            index[v] = counter
            low[v] = counter
            counter++
            stack.addLast(v)
            onStack.add(v)
            for (u in succ.getValue(v)) {
                if (u !in index) {
                    strong(u)
                    low[v] = minOf(low.getValue(v), low.getValue(u))
                } else if (u in onStack) {
                    low[v] = minOf(low.getValue(v), index.getValue(u))
                }
            }
            if (low.getValue(v) == index.getValue(v)) {
                val comp = ArrayList<Int>()
                while (true) {
                    val w = stack.removeLast()
                    onStack.remove(w)
                    comp.add(w)
                    if (w == v) break
                }
                out.add(comp)
            }
        }

        for (v in ids) if (v !in index) strong(v)
        return out
    }
}
