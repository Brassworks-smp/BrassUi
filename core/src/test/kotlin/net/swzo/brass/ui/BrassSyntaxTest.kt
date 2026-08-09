package net.swzo.brass.ui

import net.swzo.brass.ui.kit.text.BrassSyntax
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JSON is in the shipped rules file and highlighting is pure regex work - no GL, no widget - so the
 * token roles can be pinned in a plain JVM test, exactly like the rest of the node model tests.
 */
class BrassSyntaxTest {

    @Test
    fun `json is a known language`() {
        assertTrue(BrassSyntax.supports("json"), "the rules file must ship a json entry")
    }

    @Test
    fun `json strings numbers and booleans get their own colour roles`() {
        val json = """{"name": "plc", "count": 4, "enabled": true}"""
        val spans = BrassSyntax.highlight("json", json).flatten()

        val string = spans.first { it.text == "\"name\"" }
        val number = spans.first { it.text == "4" }
        val bool = spans.first { it.text == "true" }
        val plain = spans.first { it.text == "{" }

        assertEquals(Colors.SYNTAX_STRING, string.color, "a JSON string uses the string role")
        assertEquals(Colors.SYNTAX_NUMBER, number.color, "a JSON number uses the number role")
        assertEquals(Colors.SYNTAX_KEYWORD, bool.color, "true/false use the keyword role")
        assertTrue(
            plain.color != string.color && plain.color != number.color && plain.color != bool.color,
            "structural punctuation keeps the default role",
        )
    }

    @Test
    fun `no language falls back to a single default-coloured span per line`() {
        val spans = BrassSyntax.highlight(null, "hello\nworld")
        assertEquals(2, spans.size, "one span list per source line")
        assertEquals(listOf("hello", "world"), spans.map { it.single().text }, "text is preserved")
        assertTrue(spans.all { line -> line.all { it.color == Colors.SYNTAX_DEFAULT } }, "plain text uses the default role")
    }
}
