package net.swzo.brass.ui

import net.swzo.brass.ui.BrassThemes.accent
import net.swzo.brass.ui.BrassThemes.accentHex
import net.swzo.brass.ui.BrassThemes.apply
import net.swzo.brass.ui.BrassThemes.current
import net.swzo.brass.ui.BrassThemes.currentId
import java.awt.Color

/**
 * A theme built from an **ink ramp**: give it six greys, a foreground and an edge, and every surface,
 * border and muted-text role is derived from them.
 *
 * This is how the ported launcher themes are defined, and the easiest way to write a new one - the
 * launcher's own themes are exactly this, a ramp swap in CSS. The derivations mirror the relationships
 * the default palette already had: a panel is the ramp's 800, a control's hover fill is its 700, and a
 * border is the surface nudged toward the foreground, which is what keeps a border readable on a light
 * ramp as well as a dark one (mixing toward black would vanish on light).
 *
 * The accent ramp is optional: leave it null and the theme keeps brass, which is what most of the
 * launcher's themes do.
 */
open class BrassRampTheme(
    name: String,
    private val fg: Color,
    private val i950: Color,
    private val i900: Color,
    private val i850: Color,
    private val i800: Color,
    private val i700: Color,
    private val i600: Color,
    /** Optional accent ramp, brightest to darkest. Null keeps the default brass. */
    private val accentRamp: List<Color>? = null,
    /**
     * True for a light ramp - flips the washes and shadow strengths that assume a dark surface.
     *
     * No built-in theme sets this today (the ported light theme was dropped), but every derivation
     * that needs it still honours it, so adding a light theme back is a matter of supplying the ramp.
     */
    val light: Boolean = false,
    /** The accent this theme adopts when the user has not picked one. */
    private val themeAccent: Color? = null,
) : BrassTheme(name) {

    override val defaultAccent: Color? get() = themeAccent

    override val ink950: Color get() = i950
    override val ink900: Color get() = i900
    override val ink850: Color get() = i850
    override val ink800: Color get() = i800
    override val ink700: Color get() = i700
    override val ink600: Color get() = i600

    override val text: Color get() = fg
    override val textStrong: Color by lazy { Colors.mix(fg, if (light) Color.BLACK else Color.WHITE, 0.25f) }
    override val textMuted: Color get() = i600

    override val brass300: Color get() = accentRamp?.getOrNull(0) ?: super.brass300
    override val brass400: Color get() = accentRamp?.getOrNull(1) ?: super.brass400
    override val brass500: Color get() = accentRamp?.getOrNull(2) ?: super.brass500
    override val brass600: Color get() = accentRamp?.getOrNull(3) ?: super.brass600
    override val brass700: Color get() = accentRamp?.getOrNull(4) ?: super.brass700

    // ---- surfaces, derived from the ramp -----------------------------------------------------

    override val innerBg: Color get() = i850
    override val innerBgSelected: Color by lazy { toward(i850, 0.06f) }
    override val innerBorder: Color by lazy { toward(i850, 0.10f) }

    override val elementBg: Color get() = i800
    override val elementBgHover: Color get() = i700
    override val elementBgActive: Color by lazy { toward(i700, 0.06f) }
    override val elementBorder: Color by lazy { toward(i800, 0.12f) }
    override val elementBorderHover: Color by lazy { toward(i800, 0.24f) }
    override val outerBorder: Color by lazy { Colors.mix(i950, Color.BLACK, if (light) 0.10f else 0.45f) }

    override val componentBg: Color get() = i800
    override val componentBgHover: Color by lazy { toward(i800, 0.10f) }
    override val componentBgActive: Color by lazy { toward(i800, 0.18f) }
    override val buttonFill: Color by lazy { toward(i700, 0.10f) }
    override val buttonFillHover: Color by lazy { toward(i700, 0.20f) }

    override val knobFill: Color by lazy { toward(i700, 0.10f) }
    override val knobFillHover: Color by lazy { toward(i700, 0.24f) }

    override val keycapBottom: Color by lazy { Colors.mix(i800, Color.BLACK, if (light) 0.08f else 0.35f) }

    /** Neutral washes flip direction on a light ramp, where a white overlay would be invisible. */
    private val washInk: Color get() = if (light) Color.BLACK else Color.WHITE
    override val hoverFill: Color by lazy { Colors.withAlpha(washInk, if (light) 14 else 12) }
    override val rowStripe: Color by lazy { Colors.withAlpha(washInk, if (light) 10 else 6) }
    override val rowHover: Color by lazy { Colors.withAlpha(washInk, if (light) 20 else 16) }
    override val scrollTrack: Color by lazy { Colors.withAlpha(washInk, if (light) 12 else 8) }
    override val treeGuide: Color by lazy { Colors.withAlpha(washInk, if (light) 30 else 22) }
    override val dividerHighlight: Color by lazy { Colors.withAlpha(washInk, if (light) 10 else 20) }
    override val dividerShade: Color by lazy { Colors.withAlpha(Colors.mix(i950, Color.BLACK, 0.5f), 235) }

    override val codeBg: Color by lazy { Colors.withAlpha(i950, 200) }
    override val sliderChip: Color by lazy { Colors.withAlpha(i950, 220) }

    /** A light theme needs a far weaker shadow, or every card looks smudged. */
    override val shadow: Color by lazy { Colors.withAlpha(Color.BLACK, if (light) 40 else 120) }
    override val softShadow: Color by lazy { Colors.withAlpha(Color.BLACK, if (light) 24 else 64) }
    override val cardShadowNear: Color by lazy { Colors.withAlpha(Color.BLACK, if (light) 30 else 90) }
    override val cardShadowFar: Color by lazy { Colors.withAlpha(Color.BLACK, if (light) 16 else 45) }

    override val accentKeycapBg: Color by lazy { Colors.mix(i800, accent, if (light) 0.18f else 0.22f) }
    override val accentKeycapBgHover: Color by lazy { Colors.mix(i800, accent, if (light) 0.26f else 0.32f) }

    // ---- semantics, tuned to the ramp --------------------------------------------------------
    // Danger/warn/good/info are not one fixed red, amber, green and teal across every theme. Two
    // reasons: a mid-tone red that reads as urgent on near-black is muddy on Nord's slate and
    // illegible on a white surface, and a semantic colour that ignores the palette around it looks
    // pasted on. So each keeps its hue but is re-seated on this theme's ramp.

    override val danger: Color by lazy { semantic(super.danger) }
    override val warn: Color by lazy { semantic(super.warn) }
    override val good: Color by lazy { semantic(super.good) }
    override val patina400: Color by lazy { semantic(super.patina400) }
    override val patina500: Color by lazy { semantic(super.patina500) }

    /**
     * Re-seat a semantic hue on this ramp: temperature-match it to the surface, then set a brightness
     * that carries against it.
     *
     * The tint toward [ink600] is what makes the same red feel warm on mocha and cool on ocean. It is
     * deliberately restrained - push it much past a fifth and the colour stops reading as "red" and
     * starts reading as "brown", which costs more than the theming gains. Raise the 0.16 below if you
     * want the family resemblance to be more obvious. The brightness floor/ceiling is the part that actually matters: a light theme needs the
     * hue *darker* than its surface to be visible at all, which is the opposite of what every dark
     * theme needs.
     */
    private fun semantic(base: Color): Color {
        val tinted = Colors.mix(base, i600, 0.16f)
        val hsb = Color.RGBtoHSB(tinted.red, tinted.green, tinted.blue, null)
        val brightness = if (light) hsb[2].coerceAtMost(0.70f) else hsb[2].coerceAtLeast(0.62f)
        // Light surfaces wash colour out, so the hue is pushed harder to compensate.
        val saturation = if (light) (hsb[1] * 1.20f).coerceAtMost(1f) else hsb[1]
        return Color(Color.HSBtoRGB(hsb[0], saturation, brightness))
    }

    /** Nudge [c] toward the foreground - the direction that reads as "raised" on any ramp. */
    private fun toward(c: Color, amount: Float): Color = Colors.mix(c, fg, amount)
}

/**
 * The global theme registry: the list of themes an app can offer, which one is live, and the accent
 * tint layered on top of it.
 *
 * ### The static it syncs
 *
 * [Colors.theme] is the single static every widget reads. This object owns it - setting [current] or
 * [accent] recomposes and writes it, so the UI and the registry can never disagree. Read the toolkit's
 * live palette through [Colors] as usual; use this to *change* it.
 *
 * ### Persistence is the caller's job
 *
 * Nothing here touches disk. A mod (or the desktop app) decides where settings live, and this exposes
 * only the two primitives that need saving - the theme's [id][BrassTheme.name] and the accent hex:
 *
 * ```
 * // on save, from your own config code:
 * config.theme  = BrassThemes.currentId
 * config.accent = BrassThemes.accentHex
 *
 * // on load:
 * BrassThemes.apply(config.theme, config.accent)
 *
 * // or persist automatically whenever the user changes it:
 * BrassThemes.onChange { config.theme = BrassThemes.currentId; config.save() }
 * ```
 *
 * [apply] takes exactly what [currentId]/[accentHex] give back, so a round trip through a config file
 * needs no translation and an unknown id degrades to the default rather than throwing.
 */
object BrassThemes {

    // ---- built-in themes ---------------------------------------------------------------------
    // Ported from the BrassWorks launcher's globals.css, so a player's launcher theme and their
    // in-game UI can match. Each is the launcher's ink ramp; only `light` restates the accent, as
    // the brass green needs darkening to stay legible on a pale surface.

    private fun rgb(hex: Int) = Color(hex or -0x1000000, true)

    /** The original: near-black with the brass green. */
    val DEFAULT: BrassTheme = BrassTheme.DARK

    val GREY: BrassTheme = BrassRampTheme(
        "grey", fg = rgb(0xE7E9EC),
        i950 = rgb(0x181A1D), i900 = rgb(0x202327), i850 = rgb(0x272A2F),
        i800 = rgb(0x2E3237), i700 = rgb(0x3A3F45), i600 = rgb(0x9BA1A8),
    )

    val OCEAN: BrassTheme = BrassRampTheme(
        "ocean", fg = rgb(0xE6EDF3),
        i950 = rgb(0x0D1117), i900 = rgb(0x11161D), i850 = rgb(0x161D26),
        i800 = rgb(0x1C2530), i700 = rgb(0x2A3340), i600 = rgb(0x8B97A6),
        themeAccent = rgb(0x06B6D4),
    )

    val MOCHA: BrassTheme = BrassRampTheme(
        "mocha", fg = rgb(0xEDE3DA),
        i950 = rgb(0x1A1411), i900 = rgb(0x211A15), i850 = rgb(0x29201A),
        i800 = rgb(0x322820), i700 = rgb(0x40342A), i600 = rgb(0xA8998B),
        themeAccent = rgb(0xF97316),
    )

    val NORD: BrassTheme = BrassRampTheme(
        "nord", fg = rgb(0xECEFF4),
        i950 = rgb(0x2B303B), i900 = rgb(0x2E3440), i850 = rgb(0x353C4A),
        i800 = rgb(0x3B4252), i700 = rgb(0x4C566A), i600 = rgb(0x8A93A5),
        themeAccent = rgb(0x3B82F6),
    )

    val ROSE: BrassTheme = BrassRampTheme(
        "rose", fg = rgb(0xF0E0E6),
        i950 = rgb(0x1E151A), i900 = rgb(0x261A20), i850 = rgb(0x2F2028),
        i800 = rgb(0x382630), i700 = rgb(0x4A3340), i600 = rgb(0xB591A0),
        themeAccent = rgb(0xEC4899),
    )

    val AMETHYST: BrassTheme = BrassRampTheme(
        "amethyst", fg = rgb(0xE7E6F7),
        i950 = rgb(0x121022), i900 = rgb(0x181527), i850 = rgb(0x1E1A33),
        i800 = rgb(0x251F3F), i700 = rgb(0x332C54), i600 = rgb(0x948FC0),
        themeAccent = rgb(0x8B5CF6),
    )

    val CRIMSON: BrassTheme = BrassRampTheme(
        "crimson", fg = rgb(0xF3E3E1),
        i950 = rgb(0x1A0F0F), i900 = rgb(0x211311), i850 = rgb(0x2A1714),
        i800 = rgb(0x341C18), i700 = rgb(0x452621), i600 = rgb(0xB58E88),
        themeAccent = rgb(0xEF4444),
    )

    val FOREST: BrassTheme = BrassRampTheme(
        "forest", fg = rgb(0xE2EFE5),
        i950 = rgb(0x0D1611), i900 = rgb(0x111C15), i850 = rgb(0x15231A),
        i800 = rgb(0x1B2C20), i700 = rgb(0x263A2D), i600 = rgb(0x8AA896),
        themeAccent = rgb(0x10B981),
    )

    /** The launcher's accent set, in its order - a hue wheel from green round to lime, ending in grey. */
    val ACCENT_SWATCHES: List<Color> = listOf(
        rgb(0x34D27A), rgb(0x10B981), rgb(0x14B8A6), rgb(0x06B6D4),
        rgb(0x3B82F6), rgb(0x6366F1), rgb(0x8B5CF6), rgb(0xA855F7),
        rgb(0xEC4899), rgb(0xF43F5E), rgb(0xEF4444), rgb(0xF97316),
        rgb(0xF59E0B), rgb(0xEAB308), rgb(0x84CC16), rgb(0x9B9B9B),
    )

    /**
     * A keycap accent built from a single colour - what an accent swatch wears, and what a caller
     * needs to theme a control to an arbitrary colour.
     *
     * The fill and lip are derived rather than asked for: a swatch has one colour, but a keycap needs
     * four related ones to read as raised.
     */
    fun accentFor(c: Color): net.swzo.brass.ui.kit.base.BrassAccent =
        net.swzo.brass.ui.kit.base.BrassAccent.derived(
            "swatch",
            accent = { c },
            accentHover = { Colors.mix(c, Color.WHITE, 0.25f) },
            dark = { Colors.mix(Colors.UI_ELEMENT_BG, c, 0.55f) },
            darkHover = { Colors.mix(Colors.UI_ELEMENT_BG, c, 0.70f) },
            bottom = { Colors.mix(c, Color.BLACK, 0.45f) },
            bottomHover = { Colors.mix(c, Color.BLACK, 0.30f) },
        )

    // ---- registry ----------------------------------------------------------------------------

    private val registry = LinkedHashMap<String, BrassTheme>()

    init {
        listOf(DEFAULT, GREY, OCEAN, MOCHA, NORD, ROSE, AMETHYST, CRIMSON, FOREST)
            .forEach { register(it) }
    }

    /** Add a theme (or replace one with the same name). Registering the live theme re-applies it. */
    fun register(theme: BrassTheme) {
        registry[theme.name] = theme
        if (theme.name == baseId) apply(theme.name, accentHex)
    }

    /** Every registered theme, in registration order - what a theme picker lists. */
    fun all(): List<BrassTheme> = registry.values.toList()

    /** Look a theme up by id, or null if nothing is registered under it. */
    fun byId(id: String?): BrassTheme? = id?.let { registry[it] }

    // ---- current selection -------------------------------------------------------------------

    private var baseId: String = DEFAULT.name
    private var accentColor: Color? = null
    private val listeners = ArrayList<() -> Unit>()

    /** The selected theme, ignoring any accent tint. Assigning applies it immediately. */
    var current: BrassTheme
        get() = byId(baseId) ?: DEFAULT
        set(value) = apply(value.name, accentHex)

    /** The id to persist. */
    val currentId: String get() = baseId

    /**
     * The accent tint layered over the theme, or null for the theme's own accent. Assigning applies
     * it immediately.
     */
    var accent: Color?
        get() = accentColor
        set(value) = apply(baseId, value?.let(::toHex))

    /** The accent to persist, as `#RRGGBB`, or null when the theme's own accent is in use. */
    val accentHex: String? get() = accentColor?.let(::toHex)

    /**
     * Apply a saved selection. Unknown ids fall back to the default and an unparseable accent to none,
     * so a hand-edited or out-of-date config downgrades instead of failing.
     */
    fun apply(themeId: String?, accentHex: String? = null) {
        val base = byId(themeId) ?: DEFAULT
        baseId = base.name
        accentColor = accentHex?.let(::parseHex)
        // No explicit accent falls back to the theme's own, so picking "ocean" gets ocean's cyan
        // rather than brass green. An accent the user chose still overrides it.
        val effective = accentColor ?: base.defaultAccent
        Colors.theme = effective?.let { AccentTinted(base, it) } ?: base
        for (listener in listeners.toList()) listener()
    }

    /**
     * Run [listener] whenever the theme or accent changes - the hook a mod uses to write its config.
     * Returns a handle that removes it.
     */
    fun onChange(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    // ---- accent tinting ----------------------------------------------------------------------

    /**
     * A theme wearing a different accent: the base palette with the whole brass ramp rebuilt around
     * one colour.
     *
     * The ramp is generated rather than asking the user for five shades - the picker gives one. The
     * shades are lightness steps either side of it, which is what the launcher's ramps are, so a
     * generated ramp sits in the same relationship to its accent as a hand-tuned one.
     */
    private class AccentTinted(base: BrassTheme, private val tint: Color) :
        BrassForwardingTheme(base) {

        // ---- the tinted ramp -------------------------------------------------------------------
        override val brass300: Color by lazy { shade(tint, 0.35f) }
        override val brass400: Color by lazy { shade(tint, 0.18f) }
        override val brass500: Color get() = tint
        override val brass600: Color by lazy { shade(tint, -0.15f) }
        override val brass700: Color by lazy { shade(tint, -0.32f) }

        // Nothing else is stated here. The roles BrassTheme derives from the ramp - accent,
        // accentBright, selection, primaryFill, syntaxKeyword and the rest - are deliberately NOT
        // forwarded by BrassForwardingTheme, so its derivation runs against the tinted ramp above and
        // arrives at the right answer on its own. See there for what went wrong when they were.

        // The accented keycap has to be rebuilt from the tint, not inherited, or a primary button
        // keeps the old theme's green fill under its new border.
        override val accentKeycapBg: Color by lazy { Colors.mix(base.elementBg, tint, 0.22f) }
        override val accentKeycapBgHover: Color by lazy { Colors.mix(base.elementBg, tint, 0.32f) }

        /** Lighten (positive) or darken (negative) in HSB, so the hue survives the step. */
        private fun shade(c: Color, amount: Float): Color {
            val hsb = Color.RGBtoHSB(c.red, c.green, c.blue, null)
            val b = (hsb[2] + amount).coerceIn(0.05f, 1f)
            // Pull saturation back as it brightens, or a light step turns neon.
            val s = if (amount > 0f) (hsb[1] - amount * 0.35f).coerceIn(0f, 1f) else hsb[1]
            return Color(Color.HSBtoRGB(hsb[0], s, b))
        }
    }

    // ---- hex helpers -------------------------------------------------------------------------

    /** `#RRGGBB` for [c] - the form [apply] accepts and a config should store. */
    fun toHex(c: Color): String = "#%02X%02X%02X".format(c.red, c.green, c.blue)

    /** Parse `#RRGGBB` / `RRGGBB`; null when it is not one, so bad config data is simply ignored. */
    fun parseHex(hex: String): Color? {
        val body = hex.trim().removePrefix("#")
        if (body.length != 6) return null
        return runCatching { Color(body.toInt(16) or -0x1000000, true) }.getOrNull()
    }
}
