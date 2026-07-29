package net.swzo.brass.ui.kit.base

import gg.essential.elementa.components.UIContainer
import gg.essential.universal.UMatrixStack

/**
 * A [UIContainer] that paints something of its own.
 *
 * ### Why this exists
 *
 * Elementa's `UIContainer.draw` already calls `beforeDraw`, and calling it again throws
 * *"called `beforeDraw` more than once without a call to `draw`"* - **every frame**. It is the single
 * most-repeated trap in the toolkit: the identical explanatory comment appears in `BrassFlow`,
 * `BrassResize`, `BrassScrollbar` and twice in `BrassDropdown`, because a subclass overriding `draw`
 * has no way to know which base classes call it and which do not.
 *
 * Overriding [paint] instead removes the choice. The container calls `super.draw` at the right moment
 * and hands the subclass a hook that is unambiguously safe.
 */
abstract class BrassContainer : UIContainer() {

    /**
     * Paint this container. Called with the matrix ready, **before** the children draw.
     *
     * Do not call `beforeDraw` here - see the class docs for what that costs.
     */
    protected open fun paint(matrixStack: UMatrixStack) {}

    /** Painted after the children, for anything that must sit over them. */
    protected open fun paintOver(matrixStack: UMatrixStack) {}

    final override fun draw(matrixStack: UMatrixStack) {
        // No beforeDraw() - UIContainer.draw already calls it, which is the whole point of this class.
        paint(matrixStack)
        super.draw(matrixStack)
        paintOver(matrixStack)
    }
}
