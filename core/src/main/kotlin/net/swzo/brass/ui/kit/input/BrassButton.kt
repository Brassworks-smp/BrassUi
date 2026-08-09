@file:Suppress("unused")
package net.swzo.brass.ui.kit.input

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassFocusable
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.text.BrassFont
import kotlin.math.roundToInt
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A keycap [BrassWidget] with a centred label that inherits all the base
 * animation (hover-lift, animated colours, entrance). Give it an [accent] for a coloured call-to-action.
 */
open class BrassButton(
    var label: String,
    accent: BrassAccent = BrassAccent.DEFAULT,
    private val onClick: () -> Unit = {},
) : BrassWidget(accent), BrassFocusable {

    var centered = true

    init {
        clickable = true
        onMouseClick { e -> if (active && e.mouseButton == 0) onClick() }
    }

    override fun proxyActivate() { if (active) onClick() }

    fun labelWidth(): Float = BrassFont.width(this, label) + 14f

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        // ellipsise to the keycap's inner width so a long label never paints outside the button
        val show = BrassFont.fit(this, label, (w - 8).toFloat())
        val tw = BrassFont.width(this, show)
        val tx = if (centered) x + ((w - tw) / 2f).roundToInt() else x + 4
        val ty = y + (h - BrassFont.LINE) / 2 + 1
        BrassFont.draw(m, this, show, tx.toFloat(), ty.toFloat(), textColor, true)
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("button", "Button", 130f, 20f) {
            BrassButton("Primary action", BrassAccent.BRASS)
        }
    }
}
