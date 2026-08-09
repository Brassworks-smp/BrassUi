package net.swzo.brass.ui.kit.base

/**
 * The per-frame drawing state, entered and left as one.
 * ### Why this exists
 * Three independent global mutables describe "what is happening in this part of the frame":
 * [BrassAmbientFade.current] (the inherited opacity), [BrassEntrance.phase] (whether contents may
 * animate in) and [BrassStats.paused] (whether this subtree counts). All three have to be saved
 * before a subtree draws and restored after, in matching pairs, by hand.
 * That is exactly the shape of bug a `finally` exists to prevent, and the toolkit had it both ways:
 * `BrassDevLayer` used `try/finally`, while `BrassFrameAnim.push`/`pop` did not - so a throw anywhere
 * beneath a window left the ambient fade stranded below 1 and *every subsequent frame of the session*
 * rendered the whole UI semi-transparent, from an unrelated bug in one widget.
 * Entering a scope through here makes the pairing structural rather than remembered.
 * ```kotlin
 * BrassDrawScope.paused(true) { super.draw(matrixStack) }
 * ```
 */
object BrassDrawScope {

    data class Saved(
        val fade: Float,
        val phase: BrassEntrance.Phase,
        val paused: Boolean,
    )

    fun save(): Saved = Saved(BrassAmbientFade.current, BrassEntrance.phase, BrassStats.paused)

    fun restore(s: Saved) {
        BrassAmbientFade.current = s.fade
        BrassEntrance.phase = s.phase
        BrassStats.paused = s.paused
    }

    inline fun <T> with(
        fade: Float? = null,
        phase: BrassEntrance.Phase? = null,
        paused: Boolean? = null,
        body: () -> T,
    ): T {
        val saved = save()
        fade?.let { BrassAmbientFade.current = saved.fade * it }
        phase?.let { BrassEntrance.phase = it }
        paused?.let { BrassStats.paused = it }
        try {
            return body()
        } finally {
            restore(saved)
        }
    }

    /** Run [body] with instrumentation off - for the dev overlay, which must not measure itself. */
    inline fun <T> paused(paused: Boolean = true, body: () -> T): T = with(paused = paused, body = body)
}
