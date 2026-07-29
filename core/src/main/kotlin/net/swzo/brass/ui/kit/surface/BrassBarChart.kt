package net.swzo.brass.ui.kit.surface

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassEased
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * Values by category - sales per item, kills per player, votes per map, uptime per shard.
 *
 * ```kotlin
 * val chart = BrassBarChart()
 * chart.setBars(listOf(
 *     BrassBarChart.Bar("Diamond", 128f),
 *     BrassBarChart.Bar("Iron", 96f, Colors.PATINA_400),
 * ))
 * ```
 *
 * ### Why not a mode on [BrassChart]
 *
 * They take different data. [BrassChart] plots a *series over time*: a fixed window fed one sample per
 * tick, where the x axis is duration and the newest value is the interesting one. This plots *labelled
 * categories* - the set is small, arbitrary in order, and every bar wants its name next to it. Folding
 * both into one widget would mean a `mode` flag that changes what half the properties mean, which is
 * how a widget ends up with two of everything.
 *
 * Hovering works the same way in both, through [BrassPlotHover], so a chart and a bar chart in the
 * same panel do not disagree about which bucket the cursor is in.
 *
 * Bars ease toward their value rather than snapping, so a leaderboard that refreshes reads as
 * positions *moving* instead of flickering to a new arrangement.
 */
class BrassBarChart : BrassWidget(BrassAccent.DEFAULT) {

    /**
     * One category.
     *
     * [color] null takes the theme accent, which is the right default and the one to keep for a chart
     * that is about the *shape* of the data rather than about telling categories apart - a set of bars
     * in five arbitrary colours reads as five unrelated things. Give a colour only where it means
     * something (a threshold, a status, a team).
     */
    class Bar(val label: String, val value: Float, val color: Color? = null)

    /** Where a bar's value is written. */
    enum class Values {
        /** Not at all - the tooltip still has it. */
        NONE,
        /** Just above the bar's top edge, outside it. */
        ABOVE,
        /** Inside the bar, at the top. Falls back to [ABOVE] on a bar too short to hold the text. */
        INSIDE,
    }

    /**
     * Whether each bar is labelled with its value, and where.
     *
     * Off by default: on a dense chart the numbers collide with each other long before they become
     * more useful than the tooltip. Turn it on for a handful of bars a reader is meant to compare
     * precisely rather than by eye.
     */
    var values: Values = Values.NONE

    private var bars: List<Bar> = emptyList()

    /** One eased height per bar, so a refresh animates instead of jumping. */
    private val grown = ArrayList<BrassEased>()

    /** Pin the top of the axis instead of fitting the tallest bar. */
    var fixedMax: Float? = null

    /** Show each bar's label under it. Turned off for a sparkline-ish strip. */
    var labels: Boolean = true

    /** How a value is written in the tooltip. */
    var format: (Float) -> String = { if (it == it.toInt().toFloat()) it.toInt().toString() else "%.1f".format(it) }

    /** The bar under the cursor, or [BrassPlotHover.NONE]. */
    private var hoveredBar = BrassPlotHover.NONE

    init {
        // Paints its own card; the keycap machinery must draw nothing behind it.
        chrome = BrassChrome.NONE
        // Attached once with suppliers - see BrassChart for why never from onMouseEnter.
        BrassTooltip.attachLazy(
            this,
            title = { bars.getOrNull(hoveredBar)?.label ?: "" },
            body = { bars.getOrNull(hoveredBar)?.let { format(it.value) } },
        )
    }

    /** Replace the bars. Heights ease from wherever they were. */
    fun setBars(next: List<Bar>) {
        bars = next
        while (grown.size < next.size) grown.add(BrassEased(0f, speed = GROW_SPEED))
        while (grown.size > next.size) grown.removeAt(grown.size - 1)
    }

    val size: Int get() = bars.size

    /** The tallest value, or the pinned ceiling. Never zero, so a flat set still divides. */
    private fun ceiling(): Float =
        fixedMax ?: (bars.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(1e-3f)

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val left = x + PAD
        val right = x + w - PAD
        // Values written above the bars need a band reserved for them, or the tallest bar - which by
        // definition reaches the top of the plot - has its number drawn off the card.
        val top = y + PAD + if (values == Values.ABOVE) BrassFont.LINE + 1f else 0f
        val bottom = y + h - PAD - if (labels) BrassFont.LINE + 2f else 0f
        val plotW = (right - left).coerceAtLeast(1f)
        val plotH = (bottom - top).coerceAtLeast(1f)

        // panel, not draw: see BrassCard.panel - this is the shared background every self-painting,
        // possibly-clipped surface in the toolkit uses now, so a bar chart, a line chart and a code
        // view all read as the same double-ringed card rather than three near-misses.
        BrassCard.panel(m, x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())

        if (bars.isEmpty()) return

        val (cursorX, cursorY) = getMousePosition()
        hoveredBar = BrassPlotHover.indexAt(this, cursorX, cursorY, left, plotW, bars.size)

        val max = ceiling()
        val slice = plotW / bars.size

        for ((i, bar) in bars.withIndex()) {
            val eased = grown.getOrNull(i) ?: continue
            eased.target = (bar.value / max).coerceIn(0f, 1f)
            val fraction = eased.advance()

            val bx = left + i * slice
            // The hovered slice is washed across its full height, so the whole column is the hit
            // target and the tooltip clearly belongs to it - the same treatment BrassChart gives a
            // time slice.
            if (i == hoveredBar) BrassPaint.rect(m, bx, top, bx + slice, bottom, SLICE)

            val inset = (slice * GAP_RATIO).coerceIn(1f, 4f)
            val barTop = bottom - plotH * fraction
            val tint = bar.color ?: Colors.UI_ACCENT
            val bl = bx + inset
            val br = bx + slice - inset
            if (bottom - barTop >= 1f) {
                BrassPaint.rect(m, bl, barTop, br, bottom, tint)
                // A 1-px outline *inside* the bar, a step brighter than its own fill - the read
                // BrassCard.trackBlock gives a filled region. Derived from the bar's colour rather
                // than being the theme's bright accent, which was only ever right for the bars that
                // had no colour of their own and made every custom-coloured bar wear someone else's
                // highlight.
                BrassPaint.border(m, bl, barTop, br, bottom, Colors.lighten(tint, OUTLINE_LIFT))
            }

            if (values != Values.NONE) paintValue(m, bar, bl, br, barTop, bottom, i == hoveredBar)

            if (labels) {
                val fitted = BrassFont.fit(this, bar.label, slice - 2f)
                BrassFont.draw(
                    m, this, fitted,
                    bx + (slice - BrassFont.width(this, fitted)) / 2f,
                    bottom + 2f,
                    if (i == hoveredBar) Colors.UI_TEXT_HOVER else Colors.UI_TEXT_DARK,
                )
            }
        }

        // The baseline last, so bars sit on it rather than over it.
        BrassPaint.rectSnapped(m, left, bottom, right, bottom + 1f, BASELINE)
    }

    /**
     * The value written on one bar.
     *
     * [Values.INSIDE] needs the bar to be tall enough to hold a line of text *and* stay clear of its
     * own outline; below that it is drawn above the bar instead. Silently, and deliberately - the
     * alternative is a number half outside a short bar, or one that disappears exactly when the value
     * is small enough to be worth reading.
     */
    private fun paintValue(
        m: UMatrixStack,
        bar: Bar,
        left: Float,
        right: Float,
        barTop: Float,
        bottom: Float,
        hot: Boolean,
    ) {
        val text = BrassFont.fit(this, format(bar.value), right - left)
        if (text.isEmpty()) return
        val inside = values == Values.INSIDE && bottom - barTop >= BrassFont.LINE + VALUE_PAD * 2f
        val ty = if (inside) barTop + VALUE_PAD else barTop - BrassFont.LINE - 1f
        BrassFont.draw(
            m, this, text,
            left + (right - left - BrassFont.width(this, text)) / 2f,
            ty,
            // Inside the bar the text sits on the fill, so it has to be light enough to survive it;
            // above it, it is on the card and follows the same muted/hover pair as the axis labels.
            when {
                inside -> Colors.lighten(bar.color ?: Colors.UI_ACCENT, VALUE_LIFT)
                hot -> Colors.UI_TEXT_HOVER
                else -> Colors.UI_TEXT_DARK
            },
        )
    }

    companion object : BrassDemoSource {

        /**
         * Categories with values, and a bar hovered for its readout.
         *
         * The colours are mostly left to the theme accent on purpose — the class docs argue that bars
         * in five arbitrary colours read as five unrelated things — with two picked out where the
         * colour carries meaning, which is the usage worth demonstrating.
         */
        override fun demo() = BrassDemo("bar-chart", "Bar chart", 250f, 96f) {
            val chart = BrassBarChart()
            chart.setBars(listOf(
                Bar("Mon", 128f),
                Bar("Tue", 96f),
                Bar("Wed", 54f, Colors.WARN),
                Bar("Thu", 12f, Colors.DANGER),
                Bar("Fri", 71f),
            ))
            chart.values = Values.INSIDE
            chart
        }

        // ---- widget internals ------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val PAD = 4f
        /** How far toward white a bar's outline sits above its fill. */
        private const val OUTLINE_LIFT = 0.35f
        /** The same, for a value written on top of the fill - further, so it stays readable. */
        private const val VALUE_LIFT = 0.75f
        /** Gap between a bar's top edge and a value written inside it. */
        private const val VALUE_PAD = 2f
        /** Fraction of a slice left as the gap between neighbouring bars. */
        private const val GAP_RATIO = 0.18f
        /** How fast a bar grows toward a new value. */
        private const val GROW_SPEED = 9f

        private val SLICE: Color get() = Colors.UI_SELECTION_FAINT
        private val BASELINE: Color get() = Colors.UI_INNER_BORDER
    }
}
