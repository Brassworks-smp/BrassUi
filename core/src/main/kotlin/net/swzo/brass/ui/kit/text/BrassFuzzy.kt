@file:Suppress("unused")
package net.swzo.brass.ui.kit.text



/**
 * Subsequence matching with a score - "does this candidate contain the query's letters in order, and
 * how well".
 * Used by [net.swzo.brass.ui.kit.surface.BrassCommandPalette] to rank commands and by
 * [net.swzo.brass.ui.kit.input.BrassSearchField] to filter a list. Both want the same thing a command
 * palette anywhere wants: `opw` should find "Open Preview Window", and should rank it above a
 * candidate where those letters happen to fall mid-word.
 * Deliberately pure - no components, no state. That makes it the part of the palette that can be
 * tested directly, which matters because ranking is easy to get subtly wrong and impossible to notice.
 */
object BrassFuzzy {

    data class Match(val score: Int, val positions: IntArray) {
        override fun equals(other: Any?): Boolean =
            other is Match && score == other.score && positions.contentEquals(other.positions)

        override fun hashCode(): Int = score * 31 + positions.contentHashCode()
    }

    fun match(query: String, candidate: String): Match? {
        if (query.isEmpty()) return Match(0, IntArray(0))
        if (candidate.isEmpty()) return null

        val q = query.lowercase()
        val c = candidate.lowercase()

        val positions = IntArray(q.length)
        var score = 0
        var at = 0
        var previous = -2

        for ((qi, ch) in q.withIndex()) {
            val found = c.indexOf(ch, at)
            if (found < 0) return null
            positions[qi] = found

            // start of a word - the letters someone actually types when abbreviating
            val startsWord = found == 0 || !c[found - 1].isLetterOrDigit()
            if (startsWord) score += WORD_START
            // a run: "prev" beats "p...r...e...v"
            if (found == previous + 1) score += CONSECUTIVE
            // how far we had to skip to find it
            score -= (found - at).coerceAtMost(MAX_GAP_PENALTY)

            previous = found
            at = found + 1
        }

        if (positions[0] == 0) score += LEADING
        // Between equal matches, prefer the shorter candidate - "Copy" over "Copy As Path".
        score -= candidate.length / LENGTH_DIVISOR

        return Match(score, positions)
    }

    fun matches(query: String, candidate: String): Boolean = match(query, candidate) != null

    fun <T> rank(query: String, items: List<T>, text: (T) -> String): List<T> {
        if (query.isEmpty()) return items
        return items
            .mapNotNull { item -> match(query, text(item))?.let { item to it.score } }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private const val WORD_START = 10

    private const val CONSECUTIVE = 12
    private const val LEADING = 12
    private const val MAX_GAP_PENALTY = 6
    private const val LENGTH_DIVISOR = 8
}
