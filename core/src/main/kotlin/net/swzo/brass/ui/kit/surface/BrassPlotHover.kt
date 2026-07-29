package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import net.swzo.brass.ui.kit.layout.BrassCull
import net.swzo.brass.ui.kit.surface.BrassPlotHover.NONE
import kotlin.math.floor

/**
 * Which slice of a plot the cursor is over.
 *
 * Shared by [BrassChart] and [BrassBarChart] because the fiddly part is identical and easy to get
 * subtly wrong in two places: a plot of `n` values divides its width into `n` buckets, and the index
 * is `floor(fraction * n)` - **not** `round`, which makes the first and last buckets half-width and
 * leaves the last value unreachable at the right edge.
 *
 * The cursor is also gated on [BrassCull.visible], for the reason every hover test in this toolkit is:
 * Elementa dispatches from geometry alone, so a chart underneath an open popup still passes a plain
 * bounds check and lights up *through* whatever is covering it.
 */
object BrassPlotHover {

    /** No slice. */
    const val NONE = -1

    /**
     * The slice index under the cursor, or [NONE].
     *
     * [left] and [width] describe the **plot area**, not the component - a chart with a legend or an
     * axis has chrome outside the plot that should not map to a value.
     */
    fun indexAt(
        component: UIComponent,
        /** Cursor position. Passed in because `getMousePosition` is protected on UIComponent. */
        mx: Float,
        my: Float,
        left: Float,
        width: Float,
        count: Int,
    ): Int {
        if (count <= 0 || width <= 0f) return NONE
        if (!BrassCull.visible(component)) return NONE
        if (my < component.getTop() || my > component.getBottom()) return NONE
        if (mx < left || mx > left + width) return NONE

        val index = floor((mx - left) / width * count).toInt()
        return index.coerceIn(0, count - 1)
    }

    /** The left edge and width of slice [index] within a plot of [count] slices. */
    fun sliceBounds(left: Float, width: Float, count: Int, index: Int): FloatArray {
        if (count <= 0) return floatArrayOf(left, 0f)
        val slice = width / count
        return floatArrayOf(left + index * slice, slice)
    }
}
