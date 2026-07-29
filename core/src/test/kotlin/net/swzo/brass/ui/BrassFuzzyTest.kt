package net.swzo.brass.ui

import net.swzo.brass.ui.kit.text.BrassFuzzy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ranking is the part of a command palette nobody notices when it is right and everybody notices when
 * it is wrong — and it is invisible to any test of the widget itself, since the widget just draws
 * whatever order it is handed.
 */
class BrassFuzzyTest {

    @Test
    fun `matches a subsequence`() {
        assertNotNull(BrassFuzzy.match("opw", "Open Preview Window"))
        assertNotNull(BrassFuzzy.match("ow", "Open Window"))
    }

    @Test
    fun `rejects letters that are out of order`() {
        // "Window" really does contain w…o, so the out-of-order case needs letters that only appear
        // in the wrong order — "na" against "Panel" is one: the a precedes the n.
        assertNull(BrassFuzzy.match("na", "Panel"))
        assertNull(BrassFuzzy.match("xyz", "Open Preview Window"))
    }

    @Test
    fun `an empty query matches everything`() {
        val match = BrassFuzzy.match("", "anything")
        assertNotNull(match)
        assertEquals(0, match!!.score)
    }

    @Test
    fun `nothing matches an empty candidate`() {
        assertNull(BrassFuzzy.match("a", ""))
    }

    @Test
    fun `word-start matches outrank mid-word ones`() {
        // "op" as two word initials should beat "op" buried inside a single word.
        val initials = BrassFuzzy.match("op", "Open Panel")!!.score
        val buried = BrassFuzzy.match("op", "Stop Working")!!.score
        assertTrue(initials > buried, "initials=$initials buried=$buried")
    }

    @Test
    fun `consecutive characters outrank scattered ones`() {
        val run = BrassFuzzy.match("prev", "preview")!!.score
        val scattered = BrassFuzzy.match("prev", "p r e v")!!.score
        assertTrue(run > scattered, "run=$run scattered=$scattered")
    }

    @Test
    fun `the shorter of two equal matches wins`() {
        val short = BrassFuzzy.match("copy", "Copy")!!.score
        val long = BrassFuzzy.match("copy", "Copy As Absolute Path")!!.score
        assertTrue(short > long, "short=$short long=$long")
    }

    @Test
    fun `positions point at the matched characters`() {
        val match = BrassFuzzy.match("ow", "Open Window")!!
        assertEquals(listOf(0, 5), match.positions.toList())
    }

    @Test
    fun `matching is case-insensitive`() {
        assertNotNull(BrassFuzzy.match("OPW", "open preview window"))
        assertNotNull(BrassFuzzy.match("opw", "OPEN PREVIEW WINDOW"))
    }

    @Test
    fun `rank drops non-matches and orders the rest`() {
        val items = listOf("Stop Working", "Open Panel", "Delete Everything")
        val ranked = BrassFuzzy.rank("op", items) { it }
        assertEquals(listOf("Open Panel", "Stop Working"), ranked)
    }

    @Test
    fun `rank with an empty query preserves the original order`() {
        // A palette with nothing typed must show its commands as given, not reshuffled by a scoring
        // pass where every score happens to be zero.
        val items = listOf("Zebra", "Apple", "Mango")
        assertEquals(items, BrassFuzzy.rank("", items) { it })
    }
}
