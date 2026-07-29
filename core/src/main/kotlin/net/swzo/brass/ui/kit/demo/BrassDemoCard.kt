package net.swzo.brass.ui.kit.demo

import gg.essential.elementa.UIComponent
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.paint.BrassCard

/**
 * The surface a demo sits on: one card, with the widget inset on it.
 *
 * ### Why every demo gets a card
 *
 * A control captured flush to its own rectangle has nowhere to be. Against a wiki's page background a
 * bare slider is a grey bar floating in white space, and a bare tree view is indistinguishable from
 * the page's own text — the reader cannot see where the widget stops, which is precisely the thing a
 * screenshot is supposed to tell them. A card gives every demo an edge, and gives the whole set a
 * common surface so a page of them reads as one toolkit rather than a pile of unrelated crops.
 *
 * It is also honest. This *is* how the toolkit's surfaces are built — [BrassCard] is what a window, a
 * popup and a table are all made of — so a demo on a card shows the widget in the context it actually
 * ships in, not on a neutral backdrop it will never see.
 *
 * ### Except where the widget already has one
 *
 * A handful of widgets paint their own card: the inventory grid hugs its slot block with one, a split
 * pane and a pagination strip have a `card` flag, an image has a frame. Those set
 * [BrassDemo.card] to false and turn their own on instead. Nesting this card immediately inside
 * another with only a couple of pixels between them does not read as depth — it reads as a mistake,
 * two borders where the eye expects one.
 *
 * ### No drop shadow
 *
 * [BrassCard] normally casts a two-slab shadow down and to the right, and this card deliberately does
 * not, because it is the one piece of the chrome that **cannot survive both output formats**. The
 * shadow is black at alpha 16–90; GIF alpha is one bit, so
 * [net.swzo.brass.ui.neoforge.shot.BrassGif]'s threshold rounds every one of those pixels to fully
 * transparent. A PNG kept the shadow and the GIF beside it did not, which is worse than neither
 * having one — the same widget documented twice, looking like two different widgets.
 *
 * The alternatives were compositing the shadow against an assumed page colour (a GIF that then wears
 * a wrong-coloured halo anywhere else) or leaving the two formats disagreeing. Dropping it makes the
 * two identical, and the ring and border were always what actually gave a demo its edge.
 *
 * [MARGIN] of transparent padding is still left around every capture. Not for a shadow any more —
 * simply so nothing is flush against the image's edge.
 */
class BrassDemoCard(
    private val content: UIComponent,
    /**
     * Let the card's height follow the content's instead of filling the demo's box.
     *
     * ### Why a widget would want this
     *
     * Because some widgets change size as part of what they do, and a card that ignores that looks
     * broken in both directions. The dropdown is the case that forced it: its height is the field plus
     * however much of the menu is currently unrolled, so a fixed card sized for the *closed* state gets
     * a menu hanging out of the bottom, and one sized for the *open* state — which is what a demo has
     * to declare, since the frame cannot change size mid-recording — spends most of the clip as a
     * mostly-empty card with a small control parked at the top of it. Neither is what the widget looks
     * like in a real UI, where the surface around a dropdown grows with it.
     *
     * With this on, the demo's declared height is the **maximum** — the frame stays that size, which is
     * what keeps a recording's frames uniform and its GIF valid — and the card itself grows and shrinks
     * inside it, anchored at the top, with transparent space below. So the capture is a constant size
     * and the card still animates.
     *
     * Off for everything else: a card that hugged a fixed-size widget would be identical to one that
     * filled the box, and measuring per frame is not free.
     */
    private val fitContent: Boolean = false,
) : BrassWidget(BrassAccent.DEFAULT) {

    init {
        // The base class must paint nothing: this widget's whole background is the card drawn below,
        // and the default keycap would put a second, differently-shaped surface behind it.
        chrome = BrassChrome.NONE

        content.constrain {
            x = INSET.pixels()
            y = INSET.pixels()
            width = 100.percent() - (INSET * 2).pixels()
            // Fitting means the content keeps whatever height it gives itself — the dropdown's own
            // constraint already tracks its open fraction — so the card has something live to measure.
            // Overriding it with a percentage is exactly what would flatten the animation away.
            if (!fitContent) height = 100.percent() - (INSET * 2).pixels()
        } childOf this
    }

    /**
     * The card's drawn height: the content plus its inset, or the whole box.
     *
     * Clamped to the box so a content that outgrows the demo's declared maximum is clipped rather than
     * drawing a card off the bottom of the frame — a demo that does that is a demo whose declared size
     * is wrong, and a clipped card is the legible version of that mistake.
     */
    private fun cardHeight(boxHeight: Float): Float =
        if (!fitContent) boxHeight
        else (content.getHeight() + INSET * 2).coerceIn(INSET * 2, boxHeight)

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        // draw, not panel: nothing clips this card, so it wants the outer ring bled a pixel proud.
        // shadow off — see the class docs; it is the one layer a GIF cannot carry.
        val bottom = y + cardHeight(h.toFloat())
        BrassCard.draw(m, x.toFloat(), y.toFloat(), (x + w).toFloat(), bottom, shadow = false)
    }

    companion object {
        /**
         * Space between the card's edge and the widget on it.
         *
         * Eight, not the four a dense form would use: a demo is looked at on its own at four times
         * size, and at that magnification a tight inset reads as the widget straining against its
         * frame. This is the one place in the toolkit where generous padding is the correct answer.
         */
        const val INSET = 8f

        /**
         * Transparent margin a capture leaves around a demo.
         *
         * Was sized for the drop shadow, which this card no longer draws (see the class docs). Kept at
         * six because an asset butted right up against its own edge looks cropped, and a few
         * transparent pixels cost a page nothing.
         */
        const val MARGIN = 6f
    }
}
