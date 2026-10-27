package net.swzo.brass.ui.kit.paint

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import java.awt.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/**
 * The toolkit's connection wire - the node editor's wires, usable anywhere (the quest tree draws
 * the same ones). Drawn in world units under the caller's (possibly scaled) matrix, as **even pixel-art
 * curves** in the spirit of the Create mod's train map: one continuous cubic Bézier laid down as a run of
 * identical square stamps spaced by **arc length**, not by curve parameter.
 * ### Why arc length is the whole trick
 * Sampling a Bézier at a uniform `t` places samples close together where the curve is straight and far
 * apart where it bends - so a naive stamp-per-sample wire is dense in the flat middle and sparse (even
 * gappy) through the bends. Instead we sample once into a fine polyline, measure its cumulative length,
 * then walk that length in fixed steps dropping one stamp per step, so the line has one uniform density
 * end to end.
 * ### One pixel, once
 * Each step floors to a whole **world** cell and the cells are de-duplicated, so a pixel the walk visits
 * twice is painted once. That is what lets the shadow and the selection halo use translucent colours
 * without the overlaps stacking into dark knots, and why the wire scales crisply with the zoom.
 * Passes, back to front: a one-cell depth shadow, an optional selection/hover halo one cell wider, the
 * coloured core, then slow travelling motes while the wire carries a signal ([draw]'s `strength`).
 */
object BrassWire {

    /** Per-frame drawing state: the world matrix, its zoom, a clock for the motes, and the visible world rect. */
    class Frame @JvmOverloads constructor(
        val m: UMatrixStack,
        val zoom: Float,
        val time: Float,
        val visMinX: Float = Float.NEGATIVE_INFINITY,
        val visMinY: Float = Float.NEGATIVE_INFINITY,
        val visMaxX: Float = Float.POSITIVE_INFINITY,
        val visMaxY: Float = Float.POSITIVE_INFINITY,
    ) {
        fun visible(x1: Float, y1: Float, x2: Float, y2: Float): Boolean =
            x2 >= visMinX && x1 <= visMaxX && y2 >= visMinY && y1 <= visMaxY
    }

    private const val SPACING = 1f
    private const val CORE = 2f
    private const val WIRE_LOD_MIN = 0.42f
    private const val WIRE_LOD_MAX = 0.55f
    private const val SEGMENT_PX = 4f
    private const val DASH_ON = 5f
    private const val DASH_OFF = 4f
    private const val EPS = 0.01f
    private const val TRAIL_STEP = 0.012f

    /**
     * Draw a wire from ([x0], [y0]) to ([x3], [y3]).
     * @param sel 0..1 selection/hover halo.
     * @param flash 0..1 brightens the whole wire towards white (a pulse just sent along it).
     * @param strength 0..1 signal: 0 is dimmed and idle, above 0 brightens and runs motes faster.
     * @param moteFade 0..1 how strongly the motes show (fade them in late when zooming in).
     * @param reveal 0..1 how much of the wire is drawn from its start (draw-in animations); motes wait for 1.
     * @param vertical leave and arrive vertically (top-down layouts) instead of horizontally.
     * @param colorEnd blend the core from [color] at the start to this at the end (null = one colour).
     * @param glow 0..1 a soft wide glow under the wire while it carries a signal.
     * @param trail cells of fading tail behind each mote (0 = the editor's single-cell motes).
     */
    @JvmOverloads
    fun draw(
        f: Frame,
        x0: Float, y0: Float, x3: Float, y3: Float,
        color: Color,
        sel: Float = 0f,
        flash: Float = 0f,
        dashed: Boolean = false,
        strength: Float = 1f,
        moteFade: Float = 1f,
        reveal: Float = 1f,
        vertical: Boolean = false,
        colorEnd: Color? = null,
        glow: Float = 0f,
        trail: Int = 0,
    ) {
        if (reveal <= 0f) return
        val c = controls(x0, y0, x3, y3, vertical)
        val cx1 = c[0]; val cy1 = c[1]; val cx2 = c[2]; val cy2 = c[3]
        // One screen pixel in world units, the floor for every wire piece at any zoom - nothing
        // sub-pixel can ever rasterize as a triangle fragment.
        val px = maxOf(1f, 1f / f.zoom)
        val s = strength.coerceIn(0f, 1f)
        // Core. A wire carrying a signal brightens towards white as the strength rises; a wire with no
        // signal sits dimmed and darker so the topology is still visible but clearly idle.
        fun signal(c: Color): Color = when {
            flash > 0f -> Colors.mix(c, Color.WHITE, flash * 0.7f)
            s > 0f -> Colors.mix(c, Color.WHITE, 0.18f + s * 0.5f)
            else -> Colors.mix(c, Color.BLACK, 0.42f)
        }
        val base = signal(color)
        val baseEnd = colorEnd?.let { signal(it) }

        // Two quality tiers over the same bezier, cross-fading by zoom: the full stamp-cell curve at
        // high zoom, a cheaper thick polyline (segments fall off with zoom) at overview - so wires
        // stay curved everywhere but cost like the old straight lines when zoomed way out.
        val stamps = stampTier(f.zoom)
        val batch = BrassPaint.QuadBatch(f.m)
        val whole = reveal >= 1f

        if (stamps > 0.01f) {
            val size = maxOf(CORE, px)
            val spacing = maxOf(SPACING, px / 2f)
            val all = stampCells(x0, y0, cx1, cy1, cx2, cy2, x3, y3, dashed, spacing)
            val cells = if (whole) all else all.copyOf((all.size * reveal).toInt())
            val saved = BrassAmbientFade.current
            BrassAmbientFade.current = saved * stamps
            // Soft glow first, a faint wash two cells out, only while energised.
            if (glow > 0.01f && (s > 0f || flash > 0f)) {
                val g = Colors.withAlpha(color, (38 * glow.coerceAtMost(1f) * maxOf(s, flash)).toInt())
                stamp(batch, f, cells, size + 4f * px, -2f * px, -2f * px, g)
            }
            // Depth shadow, one world cell below.
            stamp(batch, f, cells, size, 0f, size / 2f, SHADOW)
            // Selection / hover halo, one cell wider all round, drawn under the core.
            if (sel > 0.01f) {
                val halo = Colors.withAlpha(Colors.mix(color, Color.WHITE, 0.4f), (255 * sel.coerceAtMost(1f)).toInt())
                stamp(batch, f, cells, size + 2f * px, -px, -px, halo)
            }
            if (baseEnd == null) stamp(batch, f, cells, size, 0f, 0f, base)
            else {
                // gradient core: each cell's colour by its place along the full walk
                val last = (all.size - 1).coerceAtLeast(1).toFloat()
                for (i in cells.indices) {
                    val key = cells[i]
                    val ix = (key shr 32).toInt().toFloat()
                    val iy = (key and 0xFFFFFFFFL).toInt().toFloat()
                    cell(batch, f, ix, iy, size, Colors.mix(base, baseEnd, i / last))
                }
            }

            // Travelling motes: only while a signal is present, and only on a fully drawn wire.
            if (whole && !dashed && s > 0f && moteFade > 0.01f) {
                val mote = Colors.withAlpha(Colors.mix(base, Color.WHITE, 0.55f), (255 * moteFade.coerceAtMost(1f)).toInt())
                val speed = 0.18f + s * 1.1f
                for (k in 0..2) {
                    val t = ((f.time * speed + k / 3f) % 1f)
                    // a comet's tail: earlier positions along the curve, fading out
                    for (j in trail downTo 0) {
                        val tj = t - j * TRAIL_STEP
                        if (tj < 0f) continue
                        val (mx, my) = bezier(x0, y0, cx1, cy1, cx2, cy2, x3, y3, tj)
                        val c = if (j == 0) mote else Colors.withAlpha(mote, (mote.alpha * (1f - j / (trail + 1f)) * 0.7f).toInt())
                        cell(batch, f, snap(mx - size / 2f), snap(my - size / 2f), size, c)
                    }
                }
            }
            BrassAmbientFade.current = saved
        }

        if (stamps < 0.99f) {
            drawPolyline(batch, f, x0, y0, cx1, cy1, cx2, cy2, x3, y3, base, 1f - stamps, reveal.coerceAtMost(1f))
        }

        batch.flush()
    }

    /** 0..1 how much of the stamp-cell tier shows at [zoom] (the rest is the overview polyline). */
    fun stampTier(zoom: Float): Float = smoothstep(WIRE_LOD_MIN, WIRE_LOD_MAX, zoom)

    /** The point at [t] (0..1) along the wire [draw] would draw between the same ends. */
    @JvmOverloads
    fun point(x0: Float, y0: Float, x3: Float, y3: Float, t: Float, vertical: Boolean = false): Pair<Float, Float> {
        val c = controls(x0, y0, x3, y3, vertical)
        return bezier(x0, y0, c[0], c[1], c[2], c[3], x3, y3, t)
    }

    /** Distance from a world point to the wire, for hit testing. */
    @JvmOverloads
    fun distanceTo(wx: Float, wy: Float, x0: Float, y0: Float, x3: Float, y3: Float, vertical: Boolean = false): Float {
        val c = controls(x0, y0, x3, y3, vertical)
        var best = Float.MAX_VALUE
        var i = 0
        while (i <= 40) {
            val (px, py) = bezier(x0, y0, c[0], c[1], c[2], c[3], x3, y3, i / 40f)
            best = minOf(best, hypot(wx - px, wy - py))
            i++
        }
        return best
    }

    fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun controls(x0: Float, y0: Float, x3: Float, y3: Float, vertical: Boolean): FloatArray =
        if (vertical) {
            val h = max(30f, abs(y3 - y0) * 0.5f)
            floatArrayOf(x0, y0 + h, x3, y3 - h)
        } else {
            val h = max(30f, abs(x3 - x0) * 0.5f)
            floatArrayOf(x0 + h, y0, x3 - h, y3)
        }

    private fun drawPolyline(
        batch: BrassPaint.QuadBatch,
        f: Frame,
        x0: Float, y0: Float, cx1: Float, cy1: Float, cx2: Float, cy2: Float, x3: Float, y3: Float,
        color: Color, alpha: Float, reveal: Float,
    ) {
        val poly = hypot(cx1 - x0, cy1 - y0) + hypot(cx2 - cx1, cy2 - cy1) + hypot(x3 - cx2, y3 - cy2)
        val segments = ((poly * f.zoom) / SEGMENT_PX).toInt().coerceIn(4, 256)
        val last = (segments * reveal).toInt()
        // Never thinner than one screen pixel, so the rotated segments can't fragment.
        val half = maxOf(0.7f, 0.5f / f.zoom)
        val c = BrassPaint.fade(color, alpha)
        var prevX = x0
        var prevY = y0
        for (i in 1..last) {
            val (bx, by) = bezier(x0, y0, cx1, cy1, cx2, cy2, x3, y3, i / segments.toFloat())
            val dx = bx - prevX
            val dy = by - prevY
            val len = hypot(dx, dy)
            if (len > 1e-4f) {
                val nx = -dy / len * half
                val ny = dx / len * half
                batch.quad(
                    prevX + nx, prevY + ny, bx + nx, by + ny,
                    bx - nx, by - ny, prevX - nx, prevY - ny,
                    c,
                )
            }
            prevX = bx
            prevY = by
        }
    }

    /** Cells in walk order, start to end - the order is what [draw]'s `reveal` draws in by. */
    private fun stampCells(
        x0: Float, y0: Float, cx1: Float, cy1: Float, cx2: Float, cy2: Float, x3: Float, y3: Float,
        dashed: Boolean,
        spacing: Float = SPACING,
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
        // Always seat the endpoints so a wire never stops a pixel short of its nub.
        addCell(cells, seen, x0, y0, false, 0, 0f, n)
        var carried = 0f
        var i = 0
        while (i < n) {
            val segLen = hypot(px[i + 1] - px[i], py[i + 1] - py[i])
            if (segLen < 1e-4f) { i++; continue }
            var d = spacing - carried
            while (d <= segLen) {
                val fr = d / segLen
                addCell(cells, seen, px[i] + (px[i + 1] - px[i]) * fr, py[i] + (py[i + 1] - py[i]) * fr, dashed, i, fr, n)
                d += spacing
            }
            carried = (segLen - (d - spacing)).let { if (it < 0f) 0f else it }
            i++
        }
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
        val ix = snap(x).toInt(); val iy = snap(y).toInt()
        val key = (ix.toLong() shl 32) or (iy.toLong() and 0xFFFFFFFFL)
        if (seen.add(key)) cells.add(key)
    }

    private fun stamp(
        batch: BrassPaint.QuadBatch, f: Frame, cells: LongArray,
        size: Float, dx: Float, dy: Float, color: Color,
    ) {
        for (key in cells) {
            val ix = (key shr 32).toInt().toFloat()
            val iy = (key and 0xFFFFFFFFL).toInt().toFloat()
            cell(batch, f, ix + dx, iy + dy, size, color)
        }
    }

    private fun cell(batch: BrassPaint.QuadBatch, f: Frame, ix: Float, iy: Float, size: Float, color: Color) {
        if (!f.visible(ix, iy, ix + size, iy + size)) return
        batch.rect(ix, iy, ix + size, iy + size, color)
    }

    /**
     * Floor to the world cell, forgiving float noise: Bézier sums land a straight wire at 4.9999 for 5,
     * which floored to the row above and left one-pixel bumps along straight runs.
     */
    private fun snap(v: Float): Float = floor(v + EPS)

    private fun bezier(
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, t: Float,
    ): Pair<Float, Float> {
        val u = 1f - t
        val a = u * u * u; val b = 3f * u * u * t; val c = 3f * u * t * t; val d = t * t * t
        return (a * x0 + b * x1 + c * x2 + d * x3) to (a * y0 + b * y1 + c * y2 + d * y3)
    }

    private val SHADOW: Color get() = Colors.withAlpha(Color.BLACK, 120)
}
