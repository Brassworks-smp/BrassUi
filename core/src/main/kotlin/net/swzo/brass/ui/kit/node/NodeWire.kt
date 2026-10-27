package net.swzo.brass.ui.kit.node

import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.paint.BrassWire
import java.awt.Color

/**
 * The node editor's wires: [BrassWire] (the shared pixel-art curve, see there) plus the editor-only
 * extras - the direction arrow and the port type's symbol.
 */
object NodeWire {

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
        val frame = BrassWire.Frame(ctx.m, ctx.zoom, ctx.time, ctx.visMinX, ctx.visMinY, ctx.visMaxX, ctx.visMaxY)
        BrassWire.draw(frame, x0, y0, x3, y3, color, sel, flash, dashed, strength, moteFade)

        if (arrow && BrassWire.stampTier(ctx.zoom) > 0.5f) {
            val (ax, ay) = BrassWire.point(x0, y0, x3, y3, 0.76f)
            NodeGlyph.arrow(ctx.m, ax, ay, left = false, color = Colors.mix(color, Color.WHITE, 0.25f))
        }
        if (symbol != null && showSymbol && ctx.zoom > 0.65f) {
            val (sx, sy) = BrassWire.point(x0, y0, x3, y3, 0.5f)
            val label = net.swzo.brass.ui.kit.text.BrassFont.fit(ctx.host, symbol, 18f)
            net.swzo.brass.ui.kit.text.BrassFont.draw(
                ctx.m, ctx.host, label,
                sx - net.swzo.brass.ui.kit.text.BrassFont.width(ctx.host, label) / 2f,
                sy - net.swzo.brass.ui.kit.text.BrassFont.LINE - 2f,
                Colors.UI_TEXT_DARK,
            )
        }
    }

    fun distanceTo(wx: Float, wy: Float, x0: Float, y0: Float, x3: Float, y3: Float): Float =
        BrassWire.distanceTo(wx, wy, x0, y0, x3, y3)
}
