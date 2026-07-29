package net.swzo.brass.ui.kit.input

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassFocusable
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.media.BrassIcons
import java.awt.Color
import kotlin.math.min
import kotlin.math.roundToInt
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A small keycap that shows a single centred pixel [icon] (a
 * [BrassIcons.Icon]). Used for toolbar / window-control buttons (close, minimise, maximise). The icon
 * tints with the animated text colour unless [iconColor] pins it.
 */
class BrassSquareButton(
    var icon: BrassIcons.Icon,
    accent: BrassAccent = BrassAccent.DEFAULT,
    private val iconColor: Color? = null,
    private val onClick: () -> Unit = {},
) : BrassWidget(accent), BrassFocusable {

    /** Fraction of the button's short side the icon occupies. */
    var iconScale = 0.5f

    init {
        clickable = true
        onMouseClick { e -> if (active && e.mouseButton == 0) onClick() }
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val size = (min(w, h) * iconScale).roundToInt().coerceAtLeast(5).toFloat()
        val ix = x + (w - size) / 2f
        val iy = y + (h - size) / 2f
        BrassIcons.draw(m, icon, ix, iy, size, iconColor ?: textColor)
    }

    companion object : BrassDemoSource {

        /** An icon key, at rest and under the pointer. */
        override fun demo() = BrassDemo("square-button", "Square button", 22f, 22f) {
            BrassSquareButton(BrassIcons.CHECK)
        }
    }
}
