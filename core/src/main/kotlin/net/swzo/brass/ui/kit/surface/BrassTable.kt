package net.swzo.brass.ui.kit.surface

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.layout.BrassVirtualList
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.text.BrassFont

/**
 * A scrollable, selectable table of [T] - player lists, log views, file browsers, anything row-shaped.
 *
 * ```kotlin
 * val table = BrassTable(listOf(
 *     BrassTable.Column("Player", 2f) { it.name },
 *     BrassTable.Column("Ping", 1f) { "${it.ping} ms" },
 * ), players) { player, _ -> open(player) }
 * ```
 *
 * ### What is left here
 *
 * Columns, the header, and sorting. Everything else - the viewport, the scroll offset, the scrollbar
 * and its drag, hover, selection, keyboard navigation - belongs to [BrassVirtualList] and is shared
 * with [BrassTreeView]. A table is a virtual list whose rows happen to be cells.
 *
 * It creates **no component per row**: rows are painted directly and only the ones inside the
 * viewport are touched, so a hundred thousand rows cost what twenty do and [setRows] is O(1).
 */
class BrassTable<T>(
    private val columns: List<Column<T>>,
    rows: List<T> = emptyList(),
    rowHeight: Float = 16f,
    onSelect: ((T, Int) -> Unit)? = null,
) : BrassVirtualList<T>(rowHeight, onSelect) {

    /**
     * One column: a heading, a flex [weight] for sharing width, and how to render a row's cell.
     *
     * [sortBy] makes the column sortable - click its heading to sort ascending, again for descending.
     * Leave it null for a column that cannot meaningfully be sorted.
     */
    class Column<T>(
        val title: String,
        val weight: Float = 1f,
        /** Sort key for a row, or null if this column is not sortable. */
        val sortBy: ((T) -> Comparable<*>)? = null,
        val cell: (T) -> String,
    )

    /** Index of the column the rows are sorted by, or -1. */
    var sortColumn: Int = -1
        private set

    /** Sort direction of [sortColumn]. */
    var sortDescending: Boolean = false
        private set

    /** The rows in the order they were given, before any sort - so a re-sort is never lossy. */
    private var sourceRows: List<T> = rows

    init {
        setItems(rows)
    }

    override val headerHeight: Float get() = HEADER_H

    /**
     * Sort by column [index], or toggle its direction if it is already the sort column. A column with
     * no `sortBy` is ignored, and an out-of-range index restores the original order.
     */
    @Suppress("UNCHECKED_CAST")
    fun sortBy(index: Int) {
        if (index !in columns.indices) {
            sortColumn = -1
            super.setItems(sourceRows)
            return
        }
        val key = columns[index].sortBy ?: return
        sortDescending = if (index == sortColumn) !sortDescending else false
        sortColumn = index
        val comparator = compareBy<T> { key(it) as Comparable<Any> }
        super.setItems(
            if (sortDescending) sourceRows.sortedWith(comparator.reversed()) else sourceRows.sortedWith(comparator),
        )
        selectedIndex = -1
    }

    /** Swap the backing data, re-applying any active sort. */
    fun setRows(next: List<T>) = setItems(next)

    override fun setItems(next: List<T>) {
        sourceRows = next
        super.setItems(next)
        // Re-apply the active sort, so replacing the data of a sorted table does not silently shuffle
        // it back into insertion order. `sortBy` toggles direction when handed its own column, hence
        // the flip first.
        if (sortColumn >= 0) {
            sortDescending = !sortDescending
            sortBy(sortColumn)
        }
    }

    override fun onHeaderClick(localX: Float) {
        val widths = columnWidths(getWidth())
        var edge = PAD
        for (i in columns.indices) {
            edge += widths[i]
            if (localX < edge) { sortBy(i); break }
        }
    }

    private var widthsFor = Float.NaN
    private var cachedWidths = FloatArray(0)

    /**
     * Resolved pixel width of each column for the current component width.
     *
     * Cached on the width: the weights never change, so re-summing them and allocating a fresh array
     * every frame bought nothing. Rebuilt only when the table is actually resized.
     */
    private fun columnWidths(width: Float): FloatArray {
        if (width == widthsFor) return cachedWidths
        val avail = (width - PAD * 2).coerceAtLeast(0f)
        val total = columns.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(0.0001f)
        widthsFor = width
        cachedWidths = FloatArray(columns.size) { avail * columns[it].weight / total }
        return cachedWidths
    }

    override fun paintRow(m: UMatrixStack, item: T, index: Int, x: Float, y: Float, w: Float) {
        val widths = columnWidths(w)
        val color = if (index == selectedIndex) Colors.UI_ACCENT_BRIGHT else Colors.UI_TEXT
        var tx = x + PAD
        for ((c, col) in columns.withIndex()) {
            val text = BrassFont.fit(this, col.cell(item), widths[c] - CELL_GAP)
            BrassFont.draw(m, this, text, tx, y + (rowHeight - BrassFont.LINE) / 2f, color)
            tx += widths[c]
        }
    }

    override fun paintHeader(m: UMatrixStack, x: Float, y: Float, w: Float) {
        // header card, sharing the body card's top edge, painted over the rows
        BrassCard.header(m, x, y, x + w, HEADER_H)
        val widths = columnWidths(w)
        var cx = x + PAD
        for ((i, col) in columns.withIndex()) {
            // An arrow marks the sort column, so the table says how it is ordered rather than leaving
            // the user to infer it.
            val marker = if (i == sortColumn) (if (sortDescending) " v" else " ^") else ""
            val label = BrassFont.fit(this, col.title.uppercase() + marker, widths[i] - CELL_GAP)
            val tint = if (i == sortColumn) Colors.UI_ACCENT else Colors.UI_TEXT_DARK
            BrassFont.draw(m, this, label, cx, y + (HEADER_H - BrassFont.LINE) / 2f, tint)
            cx += widths[i]
        }
    }

    companion object : BrassDemoSource {

        /**
         * A populated table, sorted by clicking its headings and with a row picked out.
         *
         * ### Why the columns are sortable
         *
         * A still of a table shows a grid of text, which any list widget could produce. What makes
         * this one a *table* is the header: click a heading and the rows reorder under it, click again
         * and the marker flips. That is the whole distinction, it is invisible in a still, and it is
         * the first thing anyone evaluating a table widget wants to know — so the demo's main scene is
         * two clicks on the same heading.
         *
         * The sample data is deliberately generic. A capture documents the widget, not whatever app it
         * happens to ship in, so the columns are Name / Value / Status and the rows are "Item one",
         * the way a component library's own docs would write them.
         */
        override fun demo() = BrassDemo("table", "Table", 290f, 130f) {
            BrassTable(
                listOf(
                    Column<Row>("Name", 2f, sortBy = { it.name }) { it.name },
                    Column<Row>("Value", 1f, sortBy = { it.value }) { it.value.toString() },
                    Column<Row>("Status", 1f, sortBy = { it.status }) { it.status },
                ),
                rows = ROWS,
            )
        }

        /** Vertical centre of the header strip, as a fraction of the table's box. */
        private fun headerFy(table: BrassTable<*>): Float =
            (HEADER_H / 2f) / table.getHeight().coerceAtLeast(1f)

        /**
         * Vertical centre of body row [index], as a fraction of the table's box.
         *
         * Uses [DEMO_ROW_H] rather than reading the table's own `rowHeight`, which is protected on
         * [BrassVirtualList] and so not visible from a companion. The demo builds its table with the
         * default row height, so the two agree by construction.
         */
        private fun rowFy(table: BrassTable<*>, index: Int): Float =
            (HEADER_H + DEMO_ROW_H * (index + 0.5f)) / table.getHeight().coerceAtLeast(1f)

        /** The row height the demo's table is built with — the constructor default. */
        private const val DEMO_ROW_H = 16f

        /** A row of the demo's generic sample data. */
        private data class Row(val name: String, val value: Int, val status: String)

        private val ROWS = listOf(
            Row("Item one", 128, "Active"),
            Row("Item two", 96, "Active"),
            Row("Item three", 54, "Idle"),
            Row("Item four", 12, "Idle"),
            Row("Item five", 71, "Active"),
            Row("Item six", 33, "Active"),
        )

        private const val HEADER_H = 16f
        private const val PAD = 6f
        private const val CELL_GAP = 8f
    }
}
