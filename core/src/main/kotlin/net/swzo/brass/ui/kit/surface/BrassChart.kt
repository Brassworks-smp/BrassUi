package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.layout.BrassCull
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color
import kotlin.math.roundToInt
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * One or more series plotted over time - TPS, ping, memory, frame time.
 * ```kotlin
 * val chart = BrassChart(window = 120)
 * val tps = chart.series("TPS", Colors.BRASS_400)
 * // each tick:
 * chart.push(tps, currentTps)
 * ```
 * ### A ring buffer, not a list
 * A live chart is fed a sample per tick forever, and the obvious `list.add` plus `removeFirst` is a
 * shift of the whole window on every sample. Each series is a fixed-size ring instead: [push] writes
 * one slot and moves a cursor, so the cost per sample is constant and no allocation happens after the
 * series is created. That matters because this is the widget most likely to be on screen while
 * something is already going wrong with performance.
 * ### Autoscaling, with hysteresis
 * The y axis fits the data by default, but a scale recomputed exactly per frame makes a flat line
 * jitter and a spike squash everything else permanently. The maximum therefore grows immediately and
 * decays slowly, so a transient spike is visible and then quietly stops dominating the view.
 * Set [fixedMax] when the axis has a meaningful ceiling - 20 for TPS - and the whole question
 * disappears.
 */
class BrassChart(
    val window: Int = 120,
    var fixedMax: Float? = null,
    var min: Float = 0f,
) : BrassWidget(BrassAccent.DEFAULT) {

    inner class Series internal constructor(
        val label: String,
        val color: Color,
        val filled: Boolean,
    ) {
        internal val samples = FloatArray(window) { Float.NaN }
        internal var cursor = 0
        internal var count = 0

        val latest: Float? get() = if (count == 0) null else samples[(cursor - 1 + window) % window]

        internal fun at(index: Int): Float = samples[(cursor - count + index + window * 2) % window]
    }

    private val series = ArrayList<Series>()

    private var scaledMax = 1f

    var legend: Boolean = true

    var gridLines: Int = 3

    var format: (Float) -> String = { "%.1f".format(it) }

    var labelFor: (Int) -> String = { index ->
        val back = (sampleCount() - 1 - index).coerceAtLeast(0)
        if (back == 0) "now" else "-$back"
    }

    private var hoveredSlice = BrassPlotHover.NONE

    private var plotLeft = 0f
    private var plotWidth = 0f

    private fun sampleCount(): Int = series.maxOfOrNull { it.count } ?: 0

    init {
        // Attached once with suppliers, never from onMouseEnter: adding listeners while Elementa is
        // iterating them throws from updateCurrentlyHoveredState. Both suppliers answer from the
        // hover worked out during the last draw, and return blank when there is nothing under the
        // cursor - BrassTooltip draws nothing for that.
        BrassTooltip.attachLazy(
            this,
            title = { if (hoveredSlice == BrassPlotHover.NONE) "" else labelFor(hoveredSlice) },
            body = { if (hoveredSlice == BrassPlotHover.NONE) null else sliceSummary(hoveredSlice) },
        )
        // A BrassWidget, not a UIContainer. The base class is what runs the entrance animation, the
        // hover/press colour easing, the cursor request, the focus ring and BrassDevMode.inspect - a
        // raw Elementa container painting itself gets none of that and is invisible to the inspector.
        // chrome = NONE because this widget paints all of its own background.
        chrome = BrassChrome.NONE
        // A spike drawn at the very top would otherwise paint a pixel outside the card.
        enableEffect(ScissorEffect())
    }

    fun series(label: String, color: Color, filled: Boolean = false): Series =
        Series(label, color, filled).also { series.add(it) }

    fun push(target: Series, value: Float) {
        target.samples[target.cursor] = value
        target.cursor = (target.cursor + 1) % window
        if (target.count < window) target.count++
    }

    fun reset() {
        series.forEach { it.cursor = 0; it.count = 0; it.samples.fill(Float.NaN) }
        scaledMax = 1f
    }

    private fun ceiling(): Float {
        fixedMax?.let { return it }
        var peak = 0f
        for (s in series) {
            for (i in 0 until s.count) {
                val v = s.at(i)
                if (!v.isNaN() && v > peak) peak = v
            }
        }
        // Grow at once so a spike is never clipped; shrink slowly so the view does not breathe.
        scaledMax = if (peak > scaledMax) peak else scaledMax + (peak - scaledMax) * DECAY
        return (scaledMax * HEADROOM).coerceAtLeast(min + 1e-3f)
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun drawContent(matrixStack: UMatrixStack, bx: Int, by: Int, bw: Int, bh: Int) {

        val x = getLeft(); val y = getTop()
        val w = getWidth(); val h = getHeight()
        if (w <= 0f || h <= 0f) { super.draw(matrixStack); return }

        // panel, not draw: this sits under its own ScissorEffect (see init), which would clip away
        // draw's bled outer ring - see BrassCard.panel, the shared background for this whole family.
        BrassCard.panel(matrixStack, x, y, x + w, y + h)

        val top = y + if (legend) LEGEND_H else PAD
        val bottom = y + h - PAD
        val left = x + PAD
        val right = x + w - PAD
        val plotH = (bottom - top).coerceAtLeast(1f)
        val plotW = (right - left).coerceAtLeast(1f)
        val max = ceiling()

        plotLeft = left
        plotWidth = plotW
        val (cursorX, cursorY) = getMousePosition()
        hoveredSlice = BrassPlotHover.indexAt(this, cursorX, cursorY, left, plotW, sampleCount())

        drawGrid(matrixStack, left, top, right, bottom)

        // The hovered slice, behind the lines so it reads as a band the data sits on rather than a
        // bar drawn over it. A whole vertical slice, not a dot: at one pixel per sample there is no
        // aiming at a point, and the slice is what the tooltip is actually describing.
        if (hoveredSlice != BrassPlotHover.NONE) {
            val (sx, sw) = BrassPlotHover.sliceBounds(left, plotW, sampleCount(), hoveredSlice)
                .let { it[0] to it[1] }
            BrassPaint.rect(matrixStack, sx, top, sx + sw.coerceAtLeast(1f), top + plotH, SLICE)
        }

        for (s in series) {
            if (s.count < 2) continue
            drawSeries(matrixStack, s, left, top, plotW, plotH, max)
        }

        if (legend) drawLegend(matrixStack, left, y + 2f, right)

    }

    private fun drawGrid(m: UMatrixStack, left: Float, top: Float, right: Float, bottom: Float) {
        if (gridLines <= 0) return
        val step = (bottom - top) / (gridLines + 1)
        for (i in 1..gridLines) {
            val gy = top + step * i
            BrassPaint.rectSnapped(m, left, gy, right, gy + 1f, GRID)
        }
    }

    private fun drawSeries(
        m: UMatrixStack,
        s: Series,
        left: Float,
        top: Float,
        plotW: Float,
        plotH: Float,
        max: Float,
    ) {
        val span = (max - min).coerceAtLeast(1e-3f)
        fun heightOf(v: Float): Float = ((v - min) / span).coerceIn(0f, 1f) * plotH

        val columns = plotW.roundToInt().coerceAtLeast(1)
        var previous = Float.NaN

        for (column in 0 until columns) {
            // Map the column back onto the sample window, so a chart narrower than its window
            // subsamples rather than drawing off the end.
            val index = (column.toFloat() / columns * s.count).toInt().coerceIn(0, s.count - 1)
            val value = s.at(index)
            if (value.isNaN()) { previous = Float.NaN; continue }

            val cx = left + column
            val cy = top + plotH - heightOf(value)

            if (s.filled) {
                BrassPaint.rect(m, cx, cy, cx + 1f, top + plotH, fadeOf(s.color))
            }

            if (previous.isNaN()) {
                BrassPaint.rect(m, cx, cy, cx + 1f, cy + LINE, s.color)
            } else {
                // Span the gap between consecutive samples so a steep change stays connected.
                val py = top + plotH - heightOf(previous)
                val y1 = minOf(cy, py)
                val y2 = maxOf(cy, py) + LINE
                BrassPaint.rect(m, cx, y1, cx + 1f, y2, s.color)
            }
            previous = value
        }
    }

    private fun drawLegend(m: UMatrixStack, left: Float, y: Float, right: Float) {
        var cx = left
        for (s in series) {
            BrassPaint.rect(m, cx, y + BrassFont.LINE / 2f - 1f, cx + SWATCH, y + BrassFont.LINE / 2f + 1f, s.color)
            cx += SWATCH + 3f

            val value = s.latest?.let { format(it) }
            val text = if (value == null) s.label else "${s.label} $value"
            val fitted = BrassFont.fit(this, text, right - cx)
            BrassFont.draw(m, this, fitted, cx, y, Colors.UI_TEXT_DARK)
            cx += BrassFont.width(this, fitted) + GAP
            if (cx >= right) return
        }
    }

    private fun sliceSummary(index: Int): String? {
        val parts = series.mapNotNull { s ->
            val v = s.at(index.coerceIn(0, (s.count - 1).coerceAtLeast(0)))
            if (s.count == 0 || v.isNaN()) null else "${s.label} ${format(v)}"
        }
        return if (parts.isEmpty()) null else parts.joinToString("   ")
    }

    fun hovered(): Boolean {
        if (!BrassCull.visible(this)) return false
        val (mx, my) = getMousePosition()
        return mx >= getLeft() && mx <= getRight() && my >= getTop() && my <= getBottom()
    }

    private fun fadeOf(c: Color): Color = Color(c.red, c.green, c.blue, FILL_ALPHA)

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("chart", "Chart", 270f, 100f) {
            val chart = BrassChart(window = DEMO_WINDOW, fixedMax = 20f)
            val a = chart.series("Requests", Colors.BRASS_400, filled = true)
            val b = chart.series("Errors", Colors.PATINA_400)
            // Seeded so the plot opens full rather than drawing itself in from an empty axis.
            repeat(DEMO_WINDOW) { i -> chart.push(a, sample(i)); chart.push(b, noise(i)) }

            chart
        }

        private const val DEMO_WINDOW = 120

        private fun sample(i: Int): Float =
            20f - (kotlin.math.sin(i / 9.0).toFloat() + 1f) * (if (i % 100 in 70..80) 4f else 0.6f)

        private fun noise(i: Int): Float = 8f + kotlin.math.sin(i / 14.0).toFloat() * 3f

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val PAD = 3f
        private const val LEGEND_H = 12f
        private const val LINE = 1f
        private const val SWATCH = 5f
        private const val GAP = 8f
        private const val DECAY = 0.02f
        private const val HEADROOM = 1.1f
        private const val FILL_ALPHA = 40

        private val GRID: Color get() = Colors.UI_INNER_BORDER
        private val SLICE: Color get() = Colors.UI_SELECTION_FAINT
    }
}
