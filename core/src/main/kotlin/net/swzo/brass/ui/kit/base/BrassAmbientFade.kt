package net.swzo.brass.ui.kit.base

import net.swzo.brass.ui.kit.base.BrassAmbientFade.VANISH_AT
import net.swzo.brass.ui.kit.base.BrassAmbientFade.current
import net.swzo.brass.ui.kit.base.BrassAmbientFade.earlyOut
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color

/**
 * The alpha every widget multiplies into its own while an **ancestor frame** is animating.
 *
 * Elementa has no notion of an inherited opacity: each component picks its own colours, so there is no
 * way to fade a subtree by fading its parent. This is the missing channel - a single value, set around
 * a frame's draw by [BrassFrameAnim.push] and restored by [BrassFrameAnim.pop], that [BrassWidget]
 * folds into its entrance alpha. Nesting works because push saves and restores the previous value
 * rather than assuming it was 1.
 *
 * It is a plain mutable global rather than anything threaded through the draw call because Elementa's
 * `draw` signature is fixed and the UI draws on one thread.
 */
object BrassAmbientFade {
    /** 1 while nothing is animating; the frame's eased alpha while one is. */
    var current: Float = 1f

    /** Whether anything is currently fading - lets a hot draw path skip the work entirely. */
    val active: Boolean get() = current < 0.999f

    /**
     * [c] scaled by [current].
     *
     * Applied at the toolkit's drawing chokepoints ([BrassFont.draw], [BrassIcons.draw],
     * [BrassCard]'s fills, [net.swzo.brass.ui.BrassBlock]) rather than left to each widget. Most
     * widgets paint with literal palette colours - `Colors.BRASS_600`, `Colors.UI_TEXT` - which no
     * per-widget alpha can reach, which is exactly why a closing window's contents used to stay fully
     * opaque and then blink out. Catching it where the pixels are actually emitted covers all of them.
     */
    fun apply(c: Color): Color =
        if (current >= 0.999f) c
        else Color(c.red, c.green, c.blue, (c.alpha * current.coerceIn(0f, 1f)).toInt())

    /** Fade level at which vanilla-rendered content is dropped entirely - see [earlyOut]. */
    const val VANISH_AT = 0.5f

    /**
     * Remap a fade so content finishes fading at [VANISH_AT] instead of at 0, returning 0 once it is
     * past that point.
     *
     * For content the toolkit cannot truly dissolve - items and entities, drawn by the game's own
     * renderer, which fades them by multiplying toward black rather than to transparent (see
     * `NeoForgePlatform.applyFade`). Compressing the fade into the first half means they are already
     * black by the halfway point, so dropping them there costs nothing visible, and the second half of
     * the window's fade is not spent watching a black rectangle sit in a slot.
     *
     * Returns 1 whenever nothing is fading, so this is free in the normal case.
     */
    fun earlyOut(fade: Float): Float {
        if (fade >= 0.999f) return 1f
        if (fade <= VANISH_AT) return 0f
        return ((fade - VANISH_AT) / (1f - VANISH_AT)).coerceIn(0f, 1f)
    }
}
