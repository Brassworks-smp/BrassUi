package net.swzo.brass.ui.kit.surface

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.paint.BrassCard
import kotlin.math.cos
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * An **indeterminate** loading bar: a brass block that sweeps back and forth inside a recessed track,
 * easing at each end.
 *
 * Deliberately the same chrome as [BrassProgressBar] - same groove, same border, same brass - so the two
 * read as one family and can swap places the moment real progress becomes known. Use this while a
 * duration is unknown, and [BrassProgressBar] once it isn't.
 *
 * (This replaced an earlier version that lit a row of discrete cells with an exponential comet trail.
 * It had more going on but read as noise at UI scale, and never matched the rest of the toolkit.)
 */
class BrassLoading(
    /** Fraction of the track the moving block occupies. */
    private val blockFraction: Float = 0.32f,
    /** Seconds for one full there-and-back sweep. */
    private val period: Float = 1.6f,
) : BrassWidget(BrassAccent.DEFAULT) {

    init {
        // The bar paints its own card through BrassCard; the keycap base must draw nothing behind it.
        chrome = BrassChrome.NONE
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val x1 = x.toFloat()
        val x2 = (x + w).toFloat()
        val y1 = y.toFloat()
        val y2 = (y + h).toFloat()

        // The card shell, the block and the border - the exact calls BrassProgressBar makes through
        // BrassCard.filledTrack. Sharing the primitives rather than the look is what actually keeps
        // the determinate and indeterminate bars identical; two hand-matched copies drifted apart.
        BrassCard.trackShell(m, x1, y1, x2, y2)

        // sweeping block: cosine easing, so it slows at each end instead of ticking side to side
        val t = (System.currentTimeMillis() % (period * 1000).toLong()) / (period * 1000f)
        val phase = ((1.0 - cos(t * 2.0 * Math.PI)) / 2.0).toFloat()
        val blockW = (w * blockFraction).coerceAtLeast(6f)
        val bx = Math.round(x1 + 1f + (w - 2f - blockW) * phase).toFloat()

        BrassCard.trackBlock(m, x1, y1, x2, y2, bx, bx + blockW)
        BrassCard.trackBorder(m, x1, y1, x2, y2)
    }

    companion object : BrassDemoSource {

        /**
         * The indeterminate sweep, one full cycle.
         *
         * No still: a frozen frame of this is a block sitting in a groove, which reads as a progress
         * bar stuck at some arbitrary value — the opposite of what an indeterminate indicator means.
         * The scene is a little longer than one `period` so the loop closes cleanly.
         */
        override fun demo() = BrassDemo("loading", "Loading", 190f, 14f) {
            BrassLoading()
        }
    }
}
