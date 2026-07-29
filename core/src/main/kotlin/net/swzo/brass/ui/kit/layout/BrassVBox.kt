package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.base.BrassWidget

/**
 * A **vertical stack**: children are appended top to bottom with an even [gap], and the box sizes
 * itself to whatever it ends up holding.
 *
 * This exists to kill the anchor chains that composing a column by hand produces -
 * `y = basicYConstraint { blurb.getBottom() + 12f }` repeated down a screen, where every row names the
 * row above it. Those chains are brittle in a specific way: they break silently. Insert a row in the
 * middle, or delete one, and the neighbours still *compile* while pointing at the wrong sibling. Here
 * order of insertion is the layout, so there is nothing to keep in sync.
 *
 * ```
 * BrassVBox(gap = 8f).add(
 *     BrassLabel("Server"),
 *     BrassTextInput(),
 *     BrassButton("Connect"),
 * ) childOf card
 * ```
 *
 * ### Bleed
 *
 * A [BrassWidget] paints outside its own box - outer ring, bottom lip, hover lift - so the stack
 * reserves that bleed around its children rather than letting the first row's ring clip against the
 * card edge and the last row's lip vanish. Pass `bleed = false` for a column of plain components that
 * do not paint outside themselves, and get the exact [gap] you asked for.
 *
 * ### Sizing
 *
 * Height is [BrassLayout.contentHeight], so the box grows with its content and scrolls correctly
 * inside a `ScrollComponent`. Width defaults to filling the parent. **Do not give a child a height of
 * `100.percent()`** - the box asks its children how tall they are, and such a child asks straight back
 * until the stack overflows. See [BrassLayout] for the full version of that warning.
 */
class BrassVBox(
    /** Space between consecutive children. */
    private val gap: Float = 6f,
    /** Reserve the keycap bleed around children. Turn off for plain, non-widget content. */
    private val bleed: Boolean = true,
) : UIContainer() {

    private val padX get() = if (bleed) BrassWidget.BLEED_X else 0f
    private val padTop get() = if (bleed) BrassWidget.BLEED_TOP else 0f
    private val padBottom get() = if (bleed) BrassWidget.BLEED_BOTTOM else 0f

    init {
        constrain {
            width = 100.percent()
            height = BrassLayout.contentHeight(padBottom)
        }
    }

    /**
     * Append [children] to the bottom of the stack, in order. Returns this box so a column can be
     * built and parented in one expression.
     *
     * Only x and y are constrained - a child keeps whatever width and height it was given, so a row
     * can still be a fixed size, a percentage, or intrinsic.
     */
    fun add(vararg children: UIComponent): BrassVBox {
        for (child in children) {
            // The first row sits at the top inset; every later row hangs off the one before it, which
            // is what makes the stack survive a child changing height (wrapped text gaining a line).
            val first = this.children.isEmpty()
            child.constrain {
                x = padX.pixels()
                y = if (first) padTop.pixels() else SiblingConstraint(gap + padBottom)
            }
            child childOf this
        }
        return this
    }

    /** Append blank vertical space - a section break wider than the usual [gap]. */
    fun addSpacer(size: Float): BrassVBox = add(UIContainer().constrain {
        width = 1.pixels()
        height = size.pixels()
    })
}
