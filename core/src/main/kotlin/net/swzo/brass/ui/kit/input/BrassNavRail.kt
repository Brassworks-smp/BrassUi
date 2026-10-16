package net.swzo.brass.ui.kit.input

import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.basicYConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassState
import net.swzo.brass.ui.kit.base.BrassValue
import net.swzo.brass.ui.kit.base.BrassValueHolder
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.layout.BrassScrollArea

/**
 * A vertical list of sections, one selected. The last [pinnedBottom] items sit at the bottom of the rail
 * (Settings, Log out…), the rest from the top. Rows run to the rail's right edge: the scrollbar draws
 * over them in the rare rail that scrolls.
 */
class BrassNavRail @JvmOverloads constructor(
    private val items: List<String>,
    initialIndex: Int = 0,
    private val rowHeight: Float = ROW_H,
    private val onChange: (Int) -> Unit = {},
    private val pinnedBottom: Int = 0,
) : BrassScrollArea(reserveBar = false), BrassValue<Int> {

    private val rows = ArrayList<BrassButton>()

    private val holder = BrassValueHolder(initialIndex) { applySelection(it) }

    override var value: Int
        get() = holder.value
        set(v) { if (v in items.indices) holder.value = v }

    override fun setSilently(value: Int) { if (value in items.indices) holder.setSilently(value) }
    override fun onChange(listener: (Int) -> Unit) = holder.onChange(listener)
    override fun bind(state: BrassState<Int>) = holder.bind(this, state)

    init {
        val top = items.size - pinnedBottom.coerceIn(0, items.size)
        items.forEachIndexed { i, label ->
            val row = BrassButton(label, BrassAccent.DEFAULT) { value = i }.apply {
                centered = false
                chrome = BrassChrome.FLAT
                selectable = true
            }
            row.constrain {
                x = 0.pixels()
                y = when {
                    i == 0 -> 0.pixels()
                    i < top -> SiblingConstraint(GAP)
                    // Pinned: from the bottom of what's visible, never over the rows above.
                    else -> basicYConstraint {
                        val fromBottom = (items.size - i) * (rowHeight + GAP) - GAP + BrassWidget.BLEED_BOTTOM
                        val above = rows.getOrNull(i - 1)?.let { it.getBottom() + GAP } ?: content.getTop()
                        maxOf(above, body.getTop() + body.getHeight() - fromBottom)
                    }
                }
                width = 100.percent()
                height = rowHeight.pixels()
            } childOf content
            rows.add(row)
        }
        applySelection(initialIndex)
        holder.onChange(onChange)
    }

    private fun applySelection(index: Int) {
        rows.forEachIndexed { i, row ->
            row.selected = i == index
            row.accent = if (i == index) BrassAccent.BRASS else BrassAccent.DEFAULT
        }
    }

    companion object : BrassDemoSource {
        const val ROW_H = 18f
        const val GAP = 3f

        override fun demo() = BrassDemo("nav-rail", "Nav rail", 110f, 90f) {
            BrassNavRail(listOf("General", "Video", "Audio", "Controls"))
        }
    }
}
