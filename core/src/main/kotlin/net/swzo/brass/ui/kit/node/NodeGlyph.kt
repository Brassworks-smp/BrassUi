package net.swzo.brass.ui.kit.node

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.paint.BrassPaint
import java.awt.Color

/**
 * Tiny pixel glyphs the node editor stamps in world units - a triangular arrow and a collapse chevron.
 * Drawn as stepped 1-px rows so they stay pixel-crisp and scale with the canvas' zoom like everything
 * else (they are painted under the same scaled matrix).
 */
object NodeGlyph {

    fun arrow(m: UMatrixStack, cx: Float, cy: Float, left: Boolean, color: Color = Colors.UI_TEXT_DARK, size: Float = 2.5f) {
        var k = 0
        while (k <= size) {
            val yTop = cy - size + k
            val yBot = cy + size - k
            if (left) BrassPaint.rect(m, cx + k, yTop, cx + k + 1f, yBot, color)
            else BrassPaint.rect(m, cx - k, yTop, cx - k + 1f, yBot, color)
            k++
        }
    }

    fun chevron(m: UMatrixStack, cx: Float, cy: Float, open: Boolean, color: Color, size: Float = 2.5f) {
        var k = 0
        while (k <= size) {
            if (open) {
                BrassPaint.rect(m, cx - size + k, cy - size + k, cx + size - k, cy - size + k + 1f, color)
            } else {
                BrassPaint.rect(m, cx - size + k, cy - size + k, cx - size + k + 1f, cy + size - k, color)
            }
            k++
        }
    }
}
