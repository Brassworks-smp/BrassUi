package net.swzo.brass.ui.kit.base

/**
 * What a widget's own background looks like.
 * ### Why this exists
 * [BrassWidget] carried five independent booleans describing one thing - `flat`, `transparent`,
 * `chromeless`, `rounded`, plus `roundness` - with undocumented interactions between them. The
 * documentation for `chromeless` existed only to explain that `transparent` was not enough, and four
 * widgets ([net.swzo.brass.ui.kit.input.BrassSlider],
 * [net.swzo.brass.ui.kit.input.BrassToggle],
 * [net.swzo.brass.ui.kit.surface.BrassProgressBar],
 * [net.swzo.brass.ui.kit.surface.BrassLoading]) each opened with the identical incantation
 * ```kotlin
 * chromeless = true
 * flat = true
 * ```
 * under a near-identical three-line comment explaining why. Two labels used a third combination.
 * That is an enum wearing a boolean costume: five flags describe 32 states, of which four are
 * meaningful.
 */
enum class BrassChrome {

    KEYCAP,

    FLAT,

    ROUNDED,

    NONE,
    ;

    val paintsBackground: Boolean get() = this != NONE
}
