@file:Suppress("unused")
package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.dsl.*
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.platform.BrassCursor
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.surface.BrassProgressBar
import net.swzo.brass.ui.kit.text.BrassTag
import net.swzo.brass.ui.kit.text.BrassTagStyle
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.text.BrassLabel

/**
 * Two panes with a divider you can drag - a tree beside its details, a list beside a preview, an
 * editor beside its output.
 * ```kotlin
 * // bare
 * BrassSplitPane(BrassSplitPane.Orientation.HORIZONTAL, tree, details, split = 0.3f)
 * // on a card, with the seam running the full inner height
 * BrassSplitPane(BrassSplitPane.Orientation.HORIZONTAL, tree, details, card = true)
 *     .constrain { width = 100.percent(); height = 160.pixels() }
 * ```
 * ### The rule is a [BrassDivider]
 * Not a hand-painted line. The toolkit already has one separator - a 1-px recessed groove with a 1-px
 * highlight beside it, the same two-tone bevel the sliders and the scrollbar use - and a split pane
 * drawing its own would be a second, subtly different seam in the same UI. The divider is a real
 * child component, so it also appears in the inspector as the thing it actually is.
 * ### [card]
 * With `card = true` the pane paints the toolkit's card behind both halves and insets them by its
 * border, and **the divider runs from the card's inner top to its inner bottom** - meeting the frame
 * at both ends rather than floating short of it. That is the arrangement a split pane almost always
 * wants, and doing it by hand means getting the same inset right in four places.
 * ### The split is a fraction
 * [split] is 0..1, not pixels, for the same reason [BrassResize] stores frame bounds fractionally: a
 * divider parked 200 px from the left is in a sensible place until the window is resized, after which
 * it is in the wrong one - or off screen entirely. A fraction rescales, and the [minPane] clamp is
 * re-applied every frame by the constraints themselves.
 * ### The drag
 * The same three rules every drag in this toolkit obeys, and for the same reasons: gated on a press
 * that landed on the grip (Elementa broadcasts drags to the whole tree), worked out in **absolute**
 * coordinates (a grip's `relativeX` moves as the grip does, which feeds its own motion back in), and
 * cursor-hinted on hover so the handle announces itself before you press.
 */
class BrassSplitPane(
    private val orientation: Orientation = Orientation.HORIZONTAL,
    private val first: UIComponent,
    private val second: UIComponent,
    split: Float = 0.5f,
    var card: Boolean = false,
    private val minPane: Float = 60f,
) : BrassWidget(BrassAccent.DEFAULT) {

    enum class Orientation {
        HORIZONTAL,

        VERTICAL,
    }

    var split: Float = split.coerceIn(0f, 1f)
        set(value) { field = value.coerceIn(0f, 1f) }

    var onSplit: ((Float) -> Unit)? = null

    private val horizontal get() = orientation == Orientation.HORIZONTAL

    private fun inset(): Float = if (card) BORDER else 0f

    private fun extent(): Float = (if (horizontal) getWidth() else getHeight()) - inset() * 2f

    private fun crossExtent(): Float =
        ((if (horizontal) getHeight() else getWidth()) - inset() * 2f).coerceAtLeast(0f)

    private fun offset(): Float {
        val total = extent()
        if (total <= 0f) return 0f
        val usable = (total - BrassDivider.THICKNESS).coerceAtLeast(0f)
        // With too little room for two minimum panes, split the difference rather than fighting.
        if (usable < minPane * 2f) return usable / 2f
        return (usable * split).coerceIn(minPane, usable - minPane)
    }

    private fun after(): Float = offset() + BrassDivider.THICKNESS

    private val rule = BrassDivider().also {
        it.axis = if (horizontal) BrassDivider.Axis.VERTICAL else BrassDivider.Axis.HORIZONTAL
    }
    private val grip = Grip()

    init {
        // The card is painted by drawContent, not by the keycap machinery: a split pane is a surface,
        // not a control, and must not gain a hover tint or press travel.
        chrome = BrassChrome.NONE

        first.constrain {
            x = basicXConstraint { this@BrassSplitPane.getLeft() + inset() }
            y = basicYConstraint { this@BrassSplitPane.getTop() + inset() }
            width = basicWidthConstraint { if (horizontal) offset() else crossExtent() }
            height = basicHeightConstraint { if (horizontal) crossExtent() else offset() }
        } childOf this

        second.constrain {
            x = basicXConstraint {
                this@BrassSplitPane.getLeft() + inset() + if (horizontal) after() else 0f
            }
            y = basicYConstraint {
                this@BrassSplitPane.getTop() + inset() + if (horizontal) 0f else after()
            }
            width = basicWidthConstraint {
                if (horizontal) (extent() - after()).coerceAtLeast(0f) else crossExtent()
            }
            height = basicHeightConstraint {
                if (horizontal) crossExtent() else (extent() - after()).coerceAtLeast(0f)
            }
        } childOf this

        // The visible rule, spanning the full inner extent - top to bottom for a vertical seam - so it
        // meets the card's frame at both ends instead of stopping short of it.
        rule.constrain {
            x = basicXConstraint {
                this@BrassSplitPane.getLeft() + inset() + if (horizontal) offset() else 0f
            }
            y = basicYConstraint {
                this@BrassSplitPane.getTop() + inset() + if (horizontal) 0f else offset()
            }
            width = basicWidthConstraint { if (horizontal) BrassDivider.THICKNESS else crossExtent() }
            height = basicHeightConstraint { if (horizontal) crossExtent() else BrassDivider.THICKNESS }
        } childOf this

        // A wider, invisible grip over the rule. Aiming at a 2-px line is fussy; the forgiving hit
        // area is what makes dragging feel normal rather than fiddly.
        grip.constrain {
            x = basicXConstraint {
                this@BrassSplitPane.getLeft() + inset() + if (horizontal) offset() - GRAB else 0f
            }
            y = basicYConstraint {
                this@BrassSplitPane.getTop() + inset() + if (horizontal) 0f else offset() - GRAB
            }
            width = basicWidthConstraint {
                if (horizontal) BrassDivider.THICKNESS + GRAB * 2 else crossExtent()
            }
            height = basicHeightConstraint {
                if (horizontal) crossExtent() else BrassDivider.THICKNESS + GRAB * 2
            }
        } childOf this
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        // Flush (no shadow): a split pane normally sits inside a popup or window that casts one.
        if (card) {
            BrassCard.draw(m, x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), shadow = false)
        }
    }

    private inner class Grip : BrassWidget(BrassAccent.DEFAULT) {

        private var dragging = false

        init {
            chrome = BrassChrome.NONE
            onMouseClick { e -> if (e.mouseButton == 0) dragging = true }
            onMouseRelease { dragging = false }
            onMouseDrag { mx, my, btn ->
                if (!dragging || btn != 0) return@onMouseDrag
                // Absolute, not relative: the grip moves as it is dragged, so a relative delta would
                // compound and the divider would run away from the cursor.
                val absolute = if (horizontal) getLeft() + mx else getTop() + my
                val origin =
                    (if (horizontal) this@BrassSplitPane.getLeft() else this@BrassSplitPane.getTop()) + inset()
                val usable = (extent() - BrassDivider.THICKNESS).coerceAtLeast(1f)
                split = (absolute - origin - BrassDivider.THICKNESS / 2f) / usable
                onSplit?.invoke(split)
            }
        }

        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
            // Nothing to paint - the rule underneath is the visible divider. Only the cursor hint.
            if (hoveredState || dragging) {
                BrassCursor.request(if (horizontal) BrassCursor.Kind.RESIZE_H else BrassCursor.Kind.RESIZE_V)
            }
        }
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("split-pane", "Split pane", 280f, 130f, card = false) {
            val pane = BrassSplitPane(
                first = masterList(),
                second = detailPane(),
                split = 0.38f,
            )
            pane.card = true
            pane
        }

        private fun masterList(): UIContainer {
            val box = UIContainer().constrain { width = 100.percent(); height = 100.percent() }
            listOf("Overworld", "The Nether", "The End", "Skyblock", "Creative").forEachIndexed { i, name ->
                BrassButton(name, BrassAccent.DEFAULT) {}.apply {
                    centered = false
                    chrome = BrassChrome.FLAT
                    selectable = true
                    selected = i == 1
                }.constrain {
                    x = 4.pixels()
                    y = (4 + i * 17).pixels()
                    width = 100.percent() - 8.pixels()
                    height = 15.pixels()
                } childOf box
            }
            return box
        }

        private fun detailPane(): UIContainer {
            val box = UIContainer().constrain { width = 100.percent(); height = 100.percent() }
            BrassLabel("The Nether", Colors.UI_TEXT_HOVER)
                .constrain { x = 8.pixels(); y = 6.pixels() } childOf box
            BrassTag("LOADED", BrassTagStyle.SUCCESS)
                .constrain { x = 8.pixels(); y = 22.pixels(); width = 46.pixels(); height = 12.pixels() } childOf box
            BrassTag("HARD", BrassTagStyle.WARNING)
                .constrain { x = 58.pixels(); y = 22.pixels(); width = 38.pixels(); height = 12.pixels() } childOf box
            BrassLabel("Seed  -3999915891298666503")
                .constrain { x = 8.pixels(); y = 42.pixels() } childOf box
            BrassLabel("Chunks  961 loaded")
                .constrain { x = 8.pixels(); y = 56.pixels() } childOf box
            BrassProgressBar("Generating").also { it.progress = 0.72f }
                .constrain { x = 8.pixels(); y = 74.pixels(); width = 100.percent() - 16.pixels(); height = 12.pixels() } childOf box
            return box
        }

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val GRAB = 3f
        private const val BORDER = 1f
    }
}
