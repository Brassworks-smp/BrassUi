package net.swzo.brass.ui

import java.awt.Color

/**
 * A complete palette for the toolkit. Assign one to [Colors.theme] and every widget follows it.
 *
 * ### Writing a theme
 *
 * Everything is an `open` property, and the roles are **derived from the ramps** rather than repeated,
 * so the smallest useful theme overrides a handful of swatches and lets the rest fall out:
 *
 * ```
 * object Copper : BrassTheme("copper") {
 *     override val brass300 = Color(0xF0, 0xB4, 0x7A)
 *     override val brass400 = Color(0xD9, 0x8E, 0x5A)
 *     override val brass500 = Color(0xB8, 0x6E, 0x3C)
 *     override val brass600 = Color(0x94, 0x57, 0x2E)
 *     override val brass700 = Color(0x70, 0x41, 0x22)
 * }
 * Colors.theme = Copper
 * ```
 *
 * That repaints every accent, wash, selection highlight, primary button and keyword in a code block,
 * because each of those reads through [accent] / [accentBright] and so on. Override a role directly
 * when the derivation is not what you want - `override val selection = ...` - and only that role
 * changes.
 *
 * ### Why properties and not a data class
 *
 * These are read inside draw loops, so they must not allocate. Every default returns a **shared,
 * pre-built** [Color] instance rather than constructing one per call, and an override should do the
 * same by storing its value (`override val brass500 = Color(...)`, not `get() = Color(...)`).
 *
 * They are `get()` accessors rather than stored `val`s for a subtler reason: a stored property whose
 * initialiser reads another open property would capture the *base class's* value, because the
 * subclass's override is not initialised yet when the superclass constructor runs. Accessors resolve
 * at call time, which is what makes overriding one ramp entry actually reach the roles built on it.
 *
 * ### Switching at runtime
 *
 * Widgets recompute their colour targets every frame and ease toward them, so assigning [Colors.theme]
 * mid-session animates the whole UI over to the new palette rather than needing a restart.
 */
open class BrassTheme(
    /** Shown in the dev inspector; otherwise only for the app's own theme picker. */
    val name: String = "dark",
) {

    // ---- ink: surfaces & text ----------------------------------------------------------------

    open val ink950: Color get() = D_INK_950
    open val ink900: Color get() = D_INK_900
    open val ink850: Color get() = D_INK_850
    open val ink800: Color get() = D_INK_800
    open val ink700: Color get() = D_INK_700
    open val ink600: Color get() = D_INK_600

    // ---- brass: the accent ramp --------------------------------------------------------------

    open val brass300: Color get() = D_BRASS_300
    open val brass400: Color get() = D_BRASS_400
    open val brass500: Color get() = D_BRASS_500
    open val brass600: Color get() = D_BRASS_600
    open val brass700: Color get() = D_BRASS_700

    // ---- patina: the secondary accent --------------------------------------------------------

    open val patina400: Color get() = D_PATINA_400
    open val patina500: Color get() = D_PATINA_500

    // ---- text --------------------------------------------------------------------------------

    open val text: Color get() = D_TEXT
    open val textStrong: Color get() = D_TEXT_STRONG
    open val textMuted: Color get() = D_TEXT_MUTED
    open val textOnAccent: Color get() = D_TEXT_ON_ACCENT
    open val textShadow: Color get() = D_TEXT_SHADOW

    // ---- lines & semantics -------------------------------------------------------------------

    open val edge: Color get() = D_EDGE
    open val edgeStrong: Color get() = D_EDGE_STRONG
    open val danger: Color get() = D_DANGER
    open val warn: Color get() = D_WARN
    open val good: Color get() = brass400

    /** Fully transparent - for "no border" panels. Not worth theming, but kept here for symmetry. */
    open val none: Color get() = D_NONE

    // ---- surfaces ----------------------------------------------------------------------------

    open val background: Color get() = ink900
    open val panel: Color get() = ink800
    open val innerBg: Color get() = D_INNER_BG
    open val innerBgSelected: Color get() = D_INNER_BG_SELECTED
    open val innerBorder: Color get() = D_INNER_BORDER

    // ---- interactive controls ----------------------------------------------------------------

    open val elementBg: Color get() = D_ELEMENT_BG
    open val elementBgHover: Color get() = D_ELEMENT_BG_HOVER
    open val elementBgActive: Color get() = D_ELEMENT_BG_ACTIVE
    open val elementBorder: Color get() = D_ELEMENT_BORDER
    open val elementBorderHover: Color get() = D_ELEMENT_BORDER_HOVER
    /** The near-black outer ring that seats window and panel chrome. */
    open val outerBorder: Color get() = D_OUTER_BORDER

    open val componentBg: Color get() = D_COMPONENT_BG
    open val componentBgHover: Color get() = D_COMPONENT_BG_HOVER
    open val componentBgActive: Color get() = D_COMPONENT_BG_ACTIVE

    open val buttonFill: Color get() = D_BUTTON_FILL
    open val buttonFillHover: Color get() = D_BUTTON_FILL_HOVER

    open val outline: Color get() = D_OUTLINE
    open val outlineHover: Color get() = D_OUTLINE_HOVER
    open val outlineAccent: Color get() = brass300

    open val dangerFill: Color get() = D_DANGER_FILL
    open val dangerFillHover: Color get() = D_DANGER_FILL_HOVER
    open val primaryFill: Color get() = brass600
    open val primaryFillHover: Color get() = brass500

    open val knobFill: Color get() = D_KNOB_FILL
    open val knobFillHover: Color get() = D_KNOB_FILL_HOVER

    // ---- accent roles ------------------------------------------------------------------------

    open val accent: Color get() = brass500
    open val accentBright: Color get() = brass300
    open val accentBorder: Color get() = brass500
    /**
     * Wash behind a selected row / the active tab. Derived, so it follows a retinted accent.
     *
     * `by lazy` rather than `get()` because deriving it means building a new [Color], and these are
     * read every frame - the accessor form would allocate per read in a draw loop. Lazy also runs
     * after construction, so it still sees a subclass's overridden [accent], which a stored `val`
     * initialiser would not.
     */
    open val selection: Color by lazy { Colors.withAlpha(accent, 38) }
    open val selectionFaint: Color by lazy { Colors.withAlpha(accent, 14) }
    open val hoverFill: Color get() = D_HOVER_FILL

    // ---- chrome ------------------------------------------------------------------------------
    // The washes, shadows and hairlines widgets used to hardcode. They are separate roles rather than
    // one "shadow" because they are read at different strengths and a light theme needs to retune
    // them independently - on a pale surface a drop shadow wants less alpha and a stripe wants more.

    /** Drop shadow under a raised block. */
    open val shadow: Color get() = D_SHADOW
    /** The softer shadow a widget's keycap casts. */
    open val softShadow: Color get() = D_SOFT_SHADOW
    /** A card's two-stop drop shadow: the tight inner stop and the wide outer one. */
    open val cardShadowNear: Color get() = D_CARD_SHADOW_NEAR
    open val cardShadowFar: Color get() = D_CARD_SHADOW_FAR
    /** The dimming behind a modal. Alpha is scaled by the popup's own fade. */
    open val scrim: Color get() = D_SCRIM

    /** Alternating row tint in a table. */
    open val rowStripe: Color get() = D_ROW_STRIPE
    /** Row under the cursor. */
    open val rowHover: Color get() = D_ROW_HOVER
    /** The recessed channel a scrollbar thumb runs in. */
    open val scrollTrack: Color get() = D_SCROLL_TRACK

    /**
     * The ridged grip on a resize corner, a slider handle or a toggle knob, and the lit edge along its
     * top.
     *
     * These *are* the knob roles: a grip and a knob are the same part in different clothes, and having
     * two near-identical swatches meant a theme could tune one and leave the other on the default grey.
     * Which is what happened - knob fills followed the ramp while grips stayed a fixed #3A3A3A, so a
     * slider handle only looked themed while the cursor was over it.
     */
    open val grip: Color get() = knobFill
    open val gripEdge: Color get() = knobFillHover

    /** A divider's dark groove and the highlight below it that gives the bevel. */
    open val dividerShade: Color get() = D_DIVIDER_SHADE
    open val dividerHighlight: Color get() = D_DIVIDER_HIGHLIGHT

    /** The indent guide in the dev inspector's tree. */
    open val treeGuide: Color get() = D_TREE_GUIDE

    /** Background behind a fenced code block. */
    open val codeBg: Color get() = D_CODE_BG

    /** The value chip that rides a slider. */
    open val sliderChip: Color get() = D_SLIDER_CHIP
    /** An empty item slot. */
    open val itemEmpty: Color get() = D_ITEM_EMPTY

    /** A failed progress bar: the fill and its lit leading edge. */
    open val progressFail: Color get() = D_PROGRESS_FAIL
    open val progressFailLit: Color get() = D_PROGRESS_FAIL_LIT

    // ---- keycap accents ----------------------------------------------------------------------
    // The per-accent keycap palette behind BrassAccent's built-in variants: the tinted fill under a
    // coloured control, and the raised bottom lip. Separate roles because an accent's fill is a deep,
    // desaturated version of its border colour rather than the border at lower alpha - deriving it
    // arithmetically gave muddy results, so each is a swatch a theme can tune.

    /** The raised bottom lip of a neutral keycap. */
    open val keycapBottom: Color get() = D_KEYCAP_BOTTOM

    /** Fill under a brass-accented control. */
    open val accentKeycapBg: Color get() = D_ACCENT_KEYCAP_BG
    open val accentKeycapBgHover: Color get() = D_ACCENT_KEYCAP_BG_HOVER

    /** The destructive accent: border, fill and lip. */
    open val dangerAccent: Color get() = D_DANGER_ACCENT
    open val dangerAccentHover: Color get() = D_DANGER_ACCENT_HOVER
    open val dangerKeycapBg: Color get() = D_DANGER_KEYCAP_BG
    open val dangerKeycapBgHover: Color get() = D_DANGER_KEYCAP_BG_HOVER
    open val dangerKeycapBottom: Color get() = D_DANGER_KEYCAP_BOTTOM
    open val dangerKeycapBottomHover: Color get() = D_DANGER_KEYCAP_BOTTOM_HOVER

    /** The calm/patina accent: hover border, fill and lip. */
    open val calmAccentHover: Color get() = D_CALM_ACCENT_HOVER
    open val calmKeycapBg: Color get() = D_CALM_KEYCAP_BG
    open val calmKeycapBgHover: Color get() = D_CALM_KEYCAP_BG_HOVER
    open val calmKeycapBottom: Color get() = D_CALM_KEYCAP_BOTTOM
    open val calmKeycapBottomHover: Color get() = D_CALM_KEYCAP_BOTTOM_HOVER

    // ---- loading skeleton --------------------------------------------------------------------

    /** The three stops of the shimmer that sweeps a loading image. */
    open val shimmerEdge: Color get() = D_SHIMMER_EDGE
    open val shimmerMid: Color get() = D_SHIMMER_MID
    open val shimmerCore: Color get() = D_SHIMMER_CORE
    /** The mark left where an image failed to load. */
    open val imageFailed: Color get() = itemEmpty

    // ---- syntax highlighting -----------------------------------------------------------------
    // Code blocks are part of the theme: highlighted source sitting in an unrelated palette is the
    // most obvious way a retheme looks half-finished.

    open val syntaxComment: Color get() = D_SYNTAX_COMMENT
    open val syntaxString: Color get() = D_SYNTAX_STRING
    open val syntaxKeyword: Color get() = brass400
    open val syntaxNumber: Color get() = D_SYNTAX_NUMBER
    open val syntaxMacro: Color get() = D_SYNTAX_MACRO
    open val syntaxDefault: Color get() = text

    /**
     * The accent this theme wants when the user has not chosen one - the launcher's
     * `defaultAccentForTheme`. Null keeps the theme's own [brass500].
     *
     * A theme is a whole look, not just a set of greys: ocean reads as ocean because it is cyan, not
     * because its panels are slightly blue. So selecting a theme adopts its accent, while an accent the
     * user picked explicitly still wins over it.
     */
    open val defaultAccent: Color? get() = null

    // ---- metrics -----------------------------------------------------------------------------

    /** Corner radius for interactive controls, and the larger one for panels and windows. */
    open val radius: Float get() = 4f
    open val radiusLarge: Float get() = 6f

    override fun toString(): String = "BrassTheme($name)"

    companion object {
        /** The default: the BrassWorks launcher's dark palette. */
        val DARK: BrassTheme = BrassTheme("dark")

        private fun rgb(hex: Int): Color = Color(hex or -0x1000000, true)
        private fun rgba(hex: Int, alpha: Int): Color =
            Color((hex shr 16) and 0xFF, (hex shr 8) and 0xFF, hex and 0xFF, alpha)

        // Shared instances so the accessors above never allocate.
        private val D_INK_950 = rgb(0x080808)
        private val D_INK_900 = rgb(0x0D0D0D)
        private val D_INK_850 = rgb(0x141414)
        private val D_INK_800 = rgb(0x1A1A1A)
        private val D_INK_700 = rgb(0x242424)
        private val D_INK_600 = rgb(0x9B9B9B)

        private val D_BRASS_300 = rgb(0x5FE393)
        private val D_BRASS_400 = rgb(0x34D27A)
        private val D_BRASS_500 = rgb(0x1FBF63)
        private val D_BRASS_600 = rgb(0x18A153)
        private val D_BRASS_700 = rgb(0x14803F)

        private val D_PATINA_400 = rgb(0x34E0B4)
        private val D_PATINA_500 = rgb(0x1FB88F)

        private val D_TEXT = rgb(0xEDEDED)
        private val D_TEXT_STRONG = rgb(0xF3F3F3)
        private val D_TEXT_MUTED = rgb(0x8A8A8A)
        private val D_TEXT_ON_ACCENT = rgb(0x08140A)
        private val D_TEXT_SHADOW = rgb(0x000000)

        private val D_EDGE = rgba(0xE5E7EB, 26)
        private val D_EDGE_STRONG = rgba(0xE5E7EB, 56)
        private val D_DANGER = rgb(0xF3795F)
        private val D_WARN = rgb(0xE0B15A)
        private val D_NONE = Color(0, 0, 0, 0)

        private val D_INNER_BG = rgb(0x141414)
        private val D_INNER_BG_SELECTED = rgb(0x1E1E1E)
        private val D_INNER_BORDER = rgb(0x262626)

        private val D_ELEMENT_BG = rgb(0x1B1B1B)
        private val D_ELEMENT_BG_HOVER = rgb(0x242424)
        private val D_ELEMENT_BG_ACTIVE = rgb(0x2E2E2E)
        private val D_ELEMENT_BORDER = rgb(0x303030)
        private val D_ELEMENT_BORDER_HOVER = rgb(0x454545)
        private val D_OUTER_BORDER = rgb(0x050505)

        private val D_COMPONENT_BG = rgb(0x1B1B1B)
        private val D_COMPONENT_BG_HOVER = rgb(0x2C2C2C)
        private val D_COMPONENT_BG_ACTIVE = rgb(0x3A3A3A)

        private val D_BUTTON_FILL = rgb(0x383838)
        private val D_BUTTON_FILL_HOVER = rgb(0x4A4A4A)

        private val D_OUTLINE = rgb(0x050805)
        private val D_OUTLINE_HOVER = rgb(0xE9EBE6)

        private val D_DANGER_FILL = rgb(0x9F4444)
        private val D_DANGER_FILL_HOVER = rgb(0xC02525)

        private val D_KNOB_FILL = rgb(0x3A3A3A)
        private val D_KNOB_FILL_HOVER = rgb(0x525252)

        private val D_HOVER_FILL = Color(0xFF, 0xFF, 0xFF, 12)

        private val D_SHADOW = Color(0, 0, 0, 120)
        private val D_SOFT_SHADOW = Color(0, 0, 0, 64)
        private val D_CARD_SHADOW_NEAR = Color(0, 0, 0, 90)
        private val D_CARD_SHADOW_FAR = Color(0, 0, 0, 45)
        private val D_SCRIM = Color(0, 0, 0, 255)

        private val D_ROW_STRIPE = Color(255, 255, 255, 6)
        private val D_ROW_HOVER = Color(255, 255, 255, 16)
        private val D_SCROLL_TRACK = Color(255, 255, 255, 8)


        private val D_DIVIDER_SHADE = Color(0x08, 0x08, 0x08, 235)
        private val D_DIVIDER_HIGHLIGHT = Color(0xFF, 0xFF, 0xFF, 20)
        private val D_TREE_GUIDE = Color(0xFF, 0xFF, 0xFF, 22)

        private val D_CODE_BG = Color(0x0D, 0x0D, 0x0D, 200)
        private val D_SLIDER_CHIP = Color(0x0D, 0x0D, 0x0D, 220)
        private val D_ITEM_EMPTY = Color(0x3A, 0x3A, 0x3A, 200)

        private val D_PROGRESS_FAIL = rgb(0x8C281E)
        private val D_PROGRESS_FAIL_LIT = rgb(0xE0533F)

        private val D_KEYCAP_BOTTOM = rgb(0x161717)
        private val D_ACCENT_KEYCAP_BG = rgb(0x113A24)
        private val D_ACCENT_KEYCAP_BG_HOVER = rgb(0x164C2F)
        private val D_DANGER_ACCENT = rgb(0xF14B2F)
        private val D_DANGER_ACCENT_HOVER = rgb(0xFF6347)
        private val D_DANGER_KEYCAP_BG = rgb(0x3A0E0A)
        private val D_DANGER_KEYCAP_BG_HOVER = rgb(0x4C120D)
        private val D_DANGER_KEYCAP_BOTTOM = rgb(0x660B11)
        private val D_DANGER_KEYCAP_BOTTOM_HOVER = rgb(0x801418)
        private val D_CALM_ACCENT_HOVER = rgb(0x6CC4F1)
        private val D_CALM_KEYCAP_BG = rgb(0x132C36)
        private val D_CALM_KEYCAP_BG_HOVER = rgb(0x183A48)
        private val D_CALM_KEYCAP_BOTTOM = rgb(0x1D3B58)
        private val D_CALM_KEYCAP_BOTTOM_HOVER = rgb(0x274E72)

        private val D_SHIMMER_EDGE = Color(255, 255, 255, 6)
        private val D_SHIMMER_MID = Color(255, 255, 255, 10)
        private val D_SHIMMER_CORE = Color(255, 255, 255, 16)

        private val D_SYNTAX_COMMENT = rgb(0x6B6E6B)
        private val D_SYNTAX_STRING = rgb(0xD8A657)
        private val D_SYNTAX_NUMBER = rgb(0x7FD1C1)
        private val D_SYNTAX_MACRO = rgb(0xD98E5A)
    }
}
