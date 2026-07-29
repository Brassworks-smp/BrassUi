package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.*
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.input.BrassButton

/**
 * A **weighted grid**: children flow left to right into columns whose widths are shares of the
 * container, wrapping to a new row every [columns] cells.
 *
 * ### Why this exists
 *
 * [BrassVBox], [BrassHBox] and [BrassFlow] cover stacks and wrapping rows. Anything genuinely
 * two-dimensional fell back to hand-written `basicXConstraint`/`basicYConstraint` chains - which is
 * precisely the brittleness [BrassVBox]'s own documentation argues against, since every cell names
 * its neighbours and inserting one silently breaks the rest. Both showcase screens are full of them,
 * and `component/Row` is a hardcoded 50/50 split with a comment explaining why `alignOpposite` did
 * not work.
 *
 * The weight model is [net.swzo.brass.ui.kit.surface.BrassTable.Column]'s, so a grid and a table
 * describe their columns the same way.
 *
 * ```kotlin
 * BrassGrid(weights = listOf(2f, 1f, 1f), rowHeight = 20f)
 *     .add(nameField, portField, saveButton)
 *     .add(pathField, sizeField, browseButton)
 * ```
 */
class BrassGrid(
    /** Relative width of each column. Its size is the number of columns. */
    private val weights: List<Float> = listOf(1f, 1f),
    /** Height of every row. */
    private val rowHeight: Float = 20f,
    /** Gap between columns and between rows. */
    private val gapX: Float = 6f,
    private val gapY: Float = 6f,
    /** Reserve the keycap bleed around cells. Turn off for plain, non-widget content. */
    private val bleed: Boolean = true,
) : UIContainer() {

    private val cells = ArrayList<UIComponent>()

    /** Number of columns. */
    val columns: Int get() = weights.size.coerceAtLeast(1)

    /** Number of rows the current children occupy. */
    val rows: Int get() = if (cells.isEmpty()) 0 else (cells.size + columns - 1) / columns

    private val padX get() = if (bleed) BrassWidget.BLEED_X else 0f
    private val padTop get() = if (bleed) BrassWidget.BLEED_TOP else 0f
    private val padBottom get() = if (bleed) BrassWidget.BLEED_BOTTOM else 0f

    init {
        constrain { height = basicHeightConstraint { contentHeight() } }
    }

    /**
     * Append [children] to the grid in reading order. Returns this grid so it can be built and
     * parented in one expression.
     *
     * A cell's position and size come from its **index**, so inserting or removing one reflows
     * everything after it - there are no sibling references to keep in sync.
     */
    fun add(vararg children: UIComponent): BrassGrid {
        for (child in children) {
            val index = cells.size
            cells.add(child)
            child.constrain {
                x = basicXConstraint { columnLeft(index % columns) }
                y = basicYConstraint { c -> c.parent.getTop() + padTop + (index / columns) * (rowHeight + gapY) }
                width = basicWidthConstraint { columnWidth(index % columns) }
                height = basicHeightConstraint { rowHeight }
            }
            child childOf this
        }
        return this
    }

    /** Leave a cell empty - for a grid whose last row is short, or a deliberate gap. */
    fun skip(count: Int = 1): BrassGrid = add(*Array(count) { UIContainer() })

    /** Usable width, less the bleed on both sides and the gaps between columns. */
    private fun usable(): Float =
        (getWidth() - padX * 2 - gapX * (columns - 1)).coerceAtLeast(1f)

    private fun columnWidth(index: Int): Float {
        val total = weights.sumOf { it.toDouble() }.toFloat().coerceAtLeast(0.0001f)
        return usable() * (weights.getOrNull(index) ?: 1f) / total
    }

    private fun columnLeft(index: Int): Float {
        var x = getLeft() + padX
        for (i in 0 until index) x += columnWidth(i) + gapX
        return x
    }

    /** Total height the current children need. Feed it to the container's own height constraint. */
    fun contentHeight(): Float {
        val n = rows
        if (n == 0) return padTop + padBottom
        return padTop + n * rowHeight + (n - 1) * gapY + padBottom
    }

    companion object : BrassDemoSource {

        /** A three-column grid: cells take equal-weight columns and wrap to rows in reading order. */
        override fun demo() = BrassDemo("grid", "Grid", 156f, 66f) {
            BrassGrid(weights = listOf(1f, 1f, 1f), rowHeight = 18f).add(
                BrassButton("One", BrassAccent.DEFAULT),
                BrassButton("Two", BrassAccent.DEFAULT),
                BrassButton("Three", BrassAccent.DEFAULT),
                BrassButton("Four", BrassAccent.DEFAULT),
                BrassButton("Five", BrassAccent.DEFAULT),
                BrassButton("Six", BrassAccent.DEFAULT),
            )
        }
    }
}
