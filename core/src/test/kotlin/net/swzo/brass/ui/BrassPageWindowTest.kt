package net.swzo.brass.ui

import net.swzo.brass.ui.kit.layout.BrassPageWindow
import net.swzo.brass.ui.kit.layout.BrassPageWindow.GAP
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The page window is the only part of pagination with any logic, and its failure mode is buttons
 * shuffling sideways under the cursor as you page — which nobody notices until they misclick.
 */
class BrassPageWindowTest {

    @Test
    fun `a short run shows every page`() {
        assertEquals(listOf(1, 2, 3, 4, 5), BrassPageWindow.pages(current = 3, total = 5))
    }

    @Test
    fun `the current page is always present`() {
        for (page in 1..40) {
            assertTrue(
                BrassPageWindow.pages(page, total = 40).contains(page),
                "page $page missing from its own window",
            )
        }
    }

    @Test
    fun `first and last are always present`() {
        val window = BrassPageWindow.pages(current = 20, total = 40)
        assertEquals(1, window.first())
        assertEquals(40, window.last())
    }

    @Test
    fun `the window keeps a constant width as the page moves`() {
        // The whole point: if this varies, the buttons slide sideways while you are clicking them.
        val widths = (1..40).map { BrassPageWindow.pages(it, total = 40).size }.toSet()
        assertEquals(1, widths.size, "window widths varied: $widths")
    }

    @Test
    fun `elides on both sides in the middle`() {
        val window = BrassPageWindow.pages(current = 20, total = 40)
        assertEquals(listOf(1, GAP, 19, 20, 21, GAP, 40), window)
    }

    @Test
    fun `no ellipsis stands in for a single page`() {
        // An ellipsis hiding one number takes more room than the number, so page 2 must be shown.
        for (page in 1..40) {
            val window = BrassPageWindow.pages(page, total = 40)
            for (i in 0 until window.size - 1) {
                if (window[i] == GAP) continue
                if (window[i + 1] == GAP && i + 2 < window.size) {
                    val skipped = window[i + 2] - window[i] - 1
                    assertTrue(skipped > 1, "gap hid only $skipped page(s) at page $page: $window")
                }
            }
        }
    }

    @Test
    fun `out-of-range pages are clamped rather than throwing`() {
        assertTrue(BrassPageWindow.pages(current = 0, total = 10).isNotEmpty())
        assertTrue(BrassPageWindow.pages(current = 99, total = 10).isNotEmpty())
        assertEquals(emptyList<Int>(), BrassPageWindow.pages(current = 1, total = 0))
    }

    @Test
    fun `pageCount rounds up and never returns zero`() {
        assertEquals(4, BrassPageWindow.pageCount(items = 31, perPage = 10))
        assertEquals(3, BrassPageWindow.pageCount(items = 30, perPage = 10))
        assertEquals(1, BrassPageWindow.pageCount(items = 0, perPage = 10))
    }

    @Test
    fun `range gives the slice for a page and clamps the last one`() {
        assertEquals(0 until 10, BrassPageWindow.range(page = 1, perPage = 10, items = 31))
        assertEquals(30 until 31, BrassPageWindow.range(page = 4, perPage = 10, items = 31))
        assertEquals(IntRange.EMPTY, BrassPageWindow.range(page = 9, perPage = 10, items = 31))
    }
}
