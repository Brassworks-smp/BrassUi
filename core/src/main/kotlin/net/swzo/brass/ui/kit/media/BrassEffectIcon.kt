package net.swzo.brass.ui.kit.media

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.platform.BrassPlatform
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.text.BrassFont
import kotlin.math.min
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A status effect: the game's own sprite, with a ring around it counting the remaining duration down.
 *
 * ```kotlin
 * BrassEffectIcon("minecraft:speed", amplifier = 1)
 *     .apply { setRemaining(seconds = 42f, of = 180f) }
 * ```
 *
 * The **sprite** comes from the platform; the **ring** does not. Vanilla has no such ring - it prints
 * a countdown in text - and this is toolkit chrome, so it is drawn here with the toolkit's colours
 * rather than pushed across the platform seam where it would have to be reimplemented per loader.
 *
 * The ring is drawn as four straight runs around the border rather than an arc: at 18 pixels an arc
 * is aliased into something that reads as noise, while a border that empties anticlockwise is legible
 * at any size and costs four quads.
 */
class BrassEffectIcon(
    var effectId: String,
    /** Effect level, drawn as a small numeral when above 1 (`II`, `III` in vanilla's terms). */
    var amplifier: Int = 0,
    tooltip: Boolean = true,
) : BrassPlatformVisual(BrassAccent.DEFAULT) {

    /** Remaining fraction of the effect's duration, 0..1. Negative hides the ring entirely. */
    var remaining: Float = -1f

    /** Whether the effect is fading out - vanilla blinks these; here the ring simply dims. */
    var expiring: Boolean = false

    init {
        placeholder = "?"
        if (tooltip) {
            BrassTooltip.attachLazy(
                this,
                title = { BrassPlatform.current?.effectName(effectId) ?: effectId },
                body = { if (remaining >= 0f) "${(remaining * 100).toInt()}% remaining" else null },
            )
        }
    }

    /** Set [remaining] from a duration in whatever unit, against the effect's full length. */
    fun setRemaining(seconds: Float, of: Float) {
        remaining = if (of <= 0f) -1f else (seconds / of).coerceIn(0f, 1f)
        expiring = remaining in 0f..0.15f
    }

    /** Square, centred, leaving room for the ring outside the sprite. */
    override fun contentBox(x: Int, y: Int, w: Int, h: Int): FloatArray {
        val size = (min(w, h) - (INSET + RING) * 2).coerceAtLeast(1f)
        return floatArrayOf(x + (w - size) / 2f, y + (h - size) / 2f, size, size)
    }

    override fun paintNative(
        m: UMatrixStack,
        platform: BrassPlatform,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        fade: Float,
    ): Boolean = platform.drawEffectIcon(m, effectId, x, y, w, fade)

    override fun decorate(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int, fade: Float, drawn: Boolean) {
        if (remaining >= 0f) drawRing(m, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), fade)
        if (amplifier > 0) drawAmplifier(m, x, y, w, h, fade)
    }

    /**
     * The duration ring: a border that empties clockwise from the top, drawn as four straight runs.
     *
     * Perimeter position 0 is the top-centre, and the [remaining] fraction of the perimeter is
     * painted. Each side is clipped against the covered span, so a run that the sweep only partially
     * reaches is drawn partially - which is what makes it move smoothly rather than a side at a time.
     */
    private fun drawRing(m: UMatrixStack, x: Float, y: Float, w: Float, h: Float, fade: Float) {
        val x1 = x + INSET; val y1 = y + INSET
        val x2 = x + w - INSET; val y2 = y + h - INSET
        val bw = x2 - x1; val bh = y2 - y1
        if (bw <= 0f || bh <= 0f) return

        val perimeter = (bw + bh) * 2f
        val lit = perimeter * remaining.coerceIn(0f, 1f)
        val tint = BrassPaint.fade(if (expiring) Colors.DANGER else Colors.UI_ACCENT, fade)

        // Runs in sweep order from the top-centre, each as (length, painter of a 0..1 portion).
        var travelled = 0f
        fun sweep(length: Float, paint: (Float) -> Unit) {
            if (length <= 0f) return
            val covered = (lit - travelled).coerceIn(0f, length)
            if (covered > 0f) paint(covered / length)
            travelled += length
        }

        val halfTop = bw / 2f
        sweep(halfTop) { f -> BrassPaint.rect(m, x1 + halfTop, y1, x1 + halfTop + halfTop * f, y1 + RING, tint) }
        sweep(bh) { f -> BrassPaint.rect(m, x2 - RING, y1, x2, y1 + bh * f, tint) }
        sweep(bw) { f -> BrassPaint.rect(m, x2 - bw * f, y2 - RING, x2, y2, tint) }
        sweep(bh) { f -> BrassPaint.rect(m, x1, y2 - bh * f, x1 + RING, y2, tint) }
        sweep(halfTop) { f -> BrassPaint.rect(m, x1, y1, x1 + halfTop * f, y1 + RING, tint) }
    }

    private fun drawAmplifier(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int, fade: Float) {
        val label = ROMAN.getOrNull(amplifier) ?: (amplifier + 1).toString()
        val tw = BrassFont.width(this, label)
        BrassFont.draw(
            m, this, label,
            x + w - tw - INSET - RING, y + h - BrassFont.LINE - INSET - RING,
            BrassPaint.fade(Colors.UI_TEXT_HOVER, fade),
        )
    }

    companion object : BrassDemoSource {

        /** A status effect with its level numeral. Renders off-world, like the item slot. */
        override fun demo() = BrassDemo("effect-icon", "Effect icon", 24f, 24f) {
            BrassEffectIcon("minecraft:speed", amplifier = 1)
        }

        // ---- widget internals ------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val INSET = 1f
        /** Thickness of the duration ring. */
        private const val RING = 1f

        /** Amplifier 0 is level I; vanilla writes these as numerals, so this does too. */
        private val ROMAN = arrayOf("", "II", "III", "IV", "V")
    }
}
