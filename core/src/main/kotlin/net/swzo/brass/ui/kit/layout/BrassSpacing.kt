package net.swzo.brass.ui.kit.layout

/**
 * The toolkit's spacing scale - the handful of paddings and gaps every card and screen is built from.
 *
 * ### Why this exists
 *
 * There was no shared answer to "how much padding does a card have inside it", so every consumer
 * invented one. A downstream mod grew its own `PAD = 8`, `GAP = 8`, `TITLE_H = 15` on a `BoogerUi`
 * object; the popup carried a private `PAD = 12`; individual screens reached for `10`. The result is
 * exactly the drift the [net.swzo.brass.ui.Colors] role model exists to prevent, one axis over: two
 * cards side by side, padded by different numbers, read as belonging to different interfaces.
 *
 * These are `const`, so referencing them costs what the literal did - and unlike a literal, a card
 * that uses [PAD] follows the toolkit if the scale ever changes.
 *
 * ### The one rule
 *
 * A card holds its content [PAD] in from its own edge; two cards sit [GAP] apart. [PAD] and [GAP]
 * being the *same* number is deliberate - it means a control is the same distance from a card's edge
 * as that edge is from the next card, so the rhythm is even whether you read across a card or between
 * two of them. [BrassPanel] and [BrassScrollArea] default to these, so most UIs never name a spacing
 * number at all.
 */
object BrassSpacing {

    /** Inset of a card's content from its own edge - the default padding [BrassPanel] applies. */
    const val PAD = 8f

    /** Gap between two cards sitting beside or above one another. Equal to [PAD] on purpose. */
    const val GAP = 8f

    /** A tighter gap - between rows inside a section, or a caption and the control under it. */
    const val TIGHT = 4f

    /** Height of a card's title band: the title label, above the hairline that separates it. */
    const val TITLE_H = 15f

    /** Gap above a labelled section that follows another block of controls. */
    const val SECTION_GAP = 10f
}
