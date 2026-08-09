package net.swzo.brass.ui.kit.input

import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.constrain
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.layout.BrassCull
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassTagStyle
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A multi-select shown as removable chips - selected filters, applied tags, chosen recipients.
 * ```kotlin
 * val chips = BrassChips(onRemove = { filters.remove(it) })
 * chips.set(listOf("survival", "hardcore", "1.21"))
 * ```
 * ### Why not a row of `BrassTag`s
 * [net.swzo.brass.ui.kit.text.BrassTag] is a *label*: it says something about the thing next to it and
 * has no notion of being removed. A chip is a control - it has a hit area, a hover state and an X that
 * has to be aimed at - and a set of them reflows as items are added and removed. Building that from
 * tag components would mean adding and removing components from the tree every time the selection
 * changes, in the middle of Elementa's own iteration over them.
 * So the chips are painted, and the widget owns its own flow: it wraps into as many rows as it needs
 * and reports [contentHeight] so a `basicHeightConstraint` can grow the container to match. The
 * colours come from [BrassTagStyle], so a chip and a tag saying the same thing are the same green.
 */
class BrassChips(
    var maxChipWidth: Float = 120f,
    var onRemove: ((String) -> Unit)? = null,
    var onClick: ((String) -> Unit)? = null,
) : BrassWidget(BrassAccent.DEFAULT) {

    data class Chip(val label: String, val style: BrassTagStyle = BrassTagStyle.NEUTRAL)

    private var chips: List<Chip> = emptyList()

    private var hovered = -1

    private var onClose = false

    fun set(next: List<Chip>) { chips = next }

    fun setLabels(next: List<String>) { chips = next.map { Chip(it) } }

    val size: Int get() = chips.size

    val labels: List<String> get() = chips.map { it.label }

    init {
        // A BrassWidget, not a UIContainer. The base class is what runs the entrance animation, the
        // hover/press colour easing, the cursor request, the focus ring and BrassDevMode.inspect - a
        // raw Elementa container painting itself gets none of that and is invisible to the inspector.
        // chrome = NONE because this widget paints all of its own background.
        chrome = BrassChrome.NONE
        constrain { height = basicHeightConstraint { contentHeight() } }
        onMouseClick { e ->
            if (e.mouseButton != 0) return@onMouseClick
            val index = chipAt(e.relativeX, e.relativeY) ?: return@onMouseClick
            val chip = chips[index]
            if (onCloseAt(index, e.relativeX)) onRemove?.invoke(chip.label) else onClick?.invoke(chip.label)
        }
    }


    private fun layout(): List<FloatArray> {
        val out = ArrayList<FloatArray>(chips.size)
        val avail = getWidth().coerceAtLeast(1f)
        var x = 0f
        var y = 0f
        for (chip in chips) {
            val w = chipWidth(chip)
            if (x > 0f && x + w > avail) { x = 0f; y += HEIGHT + GAP }
            out.add(floatArrayOf(x, y, w, HEIGHT))
            x += w + GAP
        }
        return out
    }

    private fun chipWidth(chip: Chip): Float {
        val text = BrassFont.width(this, chip.label)
        val closer = if (onRemove != null) CLOSE + GAP else 0f
        return (PAD * 2 + text + closer).coerceAtMost(maxChipWidth)
    }

    fun contentHeight(): Float {
        val boxes = layout()
        if (boxes.isEmpty()) return 0f
        return boxes.maxOf { it[1] + it[3] }
    }

    private fun chipAt(localX: Float, localY: Float): Int? {
        layout().forEachIndexed { index, box ->
            if (localX >= box[0] && localX <= box[0] + box[2] &&
                localY >= box[1] && localY <= box[1] + box[3]
            ) return index
        }
        return null
    }

    private fun onCloseAt(index: Int, localX: Float): Boolean {
        if (onRemove == null) return false
        val box = layout().getOrNull(index) ?: return false
        return localX >= box[0] + box[2] - CLOSE - PAD
    }


    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun drawContent(matrixStack: UMatrixStack, bx: Int, by: Int, bw: Int, bh: Int) {

        val (mx, my) = getMousePosition()
        val visible = BrassCull.visible(this)
        val localX = mx - getLeft()
        val localY = my - getTop()
        hovered = if (visible) chipAt(localX, localY) ?: -1 else -1
        onClose = hovered >= 0 && onCloseAt(hovered, localX)

        layout().forEachIndexed { index, box ->
            paintChip(matrixStack, chips[index], box, index == hovered)
        }

    }

    private fun paintChip(m: UMatrixStack, chip: Chip, box: FloatArray, hot: Boolean) {
        val x1 = getLeft() + box[0]
        val y1 = getTop() + box[1]
        val x2 = x1 + box[2]
        val y2 = y1 + box[3]

        BrassCard.flat(m, x1, y1, x2, y2, fill = if (hot) Colors.UI_ELEMENT_BG_HOVER else Colors.UI_ELEMENT_BG)

        val closer = if (onRemove != null) CLOSE + GAP else 0f
        val text = BrassFont.fit(this, chip.label, box[2] - PAD * 2 - closer)
        BrassFont.draw(
            m, this, text,
            x1 + PAD, y1 + (box[3] - BrassFont.LINE) / 2f,
            // The style tints the *label*. It used to paint a bar down the left edge, which read as a
            // stray dash rather than as a category marker - and a chip is small enough that colouring
            // the word says the same thing without adding a second element to look at.
            if (hot) Colors.UI_TEXT_HOVER else chip.style.color,
        )

        if (onRemove != null) {
            // A small x drawn as two strokes. BrassIcons.CLOSE is a window control and reads far too
            // heavy at seven pixels beside a word.
            val cx = x2 - PAD - CLOSE / 2f
            val cy = (y1 + y2) / 2f
            val r = CLOSE / 2f - 1f
            val tint = if (hot && onClose) Colors.DANGER else Colors.UI_TEXT_DARK
            for (i in 0 until (r * 2).toInt()) {
                BrassPaint.rect(m, cx - r + i, cy - r + i, cx - r + i + 1f, cy - r + i + 1f, tint)
                BrassPaint.rect(m, cx + r - i - 1f, cy - r + i, cx + r - i, cy - r + i + 1f, tint)
            }
        }
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("chips", "Chips", 210f, 20f) {
            val chips = BrassChips()
            val items = mutableListOf(
                Chip("survival", BrassTagStyle.SUCCESS),
                Chip("hardcore", BrassTagStyle.ERROR),
                Chip("modded", BrassTagStyle.INFO),
            )
            chips.set(items)
            chips.onRemove = { label ->
                items.removeAll { it.label == label }
                chips.set(items)
            }
            chips
        }

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val HEIGHT = 13f
        private const val GAP = 3f
        private const val PAD = 4f
        private const val CLOSE = 7f
    }
}
