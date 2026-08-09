package net.swzo.brass.ui.kit.media

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.platform.BrassPlatform
import net.swzo.brass.ui.kit.text.BrassFont

/**
 * The base for every widget whose picture comes from the game rather than from the toolkit -
 * [BrassItem], [BrassEntity], [BrassPlayerHead], [BrassBlockPreview], [BrassEffectIcon] and
 * [BrassCanvas].
 * ### Why this exists
 * All six repeat the same six lines, and getting any of them subtly wrong is invisible until a window
 * closes:
 * ```kotlin
 * val fade = BrassAmbientFade.earlyOut(entranceFade)
 * val hidden = fade <= 0f
 * val drawn = hidden || (BrassPlatform.current?.let { runCatching { … }.getOrDefault(false) } ?: false)
 * if (!drawn) { … placeholder … }
 * ```
 * Three details are load-bearing and were re-explained in a comment on each copy:
 * - **[BrassAmbientFade.earlyOut]**, because vanilla fades by *darkening* rather than dissolving, so
 *   game content finishes fading halfway through a closing frame and is dropped from there.
 * - **`hidden` counting as drawn**, so a widget that is merely invisible does not flash its "nothing
 *   here" placeholder as it fades away - which reads as a bug precisely when the user is least able
 *   to tell what happened.
 * - **[runCatching]**, because a broken model or a bad id must not take the whole screen down.
 * Subclasses implement [paintNative] - draw, and say whether it worked - and optionally
 * [paintPlaceholder] for something other than centred grey text.
 */
abstract class BrassPlatformVisual(accent: BrassAccent = BrassAccent.DEFAULT) : BrassWidget(accent) {

    var placeholder: String = "unavailable"

    protected abstract fun paintNative(
        m: UMatrixStack,
        platform: BrassPlatform,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        fade: Float,
    ): Boolean

    protected open fun paintPlaceholder(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val label = BrassFont.fit(this, placeholder, (w - 4).toFloat())
        BrassFont.draw(
            m, this, label,
            x + (w - BrassFont.width(this, label)) / 2f,
            y + (h - BrassFont.LINE) / 2f,
            Colors.UI_TEXT_DARK,
        )
    }

    protected open fun contentBox(x: Int, y: Int, w: Int, h: Int): FloatArray =
        floatArrayOf(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())

    final override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val fade = BrassAmbientFade.earlyOut(entranceFade)
        val hidden = fade <= 0f
        val box = contentBox(x, y, w, h)

        val drawn = hidden || box[2] <= 0f || box[3] <= 0f || run {
            val platform = BrassPlatform.current ?: return@run false
            runCatching { paintNative(m, platform, box[0], box[1], box[2], box[3], fade) }
                .getOrDefault(false)
        }

        if (!drawn) paintPlaceholder(m, x, y, w, h)
        decorate(m, x, y, w, h, fade, drawn)
    }

    protected open fun decorate(
        m: UMatrixStack,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        fade: Float,
        drawn: Boolean,
    ) {}
}
