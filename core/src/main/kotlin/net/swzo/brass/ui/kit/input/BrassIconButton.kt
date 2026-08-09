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
 * A keycap with a leading pixel [icon] and a label beside it. The icon sits at
 * the left padding; the label follows, optionally centred in the remaining space.
 */
class BrassIconButton(
    var label: String,
    var icon: BrassIcons.Icon,
    accent: BrassAccent = BrassAccent.DEFAULT,
    private val onClick: () -> Unit = {},
) : BrassWidget(accent), BrassFocusable {

    var iconSize = 8
    var iconPadding = 5
    var centered = false

    init {
        clickable = true
        onMouseClick { e -> if (active && e.mouseButton == 0) onClick() }
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val ix = x + iconPadding
        val iy = y + (h - iconSize) / 2
        BrassIcons.draw(m, icon, ix.toFloat(), iy.toFloat(), iconSize.toFloat(), textColor)

        val textStart = x + iconPadding + iconSize + iconPadding
        val remaining = w - (iconSize + iconPadding * 3)
        // fit the label to the space left beside the icon, so it stops short of the keycap edge
        val show = BrassFont.fit(this, label, remaining.toFloat())
        val tw = BrassFont.width(this, show)
        val tx = if (centered) textStart + ((remaining - tw) / 2f).roundToInt() else textStart
        val ty = y + (h - BrassFont.LINE) / 2 + 1
        BrassFont.draw(m, this, show, tx.toFloat(), ty.toFloat(), textColor, true)
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("icon-button", "Icon button", 100f, 20f) {
            BrassIconButton("Settings", BrassIcons.GEAR)
        }
    }
}
