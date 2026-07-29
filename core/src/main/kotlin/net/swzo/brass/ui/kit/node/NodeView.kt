package net.swzo.brass.ui.kit.node

import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassKeycap
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color

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

    fun draw(ctx: NodeDrawCtx, graph: NodeGraph, node: GraphNode) {
        val m = ctx.m
        val pop = node.pop.value
        if (pop <= 0.001f) return

        val dy = -node.lift.value * 2f
        val x1 = node.x
        val y1 = node.y + dy
        val x2 = node.x + node.width
        val y2 = y1 + NodeLayout.height(node)
        val accent = node.type.accent

        m.push()
        // miniature-modal pop: scale about the node centre and fade its contents together
        val s = 0.92f + 0.08f * pop
        val cx = (x1 + x2) / 2f; val cy = (y1 + y2) / 2f
        m.translate(cx, cy, 0f); m.scale(s, s, 1f); m.translate(-cx, -cy, 0f)
        val savedFade = BrassAmbientFade.current
        BrassAmbientFade.current = savedFade * pop

        // body card + stacked window header, then a node-accent seam on the header/body seam line
        BrassCard.draw(m, x1, y1, x2, y2, shadow = true)
        BrassCard.header(m, x1, y1, x2, NodeLayout.HEADER, inset = 0f, accentSeam = false)
        val hy2 = y1 + NodeLayout.HEADER
        val seamW = minOf(40f, node.width - 2f)
        BrassPaint.rect(m, x1 + 1f, hy2 - 1f, x1 + seamW, hy2, accent.accent)

        // title
        val title = BrassFont.fit(ctx.host, node.type.title, node.width - 22f)
        BrassFont.draw(m, ctx.host, title, x1 + NodeLayout.PAD, y1 + (NodeLayout.HEADER - BrassFont.LINE) / 2f,
            Colors.UI_TEXT_HOVER)

        // collapse chevron
        NodeGlyph.chevron(m, NodeLayout.chevronX(node), y1 + NodeLayout.HEADER / 2f, open = node.roll.value < 0.5f,
            color = Colors.mix(Colors.UI_TEXT_DARK, accent.accent, node.hover.value * 0.5f))

        // Selection halo, drawn before the port nubs so it hugs the body without bleeding over them.
        val sel = node.sel.value
        if (sel > 0.01f) {
            BrassPaint.border(m, x1 - 1f, y1 - 1f, x2 + 1f, y2 + 1f,
                Colors.withAlpha(Colors.mix(accent.accent, Colors.UI_ACCENT_BRIGHT, 0.4f), (220 * sel).toInt()))
        }

        // ports (over the halo, so the nubs stay clean at any selection state)
        drawPorts(ctx, graph, node, dy)

        // Fields fold as the node rolls up: clip them to the animating body so they scissor away from the
        // bottom edge rather than fading in place. The clip is only needed mid-animation.
        val roll = node.roll.value
        if (roll < 0.985f && node.visibleFields().isNotEmpty()) {
            val rolling = roll > 0.001f
            val clip = if (rolling) {
                ScissorEffect(ctx.screenX(x1) - 2f, ctx.screenY(y1), ctx.screenX(x2) + 2f, ctx.screenY(y2), true)
            } else null
            clip?.beforeDraw(m)
            for ((row, f) in node.visibleFields().withIndex()) drawField(ctx, node, f, row, dy)
            clip?.afterDraw(m)
        }

        BrassAmbientFade.current = savedFade
        m.pop()
    }

    private fun drawPorts(ctx: NodeDrawCtx, graph: NodeGraph, node: GraphNode, dy: Float) {
        val m = ctx.m
        for (i in node.type.inputs.indices) {
            val p = node.type.inputs[i]
            val cx = NodeLayout.inputX(node)
            val cy = NodeLayout.portY(node, i) + dy
            val connected = graph.links.any { it.to === node && it.toPort == i && !it.closing }
            nub(m, cx, cy, p.type, connected, node.glowIn.getOrElse(i) { 0f }, node.rejectIn.getOrElse(i) { 0f }, ctx.time)
            BrassFont.draw(m, ctx.host, BrassFont.fit(ctx.host, p.name, 52f), cx + 7f, cy - BrassFont.LINE / 2f,
                Colors.UI_TEXT_DARK)
        }
        for (i in node.type.outputs.indices) {
            val p = node.type.outputs[i]
            val cx = NodeLayout.outputX(node)
            val cy = NodeLayout.portY(node, i) + dy
            val connected = graph.links.any { it.from === node && it.fromPort == i && !it.closing }
            nub(m, cx, cy, p.type, connected, node.glowOut.getOrElse(i) { 0f }, node.rejectOut.getOrElse(i) { 0f }, ctx.time)
            val tw = BrassFont.width(ctx.host, p.name)
            BrassFont.draw(m, ctx.host, p.name, cx - 7f - tw, cy - BrassFont.LINE / 2f, Colors.UI_TEXT_DARK)
        }
    }

    /**
     * A port nub - a tiny colour-tinted keycap. It grows and lights as a valid target ([glow]) with a
     * ring that blooms when the cursor is over it, and shrinks, reddens and jitters when it is an
     * invalid drop for the wire being dragged ([reject]) - a distinct "no" against the satisfying "yes".
     */
    private fun nub(
        m: UMatrixStack, cx: Float, cy: Float, type: PortType, connected: Boolean,
        glow: Float, reject: Float, time: Float,
    ) {
        val col = type.color()
        val jitter = if (reject > 0.01f) kotlin.math.sin(time * 34f) * reject * 1.2f else 0f
        val x = cx + jitter
        // A valid target grows; a rejected one shrinks a touch.
        val r = NodeLayout.PORT_R + glow * 1.7f - reject * 1.2f
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
    }

    private fun drawField(ctx: NodeDrawCtx, node: GraphNode, f: NodeField, row: Int, dy: Float) {
        val r = NodeLayout.fieldRow(node, row)
        val y1 = r[1] + dy; val y2 = r[3] + dy
        val cy = (y1 + y2) / 2f
        val ctrlL = NodeLayout.controlLeft(node)
        val labelW = ctrlL - r[0] - 4f
        BrassFont.draw(ctx.m, ctx.host, BrassFont.fit(ctx.host, f.label, labelW), r[0], cy - BrassFont.LINE / 2f,
            Colors.UI_TEXT_DARK)
        f.drawControl(ctx, ctrlL, y1, r[2], y2, f.hover.value, f.press.value)
    }
}
