package net.swzo.brass.ui.kit.text

import net.swzo.brass.ui.Colors
import java.awt.Color

/**
 * What a [BrassTag] *means*, rather than what colour it is.
 *
 * ### Why this exists
 *
 * The tag palette was 22 `Color` constants on [BrassTag]'s companion - `BRASS`, `PATINA`, `AMBER`,
 * `RUST`, `SUCCESS`, `WARNING`, `NEW`, `BETA`, `DEPRECATED`, plus five tints belonging to the dev
 * inspector, one of which had to be called `BORDER_TAG` to avoid a name collision. Because a tag took
 * a raw [Color], keeping it theme-aware needed `roleFor`: a reverse lookup matching the value back to
 * the palette entry **by identity**, which works but is a trick, and silently fails the moment a
 * caller constructs an equal colour rather than passing the constant.
 *
 * An enum says the intent directly and resolves the colour on read, so a styled tag tracks the theme
 * by construction with no reverse lookup at all.
 */
enum class BrassTagStyle(private val of: () -> Color) {

    /** The accent green - something completed, enabled, healthy. */
    SUCCESS({ Colors.BRASS_400 }),

    /** Amber - something that needs attention but is not broken. */
    WARNING({ Colors.WARN }),

    /** The muted red - a failure, or a destructive marker. */
    ERROR({ Colors.DANGER }),

    /** The secondary teal - informational. */
    INFO({ Colors.PATINA_400 }),

    /** Plain grey - the default for a tag carrying no status. */
    NEUTRAL({ Colors.UI_TEXT_DARK }),

    /** A brighter grey, for a neutral tag that still needs to be noticed. */
    STRONG({ Colors.UI_ELEMENT_BORDER_HOVER }),

    /** The quietest tag there is - barely more than an outline. */
    SUBTLE({ Colors.UI_INNER_BORDER }),

    /** A new or highlighted item. */
    NEW({ Colors.BRASS_300 }),

    /** A pre-release or experimental marker. */
    BETA({ Colors.PATINA_500 }),

    /** Something on its way out. */
    DEPRECATED({ Colors.UI_ELEMENT_BORDER_HOVER }),
    ;

    /** The colour in the current theme. Resolved on read, so a retheme is picked up automatically. */
    val color: Color get() = of()
}
