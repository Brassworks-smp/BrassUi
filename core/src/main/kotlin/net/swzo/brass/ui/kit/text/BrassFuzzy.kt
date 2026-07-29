package net.swzo.brass.ui.kit.text

import net.swzo.brass.ui.kit.text.BrassFuzzy.WORD_START


/**
 * Subsequence matching with a score - "does this candidate contain the query's letters in order, and
 * how well".
 *
 * Used by [net.swzo.brass.ui.kit.surface.BrassCommandPalette] to rank commands and by
 * [net.swzo.brass.ui.kit.input.BrassSearchField] to filter a list. Both want the same thing a command
 * palette anywhere wants: `opw` should find "Open Preview Window", and should rank it above a
 * candidate where those letters happen to fall mid-word.
 *
 * Deliberately pure - no components, no state. That makes it the part of the palette that can be
 * tested directly, which matters because ranking is easy to get subtly wrong and impossible to notice.
 */
object BrassFuzzy {

    /** A match: where the query's characters landed, and how good the match was. */
    data class Match(val score: Int, val positions: IntArray) {
        override fun equals(other: Any?): Boolean =
            other is Match && score == other.score && positions.contentEquals(other.positions)

        override fun hashCode(): Int = score * 31 + positions.contentHashCode()
    }

    /**
     * Match [query] against [candidate], case-insensitively. Returns null when the query's characters
     * do not all appear, in order.
     *
     * An empty query matches everything with a score of zero, so a palette with nothing typed shows
     * its commands in their given order rather than empty.
     *
     * ### Scoring
     *
     * Points are awarded for the things that make a match feel intentional rather than coincidental:
     * a character at the start of a word, a run of consecutive characters, and a match that starts at
     * the beginning of the candidate. A gap costs a little, and a long candidate is penalised slightly
     * so that between two otherwise equal matches the tighter name wins.
     */
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

    /** Whether [query] matches [candidate] at all, when the score is not needed. */
    fun matches(query: String, candidate: String): Boolean = match(query, candidate) != null

    /**
     * Filter and rank [items] by [query] against the text from [text].
     *
     * Ties keep their original order - [sortedByDescending] is stable - so a palette with an empty
     * query, where every score is zero, shows its commands exactly as they were given.
     */
    fun <T> rank(query: String, items: List<T>, text: (T) -> String): List<T> {
        if (query.isEmpty()) return items
        return items
            .mapNotNull { item -> match(query, text(item))?.let { item to it.score } }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private const val WORD_START = 10

    /**
     * Deliberately **above** [WORD_START].
     *
     * With it below, a candidate whose every letter happened to start a word outscored a tight
     * prefix: "p r e v" beat "preview" for the query `prev`, because four word-start bonuses beat
     * three consecutive ones. A run of characters is the strongest evidence that a match is the one
     * the user meant, so it has to dominate.
     */
    private const val CONSECUTIVE = 12
    private const val LEADING = 12
    /** A gap costs at most this, so one long skip does not sink an otherwise good match. */
    private const val MAX_GAP_PENALTY = 6
    private const val LENGTH_DIVISOR = 8
}
