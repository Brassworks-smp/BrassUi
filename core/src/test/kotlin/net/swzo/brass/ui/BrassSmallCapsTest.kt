package net.swzo.brass.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import javax.imageio.ImageIO

/**
 * The glyph sheets and their atlases have to agree, and **nothing at runtime says so when they do
 * not**: `BrassSmallCaps` loads both behind `runCatching`, so a cell size off by one silently shears
 * every glyph and a `count` past the end of the image silently drops the last few cells. That is
 * exactly how `small-numbers.json` shipped as a byte-for-byte copy of the small-caps atlas — 26
 * glyphs of 6x9 describing a 40x9 sheet of ten 4x9 digits — without anything failing.
 *
 * These are the committed asset files, checked against each other. Deliberately *not* through
 * `BrassSmallCaps`: it imports Elementa, which is not on the test classpath, so measuring and drawing
 * can only be verified in a running client. The atlas arithmetic is the part that was wrong and the
 * part that is checkable here.
 *
 * The atlases are read with regexes rather than Gson for the same classpath reason — Gson reaches the
 * main source set through Minecraft, not through a dependency this module could also test against.
 * These are two committed files of flat integer fields, which is the one situation where picking
 * fields out with a pattern is not the mistake it usually is.
 */
class BrassSmallCapsTest {

    private fun read(name: String): String {
        val stream = javaClass.getResourceAsStream("/assets/brassui/textures/gui/$name.json")
        assertNotNull(stream, "$name.json is missing")
        return stream!!.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** The value of a top-level integer field. */
    private fun field(json: String, key: String): Int =
        Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toInt()
            ?: error("no \"$key\" in atlas")

    /** Every glyph entry, in file order, as (char, index, advance). */
    private fun glyphs(json: String): List<Triple<String, Int, Int>> =
        Regex("\\{\\s*\"char\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*,\\s*\"index\"\\s*:\\s*(\\d+)\\s*,\\s*\"advance\"\\s*:\\s*(\\d+)\\s*\\}")
            .findAll(json)
            .map { Triple(it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
            .toList()

    private fun sheetSize(name: String): Pair<Int, Int> {
        val stream = javaClass.getResourceAsStream("/assets/brassui/textures/gui/$name.png")
        assertNotNull(stream, "$name.png is missing")
        val img = stream!!.use(ImageIO::read)
        assertNotNull(img, "$name.png did not decode")
        return img.width to img.height
    }

    /** Every cell must land inside the image, and the row must use all of it. */
    private fun assertSheetMatchesAtlas(name: String) {
        val json = read(name)
        val cells = glyphs(json).size
        val (width, height) = sheetSize(name)

        assertEquals(field(json, "count"), cells, "$name: \"count\" disagrees with the glyph list")
        assertEquals(
            width, field(json, "cellWidth") * cells,
            "$name: $cells cells of ${field(json, "cellWidth")} px != a $width px sheet",
        )
        assertEquals(height, field(json, "cellHeight"), "$name: cellHeight != the sheet's height")
    }

    @Test
    fun `small caps sheet matches its atlas`() = assertSheetMatchesAtlas("small-caps")

    @Test
    fun `small numbers sheet matches its atlas`() = assertSheetMatchesAtlas("small-numbers")

    @Test
    fun `small numbers is ten 4px digits in order`() {
        val json = read("small-numbers")

        assertEquals(4, field(json, "cellWidth"))
        // A full font line box, like the caps sheet: the ink sits in the middle of it, and the blank
        // rows above and below are what BrassTag draws its padding and outline into.
        assertEquals(9, field(json, "cellHeight"))

        val cells = glyphs(json)
        assertEquals(10, cells.size)
        // BrassSmallCaps maps '0'..'9' onto cells 0..9 by arithmetic, so the atlas must be in that
        // order — a mismatch here draws digits as other digits, which is the sort of bug that survives
        // a glance at a screenshot.
        cells.forEachIndexed { i, (char, index, advance) ->
            assertEquals("$i", char)
            assertEquals(i, index)
            // 3 px of ink plus the trailing spacing column BrassTag.TRAILING accounts for.
            assertEquals(4, advance)
        }
    }
}
