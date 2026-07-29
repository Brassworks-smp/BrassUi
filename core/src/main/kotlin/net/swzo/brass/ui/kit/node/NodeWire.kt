package net.swzo.brass.ui.kit.node

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.paint.BrassPaint
import java.awt.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/**
 * The wires between ports, drawn as **even pixel-art curves** in the spirit of the Create mod's train
 * map: one continuous cubic Bézier laid down as a run of identical square stamps that are spaced by
 * **arc length**, not by curve parameter.
 *
 * ### Why arc length is the whole trick
 *
 * Sampling a Bézier at a uniform `t` places samples close together where the curve is straight and far
 * apart where it bends - so a naive stamp-per-sample wire is dense in the flat middle and sparse (even
 * gappy) through the bends, which is exactly the "too dense here, too thin there" look. Instead we
 * sample once into a fine polyline, measure its cumulative length, then walk that length in fixed
 * [SPACING] world-unit steps dropping one stamp per step. Every stamp is then the same distance from the
 * next regardless of curvature, so the line has one uniform density end to end.
 *
 * ### One pixel, once
 *
 * Each step floors to a whole **world** cell and the cells are de-duplicated, so a pixel the walk visits
 * twice (where consecutive steps land in the same cell) is painted once. That is what lets the shadow
 * and the selection halo use translucent colours without the overlaps stacking into dark knots. Because
 * every cell is a whole world unit drawn under the canvas' scaled matrix, the wire snaps to the same
 * pixel grid as the nubs and the background grid and scales crisply with the zoom.
 *
 * Passes, back to front: a one-cell depth shadow, an optional selection/hover halo one cell wider, the
 * coloured core, then a slow travelling mote so the graph reads as live.
 */
object NodeWire {

    private const val SPACING = 1f
    private const val CORE = 2f
    private const val DASH_ON = 5f
    private const val DASH_OFF = 4f

    /** The wire from an output at ([x0],[y0]) to an input at ([x3],[y3]). */
    fun draw(
        ctx: NodeDrawCtx,
        x0: Float, y0: Float, x3: Float, y3: Float,
        color: Color,
        sel: Float,
        flash: Float,
        dashed: Boolean = false,
        arrow: Boolean = false,
        symbol: String? = null,
    ) {
        val h = handle(x0, x3)
        val cx1 = x0 + h; val cy1 = y0
        val cx2 = x3 - h; val cy2 = y3
        val cells = stampCells(x0, y0, cx1, cy1, cx2, cy2, x3, y3, dashed)

        // Depth shadow, one world cell below.
        stamp(ctx.m, cells, CORE, 0f, 1f, SHADOW)
        // Selection / hover halo, one cell wider all round, drawn under the core.
        if (sel > 0.01f) {
            val halo = Colors.withAlpha(Colors.mix(color, Color.WHITE, 0.4f), (255 * sel).toInt())
            stamp(ctx.m, cells, CORE + 2f, -1f, -1f, halo)
        }
        // Core.
        val base = if (flash > 0f) Colors.mix(color, Color.WHITE, flash * 0.7f) else color
        stamp(ctx.m, cells, CORE, 0f, 0f, base)

        // Travelling motes.
        if (!dashed) {
            val mote = Colors.mix(color, Color.WHITE, 0.55f)
            for (k in 0..2) {
                val t = ((ctx.time * 0.35f + k / 3f) % 1f)
                val (px, py) = bezier(x0, y0, cx1, cy1, cx2, cy2, x3, y3, t)
                cell(ctx.m, floor(px - 1f), floor(py - 1f), CORE, mote)
            }
        }

        if (arrow) {
            val (ax, ay) = bezier(x0, y0, cx1, cy1, cx2, cy2, x3, y3, 0.76f)
            NodeGlyph.arrow(ctx.m, ax, ay, left = false, color = Colors.mix(color, Color.WHITE, 0.25f))
        }
        if (symbol != null && ctx.zoom > 0.65f) {
            val (sx, sy) = bezier(x0, y0, cx1, cy1, cx2, cy2, x3, y3, 0.5f)
            val label = net.swzo.brass.ui.kit.text.BrassFont.fit(ctx.host, symbol, 18f)
            net.swzo.brass.ui.kit.text.BrassFont.draw(
                ctx.m, ctx.host, label,
                sx - net.swzo.brass.ui.kit.text.BrassFont.width(ctx.host, label) / 2f,
                sy - net.swzo.brass.ui.kit.text.BrassFont.LINE - 2f,
                Colors.UI_TEXT_DARK,
            )
        }
    }

    /** Distance from world point ([wx],[wy]) to the wire, for hit-testing a click on the line itself. */
    fun distanceTo(wx: Float, wy: Float, x0: Float, y0: Float, x3: Float, y3: Float): Float {
        val h = handle(x0, x3)
        val cx1 = x0 + h; val cx2 = x3 - h
        var best = Float.MAX_VALUE
        var i = 0
        while (i <= 40) {
            val (px, py) = bezier(x0, y0, cx1, y0, cx2, y3, x3, y3, i / 40f)
            best = minOf(best, hypot(wx - px, wy - py))
            i++
        }
        return best
    }

    private fun handle(x0: Float, x3: Float): Float = max(30f, abs(x3 - x0) * 0.5f)

    /**
     * Sample the cubic into a fine polyline, then walk it by arc length dropping one de-duplicated whole
     * world cell every [SPACING] units - the even stamp run every pass then reuses. Packed as `ix<<32 |
     * iy` so a visited-set can reject a repeat pixel in O(1).
     */
    private fun stampCells(
        x0: Float, y0: Float, cx1: Float, cy1: Float, cx2: Float, cy2: Float, x3: Float, y3: Float,
        dashed: Boolean,
    ): LongArray {
        // A generous polyline: the control polygon's length caps how far the curve can wander, so it
        // sizes the sampling without a chicken-and-egg measure of the curve itself.
        val poly = hypot(cx1 - x0, cy1 - y0) + hypot(cx2 - cx1, cy2 - cy1) + hypot(x3 - cx2, y3 - cy2)
        val n = (poly / 2f).toInt().coerceIn(32, 300)
        val px = FloatArray(n + 1); val py = FloatArray(n + 1)
        for (i in 0..n) {
            val (bx, by) = bezier(x0, y0, cx1, cy1, cx2, cy2, x3, y3, i / n.toFloat())
            px[i] = bx; py[i] = by
        }

        val cells = ArrayList<Long>(n + 1)
        val seen = HashSet<Long>(n * 2)
        var carried = 0f
        var i = 0
        while (i < n) {
            val segLen = hypot(px[i + 1] - px[i], py[i + 1] - py[i])
            if (segLen < 1e-4f) { i++; continue }
            var d = SPACING - carried
            while (d <= segLen) {
                val f = d / segLen
                addCell(cells, seen, px[i] + (px[i + 1] - px[i]) * f, py[i] + (py[i + 1] - py[i]) * f, dashed, i, f, n)
                d += SPACING
            }
            carried = (segLen - (d - SPACING)).let { if (it < 0f) 0f else it }
            i++
        }
        // Always seat the endpoints so a wire never stops a pixel short of its nub.
        addCell(cells, seen, x0, y0, false, 0, 0f, n)
        addCell(cells, seen, x3, y3, false, n, 1f, n)
        return cells.toLongArray()
    }

    private fun addCell(
        cells: ArrayList<Long>, seen: HashSet<Long>, x: Float, y: Float,
        dashed: Boolean, seg: Int, f: Float, n: Int,
    ) {
        if (dashed) {
            // Dash by arc position along the polyline (segment index is a fine proxy at this density).
            val at = (seg + f) / (n + 1f) * n
            if (at % (DASH_ON + DASH_OFF) >= DASH_ON) return
        }
        val ix = floor(x).toInt(); val iy = floor(y).toInt()
        val key = (ix.toLong() shl 32) or (iy.toLong() and 0xFFFFFFFFL)
        if (seen.add(key)) cells.add(key)
    }

    /** Paint every cell as a [size] square, shifted by ([dx],[dy]) world units, in [color]. */
    private fun stamp(m: UMatrixStack, cells: LongArray, size: Float, dx: Float, dy: Float, color: Color) {
        for (key in cells) {
            val ix = (key shr 32).toInt().toFloat()
            val iy = (key and 0xFFFFFFFFL).toInt().toFloat()
            cell(m, ix + dx, iy + dy, size, color)
        }
    }

    private fun cell(m: UMatrixStack, ix: Float, iy: Float, size: Float, color: Color) =
        BrassPaint.rect(m, ix, iy, ix + size, iy + size, color)

    private fun bezier(
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, t: Float,
    ): Pair<Float, Float> {
        val u = 1f - t
        val a = u * u * u; val b = 3f * u * u * t; val c = 3f * u * t * t; val d = t * t * t
        return (a * x0 + b * x1 + c * x2 + d * x3) to (a * y0 + b * y1 + c * y2 + d * y3)
    }

    private val SHADOW: Color get() = Colors.withAlpha(Color.BLACK, 120)
}
