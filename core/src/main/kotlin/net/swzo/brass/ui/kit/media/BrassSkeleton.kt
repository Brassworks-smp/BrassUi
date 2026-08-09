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
 * ### Why this exists
 * The sweep was written for [BrassImage] and locked inside it, so the one good "content is on its
 * way" affordance in the toolkit was available only to images. A list waiting on a server response,
 * a panel waiting on a config read, a table waiting on a query - all of them had nothing.
 * The sweep is what separates "loading" from "broken" at a glance: a static grey box reads as a
 * missing thing, and the whole reason to show a placeholder rather than nothing is to say that
 * something is coming.
 */
class BrassSkeleton(
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


        override fun demo() = BrassDemo("skeleton", "Skeleton", 180f, 44f) {
            BrassSkeleton()
        }

        const val SHIMMER_MS = 1200L

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
