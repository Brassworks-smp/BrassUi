package net.swzo.brass.ui

import net.swzo.brass.ui.kit.input.BrassRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [BrassRange] is the arithmetic three controls share, and it is the kind of code that is easy to get
 * *nearly* right — an off-by-one in the snap, a format that prints `0.30000001`. It is also pure, so
 * unlike the widgets around it, it can simply be checked.
 */
class BrassRangeTest {

    @Test
    fun `snap clamps to the bounds`() {
        val range = BrassRange(0f, 10f)
        assertEquals(0f, range.snap(-5f))
        assertEquals(10f, range.snap(99f))
        assertEquals(4f, range.snap(4f))
    }

    @Test
    fun `snap quantises to the step`() {
        val range = BrassRange(0f, 10f, step = 2.5f)
        assertEquals(2.5f, range.snap(3f))
        assertEquals(5f, range.snap(4f))
        assertEquals(0f, range.snap(1f))
    }

    @Test
    fun `a step that does not divide the span never leaves the bounds`() {
        // 3 does not divide 10; rounding up from 9 lands on 12, which must still clamp to the max.
        val range = BrassRange(0f, 10f, step = 3f)
        assertTrue(range.snap(10f) <= 10f)
        assertTrue(range.snap(9.9f) <= 10f)
    }

    @Test
    fun `fraction and valueAt are inverses`() {
        val range = BrassRange(-20f, 60f)
        for (value in listOf(-20f, 0f, 13.5f, 60f)) {
            assertEquals(value, range.valueAt(range.fraction(value)), 0.001f)
        }
    }

    @Test
    fun `fraction is clamped for values outside the range`() {
        val range = BrassRange(0f, 10f)
        assertEquals(0f, range.fraction(-100f))
        assertEquals(1f, range.fraction(100f))
    }

    @Test
    fun `nudge moves by one step and stops at the bounds`() {
        val range = BrassRange(0f, 5f, step = 1f)
        assertEquals(3f, range.nudge(2f, 1))
        assertEquals(1f, range.nudge(2f, -1))
        assertEquals(5f, range.nudge(5f, 1))
        assertEquals(0f, range.nudge(0f, -1))
    }

    @Test
    fun `nudge without a step moves by a hundredth of the span`() {
        val range = BrassRange(0f, 100f)
        assertEquals(51f, range.nudge(50f, 1), 0.001f)
    }

    @Test
    fun `decimals follow the step`() {
        assertEquals(0, BrassRange(0f, 10f, step = 1f).decimals)
        assertEquals(1, BrassRange(0f, 10f, step = 0.5f).decimals)
        assertEquals(2, BrassRange(0f, 10f, step = 0.01f).decimals)
    }

    @Test
    fun `format trims an exact value to an integer`() {
        val range = BrassRange(0f, 10f, step = 0.5f)
        assertEquals("2", range.format(2f))
        assertEquals("2.5", range.format(2.5f))
    }

    @Test
    fun `an inverted range is rejected at construction`() {
        // Silently swapping or clamping would produce a control that looks fine and behaves backwards.
        assertThrows(IllegalArgumentException::class.java) { BrassRange(10f, 0f) }
        assertThrows(IllegalArgumentException::class.java) { BrassRange(1f, 1f) }
    }
}
