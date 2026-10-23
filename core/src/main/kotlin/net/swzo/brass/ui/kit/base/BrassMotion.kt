package net.swzo.brass.ui.kit.base

import kotlin.math.exp

/**
 * The one motion for moving between views: switching pages ([net.swzo.brass.ui.kit.layout.BrassPages], tabs
 * and nav rails) and going into or out of a level ([net.swzo.brass.ui.kit.layout.BrassNavStack]) all glide
 * the same way. The arriving view comes in from its side on a spring (quick to start, a long gentle settle, no
 * bounce) and fades in early; the leaving one drifts a little the other way and fades out early, so the two are
 * never half-seen over each other. Offsets snap to whole pixels, so pixel art stays crisp.
 */
object BrassMotion {
    /** How long a move takes, in seconds: unhurried, most of it the settle. */
    const val DURATION = 0.55f
    /** How far the arriving view starts out (of the width or height): a glide, not a full sweep. */
    const val TRAVEL = 0.35f
    /** How far the leaving view drifts the other way. */
    const val DRIFT = 0.12f
    /** By what share of the move the old view has faded out, and the new one in. */
    const val OUT = 0.4f
    const val IN = 0.5f
    /** The spring's stiffness over the whole move (critically damped: it settles without overshoot). */
    private const val STIFFNESS = 7f

    /** Ease in-out (quintic smootherstep). */
    fun smooth(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

    /** A critically damped spring from 0 to 1 over the move, scaled to land exactly on 1. */
    fun spring(t: Float): Float {
        fun f(x: Float) = 1f - (1f + STIFFNESS * x) * exp(-STIFFNESS * x)
        return (f(t) / f(1f)).coerceIn(0f, 1f)
    }

    /** Where the arriving view sits at [progress] (0..1), coming from [direction] (+1 / -1) across [size]. */
    fun arrive(progress: Float, direction: Int, size: Float): Float =
        Math.round(direction * (1f - spring(progress)) * size * TRAVEL).toFloat()

    /** Where the leaving view sits at [progress], drifting away from [direction] across [size]. */
    fun drift(progress: Float, direction: Int, size: Float): Float =
        Math.round(-direction * spring(progress) * size * DRIFT).toFloat()

    /** The arriving view's opacity at [progress]. */
    fun fadeIn(progress: Float): Float = smooth((progress / IN).coerceIn(0f, 1f))

    /** The leaving view's opacity at [progress] (0 once it's gone). */
    fun fadeOut(progress: Float): Float {
        val out = 1f - smooth((progress / OUT).coerceIn(0f, 1f))
        return out * out
    }
}
