package net.swzo.brass.ui.kit.paint

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.paint.BrassCard.draw
import net.swzo.brass.ui.kit.paint.BrassCard.dropShadow
import net.swzo.brass.ui.kit.paint.BrassCard.header
import net.swzo.brass.ui.kit.paint.BrassCard.miniKeycap
import net.swzo.brass.ui.kit.paint.BrassCard.trackBorder
import net.swzo.brass.ui.kit.paint.BrassCard.trackShell
import java.awt.Color

/**
 * The card chrome every surface in the toolkit is built from - windows, popups, tables, panels.
 *
 * A card is a drop shadow, a near-black outer ring, a panel fill and a 1-px inner border. Having it
 * in one place is what keeps a table looking like it belongs to the same UI as the window containing
 * it; previously each surface open-coded its own stack of fills and they had drifted apart.
 *
 * ### Stacked headers
 *
 * [header] draws a second card **sharing the body card's top edge**, so the two read as one card
 * resting on another rather than as a strip painted inside a single panel. The shared top edge is
 * the whole trick: the header's ring runs along the same line as the body's, and only its bottom
 * edge and lip separate them.
 */
object BrassCard {

    /** Body fill. */
    private val FILL: Color get() = Colors.UI_INNER_BG

    /** Header fill - a step darker, so a stacked header reads as a distinct surface. */
    private val HEADER_FILL: Color get() = Colors.UI_BACKGROUND

    private val SHADOW_NEAR: Color get() = Colors.CARD_SHADOW_NEAR
    private val SHADOW_FAR: Color get() = Colors.CARD_SHADOW_FAR

    /**
     * Every layer of every card goes through here, which makes it the right place to apply the ambient
     * frame fade - see [BrassAmbientFade.apply]. Track cards, grips and headers all paint with literal
     * palette colours, so without this they stayed solid inside a closing window.
     */
    private fun fill(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) =
        BrassPaint.rect(m, x1, y1, x2, y2, c)

    /**
     * Draw a **floating** card occupying `[x1,y1]..[x2,y2]` - its near-black outer ring bleeds one
     * pixel *outside* those bounds, so the fill can meet the ring cleanly. Right for a window or a
     * popup, which has empty space around it for the ring and shadow to fall on.
     *
     * **Wrong for a card that sits flush inside a clipping parent** - a `ScrollComponent`, or anything
     * carrying a `ScissorEffect` clipped to its exact bounds. The clip shaves the bleeding ring off the
     * top, left and right and the card reads as having no frame on three sides. Use [panel] there: it
     * draws the identical stack *inside* the bounds and looks the same whether or not a scissor is
     * present. [BrassPanel] is the widget that makes that choice for you.
     *
     * [shadow] adds a two-step drop shadow down-right, which is what makes a floating surface read as
     * lifted off what is behind it. Leave it off for a card that sits flush inside another.
     */
    fun draw(
        m: UMatrixStack,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        shadow: Boolean = true,
        /** Multiplies every layer's alpha, so a card can fade in as one piece. */
        alpha: Float = 1f,
    ) {
        if (x2 <= x1 || y2 <= y1 || alpha <= 0.001f) return

        if (shadow) dropShadow(m, x1, y1, x2, y2, alpha)

        // Filled slab, immediately covered by the panel fill below - see BrassPaint.ringUnder.
        BrassPaint.ringUnder(m, x1, y1, x2, y2, fade(Colors.UI_OUTER_BORDER, alpha))
        fill(m, x1, y1, x2, y2, fade(FILL, alpha))
        border(m, x1, y1, x2, y2, fade(Colors.UI_INNER_BORDER, alpha))
    }

    /**
     * The batched twin of [draw]: every layer joins [batch] instead of its own tessellator call, so
     * hundreds of cards (the node editor's LOD pass) render in one GPU draw. Same colours, same
     * layers, same look - the LOD silhouette is pixel-identical to the real card.
     */
    fun drawInto(
        batch: BrassPaint.QuadBatch,
        x1: Float, y1: Float, x2: Float, y2: Float,
        shadow: Boolean = true,
        alpha: Float = 1f,
        /** Multiplies the ring/border layers' alpha - the LOD fades sub-pixel outlines out early. */
        outline: Float = 1f,
    ) {
        if (x2 <= x1 || y2 <= y1 || alpha <= 0.001f) return
        if (shadow) {
            batch.rect(x1 + 3f, y1 + 4f, x2 + 4f, y2 + 5f, fade(SHADOW_FAR, alpha))
            batch.rect(x1 + 1f, y1 + 2f, x2 + 2f, y2 + 3f, fade(SHADOW_NEAR, alpha))
        }
        batch.rect(x1 - 1f, y1 - 1f, x2 + 1f, y2 + 1f, fade(Colors.UI_OUTER_BORDER, alpha * outline))
        batch.rect(x1, y1, x2, y2, fade(FILL, alpha))
        batch.rect(x1, y1, x2, y1 + 1f, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(x1, y2 - 1f, x2, y2, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(x1, y1, x1 + 1f, y2, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(x2 - 1f, y1, x2, y2, fade(Colors.UI_INNER_BORDER, alpha * outline))
    }

    /**
     * The two-slab drop shadow every **floating** card casts down and to the right. Two offset slabs
     * rather than a gradient: cheap, and it keeps the pixel look.
     *
     * Only for [draw], and only when [draw]'s own `shadow` is true - a popup or a window, something
     * that is genuinely lifted off the surface behind it. Nothing else in the toolkit should call this
     * directly any more. It used to be shared with [trackShell] on the theory that a track control's
     * shadow ought to be "the same shadow" as a window's, but a slider is not a floating object - it
     * is a groove sunk *into* the panel - and at 14–20 px tall the offset slabs, which read as a
     * gentle lift under a 40-px window, instead swallowed a third of the control and crossed the ring
     * at an angle that looked like two shadows fighting each other rather than one falling naturally.
     */
    fun dropShadow(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, alpha: Float = 1f) {
        fill(m, x1 + 3f, y1 + 4f, x2 + 4f, y2 + 5f, fade(SHADOW_FAR, alpha))
        fill(m, x1 + 1f, y1 + 2f, x2 + 2f, y2 + 3f, fade(SHADOW_NEAR, alpha))
    }

    /** [c] with its alpha scaled by [a]. */
    private fun fade(c: Color, a: Float): Color = BrassPaint.fade(c, a)

    /**
     * The same two rings and fill as [draw], drawn **entirely inside** `[x1,y1]..[x2,y2]` instead of
     * bleeding the outer ring a pixel past them.
     *
     * For a self-painting widget that may sit under a `ScissorEffect` clipped to its own exact bounds
     * - [net.swzo.brass.ui.kit.surface.BrassBarChart], [net.swzo.brass.ui.kit.surface.BrassChart],
     * [net.swzo.brass.ui.kit.layout.BrassVirtualList] (so the code view and the tree), an accordion's
     * dropped panel. [draw]'s bleed is invisible the moment such a scissor is added - the clip cuts
     * the outer ring away and only the inner border survives, so the widget reads as having a single
     * outline while an unclipped sibling drawn with the exact same card has two. Every one of the
     * widgets above hit this independently before it had a name; this is the version that looks
     * identical whether or not a scissor happens to be present, so nothing has to remember which case
     * it is in.
     *
     * Never call [draw] for one of these. If a future widget paints its own background and clips to
     * its own bounds, it wants this one.
     *
     * [border] defaults to the card's own inner border, but takes [Colors.UI_ELEMENT_BORDER] instead
     * where the shape is standing in for a *control* rather than a card - see [miniKeycap], which is
     * this with that swap made for you.
     */
    fun panel(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        fill: Color = FILL,
        border: Color = Colors.UI_INNER_BORDER,
        alpha: Float = 1f,
    ) {
        if (x2 <= x1 || y2 <= y1 || alpha <= 0.001f) return
        BrassPaint.border(m, x1, y1, x2, y2, fade(Colors.UI_OUTER_BORDER, alpha))
        val ix1 = x1 + 1f; val iy1 = y1 + 1f; val ix2 = x2 - 1f; val iy2 = y2 - 1f
        if (ix2 <= ix1 || iy2 <= iy1) return
        BrassPaint.rect(m, ix1, iy1, ix2, iy2, fade(fill, alpha))
        this.border(m, ix1, iy1, ix2, iy2, fade(border, alpha))
    }

    /**
     * A small raised button painted by hand - the same fill, border and outer ring a real
     * [net.swzo.brass.ui.kit.input.BrassSquareButton] draws through the keycap base, minus the bottom
     * lip. For a composite widget that paints a *button-shaped region* rather than hosting a real
     * child widget for it, so it is not left with a mismatched border colour if the widget it is drawn
     * next to changes.
     *
     * The lip is the one thing missing. A real keycap bleeds 2–4 px below itself for it, which is fine
     * with a whole panel of empty space beneath a standalone button and is not fine for a stepper
     * packed edge-to-edge against the border of the control it belongs to - the lip would print
     * straight over that border. Fill, border and ring already carry the "this is a button" read
     * without it.
     */
    fun miniKeycap(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, hot: Boolean) {
        panel(
            m, x1, y1, x2, y2,
            fill = if (hot) Colors.UI_ELEMENT_BG_HOVER else Colors.UI_ELEMENT_BG,
            border = if (hot) Colors.UI_ELEMENT_BORDER_HOVER else Colors.UI_ELEMENT_BORDER,
        )
    }

    /**
     * Draw a header card across the top of a body card, sharing its top edge.
     *
     * [inset] pulls the header's sides in from the body's, so the body's edge stays visible beside
     * it and the stacking is legible; pass 0 for a header flush with the body.
     */
    fun header(
        m: UMatrixStack,
        x1: Float,
        y1: Float,
        x2: Float,
        height: Float,
        inset: Float = 0f,
        accentSeam: Boolean = true,
        /** Multiplies every layer's alpha, so a header fades with the card it sits on. */
        alpha: Float = 1f,
    ) {
        val hx1 = x1 + inset
        val hx2 = x2 - inset
        val hy2 = y1 + height
        if (hx2 <= hx1 || height <= 0f || alpha <= 0.001f) return

        // ring and fill, top edge coincident with the body card's
        fill(m, hx1 - 1f, y1 - 1f, hx2 + 1f, hy2 + 1f, fade(Colors.UI_OUTER_BORDER, alpha))
        fill(m, hx1, y1, hx2, hy2, fade(HEADER_FILL, alpha))
        border(m, hx1, y1, hx2, hy2, fade(Colors.UI_INNER_BORDER, alpha))

        // a lip under the header sells the stack - the body appears to pass beneath it
        fill(m, hx1, hy2, hx2, hy2 + 1f, fade(SHADOW_NEAR, alpha))

        // the brass tell, on the seam where the two cards meet
        if (accentSeam) {
            fill(m, hx1 + 1f, hy2 - 1f, hx1 + minOf(40f, hx2 - hx1 - 2f), hy2, fade(Colors.UI_ACCENT, alpha))
        }
    }

    /** The batched twin of [header]; see [drawInto]. */
    fun headerInto(
        batch: BrassPaint.QuadBatch,
        x1: Float, y1: Float, x2: Float,
        height: Float,
        inset: Float = 0f,
        accentSeam: Boolean = true,
        alpha: Float = 1f,
        /** Multiplies the ring/border layers' alpha - see [drawInto]. */
        outline: Float = 1f,
    ) {
        val hx1 = x1 + inset
        val hx2 = x2 - inset
        val hy2 = y1 + height
        if (hx2 <= hx1 || height <= 0f || alpha <= 0.001f) return
        batch.rect(hx1 - 1f, y1 - 1f, hx2 + 1f, hy2 + 1f, fade(Colors.UI_OUTER_BORDER, alpha * outline))
        batch.rect(hx1, y1, hx2, hy2, fade(HEADER_FILL, alpha))
        batch.rect(hx1, y1, hx2, y1 + 1f, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(hx1, hy2 - 1f, hx2, hy2, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(hx1, y1, hx1 + 1f, hy2, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(hx2 - 1f, y1, hx2, hy2, fade(Colors.UI_INNER_BORDER, alpha * outline))
        batch.rect(hx1, hy2, hx2, hy2 + 1f, fade(SHADOW_NEAR, alpha))
        if (accentSeam) {
            batch.rect(hx1 + 1f, hy2 - 1f, hx1 + minOf(40f, hx2 - hx1 - 2f), hy2, fade(Colors.UI_ACCENT, alpha))
        }
    }

    /** A 1-px border drawn inside the given bounds. */
    fun border(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) =
        BrassPaint.border(m, x1, y1, x2, y2, c)

    /**
     * A card with **no fill and no shadow** - the near-black outer ring plus the 1-px inner border,
     * and nothing else. Optionally a brass [seam] down the left edge, the tell that ties a menu or a
     * sub-panel to the control it hangs off.
     *
     * The colour picker's region frames and the dropdown's options panel both open-coded this stack
     * of fills. The file comment above records that every surface once did, and that they had
     * drifted; those two simply survived the cleanup.
     *
     * By default the outer ring bleeds one pixel *outside* the bounds, matching [draw]. Pass
     * [contained] for a card flush inside a scissor - the ring is drawn *inside* instead, the way
     * [panel] does it, so the clip does not shave it off. When there is a [fill], [panel] is usually
     * the better call; [contained] here is for a framed region whose interior must be left untouched
     * (a gradient, an image) but which still sits inside a clip.
     */
    fun flat(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        fill: Color? = null,
        seam: Color? = null,
        contained: Boolean = false,
    ) {
        if (x2 <= x1 || y2 <= y1) return
        // A hollow ring, not a filled slab. [draw] can use the cheap filled version because its panel
        // fill covers the interior a moment later; here the interior may be a gradient, an image or an
        // entity preview that must survive, and painting over it is how the colour picker's saturation
        // square and hue strip came out solid black.
        if (fill != null) {
            if (contained) {
                // Ring on the edge, fill inset by 1 so the ring survives - the same order as [panel].
                // Filling the full box here (as the bleeding path can, because its ring sits a pixel
                // outside) would paint straight over the near-black ring and leave the card with only
                // its grey inner border, which is exactly how a contained row lost its black outline.
                BrassPaint.border(m, x1, y1, x2, y2, Colors.UI_OUTER_BORDER)
                BrassPaint.rect(m, x1 + 1f, y1 + 1f, x2 - 1f, y2 - 1f, fill)
            } else {
                BrassPaint.ringUnder(m, x1, y1, x2, y2, Colors.UI_OUTER_BORDER)
                BrassPaint.rect(m, x1, y1, x2, y2, fill)
            }
        } else {
            // ringAround already lives outside the bounds; contained draws the ring on the top edge in.
            if (contained) BrassPaint.border(m, x1, y1, x2, y2, Colors.UI_OUTER_BORDER)
            else BrassPaint.ringAround(m, x1, y1, x2, y2, Colors.UI_OUTER_BORDER)
        }
        val inset = if (contained) 1f else 0f
        BrassPaint.border(m, x1 + inset, y1 + inset, x2 - inset, y2 - inset, Colors.UI_INNER_BORDER)
        seam?.let { BrassPaint.rect(m, x1 + inset, y1 + inset, x1 + inset + 1f, y2 - inset, it) }
    }

    // ---- filled track ----------------------------------------------------------------------------

    /**
     * The shell every track control is built from - the slider, the range slider, the confirm-hold
     * bar, the toggle, the progress bar and the loading sweep. The same near-black ring and 1-px
     * border as [draw], with a recessed [track] fill in place of the panel fill - **no drop shadow**.
     *
     * It used to have [dropShadow]'s two offset slabs, on the theory that a track should be made of
     * the same material as the windows around it. It reads the wrong way at this size: a track is a
     * groove sunk *into* the panel, not a card floating above it, and the shadow - sized for a 40-px
     * window - ate a third of a 14-px-tall control and crossed its ring at an angle, so it looked like
     * two shadows colliding rather than one falling naturally. What actually ties a track to the rest
     * of the toolkit is the ring and the border, which is exactly what a plain button's keycap reads
     * as too - so dropping the shadow is what makes a track read as *clean*, the way a button does.
     *
     * Draw order is shell → block → [trackBorder], so the border always caps the fill cleanly.
     */
    fun trackShell(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        track: Color = Colors.UI_ELEMENT_BG,
    ) {
        if (x2 <= x1 || y2 <= y1) return
        fill(m, x1 - 1f, y1 - 1f, x2 + 1f, y2 + 1f, Colors.UI_OUTER_BORDER)
        fill(m, x1, y1, x2, y2, track)
    }

    /**
     * A brass block inside a [trackShell], spanning [bx1]..[bx2] and inset to the card's interior.
     *
     * The block carries a 1-px [lit] **outline** rather than a lit top edge. A top highlight reads as
     * a bevel - the raised-keycap language - which is exactly what these controls are not; an outline
     * reads as a filled region, which is what a progress bar actually is.
     */
    fun trackBlock(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        bx1: Float, bx2: Float,
        fill: Color = Colors.BRASS_600,
        lit: Color = Colors.BRASS_400,
    ) {
        val ix1 = x1 + 1f
        val iy1 = y1 + 1f
        val ix2 = x2 - 1f
        val iy2 = y2 - 1f
        val l = bx1.coerceIn(ix1, ix2)
        val r = bx2.coerceIn(ix1, ix2)
        if (r <= l + 0.5f || iy2 <= iy1) return
        fill(m, l, iy1, r, iy2, fill)
        border(m, l, iy1, r, iy2, lit)
    }

    /** The card's 1-px border, drawn last so the fill can never spill over it. */
    fun trackBorder(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float) =
        border(m, x1, y1, x2, y2, Colors.UI_INNER_BORDER)

    /**
     * A track card filled from the left up to [fraction] - the determinate case, and the shape the
     * progress bar, slider and toggle are all built from.
     */
    fun filledTrack(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        fraction: Float,
        fill: Color = Colors.BRASS_600,
        lit: Color = Colors.BRASS_400,
        track: Color = Colors.UI_ELEMENT_BG,
    ) {
        if (x2 <= x1 || y2 <= y1) return
        trackShell(m, x1, y1, x2, y2, track)
        val ix1 = x1 + 1f
        val ix2 = x2 - 1f
        trackBlock(m, x1, y1, x2, y2, ix1, ix1 + (ix2 - ix1) * fraction.coerceIn(0f, 1f), fill, lit)
        trackBorder(m, x1, y1, x2, y2)
    }

    /**
     * The draggable handle shared by the slider and the toggle - a miniature card: a near-black ring
     * **drawn on the given bounds**, a grey body inset inside it and a lighter top edge, brightening
     * toward brass as [glow] rises from 0 (rest) to 1 (hovered / active).
     *
     * The ring being inside the bounds rather than around them is what lets a caller draw a handle
     * exactly 1 px proud of its track: passing `y1 - 1 .. y2 + 1` gives one pixel of overhang, not the
     * two an outset ring would have added.
     */
    fun grip(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, glow: Float = 0f) {
        if (x2 <= x1 || y2 <= y1) return
        val g = glow.coerceIn(0f, 1f)
        fill(m, x1, y1, x2, y2, Colors.UI_OUTER_BORDER)
        fill(m, x1 + 1f, y1 + 1f, x2 - 1f, y2 - 1f, Colors.mix(GRIP, Colors.BRASS_600, g))
        fill(m, x1 + 1f, y1 + 1f, x2 - 1f, y1 + 2f, Colors.mix(GRIP_EDGE, Colors.BRASS_400, g))
    }

    private val GRIP: Color get() = Colors.GRIP
    private val GRIP_EDGE: Color get() = Colors.GRIP_EDGE
}
