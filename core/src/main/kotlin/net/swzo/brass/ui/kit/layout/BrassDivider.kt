package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.dsl.*
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.layout.BrassDivider.Companion.between
import net.swzo.brass.ui.kit.layout.BrassDivider.Companion.under
import net.swzo.brass.ui.kit.paint.BrassPaint
import java.awt.Color
import kotlin.math.roundToInt
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A separator rule in the toolkit's grammar: a 1-px recessed groove with a 1-px highlight beside it -
 * the same two-tone bevel the sliders and scrollbar use - running at full strength from end to end so
 * it reads as an etched seam in the panel.
 *
 * Deliberately **unaccented**. An earlier version tinted its head with `Colors.UI_ACCENT` to echo the
 * window title's tell, which was a mistake twice over: the "brass" accent is `0x1FBF63`, i.e. green,
 * so it read as a stray green blob, and a divider is structural furniture - it should recede, not
 * carry a highlight competing with the widgets on either side of it.
 *
 * ### Placing one without hardcoding coordinates
 *
 * The point of [between] and [under] is that a divider is defined by *what it separates*, never by a
 * literal x. Both derive every constraint from the neighbouring components, so the rule follows them
 * when the window resizes, when a rail's clamped width changes, or when wrapped text above it grows a
 * line. Reuse in another screen is one call:
 *
 * ```kotlin
 * BrassDivider.between(window.content, nav, mainScroll)               // centred in the gap, rail-height
 * BrassDivider.between(window.content, nav, main, span = window.content, inset = 0f)  // full-height seam
 * BrassDivider.under(panel, heading)                                  // horizontal, across the panel
 * ```
 *
 * Constructing one directly is fine too - [Axis] is inferred from whichever dimension is thinner, so a
 * tall narrow box paints a vertical rule and a wide flat one paints a horizontal rule.
 */
class BrassDivider : UIComponent() {

    enum class Axis { VERTICAL, HORIZONTAL }

    /** Forced orientation, or null to infer it from the component's own shape. */
    var axis: Axis? = null

    override fun draw(matrixStack: UMatrixStack) {
        beforeDraw(matrixStack)

        val x = getLeft().roundToInt()
        val y = getTop().roundToInt()
        val x2 = getRight().roundToInt()
        val y2 = getBottom().roundToInt()
        val w = x2 - x
        val h = y2 - y

        if (w > 0 && h > 0) {
            val vertical = axis?.let { it == Axis.VERTICAL } ?: (w <= h)
            val length = if (vertical) h else w
            if (length > 0) paint(matrixStack, x, y, vertical, length)
        }

        super.draw(matrixStack)
    }

    /**
     * Paint the rule at full strength end to end - two 1-px runs, the groove and its highlight.
     *
     * It deliberately does *not* fade at the ends. A fade suits a rule floating in open space, but
     * this one is a structural seam that meets the frame at both ends, and tapering out just made it
     * look like it had failed to reach the edges.
     */
    private fun paint(m: UMatrixStack, x: Int, y: Int, vertical: Boolean, length: Int) {
        if (vertical) {
            fill(m, x, y, x + 1, y + length, SHADE)
            fill(m, x + 1, y, x + 2, y + length, HIGHLIGHT)
        } else {
            fill(m, x, y, x + length, y + 1, SHADE)
            fill(m, x, y + 1, x + length, y + 2, HIGHLIGHT)
        }
    }

    private fun fill(m: UMatrixStack, x1: Int, y1: Int, x2: Int, y2: Int, c: Color) =
        BrassPaint.rect(m, x1, y1, x2, y2, c)

    companion object : BrassDemoSource {


        /** A rule. Nothing to perform — see [BrassDemo.Stage.still]. */
        override fun demo() = BrassDemo("divider", "Divider", 170f, 10f) {
            BrassDivider()
        }

        /** Painted width of the rule: the groove plus its highlight. */
        const val THICKNESS = 2f

        private val SHADE: Color get() = Colors.DIVIDER_SHADE
        private val HIGHLIGHT: Color get() = Colors.DIVIDER_HIGHLIGHT

        /**
         * A **vertical** rule centred in the horizontal gap between [left] and [right].
         *
         * Its vertical extent comes from [span], inset by [inset] at each end. [span] defaults to
         * [left], which suits a rule that only flanks one list; pass the shared container (typically
         * the same component as [parent]) for a full-height seam that runs edge to edge - e.g. from
         * just under a window header down to the window's bottom.
         *
         * Nothing here is a literal coordinate - if the rail's width is clamped differently at another
         * window size, or either side moves, the rule follows. Add it to whichever container already
         * holds the two sides.
         */
        fun between(
            parent: UIComponent,
            left: UIComponent,
            right: UIComponent,
            span: UIComponent = left,
            inset: Float = 4f,
        ): BrassDivider = BrassDivider().also { rule ->
            rule.axis = Axis.VERTICAL
            rule.constrain {
                x = basicXConstraint {
                    // centre of the gap, rounded so the 1-px groove lands on a whole pixel
                    ((left.getRight() + right.getLeft()) / 2f - THICKNESS / 2f).roundToInt().toFloat()
                }
                y = basicYConstraint { span.getTop() + inset }
                width = THICKNESS.pixels()
                height = basicHeightConstraint { (span.getHeight() - inset * 2f).coerceAtLeast(0f) }
            } childOf parent
        }

        /**
         * A **horizontal** rule sitting [gap] below [above], as wide as [above] is. Use it to break a
         * stack of sections apart; the rule tracks [above]'s measured bottom, so wrapped text that
         * grows a line pushes it down rather than being crossed out by it.
         */
        fun under(
            parent: UIComponent,
            above: UIComponent,
            gap: Float = 8f,
        ): BrassDivider = BrassDivider().also { rule ->
            rule.axis = Axis.HORIZONTAL
            rule.constrain {
                x = basicXConstraint { above.getLeft() }
                y = basicYConstraint { above.getBottom() + gap }
                width = basicWidthConstraint { above.getWidth() }
                height = THICKNESS.pixels()
            } childOf parent
        }
    }
}
