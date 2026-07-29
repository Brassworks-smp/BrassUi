package net.swzo.brass.ui.kit.media

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.paint.BrassPaint
import java.awt.Color
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A loading placeholder: a recessed block with a soft highlight sweeping across it.
 *
 * ### Why this exists
 *
 * The sweep was written for [BrassImage] and locked inside it, so the one good "content is on its
 * way" affordance in the toolkit was available only to images. A list waiting on a server response,
 * a panel waiting on a config read, a table waiting on a query - all of them had nothing.
 *
 * The sweep is what separates "loading" from "broken" at a glance: a static grey box reads as a
 * missing thing, and the whole reason to show a placeholder rather than nothing is to say that
 * something is coming.
 */
class BrassSkeleton(
    /** Draw the sweeping highlight. Off gives a plain recessed block. */
    var shimmer: Boolean = true,
) : BrassWidget(BrassAccent.DEFAULT) {

    init {
        chrome = BrassChrome.NONE
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        draw(m, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), shimmer)
    }

    companion object : BrassDemoSource {


        /**
         * The shimmer, looping.
         *
         * There is no still worth having. A frozen frame of a loading placeholder is a grey box, which
         * documents none of what makes it a *loading* placeholder — so this demo declares one scene
         * and no still, and the sweep is left to the clock.
         */
        override fun demo() = BrassDemo("skeleton", "Skeleton", 180f, 44f) {
            BrassSkeleton()
        }

        /** One full sweep, in milliseconds. */
        const val SHIMMER_MS = 1200L

        /**
         * Paint a skeleton into an arbitrary rectangle - for a component drawing several at once (the
         * rows of a table waiting on data), where a child per placeholder would be wasteful.
         */
        fun draw(m: UMatrixStack, x: Float, y: Float, w: Float, h: Float, shimmer: Boolean = true) {
            BrassPaint.rect(m, x, y, x + w, y + h, BG)
            if (!shimmer) return

            val t = (System.currentTimeMillis() % SHIMMER_MS) / SHIMMER_MS.toFloat()
            // travel from fully off the left to fully off the right, so there is a beat with no highlight
            val bandW = (w * 0.45f).coerceAtLeast(4f)
            val bx = x - bandW + (w + bandW * 2f) * t

            // three stacked slabs approximate a gradient band without a vertex-coloured quad
            band(m, x, w, bx, bx + bandW, y, h, EDGE)
            band(m, x, w, bx + bandW * 0.25f, bx + bandW * 0.75f, y, h, MID)
            band(m, x, w, bx + bandW * 0.4f, bx + bandW * 0.6f, y, h, CORE)
        }

        /**
         * One slab of the sweep, clipped to the skeleton's own rectangle.
         *
         * The band starts a full width off the left and ends a full width off the right — that
         * overshoot is what gives the sweep a beat with no highlight at either end — so its slabs are
         * *expected* to fall outside the block and were being painted there: the shimmer bled past the
         * placeholder on both sides, over whatever the widget happened to be sitting on.
         *
         * Clamped here rather than fixed with a `ScissorEffect` on the component, because [draw] is
         * also the shared entry point for anything painting several placeholders into arbitrary
         * rectangles (a table's rows waiting on a query). Those callers have no component of their own
         * to scissor, so a component-level fix would have left every one of them still bleeding.
         */
        private fun band(
            m: UMatrixStack,
            x: Float, w: Float,
            x1: Float, x2: Float,
            y: Float, h: Float,
            color: Color,
        ) {
            val l = x1.coerceIn(x, x + w)
            val r = x2.coerceIn(x, x + w)
            if (r > l) BrassPaint.rect(m, l, y, r, y + h, color)
        }

        private val BG: Color get() = Colors.UI_ELEMENT_BG
        private val EDGE: Color get() = Colors.SHIMMER_EDGE
        private val MID: Color get() = Colors.SHIMMER_MID
        private val CORE: Color get() = Colors.SHIMMER_CORE
    }
}
