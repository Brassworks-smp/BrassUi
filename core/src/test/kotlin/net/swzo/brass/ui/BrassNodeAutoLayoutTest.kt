package net.swzo.brass.ui

import net.swzo.brass.ui.kit.node.NodeAutoLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** The auto-layout is pure geometry, so it is exercised with no GL context at all. */
class BrassNodeAutoLayoutTest {

    private fun node(id: Int, w: Float = 156f, h: Float = 90f) = NodeAutoLayout.LayoutNode(id, w, h)
    private fun edge(a: Int, b: Int) = NodeAutoLayout.LayoutEdge(a, b)

    @Test
    fun `a chain flows left to right`() {
        val out = NodeAutoLayout.layout(
            listOf(node(1), node(2), node(3), node(4)),
            listOf(edge(1, 2), edge(2, 3), edge(3, 4)),
        )
        val p1 = out.positions.getValue(1)
        val p4 = out.positions.getValue(4)
        assertTrue(p4.first > p1.first, "last node must be to the right of the first")
        assertTrue(p1.second == p4.second, "a single-line chain should share its y")
    }

    @Test
    fun `unconnected nodes are split away from connected ones`() {
        val out = NodeAutoLayout.layout(
            listOf(node(1), node(2), node(3)),
            listOf(edge(1, 2)),
        )
        val connectedMaxY = maxOf(out.positions.getValue(1).second, out.positions.getValue(2).second) + 90f
        val isolatedY = out.positions.getValue(3).second
        assertTrue(isolatedY > connectedMaxY + 100f, "isolated nodes park below the connected flow")
    }

    @Test
    fun `nodes inside a column stack without overlapping`() {
        val out = NodeAutoLayout.layout(
            listOf(node(1), node(2), node(3), node(4), node(5), node(6)),
            listOf(edge(1, 4), edge(2, 4), edge(3, 4), edge(4, 5), edge(4, 6)),
        )
        // Layer 0 holds 1,2,3 stacked in one column.
        val layer = listOf(1, 2, 3).map { out.positions.getValue(it) to 90f }
        for (i in layer.indices) for (j in i + 1 until layer.size) {
            val (pi, hi) = layer[i]
            val (pj, _) = layer[j]
            assertTrue(pi.second + hi <= pj.second || pj.second + 90f <= pi.second, "column nodes must not overlap")
        }
    }

    @Test
    fun `output is deterministic`() {
        val nodes = listOf(node(1), node(2), node(3), node(4), node(5))
        val edges = listOf(edge(1, 3), edge(2, 3), edge(3, 4), edge(3, 5), edge(5, 2))
        val a = NodeAutoLayout.layout(nodes, edges)
        val b = NodeAutoLayout.layout(nodes, edges)
        assertEquals(a.positions, b.positions)
    }

    @Test
    fun `a feedback loop settles in one column instead of spiralling`() {
        val out = NodeAutoLayout.layout(
            listOf(node(1), node(2), node(3), node(4)),
            listOf(edge(1, 2), edge(2, 3), edge(3, 4), edge(4, 1)),
        )
        val xs = listOf(1, 2, 3, 4).map { out.positions.getValue(it).first }
        val spread = xs.max() - xs.min()
        assertTrue(spread < 1f, "a pure cycle must share one column (spread $spread)")
    }

    @Test
    fun `multiple components never overlap`() {
        val out = NodeAutoLayout.layout(
            listOf(node(1), node(2), node(3), node(4)),
            listOf(edge(1, 2), edge(3, 4)),
        )
        // Regression: component placement used to forget the running offset, so the second component
        // (and the isolated grid) landed on top of the first.
        val a = listOf(1, 2).map { out.positions.getValue(it) }
        val b = listOf(3, 4).map { out.positions.getValue(it) }
        for ((ax, ay) in a) for ((bx, by) in b) {
            val separated = ax + 156f <= bx || bx + 156f <= ax || ay + 90f <= by || by + 90f <= ay
            assertTrue(separated, "components must not overlap")
        }
    }

    @Test
    fun `three components never stack on top of each other`() {
        val out = NodeAutoLayout.layout(
            listOf(node(1), node(2), node(3), node(4), node(5), node(6), node(7), node(8)),
            listOf(edge(1, 2), edge(3, 4), edge(5, 6), edge(6, 7), edge(7, 8)),
        )
        val groups = listOf(listOf(1, 2), listOf(3, 4), listOf(5, 6, 7, 8))
        for (g in groups.indices) for (h in g + 1 until groups.size) {
            for ((ax, ay) in groups[g].map { out.positions.getValue(it) })
                for ((bx, by) in groups[h].map { out.positions.getValue(it) }) {
                    val separated = ax + 156f <= bx || bx + 156f <= ax || ay + 90f <= by || by + 90f <= ay
                    assertTrue(
                        separated,
                        "component ${groups[g]} must not overlap component ${groups[h]}",
                    )
                }
        }
    }

    @Test
    fun `repeated runs anchored to the top-left never drift`() {
        val nodes = listOf(node(1), node(2), node(3), node(4), node(5), node(6), node(7))
        val edges = listOf(edge(1, 4), edge(2, 4), edge(3, 4), edge(4, 5), edge(5, 6), edge(6, 7))
        val out = NodeAutoLayout.layout(nodes, edges)

        // Simulate the editor's origin anchoring across repeated runs.
        var current = out.positions.mapValues { (_, p) -> p.first to (p.second + 500f) }
        repeat(3) {
            val oldMinX = current.values.minOf { it.first }
            val oldMinY = current.values.minOf { it.second }
            val dx = oldMinX - out.bounds[0]
            val dy = oldMinY - out.bounds[1]
            current = out.positions.mapValues { (_, p) -> (p.first + dx) to (p.second + dy) }
        }
        assertEquals(500f, current.values.minOf { it.second }, 0.001f, "top edge must stay at its first-run position")
    }

    @Test
    fun `unconnected nodes never overlap connected ones`() {
        val out = NodeAutoLayout.layout(
            listOf(node(1), node(2), node(3), node(4), node(5), node(6)),
            listOf(edge(1, 2), edge(2, 3)),
        )
        val connected = listOf(1, 2, 3).map { out.positions.getValue(it) }
        val isolated = listOf(4, 5, 6).map { out.positions.getValue(it) }
        for ((_, cy) in connected) for ((_, iy) in isolated) {
            assertTrue(
                cy + 90f <= iy || iy + 90f <= cy,
                "isolated grid must sit below the connected flow",
            )
        }
    }

    @Test
    fun `every node gets a position`() {
        val out = NodeAutoLayout.layout(
            listOf(node(1), node(2), node(3), node(4), node(5)),
            listOf(edge(1, 3), edge(2, 3), edge(3, 5)),
        )
        assertEquals(5, out.positions.size)
        for (id in 1..5) {
            val (x, y) = out.positions.getValue(id)
            assertTrue(abs(x) < 1e5f && abs(y) < 1e5f, "finite position for $id")
        }
    }
}
