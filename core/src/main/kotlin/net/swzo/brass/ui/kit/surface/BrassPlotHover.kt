package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import net.swzo.brass.ui.kit.layout.BrassCull
import kotlin.math.floor

/**
 * Which slice of a plot the cursor is over.
 * Shared by [BrassChart] and [BrassBarChart] because the fiddly part is identical and easy to get
 * subtly wrong in two places: a plot of `n` values divides its width into `n` buckets, and the index
 * is `floor(fraction * n)` - **not** `round`, which makes the first and last buckets half-width and
 * leaves the last value unreachable at the right edge.
 * The cursor is also gated on [BrassCull.visible], for the reason every hover test in this toolkit is:
 * Elementa dispatches from geometry alone, so a chart underneath an open popup still passes a plain
 * bounds check and lights up *through* whatever is covering it.
 */
object BrassPlotHover {

    const val NONE = -1

    fun indexAt(
        component: UIComponent,
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

    fun sliceBounds(left: Float, width: Float, count: Int, index: Int): FloatArray {
        if (count <= 0) return floatArrayOf(left, 0f)
        val slice = width / count
        return floatArrayOf(left + index * slice, slice)
    }
}
