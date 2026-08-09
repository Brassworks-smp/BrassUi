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
 * ### Why every demo gets a card
 * A control captured flush to its own rectangle has nowhere to be. Against a wiki's page background a
 * bare slider is a grey bar floating in white space, and a bare tree view is indistinguishable from
 * the page's own text — the reader cannot see where the widget stops, which is precisely the thing a
 * screenshot is supposed to tell them. A card gives every demo an edge, and gives the whole set a
 * common surface so a page of them reads as one toolkit rather than a pile of unrelated crops.
 * It is also honest. This *is* how the toolkit's surfaces are built — [BrassCard] is what a window, a
 * popup and a table are all made of — so a demo on a card shows the widget in the context it actually
 * ships in, not on a neutral backdrop it will never see.
 * ### Except where the widget already has one
 * A handful of widgets paint their own card: the inventory grid hugs its slot block with one, a split
 * pane and a pagination strip have a `card` flag, an image has a frame. Those set
 * [BrassDemo.card] to false and turn their own on instead. Nesting this card immediately inside
 * another with only a couple of pixels between them does not read as depth — it reads as a mistake,
 * two borders where the eye expects one.
 * ### No drop shadow
 * [BrassCard] normally casts a two-slab shadow down and to the right, and this card deliberately does
 * not, because it is the one piece of the chrome that **cannot survive both output formats**. The
 * shadow is black at alpha 16–90; GIF alpha is one bit, so
 * BrassGif's threshold rounds every one of those pixels to fully
 * transparent. A PNG kept the shadow and the GIF beside it did not, which is worse than neither
 * having one — the same widget documented twice, looking like two different widgets.
 * The alternatives were compositing the shadow against an assumed page colour (a GIF that then wears
 * a wrong-coloured halo anywhere else) or leaving the two formats disagreeing. Dropping it makes the
 * two identical, and the ring and border were always what actually gave a demo its edge.
 * [MARGIN] of transparent padding is still left around every capture. Not for a shadow any more —
 * simply so nothing is flush against the image's edge.
 */
class BrassDemoCard(
    private val content: UIComponent,
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
        const val INSET = 8f

        const val MARGIN = 6f
    }
}
