package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.input.BrassButton
import kotlin.math.max

/**
 * A flexbox-style wrapping row (CSS `flex-wrap: wrap`). Children are laid out left to right at their
 * natural width and pushed onto a new line whenever the next one would not fit, so a row of controls
 * reflows instead of running off the edge of its card.
 * Positions are recomputed every frame from the container's *current* width, which is what makes this
 * work with percentage/derived widths and with the window being resized or maximised.
 * The layout reserves the keycap bleed ([BrassWidget.BLEED_X] / [BrassWidget.BLEED_TOP] /
 * [BrassWidget.BLEED_BOTTOM]) around every item, so a widget's outer ring, bottom lip and hover lift all
 * stay inside the container and never get clipped by a card's padding.
 * Add children with [add] (which records the width each one wants), then size the container with
 * `basicHeightConstraint { flow.contentHeight() }` so it grows to fit however many lines the reflow
 * needs.
 */
class BrassFlow(
    private val gapX: Float = 6f,
    private val gapY: Float = 6f,
    private val itemHeight: Float = 20f,
    private val stretch: Boolean = false,
) : UIContainer() {

    private data class Item(val component: UIComponent, val width: Float)

    private val items = ArrayList<Item>()

    private var lastLaidOutWidth = Float.NaN

    var lines: Int = 1
        private set

    fun add(component: UIComponent, width: Float): BrassFlow {
        component.constrain {
            x = 0.pixels(); y = 0.pixels()
            this.width = width.pixels(); height = itemHeight.pixels()
        } childOf this
        items.add(Item(component, width))
        invalidate() // a new child invalidates both the placement and the measurement
        return this
    }

    private var measuredWidth = Float.NaN
    private var measuredLines = 1

    fun contentHeight(): Float {
        val w = getWidth()
        if (w != measuredWidth) {
            measuredWidth = w
            measuredLines = layout(w, apply = false)
        }
        val n = measuredLines
        return n * itemHeight + (n - 1) * gapY + BrassWidget.BLEED_TOP + BrassWidget.BLEED_BOTTOM
    }

    /** Both caches are dropped whenever the child set changes - see [add]. */
    private fun invalidate() {
        lastLaidOutWidth = Float.NaN
        measuredWidth = Float.NaN
    }

    private fun layout(available: Float, apply: Boolean): Int {
        if (items.isEmpty()) return 1
        // usable width excludes the bleed on both sides, so edge items keep their outer ring
        val usable = max(1f, available - BrassWidget.BLEED_X * 2)

        // pass 1 - break into lines at their natural widths
        val lines = ArrayList<MutableList<Item>>()
        var current = ArrayList<Item>()
        var cursor = 0f
        for (item in items) {
            val w = item.width.coerceAtMost(usable)
            if (current.isNotEmpty() && cursor + w > usable) {
                lines.add(current)
                current = ArrayList()
                cursor = 0f
            }
            current.add(item.copy(width = w))
            cursor += w + gapX
        }
        if (current.isNotEmpty()) lines.add(current)

        // pass 2 - place, optionally sharing each line's leftover space among its items
        if (apply) {
            lines.forEachIndexed { row, lineItems ->
                val natural = lineItems.sumOf { it.width.toDouble() }.toFloat() + gapX * (lineItems.size - 1)
                val slack = if (stretch) (usable - natural).coerceAtLeast(0f) / lineItems.size else 0f
                var x = BrassWidget.BLEED_X
                val y = BrassWidget.BLEED_TOP + row * (itemHeight + gapY)
                for (item in lineItems) {
                    val w = item.width + slack
                    val px = x
                    item.component.constrain {
                        this.x = px.pixels(); this.y = y.pixels()
                        width = w.pixels(); height = itemHeight.pixels()
                    }
                    x += w + gapX
                }
            }
        }
        return lines.size
    }

    override fun draw(matrixStack: UMatrixStack) {
        // NOTE: no beforeDraw() here. UIContainer.draw already calls it, and calling it again throws
        // "called `beforeDraw` more than once without a call to `draw`" every frame. Only UIComponent
        // subclasses that fully own their draw (BrassWidget, BrassPopup) should call it themselves.
        // Re-place children only when the available width actually changed. Applying constraints every
        // frame would allocate a fresh set of constraint objects per child per frame for a layout that
        // is almost always identical to the last one.
        val w = getWidth()
        if (w != lastLaidOutWidth) {
            lines = layout(w, apply = true)
            lastLaidOutWidth = w
            measuredWidth = w
            measuredLines = lines
        }
        super.draw(matrixStack)
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("flow", "Flow", 150f, 80f) {
            BrassFlow(itemHeight = 18f).apply {
                for (label in listOf("Survival", "Creative", "Hardcore", "Adventure", "Peaceful", "Spectator")) {
                    // A rough width from the label length is enough to seed the wrap; the flow re-measures.
                    add(BrassButton(label, BrassAccent.DEFAULT), label.length * 6f + 14f)
                }
            }
        }
    }
}
