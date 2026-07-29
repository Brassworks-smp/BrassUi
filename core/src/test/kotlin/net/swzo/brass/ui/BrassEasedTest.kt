package net.swzo.brass.ui

import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.base.BrassEased
import net.swzo.brass.ui.kit.base.BrassUiSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The regression the class was written for: an early return taken *before* the clock is read leaves a
 * stale timestamp, so the next real animation gets a `dt` of however long the control sat idle and
 * covers the whole transition in one frame — "works once, then snaps forever after".
 *
 * Driven through [BrassClock.advanceManually], because a test loop runs far faster than real time and
 * a wall-clock-driven ease would never converge.
 */
class BrassEasedTest {

    /** One frame at 60 fps. */
    private fun frame() = BrassClock.advanceManually(1f / 60f)

    @AfterEach
    fun tearDown() = BrassUiSession.reset()

    @Test
    fun `settles exactly on its target rather than approaching forever`() {
        val e = BrassEased(0f, speed = 15f)
        e.target = 1f
        repeat(120) { frame(); e.advance() }
        assertEquals(1f, e.value)
        assertTrue(e.settled)
    }

    @Test
    fun `converges in roughly the time the speed implies`() {
        val e = BrassEased(0f, speed = 15f)
        e.target = 1f
        var frames = 0
        while (!e.settled && frames < 600) { frame(); e.advance(); frames++ }
        assertTrue(e.settled, "never settled")
        // speed 15 at 60fps is a quarter of the distance a frame; a third of a second is generous
        assertTrue(frames < 40, "took $frames frames, expected well under 40")
    }

    @Test
    fun `snapTo moves without animating`() {
        val e = BrassEased(0f)
        e.snapTo(0.5f)
        assertEquals(0.5f, e.value)
        assertTrue(e.settled)
    }

    @Test
    fun `an idle gap does not make the next animation instant`() {
        val e = BrassEased(0f, speed = 15f)
        e.target = 1f
        repeat(120) { frame(); e.advance() }
        assertTrue(e.settled)

        // sit settled — this is where the hand-rolled loop skipped its clock read and went stale
        repeat(50) { frame(); e.advance() }

        e.target = 0f
        frame()
        e.advance()
        assertTrue(e.value > 0.5f, "one frame covered the whole animation — the stale-clock bug")
        assertTrue(e.value < 1f, "should have moved at all")
    }

    @Test
    fun `advancing an already settled value is a no-op`() {
        val e = BrassEased(1f)
        e.target = 1f
        frame()
        assertEquals(1f, e.advance())
    }

    @Test
    fun `a large frame step is clamped so a lag spike cannot teleport the UI`() {
        // The clock caps dt at 0.1s, so the biggest step a slow ease can take is speed * 0.1 of the
        // remaining distance however long the game actually stalled for. At speed 5 that is half.
        val e = BrassEased(0f, speed = 5f)
        e.target = 1f
        BrassClock.advanceManually(10f)   // ten seconds of stall
        e.advance()
        assertEquals(0.5f, e.value, 0.01f, "a ten-second stall should still take one clamped step")
    }

    @Test
    fun `never overshoots its target`() {
        // speed * dt is capped at 1, so even an absurd speed lands on the target rather than past it
        val e = BrassEased(0f, speed = 1000f)
        e.target = 1f
        frame()
        e.advance()
        assertEquals(1f, e.value)
    }
}
