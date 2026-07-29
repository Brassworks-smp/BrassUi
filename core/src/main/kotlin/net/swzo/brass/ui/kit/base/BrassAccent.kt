package net.swzo.brass.ui.kit.base

import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent.Companion.derived
import java.awt.Color

/**
 * A widget accent. A **colored** accent tints a
 * control's fill (`darkColor`), border/text (`accentColor`), and raised bottom edge (`bottomColor`); the
 * `DEFAULT` accent instead falls back to the neutral element colours in [Colors] (dark fill, grey
 * border, light text). Each field has a `…Hover` sibling for the animated hover state.
 *
 * The set is default/nice/danger/calm, filled from the BrassWorks palette - `NICE`/`BRASS` are our
 * green, `DANGER` red, `CALM` the patina teal.
 *
 * ### Themeing
 *
 * The built-in accents resolve their colours from [Colors] **on every read**, not once when the class
 * is first touched, so they follow a theme swap like everything else. This matters more than it looks:
 * a widget holds its [BrassAccent] for life and re-reads these fields every frame to pick its animation
 * targets, so an accent that snapshotted its colours would leave every accented control - the primary
 * buttons, the danger buttons, the selected tab - stranded on the old palette while the neutral chrome
 * around it retinted.
 *
 * An accent built through the public constructor keeps whatever [Color] values it is handed. Pass
 * suppliers via [derived] to build a custom accent that tracks the theme too.
 */
class BrassAccent private constructor(
    val name: String,
    private val accentOf: () -> Color,
    private val accentHoverOf: () -> Color,
    private val darkOf: () -> Color,
    private val darkHoverOf: () -> Color,
    private val outerOf: () -> Color,
    private val bottomOf: () -> Color,
    private val bottomHoverOf: () -> Color,
    val isDefault: Boolean = false,
) {

    /**
     * A fixed-colour accent. The values are used exactly as given and do not follow the theme.
     *
     * The parameter order matches [derived] exactly - accent, dark, bottom, then the rarely-changed
     * [outer]. It used to put `outer` between `darkHover` and `bottom` while `derived` put it last,
     * so the two factories for the same object took seven interchangeable [Color]s in **different
     * orders** and a positional call silently swapped the outer ring with the bottom lip.
     */
    constructor(
        name: String,
        accent: Color,
        accentHover: Color,
        dark: Color,
        darkHover: Color,
        bottom: Color,
        bottomHover: Color,
        outer: Color = Colors.UI_OUTER_BORDER,
        isDefault: Boolean = false,
    ) : this(
        name, { accent }, { accentHover }, { dark }, { darkHover }, { outer }, { bottom },
        { bottomHover }, isDefault,
    )

    /** Border + (accent) text colour. */
    val accent: Color get() = accentOf()
    val accentHover: Color get() = accentHoverOf()

    /** The dark fill under a colored control. */
    val dark: Color get() = darkOf()
    val darkHover: Color get() = darkHoverOf()

    /**
     * The 1-px outer ring. This is **always** the near-black [Colors.UI_OUTER_BORDER], for every accent
     * and in every state including hover: the ring is what seats a keycap against the panel behind it,
     * and tinting it made hovered buttons look outlined in colour rather than raised.
     */
    val outer: Color get() = outerOf()

    /** The raised bottom-edge colour. */
    val bottom: Color get() = bottomOf()
    val bottomHover: Color get() = bottomHoverOf()

    companion object {

        /**
         * An accent whose colours are read from the theme each time they are used - how every built-in
         * accent below is defined, and how a custom one should be if it is to survive a retheme.
         */
        fun derived(
            name: String,
            accent: () -> Color,
            accentHover: () -> Color,
            dark: () -> Color,
            darkHover: () -> Color,
            bottom: () -> Color,
            bottomHover: () -> Color,
            outer: () -> Color = { Colors.UI_OUTER_BORDER },
            isDefault: Boolean = false,
        ) = BrassAccent(name, accent, accentHover, dark, darkHover, outer, bottom, bottomHover, isDefault)

        /** Neutral - uses the element colours in [Colors], no tint. */
        val DEFAULT = derived(
            "default",
            accent = { Colors.UI_ELEMENT_BORDER }, accentHover = { Colors.UI_ELEMENT_BORDER_HOVER },
            dark = { Colors.UI_ELEMENT_BG }, darkHover = { Colors.UI_ELEMENT_BG_HOVER },
            bottom = { Colors.KEYCAP_BOTTOM }, bottomHover = { Colors.KEYCAP_BOTTOM },
            isDefault = true,
        )

        /** Brass - the primary green accent. */
        val BRASS = derived(
            "brass",
            accent = { Colors.BRASS_400 }, accentHover = { Colors.BRASS_300 },
            dark = { Colors.ACCENT_KEYCAP_BG }, darkHover = { Colors.ACCENT_KEYCAP_BG_HOVER },
            bottom = { Colors.BRASS_700 }, bottomHover = { Colors.BRASS_600 },
        )

        /** "nice" - reuses the brass green. */
        val NICE = BRASS.copy("nice")

        /** Danger - destructive red. */
        val DANGER = derived(
            "danger",
            accent = { Colors.DANGER_ACCENT }, accentHover = { Colors.DANGER_ACCENT_HOVER },
            dark = { Colors.DANGER_KEYCAP_BG }, darkHover = { Colors.DANGER_KEYCAP_BG_HOVER },
            bottom = { Colors.DANGER_KEYCAP_BOTTOM }, bottomHover = { Colors.DANGER_KEYCAP_BOTTOM_HOVER },
        )

        /** Calm - the patina teal. */
        val CALM = derived(
            "calm",
            accent = { Colors.PATINA_400 }, accentHover = { Colors.CALM_ACCENT_HOVER },
            dark = { Colors.CALM_KEYCAP_BG }, darkHover = { Colors.CALM_KEYCAP_BG_HOVER },
            bottom = { Colors.CALM_KEYCAP_BOTTOM }, bottomHover = { Colors.CALM_KEYCAP_BOTTOM_HOVER },
        )
    }

    private fun copy(newName: String) = BrassAccent(
        newName, accentOf, accentHoverOf, darkOf, darkHoverOf, outerOf, bottomOf, bottomHoverOf,
        isDefault,
    )
}
