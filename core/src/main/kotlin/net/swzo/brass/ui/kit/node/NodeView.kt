package net.swzo.brass.ui.kit.node

import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassKeycap
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color
import java.util.Locale
import kotlin.math.abs

/**
 * Draws one node as a **miniature modal**: the toolkit's floating card body with a stacked window
 * header (a per-node accent seam), a collapse chevron, colour-coded keycap port nubs, and its inline
 * controls - every piece painted through the shared toolkit painters so it is the same chrome the real
 * windows and widgets wear, just smaller and drawn under the canvas' zoom.
 *
 * Animation is read here, not advanced: the editor advances every [net.swzo.brass.ui.kit.base.BrassEased]
 * once per frame (so nothing double-steps) and this paints the current values - the open/close pop, the
 * hover lift, the selection halo, the roll-up, and each control's hover/press.
 */
object NodeView {

    /** Interior chrome (title/ports/fields) fades in across this detail band; the card never changes. */
    private const val INTERIOR_MIN_DETAIL = 0.22f
    private const val INTERIOR_FULL_DETAIL = 0.50f
    /** The accent seam fades out around 15% zoom (detail ~0.14) - sub-pixel bars flicker. */
    private const val SEAM_MIN_DETAIL = 0.07f
    private const val SEAM_MAX_DETAIL = 0.14f
    /** The card's 1px outlines fade out around 10% zoom - below that, header + body fills only. */
    private const val OUTLINE_MIN_DETAIL = 0.025f
    private const val OUTLINE_MAX_DETAIL = 0.06f

    /**
     * Draw one node. [value] is an optional live output value shown as a small badge in the header -
     * tinted by the node's first output port type, so the badge reads as "what kind of value this
     * node carries right now" - the host's way of putting a live readout on every card without owning
     * the drawing. Null hides the badge.
     */
    fun draw(ctx: NodeDrawCtx, graph: NodeGraph, node: GraphNode, value: Any? = null) {
        node.type.renderer?.let { renderer ->
            renderer.draw(ctx, graph, node)
            return
        }
        val m = ctx.m
        val pop = node.pop.value
        if (pop <= 0.001f) return

        val detail = ctx.detail
        val accent = node.type.accent
        val dy = -node.lift.value * 2f
        val x1 = node.x
        val y1 = node.y + dy
        val x2 = node.x + node.width
        val y2 = y1 + NodeLayout.height(node)

        // A direct host without the editor's batched LOD pass draws the card itself (with halo);
        // the editor already drew the same card batched, so here only the interior fades in.
        if (ctx.lodRects == null) drawCard(ctx, node, x1, y1, x2, y2, accent)

        // Interior chrome (title, chevron, badge, ports, fields) fades in once the card is big
        // enough to read. The card silhouette is identical at every zoom, so the LOD is invisible.
        val interiorFade = NodeDrawCtx.smoothstep(INTERIOR_MIN_DETAIL, INTERIOR_FULL_DETAIL, detail)
        if (interiorFade <= 0.01f) return

        m.push()
        // miniature-modal pop: scale about the node centre and fade its contents together
        val s = 0.92f + 0.08f * pop
        val cx = (x1 + x2) / 2f; val cy = (y1 + y2) / 2f
        m.translate(cx, cy, 0f); m.scale(s, s, 1f); m.translate(-cx, -cy, 0f)
        val savedFade = BrassAmbientFade.current
        BrassAmbientFade.current = savedFade * pop * interiorFade

        val coveredBy = ctx.coveredBy
        fun covered(rx1: Float, ry1: Float, rx2: Float, ry2: Float): Boolean =
            coveredBy?.invoke(node, rx1, ry1, rx2, ry2) == true

        // title + collapse chevron: the whole header strip is skipped when a node above covers it.
        if (!covered(x1, y1, x2, y1 + NodeLayout.HEADER)) {
            val title = BrassFont.fit(ctx.host, node.type.title, if (value != null) node.width - 52f else node.width - 22f)
            BrassFont.draw(m, ctx.host, title, x1 + NodeLayout.PAD, y1 + (NodeLayout.HEADER - BrassFont.LINE) / 2f,
                Colors.UI_TEXT_HOVER)
            NodeGlyph.chevron(m, NodeLayout.chevronX(node), y1 + NodeLayout.HEADER / 2f, open = node.roll.value < 0.5f,
                color = Colors.mix(Colors.UI_TEXT_DARK, accent.accent, node.hover.value * 0.5f))
        }

            // Live value badge: a small keycap in the header, tinted by the node's first output port type
            // and showing the current value formatted for that kind (whole numbers drop their .0).
            value?.let { raw ->
                val tint = node.type.outputs.firstOrNull()?.type?.color() ?: Colors.DANGER
                val label = when (raw) {
                    is Double -> formatBadge(raw)
                    is Float -> formatBadge(raw.toDouble())
                    is Boolean -> if (raw) "1" else "0"
                    else -> raw.toString()
                }
                val bw = (BrassFont.width(ctx.host, label) + 10f).coerceAtLeast(24f)
                val bh = 12f
                val bx = x2 - 46f - (bw - 24f)
                val by = y1 + (NodeLayout.HEADER - bh) / 2f - 1f
                if (!covered(bx, by, bx + bw, by + bh)) {
                    val bg = Colors.mix(tint, Color.BLACK, 0.72f)
                    BrassKeycap.draw(
                        m, bx, by, bw, bh,
                        bg = bg,
                        border = Colors.mix(bg, Color.WHITE, 0.28f),
                        outer = Colors.UI_OUTER_BORDER,
                        bottom = Colors.mix(bg, Color.BLACK, 0.45f),
                        defaultAccent = false,
                        lip = 0f,
                        // Keep the raised 3D lip, but drop the soft shadow it would cast onto the busy header.
                        shadow = false,
                    )
                    BrassFont.draw(
                        m, ctx.host, label,
                        bx + (bw - BrassFont.width(ctx.host, label)) / 2f,
                        by + (bh - BrassFont.LINE) / 2f + 1f,
                        Colors.mix(Colors.UI_TEXT_DARK, Color.WHITE, 0.75f),
                    )
                }
            }

        // ports (over the halo, so the nubs stay clean at any selection state)
        drawPorts(ctx, graph, node, dy)

            // Fields fold as the node rolls up: clip them to the animating body so they scissor away from the
            // bottom edge rather than fading in place. The clip is only needed mid-animation.
            val roll = node.roll.value
            if (roll < 0.985f && node.fields.any { it.reveal.value > 0.01f }) {
                val rolling = roll > 0.001f
                val reflowing = node.fields.any { !it.reveal.settled }
                val clip = if (rolling || reflowing) {
                    ScissorEffect(ctx.screenX(x1) - 2f, ctx.screenY(y1), ctx.screenX(x2) + 2f, ctx.screenY(y2), true)
                } else null
                clip?.beforeDraw(m)
                for (f in node.fields) if (f.reveal.value > 0.01f) {
                    val row = NodeLayout.fieldRow(node, f)
                    // A field row (slider/param/control) fully under a node above is skipped too.
                    if (!covered(row[0], row[1], row[2], row[3])) drawField(ctx, node, f, dy)
                }
                clip?.afterDraw(m)
            }

        BrassAmbientFade.current = savedFade
        m.pop()
    }

    /**
     * The batched silhouette pass: every visible node's **real card** (body, header, accent seam,
     * selection halo) is added to the frame's shared [NodeDrawCtx.lodRects] batch - pixel-identical
     * to the full-detail card, but one GPU draw call for the whole tree. [draw] then layers the
     * interior chrome over it, so the LOD is invisible.
     */
    fun drawLod(ctx: NodeDrawCtx, node: GraphNode) {
        val pop = node.pop.value
        if (pop <= 0.001f) return
        val batch = ctx.lodRects ?: return
        val m = ctx.m
        val dy = -node.lift.value * 2f
        val x1 = node.x
        val y1 = node.y + dy
        val x2 = node.x + node.width
        val y2 = y1 + NodeLayout.height(node)
        val accent = node.type.accent
        val detail = ctx.detail
        val seamFade = NodeDrawCtx.smoothstep(SEAM_MIN_DETAIL, SEAM_MAX_DETAIL, detail)
        val outlineFade = NodeDrawCtx.smoothstep(OUTLINE_MIN_DETAIL, OUTLINE_MAX_DETAIL, detail)
        m.push()
        val s = 0.92f + 0.08f * pop
        val cx = (x1 + x2) / 2f; val cy = (y1 + y2) / 2f
        m.translate(cx, cy, 0f); m.scale(s, s, 1f); m.translate(-cx, -cy, 0f)
        BrassCard.drawInto(batch, x1, y1, x2, y2, shadow = true, alpha = pop, outline = outlineFade)
        BrassCard.headerInto(batch, x1, y1, x2, NodeLayout.HEADER, inset = 0f, accentSeam = false, alpha = pop, outline = outlineFade)
        val hy2 = y1 + NodeLayout.HEADER
        val seamW = minOf(40f, node.width - 2f)
        batch.rect(x1 + 1f, hy2 - 1f, x1 + seamW, hy2, BrassPaint.fade(accent.accent, pop * seamFade))
        val sel = node.sel.value
        if (sel > 0.01f) {
            val halo = Colors.withAlpha(
                Colors.mix(accent.accent, Colors.UI_ACCENT_BRIGHT, 0.4f),
                (220 * sel * pop * outlineFade).toInt(),
            )
            batch.rect(x1 - 1f, y1 - 1f, x2 + 1f, y1, halo)
            batch.rect(x1 - 1f, y2, x2 + 1f, y2 + 1f, halo)
            batch.rect(x1 - 1f, y1, x1, y2, halo)
            batch.rect(x2, y1, x2 + 1f, y2, halo)
        }
        m.pop()
    }

    /** The real card for direct hosts (no batched LOD pass): body, header, seam and selection halo. */
    private fun drawCard(
        ctx: NodeDrawCtx,
        node: GraphNode,
        x1: Float, y1: Float, x2: Float, y2: Float,
        accent: BrassAccent,
    ) {
        val m = ctx.m
        val pop = node.pop.value
        m.push()
        val s = 0.92f + 0.08f * pop
        val cx = (x1 + x2) / 2f; val cy = (y1 + y2) / 2f
        m.translate(cx, cy, 0f); m.scale(s, s, 1f); m.translate(-cx, -cy, 0f)
        val saved = BrassAmbientFade.current
        BrassAmbientFade.current = saved * pop
        BrassCard.draw(m, x1, y1, x2, y2, shadow = true)
        BrassCard.header(m, x1, y1, x2, NodeLayout.HEADER, inset = 0f, accentSeam = false)
        val hy2 = y1 + NodeLayout.HEADER
        val seamW = minOf(40f, node.width - 2f)
        BrassPaint.rect(m, x1 + 1f, hy2 - 1f, x1 + seamW, hy2, accent.accent)
        val sel = node.sel.value
        if (sel > 0.01f) {
            BrassPaint.border(m, x1 - 1f, y1 - 1f, x2 + 1f, y2 + 1f,
                Colors.withAlpha(Colors.mix(accent.accent, Colors.UI_ACCENT_BRIGHT, 0.4f), (220 * sel).toInt()))
        }
        BrassAmbientFade.current = saved
        m.pop()
    }

    /**
     * Compact badge text for a live number: whole values drop their `.0`, everything else shows at
     * most two decimals (trailing zeros trimmed), and absurdly large magnitudes fall back to short
     * scientific notation so the header badge never sprawls.
     */
    private fun formatBadge(raw: Double): String {
        if (raw == raw.toLong().toDouble() && abs(raw) < 1e15) return raw.toLong().toString()
        if (abs(raw) >= 1e12) return String.format(Locale.ROOT, "%.3g", raw)
        return String.format(Locale.ROOT, "%.2f", raw).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }

    private fun drawPorts(ctx: NodeDrawCtx, graph: NodeGraph, node: GraphNode, dy: Float) {
        val m = ctx.m
        val coveredBy = ctx.coveredBy
        fun covered(rx1: Float, ry1: Float, rx2: Float, ry2: Float): Boolean =
            coveredBy?.invoke(node, rx1, ry1, rx2, ry2) == true
        for (i in node.type.inputs.indices) {
            val p = node.type.inputs[i]
            if (p.hidden) continue
            val cx = NodeLayout.inputX(node)
            val cy = NodeLayout.inputY(node, i) + dy
            val connected = graph.links.any { it.to === node && it.toPort == i && !it.closing }
            if (!covered(cx - 3f, cy - 3f, cx + 3f, cy + 3f)) {
                nub(m, cx, cy, p, connected, node.glowIn.getOrElse(i) { 0f }, node.rejectIn.getOrElse(i) { 0f }, ctx.time)
            }
            if (p.showLabel && !covered(cx + 1f, cy - BrassFont.LINE / 2f, cx + 60f, cy + BrassFont.LINE / 2f)) {
                BrassFont.draw(m, ctx.host, BrassFont.fit(ctx.host, p.name, 52f), cx + 7f,
                    cy - BrassFont.LINE / 2f, Colors.UI_TEXT_DARK)
            }
        }
        for (i in node.type.outputs.indices) {
            val p = node.type.outputs[i]
            if (p.hidden) continue
            val cx = NodeLayout.outputX(node)
            val cy = NodeLayout.outputY(node, i) + dy
            val connected = graph.links.any { it.from === node && it.fromPort == i && !it.closing }
            if (!covered(cx - 3f, cy - 3f, cx + 3f, cy + 3f)) {
                nub(m, cx, cy, p, connected, node.glowOut.getOrElse(i) { 0f }, node.rejectOut.getOrElse(i) { 0f }, ctx.time)
            }
            if (p.showLabel && !covered(cx - 62f, cy - BrassFont.LINE / 2f, cx - 1f, cy + BrassFont.LINE / 2f)) {
                val tw = BrassFont.width(ctx.host, p.name)
                BrassFont.draw(m, ctx.host, p.name, cx - 7f - tw, cy - BrassFont.LINE / 2f, Colors.UI_TEXT_DARK)
            }
        }
    }

    /**
     * A port nub - a tiny colour-tinted keycap. It grows and lights as a valid target ([glow]) with a
     * ring that blooms when the cursor is over it, and shrinks, reddens and jitters when it is an
     * invalid drop for the wire being dragged ([reject]) - a distinct "no" against the satisfying "yes".
     */
    private fun nub(
        m: UMatrixStack, cx: Float, cy: Float, port: Port, connected: Boolean,
        glow: Float, reject: Float, time: Float,
    ) {
        val type = port.type
        val col = type.color()
        val jitter = if (reject > 0.01f) kotlin.math.sin(time * 34f) * reject * 1.2f else 0f
        val x = cx + jitter
        // A valid target grows; a rejected one shrinks a touch.
        val r = NodeLayout.PORT_R * port.size + glow * 1.7f - reject * 1.2f
        val bg = when {
            reject > 0.01f -> Colors.mix(Colors.INK_900, Colors.DANGER, reject)
            connected || glow > 0.35f -> col
            else -> Colors.INK_900
        }
        val edge = if (reject > 0.01f) Colors.mix(col, Colors.DANGER, reject) else col
        // Valid-target bloom: a soft coloured ring one cell out, growing with the glow.
        if (glow > 0.35f && reject < 0.01f) {
            val rr = r + 1f + glow * 1.5f
            BrassPaint.border(m, x - rr, cy - rr, x + rr, cy + rr,
                Colors.withAlpha(Colors.mix(col, Color.WHITE, 0.4f), (150 * glow).toInt()))
        }
        BrassKeycap.draw(
            m, x - r, cy - r, r * 2f, r * 2f,
            bg = bg,
            border = edge,
            outer = Colors.UI_OUTER_BORDER,
            bottom = Colors.mix(edge, Color.BLACK, 0.45f),
            flat = false,
            defaultAccent = false,
        )
        when (port.shape) {
            PortShape.ROUND -> Unit
            PortShape.SQUARE -> BrassPaint.border(m, x - 1.5f, cy - 1.5f, x + 1.5f, cy + 1.5f, edge)
            PortShape.DIAMOND -> {
                BrassPaint.rect(m, x, cy - 2f, x + 1f, cy + 3f, edge)
                BrassPaint.rect(m, x - 2f, cy, x + 3f, cy + 1f, edge)
            }
            PortShape.DOT -> {
                val ink = if (connected) Colors.INK_900 else edge
                BrassPaint.rect(m, x - 1f, cy - 1f, x + 1f, cy + 1f, ink)
            }
            PortShape.CROSS -> {
                val ink = if (connected) Colors.INK_900 else edge
                BrassPaint.rect(m, x - 0.5f, cy - 2.5f, x + 0.5f, cy + 2.5f, ink)
                BrassPaint.rect(m, x - 2.5f, cy - 0.5f, x + 2.5f, cy + 0.5f, ink)
            }
        }
    }

    private fun drawField(ctx: NodeDrawCtx, node: GraphNode, f: NodeField, dy: Float) {
        val r = NodeLayout.fieldRow(node, f)
        val reveal = f.reveal.value
        val y1 = r[1] + dy + (1f - reveal) * 3f
        val y2 = r[3] + dy + (1f - reveal) * 3f
        val cy = (y1 + y2) / 2f
        val ctrlL = NodeLayout.controlLeft(node)
        val labelW = ctrlL - r[0] - 4f
        val savedFade = BrassAmbientFade.current
        BrassAmbientFade.current = savedFade * reveal
        BrassFont.draw(ctx.m, ctx.host, BrassFont.fit(ctx.host, f.label, labelW), r[0],
            cy - BrassFont.LINE / 2f, Colors.UI_TEXT_DARK)
        f.drawControl(ctx, ctrlL, y1, r[2], y2, f.hover.value, f.press.value)
        BrassAmbientFade.current = savedFade
    }
}
