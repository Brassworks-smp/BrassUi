package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassFocusable
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import java.awt.Color
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A scrolling list of [T] that creates **no component per row** - rows are painted directly, and only
 * the handful inside the viewport are touched at all.
 *
 * ### Why this is a base class and not a widget
 *
 * All of this was inside [net.swzo.brass.ui.kit.surface.BrassTable]: the scroll offset and its clamp,
 * a [BrassScrollbarModel] and the grip's hit-testing, drag-tracking and track paging, the hovered-row
 * lookup with its visibility gate, selection, arrow-key navigation, `scrollTo` / `scrollToEnd` /
 * `scrollOffset`, and the first/last arithmetic that decides which rows to draw. None of it is about
 * *tables*. A tree view needs every line of it and would otherwise have arrived as a second copy -
 * which is exactly how the table and [BrassScrollbar] ended up with two different scrollbars before
 * [BrassScrollbarModel] pulled the geometry out.
 *
 * Subclasses supply the two things that genuinely differ:
 *
 * - [paintRow] - what one row looks like.
 * - [headerHeight] / [paintHeader] - an optional fixed band above the rows that does not scroll.
 *
 * Everything else, including what a row *is*, stays here. A row is an index into [items]; the
 * subclass decides what to draw for it.
 *
 * ### Scrolling is internal
 *
 * The component owns its viewport rather than living inside a `ScrollComponent`. That is what lets it
 * skip off-screen rows - a scroll view would need every row to exist as a child in order to position
 * it - and it is why the scrollbar has to be painted and dragged by hand here rather than by
 * attaching a [BrassScrollbar].
 */
abstract class BrassVirtualList<T>(
    protected val rowHeight: Float = 16f,
    private val onSelect: ((T, Int) -> Unit)? = null,
) : BrassWidget(BrassAccent.DEFAULT), BrassFocusable {

    // An explicit backing field, not `var items` with a protected setter: the generated setter and
    // `setItems` collide on the same JVM signature.
    private var current: List<T> = emptyList()

    /** The backing data. Replace with [setItems]. */
    val items: List<T> get() = current

    /** Index of the selected row, or -1. */
    var selectedIndex: Int = -1
        protected set

    /** Index of the row under the cursor, or -1. Valid during [paintRow]. */
    protected var hoveredRow: Int = -1
        private set

    private var scroll = 0f

    /** Height of the non-scrolling band above the rows. Zero for a list with no header. */
    protected open val headerHeight: Float get() = 0f

    /** Paint the header band, if [headerHeight] is non-zero. */
    protected open fun paintHeader(m: UMatrixStack, x: Float, y: Float, w: Float) {}

    /**
     * Paint one row.
     *
     * ([x], [y]) is the row's top-left in absolute coordinates and ([w], [rowHeight]) its size. The
     * row may be partially outside the viewport - the whole component carries a `ScissorEffect`, so
     * paint it in full and let the clip cut it, which is what makes scrolling look continuous rather
     * than making text pop in and out at the edges.
     *
     * The background wash for selection, hover and striping is already drawn; override
     * [rowBackground] to change it rather than painting over it here.
     */
    protected abstract fun paintRow(m: UMatrixStack, item: T, index: Int, x: Float, y: Float, w: Float)

    /** The wash behind row [index], or null for none. */
    protected open fun rowBackground(index: Int): Color? = when {
        index == selectedIndex -> SELECTED
        index == hoveredRow -> HOVER
        index % 2 == 1 -> STRIPE
        else -> null
    }

    /**
     * A click landed on row [index] at [localX] (from the component's left edge). Return true to
     * consume it and suppress the default select-the-row behaviour.
     */
    protected open fun onRowClick(index: Int, localX: Float, button: Int): Boolean = false

    /** A click landed on the header at [localX]. Only called when [headerHeight] is non-zero. */
    protected open fun onHeaderClick(localX: Float) {}

    /**
     * First look at any click, before the scrollbar and the rows - for chrome painted in
     * [paintOverlay] that has to be clickable, like the code view's copy button. Return true to
     * consume the click.
     *
     * A subclass cannot do this with a listener of its own: listeners run in registration order, so
     * the base class's handler has already selected the row underneath by the time a later listener
     * sees the click, and a copy button that also changes the selection reads as a misclick.
     */
    protected open fun onViewportClick(localX: Float, localY: Float, button: Int): Boolean = false

    /**
     * Paint over the rows, before the scrollbar, across the **whole viewport** rather than one row.
     *
     * For chrome that belongs to the list rather than to any row and must not stop where the rows do -
     * a line-number gutter, a fold column, a ruler. Painting those in [paintRow] leaves them ending at
     * the last row instead of at the bottom of the view, which reads as the column having run out.
     */
    protected open fun paintOverlay(m: UMatrixStack, x: Float, y: Float, w: Float, h: Float) {}

    /** Whether a press anywhere should take keyboard focus. */
    override val focusable: Boolean get() = true

    // ---- scrollbar -------------------------------------------------------------------------------

    private var draggingBar = false
    private var barGrabOffset = 0f

    /** Geometry shared with [BrassScrollbar] - see [BrassScrollbarModel]. */
    private val bar = BrassScrollbarModel()

    private fun syncBar(): BrassScrollbarModel {
        bar.viewport = bodyHeight()
        bar.content = contentHeight()
        return bar
    }

    /** Grip rectangle `[x1, y1, x2, y2]`, or null when there is nothing to scroll. */
    private fun gripRect(): FloatArray? {
        val m = syncBar()
        if (!m.scrollable) return null
        val x = getLeft(); val y = getTop(); val w = getWidth()
        val gripY = y + headerHeight + m.gripTop(scroll)
        return floatArrayOf(x + w - BrassScrollbar.WIDTH - 2f, gripY, x + w - 2f, gripY + m.gripHeight())
    }

    // ---- geometry --------------------------------------------------------------------------------

    protected fun bodyHeight() = (getHeight() - headerHeight).coerceAtLeast(0f)

    private fun contentHeight() = items.size * rowHeight

    private fun clampScroll(v: Float) = syncBar().clamp(v)

    /** Row index under a y offset measured from the component's top, or -1 outside the body. */
    protected fun rowAt(localY: Float): Int {
        if (localY < headerHeight) return -1
        val index = floor((localY - headerHeight + scroll) / rowHeight).toInt()
        return if (index in items.indices) index else -1
    }

    // ---- public API ------------------------------------------------------------------------------

    /** Swap the backing data. Cheap: nothing is built until the rows are drawn. */
    open fun setItems(next: List<T>) {
        current = next
        if (selectedIndex >= items.size) selectedIndex = -1
        scroll = clampScroll(scroll)
    }

    fun rowCount(): Int = items.size

    fun selected(): T? = items.getOrNull(selectedIndex)

    /** Select row [index] (or -1 to clear) and notify, without scrolling to it. */
    fun select(index: Int) {
        if (index !in items.indices) { selectedIndex = -1; return }
        selectedIndex = index
        onSelect?.invoke(items[index], index)
    }

    /** Scroll so [index] is visible, nudging by the minimum needed. */
    fun scrollTo(index: Int) {
        scroll = syncBar().reveal(scroll, index * rowHeight, rowHeight)
    }

    /** Pin to the bottom - for a log view that should follow new lines. */
    fun scrollToEnd() { scroll = clampScroll(Float.MAX_VALUE) }

    /**
     * Current scroll offset in pixels, and settable - a view that reloads its data needs to put the
     * scroll back where it was.
     */
    var scrollOffset: Float
        get() = scroll
        set(value) { scroll = clampScroll(value) }

    /** Move the selection by [delta] rows, scrolling to follow it. Routed here by `BrassFocus`. */
    fun moveSelection(delta: Int) {
        if (items.isEmpty()) return
        val next = (if (selectedIndex < 0) 0 else selectedIndex + delta).coerceIn(0, items.size - 1)
        if (next == selectedIndex) return
        selectedIndex = next
        scrollTo(next)
        onSelect?.invoke(items[next], next)
    }

    // ---- input -----------------------------------------------------------------------------------

    init {
        // A BrassWidget, not a bare UIComponent. The base class is what runs the entrance cascade and
        // registers the component with BrassDevMode.inspect - a list that paints itself with raw
        // Elementa gets neither, which is why the table and the tree were invisible to the inspector.
        // chrome = NONE because paintBody draws the whole card, rows and scrollbar itself.
        chrome = BrassChrome.NONE

        onMouseScroll { e ->
            scroll = clampScroll(scroll - e.delta.toFloat() * rowHeight * 2f)
            e.stopPropagation()
        }
        // Clip to our own bounds. This is what lets a partial row render at all: it draws its full
        // text and is simply cut off at the edge, instead of being skipped.
        enableEffect(ScissorEffect())

        onMouseClick { e ->
            if (e.mouseButton != 0) return@onMouseClick
            val ax = getLeft() + e.relativeX
            val ay = getTop() + e.relativeY

            // Overlay chrome gets the first refusal - see onViewportClick.
            if (onViewportClick(e.relativeX, e.relativeY, e.mouseButton)) return@onMouseClick

            // A press on the scrollbar grabs it instead of selecting a row; a press on the track above
            // or below the grip pages toward the click, as every scrollbar does.
            val grip = gripRect()
            if (grip != null && ax >= grip[0] - BAR_GRAB && ax <= grip[2] + BAR_GRAB) {
                if (ay >= grip[1] && ay <= grip[3]) {
                    draggingBar = true
                    barGrabOffset = ay - grip[1]
                } else if (ay >= getTop() + headerHeight) {
                    scroll = syncBar().pageToward(scroll, ay - (getTop() + headerHeight))
                }
                return@onMouseClick
            }

            if (headerHeight > 0f && e.relativeY < headerHeight) {
                onHeaderClick(e.relativeX)
                return@onMouseClick
            }

            val index = rowAt(e.relativeY)
            if (index in items.indices) {
                if (onRowClick(index, e.relativeX, e.mouseButton)) return@onMouseClick
                selectedIndex = index
                onSelect?.invoke(items[index], index)
            }
        }

        // Elementa broadcasts drags to the whole tree, hence the `draggingBar` gate - the same one
        // BrassSlider and the frame drags need.
        onMouseDrag { _, my, btn ->
            if (!draggingBar || btn != 0) return@onMouseDrag
            scroll = syncBar().offsetForGripTop(getTop() + my - barGrabOffset - (getTop() + headerHeight))
        }
        onMouseRelease { draggingBar = false }
    }

    // ---- paint -----------------------------------------------------------------------------------

    override fun drawContent(matrixStack: UMatrixStack, bx: Int, by: Int, bw: Int, bh: Int) {
        // Whole-pixel bounds, snapped *inward* (ceil the origin, floor the far edge), used by every
        // layer of the paint below. Two reasons, both learned from the top rule of the code view's
        // card coming out as a dim half-height line:
        //
        //  - A list stacked by SiblingConstraint lands on a fractional y as often as not, and a 1-px
        //    rule drawn at y = 100.5 is split across two device rows at half intensity.
        //  - The component carries a ScissorEffect clipped to its *exact* float bounds, so snapping
        //    toward the nearest pixel can move a rule just outside the clip and shave it entirely.
        //    Ceiling the origin keeps everything inside the scissor whatever its own rounding does;
        //    the sub-pixel strip left over shows the surface behind the card, which is invisible.
        val x = ceil(getLeft())
        val y = ceil(getTop())
        val w = floor(getRight()) - x
        val h = floor(getBottom()) - y

        if (w > 0 && h > 0) {
            scroll = clampScroll(scroll)

            // Nothing the user cannot see gets to react to the cursor. Elementa dispatches hover from
            // geometry alone, so a list under an open popup - or scrolled out of its own parent's
            // view - still passed the bounds test and lit rows up *through* whatever covered it.
            val (mx, my) = getMousePosition()
            hoveredRow = if (mx >= x && mx <= x + w && my >= y && my <= y + h && BrassCull.visible(this)) {
                rowAt(my - y)
            } else -1

            paintBody(matrixStack, x, y, w, h)
        }
    }

    private fun paintBody(m: UMatrixStack, x: Float, y: Float, w: Float, h: Float) {
        // Body card, drawn entirely inside the component's own box: it sits under a ScissorEffect
        // clipped to exactly this rect (see the class docs), so BrassCard.draw's outer ring - which
        // bleeds a pixel past the box on purpose, for a floating card - would have that pixel clipped
        // away and read as a single outline instead of the toolkit's usual two. See BrassCard.panel.
        BrassCard.panel(m, x, y, x + w, y + h)

        // Rows are drawn BEFORE the header so a row scrolling up passes underneath it.
        val bodyTop = y + headerHeight
        val viewport = bodyHeight()
        val first = floor(scroll / rowHeight).toInt().coerceAtLeast(0)
        val last = ceil((scroll + viewport) / rowHeight).toInt().coerceAtMost(items.size)

        for (i in first until last) {
            val ry = bodyTop + i * rowHeight - scroll
            rowBackground(i)?.let { BrassPaint.rectSnapped(m, x + 1f, ry, x + w - 1f, ry + rowHeight, it) }
            paintRow(m, items[i], i, x, ry, w)
        }

        if (headerHeight > 0f) paintHeader(m, x, y, w)

        // Full-viewport chrome, over the rows and under the scrollbar.
        paintOverlay(m, x, bodyTop, w, viewport)

        // Painted with the same track wash and BrassCard.grip a BrassScrollbar uses. The list scrolls
        // itself so it cannot reuse the component, but it can and must reuse the drawing.
        gripRect()?.let { g ->
            BrassPaint.rectSnapped(m, g[0], bodyTop, g[2], y + h - 1f, TRACK)
            BrassCard.grip(m, g[0], g[1], g[2], g[3], if (draggingBar) 1f else 0f)
        }
    }

    protected companion object {
        /** How far either side of the 3-px grip still counts as grabbing it. */
        const val BAR_GRAB = 3f

        val STRIPE: Color get() = Colors.ROW_STRIPE
        val HOVER: Color get() = Colors.ROW_HOVER
        val SELECTED: Color
            get() = Color(Colors.UI_ACCENT.red, Colors.UI_ACCENT.green, Colors.UI_ACCENT.blue, 46)
        /** The scrollbar track - the faint wash BrassScrollbar uses, not a black groove. */
        val TRACK: Color get() = Colors.SCROLL_TRACK
    }
}
