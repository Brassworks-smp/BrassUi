@file:Suppress("unused")
package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.WidthConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import kotlin.math.floor

/**
 * Size bands, for a layout that has to work at every GUI scale.
 * ### Why this exists
 * Both showcase screens clamp by hand - `(parent.getWidth() * 0.26f).coerceIn(110f, 170f)` - and both
 * arrived at different numbers for the same rail. A Minecraft GUI is resized by the *GUI scale*
 * setting as well as the window, so the range a layout must survive is wider than a desktop app's,
 * and "how wide is wide" was decided independently in every file.
 */
object BrassBreakpoint {

    const val COMPACT = 400f

    const val MEDIUM = 640f

    const val WIDE = 900f

    enum class Band { COMPACT, MEDIUM, WIDE }

    fun bandOf(width: Float): Band = when {
        width < COMPACT -> Band.COMPACT
        width < WIDE -> Band.MEDIUM
        else -> Band.WIDE
    }

    fun bandOf(c: UIComponent): Band = bandOf(c.getWidth())

    fun <T> pick(c: UIComponent, compact: T, medium: T, wide: T): T = when (bandOf(c)) {
        Band.COMPACT -> compact
        Band.MEDIUM -> medium
        Band.WIDE -> wide
    }

    fun proportional(fraction: Float, min: Float, max: Float): WidthConstraint =
        basicWidthConstraint { c -> (c.parent.getWidth() * fraction).coerceIn(min, max) }

    fun columns(width: Float, minItem: Float, gap: Float = BrassSpacing.GAP): Int {
        if (minItem <= 0f) return 1
        return maxOf(1, floor((width + gap) / (minItem + gap)).toInt())
    }

    /**
     * The width one column gets when [count] of them share [width] with [gap] between - the companion
     * to [columns], so a caller can size the cells it decided to lay out. Never negative.
     */
    fun columnWidth(width: Float, count: Int, gap: Float = BrassSpacing.GAP): Float {
        val n = count.coerceAtLeast(1)
        return ((width - gap * (n - 1)) / n).coerceAtLeast(0f)
    }
}
