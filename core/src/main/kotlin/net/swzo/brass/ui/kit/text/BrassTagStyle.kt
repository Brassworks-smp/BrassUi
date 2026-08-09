@file:Suppress("unused")
package net.swzo.brass.ui.kit.text

import net.swzo.brass.ui.Colors
import java.awt.Color

/**
 * What a [BrassTag] *means*, rather than what colour it is.
 * ### Why this exists
 * The tag palette was 22 `Color` constants on [BrassTag]'s companion - `BRASS`, `PATINA`, `AMBER`,
 * `RUST`, `SUCCESS`, `WARNING`, `NEW`, `BETA`, `DEPRECATED`, plus five tints belonging to the dev
 * inspector, one of which had to be called `BORDER_TAG` to avoid a name collision. Because a tag took
 * a raw [Color], keeping it theme-aware needed `roleFor`: a reverse lookup matching the value back to
 * the palette entry **by identity**, which works but is a trick, and silently fails the moment a
 * caller constructs an equal colour rather than passing the constant.
 * An enum says the intent directly and resolves the colour on read, so a styled tag tracks the theme
 * by construction with no reverse lookup at all.
 */
enum class BrassTagStyle(private val of: () -> Color) {

    SUCCESS({ Colors.BRASS_400 }),

    WARNING({ Colors.WARN }),

    ERROR({ Colors.DANGER }),

    INFO({ Colors.PATINA_400 }),

    NEUTRAL({ Colors.UI_TEXT_DARK }),

    STRONG({ Colors.UI_ELEMENT_BORDER_HOVER }),

    SUBTLE({ Colors.UI_INNER_BORDER }),

    NEW({ Colors.BRASS_300 }),

    BETA({ Colors.PATINA_500 }),

    DEPRECATED({ Colors.UI_ELEMENT_BORDER_HOVER }),
    ;

    val color: Color get() = of()
}
