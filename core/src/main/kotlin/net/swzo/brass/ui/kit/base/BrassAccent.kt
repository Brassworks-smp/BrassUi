package net.swzo.brass.ui.kit.base

import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent.Companion.derived
import java.awt.Color

/**
 * A widget accent. A **colored** accent tints a
 * control's fill (`darkColor`), border/text (`accentColor`), and raised bottom edge (`bottomColor`); the
 * `DEFAULT` accent instead falls back to the neutral element colours in [Colors] (dark fill, grey
 * border, light text). Each field has a `…Hover` sibling for the animated hover state.
 * The set is default/nice/danger/calm, filled from the BrassWorks palette - `NICE`/`BRASS` are our
 * green, `DANGER` red, `CALM` the patina teal.
 * ### Themeing
 * The built-in accents resolve their colours from [Colors] **on every read**, not once when the class
 * is first touched, so they follow a theme swap like everything else. This matters more than it looks:
 * a widget holds its [BrassAccent] for life and re-reads these fields every frame to pick its animation
 * targets, so an accent that snapshotted its colours would leave every accented control - the primary
 * buttons, the danger buttons, the selected tab - stranded on the old palette while the neutral chrome
 * around it retinted.
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

    val accent: Color get() = accentOf()
    val accentHover: Color get() = accentHoverOf()

    val dark: Color get() = darkOf()
    val darkHover: Color get() = darkHoverOf()

    val outer: Color get() = outerOf()

    val bottom: Color get() = bottomOf()
    val bottomHover: Color get() = bottomHoverOf()

    companion object {

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

        val DEFAULT = derived(
            "default",
            accent = { Colors.UI_ELEMENT_BORDER }, accentHover = { Colors.UI_ELEMENT_BORDER_HOVER },
            dark = { Colors.UI_ELEMENT_BG }, darkHover = { Colors.UI_ELEMENT_BG_HOVER },
            bottom = { Colors.KEYCAP_BOTTOM }, bottomHover = { Colors.KEYCAP_BOTTOM },
            isDefault = true,
        )

        val BRASS = derived(
            "brass",
            accent = { Colors.BRASS_400 }, accentHover = { Colors.BRASS_300 },
            dark = { Colors.ACCENT_KEYCAP_BG }, darkHover = { Colors.ACCENT_KEYCAP_BG_HOVER },
            bottom = { Colors.BRASS_700 }, bottomHover = { Colors.BRASS_600 },
        )

        val NICE = BRASS.copy()

        val DANGER = derived(
            "danger",
            accent = { Colors.DANGER_ACCENT }, accentHover = { Colors.DANGER_ACCENT_HOVER },
            dark = { Colors.DANGER_KEYCAP_BG }, darkHover = { Colors.DANGER_KEYCAP_BG_HOVER },
            bottom = { Colors.DANGER_KEYCAP_BOTTOM }, bottomHover = { Colors.DANGER_KEYCAP_BOTTOM_HOVER },
        )

        val CALM = derived(
            "calm",
            accent = { Colors.PATINA_400 }, accentHover = { Colors.CALM_ACCENT_HOVER },
            dark = { Colors.CALM_KEYCAP_BG }, darkHover = { Colors.CALM_KEYCAP_BG_HOVER },
            bottom = { Colors.CALM_KEYCAP_BOTTOM }, bottomHover = { Colors.CALM_KEYCAP_BOTTOM_HOVER },
        )
    }

    private fun copy() = BrassAccent(
        "nice", accentOf, accentHoverOf, darkOf, darkHoverOf, outerOf, bottomOf, bottomHoverOf,
        isDefault,
    )
}
