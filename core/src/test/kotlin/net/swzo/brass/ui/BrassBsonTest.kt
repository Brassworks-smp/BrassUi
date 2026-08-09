package net.swzo.brass.ui

import net.swzo.brass.ui.kit.net.BrassBson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The reflective BSON codec brassnet now serialises through. Pure JVM, so the exact shapes the game
 * hosts move - action inputs, live state, wire results - are exercised off-game.
 */
class BrassBsonTest {

    private enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

    private data class Payload(
        val pos: String,
        val graph: ByteArray,
        val name: String?,
        val count: Int,
        val ratio: Float,
        val big: Long,
        val flag: Boolean,
    )

    private data class LiveState(
        val strengths: Map<String, Int>,
        val nodes: Map<Int, Int>,
        val values: Map<Int, Any?>,
        val season: Season,
    )

    @Test
    fun `a data class with binary nullable and numeric fields round-trips`() {
        val original = Payload(
            pos = "minecraft:overworld|1|2|3",
            graph = ByteArray(64) { it.toByte() },
            name = null,
            count = 7,
            ratio = 0.5f,
            big = 9_000_000_000L,
            flag = true,
        )
        val restored = BrassBson.fromBytes(BrassBson.toBytes(original), Payload::class.java)!!

        assertEquals(original.pos, restored.pos)
        assertTrue(original.graph.contentEquals(restored.graph), "byte arrays travel as binary, not text")
        assertNull(restored.name)
        assertEquals(7, restored.count)
        assertEquals(0.5f, restored.ratio, 1e-4f)
        assertEquals(9_000_000_000L, restored.big)
        assertEquals(true, restored.flag)
    }

    @Test
    fun `generic map keys and values keep their types`() {
        val original = LiveState(
            strengths = mapOf("a|b" to 15, "c|d" to 0),
            nodes = mapOf(1 to 8, 42 to 3),
            values = mapOf(1 to 0.5, 2 to "text", 3 to 12),
            season = Season.WINTER,
        )
        val restored = BrassBson.fromBytes(BrassBson.toBytes(original), LiveState::class.java)!!

        assertEquals(15, restored.strengths["a|b"])
        assertEquals(3, restored.nodes[42], "integer map keys survive as integers")
        assertEquals(0.5, restored.values[1])
        assertEquals("text", restored.values[2])
        assertEquals(12, restored.values[3])
        assertEquals(Season.WINTER, restored.season, "enums round-trip by name")
    }

    @Test
    fun `top-level scalars and nulls round-trip`() {
        assertEquals("hello", BrassBson.fromBytes(BrassBson.toBytes("hello"), String::class.java))
        assertEquals(42, BrassBson.fromBytes(BrassBson.toBytes(42), Int::class.java))
        assertEquals(2.5, BrassBson.fromBytes(BrassBson.toBytes(2.5), Double::class.java))
        assertEquals(true, BrassBson.fromBytes(BrassBson.toBytes(true), Boolean::class.java))
        assertNull(BrassBson.fromBytes(BrassBson.toBytes(null), String::class.java))
    }

    @Test
    fun `numbers decode into narrower field types`() {
        data class Narrow(val b: Byte, val s: Short, val l: Long, val f: Float)
        val original = Narrow(5, 300, 99L, 1.25f)
        val restored = BrassBson.fromBytes(BrassBson.toBytes(original), Narrow::class.java)!!
        assertEquals(original, restored)
    }

    @Test
    fun `lists and string maps round-trip`() {
        data class WithList(val args: List<String>, val nested: Map<String, List<Int>>)
        val original = WithList(listOf("a", "b"), mapOf("k" to listOf(1, 2, 3)))
        assertEquals(original, BrassBson.fromBytes(BrassBson.toBytes(original), WithList::class.java))
    }

    @Test
    fun `wire results round-trip like the old JSON wire`() {
        data class WireLike(val ok: Boolean, val code: String?, val args: List<String>?, val payload: String?)
        val failure = WireLike(false, "team.missing", listOf("42"), null)
        assertEquals(failure, BrassBson.fromBytes(BrassBson.toBytes(failure), WireLike::class.java))
        val success = WireLike(true, null, null, "\"payload\"")
        assertEquals(success, BrassBson.fromBytes(BrassBson.toBytes(success), WireLike::class.java))
    }

    @Test
    fun `garbage bytes decode to null instead of throwing`() {
        assertNull(BrassBson.fromBytes(byteArrayOf(1, 2, 3, 4, 5), String::class.java))
        assertNull(BrassBson.fromBytes(ByteArray(0), String::class.java))
    }

    @Test
    fun `unknown fields are skipped and missing fields keep defaults`() {
        val bytes = BrassBson.toBytes(mapOf("known" to "yes", "unknown" to 5))
        data class Minimal(val known: String?, val missing: Int)
        val restored = BrassBson.fromBytes(bytes, Minimal::class.java)!!
        assertEquals("yes", restored.known)
        assertEquals(0, restored.missing, "a field absent from the document keeps the allocated default, like Gson")
    }
}
