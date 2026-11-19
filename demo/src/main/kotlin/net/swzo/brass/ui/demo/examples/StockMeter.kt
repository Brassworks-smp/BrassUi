package net.swzo.brass.ui.demo.examples

import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassEased
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.paint.BrassPaint

class StockMeter(var fraction: () -> Float) : BrassWidget() {

    private val shown = BrassEased(fraction().coerceIn(0f, 1f), speed = 10f)

    init {
        chrome = BrassChrome.NONE
        constrain { height = 9.pixels() }
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        shown.target = fraction().coerceIn(0f, 1f)
        val f = shown.advance()
        val bars = BARS
        val gap = 1
        val bw = ((w - gap * (bars - 1)) / bars).coerceAtLeast(1)
        val lit = Math.round(f * bars)
        val color = when {
            f < 0.15f -> Colors.DANGER
            f < 0.4f -> Colors.WARN
            else -> Colors.UI_ACCENT
        }
        for (i in 0 until bars) {
            val bx = x + i * (bw + gap)
            val c = if (i < lit) color else Colors.UI_INNER_BORDER
            BrassPaint.rect(m, bx, y, bx + bw, y + h, c)
        }
    }

    companion object : BrassDemoSource {
        private const val BARS = 12

        override fun demo() = BrassDemo("stock-meter", "Stock meter", 120f, 9f) { StockMeter { 0.6f } }
    }
}
