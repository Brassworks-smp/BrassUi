@file:Suppress("unused")
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
 * ### Why this exists
 * [BrassVBox], [BrassHBox] and [BrassFlow] cover stacks and wrapping rows. Anything genuinely
 * two-dimensional fell back to hand-written `basicXConstraint`/`basicYConstraint` chains - which is
 * precisely the brittleness [BrassVBox]'s own documentation argues against, since every cell names
 * its neighbours and inserting one silently breaks the rest. Both showcase screens are full of them,
 * and `component/Row` is a hardcoded 50/50 split with a comment explaining why `alignOpposite` did
 * not work.
 * The weight model is [net.swzo.brass.ui.kit.surface.BrassTable.Column]'s, so a grid and a table
 * describe their columns the same way.
 * ```kotlin
 * BrassGrid(weights = listOf(2f, 1f, 1f), rowHeight = 20f)
 *     .add(nameField, portField, saveButton)
 *     .add(pathField, sizeField, browseButton)
 * ```
 */
class BrassGrid(
    private val weights: List<Float> = listOf(1f, 1f),
    private val rowHeight: Float = 20f,
    private val gapX: Float = 6f,
    private val gapY: Float = 6f,
    private val bleed: Boolean = true,
) : UIContainer() {

    private val cells = ArrayList<UIComponent>()

    val columns: Int get() = weights.size.coerceAtLeast(1)

    val rows: Int get() = if (cells.isEmpty()) 0 else (cells.size + columns - 1) / columns

    private val padX get() = if (bleed) BrassWidget.BLEED_X else 0f
    private val padTop get() = if (bleed) BrassWidget.BLEED_TOP else 0f
    private val padBottom get() = if (bleed) BrassWidget.BLEED_BOTTOM else 0f

    init {
        constrain { height = basicHeightConstraint { contentHeight() } }
    }

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

    fun skip(count: Int = 1): BrassGrid = add(*Array(count) { UIContainer() })

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

    fun contentHeight(): Float {
        val n = rows
        if (n == 0) return padTop + padBottom
        return padTop + n * rowHeight + (n - 1) * gapY + padBottom
    }

    companion object : BrassDemoSource {

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
