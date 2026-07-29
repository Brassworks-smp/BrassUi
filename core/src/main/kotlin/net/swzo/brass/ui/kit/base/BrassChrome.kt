package net.swzo.brass.ui.kit.base

/**
 * What a widget's own background looks like.
 *
 * ### Why this exists
 *
 * [BrassWidget] carried five independent booleans describing one thing - `flat`, `transparent`,
 * `chromeless`, `rounded`, plus `roundness` - with undocumented interactions between them. The
 * documentation for `chromeless` existed only to explain that `transparent` was not enough, and four
 * widgets ([net.swzo.brass.ui.kit.input.BrassSlider],
 * [net.swzo.brass.ui.kit.input.BrassToggle],
 * [net.swzo.brass.ui.kit.surface.BrassProgressBar],
 * [net.swzo.brass.ui.kit.surface.BrassLoading]) each opened with the identical incantation
 *
 * ```kotlin
 * chromeless = true
 * flat = true
 * ```
 *
 * under a near-identical three-line comment explaining why. Two labels used a third combination.
 * That is an enum wearing a boolean costume: five flags describe 32 states, of which four are
 * meaningful.
 */
enum class BrassChrome {

    /** The full raised keycap: fill, inner border, outer ring, and the bottom lip. The default. */
    KEYCAP,

    /** Fill and inner border only - no ring, no lip. A control that sits *in* the surface. */
    FLAT,

    /** A rounded rect rather than the sharp keycap. Radius comes from `BrassWidget.roundness`. */
    ROUNDED,

    /**
     * Nothing at all. For a widget that paints all of its own chrome (the sliders and bars, which
     * draw a card) or none of it (a label, which is only text).
     */
    NONE,
    ;

    /** Whether the base class should paint anything behind [drawContent][BrassWidget.drawContent]. */
    val paintsBackground: Boolean get() = this != NONE
}
