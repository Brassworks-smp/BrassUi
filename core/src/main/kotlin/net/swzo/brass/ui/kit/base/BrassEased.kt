package net.swzo.brass.ui.kit.base

import kotlin.math.abs

/**
 * A float that eases toward a target, frame by frame.
 * ### Why this exists
 * Every animated control in the toolkit had grown its own copy of the same six lines: a `lastNanos`
 * field, read the clock, subtract, clamp, lerp, snap. Twice that copy shipped the *same bug* - an early
 * return when the value was already settled, taken **before** the clock was read. `lastNanos` then held
 * the moment the previous animation ended, so the next time the target changed, `dt` was however many
 * seconds the control had sat idle. Clamped to the 0.1 s ceiling, that single frame covered the whole
 * animation: the control appeared to work once and then snap forever after.
 * [advance] reads the clock **first, unconditionally**, which is what makes that bug unwriteable here.
 * Anything animating a value should use this rather than rolling the loop again.
 * ### The supported way to animate
 * This and [BrassClock] are the toolkit's animation API, for widgets outside it as much as within.
 * A custom widget should hold a `BrassEased` per animated value, set [target] when something changes,
 * and call [advance] once at the top of its draw:
 * ```kotlin
 * private val glow = BrassEased(0f, speed = 12f)
 * override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
 *     glow.target = if (hovered) 1f else 0f
 *     val lit = glow.advance()
 *     // ...draw using `lit`
 * }
 * ```
 * Rolling the delta-time loop by hand instead is exactly how the toolkit ended up with twelve copies
 * of it, two of which had the bug described above.
 */
class BrassEased(
    initial: Float = 0f,
    var speed: Float = 15f,
) {

    var target: Float = initial

    var value: Float = initial
        private set

    private var lastNanos = 0L

    val settled: Boolean get() = value == target

    /** Jump straight to [to] with no animation - for a state change that must not be seen to move. */
    fun snapTo(to: Float) {
        target = to
        value = to
    }

    fun advance(): Float {
        val dt = if (BrassClock.driven) BrassClock.dt else selfTimedDt()

        if (value == target) return value

        value += (target - value) * (speed * dt).coerceAtMost(1f)
        if (abs(value - target) < SNAP) value = target
        return value
    }

    private fun selfTimedDt(): Float {
        val now = System.nanoTime()
        val d = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastNanos = now
        return d
    }

    private companion object {
        const val SNAP = 0.004f
    }
}
