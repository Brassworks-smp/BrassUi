package net.swzo.brass.ui

import java.awt.Color

/**
 * A [BrassTheme] that takes its **primitive** roles from another theme, so a subclass only has to
 * state what it actually changes.
 * ### Why this exists
 * [BrassThemes]' accent tinting used to be a hand-written list of 62 `override val x get() = base.x`
 * lines against a base class declaring 100 roles. The other 38 did not forward at all, and nothing
 * distinguished the ones that were *deliberately* not forwarded from the ones that had simply been
 * missed. Every role added to [BrassTheme] from then on would be broken under accent tinting by
 * default, silently.
 * ### What is NOT forwarded, and why
 * [BrassTheme] builds these out of the brass ramp:
 * - `accent`
 * - `accentBorder`
 * - `accentBright`
 * - `good`
 * - `outlineAccent`
 * - `primaryFill`
 * - `primaryFillHover`
 * - `selection`
 * - `selectionFaint`
 * - `syntaxKeyword`
 * Forwarding them is **wrong**, and getting that wrong is what broke accent switching: they were
 * taken from the base theme's untinted ramp, so a window header's brass seam, a tooltip's left rule,
 * a dropdown's seam and every heading kept the old colour while the ramp underneath them changed.
 * Leaving them out means [BrassTheme]'s own derivation runs, against whatever `brass300`…`brass700`
 * the subclass ends up with - the forwarded ones for a plain wrapper, the tinted ones for
 * `AccentTinted`. Both cases come out right without either having to restate the derivation.
 * ```kotlin
 * class Tinted(base: BrassTheme, tint: Color) : BrassForwardingTheme(base) {
 *     override val brass500 get() = tint      // and the rest of the ramp; nothing else
 * }
 * ```
 * **Generated shape, hand-maintained:** adding a role to [BrassTheme] means adding a matching line
 * here - unless it is derived from the ramp, in which case it belongs in the list above instead.
 */
open class BrassForwardingTheme(
    protected val base: BrassTheme,
    name: String = base.name,
) : BrassTheme(name) {

    override val ink950: Color get() = base.ink950
    override val ink900: Color get() = base.ink900
    override val ink850: Color get() = base.ink850
    override val ink800: Color get() = base.ink800
    override val ink700: Color get() = base.ink700
    override val ink600: Color get() = base.ink600
    override val brass300: Color get() = base.brass300
    override val brass400: Color get() = base.brass400
    override val brass500: Color get() = base.brass500
    override val brass600: Color get() = base.brass600
    override val brass700: Color get() = base.brass700
    override val patina400: Color get() = base.patina400
    override val patina500: Color get() = base.patina500
    override val text: Color get() = base.text
    override val textStrong: Color get() = base.textStrong
    override val textMuted: Color get() = base.textMuted
    override val textOnAccent: Color get() = base.textOnAccent
    override val textShadow: Color get() = base.textShadow
    override val edge: Color get() = base.edge
    override val edgeStrong: Color get() = base.edgeStrong
    override val danger: Color get() = base.danger
    override val warn: Color get() = base.warn
    override val none: Color get() = base.none
    override val background: Color get() = base.background
    override val panel: Color get() = base.panel
    override val innerBg: Color get() = base.innerBg
    override val innerBgSelected: Color get() = base.innerBgSelected
    override val innerBorder: Color get() = base.innerBorder
    override val elementBg: Color get() = base.elementBg
    override val elementBgHover: Color get() = base.elementBgHover
    override val elementBgActive: Color get() = base.elementBgActive
    override val elementBorder: Color get() = base.elementBorder
    override val elementBorderHover: Color get() = base.elementBorderHover
    override val outerBorder: Color get() = base.outerBorder
    override val componentBg: Color get() = base.componentBg
    override val componentBgHover: Color get() = base.componentBgHover
    override val componentBgActive: Color get() = base.componentBgActive
    override val buttonFill: Color get() = base.buttonFill
    override val buttonFillHover: Color get() = base.buttonFillHover
    override val outline: Color get() = base.outline
    override val outlineHover: Color get() = base.outlineHover
    override val dangerFill: Color get() = base.dangerFill
    override val dangerFillHover: Color get() = base.dangerFillHover
    override val knobFill: Color get() = base.knobFill
    override val knobFillHover: Color get() = base.knobFillHover
    override val hoverFill: Color get() = base.hoverFill
    override val shadow: Color get() = base.shadow
    override val softShadow: Color get() = base.softShadow
    override val cardShadowNear: Color get() = base.cardShadowNear
    override val cardShadowFar: Color get() = base.cardShadowFar
    override val scrim: Color get() = base.scrim
    override val rowStripe: Color get() = base.rowStripe
    override val rowHover: Color get() = base.rowHover
    override val scrollTrack: Color get() = base.scrollTrack
    override val grip: Color get() = base.grip
    override val gripEdge: Color get() = base.gripEdge
    override val dividerShade: Color get() = base.dividerShade
    override val dividerHighlight: Color get() = base.dividerHighlight
    override val treeGuide: Color get() = base.treeGuide
    override val codeBg: Color get() = base.codeBg
    override val sliderChip: Color get() = base.sliderChip
    override val itemEmpty: Color get() = base.itemEmpty
    override val progressFail: Color get() = base.progressFail
    override val progressFailLit: Color get() = base.progressFailLit
    override val keycapBottom: Color get() = base.keycapBottom
    override val accentKeycapBg: Color get() = base.accentKeycapBg
    override val accentKeycapBgHover: Color get() = base.accentKeycapBgHover
    override val dangerAccent: Color get() = base.dangerAccent
    override val dangerAccentHover: Color get() = base.dangerAccentHover
    override val dangerKeycapBg: Color get() = base.dangerKeycapBg
    override val dangerKeycapBgHover: Color get() = base.dangerKeycapBgHover
    override val dangerKeycapBottom: Color get() = base.dangerKeycapBottom
    override val dangerKeycapBottomHover: Color get() = base.dangerKeycapBottomHover
    override val calmAccentHover: Color get() = base.calmAccentHover
    override val calmKeycapBg: Color get() = base.calmKeycapBg
    override val calmKeycapBgHover: Color get() = base.calmKeycapBgHover
    override val calmKeycapBottom: Color get() = base.calmKeycapBottom
    override val calmKeycapBottomHover: Color get() = base.calmKeycapBottomHover
    override val shimmerEdge: Color get() = base.shimmerEdge
    override val shimmerMid: Color get() = base.shimmerMid
    override val shimmerCore: Color get() = base.shimmerCore
    override val imageFailed: Color get() = base.imageFailed
    override val syntaxComment: Color get() = base.syntaxComment
    override val syntaxString: Color get() = base.syntaxString
    override val syntaxNumber: Color get() = base.syntaxNumber
    override val syntaxMacro: Color get() = base.syntaxMacro
    override val syntaxDefault: Color get() = base.syntaxDefault
    override val defaultAccent: Color? get() = base.defaultAccent
    override val radius: Float get() = base.radius
    override val radiusLarge: Float get() = base.radiusLarge
}
