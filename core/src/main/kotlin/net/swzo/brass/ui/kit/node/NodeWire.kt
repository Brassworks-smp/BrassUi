package net.swzo.brass.ui.kit.node

import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAmbientFade
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
 * ### Why arc length is the whole trick
 * Sampling a Bézier at a uniform `t` places samples close together where the curve is straight and far
 * apart where it bends - so a naive stamp-per-sample wire is dense in the flat middle and sparse (even
 * gappy) through the bends, which is exactly the "too dense here, too thin there" look. Instead we
 * sample once into a fine polyline, measure its cumulative length, then walk that length in fixed
 * [SPACING] world-unit steps dropping one stamp per step. Every stamp is then the same distance from the
 * next regardless of curvature, so the line has one uniform density end to end.
 * ### One pixel, once
 * Each step floors to a whole **world** cell and the cells are de-duplicated, so a pixel the walk visits
 * twice (where consecutive steps land in the same cell) is painted once. That is what lets the shadow
 * and the selection halo use translucent colours without the overlaps stacking into dark knots. Because
 * every cell is a whole world unit drawn under the canvas' scaled matrix, the wire snaps to the same
 * pixel grid as the nubs and the background grid and scales crisply with the zoom.
 * Passes, back to front: a one-cell depth shadow, an optional selection/hover halo one cell wider, the
 * coloured core, then a slow travelling mote so the graph reads as live.
 */
object NodeWire {

    private const val SPACING = 1f
    private const val CORE = 2f
    private const val WIRE_LOD_MIN = 0.42f
    private const val WIRE_LOD_MAX = 0.55f
    private const val SEGMENT_PX = 4f
    private const val DASH_ON = 5f
    private const val DASH_OFF = 4f

    fun draw(
        ctx: NodeDrawCtx,
        x0: Float, y0: Float, x3: Float, y3: Float,
        color: Color,
        sel: Float,
        flash: Float,
        dashed: Boolean = false,
        arrow: Boolean = false,
        symbol: String? = null,
        strength: Float = 1f,
        showSymbol: Boolean = true,
        /**
         * 0..1 how strongly the travelling motes show. The LOD cross-fade ramps this in late, so the
         * bright particles never flash in over the straight overview lines.
         */
        moteFade: Float = 1f,
    ) {
        val h = handle(x0, x3)
        val cx1 = x0 + h; val cy1 = y0
        val cx2 = x3 - h; val cy2 = y3
        // One screen pixel in world units, the floor for every wire piece at any zoom - nothing
        // sub-pixel can ever rasterize as a triangle fragment.
        val px = maxOf(1f, 1f / ctx.zoom)
        val s = strength.coerceIn(0f, 1f)
        // Core. A wire carrying a signal brightens towards white as the strength rises; a wire with no
        // signal sits dimmed and darker so the topology is still visible but clearly idle.
        val base = when {
            flash > 0f -> Colors.mix(color, Color.WHITE, flash * 0.7f)
            s > 0f -> Colors.mix(color, Color.WHITE, 0.18f + s * 0.5f)
            else -> Colors.mix(color, Color.BLACK, 0.42f)
        }

        // Two quality tiers over the same bezier, cross-fading by zoom: the full stamp-cell curve at
        // high zoom, a cheaper thick polyline (segments fall off with zoom) at overview - so wires
        // stay curved everywhere but cost like the old straight lines when zoomed way out.
        val stamps = NodeDrawCtx.smoothstep(WIRE_LOD_MIN, WIRE_LOD_MAX, ctx.zoom)
        val batch = BrassPaint.QuadBatch(ctx.m)

        if (stamps > 0.01f) {
            val size = maxOf(CORE, px)
            val spacing = maxOf(SPACING, px / 2f)
            val cells = stampCells(x0, y0, cx1, cy1, cx2, cy2, x3, y3, dashed, spacing)
            val saved = BrassAmbientFade.current
            BrassAmbientFade.current = saved * stamps
            // Depth shadow, one world cell below.
            stamp(batch, ctx, cells, size, 0f, size / 2f, SHADOW)
            // Selection / hover halo, one cell wider all round, drawn under the core.
            if (sel > 0.01f) {
                val halo = Colors.withAlpha(Colors.mix(color, Color.WHITE, 0.4f), (255 * sel).toInt())
                stamp(batch, ctx, cells, size + 2f * px, -px, -px, halo)
            }
            stamp(batch, ctx, cells, size, 0f, 0f, base)

            // Travelling motes: only while a signal is present, and hidden until ~30% zoom so a
            // zoomed-out overview never fills with moving bright specks.
            if (!dashed && s > 0f && moteFade > 0.01f) {
                val mote = Colors.withAlpha(Colors.mix(base, Color.WHITE, 0.55f), (255 * moteFade).toInt())
                val speed = 0.18f + s * 1.1f
                for (k in 0..2) {
                    val t = ((ctx.time * speed + k / 3f) % 1f)
                    val (mx, my) = bezier(x0, y0, cx1, cy1, cx2, cy2, x3, y3, t)
                    cell(batch, ctx, floor(mx - size / 2f), floor(my - size / 2f), size, mote)
                }
            }
            BrassAmbientFade.current = saved
        }

        if (stamps < 0.99f) {
            drawPolyline(batch, ctx, x0, y0, cx1, cy1, cx2, cy2, x3, y3, base, 1f - stamps)
        }

        batch.flush()

        if (arrow && stamps > 0.5f) {
            val (ax, ay) = bezier(x0, y0, cx1, cy1, cx2, cy2, x3, y3, 0.76f)
            NodeGlyph.arrow(ctx.m, ax, ay, left = false, color = Colors.mix(color, Color.WHITE, 0.25f))
        }
        if (symbol != null && showSymbol && ctx.zoom > 0.65f) {
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

    private fun drawPolyline(
        batch: BrassPaint.QuadBatch,
        ctx: NodeDrawCtx,
        x0: Float, y0: Float, cx1: Float, cy1: Float, cx2: Float, cy2: Float, x3: Float, y3: Float,
        color: Color, alpha: Float,
    ) {
        val poly = hypot(cx1 - x0, cy1 - y0) + hypot(cx2 - cx1, cy2 - cy1) + hypot(x3 - cx2, y3 - cy2)
        val segments = ((poly * ctx.zoom) / SEGMENT_PX).toInt().coerceIn(4, 256)
        // Never thinner than one screen pixel, so the rotated segments can't fragment.
        val half = maxOf(0.7f, 0.5f / ctx.zoom)
        val c = BrassPaint.fade(color, alpha)
        var prevX = x0
        var prevY = y0
        for (i in 1..segments) {
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
        var carried = 0f
        var i = 0
        while (i < n) {
            val segLen = hypot(px[i + 1] - px[i], py[i + 1] - py[i])
            if (segLen < 1e-4f) { i++; continue }
            var d = spacing - carried
            while (d <= segLen) {
                val f = d / segLen
                addCell(cells, seen, px[i] + (px[i + 1] - px[i]) * f, py[i] + (py[i + 1] - py[i]) * f, dashed, i, f, n)
                d += spacing
            }
            carried = (segLen - (d - spacing)).let { if (it < 0f) 0f else it }
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

    private fun stamp(
        batch: BrassPaint.QuadBatch, ctx: NodeDrawCtx, cells: LongArray,
        size: Float, dx: Float, dy: Float, color: Color,
    ) {
        for (key in cells) {
            val ix = (key shr 32).toInt().toFloat()
            val iy = (key and 0xFFFFFFFFL).toInt().toFloat()
            cell(batch, ctx, ix + dx, iy + dy, size, color)
        }
    }

    private fun cell(batch: BrassPaint.QuadBatch, ctx: NodeDrawCtx, ix: Float, iy: Float, size: Float, color: Color) {
        if (!ctx.visible(ix, iy, ix + size, iy + size)) return
        batch.rect(ix, iy, ix + size, iy + size, color)
    }

    private fun bezier(
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, t: Float,
    ): Pair<Float, Float> {
        val u = 1f - t
        val a = u * u * u; val b = 3f * u * u * t; val c = 3f * u * t * t; val d = t * t * t
        return (a * x0 + b * x1 + c * x2 + d * x3) to (a * y0 + b * y1 + c * y2 + d * y3)
    }

    private val SHADOW: Color get() = Colors.withAlpha(Color.BLACK, 120)
}
