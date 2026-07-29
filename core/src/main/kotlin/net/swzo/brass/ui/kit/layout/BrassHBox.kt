package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.base.BrassWidget

/**
 * A **horizontal row**: children are appended left to right with an even [gap], and the box sizes
 * itself to what it holds. The counterpart to [BrassVBox]; see there for why the anchor chains this
 * replaces are worth replacing.
 *
 * ```
 * BrassHBox(gap = 6f).add(
 *     BrassButton("Cancel"),
 *     BrassButton("Save", BrassAccent.BRASS),
 * ) childOf footer
 * ```
 *
 * ### When to use [BrassFlow] instead
 *
 * A row lays its children out on **one line** and lets them run past the edge if there is not enough
 * space. [BrassFlow] wraps onto further lines and can stretch its items to fill the width. Reach for
 * this when the row is short and known - a pair of footer buttons, a label beside a field - and for
 * [BrassFlow] when the content is open-ended, like a tag list or a toolbar that has to survive being
 * resized.
 */
class BrassHBox(
    /** Space between consecutive children. */
    private val gap: Float = 6f,
    /** Reserve the keycap bleed around children. Turn off for plain, non-widget content. */
    private val bleed: Boolean = true,
) : UIContainer() {

    private val padX get() = if (bleed) BrassWidget.BLEED_X else 0f
    private val padTop get() = if (bleed) BrassWidget.BLEED_TOP else 0f

    init {
        constrain {
            width = BrassLayout.contentWidth(padX)
            height = BrassLayout.contentHeight(if (bleed) BrassWidget.BLEED_BOTTOM else 0f)
        }
    }

    /**
     * Append [children] to the right-hand end of the row, in order. Returns this box so a row can be
     * built and parented in one expression.
     *
     * Only x and y are constrained; a child keeps the width and height it was given.
     */
    fun add(vararg children: UIComponent): BrassHBox {
        for (child in children) {
            val first = this.children.isEmpty()
            child.constrain {
                x = if (first) padX.pixels() else SiblingConstraint(gap + padX)
                y = padTop.pixels()
            }
            child childOf this
        }
        return this
    }

    /**
     * Push everything added after this call to the right-hand end of the row.
     *
     * The row-with-a-gap-in-the-middle every footer needs: `add(title); spring(); add(cancel, save)`.
     * Previously this meant either an `addSpacer` with a hand-computed width, which breaks the moment
     * the row resizes, or an `alignOpposite` constraint, which needs a parent whose width is already
     * authoritative and silently misplaces the child when it is not (see `component/Row`).
     */
    fun spring(): BrassHBox = add(UIContainer().constrain {
        width = gg.essential.elementa.dsl.basicWidthConstraint { c ->
            val used = c.parent.children.filter { it !== c }.sumOf { (it.getWidth() + gap).toDouble() }.toFloat()
            (c.parent.getWidth() - used - padX * 2).coerceAtLeast(0f)
        }
        height = 1.pixels()
    })

    /** Append blank horizontal space - for splitting a row into left and right groups. */
    fun addSpacer(size: Float): BrassHBox = add(UIContainer().constrain {
        width = size.pixels()
        height = 1.pixels()
    })
}
