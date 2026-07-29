package net.swzo.brass.ui

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import java.awt.Color

/**
 * The core brassui primitive: a **rounded flat rect** with a thin **concentric border**. The border
 * silhouette is drawn as a rounded rect, then the fill inset 1 px inside it, which is what gives the
 * control its crisp seated edge. An optional [outerBorder]
 * adds a second, outer 1-px ring (the near-black `globalOuterBorder`) for panels and the window chrome,
 * and an optional [shadow] drops a soft rounded shadow down-right so a floating surface reads as lifted.
 *
 * Everything is drawn inside the component's own bounds (both borders inset inward, the shadow reserving
 * the bottom-right pixel) - nothing overruns the layout box, matching how the old flat primitive behaved.
 * Rounded fills are rendered with Elementa's [UIRoundedRectangle] shader; sub-pixel radii fall back to a
 * plain [UIBlock] so hairlines stay crisp.
 */
open class BrassBlock(
    /** The flat fill colour. */
    var fill: Color = Colors.UI_ELEMENT_BG,
    /** The 1-px inner border; a transparent colour draws none. */
    var border: Color? = Colors.UI_ELEMENT_BORDER,
    /** Corner radius, in pixels. */
    var cornerRadius: Float = Colors.RADIUS,
    /** An optional outer 1-px ring outside the border - for panels. */
    var outerBorder: Color? = null,
    /** Drop a soft rounded shadow down-right, reserving the bottom-right pixel of the bounds. */
    var shadow: Boolean = false,
) : UIComponent() {

    override fun draw(matrixStack: UMatrixStack) {
        beforeDraw(matrixStack)

        val x1 = getLeft()
        val y1 = getTop()
        val x2 = getRight()
        val y2 = getBottom()

        val bx2 = if (shadow) x2 - 1f else x2
        val by2 = if (shadow) y2 - 1f else y2

        if (shadow) roundedRect(matrixStack, SHADOW, x1 + 1f, y1 + 1f, x2, y2, cornerRadius)
        if (bx2 > x1 && by2 > y1) {
            drawBox(matrixStack, x1, y1, bx2, by2, cornerRadius, fill, border, outerBorder)
        }

        super.draw(matrixStack)
    }

    companion object {
        /** The drop-shadow tone - a translucent black. */
        val SHADOW: Color get() = Colors.SHADOW

        /**
         * Drawn inward so it stays inside `[x1,y1]..[x2,y2]`:
         * the outer ring (if any) at the bounds edge, the inner border inset 1 px, then the fill inset
         * again. Radius shrinks by 1 px per ring so the corners stay concentric.
         */
        fun drawBox(
            m: UMatrixStack,
            x1: Float, y1: Float, x2: Float, y2: Float,
            radius: Float,
            fill: Color, border: Color?, outer: Color?,
        ) {
            var ax1 = x1; var ay1 = y1; var ax2 = x2; var ay2 = y2; var r = radius
            if (outer != null && outer.alpha > 0) {
                roundedRect(m, outer, ax1, ay1, ax2, ay2, r)
                ax1 += 1f; ay1 += 1f; ax2 -= 1f; ay2 -= 1f; r = (r - 1f).coerceAtLeast(0f)
            }
            if (border != null && border.alpha > 0) {
                roundedRect(m, border, ax1, ay1, ax2, ay2, r)
                ax1 += 1f; ay1 += 1f; ax2 -= 1f; ay2 -= 1f; r = (r - 1f).coerceAtLeast(0f)
            }
            roundedRect(m, fill, ax1, ay1, ax2, ay2, r)
        }

        /** Fill a rounded rect `[x1,y1]..[x2,y2]` (radius clamped to half the shorter side). */
        fun roundedRect(m: UMatrixStack, c: Color, x1: Float, y1: Float, x2: Float, y2: Float, radius: Float) {
            if (x2 <= x1 || y2 <= y1) return
            val rr = minOf(radius, (x2 - x1) / 2f, (y2 - y1) / 2f).coerceAtLeast(0f)
            if (rr <= 0.5f) {
                UIBlock.drawBlock(m, BrassAmbientFade.apply(c), x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble())
            } else {
                UIRoundedRectangle.drawRoundedRectangle(m, x1, y1, x2, y2, rr, c)
            }
        }

        /** Lighten toward white by [t] (0..1). */
        fun lighten(c: Color, t: Float): Color {
            fun ch(v: Int) = (v + (255 - v) * t).toInt().coerceIn(0, 255)
            return Color(ch(c.red), ch(c.green), ch(c.blue), c.alpha)
        }

        /** Darken toward black by [t] (0..1). */
        fun darken(c: Color, t: Float): Color {
            fun ch(v: Int) = (v * (1f - t)).toInt().coerceIn(0, 255)
            return Color(ch(c.red), ch(c.green), ch(c.blue), c.alpha)
        }
    }
}
