package net.swzo.brass.ui

import net.swzo.brass.ui.kit.node.ColorField
import net.swzo.brass.ui.kit.node.DefaultNodes
import net.swzo.brass.ui.kit.node.EnumField
import net.swzo.brass.ui.kit.node.NodeGraph
import net.swzo.brass.ui.kit.node.NodeIO
import net.swzo.brass.ui.kit.node.SliderField
import net.swzo.brass.ui.kit.node.ToggleField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color

/**
 * The node graph's native **BSON** save/load format - the same pure-data round-trip the JSON format
 * already tests, plus the JSON/BSON equivalence that keeps export and import interchangeable.
 */
class BrassNodeBsonTest {

    private fun registry() = DefaultNodes.registry()

    private fun sampleGraph(): NodeGraph {
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
        val link = g.link(noise, 0, gradient, 0)!!
        g.reroute(link, 300f, 10f)
        g.reroute(link, 310f, 20f)
        g.frame("Group", listOf(time.id, noise.id), tone = net.swzo.brass.ui.kit.node.FrameTone.PATINA)
        g.comment("note", 5f, 5f)
        g.bookmark("view", 3f, 4f, 1.5f)
        return g
    }

    @Test
    fun `a graph round-trips through BSON with fields wires reroutes and decorations intact`() {
        val g = sampleGraph()
        val restored = NodeGraph.fromBson(registry(), g.toBson())

        assertEquals(3, restored.nodes.size)
        assertEquals(2, restored.links.size)
        assertEquals(1, restored.frames.size)
        assertEquals(1, restored.comments.size)
        assertEquals(1, restored.bookmarks.size)

        val rn = restored.byId(g.nodes[1].id)!!
        assertEquals(200f, rn.x)
        assertTrue(rn.collapsed)
        assertEquals(0.8f, (rn.field("scale") as SliderField).value, 1e-4f)
        assertEquals("Worley", (rn.field("type") as EnumField).current)
        assertEquals(false, (rn.field("ridged") as ToggleField).on)
        assertEquals(Color(0x123456), (restored.byId(g.nodes[2].id)!!.field("a") as ColorField).color)

        val wire = restored.links.first { it.from.id == g.nodes[1].id && it.to.id == g.nodes[2].id }
        assertEquals(2, wire.reroutes.size, "bend pins survive the binary round trip")
        assertEquals(300f, wire.reroutes[0].x)
        assertEquals("view", restored.bookmarks.first().name)
        assertEquals(1.5f, restored.bookmarks.first().zoom)
    }

    @Test
    fun `BSON and JSON carry identical graphs`() {
        val g = sampleGraph()
        val fromJson = NodeGraph.fromJson(registry(), g.toJson())
        val fromBson = NodeGraph.fromBson(registry(), g.toBson())

        assertEquals(fromJson.toJson(), fromBson.toJson(), "the same graph must serialize identically from either format")
    }

    @Test
    fun `unknown node types are skipped rather than crashing the BSON load`() {
        val g = NodeGraph(registry())
        g.spawn("time", 0f, 0f)
        val root = net.swzo.brass.ui.kit.net.BrassBson.parseDocument(g.toBson())!!
        val nodes = root.getArray("nodes")
        val alien = org.bson.BsonDocument()
        alien.put("id", org.bson.BsonInt32(999))
        alien.put("type", org.bson.BsonString("not-a-real-type"))
        alien.put("x", org.bson.BsonDouble(0.0))
        alien.put("y", org.bson.BsonDouble(0.0))
        nodes.add(alien)

        val restored = NodeGraph.fromBson(registry(), net.swzo.brass.ui.kit.net.BrassBson.writeDocument(root))
        assertEquals(1, restored.nodes.size, "a file from a newer build opens, dropping types this build lacks")
    }

    @Test
    fun `invalid BSON is rejected without destroying the open graph`() {
        val g = NodeGraph(registry())
        g.spawn("time", 0f, 0f)
        val before = g.toJson()

        assertFalse(g.loadBson(byteArrayOf(1, 2, 3)), "garbage bytes must not load")
        assertFalse(g.loadBson(net.swzo.brass.ui.kit.net.BrassBson.toBytes("not a graph")), "a non-graph document must not load")
        assertEquals(before, g.toJson(), "a failed load leaves the existing graph intact")
    }

    @Test
    fun `compatibility reports version differences for BSON like JSON`() {
        val g = NodeGraph(registry())
        g.spawn("time", 0f, 0f)
        val doc = net.swzo.brass.ui.kit.net.BrassBson.parseDocument(g.toBson())!!
        doc.put("version", org.bson.BsonInt32(NodeIO.CURRENT_VERSION + 1))
        assertEquals(
            NodeIO.Compatibility.FUTURE,
            NodeIO.compatibility(net.swzo.brass.ui.kit.net.BrassBson.writeDocument(doc)),
        )
        assertEquals(
            NodeIO.Compatibility.INVALID,
            NodeIO.compatibility(byteArrayOf(9, 9, 9)),
        )
    }
}
