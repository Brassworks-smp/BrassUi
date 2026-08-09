package net.swzo.brass.ui.kit.layout

import net.swzo.brass.ui.kit.layout.BrassPageWindow.GAP


/**
 * Which page numbers a pager shows: `1 … 7 8 [9] 10 11 … 40`.
 * ### Why this is separate from the widget
 * It is the only part of pagination with any logic in it, and it is entirely arithmetic - so it can
 * be tested directly, which the drawing cannot. It is also the part that is easy to get *nearly*
 * right: the window has to stay a constant width as the current page moves (otherwise the buttons
 * shuffle under the cursor), it must not show an ellipsis standing in for a single hidden page (an
 * ellipsis that hides one number is wider than the number), and it must clamp at both ends rather
 * than running off.
 */
object BrassPageWindow {

    const val GAP = -1

    /**
     * Page numbers to show for [current] of [total], with [around] pages either side of the current
     * one, plus the first and last. Pages are **1-based**; [GAP] marks an elision.
     * The result is a **constant length** whenever the total is large enough to need eliding, so the
     * buttons keep their positions as the user pages through instead of sliding sideways - which is
     * the difference between a pager you can click twice in a row and one you have to re-aim at.
     * An ellipsis never stands in for a single page: hiding one number takes more room than showing
     * it, so near an end the run of numbers lengthens instead.
     */
    fun pages(current: Int, total: Int, around: Int = 1): List<Int> {
        if (total <= 0) return emptyList()
        val page = current.coerceIn(1, total)

        // The window at full width: first, an ellipsis, `around` either side of the current page,
        // another ellipsis, last.
        val budget = around * 2 + 5
        if (total <= budget) return (1..total).toList()

        // Near an end there is only one ellipsis, so the run of numbers grows by one to keep the row
        // the same width - otherwise the buttons slide sideways as you page toward the edge.
        val nearEnd = budget - 3

        val from: Int
        val to: Int
        when {
            // Close enough to the start that a leading ellipsis would hide one page or none.
            page - around <= 3 -> { from = 2; to = 1 + nearEnd }
            page + around >= total - 2 -> { to = total - 1; from = total - nearEnd }
            else -> { from = page - around; to = page + around }
        }

        return buildList {
            add(1)
            if (from > 2) add(GAP)
            for (p in from..to) add(p)
            if (to < total - 1) add(GAP)
            add(total)
        }
    }

    /** Total pages needed for [items] at [perPage], never below one. */
    fun pageCount(items: Int, perPage: Int): Int {
        if (perPage <= 0) return 1
        return ((items + perPage - 1) / perPage).coerceAtLeast(1)
    }

    fun range(page: Int, perPage: Int, items: Int): IntRange {
        if (perPage <= 0 || items <= 0) return IntRange.EMPTY
        val start = ((page - 1).coerceAtLeast(0)) * perPage
        if (start >= items) return IntRange.EMPTY
        return start until minOf(start + perPage, items)
    }
}
