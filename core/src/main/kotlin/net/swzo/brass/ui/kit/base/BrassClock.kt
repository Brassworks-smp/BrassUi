package net.swzo.brass.ui.kit.base

import net.swzo.brass.ui.kit.base.BrassClock.FIRST_DT
import net.swzo.brass.ui.kit.base.BrassClock.beginFrame
import net.swzo.brass.ui.kit.base.BrassClock.driven
import net.swzo.brass.ui.kit.base.BrassClock.dt


/**
 * The frame clock - **one** reading of the wall clock per frame, shared by everything that animates.
 *
 * ### Why this exists
 *
 * Twelve components had each written the same six lines: hold a `lastNanos`, read `System.nanoTime()`,
 * subtract, divide, clamp to a 0.1 s ceiling, store. [BrassEased] was introduced specifically to stop
 * that (its docs record the bug the pattern shipped twice) and twelve call sites carried on rolling it
 * anyway.
 *
 * Beyond the duplication, twelve private clocks are *wrong* in a way one shared clock is not. They
 * drift apart: a widget that was culled for a few frames comes back with a large `dt` of its own while
 * its neighbours have a small one, so a row of controls animating "together" visibly does not. And a
 * component whose `draw` runs twice in a frame advances its animation twice.
 *
 * [beginFrame] is called once by [net.swzo.brass.ui.BrassScreen] before anything paints. Everything
 * downstream reads [dt] and gets the same number.
 *
 * ### Standalone use
 *
 * A toolkit embedded somewhere that never calls [beginFrame] would otherwise freeze every animation at
 * `dt = 0`. [driven] guards against that: until the first [beginFrame], [BrassEased] falls back to
 * timing itself, so a widget used outside a `BrassScreen` still animates.
 */
object BrassClock {

    /** Longest step any animation will take in one frame - a lag spike must not teleport the UI. */
    private const val MAX_DT = 0.1f

    /** Assumed first-frame step, before there are two timestamps to subtract. */
    private const val FIRST_DT = 0.016f

    /** Seconds since the previous frame, clamped. Read this instead of touching the clock. */
    var dt: Float = FIRST_DT
        private set

    /** Frames since startup. Useful as a cheap cache key for per-frame memoisation. */
    var frame: Long = 0L
        private set

    /** Whether anything is actually driving this clock - see the standalone note above. */
    var driven: Boolean = false
        private set

    private var lastNanos = 0L

    /** Start a new frame. Called once by the screen, before anything draws. */
    fun beginFrame() {
        driven = true
        val now = System.nanoTime()
        dt = if (lastNanos == 0L) FIRST_DT else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, MAX_DT)
        lastNanos = now
        frame++
    }

    /**
     * Forget the last timestamp, so the next frame starts from [FIRST_DT] rather than from however
     * long the UI has been closed. For a screen opening after the game has sat on a menu.
     */
    fun reset() {
        lastNanos = 0L
        dt = FIRST_DT
        driven = false
        frame = 0L
    }

    /**
     * Advance by an exact step instead of reading the wall clock.
     *
     * Every animation in the toolkit is driven by elapsed real time, which is right in a game and
     * makes the behaviour untestable: a test loop runs in microseconds, so `dt` is near zero and an
     * eased value never converges however many times it is stepped. Feeding the step in directly is
     * what makes "does this settle, and does it take roughly the right number of frames" a question
     * that can be asked at all.
     *
     * Intended for tests and for a caller rendering to a fixed timebase (recording a GIF, say).
     */
    fun advanceManually(step: Float) {
        driven = true
        dt = step.coerceIn(0f, MAX_DT)
        lastNanos = System.nanoTime()
        frame++
    }
}
