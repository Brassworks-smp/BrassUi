package net.swzo.brass.ui.kit.dev

import gg.essential.elementa.components.UIContainer
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassDrawScope

/**
 * The root the dev overlay hangs from. Its job is to **pause [BrassStats] around its own subtree's
 * draw**, so the panel, cards and tree rows the inspector is built from cost nothing in the numbers the
 * inspector reports. Docked to the right edge only while dev mode is on.
 */
class BrassDevLayer : UIContainer(), BrassDevOverlay {
    override fun draw(matrixStack: UMatrixStack) {
        BrassDrawScope.paused { super.draw(matrixStack) }
    }
}
