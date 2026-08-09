package net.swzo.brass.ui.kit.surface

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color
import kotlin.math.roundToInt
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A **determinate** progress bar: a recessed track with a brass fill, an optional caption on the left
 * and a percentage on the right.
 * Distinct from [BrassLoading], which is the *indeterminate* animation for "something is happening, no
 * idea how long". Use this one whenever real progress is known - a download, a world transfer, a
 * shard migration - since a bar that reflects actual progress is far more informative than a sweep.
 * [progress] is clamped to 0..1 and eased toward, so setting it in jumps still animates smoothly
 * rather than snapping.
 */
class BrassProgressBar(
    var label: String = "",
    private val showPercent: Boolean = true,
) : BrassWidget(BrassAccent.DEFAULT) {

    var progress: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f) }

    var failed: Boolean = false

    /**
     * Follow [state]: the bar's [progress] tracks it for as long as both live, so the work reporting
     * progress never has to hold a reference to the widget showing it.
     * Returns this bar, so it can be bound and parented in one expression.
     */
    fun bind(state: BrassState<Float>): BrassProgressBar {
        // The unsubscribe handle used to be discarded here. BrassState holds its listeners strongly,
        // so a state that outlived the screen - a download tracker on some long-lived object, say -
        // kept this bar, its parents, and therefore the entire screen tree alive for the rest of the
        // process. Reopening the screen added another. Registering the handle for teardown is what
        // makes binding against a long-lived state safe, which is the only interesting case.
        disposeWith(state.onChange { progress = it })
        return this
    }

    private val shownValue = BrassEased(0f, speed = FILL_SPEED)

    init {
        // The bar paints its own card through BrassCard; the keycap base must draw nothing behind it.
        // `transparent` alone was not enough - it drops the fill but still outlines the widget, which
        // put a second, offset border around every bar.
        chrome = BrassChrome.NONE
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        shownValue.target = progress
        val shown = shownValue.advance()

        val x1 = x.toFloat()
        val x2 = (x + w).toFloat()
        val y1 = y.toFloat()
        val y2 = (y + h).toFloat()

        // the shared BrassLoading-style groove, filled up to the progress
        if (failed) BrassCard.filledTrack(m, x1, y1, x2, y2, shown, FAIL, FAIL_LIT)
        else BrassCard.filledTrack(m, x1, y1, x2, y2, shown)

        // caption and percentage, both inside the bar
        val ty = (y + (h - BrassFont.LINE) / 2 + 1).toFloat()
        val pct = if (showPercent) "${(shown * 100).roundToInt()}%" else ""
        val pctW = if (pct.isEmpty()) 0f else BrassFont.width(this, pct) + 6f

        if (label.isNotEmpty()) {
            val room = (w - pctW - 10f).coerceAtLeast(0f)
            val shownLabel = BrassFont.fit(this, label, room)
            if (shownLabel.isNotEmpty()) {
                BrassFont.draw(m, this, shownLabel, x1 + 5f, ty, Colors.UI_TEXT, true)
            }
        }
        if (pct.isNotEmpty()) {
            BrassFont.draw(m, this, pct, x2 - pctW + 1f, ty, Colors.UI_TEXT_HOVER, true)
        }
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("progress-bar", "Progress bar", 200f, 14f) {
            SelfDriving(BrassProgressBar("Downloading"))
        }

        private class SelfDriving(private val bar: BrassProgressBar) : UIContainer() {

            private var elapsed = 0f

            init {
                bar.constrain {
                    x = 0.pixels(); y = 0.pixels()
                    width = 100.percent(); height = 100.percent()
                } childOf this
            }

            override fun draw(matrixStack: UMatrixStack) {
                elapsed += BrassClock.dt
                if (elapsed > CYCLE) elapsed = 0f
                bar.progress = at(elapsed)
                super.draw(matrixStack)
            }

            private fun at(t: Float): Float {
                var value = 0f
                for ((time, p) in KEYFRAMES) {
                    if (t < time) break
                    value = p
                }
                return value
            }

            private companion object {
                val KEYFRAMES = listOf(
                    0.0f to 0.00f,
                    0.3f to 0.18f,
                    0.8f to 0.42f,
                    1.4f to 0.55f,
                    // the stall every real download has
                    2.6f to 0.61f,
                    3.1f to 0.88f,
                    3.5f to 1.00f,
                )

                const val CYCLE = 5.0f
            }
        }

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val FILL_SPEED = 9f
        private val FAIL: Color get() = Colors.PROGRESS_FAIL
        private val FAIL_LIT: Color get() = Colors.PROGRESS_FAIL_LIT
    }
}
