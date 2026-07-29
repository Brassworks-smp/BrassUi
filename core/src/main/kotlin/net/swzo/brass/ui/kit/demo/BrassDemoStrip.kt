package net.swzo.brass.ui.kit.demo

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.text.BrassLabel

/**
 * One widget's demo, live, with its title under it.
 *
 * ### Why the gallery shows the same declarations the browser captures
 *
 * Because otherwise the two drift, and they drift silently. The gallery used to build its own showcase
 * of every widget by hand, and the capture path had a second hand-written catalogue of the same forty
 * widgets; between them they disagreed about sizes and sample data, and neither was a trustworthy
 * answer to "what does this widget look like". Sharing one declaration makes the gallery a *preview of
 * the documentation* rather than a second opinion about it.
 *
 * The demos here are as interactive as they are anywhere else — they are the real widgets — so this
 * section doubles as a place to actually try each one.
 */
class BrassDemoTile(private val demo: BrassDemo) : UIContainer() {

    init {
        val content = demo.build()
        val root: UIComponent = if (demo.card) BrassDemoCard(content, demo.fitCard) else content
        root.constrain {
            x = 0.pixels(); y = 0.pixels()
            width = demo.outerWidth.pixels()
            height = demo.outerHeight.pixels()
        } childOf this

        BrassLabel(demo.title).constrain {
            x = 0.pixels()
            y = SiblingConstraint(4f)
        } childOf this
    }

    /** Total height this tile wants — the demo plus its caption. */
    fun contentHeight(): Float = demo.outerHeight + CAPTION

    private companion object {
        /** Room under the demo for its caption. */
        const val CAPTION = 16f
    }
}

/**
 * Every declared demo, stacked.
 *
 * The gallery's "Demos" section, and the closest thing the toolkit has to a single answer to "show me
 * everything". Sized by [contentHeight] so it can go straight into a `ScrollComponent`.
 */
class BrassDemoStrip(demos: List<BrassDemo> = BrassDemos.ALL) : UIContainer() {

    private val tiles = ArrayList<BrassDemoTile>()

    init {
        demos.forEachIndexed { i, demo ->
            val tile = BrassDemoTile(demo)
            tile.constrain {
                x = 0.pixels()
                y = if (i == 0) 0.pixels() else SiblingConstraint(GAP)
                width = demo.outerWidth.pixels()
                height = basicHeightConstraint { tile.contentHeight() }
            } childOf this
            tiles.add(tile)
        }
    }

    /** Total height of the stack, for a `basicHeightConstraint` on whatever holds it. */
    fun contentHeight(): Float =
        tiles.sumOf { it.contentHeight().toDouble() }.toFloat() + GAP * (tiles.size - 1).coerceAtLeast(0)

    private companion object {
        /** Vertical gap between tiles. Generous: each demo is a separate object of attention. */
        const val GAP = 18f
    }
}
