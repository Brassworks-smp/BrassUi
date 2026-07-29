package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.WidthConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import kotlin.math.floor

/**
 * Size bands, for a layout that has to work at every GUI scale.
 *
 * ### Why this exists
 *
 * Both showcase screens clamp by hand - `(parent.getWidth() * 0.26f).coerceIn(110f, 170f)` - and both
 * arrived at different numbers for the same rail. A Minecraft GUI is resized by the *GUI scale*
 * setting as well as the window, so the range a layout must survive is wider than a desktop app's,
 * and "how wide is wide" was decided independently in every file.
 */
object BrassBreakpoint {

    /** Below this the UI is a single column; a nav rail should collapse. */
    const val COMPACT = 400f

    /** Below this a two-column layout is tight but workable. */
    const val MEDIUM = 640f

    /** At or above this there is room for a rail, a body and a detail pane. */
    const val WIDE = 900f

    enum class Band { COMPACT, MEDIUM, WIDE }

    /** Which band [width] falls in. */
    fun bandOf(width: Float): Band = when {
        width < COMPACT -> Band.COMPACT
        width < WIDE -> Band.MEDIUM
        else -> Band.WIDE
    }

    /** The band [c]'s own width falls in. */
    fun bandOf(c: UIComponent): Band = bandOf(c.getWidth())

    /** Pick a value for the component's current band - the whole point of the type. */
    fun <T> pick(c: UIComponent, compact: T, medium: T, wide: T): T = when (bandOf(c)) {
        Band.COMPACT -> compact
        Band.MEDIUM -> medium
        Band.WIDE -> wide
    }

    /**
     * A width that is [fraction] of the parent, clamped to `[min, max]` - the clamp both showcase
     * screens wrote by hand for their nav rails, at two different sets of numbers.
     */
    fun proportional(fraction: Float, min: Float, max: Float): WidthConstraint =
        basicWidthConstraint { c -> (c.parent.getWidth() * fraction).coerceIn(min, max) }

    /**
     * How many columns of at least [minItem] wide fit in [width], with [gap] between them - at least 1.
     *
     * The responsive answer to elements clipping inside a card at small resolutions: rather than laying
     * a fixed number of items across a row and letting them run off (or overlap) when the card is
     * squeezed, ask how many actually fit and lay out that many, wrapping the rest. `n` columns fit when
     * `n * minItem + (n - 1) * gap <= width`, which solves to `n <= (width + gap) / (minItem + gap)`.
     *
     * Returns 1 below one item's width - a single column that the caller can let scroll or itself wrap,
     * rather than 0, which no layout can use.
     */
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
