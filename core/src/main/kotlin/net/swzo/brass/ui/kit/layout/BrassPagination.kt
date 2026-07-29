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
 *
 * ```kotlin
 * val pager = BrassPagination(total = 40) { page -> market.load(page) }
 * ```
 *
 * ### Why a list widget is not enough
 *
 * [BrassVirtualList] makes a long list cheap to *draw*, but it still holds every row in memory. A
 * marketplace with forty thousand listings is not a scrolling problem, it is a fetching one - the
 * client should hold one page and ask for the next. This is the control that says which page, and
 * [BrassPageWindow.range] gives the slice for callers paging a local list instead.
 *
 * ### Painted, not built
 *
 * The buttons are drawn rather than being child components, for the reason [BrassChips] is: the set
 * changes every time the page does, so components would mean adding and removing children from the
 * tree in the middle of Elementa's own iteration over them. Painting also keeps the row a fixed
 * height whatever the page count.
 *
 * ### Animation
 *
 * The current-page marker **slides** to its new cell rather than jumping, trailing a short smear
 * behind it while it travels; each cell brightens under the cursor over a few frames rather than
 * switching, and flares once when clicked. All of it comes from the painting decision above: with no
 * child components there is no [net.swzo.brass.ui.kit.base.BrassWidget] per cell to run the toolkit's
 * usual hover easing, so the row keeps its own [BrassEased] per cell plus a handful for the marker's
 * position and width, the press flare, and the window shift below.
 *
 * The marker slides across a *changing* row, which is the case worth knowing about: the window of page
 * numbers shifts as you move through a long set, so the cell that was "5" a moment ago may now be "6".
 * The marker eases toward wherever the current page **currently** sits, so a shift reads as the
 * highlight travelling to its new home rather than as the row teleporting under a fixed marker.
 *
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
    /** Pages shown either side of the current one before an ellipsis takes over. */
    var around: Int = 1,
    private val onPage: (Int) -> Unit = {},
) : BrassWidget(BrassAccent.DEFAULT), BrassFocusable {

    /** Total pages. Setting it re-clamps [page]. */
    var total: Int = total.coerceAtLeast(1)
        set(value) {
            field = value.coerceAtLeast(1)
            if (page > field) page = field
        }

    /** The current page, 1-based. Assigning it notifies, so a caller can drive it from code. */
    var page: Int = current.coerceIn(1, total.coerceAtLeast(1))
        set(value) {
            val next = value.coerceIn(1, total)
            if (next == field) return
            field = next
            onPage(next)
        }

    /** The cell under the cursor, as an index into [cells], or -1. */
    private var hoveredCell = -1

    // ---- animation -------------------------------------------------------------------------------

    /**
     * The current-page marker's left edge and width, eased so changing page **slides** the highlight
     * along the row instead of snapping it to a new cell.
     *
     * Position and width are tracked separately because both genuinely move: the row divides its width
     * evenly among however many cells it currently shows, so a window that grows an ellipsis re-sizes
     * every cell at the same moment one of them becomes current.
     */
    private val markerX = BrassEased(0f, speed = MARKER_SPEED)
    private val markerW = BrassEased(0f, speed = MARKER_SPEED)
    private var markerPrimed = false

    /**
     * Per-cell hover brightness, indexed as [cells] is.
     *
     * Rebuilt whenever the cell count changes - the eased value for "cell 3" means nothing once the
     * window has shifted and cell 3 is a different page, so carrying it over would bleed one cell's
     * glow onto another.
     */
    private val cellGlow = ArrayList<BrassEased>()

    /**
     * The row as it looked before the last change, and how far along the cross-slide between the two
     * is: 0 is entirely the old row, 1 entirely the new one.
     *
     * Only a change that keeps the cell **count** animates. A window that grows or loses a cell -
     * an ellipsis appearing, the total changing - re-sizes every cell at the same moment, so cell `i`
     * of the old row is not even the same width as cell `i` of the new one and sliding one into the
     * other would smear the whole row sideways. Those snap, and the marker's own ease carries them.
     */
    private var shownCells: List<Int> = emptyList()
    private var priorCells: List<Int> = emptyList()
    private val shift = BrassEased(1f, speed = SHIFT_SPEED)

    /**
     * The cell last clicked and how bright its press still is, eased back down to nothing.
     *
     * Separate from [cellGlow] because it is a different event: hover says where the cursor is and
     * follows it, a press is a moment that has to decay on its own. Keyboard paging leaves it alone -
     * there is no cell under the finger to flash.
     */
    private var pressedCell = -1
    private val pressFlash = BrassEased(0f, speed = PRESS_SPEED)

    private fun glowFor(index: Int, count: Int): BrassEased {
        if (cellGlow.size != count) {
            cellGlow.clear()
            repeat(count) { cellGlow.add(BrassEased(0f, speed = GLOW_SPEED)) }
        }
        return cellGlow[index]
    }

    /**
     * Draw a card behind the whole row, with the cells inset inside it.
     *
     * Off by default: a pager dropped into a form sits under the list it pages and wants to read as
     * part of it. Turn it on where the row stands alone - a footer under a table, a toolbar - and it
     * becomes a surface of its own instead of a line of loose buttons on the background.
     */
    var card: Boolean = false

    /** Space between the card and the cells, or zero when there is no card. */
    private fun pad(): Float = if (card) CARD_PAD else 0f

    /** What the row currently shows, left to right. Rebuilt each frame - it is a handful of ints. */
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
            if (cell == -1 || cell == BrassPageWindow.GAP || !enabledFor(cell)) return@onMouseClick

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

    /** Left and right page, Home and End jump to the ends - as they do in every pager. */
    override fun onKeyPressed(keyCode: Int): Boolean = when (keyCode) {
        GLFW.GLFW_KEY_LEFT -> { page -= 1; true }
        GLFW.GLFW_KEY_RIGHT -> { page += 1; true }
        GLFW.GLFW_KEY_HOME -> { page = 1; true }
        GLFW.GLFW_KEY_END -> { page = total; true }
        else -> false
    }

    /** Width of one cell, so the row stays evenly divided however many cells there are. */
    private fun cellWidth(): Float =
        ((getWidth() - pad() * 2f) / cells().size.coerceAtLeast(1)).coerceAtLeast(1f)

    /**
     * The cell **index** at a local x, or -1 outside the row.
     *
     * Floored rather than truncated: with the card's padding subtracted a point in the left margin is
     * a small negative number, and truncation rounds that to index 0 - so the pixels just outside the
     * first cell lit it up and clicked it.
     */
    private fun cellIndexAt(localX: Float): Int {
        val index = floor((localX - pad()) / cellWidth()).toInt()
        return if (index in cells().indices) index else -1
    }

    /** The cell value at a local x, or -1 outside the row. */
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

    /**
     * The row's cells since last frame, and the start of a cross-slide when they changed.
     *
     * A cell-count change is not a shift - see [priorCells] - so it lands the animation instead of
     * starting one, and the very first row is shown settled rather than sliding in from nowhere.
     */
    private fun noteRow(list: List<Int>) {
        if (list == shownCells) return
        val shifted = shownCells.isNotEmpty() && shownCells.size == list.size
        priorCells = if (shifted) shownCells else list
        shift.snapTo(if (shifted) 0f else 1f)
        shift.target = 1f
        shownCells = list
    }

    /** One cell's glyph or arrow, at [alpha] and offset [dx] along the row for a cross-slide. */
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
     *
     * [BrassEased] already decelerates, but it never accelerates - it is at its fastest on the first
     * frame, which for a value driving *position* reads as the numbers being flicked rather than
     * carried. Easing the progress rather than the position keeps the two halves of the cross-slide
     * exactly complementary, which is what stops the pair reading as two separate labels.
     */
    private fun ease(t: Float): Float {
        val u = t.coerceIn(0f, 1f)
        return u * u * (3f - 2f * u)
    }

    /**
     * One cell's resting card, brightening toward the hover fill as [glow] rises from 0 to 1, and
     * further toward the active fill for as long as [flash] - a click's decaying afterglow - lasts.
     *
     * The two stack rather than replacing one another: a press happens under the cursor by definition,
     * so the hover glow is always up when the flash fires, and picking one would mean the cell dropping
     * to its resting colour at the moment it was clicked.
     */
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

    /**
     * The current page's marker - the active fill plus the accent border, so the position you are on is
     * legible at a glance rather than needing to be read. Drawn at the eased position, which is what
     * turns a page change into a slide along the row.
     */
    private fun markerCard(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float) {
        if (x2 <= x1 || y2 <= y1) return
        BrassCard.flat(m, x1, y1, x2, y2, fill = Colors.UI_ELEMENT_BG_ACTIVE)
        BrassCard.border(m, x1, y1, x2, y2, Colors.UI_ACCENT_BORDER)
    }

    /** Whether a cell can be clicked - the arrows dim at the ends rather than disappearing. */
    private fun enabledFor(cell: Int): Boolean = when (cell) {
        PREV -> page > 1
        NEXT -> page < total
        BrassPageWindow.GAP -> false
        else -> true
    }

    companion object : BrassDemoSource {

        /**
         * Pages stepped through, including the ellipsis collapsing as the window moves.
         *
         * Clicked positionally: the page numbers are painted cells, not child widgets, so there is
         * nothing to fire an event at — and the cell hover is resolved from the pointer during draw,
         * which is why the sweep between clicks makes the highlight follow.
         *
         * `card` is on and [BrassDemo.card] off — the strip paints its own.
         */
        override fun demo() = BrassDemo("pagination", "Pagination", 200f, 20f, card = false) {
            val pager = BrassPagination(total = 12, current = 1)
            pager.card = true
            pager
        }

        // ---- widget internals ------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val DEFAULT_H = 14f
        private const val ICON = 6f
        /** How far a page number drops below the arithmetic centre of its cell. */
        private const val NUMBER_NUDGE = 1f
        /** Margin between the row's card and its cells. */
        private const val CARD_PAD = 2f
        /** How fast the current-page marker slides to a new cell. */
        private const val MARKER_SPEED = 16f
        /** How fast a cell brightens under the cursor - the speed every hover in the toolkit uses. */
        private const val GLOW_SPEED = 12f
        /**
         * How fast the numbers cross-slide when the window shifts. A little slower than the marker:
         * the marker is one shape moving a known distance and can afford to be brisk, while the row
         * is a dozen glyphs crossing each other and needs long enough to be read as travelling.
         */
        private const val SHIFT_SPEED = 11f
        /** How fast a clicked cell's flash decays. Fast - it is a confirmation, not a state. */
        private const val PRESS_SPEED = 7f
        /** How far the marker's trailing edge lags behind, as a fraction of one cell, at full speed. */
        private const val MARKER_TRAIL = 0.35f
        /** Sentinels for the two arrow cells, outside any valid page number. */
        private const val PREV = -2
        private const val NEXT = -3
    }
}
