package net.swzo.brass.ui.kit.text

/**
 * "How long ago", in words - the arithmetic behind [BrassTimeAgo].
 *
 * ### Why it is not on the widget
 *
 * Same reason [net.swzo.brass.ui.kit.layout.BrassPageWindow] is not on `BrassPagination`: this is the
 * only part with any logic, and it is the only part that can be tested. Elementa is `compileOnly` in
 * this module, so a widget's companion object cannot even be *loaded* in a unit test - the class
 * extends `BrassWidget` and the JVM resolves that supertype before it will hand over a static.
 * Putting the rules in a plain object is what makes them checkable at all.
 */
object BrassRelativeTime {

    const val SECOND = 1000L
    const val MINUTE = 60 * SECOND
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR
    const val WEEK = 7 * DAY
    const val YEAR = 365 * DAY

    /** Below this, everything is "just now" rather than a countdown of seconds. */
    const val JUST_NOW = 45 * SECOND

    /**
     * How long ago [elapsed] millis is, in words.
     *
     * Deliberately coarse: one unit, never "1h 12m". A relative time is a glance, and the moment it
     * needs two units the absolute timestamp is the better answer - which is what [BrassTimeAgo]'s
     * tooltip is for.
     *
     * A **negative** elapsed - a timestamp in the future, which clock skew between a client and its
     * server produces routinely - reads as "just now" rather than as a nonsensical negative age.
     */
    fun format(elapsed: Long, suffix: Boolean = true): String {
        if (elapsed < JUST_NOW) return "just now"
        val (value, unit) = when {
            elapsed < HOUR -> elapsed / MINUTE to "m"
            elapsed < DAY -> elapsed / HOUR to "h"
            elapsed < WEEK -> elapsed / DAY to "d"
            elapsed < YEAR -> elapsed / WEEK to "w"
            else -> elapsed / YEAR to "y"
        }
        return if (suffix) "$value$unit ago" else "$value$unit"
    }

    /**
     * The bucket [elapsed] falls in, so a caller can rebuild the string only when the displayed value
     * would actually change - once a minute for the first hour, once an hour after that, and so on.
     *
     * The offsets keep buckets from different units apart, so "59 minutes" and "1 hour" never collide
     * on the same number and leave the text stale across a unit boundary.
     */
    fun bucketOf(elapsed: Long): Long = when {
        elapsed < JUST_NOW -> 0L
        elapsed < HOUR -> elapsed / MINUTE
        elapsed < DAY -> HOUR + elapsed / HOUR
        elapsed < WEEK -> DAY + elapsed / DAY
        elapsed < YEAR -> WEEK + elapsed / WEEK
        else -> YEAR + elapsed / YEAR
    }
}
