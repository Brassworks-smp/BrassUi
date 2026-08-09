package net.swzo.brass.ui.kit.paint

import gg.essential.elementa.components.UIBlock
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.base.BrassStats
import java.awt.Color
import kotlin.math.roundToInt

/**
 * The toolkit's only route to a filled quad.
 * ### Why this exists
 * Eight components had each grown a private `fill(m, x1, y1, x2, y2, c)` doing the same three
 * things - reject a degenerate rect, count the quad, apply the ambient frame fade, submit - and they
 * disagreed about which of the three to remember. [net.swzo.brass.ui.kit.text.BrassMarkdown] and
 * `BrassDropdown.Card` both skipped [BrassStats.quad], so a screenful of markdown or an open dropdown
 * was simply absent from the quad counter the dev overlay reported: the number was wrong and there
 * was no way to notice from inside either file.
 * Routing every quad through here makes the three steps unforgettable rather than merely
 * conventional. Nothing in the toolkit should call [UIBlock.drawBlock] directly.
 */
object BrassPaint {

    fun rect(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) {
        if (x2 <= x1 || y2 <= y1) return
        BrassStats.quad()
        UIBlock.drawBlock(
            m, BrassAmbientFade.apply(c),
            x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble(),
        )
    }

    fun rect(m: UMatrixStack, x1: Int, y1: Int, x2: Int, y2: Int, c: Color) =
        rect(m, x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), c)

    fun rectSnapped(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) =
        rect(m, x1.roundToInt(), y1.roundToInt(), x2.roundToInt(), y2.roundToInt(), c)

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

    fun ringUnder(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color, thickness: Float = 1f) =
        rect(m, x1 - thickness, y1 - thickness, x2 + thickness, y2 + thickness, c)

    /**
     * A hollow ring of [thickness] drawn **outside** the given bounds, leaving the interior untouched.
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

    fun fade(c: Color, a: Float): Color =
        if (a >= 1f) c
        else Color(c.red, c.green, c.blue, (c.alpha * a.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255))

    class QuadBatch(private val m: UMatrixStack) {
        private val g = UGraphics.getFromTessellator()
        private var open = false

        fun rect(x1: Float, y1: Float, x2: Float, y2: Float, c: Color) {
            if (x2 <= x1 || y2 <= y1) return
            if (!open) {
                @Suppress("DEPRECATION")
                UGraphics.enableBlend()
                @Suppress("DEPRECATION")
                g.beginWithDefaultShader(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR)
                open = true
            }
            BrassStats.quad()
            val col = BrassAmbientFade.apply(c)
            g.pos(m, x1.toDouble(), y2.toDouble(), 0.0).color(col).endVertex()
            g.pos(m, x2.toDouble(), y2.toDouble(), 0.0).color(col).endVertex()
            g.pos(m, x2.toDouble(), y1.toDouble(), 0.0).color(col).endVertex()
            g.pos(m, x1.toDouble(), y1.toDouble(), 0.0).color(col).endVertex()
        }

        fun quad(
            x1: Float, y1: Float, x2: Float, y2: Float,
            x3: Float, y3: Float, x4: Float, y4: Float,
            c: Color,
        ) {
            if (!open) {
                @Suppress("DEPRECATION")
                UGraphics.enableBlend()
                @Suppress("DEPRECATION")
                g.beginWithDefaultShader(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR)
                open = true
            }
            BrassStats.quad()
            val col = BrassAmbientFade.apply(c)
            g.pos(m, x1.toDouble(), y1.toDouble(), 0.0).color(col).endVertex()
            g.pos(m, x2.toDouble(), y2.toDouble(), 0.0).color(col).endVertex()
            g.pos(m, x3.toDouble(), y3.toDouble(), 0.0).color(col).endVertex()
            g.pos(m, x4.toDouble(), y4.toDouble(), 0.0).color(col).endVertex()
        }

        fun flush() {
            if (!open) return
            g.drawDirect()
            @Suppress("DEPRECATION")
            UGraphics.disableBlend()
            open = false
        }
    }
}
