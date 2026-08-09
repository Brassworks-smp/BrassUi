@file:Suppress("unused")
package net.swzo.brass.ui.kit.layout

/**
 * An axis-aligned rectangle.
 * Replaces the `FloatArray(4)` that [BrassCull], [net.swzo.brass.ui.kit.surface.BrassTooltip] and
 * [net.swzo.brass.ui.kit.surface.BrassTable] passed around. Two problems with the array: it allocated
 * on every call - and `clipOf` is called per frame by anything that paints many pieces - and call
 * sites read `clip[0]` and `clip[2]`, which is unreadable and one transposition away from a bug that
 * only shows as "culling is slightly wrong near the edges".
 * A `data class` rather than a value class because it carries four fields; the allocation it saves is
 * the *repeated* one, via [mutate] on a reusable instance.
 */
data class BrassRect(
    var left: Float = 0f,
    var top: Float = 0f,
    var right: Float = 0f,
    var bottom: Float = 0f,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun mutate(l: Float, t: Float, r: Float, b: Float): BrassRect {
        left = l; top = t; right = r; bottom = b
        return this
    }

    fun overlaps(other: BrassRect): Boolean =
        other.right > left && other.left < right && other.bottom > top && other.top < bottom

    fun overlaps(l: Float, t: Float, r: Float, b: Float): Boolean =
        r > left && l < right && b > top && t < bottom

    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom

    fun intersect(l: Float, t: Float, r: Float, b: Float): BrassRect =
        mutate(maxOf(left, l), maxOf(top, t), minOf(right, r), minOf(bottom, b))

    fun expand(by: Float): BrassRect = mutate(left - by, top - by, right + by, bottom + by)

    companion object {
        fun infinite() = BrassRect(
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
        )
    }
}
