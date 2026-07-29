package net.swzo.brass.ui.kit.demo

import gg.essential.elementa.UIComponent

/**
 * A widget's own showcase: how to build the thing, at what size, on what surface.
 *
 * ### Why this lives next to the widget
 *
 * The capture pipeline used to hold a single central catalogue describing every widget — its size and
 * its sample content. That list was wrong the moment anything changed, in a way nobody noticed until an
 * asset came out looking broken, because the one file that knew a tree view needs expandable children
 * to be worth photographing was nowhere near the tree view.
 *
 * A demo declared beside the widget fixes that. The tree knows it wants a two-level hierarchy; the
 * inventory grid knows one grid cannot show what it is for and declares a linked hotbar too; the table
 * knows its columns should be sortable or the demo is a grid of text. Each is written once, by whoever
 * knows the widget, and both consumers — the gallery strip and the demo browser you capture from — read
 * the same declaration.
 *
 * ### Just a widget, not a performance
 *
 * A demo builds something and stops. It does **not** script what the widget then does.
 *
 * It used to. Each demo carried a timeline of scenes — open this section at 0.3s, release the drag at
 * 1.7s — replayed by a player that drove synthetic mouse events. It produced usable clips and it was
 * the wrong tool: every animation worth recording had to be *predicted in advance and written in
 * numbers*, so getting a clip to look right meant editing a Kotlin file, rebuilding, launching the
 * game, watching, and adjusting a decimal. The person recording could see exactly what they wanted and
 * had no way to just do it.
 *
 * So the widget in a demo is live and takes the real mouse. You open the accordion section yourself, at
 * the pace you want, and hit record. Timing stops being a compile-time constant and goes back to being
 * a thing a hand does — which is what it always was.
 *
 * ### Optional by construction
 *
 * A widget declares a demo by giving its companion a [BrassDemoSource]. Nothing forces it to: a widget
 * with no demo simply does not appear in the showcase, and adding one later is a local change to that
 * widget's file.
 */
class BrassDemo(
    /** Stable id: the file-name stem of a capture, the anchor in a wiki. Kebab-case. */
    val name: String,
    /** Human title, e.g. "Number input". */
    val title: String,
    /**
     * The **widget's** size in GUI units — the whole composition, including any sub-widgets, but not
     * counting the card around it.
     *
     * Deliberately not the size of the finished image. A demo declares how big the thing it is
     * demonstrating needs to be; how much surface goes around that is [BrassDemoCard]'s business, and
     * a demo that had to state its size *inclusive* of a card would have to know the card's inset — so
     * every one of forty demos would carry a duplicate of one constant, and the small controls would
     * silently be drawn at a negative size the first time anybody changed it. Consumers read
     * [outerWidth] / [outerHeight] for the space actually needed.
     */
    val width: Float,
    val height: Float,
    /**
     * Whether to wrap the demo in a card.
     *
     * True for the great majority: a demo on a card reads as one deliberate object rather than a
     * control floating in space, and every capture then sits on the same surface. Set false only where
     * the widget **paints its own card** — the inventory grid, a split pane with `card = true`, an
     * image with a frame — because two cards nested with nothing between them looks like a mistake.
     */
    val card: Boolean = true,
    /**
     * Whether the card should track the widget's live height rather than filling the demo's box.
     *
     * For a widget that changes size as part of what it does — the dropdown unrolling its menu. The
     * declared [height] becomes the maximum, the frame stays that size (so a recording's frames stay
     * uniform), and the card grows and shrinks inside it. See [BrassDemoCard.fitContent].
     */
    val fitCard: Boolean = false,
    /**
     * Allow the browser stage to become smaller than the declared capture size when its available
     * panel shrinks. Intended for viewport-style widgets such as editors, not fixed-size controls.
     */
    val shrinkToFit: Boolean = false,
    /**
     * Whether this demo needs a loaded world to render.
     *
     * The entity, block and player-head widgets draw through the game's entity renderer and skin
     * system, which need a client level and a session that a title screen does not have — off-world
     * they come out as the platform's "no entity" placeholder. The demo browser flags a marked demo in
     * its status line, so it is obvious *before* you capture one that you need to be in a world rather
     * than after you look at the file. Item and inventory demos are *not* marked: item rendering needs
     * no level and works anywhere.
     */
    val worldRequired: Boolean = false,
    /**
     * Builds the demo fresh.
     *
     * Called again whenever the browser's Reset is pressed, so a demo that has been clicked into some
     * state can be put back without leaving the screen — which matters when the state you want to
     * record from is "untouched" and you have just spent a take opening everything.
     *
     * Taking a builder rather than a prebuilt component is also what keeps every consumer's instance
     * its own: the gallery strip and the browser can show the same demo at once without sharing a
     * widget whose hover state would flicker between them.
     */
    val build: () -> UIComponent,
) {

    /**
     * Total width the demo occupies once its card is accounted for.
     *
     * The card is drawn *around* the widget, so a target sized to [width] would either clip the card
     * or, for a control smaller than the card's own inset, hand the widget a negative box — which is
     * what a 12-px checkbox declared at 14 px did before this existed. A demo whose widget paints its
     * own card ([card] false) needs no allowance and reports its own size unchanged.
     */
    val outerWidth: Float get() = if (card) width + BrassDemoCard.INSET * 2 else width

    /** Total height the demo occupies once its card is accounted for. See [outerWidth]. */
    val outerHeight: Float get() = if (card) height + BrassDemoCard.INSET * 2 else height
}
