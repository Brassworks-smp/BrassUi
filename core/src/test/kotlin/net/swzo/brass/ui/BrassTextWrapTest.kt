package net.swzo.brass.ui

import net.swzo.brass.ui.kit.text.BrassFont
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tooltip wrapping: the width-agnostic engine that keeps long descriptions and wire readouts from
 * sprawling into one unreadable line. A fake measurer (1px per character) makes the line-width
 * rules directly testable without Minecraft's font.
 */
class BrassTextWrapTest {

    private val measure: (String) -> Float = { it.length.toFloat() }

    @Test
    fun `short text stays one line`() {
        assertEquals(listOf("short"), BrassFont.wrapMeasured(measure, "short", 20f))
    }

    @Test
    fun `breaks at word boundaries before the limit`() {
        assertEquals(
            listOf("aaaa bbbb", "cccc dddd"),
            BrassFont.wrapMeasured(measure, "aaaa bbbb cccc dddd", 10f),
        )
    }

    @Test
    fun `a single word wider than the limit is hard-broken`() {
        assertEquals(listOf("abcd", "ef"), BrassFont.wrapMeasured(measure, "abcdef", 4f))
    }

    @Test
    fun `existing newlines are preserved`() {
        assertEquals(
            listOf("aaaa", "bbbb"),
            BrassFont.wrapMeasured(measure, "aaaa\nbbbb", 20f),
        )
    }

    @Test
    fun `empty lines survive`() {
        assertEquals(listOf("aaaa", "", "bbbb"), BrassFont.wrapMeasured(measure, "aaaa\n\nbbbb", 20f))
    }

    @Test
    fun `nothing fits means nothing changes`() {
        assertEquals(listOf("abc"), BrassFont.wrapMeasured(measure, "abc", 0f))
        assertEquals(listOf(""), BrassFont.wrapMeasured(measure, "", 10f))
    }
}
