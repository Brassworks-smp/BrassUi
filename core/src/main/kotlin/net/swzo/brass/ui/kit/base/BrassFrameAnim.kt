package net.swzo.brass.ui.kit.base

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassFrameAnim.Companion.SPEED
import net.swzo.brass.ui.kit.surface.BrassPopup
import net.swzo.brass.ui.kit.surface.BrassWindow

/**
 * The open / close animation shared by [BrassWindow] and [BrassPopup] - a frame **pops** open from
 * slightly under size and fades in, and reverses on the way out.
 * ### Why the matrix rather than the constraints
 * Scaling through the constraint system would mean recomputing every descendant's layout each frame of
 * the animation, and the frame's *contents* would reflow as it grew - text rewrapping mid-pop, which
 * looks like a bug rather than a transition. Applying a scale to the matrix stack around both the card
 * and `super.draw` scales the finished frame as one image instead, and costs nothing but a push/pop.
 * The trade is that hit-testing uses the unscaled bounds while the animation runs, so a click during
 * those few frames lands a pixel or two off. At [SPEED] that window is under a fifth of a second and
 * the frame is not yet something the user is aiming at.
 * ### Closing
 * A frame that animates out has to outlive the call that closed it. [beginClose] only starts the
 * animation; the owner polls [finished] each frame and does the actual removal when it comes back true.
 */
class BrassFrameAnim {

    private var progress = 0f
    private var closing = false

    private var settledAt = 0L

    val isClosing: Boolean get() = closing

    val finished: Boolean get() = closing && progress <= 0.02f

    fun beginClose() { closing = true }

    fun advance(): Float {
        val dt = BrassClock.dt
        val now = System.nanoTime()

        val target = if (closing) 0f else 1f
        val wasOpening = !closing && progress < 1f
        progress += (target - progress) * (SPEED * dt).coerceAtMost(1f)
        if (kotlin.math.abs(progress - target) < 0.005f) progress = target
        // the instant the open animation lands is when the contents are allowed to cascade
        if (wasOpening && progress == 1f) settledAt = now

        // ease-out cubic on the way in; the linear approach above is already fast enough on the way out
        return if (closing) progress else 1f - (1f - progress) * (1f - progress) * (1f - progress)
    }

    /**
     * Wrap a frame's drawing in the pop transform, scaling about the centre of the given bounds.
     * Returns true when a matrix was pushed, and the caller must [pop] afterwards - an
     * animation at rest pushes nothing at all, so a settled UI pays no cost here.
     */
    fun push(m: UMatrixStack, eased: Float, x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        // The entrance phase is published even when no matrix is pushed: a settled frame still has a
        // short SETTLING window during which its contents are allowed to animate in.
        savedPhase = BrassEntrance.phase
        BrassEntrance.phase = phase()

        if (eased >= 0.999f) return false
        val cx = (x1 + x2) / 2f
        val cy = (y1 + y2) / 2f
        val s = MIN_SCALE + (1f - MIN_SCALE) * eased
        m.push()
        m.translate(cx, cy, 0f)
        m.scale(s, s, 1f)
        m.translate(-cx, -cy, 0f)
        // The frame's *contents* fade with it. The card takes an explicit alpha, but its children are
        // separate components drawing their own colours - so a closing window used to fade to nothing
        // while every widget inside it stayed fully opaque until the last frame and then vanished.
        // BrassAmbientFade is what carries the frame's alpha down to them.
        savedFade = BrassAmbientFade.current
        BrassAmbientFade.current = savedFade * eased
        return true
    }

    fun pop(m: UMatrixStack, pushed: Boolean) {
        BrassEntrance.phase = savedPhase
        if (!pushed) return
        BrassAmbientFade.current = savedFade
        m.pop()
    }

    private fun phase(): BrassEntrance.Phase = when {
        closing -> BrassEntrance.Phase.IDLE
        progress < 1f -> BrassEntrance.Phase.OPENING
        System.nanoTime() - settledAt < SETTLE_NANOS -> BrassEntrance.Phase.SETTLING
        else -> BrassEntrance.Phase.IDLE
    }

    private var savedFade = 1f
    private var savedPhase = BrassEntrance.Phase.IDLE

    private companion object {
        const val SPEED = 17f
        const val MIN_SCALE = 0.93f

        val SETTLE_NANOS: Long =
            ((BrassWidget.ENTRANCE_DELAY_MAX + 1f / BrassWidget.ENTRANCE_SPEED) * 1e9f).toLong()
    }
}
