package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassContainer
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.layout.BrassFlow
import net.swzo.brass.ui.kit.layout.BrassLayout
import net.swzo.brass.ui.kit.layout.BrassSpacing
import net.swzo.brass.ui.kit.layout.BrassVBox
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.text.BrassLabel

/**
 * A titled **card** with a padded, self-contained content area - the shape a screen is built out of.
 *
 * ### What it does for you, that a bare card does not
 *
 * Two things go wrong every time a card is hand-built, and this fixes both by construction:
 *
 * 1. **It never clips its own frame.** The card is painted with [BrassCard.panel], whose near-black
 *    ring is drawn *inside* the panel's bounds rather than bleeding a pixel past them. So a panel
 *    dropped flush inside a `ScrollComponent` (or any `ScissorEffect`) keeps its frame on all four
 *    sides - the "the black border gets shaved off the top and sides" bug simply cannot happen. A card
 *    hand-drawn with [BrassCard.draw] has to be inset by hand to avoid it; this one does not.
 *
 * 2. **Its content is padded, by the toolkit's own scale.** [content] sits [pad] in from every edge
 *    ([BrassSpacing.PAD] by default), so controls never touch the frame and every panel in an app is
 *    padded the *same*. No screen has to remember a magic number, and two panels built by two different
 *    people line up.
 *
 * ### Adding content
 *
 * The default body is a **vertical stack** - the shape a card wants almost every time:
 *
 * ```kotlin
 * BrassPanel("SERVER").add(
 *     BrassTextInput("", "address"),
 *     BrassButton("Connect", BrassAccent.BRASS),
 * ) childOf parent
 * ```
 *
 * [add] appends full-width rows top to bottom, reserving the keycap bleed so a control's ring and lip
 * are never clipped. Side-by-side controls go in through [row], and **wrapping is what they do by
 * default**: a row reflows onto another line when the panel is too narrow to hold it, rather than
 * letting its controls collide or run off the edge at small GUI scales. There is deliberately no
 * fixed-count side-by-side option - a row that cannot wrap is the bug this is here to prevent.
 *
 * ```kotlin
 * panel.row(18f, BrassButton("Edit"), BrassButton("Copy"), BrassButton("Delete"))
 * ```
 *
 * That row wraps instead of overlapping, so a panel laid out this way survives being squeezed without
 * any per-screen responsive code.
 *
 * For a card that lays out its own body by hand (a master/detail split, absolute positioning), pass
 * [Layout.FREE] and add straight to [content] - it is still padded and still self-contained, it just
 * does not manage a stack for you.
 *
 * ### Sizing
 *
 * By default the panel fills whatever height it is given, and [content] fills the area below the title.
 * Pass `hug = true` (or use [BrassPanel.hug]) for a card that measures its own height from its content -
 * the tallest child, not the sum (see [BrassLayout.tallestChildHeight]) - which is what a card in a
 * scrolling list wants so the list scrolls to exactly its end.
 */
class BrassPanel @JvmOverloads constructor(
    private val title: String? = null,
    /** Inset of [content] from the card's edge. Defaults to the toolkit scale. */
    private val pad: Float = BrassSpacing.PAD,
    /** Gap between stacked rows added through [add]. */
    private val gap: Float = BrassSpacing.GAP,
    /** Fill the card, or leave it a bordered frame over whatever is behind it. */
    private val filled: Boolean = true,
    /** How [add] places children - a wrapping vertical stack, or hand-positioned [content]. */
    private val layout: Layout = Layout.COLUMN,
    /** Measure the panel's own height from its content rather than filling the height it is given. */
    private val hug: Boolean = false,
) : BrassContainer() {

    /** How a panel arranges what [add] puts in it. */
    enum class Layout {
        /** A vertical stack of full-width rows, wrapping the keycap bleed - the default a card wants. */
        COLUMN,

        /** No managed layout: add to [content] and position by hand. For a master/detail body. */
        FREE,
    }

    /** Height of the title band, or 0 when the panel has no title. */
    private val titleH: Float = if (title != null) BrassSpacing.TITLE_H else 0f

    /**
     * The padded region inside the card. Add here when the panel is [Layout.FREE] or you are
     * positioning children yourself; otherwise prefer [add] / [row], which fill it for you.
     */
    val content: UIContainer

    /** The managed stack [add] appends to, created lazily on first use. Null for [Layout.FREE]. */
    private var stack: BrassVBox? = null

    init {
        content = UIContainer().constrain {
            x = pad.pixels()
            y = (titleH + pad).pixels()
            width = 100.percent() - (pad * 2).pixels()
            height = if (hug) {
                BrassLayout.tallestChildHeight(0f)
            } else {
                basicHeightConstraint { c -> (c.parent.getBottom() - c.getTop() - pad).coerceAtLeast(0f) }
            }
        } childOf this

        if (hug) {
            // The card sizes to the tallest thing in it, plus the title band and padding on both edges.
            constrain {
                height = basicHeightConstraint { c ->
                    val kids = content.children
                    val body = if (kids.isEmpty()) 0f else kids.maxOf { it.getBottom() } - content.getTop()
                    titleH + pad + body + pad
                }
            }
        }

        if (title != null) buildTitle(title)
    }

    private fun buildTitle(text: String) {
        net.swzo.brass.ui.kit.text.BrassLabel(text, Colors.UI_TEXT_DARK).constrain {
            x = pad.pixels()
            y = 4.pixels()
        } childOf this
        // The hairline under the title, edge to edge, so the band reads as a header rather than a
        // caption that happens to sit near the top control.
        Rule().constrain {
            x = 0.pixels()
            y = titleH.pixels()
            width = 100.percent()
            height = 1.pixels()
        } childOf this
    }

    // ---- building --------------------------------------------------------------------------------

    /** The managed vertical stack, created on first [add]. Throws in [Layout.FREE]. */
    private fun column(): BrassVBox {
        check(layout == Layout.COLUMN) {
            "This panel is Layout.FREE; add to `content` and position children yourself."
        }
        return stack ?: BrassVBox(gap = gap).also {
            it.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent() } childOf content
            stack = it
        }
    }

    /**
     * Append [children] as full-width rows, top to bottom, reserving the keycap bleed so nothing is
     * clipped. Returns this panel so a card can be built and parented in one expression.
     */
    fun add(vararg children: UIComponent): BrassPanel {
        val col = column()
        for (child in children) {
            // Full width, less the keycap bleed the stack reserves on each side, so a full-width
            // control's outer ring stays inside the padded area rather than poking into it.
            child.constrain { width = 100.percent() - (BrassWidget.BLEED_X * 2).pixels() }
            col.add(child)
        }
        return this
    }

    /**
     * Append a row of [controls] that sit side by side and **wrap onto another line when the panel is
     * too narrow to fit them** - the responsive answer to controls colliding at small GUI scales. Each
     * control is [height] tall and stretches to share the line; below a readable minimum the row wraps
     * instead of squeezing them to slivers.
     */
    fun row(rowHeight: Float, vararg controls: UIComponent): BrassPanel {
        val flow = BrassFlow(gapX = gap, gapY = BrassSpacing.TIGHT, itemHeight = rowHeight, stretch = true)
        controls.forEach { flow.add(it, MIN_CONTROL) }
        flow.constrain { height = basicHeightConstraint { flow.contentHeight() } }
        return add(flow)
    }

    /** Append blank vertical space - a break wider than the usual [gap]. */
    fun addSpacer(size: Float): BrassPanel {
        column().addSpacer(size)
        return this
    }

    // ---- paint -----------------------------------------------------------------------------------

    override fun paint(matrixStack: UMatrixStack) {
        val x = getLeft(); val y = getTop(); val x2 = getRight(); val y2 = getBottom()
        // The CONTAINED card: ring drawn inside the bounds, so a panel flush in a scroll keeps its
        // frame on every side. This is the whole reason BrassPanel exists rather than a bare BrassCard.
        if (filled) {
            BrassCard.panel(matrixStack, x, y, x2, y2, fill = Colors.UI_INNER_BG)
        } else {
            // Just the frame, drawn inside the bounds - the same contained ring, no fill.
            BrassCard.flat(matrixStack, x, y, x2, y2, fill = null, contained = true)
        }
    }

    /** A hairline, for the title band's underline. Kept private - it is not a general-purpose divider. */
    private class Rule : UIComponent() {
        override fun draw(matrixStack: UMatrixStack) {
            beforeDraw(matrixStack)
            net.swzo.brass.ui.kit.paint.BrassPaint.rect(
                matrixStack, getLeft(), getTop(), getRight(), getBottom(), Colors.UI_INNER_BORDER,
            )
            super.draw(matrixStack)
        }
    }

    companion object : BrassDemoSource {
        /** Narrowest a control in a [row] gets before the row wraps instead of squeezing it. */
        private const val MIN_CONTROL = 76f

        /**
         * A card that measures its own height from its content - for a panel in a scrolling list, so
         * the list scrolls to exactly the panel's end.
         */
        @JvmStatic
        fun hug(
            title: String? = null,
            pad: Float = BrassSpacing.PAD,
            gap: Float = BrassSpacing.GAP,
            filled: Boolean = true,
            layout: Layout = Layout.COLUMN,
        ): BrassPanel = BrassPanel(title, pad, gap, filled, layout, hug = true)

        /**
         * A titled card with a stacked body and a wrapping button row. A panel paints its own card, so
         * the demo does not wrap it in another one (`card = false`).
         */
        override fun demo() = BrassDemo("panel", "Panel", 210f, 96f, card = false) {
            BrassPanel("SERVER").add(
                BrassLabel("survival.example.net"),
                BrassLabel("1.21.1  ·  NeoForge"),
            ).apply {
                row(18f, BrassButton("Connect", BrassAccent.BRASS), BrassButton("Edit", BrassAccent.DEFAULT))
            }
        }
    }
}
