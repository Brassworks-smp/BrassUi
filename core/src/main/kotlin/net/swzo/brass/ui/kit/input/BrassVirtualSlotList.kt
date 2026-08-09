@file:Suppress("unused")
package net.swzo.brass.ui.kit.input

import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.layout.BrassScrollbar
import net.swzo.brass.ui.kit.layout.BrassScrollbarModel
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.platform.BrassPlatform
import net.swzo.brass.ui.kit.text.BrassFont
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A virtualized, scrollable one-column list of item slots - a catalogue or search-result list where
 * the rows are **real** [BrassInventoryGrid] slots.
 * The whole drag vocabulary works exactly as it does in a grid: a row can be picked up, dragged into
 * any linked grid, shift-clicked, spread over by a drag, and hovered for its item tooltip or a
 * [net.swzo.brass.ui.kit.input.BrassInventoryGrid.highlight] - because the list *is* a grid (one
 * column, arbitrarily many rows) that happens to paint only the rows in its viewport. That is what
 * keeps a catalogue of ten thousand entries cheap: a row that is not on screen is never painted,
 * hit-tested or tooltipped, while the rows that are on screen behave like normal inventory slots.
 * ### Why "portal" rows matter
 * A painted-only list (see [net.swzo.brass.ui.kit.layout.BrassVirtualList]) cannot participate in
 * the shared cursor: an item dragged from it has nowhere to live, and the carried stack renders
 * *under* whatever card surrounds the list. Here the list joins the same
 * [BrassInventoryLink] the slots around it use, so the carried stack is painted above the card and
 * drops anywhere - a frequency slot, another grid, or dead space to discard.
 */
class BrassVirtualSlotList(
    slotSize: Float = 18f,
    gap: Float = 2f,
    var showNames: Boolean = true,
) : BrassInventoryGrid(columns = 1, rows = 1, slotSize = slotSize, gap = gap) {

    private var itemCount = 0

    private var scroll = 0f
    private var draggingBar = false
    private var barGrabOffset = 0f
    private val bar = BrassScrollbarModel()

    override val slotCount: Int get() = itemCount.coerceAtLeast(1)

    private fun pitch(): Float = sz() + gap

    private fun viewport(): Float = (getHeight() - pad() * 2f).coerceAtLeast(0f)

    private fun content(): Float = itemCount * pitch()

    private fun clampScroll(v: Float): Float {
        bar.viewport = viewport()
        bar.content = content()
        return bar.clamp(v)
    }

    fun setItems(ids: List<String>) {
        itemCount = ids.size
        setContents(ids.mapIndexed { index, id -> index to Slot(id, 1) }.toMap())
        scroll = clampScroll(scroll)
    }

    var scrollOffset: Float
        get() = scroll
        set(value) { scroll = clampScroll(value) }


    override fun slotX(index: Int): Float = originX()

    override fun slotY(index: Int): Float = originY() + index * pitch() - scroll

    override fun slotAt(localX: Float, localY: Float): Int {
        if (localX !in 0f..sz()) return -1
        val p = pitch()
        val ly = localY + scroll
        if (ly < 0f) return -1
        val row = floor(ly / p).toInt()
        if (row !in 0 until slotCount) return -1
        // Reject the gap between rows, so dropping on a seam is a miss rather than a neighbour.
        if (ly - row * p > sz()) return -1
        return row
    }


    private fun gripRect(): FloatArray? {
        val m = bar
        if (!m.scrollable) return null
        val x = getLeft() + getWidth() - BrassScrollbar.WIDTH - 2f
        val y = getTop() + pad()
        val gripY = y + m.gripTop(scroll)
        return floatArrayOf(x, gripY, x + BrassScrollbar.WIDTH, gripY + m.gripHeight())
    }


    override fun drawContent(matrixStack: UMatrixStack, bx: Int, by: Int, bw: Int, bh: Int) {
        val platform = BrassPlatform.current
        val hovered = slotUnderCursor()
        hoverIndex = hovered
        advanceLandings()
        scroll = clampScroll(scroll)

        val s = sz()
        val p = pitch()
        val first = floor(scroll / p).toInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        val last = ceil((scroll + viewport()) / p).toInt().coerceIn(first, itemCount)

        for (i in first until last) {
            val x = slotX(i)
            val y = slotY(i)
            paintWell(
                matrixStack, i, x, y,
                if (i == hovered) 1f else 0f,
                if (link.isPainted(this, i)) 1f else 0f,
            )
            val slot = slot(i) ?: continue
            val (ix, iy) = itemPos(i, x, y)
            paintItem(matrixStack, platform, slot, ix, iy)
            if (showNames) {
                val name = platform?.itemName(slot.itemId) ?: slot.itemId
                BrassFont.draw(
                    matrixStack, this, name,
                    x + s + 6f, y + (s - BrassFont.LINE) / 2f,
                    Colors.UI_TEXT,
                )
            }
        }

        paintHeld(matrixStack, platform)

        gripRect()?.let { g ->
            BrassPaint.rectSnapped(matrixStack, g[0], getTop() + pad(), g[2], getBottom() - 1f, TRACK)
            BrassCard.grip(matrixStack, g[0], g[1], g[2], g[3], if (draggingBar) 1f else 0f)
        }
    }


    init {
        card = false
        chrome = BrassChrome.NONE
        // Clip rows to the component's own box, so a partially visible row paints in full and is
        // simply cut off at the edge instead of being skipped.
        enableEffect(ScissorEffect())

        onMouseScroll { e ->
            scroll = clampScroll(scroll - e.delta.toFloat() * pitch() * 2f)
            e.stopPropagation()
        }

        onMouseClick { e ->
            if (e.mouseButton != 0) return@onMouseClick
            val ax = getLeft() + e.relativeX
            val ay = getTop() + e.relativeY
            val grip = gripRect()
            if (grip != null && ax >= grip[0] - BAR_GRAB && ax <= grip[2] + BAR_GRAB) {
                if (ay >= grip[1] && ay <= grip[3]) {
                    draggingBar = true
                    barGrabOffset = ay - grip[1]
                } else if (ay >= getTop() + pad()) {
                    scroll = bar.pageToward(scroll, ay - (getTop() + pad()))
                }
                return@onMouseClick
            }
        }

        // Elementa broadcasts drags to the whole tree, hence the `draggingBar` gate.
        onMouseDrag { _, my, btn ->
            if (!draggingBar || btn != 0) return@onMouseDrag
            scroll = bar.offsetForGripTop(getTop() + my - barGrabOffset - (getTop() + pad()))
        }
        onMouseRelease { draggingBar = false }
    }

    private companion object {
        const val BAR_GRAB = 3f
        val TRACK: java.awt.Color get() = Colors.SCROLL_TRACK
    }
}
