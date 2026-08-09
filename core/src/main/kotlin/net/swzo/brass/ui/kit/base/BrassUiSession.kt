package net.swzo.brass.ui.kit.base

import net.swzo.brass.ui.kit.platform.BrassCursor

/**
 * Put the toolkit's global state back to how it starts.
 * ### Why this exists
 * A handful of the toolkit's pieces are `object`s holding mutable state: the frame clock, the frame
 * counters, the entrance phase, the ambient fade, the cursor request, the tooltip's hover, the open
 * context menu. That is a defensible trade for a single-screen game GUI - the alternative is
 * threading a context through every `draw` signature Elementa has already fixed - but it has two
 * costs, and this addresses both.
 * **Testing.** Nothing could be exercised in isolation, because the previous test would have left
 * state behind. A `reset()` between cases is all that was missing.
 * **Screen transitions.** A screen that closes mid-animation leaves the ambient fade below 1 and the
 * entrance phase mid-cascade; the next screen then opens with its first frame already faded.
 * Deliberately *not* an attempt to remove the globals. They are the right shape for the problem; they
 * simply need a way back to a known state.
 */
object BrassUiSession {

    fun reset() {
        BrassAmbientFade.current = 1f
        BrassEntrance.phase = BrassEntrance.Phase.IDLE
        BrassStats.paused = false
        BrassClock.reset()
        BrassCursor.forget()
    }
}
