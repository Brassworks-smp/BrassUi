package net.swzo.brass.ui.kit.paint

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import java.awt.Color

/**
 * A raised **colour chip**, the same keycap the accent swatches in the appearance card wear - a flat
 * fill of the colour, a lighter top-lit inner border, a near-black outer ring and a darker coloured lip
 * - so a colour shown inside a node reads as the identical control the theme picker uses rather than a
 * flat painted rectangle.
 * Being a painter (not a widget) is what lets a node's inline [net.swzo.brass.ui.kit.node.ColorField]
 * draw one under the canvas' zoom, the same way the port nubs draw their keycaps. A translucent colour
 * gets a checkerboard behind it so its alpha reads honestly rather than muddying against the panel.
 */
object BrassSwatch {

    fun draw(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, color: Color, hot: Float = 0f, lip: Float = 0f) {
        if (x2 <= x1 || y2 <= y1) return
        if (color.alpha < 255) checker(m, x1, y1, x2, y2)
        val border = Colors.mix(Colors.mix(color, Color.WHITE, 0.3f), Colors.UI_ACCENT_BRIGHT, hot * 0.6f)
        val bottom = Colors.mix(color, Color.BLACK, 0.45f)
        BrassKeycap.draw(
            m, x1, y1, x2 - x1, y2 - y1,
            bg = color, border = border, outer = Colors.UI_OUTER_BORDER, bottom = bottom,
            flat = false, defaultAccent = false, lip = lip,
        )
    }

    private fun checker(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float) {
        val s = 3f
        BrassPaint.rect(m, x1, y1, x2, y2, LIGHT)
        var yy = y1; var row = 0
        while (yy < y2) {
            var xx = x1 + if (row % 2 == 0) 0f else s
            while (xx < x2) {
                BrassPaint.rect(m, xx, yy, minOf(xx + s, x2), minOf(yy + s, y2), DARK)
                xx += s * 2f
            }
            yy += s; row++
        }
    }

    private val LIGHT: Color get() = Colors.mix(Colors.UI_ELEMENT_BG, Color.WHITE, 0.25f)
    private val DARK: Color get() = Colors.UI_ELEMENT_BG
}
