@file:Suppress("unused")
package net.swzo.brass.ui

import net.swzo.brass.ui.Colors.theme
import java.awt.Color

/**
 * How every brassui component reads colour. Each name is a **role** - "the fill of an interactive
 * control", not "dark grey #1B1B1B" - and each one forwards to the current [theme].
 * Nothing here holds a swatch of its own any more: swap [theme] and the whole toolkit follows,
 * including code blocks, washes and drop shadows. See [BrassTheme] for how to write one.
 * ```
 * Colors.theme = MyTheme      // anywhere; widgets ease over to it
 * ```
 * Widgets should keep referencing these role names rather than reaching into [theme] directly, so a
 * later change to how a role is derived stays in one place.
 */
object Colors {

    /**
     * The palette in force. Assign to retheme the toolkit.
     * Safe to change at runtime: widgets recompute their colour targets every frame and ease toward
     * them, so a swap animates in. Set it from the render thread, like anything else that touches UI.
     */
    var theme: BrassTheme = BrassTheme.DARK


    fun withAlpha(base: Color, alpha: Int): Color = Color(base.red, base.green, base.blue, alpha)

    fun mix(a: Color, b: Color, t: Float): Color {
        val u = t.coerceIn(0f, 1f)
        if (u <= 0f) return a
        if (u >= 1f) return b
        fun c(x: Int, y: Int) = (x + (y - x) * u).toInt().coerceIn(0, 255)
        return Color(c(a.red, b.red), c(a.green, b.green), c(a.blue, b.blue), c(a.alpha, b.alpha))
    }

    fun lighten(base: Color, amount: Float): Color {
        val t = amount.coerceIn(0f, 1f)
        if (t <= 0f) return base
        fun c(x: Int) = (x + (255 - x) * t).toInt().coerceIn(0, 255)
        return Color(c(base.red), c(base.green), c(base.blue), base.alpha)
    }


    val INK_950: Color get() = theme.ink950
    val INK_900: Color get() = theme.ink900
    val INK_850: Color get() = theme.ink850
    val INK_800: Color get() = theme.ink800
    val INK_700: Color get() = theme.ink700
    val INK_600: Color get() = theme.ink600


    val BRASS_300: Color get() = theme.brass300
    val BRASS_400: Color get() = theme.brass400
    val BRASS_500: Color get() = theme.brass500
    val BRASS_600: Color get() = theme.brass600
    val BRASS_700: Color get() = theme.brass700


    val PATINA_400: Color get() = theme.patina400
    val PATINA_500: Color get() = theme.patina500


    val TEXT: Color get() = theme.text
    val TEXT_STRONG: Color get() = theme.textStrong
    val TEXT_ON_ACCENT: Color get() = theme.textOnAccent
    val TEXT_SHADOW: Color get() = theme.textShadow


    val EDGE: Color get() = theme.edge
    val EDGE_STRONG: Color get() = theme.edgeStrong
    val DANGER: Color get() = theme.danger
    val WARN: Color get() = theme.warn
    val GOOD: Color get() = theme.good

    val NONE: Color get() = theme.none


    val COMPONENT_BG: Color get() = theme.componentBg
    val COMPONENT_BG_HOVER: Color get() = theme.componentBgHover
    val COMPONENT_BG_ACTIVE: Color get() = theme.componentBgActive

    val BUTTON_FILL: Color get() = theme.buttonFill
    val BUTTON_FILL_HOVER: Color get() = theme.buttonFillHover

    val OUTLINE: Color get() = theme.outline
    val OUTLINE_HOVER: Color get() = theme.outlineHover
    val OUTLINE_BRASS: Color get() = theme.outlineAccent

    val DANGER_FILL: Color get() = theme.dangerFill
    val DANGER_FILL_HOVER: Color get() = theme.dangerFillHover
    val PRIMARY_FILL: Color get() = theme.primaryFill
    val PRIMARY_FILL_HOVER: Color get() = theme.primaryFillHover

    val KNOB_FILL: Color get() = theme.knobFill
    val KNOB_FILL_HOVER: Color get() = theme.knobFillHover


    val PANEL: Color get() = theme.panel

    @Deprecated("Use UI_BACKGROUND", ReplaceWith("UI_BACKGROUND"))
    val BACKGROUND: Color get() = theme.background

    @Deprecated("Use UI_SELECTION", ReplaceWith("UI_SELECTION"))
    val ACCENT_WASH: Color get() = theme.selection

    @Deprecated("Use UI_SELECTION_FAINT", ReplaceWith("UI_SELECTION_FAINT"))
    val ACCENT_WASH_FAINT: Color get() = theme.selectionFaint

    val HOVER_FILL: Color get() = theme.hoverFill


    val RADIUS: Float get() = theme.radius
    val RADIUS_LG: Float get() = theme.radiusLarge

    val UI_BACKGROUND: Color get() = theme.background
    val UI_INNER_BG: Color get() = theme.innerBg
    val UI_INNER_BG_SELECTED: Color get() = theme.innerBgSelected
    val UI_INNER_BORDER: Color get() = theme.innerBorder

    val UI_ELEMENT_BG: Color get() = theme.elementBg
    val UI_ELEMENT_BG_HOVER: Color get() = theme.elementBgHover
    val UI_ELEMENT_BG_ACTIVE: Color get() = theme.elementBgActive
    val UI_ELEMENT_BORDER: Color get() = theme.elementBorder
    val UI_ELEMENT_BORDER_HOVER: Color get() = theme.elementBorderHover
    val UI_OUTER_BORDER: Color get() = theme.outerBorder

    val UI_TEXT: Color get() = theme.text
    val UI_TEXT_DARK: Color get() = theme.textMuted
    val UI_TEXT_HOVER: Color get() = theme.textStrong

    val UI_ACCENT: Color get() = theme.accent
    val UI_ACCENT_BRIGHT: Color get() = theme.accentBright
    val UI_ACCENT_BORDER: Color get() = theme.accentBorder
    val UI_SELECTION: Color get() = theme.selection
    val UI_SELECTION_FAINT: Color get() = theme.selectionFaint

    // Roles that used to be hardcoded inside individual widgets.

    val SHADOW: Color get() = theme.shadow
    val SOFT_SHADOW: Color get() = theme.softShadow
    val CARD_SHADOW_NEAR: Color get() = theme.cardShadowNear
    val CARD_SHADOW_FAR: Color get() = theme.cardShadowFar
    val SCRIM: Color get() = theme.scrim

    val ROW_STRIPE: Color get() = theme.rowStripe
    val ROW_HOVER: Color get() = theme.rowHover
    val SCROLL_TRACK: Color get() = theme.scrollTrack

    val GRIP: Color get() = theme.grip
    val GRIP_EDGE: Color get() = theme.gripEdge

    val DIVIDER_SHADE: Color get() = theme.dividerShade
    val DIVIDER_HIGHLIGHT: Color get() = theme.dividerHighlight
    val TREE_GUIDE: Color get() = theme.treeGuide

    val CODE_BG: Color get() = theme.codeBg
    val SLIDER_CHIP: Color get() = theme.sliderChip
    val ITEM_EMPTY: Color get() = theme.itemEmpty

    val PROGRESS_FAIL: Color get() = theme.progressFail
    val PROGRESS_FAIL_LIT: Color get() = theme.progressFailLit


    val KEYCAP_BOTTOM: Color get() = theme.keycapBottom
    val ACCENT_KEYCAP_BG: Color get() = theme.accentKeycapBg
    val ACCENT_KEYCAP_BG_HOVER: Color get() = theme.accentKeycapBgHover

    val DANGER_ACCENT: Color get() = theme.dangerAccent
    val DANGER_ACCENT_HOVER: Color get() = theme.dangerAccentHover
    val DANGER_KEYCAP_BG: Color get() = theme.dangerKeycapBg
    val DANGER_KEYCAP_BG_HOVER: Color get() = theme.dangerKeycapBgHover
    val DANGER_KEYCAP_BOTTOM: Color get() = theme.dangerKeycapBottom
    val DANGER_KEYCAP_BOTTOM_HOVER: Color get() = theme.dangerKeycapBottomHover

    val CALM_ACCENT_HOVER: Color get() = theme.calmAccentHover
    val CALM_KEYCAP_BG: Color get() = theme.calmKeycapBg
    val CALM_KEYCAP_BG_HOVER: Color get() = theme.calmKeycapBgHover
    val CALM_KEYCAP_BOTTOM: Color get() = theme.calmKeycapBottom
    val CALM_KEYCAP_BOTTOM_HOVER: Color get() = theme.calmKeycapBottomHover


    val SHIMMER_EDGE: Color get() = theme.shimmerEdge
    val SHIMMER_MID: Color get() = theme.shimmerMid
    val SHIMMER_CORE: Color get() = theme.shimmerCore
    val IMAGE_FAILED: Color get() = theme.imageFailed


    val SYNTAX_COMMENT: Color get() = theme.syntaxComment
    val SYNTAX_STRING: Color get() = theme.syntaxString
    val SYNTAX_KEYWORD: Color get() = theme.syntaxKeyword
    val SYNTAX_NUMBER: Color get() = theme.syntaxNumber
    val SYNTAX_MACRO: Color get() = theme.syntaxMacro
    val SYNTAX_DEFAULT: Color get() = theme.syntaxDefault
}
