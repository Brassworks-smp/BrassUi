package net.swzo.brass.ui

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.layout.BrassSpacing
import net.swzo.brass.ui.kit.surface.BrassWindow
import java.awt.Color

open class BrassAppScreen @JvmOverloads constructor(
    title: String,
    subtitle: String? = null,
    val fit: Fit = Fit.FLOATING,
    private val maxWidth: Float = 520f,
    private val maxHeight: Float = 340f,
    backdrop: Color? = null,
) : BrassScreen(backdrop) {

    enum class Fit { FLOATING, FILL }

    val frame: BrassWindow = BrassWindow(
        title = title,
        subtitle = subtitle,
        onClose = { onCloseRequested() },
        controls = fit == Fit.FLOATING,
        minW = 200f,
        minH = 140f,
    )

    val body: UIContainer

    init {
        if (fit == Fit.FILL) {
            frame.constrain {
                x = 0.pixels(); y = 0.pixels()
                width = 100.percent(); height = 100.percent()
            } childOf background
        } else {
            frame.constrain {
                x = CenterConstraint(); y = CenterConstraint()
                width = basicWidthConstraint { c -> maxWidth.coerceAtMost(c.parent.getWidth() - MARGIN * 2).coerceAtLeast(0f) }
                height = basicHeightConstraint { c -> maxHeight.coerceAtMost(c.parent.getHeight() - MARGIN * 2).coerceAtLeast(0f) }
            } childOf background
        }

        body = UIContainer().constrain {
            x = BrassSpacing.PAD.pixels(); y = BrassSpacing.PAD.pixels()
            width = 100.percent() - (BrassSpacing.PAD * 2).pixels()
            height = 100.percent() - (BrassSpacing.PAD * 2).pixels()
        } childOf frame.content
    }

    fun resizeWindow(w: Float, h: Float) {
        if (fit == Fit.FILL) return
        frame.constrain {
            x = CenterConstraint(); y = CenterConstraint()
            width = basicWidthConstraint { c -> w.coerceAtMost(c.parent.getWidth() - MARGIN * 2).coerceAtLeast(0f) }
            height = basicHeightConstraint { c -> h.coerceAtMost(c.parent.getHeight() - MARGIN * 2).coerceAtLeast(0f) }
        }
    }

    protected open fun onCloseRequested() {
        goBack()
    }

    companion object {
        const val MARGIN = 16f
    }
}
