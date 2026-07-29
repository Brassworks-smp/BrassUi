package net.swzo.brass.ui.kit.paint

import gg.essential.elementa.components.UIBlock
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.base.BrassStats
import net.swzo.brass.ui.kit.paint.BrassPaint.rect
import net.swzo.brass.ui.kit.paint.BrassPaint.ringAround
import java.awt.Color
import kotlin.math.roundToInt

/**
 * The toolkit's only route to a filled quad.
 *
 * ### Why this exists
 *
 * Eight components had each grown a private `fill(m, x1, y1, x2, y2, c)` doing the same three
 * things - reject a degenerate rect, count the quad, apply the ambient frame fade, submit - and they
 * disagreed about which of the three to remember. [net.swzo.brass.ui.kit.text.BrassMarkdown] and
 * `BrassDropdown.Card` both skipped [BrassStats.quad], so a screenful of markdown or an open dropdown
 * was simply absent from the quad counter the dev overlay reported: the number was wrong and there
 * was no way to notice from inside either file.
 *
 * Routing every quad through here makes the three steps unforgettable rather than merely
 * conventional. Nothing in the toolkit should call [UIBlock.drawBlock] directly.
 */
object BrassPaint {

    /**
     * Fill `[x1,y1]..[x2,y2]`. Degenerate and inverted rectangles are dropped rather than submitted,
     * which is what lets callers do arithmetic on their bounds without guarding every result.
     */
    fun rect(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) {
        if (x2 <= x1 || y2 <= y1) return
        BrassStats.quad()
        UIBlock.drawBlock(
            m, BrassAmbientFade.apply(c),
            x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble(),
        )
    }

    /** [rect] with integer bounds - for callers already working in whole pixels. */
    fun rect(m: UMatrixStack, x1: Int, y1: Int, x2: Int, y2: Int, c: Color) =
        rect(m, x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), c)

    /** [rect] with its edges snapped to whole pixels, so a fill lands on the pixel grid. */
    fun rectSnapped(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) =
        rect(m, x1.roundToInt(), y1.roundToInt(), x2.roundToInt(), y2.roundToInt(), c)

    /**
     * A border of [thickness] drawn **inside** `[x1,y1]..[x2,y2]`.
     *
     * The top and bottom runs span the full width and the sides sit between them, so no pixel is
     * painted twice. That matters for translucent colours, where a doubled corner blends twice and
     * shows up as four dark dots framing the shape.
     */
    fun border(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        c: Color,
        thickness: Float = 1f,
    ) {
        if (x2 <= x1 || y2 <= y1) return
        val t = thickness.coerceAtMost(minOf(x2 - x1, y2 - y1) / 2f)
        rect(m, x1, y1, x2, y1 + t, c)
        rect(m, x1, y2 - t, x2, y2, c)
        rect(m, x1, y1 + t, x1 + t, y2 - t, c)
        rect(m, x2 - t, y1 + t, x2, y2 - t, c)
    }

    /**
     * A **filled** slab extending [thickness] beyond the given bounds.
     *
     * This is the cheap way to seat a card: paint one oversized rectangle in the ring colour and then
     * paint the card's fill on top, leaving only the border visible. It is only correct when
     * something opaque covers the interior afterwards.
     *
     * Named `ringUnder`, not `ring`, because the previous name promised an outline and delivered a
     * solid block - and the one caller that did *not* paint a fill over it (a flat card frame) came
     * out as a solid near-black rectangle, which is what turned the colour picker's gradients black.
     * Use [ringAround] when there is no fill.
     */
    fun ringUnder(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color, thickness: Float = 1f) =
        rect(m, x1 - thickness, y1 - thickness, x2 + thickness, y2 + thickness, c)

    /**
     * A hollow ring of [thickness] drawn **outside** the given bounds, leaving the interior untouched.
     *
     * For a frame over content that must stay visible - a gradient, an image, an entity preview.
     */
    fun ringAround(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color, thickness: Float = 1f) =
        band(
            m,
            x1 - thickness, y1 - thickness, x2 + thickness, y2 + thickness,
            x1, y1, x2, y2,
            c,
        )

    /**
     * Fill the four bands between an outer and an inner rectangle - a box-model band, and the shape
     * an inset border makes when the interior must be left untouched.
     */
    fun band(
        m: UMatrixStack,
        ox1: Float, oy1: Float, ox2: Float, oy2: Float,
        ix1: Float, iy1: Float, ix2: Float, iy2: Float,
        c: Color,
    ) {
        rect(m, ox1, oy1, ox2, iy1, c)
        rect(m, ox1, iy2, ox2, oy2, c)
        rect(m, ox1, iy1, ix1, iy2, c)
        rect(m, ix2, iy1, ox2, iy2, c)
    }

    /** [c] with its alpha scaled by [a]. Returns [c] itself at full alpha, so the common case is free. */
    fun fade(c: Color, a: Float): Color =
        if (a >= 1f) c
        else Color(c.red, c.green, c.blue, (c.alpha * a.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255))
}
