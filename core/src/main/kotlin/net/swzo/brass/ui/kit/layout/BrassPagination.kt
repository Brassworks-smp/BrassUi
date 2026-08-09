package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.text.BrassFont
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.abs
import kotlin.math.floor
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * Page numbers with prev and next - the companion to any list whose data lives on a server.
 * ```kotlin
 * val pager = BrassPagination(total = 40) { page -> market.load(page) }
 * ```
 * ### Why a list widget is not enough
 * [BrassVirtualList] makes a long list cheap to *draw*, but it still holds every row in memory. A
 * marketplace with forty thousand listings is not a scrolling problem, it is a fetching one - the
 * client should hold one page and ask for the next. This is the control that says which page, and
 * [BrassPageWindow.range] gives the slice for callers paging a local list instead.
 * ### Painted, not built
 * The buttons are drawn rather than being child components, for the reason [net.swzo.brass.ui.kit.input.BrassChips] is: the set
 * changes every time the page does, so components would mean adding and removing children from the
 * tree in the middle of Elementa's own iteration over them. Painting also keeps the row a fixed
 * height whatever the page count.
 * ### Animation
 * The current-page marker **slides** to its new cell rather than jumping, trailing a short smear
 * behind it while it travels; each cell brightens under the cursor over a few frames rather than
 * switching, and flares once when clicked. All of it comes from the painting decision above: with no
 * child components there is no [net.swzo.brass.ui.kit.base.BrassWidget] per cell to run the toolkit's
 * usual hover easing, so the row keeps its own [BrassEased] per cell plus a handful for the marker's
 * position and width, the press flare, and the window shift below.
 * The marker slides across a *changing* row, which is the case worth knowing about: the window of page
 * numbers shifts as you move through a long set, so the cell that was "5" a moment ago may now be "6".
 * The marker eases toward wherever the current page **currently** sits, so a shift reads as the
 * highlight travelling to its new home rather than as the row teleporting under a fixed marker.
 * That leaves the *numbers* themselves, which is the other half of the same problem. Paging forward
 * inside the middle run shifts the whole window by one, so every cell in it changes its digits at
 * once - with the marker sliding smoothly over a row that snapped, the effect was the marker moving
 * and the row flickering underneath. So a cell whose value changed **cross-slides**: the outgoing
 * number leaves toward the cell that now holds it and the incoming one arrives from the cell that
 * used to, both fading as they travel. Since `new[i] == old[i + 1]` for a forward shift, every cell
 * animates the same direction at the same time and the run reads as one strip of numbers sliding
 * under a stationary set of keycaps. Cells whose value did not change - page 1, the last page, the
 * arrows - do not move, which is what anchors the illusion.
 */
class BrassPagination(
    total: Int = 1,
    current: Int = 1,
    var around: Int = 1,
    private val onPage: (Int) -> Unit = {},
) : BrassWidget(BrassAccent.DEFAULT), BrassFocusable {

    var total: Int = total.coerceAtLeast(1)
        set(value) {
            field = value.coerceAtLeast(1)
            if (page > field) page = field
        }

    var page: Int = current.coerceIn(1, total.coerceAtLeast(1))
        set(value) {
            val next = value.coerceIn(1, total)
            if (next == field) return
            field = next
            onPage(next)
        }

    private var hoveredCell = -1


    private val markerX = BrassEased(0f, speed = MARKER_SPEED)
    private val markerW = BrassEased(0f, speed = MARKER_SPEED)
    private var markerPrimed = false

    /**
     * Per-cell hover brightness, indexed as [cells] is.
     * Rebuilt whenever the cell count changes - the eased value for "cell 3" means nothing once the
     * window has shifted and cell 3 is a different page, so carrying it over would bleed one cell's
     * glow onto another.
     */
    private val cellGlow = ArrayList<BrassEased>()

    private var shownCells: List<Int> = emptyList()
    private var priorCells: List<Int> = emptyList()
    private val shift = BrassEased(1f, speed = SHIFT_SPEED)

    private var pressedCell = -1
    private val pressFlash = BrassEased(0f, speed = PRESS_SPEED)

    private fun glowFor(index: Int, count: Int): BrassEased {
        if (cellGlow.size != count) {
            cellGlow.clear()
            repeat(count) { cellGlow.add(BrassEased(0f, speed = GLOW_SPEED)) }
        }
        return cellGlow[index]
    }

    var card: Boolean = false

    private fun pad(): Float = if (card) CARD_PAD else 0f

    private fun cells(): List<Int> = buildList {
        add(PREV)
        addAll(BrassPageWindow.pages(page, total, around))
        add(NEXT)
    }

    init {
        chrome = BrassChrome.NONE
        constrain { height = DEFAULT_H.pixels() }

        onMouseClick { e ->
            if (!active || e.mouseButton != 0) return@onMouseClick
            val cell = cellAt(e.relativeX)
            if (cell == -1 || !enabledFor(cell)) return@onMouseClick

            // Armed before the page changes, so the index still refers to the row the click landed on.
            pressedCell = cellIndexAt(e.relativeX)
            pressFlash.snapTo(1f)
            pressFlash.target = 0f

            when (cell) {
                PREV -> page -= 1
                NEXT -> page += 1
                else -> page = cell
            }
        }
    }

    override fun onKeyPressed(keyCode: Int): Boolean = when (keyCode) {
        GLFW.GLFW_KEY_LEFT -> { page -= 1; true }
        GLFW.GLFW_KEY_RIGHT -> { page += 1; true }
        GLFW.GLFW_KEY_HOME -> { page = 1; true }
        GLFW.GLFW_KEY_END -> { page = total; true }
        else -> false
    }

    private fun cellWidth(): Float =
        ((getWidth() - pad() * 2f) / cells().size.coerceAtLeast(1)).coerceAtLeast(1f)

    private fun cellIndexAt(localX: Float): Int {
        val index = floor((localX - pad()) / cellWidth()).toInt()
        return if (index in cells().indices) index else -1
    }

    private fun cellAt(localX: Float): Int = cells().getOrElse(cellIndexAt(localX)) { -1 }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val list = cells()
        val cw = cellWidth()
        val p = pad()

        if (card) {
            BrassCard.draw(m, x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), shadow = false)
        }

        val (mx, _) = getMousePosition()
        hoveredCell = if (hoveredState) cellIndexAt(mx - getLeft()) else -1

        noteRow(list)
        val slide = shift.advance()
        val flash = pressFlash.advance()

        val top = y + p
        val bottom = y + h - p
        val cy = top + (bottom - top) / 2f

        // Three passes rather than one, and the order is the whole point: every cell's resting button,
        // then the sliding marker over the current one, then every label on top. Drawn cell-by-cell the
        // marker would be painted under whichever buttons happen to come after it in the row, and it
        // would clip the label of the cell it is sliding across.

        // 1. Resting buttons. The arrows used to be bare icons floating on the background, which made
        // them look like decoration rather than the two controls people actually reach for. Only the
        // ellipsis and a dead-end arrow stay flat, because they are the two things that cannot be
        // pressed.
        for ((i, cell) in list.withIndex()) {
            if (cell == BrassPageWindow.GAP || !enabledFor(cell)) continue
            val cx = x + p + i * cw
            val glow = glowFor(i, list.size)
            glow.target = if (i == hoveredCell) 1f else 0f
            val lit = glow.advance()
            val hit = if (i == pressedCell) flash else 0f
            cellButton(m, cx + 1f, top + 1f, cx + cw - 1f, bottom - 1f, lit, hit)
        }

        // 2. The marker, eased toward whichever cell is current.
        val currentIndex = list.indexOf(page)
        if (currentIndex >= 0) {
            val targetX = x + p + currentIndex * cw
            if (!markerPrimed) {
                markerPrimed = true
                markerX.snapTo(targetX)
                markerW.snapTo(cw)
            }
            markerX.target = targetX
            markerW.target = cw
        }
        if (markerPrimed) {
            // The remaining distance, read *before* advancing: how far the marker still has to go is
            // what it stretches by, so the smear is longest as it sets off and gone as it lands.
            val remaining = markerX.target - markerX.value
            val mxPos = markerX.advance()
            val mwPos = markerW.advance()

            // Squash and stretch, the cheap kind: the trailing edge lags behind so the marker leaves a
            // short tail in the direction it came from. Only the trailing edge moves - stretching both
            // would read as the marker growing rather than as speed.
            val tail = (abs(remaining) / cw).coerceAtMost(1f) * MARKER_TRAIL * cw
            val lead = if (remaining > 0f) 0f else tail
            val drag = if (remaining > 0f) tail else 0f

            markerCard(m, mxPos + 1f - drag, top + 1f, mxPos + mwPos - 1f + lead, bottom - 1f)
        }

        // 3. Labels, over both. A cell whose value changed draws twice - the number leaving and the
        // number arriving - which is what turns a window shift into a slide rather than a flicker.
        for ((i, cell) in list.withIndex()) {
            val cx = x + p + i * cw
            val hot = i == hoveredCell && cell != BrassPageWindow.GAP && enabledFor(cell)
            val tint = when {
                !enabledFor(cell) -> Colors.UI_INNER_BORDER
                cell == page -> Colors.UI_ACCENT_BRIGHT
                hot -> Colors.UI_TEXT_HOVER
                else -> Colors.UI_TEXT_DARK
            }

            val was = priorCells.getOrElse(i) { cell }
            if (slide >= 1f || was == cell) {
                cellLabel(m, cell, cx, cy, cw, tint, 1f, 0f)
                continue
            }

            // `new[i] == old[i + 1]` for a forward shift, so a rising number arrived from the right;
            // the outgoing one leaves the way its replacement came in.
            val dir = if (cell > was) 1f else -1f
            val eased = ease(slide)
            cellLabel(m, was, cx, cy, cw, Colors.UI_TEXT_DARK, 1f - eased, -dir * cw * eased)
            cellLabel(m, cell, cx, cy, cw, tint, eased, dir * cw * (1f - eased))
        }
    }

    private fun noteRow(list: List<Int>) {
        if (list == shownCells) return
        val shifted = shownCells.isNotEmpty() && shownCells.size == list.size
        priorCells = if (shifted) shownCells else list
        shift.snapTo(if (shifted) 0f else 1f)
        shift.target = 1f
        shownCells = list
    }

    private fun cellLabel(
        m: UMatrixStack,
        cell: Int,
        cx: Float,
        cy: Float,
        cw: Float,
        tint: Color,
        alpha: Float,
        dx: Float,
    ) {
        if (alpha <= 0.01f) return
        val c = if (alpha >= 1f) tint else Colors.withAlpha(tint, (tint.alpha * alpha).toInt().coerceIn(0, 255))

        when (cell) {
            PREV, NEXT -> {
                val icon = if (cell == PREV) BrassIcons.CHEVRON_LEFT else BrassIcons.CHEVRON_RIGHT
                BrassIcons.draw(m, icon, cx + dx + (cw - ICON) / 2f, cy - ICON / 2f, ICON, c)
            }
            BrassPageWindow.GAP -> {
                val label = "…"
                BrassFont.draw(
                    m, this, label,
                    cx + dx + (cw - BrassFont.width(this, label)) / 2f,
                    cy - BrassFont.LINE / 2f,
                    if (alpha >= 1f) Colors.UI_INNER_BORDER else c,
                )
            }
            else -> {
                val label = cell.toString()
                BrassFont.draw(
                    m, this, label,
                    cx + dx + (cw - BrassFont.width(this, label)) / 2f,
                    // A pixel below the true centre. The font's cap height sits high in its line box,
                    // so a digit centred arithmetically inside a keycap reads as riding up against the
                    // top edge; the arrows, which are drawn as geometry rather than glyphs, do not
                    // need it.
                    cy - BrassFont.LINE / 2f + NUMBER_NUDGE,
                    c,
                )
            }
        }
    }

    /**
     * Smoothstep, so a sliding number leaves and arrives gently instead of at full speed.
     * [BrassEased] already decelerates, but it never accelerates - it is at its fastest on the first
     * frame, which for a value driving *position* reads as the numbers being flicked rather than
     * carried. Easing the progress rather than the position keeps the two halves of the cross-slide
     * exactly complementary, which is what stops the pair reading as two separate labels.
     */
    private fun ease(t: Float): Float {
        val u = t.coerceIn(0f, 1f)
        return u * u * (3f - 2f * u)
    }

    private fun cellButton(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        glow: Float,
        flash: Float,
    ) {
        if (x2 <= x1 || y2 <= y1) return
        val rest = Colors.mix(Colors.UI_ELEMENT_BG, Colors.UI_ELEMENT_BG_HOVER, glow)
        BrassCard.flat(
            m, x1, y1, x2, y2,
            fill = if (flash <= 0f) rest else Colors.mix(rest, Colors.UI_ELEMENT_BG_ACTIVE, flash),
        )
    }

    private fun markerCard(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float) {
        if (x2 <= x1 || y2 <= y1) return
        BrassCard.flat(m, x1, y1, x2, y2, fill = Colors.UI_ELEMENT_BG_ACTIVE)
        BrassCard.border(m, x1, y1, x2, y2, Colors.UI_ACCENT_BORDER)
    }

    private fun enabledFor(cell: Int): Boolean = when (cell) {
        PREV -> page > 1
        NEXT -> page < total
        BrassPageWindow.GAP -> false
        else -> true
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("pagination", "Pagination", 200f, 20f, card = false) {
            val pager = BrassPagination(total = 12, current = 1)
            pager.card = true
            pager
        }

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val DEFAULT_H = 14f
        private const val ICON = 6f
        private const val NUMBER_NUDGE = 1f
        private const val CARD_PAD = 2f
        private const val MARKER_SPEED = 16f
        private const val GLOW_SPEED = 12f
        private const val SHIFT_SPEED = 11f
        private const val PRESS_SPEED = 7f
        private const val MARKER_TRAIL = 0.35f
        private const val PREV = -2
        private const val NEXT = -3
    }
}
