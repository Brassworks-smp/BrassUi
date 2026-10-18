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
import net.swzo.brass.ui.kit.layout.BrassDivider
import net.swzo.brass.ui.kit.layout.BrassScrollArea

/**
 * A vertical list of sections, one selected. The last [pinnedBottom] items sit at the bottom of the rail
 * (Settings, Log out…), the rest from the top. The first [pinnedTop] items are the rail's headline
 * sections (Overview, Home…): keycaps instead of flat rows, set apart from the rest by a separator.
 * Rows run to the rail's right edge: the scrollbar draws over them in the rare rail that scrolls.
 */
class BrassNavRail @JvmOverloads constructor(
    private val items: List<String>,
    initialIndex: Int = 0,
    private val rowHeight: Float = ROW_H,
    private val onChange: (Int) -> Unit = {},
    private val pinnedBottom: Int = 0,
    private val pinnedTop: Int = 0,
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
        val heads = pinnedTop.coerceIn(0, top)
        items.forEachIndexed { i, label ->
            val head = i < heads
            val row = BrassButton(label, BrassAccent.DEFAULT) { value = i }.apply {
                centered = false
                chrome = if (head) BrassChrome.KEYCAP else BrassChrome.FLAT
                selectable = true
            }
            if (i == heads && heads > 0) {
                // The separator: clear of the keycap's lip above, the same gap again below.
                BrassDivider().apply { axis = BrassDivider.Axis.HORIZONTAL }.constrain {
                    x = 0.pixels()
                    y = SiblingConstraint(BrassWidget.BLEED_BOTTOM + SEPARATOR_GAP)
                    width = 100.percent()
                    height = 2.pixels()
                } childOf content
            }
            row.constrain {
                // A keycap's bevel and lip draw outside its box: inset so they stay in the rail.
                x = (if (head) BrassWidget.BLEED_X else 0f).pixels()
                y = when {
                    i == 0 -> (if (head) BrassWidget.BLEED_TOP else 0f).pixels()
                    i == heads && heads > 0 -> SiblingConstraint(SEPARATOR_GAP)
                    head -> SiblingConstraint(BrassWidget.BLEED_BOTTOM + GAP)
                    i < top -> SiblingConstraint(GAP)
                    // Pinned: from the bottom of what's visible, never over the rows above.
                    else -> basicYConstraint {
                        val fromBottom = (items.size - i) * (rowHeight + GAP) - GAP + BrassWidget.BLEED_BOTTOM
                        val above = rows.getOrNull(i - 1)?.let { it.getBottom() + GAP } ?: content.getTop()
                        maxOf(above, body.getTop() + body.getHeight() - fromBottom)
                    }
                }
                width = if (head) 100.percent() - (BrassWidget.BLEED_X * 2).pixels() else 100.percent()
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
        /** Between the headline keycaps' lip and the separator, and the separator and the rows below. */
        const val SEPARATOR_GAP = 4f

        override fun demo() = BrassDemo("nav-rail", "Nav rail", 110f, 110f) {
            BrassNavRail(listOf("Overview", "General", "Video", "Audio", "Controls"), pinnedTop = 1)
        }
    }
}
