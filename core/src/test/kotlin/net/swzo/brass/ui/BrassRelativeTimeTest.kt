package net.swzo.brass.ui

import net.swzo.brass.ui.kit.text.BrassRelativeTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Relative-time formatting: coarse on purpose, and it has to survive a clock that disagrees with the
 * server's — which in practice it always does, by a second or two in either direction.
 */
class BrassRelativeTimeTest {

    private val second = 1000L
    private val minute = 60 * second
    private val hour = 60 * minute
    private val day = 24 * hour
    private val week = 7 * day
    private val year = 365 * day

    @Test
    fun `anything recent is just now`() {
        assertEquals("just now", BrassRelativeTime.format(0))
        assertEquals("just now", BrassRelativeTime.format(20 * second))
        assertEquals("just now", BrassRelativeTime.format(44 * second))
    }

    @Test
    fun `a future timestamp reads as just now`() {
        // Client and server clocks disagree routinely; "-3m ago" is nonsense the user should not see.
        assertEquals("just now", BrassRelativeTime.format(-5 * minute))
    }

    @Test
    fun `minutes hours days weeks years`() {
        assertEquals("2m ago", BrassRelativeTime.format(2 * minute))
        assertEquals("3h ago", BrassRelativeTime.format(3 * hour))
        assertEquals("2d ago", BrassRelativeTime.format(2 * day))
        assertEquals("3w ago", BrassRelativeTime.format(3 * week))
        assertEquals("2y ago", BrassRelativeTime.format(2 * year))
    }

    @Test
    fun `each unit boundary steps to the next unit`() {
        assertEquals("59m ago", BrassRelativeTime.format(hour - minute))
        assertEquals("1h ago", BrassRelativeTime.format(hour))
        assertEquals("23h ago", BrassRelativeTime.format(day - hour))
        assertEquals("1d ago", BrassRelativeTime.format(day))
    }

    @Test
    fun `the suffix can be dropped for a narrow column`() {
        assertEquals("2m", BrassRelativeTime.format(2 * minute, suffix = false))
        // "just now" has no unit to suffix, so it is unaffected.
        assertEquals("just now", BrassRelativeTime.format(0, suffix = false))
    }

    @Test
    fun `only one unit is ever shown`() {
        // 1h 12m reads as "1h" — the moment two units are needed the absolute time is the better
        // answer, which is what the tooltip is for.
        assertEquals("1h ago", BrassRelativeTime.format(hour + 12 * minute))
    }
}
