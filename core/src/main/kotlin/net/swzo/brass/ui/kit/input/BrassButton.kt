@file:Suppress("unused")
package net.swzo.brass.ui.kit.input

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassFocusable
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.media.BrassIcons
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

    /** A pixel icon before the label (a nav rail's sections, say); none by default. */
    var icon: BrassIcons.Icon? = null

    init {
        clickable = true
        onMouseClick { e -> if (active && e.mouseButton == 0) onClick() }
    }

    override fun proxyActivate() { if (active) onClick() }

    fun labelWidth(): Float = BrassFont.width(this, label) + 14f + if (icon != null) (ICON + ICON_GAP).toFloat() else 0f

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val ic = icon
        var left = x
        var room = w
        if (ic != null) {
            BrassIcons.draw(m, ic, (x + 4).toFloat(), (y + (h - ICON) / 2).toFloat(), ICON.toFloat(), textColor)
            left = x + ICON + ICON_GAP
            room = w - (ICON + ICON_GAP)
        }
        // ellipsise to the keycap's inner width so a long label never paints outside the button
        val show = BrassFont.fit(this, label, (room - 8).toFloat())
        val tw = BrassFont.width(this, show)
        val tx = if (centered) left + ((room - tw) / 2f).roundToInt() else left + 4
        val ty = y + (h - BrassFont.LINE) / 2 + 1
        BrassFont.draw(m, this, show, tx.toFloat(), ty.toFloat(), textColor, true)
    }

    companion object : BrassDemoSource {
        private const val ICON = 8
        private const val ICON_GAP = 5

        override fun demo() = BrassDemo("button", "Button", 130f, 20f) {
            BrassButton("Primary action", BrassAccent.BRASS)
        }
    }
}
