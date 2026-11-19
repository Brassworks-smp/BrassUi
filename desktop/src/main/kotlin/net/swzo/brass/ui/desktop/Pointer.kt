package net.swzo.brass.ui.desktop

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.image.BufferedImage

internal object Pointer {
    fun draw(img: BufferedImage, px: Float, py: Float, scale: Float, pressed: Boolean, flash: Float) {
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        if (flash > 0f) {
            val r = (4f + (1f - flash) * 6f) * scale
            g.color = Color(255, 255, 255, (flash * 160).toInt().coerceIn(0, 255))
            g.stroke = BasicStroke(scale)
            g.drawOval((px - r).toInt(), (py - r).toInt(), (2 * r).toInt(), (2 * r).toInt())
        }
        val xs = intArrayOf(0, 0, 3, 5, 7, 5, 8).map { (px + it * scale * 1.1f).toInt() }.toIntArray()
        val ys = intArrayOf(0, 11, 8, 12, 11, 7, 7).map { (py + it * scale * 1.1f).toInt() }.toIntArray()
        val arrow = Polygon(xs, ys, xs.size)
        g.color = if (pressed) Color(215, 215, 215) else Color.WHITE
        g.fillPolygon(arrow)
        g.color = Color.BLACK
        g.stroke = BasicStroke(maxOf(1f, scale * 0.8f))
        g.drawPolygon(arrow)
        g.dispose()
    }
}
