package net.swzo.brass.ui.kit.demo

import gg.essential.elementa.UIComponent

/**
 * A widget's own showcase: how to build the thing, at what size, on what surface.
 * ### Why this lives next to the widget
 * The capture pipeline used to hold a single central catalogue describing every widget — its size and
 * its sample content. That list was wrong the moment anything changed, in a way nobody noticed until an
 * asset came out looking broken, because the one file that knew a tree view needs expandable children
 * to be worth photographing was nowhere near the tree view.
 * A demo declared beside the widget fixes that. The tree knows it wants a two-level hierarchy; the
 * inventory grid knows one grid cannot show what it is for and declares a linked hotbar too; the table
 * knows its columns should be sortable or the demo is a grid of text. Each is written once, by whoever
 * knows the widget, and both consumers — the gallery strip and the demo browser you capture from — read
 * the same declaration.
 * ### Just a widget, not a performance
 * A demo builds something and stops. It does **not** script what the widget then does.
 * It used to. Each demo carried a timeline of scenes — open this section at 0.3s, release the drag at
 * 1.7s — replayed by a player that drove synthetic mouse events. It produced usable clips and it was
 * the wrong tool: every animation worth recording had to be *predicted in advance and written in
 * numbers*, so getting a clip to look right meant editing a Kotlin file, rebuilding, launching the
 * game, watching, and adjusting a decimal. The person recording could see exactly what they wanted and
 * had no way to just do it.
 * So the widget in a demo is live and takes the real mouse. You open the accordion section yourself, at
 * the pace you want, and hit record. Timing stops being a compile-time constant and goes back to being
 * a thing a hand does — which is what it always was.
 * ### Optional by construction
 * A widget declares a demo by giving its companion a [BrassDemoSource]. Nothing forces it to: a widget
 * with no demo simply does not appear in the showcase, and adding one later is a local change to that
 * widget's file.
 */
class BrassDemo(
    val name: String,
    val title: String,
    val width: Float,
    val height: Float,
    val card: Boolean = true,
    val fitCard: Boolean = false,
    val shrinkToFit: Boolean = false,
    val worldRequired: Boolean = false,
    /**
     * Builds the demo fresh.
     * Called again whenever the browser's Reset is pressed, so a demo that has been clicked into some
     * state can be put back without leaving the screen — which matters when the state you want to
     * record from is "untouched" and you have just spent a take opening everything.
     * Taking a builder rather than a prebuilt component is also what keeps every consumer's instance
     * its own: the gallery strip and the browser can show the same demo at once without sharing a
     * widget whose hover state would flicker between them.
     */
    val build: () -> UIComponent,
) {

    val outerWidth: Float get() = if (card) width + BrassDemoCard.INSET * 2 else width

    val outerHeight: Float get() = if (card) height + BrassDemoCard.INSET * 2 else height
}
