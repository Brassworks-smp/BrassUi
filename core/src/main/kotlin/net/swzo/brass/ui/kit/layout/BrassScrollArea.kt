package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.base.BrassWidget

/**
 * A scroll view with its scrollbar and content inset already wired up - the four-line dance every
 * scrolling list otherwise repeats by hand.
 *
 * ### Why this exists
 *
 * A scrolling list in the toolkit is always the same three moving parts, and always the same three
 * mistakes when they are assembled by hand:
 *
 * ```kotlin
 * val scroll = ScrollComponent(innerPadding = 0f).constrain {
 *     width = 100.percent() - (BrassScrollbar.WIDTH + 3f).pixels()   // reserve the bar's gutter
 *     height = 100.percent()
 * } childOf host
 * BrassScrollbar.attach(host, scroll)                                // or the bar sits on the content
 * val column = UIContainer().constrain {
 *     x = BrassWidget.BLEED_X.pixels()                               // or inner cards clip on the left
 *     width = 100.percent() - (BrassWidget.BLEED_X * 2).pixels()
 *     height = BrassLayout.contentHeight()                           // or the list stops short of its end
 * } childOf scroll
 * ```
 *
 * Forget the gutter and the bar overlays the last pixel of every row; forget the inset and a card
 * placed flush inside scrolls with its ring shaved off the left; measure the column with
 * `ChildBasedSizeConstraint` and the list refuses to scroll to its own bottom. This packages all of it:
 * add rows to [content] (or with [add]) and none of the three can be got wrong.
 *
 * ```kotlin
 * val list = BrassScrollArea().constrain { … } childOf host
 * list.add(rowA, rowB, rowC)          // vertical stack, or position in `content` by hand
 * ```
 */
class BrassScrollArea @JvmOverloads constructor(
    /** Gap between the scrolling body and its scrollbar. */
    private val scrollbarGap: Float = 3f,
    /** Keep the scrollbar visible even when the content fits - reserves the gutter so nothing jumps. */
    alwaysShowBar: Boolean = false,
) : UIContainer() {

    /** The scroll view itself, for a caller that needs to drive it (scroll to a row, read the offset). */
    val body: ScrollComponent

    /**
     * The scrolling content region, inset by the keycap bleed so a card or control placed flush inside
     * keeps its outer ring and bottom lip. Add rows here to position them yourself, or use [add] for a
     * managed vertical stack.
     */
    val content: UIContainer

    /** The managed stack [add] appends to, created lazily. */
    private var stack: BrassVBox? = null

    init {
        body = ScrollComponent(emptyString = "", innerPadding = 0f).constrain {
            x = 0.pixels()
            y = 0.pixels()
            // Reserve the bar's gutter rather than letting it overlay the content: the body scissors to
            // its own bounds, so a bar drawn on top would sit over the right edge of every row.
            width = 100.percent() - (BrassScrollbar.WIDTH + scrollbarGap).pixels()
            height = 100.percent()
        } childOf this

        BrassScrollbar.attach(this, body, alwaysShow = alwaysShowBar)

        content = UIContainer().constrain {
            x = BrassWidget.BLEED_X.pixels()
            y = BrassWidget.BLEED_TOP.pixels()
            width = 100.percent() - (BrassWidget.BLEED_X * 2).pixels()
            // The real extent plus the keycap bleed, so the bottom row's lip is inside the scrollable
            // area and the list scrolls to exactly its end.
            height = BrassLayout.contentHeight()
        } childOf body
    }

    /** The managed vertical stack, created on first [add]. */
    private fun column(): BrassVBox = stack ?: BrassVBox().also {
        it.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent() } childOf content
        stack = it
    }

    /**
     * Append [rows] to the bottom of the list as full-width entries, in order. Returns this area so it
     * can be built and parented in one expression.
     */
    fun add(vararg rows: UIComponent): BrassScrollArea {
        val col = column()
        for (row in rows) {
            row.constrain { width = 100.percent() - (BrassWidget.BLEED_X * 2).pixels() }
            col.add(row)
        }
        return this
    }

    /** Empty the list - both the managed stack and anything added to [content] by hand. */
    fun clear() {
        content.clearChildren()
        stack = null
    }
}
