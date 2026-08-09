package net.swzo.brass.ui

import net.swzo.brass.ui.kit.node.ColorField
import net.swzo.brass.ui.kit.node.DefaultNodes
import net.swzo.brass.ui.kit.node.EnumField
import net.swzo.brass.ui.kit.node.NodeGraph
import net.swzo.brass.ui.kit.node.SliderField
import net.swzo.brass.ui.kit.node.ToggleField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color

/**
 * The node graph's save/load format is pure data, so it can be exercised with no GL context - which is
 * the point of keeping the model plain: persistence is testable and trivial.
 */
class BrassNodeGraphTest {

    private fun registry() = DefaultNodes.registry()

    @Test
    fun `a graph round-trips through JSON with node values and links intact`() {
        val g = NodeGraph(registry())
        val time = g.spawn("time", 10f, 20f)!!
        val noise = g.spawn("noise", 200f, 40f)!!
        val gradient = g.spawn("gradient", 400f, 60f)!!

        (noise.field("scale") as SliderField).value = 0.8f
        (noise.field("type") as EnumField).index = 2               // Worley
        (noise.field("ridged") as ToggleField).on = false
        (gradient.field("a") as ColorField).color = Color(0x123456)
        noise.collapsed = true

        g.link(time, 0, noise, 0)
        g.link(noise, 0, gradient, 0)

        val restored = NodeGraph.fromJson(registry(), g.toJson())

        assertEquals(3, restored.nodes.size, "every node survives the round trip")
        assertEquals(2, restored.links.size, "every wire survives the round trip")

        val rn = restored.byId(noise.id)!!
        assertEquals(200f, rn.x, "position is preserved")
        assertTrue(rn.collapsed, "collapse state is preserved")
        assertEquals(0.8f, (rn.field("scale") as SliderField).value, 1e-4f, "slider value is preserved")
        assertEquals("Worley", (rn.field("type") as EnumField).current, "enum selection is preserved by name")
        assertEquals(false, (rn.field("ridged") as ToggleField).on, "toggle value is preserved")
        assertEquals(Color(0x123456), (restored.byId(gradient.id)!!.field("a") as ColorField).color, "colour is preserved")
    }

    @Test
    fun `a link only forms between matching port types`() {
        val g = NodeGraph(registry())
        val gradient = g.spawn("gradient", 0f, 0f)!!    // outputs a COLOR
        val noise = g.spawn("noise", 100f, 0f)!!        // input is a NUMBER
        assertNull(g.link(gradient, 0, noise, 0), "a colour output must not connect to a number input")

        val time = g.spawn("time", 0f, 100f)!!          // outputs a NUMBER
        assertNotNull(g.link(time, 0, noise, 0), "matching number ports connect")
    }

    @Test
    fun `an input holds a single wire - a second replaces the first`() {
        val g = NodeGraph(registry())
        val a = g.spawn("time", 0f, 0f)!!
        val b = g.spawn("time", 0f, 60f)!!
        val noise = g.spawn("noise", 200f, 0f)!!
        g.link(a, 0, noise, 0)
        g.link(b, 0, noise, 0)
        assertEquals(1, g.links.size, "the second wire into an input replaces the first")
        assertEquals(b.id, g.links.first().from.id, "the surviving wire is the newest one")
    }

    @Test
    fun `unknown node types are skipped rather than crashing the load`() {
        val json = """{ "version":1,
            "nodes":[ {"id":1,"type":"time","x":0,"y":0,"fields":{}},
                      {"id":2,"type":"not-a-real-type","x":0,"y":0,"fields":{}} ],
            "links":[] }"""
        val restored = NodeGraph.fromJson(registry(), json)
        assertEquals(1, restored.nodes.size, "a file from a newer build opens, dropping types this build lacks")
    }

    @Test
    fun `clear removes nodes, wires, groups, notes and bookmarks`() {
        val g = NodeGraph(registry())
        val a = g.spawn("time", 0f, 0f)!!
        val b = g.spawn("noise", 100f, 0f)!!
        g.link(a, 0, b, 0)
        g.frame("Group", listOf(a.id, b.id))
        g.comment("note", 0f, 0f)
        g.bookmark("view", 0f, 0f, 1f)

        g.clear()

        assertTrue(g.nodes.isEmpty(), "nodes are cleared")
        assertTrue(g.links.isEmpty(), "wires are cleared")
        assertTrue(g.frames.isEmpty(), "groups are cleared")
        assertTrue(g.comments.isEmpty(), "notes are cleared")
        assertTrue(g.bookmarks.isEmpty(), "bookmarks are cleared")
    }
}
