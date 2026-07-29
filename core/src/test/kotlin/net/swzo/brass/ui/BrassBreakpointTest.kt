package net.swzo.brass.ui

import net.swzo.brass.ui.kit.layout.BrassBreakpoint
import net.swzo.brass.ui.kit.layout.BrassSpacing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The column maths a card leans on to reflow instead of clipping when it is squeezed. The failure mode
 * it guards is the one the whole exercise is about: elements laid out at a fixed count that run off the
 * edge (or overlap) the moment the resolution drops.
 */
class BrassBreakpointTest {

    @Test
    fun `columns never drops below one`() {
        // Even when a single item is wider than the whole card, layout needs a column to put it in.
        assertEquals(1, BrassBreakpoint.columns(width = 40f, minItem = 100f, gap = 8f))
        assertEquals(1, BrassBreakpoint.columns(width = 0f, minItem = 100f, gap = 8f))
    }

    @Test
    fun `columns fit exactly at the boundary and one fewer just under it`() {
        // Three 60-wide items with an 8 gap need 60*3 + 8*2 = 196.
        assertEquals(3, BrassBreakpoint.columns(width = 196f, minItem = 60f, gap = 8f))
        // A pixel short of the third column drops to two, rather than clipping the third.
        assertEquals(2, BrassBreakpoint.columns(width = 195f, minItem = 60f, gap = 8f))
    }

    @Test
    fun `a laid-out row of columns always fits the width it was measured for`() {
        // The core invariant: for any width, the count columns() returns, at the width columnWidth()
        // gives, never exceeds the available width - which is exactly "it does not clip".
        val gap = BrassSpacing.GAP
        val minItem = 50f
        for (w in 20..1000 step 7) {
            val width = w.toFloat()
            val n = BrassBreakpoint.columns(width, minItem, gap)
            val cell = BrassBreakpoint.columnWidth(width, n, gap)
            val used = cell * n + gap * (n - 1)
            assertTrue(used <= width + 0.001f, "row of $n at ${cell}px overflowed ${width}px (used $used)")
            // And every column is at least the minimum, unless there is only one (which may be narrower
            // than the minimum because a single item is allowed to be squeezed rather than vanish).
            if (n > 1) assertTrue(cell >= minItem - 0.001f, "column ${cell}px fell under the ${minItem}px minimum at width $width")
        }
    }

    @Test
    fun `columnWidth splits the width evenly minus the gaps`() {
        // Two columns in 108 with a gap of 8: (108 - 8) / 2 = 50 each.
        assertEquals(50f, BrassBreakpoint.columnWidth(width = 108f, count = 2, gap = 8f), 0.001f)
        // A zero or negative count is treated as one column, never a divide-by-zero.
        assertEquals(108f, BrassBreakpoint.columnWidth(width = 108f, count = 0, gap = 8f), 0.001f)
    }

    @Test
    fun `the spacing scale keeps its one rule`() {
        // PAD == GAP is load-bearing: a control is the same distance from a card's edge as that edge is
        // from the next card, so the rhythm reads as even across a card and between two of them.
        assertEquals(BrassSpacing.PAD, BrassSpacing.GAP)
        assertTrue(BrassSpacing.TIGHT < BrassSpacing.PAD, "TIGHT should be tighter than the standard pad")
    }
}
