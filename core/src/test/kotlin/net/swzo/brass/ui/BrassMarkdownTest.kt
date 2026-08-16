package net.swzo.brass.ui

import net.swzo.brass.ui.kit.text.Markdown
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrassMarkdownTest {

    @Test
    fun `parse recognizes headings bullets and paragraphs`() {
        val blocks = Markdown.parse("# Title\n\nSome **bold** prose\n- one\n- two")
        assertEquals(5, blocks.size)
        assertTrue(blocks[0] is Markdown.Block.Heading)
        assertTrue(blocks[1] is Markdown.Block.Blank)
        assertTrue(blocks[2] is Markdown.Block.Paragraph)
        assertTrue(blocks[3] is Markdown.Block.Bullet)
        assertTrue(blocks[4] is Markdown.Block.Bullet)
    }

    @Test
    fun `inline marks bold code italic and links`() {
        val runs = Markdown.inline("a **bold** and `code` with [x](url)")
        assertEquals(Markdown.Style.NORMAL, runs[0].style)
        assertEquals("a ", runs[0].text)
        assertEquals(Markdown.Style.BOLD, runs[1].style)
        assertEquals("bold", runs[1].text)
        assertEquals(Markdown.Style.NORMAL, runs[2].style)
        assertEquals(" and ", runs[2].text)
        assertEquals(Markdown.Style.CODE, runs[3].style)
        assertEquals("code", runs[3].text)
        assertEquals(Markdown.Style.LINK, runs[5].style)
        assertEquals("x", runs[5].text)
        assertEquals("url", runs[5].url)
    }

    @Test
    fun `code fences become code blocks with a language`() {
        val blocks = Markdown.parse("before\n```lua\nlocal x = 1\noutput(\"y\", x)\n```\nafter")
        assertEquals(3, blocks.size)
        val code = blocks[1] as Markdown.Block.Code
        assertEquals("lua", code.language)
        assertEquals(listOf("local x = 1", "output(\"y\", x)"), code.lines)
    }

    @Test
    fun `wrap breaks long prose into multiple rows`() {
        val blocks = Markdown.parse("one two three four five six seven eight nine ten")
        val rows = Markdown.wrap(blocks, 60f, { it.length * 6f })
        assertTrue(rows.size > 1, "a wide paragraph must wrap")
        val joined = rows.joinToString(" ") { it.runs.joinToString("") { r -> r.text } }
        assertEquals("one two three four five six seven eight nine ten", joined.replace("\\s+".toRegex(), " ").trim())
    }

    @Test
    fun `a link too wide for the line is broken but keeps its url`() {
        val blocks = Markdown.parse("[click me](http://example.com) and some words")
        val rows = Markdown.wrap(blocks, 40f, { it.length * 6f })
        assertTrue(rows.size > 1)
        val linkRuns = rows.flatMap { it.runs }.filter { it.style == Markdown.Style.LINK }
        assertTrue(linkRuns.isNotEmpty())
        assertTrue(linkRuns.all { it.url == "http://example.com" }, "every broken chunk keeps the url")
        assertEquals("click me", linkRuns.joinToString("") { it.text })
    }

    @Test
    fun `an unbreakable word longer than the line is split mid-word`() {
        val blocks = Markdown.parse("some text then AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA end")
        val rows = Markdown.wrap(blocks, 60f, { it.length * 6f })
        // The long token must be split so no row exceeds the width.
        val widest = rows.map { row -> row.runs.joinToString("") { r -> r.text }.length * 6f }.maxOrNull()!!
        assertTrue(widest <= 60f, "widest row ${widest}px overflows the 60px line")
    }
}
