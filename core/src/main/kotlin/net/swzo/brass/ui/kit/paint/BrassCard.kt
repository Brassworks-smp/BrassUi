package net.swzo.brass.ui.kit.paint

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.paint.BrassCard.dropShadow
import net.swzo.brass.ui.kit.paint.BrassCard.trackShell
import java.awt.Color

/**
 * The card chrome every surface in the toolkit is built from - windows, popups, tables, panels.
 * A card is a drop shadow, a near-black outer ring, a panel fill and a 1-px inner border. Having it
 * in one place is what keeps a table looking like it belongs to the same UI as the window containing
 * it; previously each surface open-coded its own stack of fills and they had drifted apart.
 * ### Stacked headers
 * [header] draws a second card **sharing the body card's top edge**, so the two read as one card
 * resting on another rather than as a strip painted inside a single panel. The shared top edge is
 * the whole trick: the header's ring runs along the same line as the body's, and only its bottom
 * edge and lip separate them.
 */
object BrassCard {

    private val FILL: Color get() = Colors.UI_INNER_BG

    private val HEADER_FILL: Color get() = Colors.UI_BACKGROUND

    private val SHADOW_NEAR: Color get() = Colors.CARD_SHADOW_NEAR
    private val SHADOW_FAR: Color get() = Colors.CARD_SHADOW_FAR

    private fun fill(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) =
        BrassPaint.rect(m, x1, y1, x2, y2, c)

    fun draw(
        m: UMatrixStack,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        shadow: Boolean = true,
        alpha: Float = 1f,
    ) {
        if (x2 <= x1 || y2 <= y1 || alpha <= 0.001f) return

        if (shadow) dropShadow(m, x1, y1, x2, y2, alpha)

        // Filled slab, immediately covered by the panel fill below - see BrassPaint.ringUnder.
        BrassPaint.ringUnder(m, x1, y1, x2, y2, fade(Colors.UI_OUTER_BORDER, alpha))
        fill(m, x1, y1, x2, y2, fade(FILL, alpha))
        border(m, x1, y1, x2, y2, fade(Colors.UI_INNER_BORDER, alpha))
    }

    fun drawInto(
        batch: BrassPaint.QuadBatch,
        x1: Float, y1: Float, x2: Float, y2: Float,
        shadow: Boolean = true,
        alpha: Float = 1f,
        outline: Float = 1f,
    ) {
        if (x2 <= x1 || y2 <= y1 || alpha <= 0.001f) return
        if (shadow) {
            batch.rect(x1 + 3f, y1 + 4f, x2 + 4f, y2 + 5f, fade(SHADOW_FAR, alpha))
            batch.rect(x1 + 1f, y1 + 2f, x2 + 2f, y2 + 3f, fade(SHADOW_NEAR, alpha))
        }
        batch.rect(x1 - 1f, y1 - 1f, x2 + 1f, y2 + 1f, fade(Colors.UI_OUTER_BORDER, alpha * outline))
        batch.rect(x1, y1, x2, y2, fade(FILL, alpha))
        batch.rect(x1, y1, x2, y1 + 1f, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(x1, y2 - 1f, x2, y2, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(x1, y1, x1 + 1f, y2, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(x2 - 1f, y1, x2, y2, fade(Colors.UI_INNER_BORDER, alpha * outline))
    }

    fun dropShadow(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, alpha: Float = 1f) {
        fill(m, x1 + 3f, y1 + 4f, x2 + 4f, y2 + 5f, fade(SHADOW_FAR, alpha))
        fill(m, x1 + 1f, y1 + 2f, x2 + 2f, y2 + 3f, fade(SHADOW_NEAR, alpha))
    }

    private fun fade(c: Color, a: Float): Color = BrassPaint.fade(c, a)

    /** Same rings and fill as [draw], but entirely inside the bounds so a scissor cannot shave the ring. */
    fun panel(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        fill: Color = FILL,
        border: Color = Colors.UI_INNER_BORDER,
        alpha: Float = 1f,
    ) {
        if (x2 <= x1 || y2 <= y1 || alpha <= 0.001f) return
        BrassPaint.border(m, x1, y1, x2, y2, fade(Colors.UI_OUTER_BORDER, alpha))
        val ix1 = x1 + 1f; val iy1 = y1 + 1f; val ix2 = x2 - 1f; val iy2 = y2 - 1f
        if (ix2 <= ix1 || iy2 <= iy1) return
        BrassPaint.rect(m, ix1, iy1, ix2, iy2, fade(fill, alpha))
        this.border(m, ix1, iy1, ix2, iy2, fade(border, alpha))
    }

    fun miniKeycap(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, hot: Boolean) {
        panel(
            m, x1, y1, x2, y2,
            fill = if (hot) Colors.UI_ELEMENT_BG_HOVER else Colors.UI_ELEMENT_BG,
            border = if (hot) Colors.UI_ELEMENT_BORDER_HOVER else Colors.UI_ELEMENT_BORDER,
        )
    }

    fun header(
        m: UMatrixStack,
        x1: Float,
        y1: Float,
        x2: Float,
        height: Float,
        inset: Float = 0f,
        accentSeam: Boolean = true,
        alpha: Float = 1f,
    ) {
        val hx1 = x1 + inset
        val hx2 = x2 - inset
        val hy2 = y1 + height
        if (hx2 <= hx1 || height <= 0f || alpha <= 0.001f) return

        // ring and fill, top edge coincident with the body card's
        fill(m, hx1 - 1f, y1 - 1f, hx2 + 1f, hy2 + 1f, fade(Colors.UI_OUTER_BORDER, alpha))
        fill(m, hx1, y1, hx2, hy2, fade(HEADER_FILL, alpha))
        border(m, hx1, y1, hx2, hy2, fade(Colors.UI_INNER_BORDER, alpha))

        // a lip under the header sells the stack - the body appears to pass beneath it
        fill(m, hx1, hy2, hx2, hy2 + 1f, fade(SHADOW_NEAR, alpha))

        // the brass tell, on the seam where the two cards meet
        if (accentSeam) {
            fill(m, hx1 + 1f, hy2 - 1f, hx1 + minOf(40f, hx2 - hx1 - 2f), hy2, fade(Colors.UI_ACCENT, alpha))
        }
    }

    fun headerInto(
        batch: BrassPaint.QuadBatch,
        x1: Float, y1: Float, x2: Float,
        height: Float,
        inset: Float = 0f,
        accentSeam: Boolean = true,
        alpha: Float = 1f,
        outline: Float = 1f,
    ) {
        val hx1 = x1 + inset
        val hx2 = x2 - inset
        val hy2 = y1 + height
        if (hx2 <= hx1 || height <= 0f || alpha <= 0.001f) return
        batch.rect(hx1 - 1f, y1 - 1f, hx2 + 1f, hy2 + 1f, fade(Colors.UI_OUTER_BORDER, alpha * outline))
        batch.rect(hx1, y1, hx2, hy2, fade(HEADER_FILL, alpha))
        batch.rect(hx1, y1, hx2, y1 + 1f, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(hx1, hy2 - 1f, hx2, hy2, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(hx1, y1, hx1 + 1f, hy2, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(hx2 - 1f, y1, hx2, hy2, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(hx1, hy2, hx2, hy2 + 1f, fade(SHADOW_NEAR, alpha))
        if (accentSeam) {
            batch.rect(hx1 + 1f, hy2 - 1f, hx1 + minOf(40f, hx2 - hx1 - 2f), hy2, fade(Colors.UI_ACCENT, alpha))
        }
    }

    fun border(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) =
        BrassPaint.border(m, x1, y1, x2, y2, c)

    /** Ring-only card with an optional brass [seam]; pass [contained] when a scissor would shave the ring. */
    fun flat(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        fill: Color? = null,
        seam: Color? = null,
        contained: Boolean = false,
    ) {
        if (x2 <= x1 || y2 <= y1) return
        // A hollow ring, not a filled slab. [draw] can use the cheap filled version because its panel
        // fill covers the interior a moment later; here the interior may be a gradient, an image or an
        // entity preview that must survive, and painting over it is how the colour picker's saturation
        // square and hue strip came out solid black.
        if (fill != null) {
            if (contained) {
                // Ring on the edge, fill inset by 1 so the ring survives - the same order as [panel].
                // Filling the full box here (as the bleeding path can, because its ring sits a pixel
                // outside) would paint straight over the near-black ring and leave the card with only
                // its grey inner border, which is exactly how a contained row lost its black outline.
                BrassPaint.border(m, x1, y1, x2, y2, Colors.UI_OUTER_BORDER)
                BrassPaint.rect(m, x1 + 1f, y1 + 1f, x2 - 1f, y2 - 1f, fill)
            } else {
                BrassPaint.ringUnder(m, x1, y1, x2, y2, Colors.UI_OUTER_BORDER)
                BrassPaint.rect(m, x1, y1, x2, y2, fill)
            }
        } else {
            // ringAround already lives outside the bounds; contained draws the ring on the top edge in.
            if (contained) BrassPaint.border(m, x1, y1, x2, y2, Colors.UI_OUTER_BORDER)
            else BrassPaint.ringAround(m, x1, y1, x2, y2, Colors.UI_OUTER_BORDER)
        }
        val inset = if (contained) 1f else 0f
        BrassPaint.border(m, x1 + inset, y1 + inset, x2 - inset, y2 - inset, Colors.UI_INNER_BORDER)
        seam?.let { BrassPaint.rect(m, x1 + inset, y1 + inset, x1 + inset + 1f, y2 - inset, it) }
    }


    fun trackShell(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        track: Color = Colors.UI_ELEMENT_BG,
    ) {
        if (x2 <= x1 || y2 <= y1) return
        fill(m, x1 - 1f, y1 - 1f, x2 + 1f, y2 + 1f, Colors.UI_OUTER_BORDER)
        fill(m, x1, y1, x2, y2, track)
    }

    fun trackBlock(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        bx1: Float, bx2: Float,
        fill: Color = Colors.BRASS_600,
        lit: Color = Colors.BRASS_400,
    ) {
        val ix1 = x1 + 1f
        val iy1 = y1 + 1f
        val ix2 = x2 - 1f
        val iy2 = y2 - 1f
        val l = bx1.coerceIn(ix1, ix2)
        val r = bx2.coerceIn(ix1, ix2)
        if (r <= l + 0.5f || iy2 <= iy1) return
        fill(m, l, iy1, r, iy2, fill)
        border(m, l, iy1, r, iy2, lit)
    }

    /** The card's 1-px border, drawn last so the fill can never spill over it. */
    fun trackBorder(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float) =
        border(m, x1, y1, x2, y2, Colors.UI_INNER_BORDER)

    fun filledTrack(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        fraction: Float,
        fill: Color = Colors.BRASS_600,
        lit: Color = Colors.BRASS_400,
        track: Color = Colors.UI_ELEMENT_BG,
    ) {
        if (x2 <= x1 || y2 <= y1) return
        trackShell(m, x1, y1, x2, y2, track)
        val ix1 = x1 + 1f
        val ix2 = x2 - 1f
        trackBlock(m, x1, y1, x2, y2, ix1, ix1 + (ix2 - ix1) * fraction.coerceIn(0f, 1f), fill, lit)
        trackBorder(m, x1, y1, x2, y2)
    }

    fun grip(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, glow: Float = 0f) {
        if (x2 <= x1 || y2 <= y1) return
        val g = glow.coerceIn(0f, 1f)
        fill(m, x1, y1, x2, y2, Colors.UI_OUTER_BORDER)
        fill(m, x1 + 1f, y1 + 1f, x2 - 1f, y2 - 1f, Colors.mix(GRIP, Colors.BRASS_600, g))
        fill(m, x1 + 1f, y1 + 1f, x2 - 1f, y1 + 2f, Colors.mix(GRIP_EDGE, Colors.BRASS_400, g))
    }

    private val GRIP: Color get() = Colors.GRIP
    private val GRIP_EDGE: Color get() = Colors.GRIP_EDGE
}
