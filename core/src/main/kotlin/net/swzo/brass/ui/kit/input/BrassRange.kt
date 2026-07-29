package net.swzo.brass.ui.kit.input

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * A bounded numeric range with an optional step - what [BrassSlider], [BrassNumberInput] and
 * [BrassRangeSlider] all mean by "a value between min and max".
 *
 * ### Why this exists
 *
 * The slider carried `min`, `max`, `step`, a `snap` that rounded to the step, a `fraction` that mapped
 * value to 0..1, its inverse for a click position, and a `format` that picked a sensible number of
 * decimals from the step. Three more controls need exactly that arithmetic, and the decimal-picking in
 * particular is the sort of thing that quietly ends up subtly different in each copy - one showing
 * `0.30000001`, another `0`.
 *
 * Pure and immutable, so it is directly testable and can be shared between two handles of the same
 * slider without either being able to disturb the other.
 */
data class BrassRange(
    val min: Float = 0f,
    val max: Float = 1f,
    /** Quantisation. Zero means continuous. */
    val step: Float = 0f,
) {

    init {
        require(max > min) { "BrassRange needs max > min, got min=$min max=$max" }
    }

    val span: Float get() = max - min

    /** Clamp to the range and quantise to [step]. */
    fun snap(value: Float): Float {
        val clamped = value.coerceIn(min, max)
        if (step <= 0f) return clamped
        return (min + ((clamped - min) / step).roundToInt() * step).coerceIn(min, max)
    }

    /** Where [value] sits in the range, as 0..1. */
    fun fraction(value: Float): Float = ((value - min) / span).coerceIn(0f, 1f)

    /** The value at [fraction] of the range, snapped. */
    fun valueAt(fraction: Float): Float = snap(min + fraction.coerceIn(0f, 1f) * span)

    /** Move [value] by [steps] increments - one [step], or a hundredth of the span if continuous. */
    fun nudge(value: Float, steps: Int): Float =
        snap(value + steps * (if (step > 0f) step else span / 100f))

    /**
     * Decimal places worth showing, derived from [step].
     *
     * A step of 1 wants none, 0.5 wants one, 0.01 wants two. With no step, the *span* decides: a 0..1
     * range needs two decimals to be readable at all, a 0..1000 range needs none.
     */
    val decimals: Int get() {
        val basis = if (step > 0f) step else span / 100f
        if (basis <= 0f) return 0
        // The epsilon is load-bearing, and 1e-9 is too small for it. `0.01f` is 0.009999999776 in
        // binary32, whose log10 is -2.0000000097 - a hair below -2, so floor takes it to -3 and a step
        // of 0.01 asks for three decimals. The error is at float precision, so the tolerance has to be
        // too; 1e-6 absorbs it without reaching the next real decade.
        val places = -floor(log10(basis.toDouble()) + 1e-6).toInt()
        return places.coerceIn(0, 4)
    }

    /** [value] as text, at [decimals] precision, with a trailing `.0` trimmed to an integer. */
    fun format(value: Float): String {
        val d = decimals
        if (d == 0) return value.roundToInt().toString()
        val text = "%.${d}f".format(value)
        // 2.50 reads worse than 2.5, and 2.00 worse than 2 - but only trim when it is exact, so a
        // column of values does not jitter in width as it changes.
        return if (abs(value - value.roundToInt()) < 1e-4f) value.roundToInt().toString() else text
    }
}
