@file:Suppress("unused")
package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UMatrixStack
import org.lwjgl.glfw.GLFW
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassDismissable
import net.swzo.brass.ui.kit.base.BrassMetrics
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.layout.BrassDivider
import net.swzo.brass.ui.kit.layout.BrassScrollbar
import net.swzo.brass.ui.kit.layout.BrassScrollbarModel
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassTextInput

/**
 * A floating panel of action rows shown at a point (typically on right-click). Row menus get a
 * search field (only when they have more than eight entries), an eight-row scrollable section with a
 * scrollbar, and an optional pinned footer ([footerItems]) that always stays visible below a
 * divider. Picking a row runs its action and closes the menu.
 */
class BrassContextMenu private constructor(
    items: List<Item>,
    footerItems: List<Item>,
    private val content: UIComponent?,
    private val rowWidth: Int,
    private val contentHeight: Int,
) : UIContainer(), BrassDismissable {

    constructor(items: List<Item>, rowWidth: Int = 130, footerItems: List<Item> = emptyList()) :
        this(items, footerItems, null, rowWidth, 0)

    class Item private constructor(
        val label: String?,
        val action: (() -> Unit)?,
        val isSeparator: Boolean,
    ) {
        constructor(label: String, action: () -> Unit) : this(label, action, false)

        companion object {
            val SEPARATOR: Item = Item(null, null, true)
            fun separator(): Item = SEPARATOR
        }
    }

    // A menu built with only a footer (nothing to scroll) promotes it to the scrollable section, so
    // single-section menus stay unchanged.
    private val scrollItems: List<Item>
    private val pinnedItems: List<Item>
    private val showSearch: Boolean
    private val searchBandH: Float

    init {
        val promote = items.isEmpty() && footerItems.isNotEmpty()
        scrollItems = if (promote) footerItems else items
        pinnedItems = if (promote) emptyList() else footerItems
        showSearch = allItems.count { !it.isSeparator } > SEARCH_THRESHOLD
        searchBandH = if (showSearch) SEARCH_AREA_H else 0f
    }

    private val panel = Panel()
    private var root: UIComponent? = null

    var onDismiss: (() -> Unit)? = null

    private val rowH = 17
    private val sepH = 9
    private val gap = 1
    private val pad = 3

    // Buttons in menu order; null at separator slots, so highlight stays index-aligned.
    private val rows = ArrayList<BrassButton?>()
    private val footerRows = ArrayList<BrassButton?>()

    private val searchInput = BrassTextInput("", "Search…")
    private val rowViewport = UIContainer()
    private val rowsCard = UIContainer()
    private val footerBand = UIContainer()

    private var scroll = 0f
    private var currentHeight = 0
    private var viewportH = 0f
    private var query = ""
    private var highlightedIndex = -1
    // Enter reaches both the search field's onSubmit and the menu's key handler.
    private var activated = false
    private var openedAt = 0L

    companion object {
        private const val EDGE = BrassMetrics.FLOATING_EDGE
        private const val SEARCH_AREA_H = 22f
        private const val SEARCH_THRESHOLD = 8
        private const val MAX_SCROLL_H = 149f
        private const val MIN_SCROLL_H = 60f
        private const val MAX_SCROLL_H_DEFAULT = 149f
        private const val DIVIDER_STEP = 10f
        private const val BAR_GRAB = 3f
        private val SCROLL_TRACK: java.awt.Color get() = Colors.SCROLL_TRACK

        private var openRef: java.lang.ref.WeakReference<BrassContextMenu>? = null

        private var open: BrassContextMenu?
            get() = openRef?.get()
            set(value) { openRef = value?.let { java.lang.ref.WeakReference(it) } }

        // Weak: a closed screen must not be kept alive through its menu.
        private val wired = java.util.WeakHashMap<UIComponent, Boolean>()

        fun closeOpen() {
            open?.dismiss()
        }

        fun isAnyOpen(): Boolean = open != null

        private fun wire(root: UIComponent) {
            if (wired.put(root, true) != null) return
            root.onMouseClick { e ->
                val menu = open ?: return@onMouseClick
                if (System.nanoTime() - menu.openedAt < OPEN_GRACE_NANOS) return@onMouseClick
                val x = e.absoluteX
                val y = e.absoluteY
                val inside = x >= menu.getLeft() && x <= menu.getRight() &&
                    y >= menu.getTop() && y <= menu.getBottom()
                if (!inside) menu.dismiss()
            }
        }

        private const val OPEN_GRACE_NANOS = 60_000_000L

        fun custom(content: UIComponent, width: Int, height: Int): BrassContextMenu =
            BrassContextMenu(emptyList(), emptyList(), content, width, height)

        fun keepOnTop(root: UIComponent) {
            val menu = open ?: return
            if (menu.root !== root) return
            BrassLayers.raise(root, menu)
        }
    }

    private fun rowStep(section: List<Item>, i: Int): Int =
        if (section[i].isSeparator) sepH + gap else rowH + gap

    private fun sectionHeight(section: List<Item>): Int {
        var h = pad
        for (i in section.indices) h += rowStep(section, i)
        return h - gap + pad
    }

    private fun scrollRowsHeight(): Int = sectionHeight(scrollItems)

    // The section-height top pad is already consumed by the divider starting at y=0.
    private fun footerRowsHeight(): Int =
        if (pinnedItems.isEmpty()) 0 else sectionHeight(pinnedItems) - pad + DIVIDER_STEP.toInt()

    private fun rowTop(index: Int): Int {
        var y = pad
        for (i in 0 until index) y += rowStep(scrollItems, i)
        return y
    }

    private val allItems: List<Item>
        get() = if (pinnedItems.isEmpty()) scrollItems else scrollItems + pinnedItems

    private fun rowMenuHeight(screenH: Float): Pair<Int, Float> {
        val footerH = footerRowsHeight()
        val available = (screenH - EDGE * 2 - searchBandH - footerH).coerceAtLeast(MIN_SCROLL_H)
        val scrollH = minOf(scrollRowsHeight().toFloat(), MAX_SCROLL_H, available)
        return (searchBandH + scrollH + footerH).toInt() to scrollH
    }

    private fun maxScroll(): Float = (scrollRowsHeight() - viewportH).coerceAtLeast(0f)

    // True only once show() has computed the final viewport height and the rows overflow it.
    private fun scrollbarNeeded(): Boolean = viewportH > 0f && maxScroll() > 0f

    init {
        val interim = searchBandH +
            minOf(scrollRowsHeight().toFloat(), MAX_SCROLL_H_DEFAULT) +
            footerRowsHeight()
        constrain {
            width = rowWidth.pixels()
            height = interim.pixels()
        }
        panel.constrain { width = 100.percent(); height = 100.percent() } childOf this

        if (content != null) {
            content.constrain {
                x = pad.pixels(); y = pad.pixels()
                width = 100.percent() - (pad * 2).pixels()
                height = 100.percent() - (pad * 2).pixels()
            }.childOf(this)
        } else {
            if (showSearch) {
                val searchRow = UIContainer().constrain {
                    x = 0.pixels(); y = 0.pixels()
                    width = 100.percent(); height = 20.pixels()
                } childOf this
                searchInput.constrain {
                    x = pad.pixels(); y = 2.pixels()
                    width = 100.percent() - (pad * 2).pixels()
                    height = 16.pixels()
                } childOf searchRow
                searchInput.onChange { text ->
                    query = text
                    reveal()
                }
                searchInput.onSubmit = { activate() }
            }

            rowViewport.constrain {
                x = 0.pixels(); y = searchBandH.pixels()
                // Reserve the scrollbar strip only when the rows actually overflow the viewport -
                // a short menu (≤ the 8-row cap) has no scrollbar, so its rows fill the full width.
                width = basicWidthConstraint { c ->
                    c.parent.getWidth() - if (scrollbarNeeded()) (BrassScrollbar.WIDTH + 4f) else 0f
                }
                height = basicHeightConstraint { c ->
                    (c.parent.getHeight() - searchBandH - footerRowsHeight()).coerceAtLeast(0f)
                }
            } childOf this
            rowViewport.enableEffect(ScissorEffect())
            rowsCard.constrain {
                x = 0.pixels()
                // basicYConstraint resolves an ABSOLUTE screen y, so offset from the viewport's top.
                y = basicYConstraint { c -> c.parent.getTop() - scroll }
                width = 100.percent()
                height = scrollRowsHeight().toFloat().pixels()
            } childOf rowViewport

            scrollItems.forEachIndexed { i, item ->
                if (item.isSeparator) {
                    val sepRow = UIContainer().constrain {
                        x = pad.pixels()
                        y = if (i == 0) pad.pixels() else SiblingConstraint(gap.toFloat())
                        width = 100.percent() - (pad * 2).pixels()
                        height = sepH.pixels()
                    } childOf rowsCard
                    BrassDivider().constrain {
                        x = 4.pixels(); y = CenterConstraint()
                        width = 100.percent() - 8.pixels(); height = 2.pixels()
                    } childOf sepRow
                    rows += null
                } else {
                    val row = BrassButton(item.label ?: "", BrassAccent.DEFAULT) {
                        item.action?.invoke(); dismiss()
                    }.apply {
                        centered = false
                        chrome = BrassChrome.FLAT
                        entranceEnabled = false
                        selectable = true
                    }
                    row.constrain {
                        x = pad.pixels()
                        y = if (i == 0) pad.pixels() else SiblingConstraint(gap.toFloat())
                        width = 100.percent() - (pad * 2).pixels()
                        height = rowH.pixels()
                    } childOf rowsCard
                    rows += row
                }
            }

            footerBand.constrain {
                x = 0.pixels()
                // Absolute screen y: the parent's top plus its height, minus the band.
                y = basicYConstraint { c -> c.parent.getTop() + c.parent.getHeight() - footerRowsHeight() }
                width = 100.percent()
                height = footerRowsHeight().toFloat().pixels()
            } childOf this
            if (pinnedItems.isNotEmpty()) {
                val dividerRow = UIContainer().constrain {
                    x = pad.pixels(); y = 0.pixels()
                    width = 100.percent() - (pad * 2).pixels()
                    height = sepH.pixels()
                } childOf footerBand
                BrassDivider().constrain {
                    x = 4.pixels(); y = CenterConstraint()
                    width = 100.percent() - 8.pixels(); height = 2.pixels()
                } childOf dividerRow

                pinnedItems.forEachIndexed { i, item ->
                    val row = BrassButton(item.label ?: "", BrassAccent.DEFAULT) {
                        item.action?.invoke(); dismiss()
                    }.apply {
                        centered = false
                        chrome = BrassChrome.FLAT
                        entranceEnabled = false
                        selectable = true
                    }
                    row.constrain {
                        x = pad.pixels()
                        y = if (i == 0) (sepH + gap).toFloat().pixels() else SiblingConstraint(gap.toFloat())
                        width = 100.percent() - (pad * 2).pixels()
                        height = rowH.pixels()
                    } childOf footerBand
                    footerRows += row
                }
            }

            MenuScrollbar({ scroll }, { scroll = it }).constrain {
                x = 100.percent() - (BrassScrollbar.WIDTH + 3f).pixels()
                y = searchBandH.pixels()
                width = BrassScrollbar.WIDTH.pixels()
                height = basicHeightConstraint { c ->
                    (c.parent.getHeight() - searchBandH - footerRowsHeight()).coerceAtLeast(0f)
                }
            } childOf this

            onMouseScroll { e ->
                val max = maxScroll()
                if (max <= 0f) return@onMouseScroll
                scroll = (scroll - e.delta.toFloat() * (rowH + gap) * 2f).coerceIn(0f, max)
                e.stopPropagation()
            }

            // Elementa hands keys to the whole tree, so this fires whether or not the search field
            // has focus; while it does, its own typing owns letters/backspace (no double-append).
            onKeyType { typedChar, keyCode ->
                when {
                    keyCode == GLFW.GLFW_KEY_UP -> moveHighlight(-1)
                    keyCode == GLFW.GLFW_KEY_DOWN -> moveHighlight(1)
                    keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER -> activate()
                    searchInput.focused -> Unit
                    keyCode == GLFW.GLFW_KEY_BACKSPACE -> {
                        query = query.dropLast(1)
                        syncSearch()
                    }
                    !typedChar.isISOControl() && typedChar.code >= 32 -> {
                        query += typedChar
                        syncSearch()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun syncSearch() {
        searchInput.setTextSilently(query)
        reveal()
    }

    private fun activate() {
        if (activated) return
        val index = highlightedIndex
        if (index !in allItems.indices || allItems[index].isSeparator) return
        activated = true
        allItems[index].action?.invoke()
        dismiss()
    }

    private fun moveHighlight(delta: Int) {
        if (allItems.isEmpty()) return
        var next = (if (highlightedIndex < 0) 0 else highlightedIndex + delta).coerceIn(0, allItems.size - 1)
        while (next in allItems.indices && allItems[next].isSeparator) {
            val candidate = next + delta
            if (candidate !in allItems.indices) return
            next = candidate
        }
        setHighlight(next)
    }

    private fun reveal() {
        val q = query.trim().lowercase()
        if (q.isEmpty()) { setHighlight(-1); return }
        val index = allItems.indexOfFirst { !it.isSeparator && matchKey(it.label ?: "").startsWith(q) }
        setHighlight(index)
    }

    // Leading marker glyphs (★ / ↺) are stripped so search still matches favorited titles.
    private fun matchKey(label: String): String =
        label.lowercase().trimStart { it !in 'a'..'z' && it !in '0'..'9' }

    private fun setHighlight(index: Int) {
        highlightedIndex = index
        rows.forEachIndexed { i, row ->
            row?.let {
                it.selected = i == index
                it.accent = if (i == index) BrassAccent.BRASS else BrassAccent.DEFAULT
            }
        }
        footerRows.forEachIndexed { i, row ->
            val fi = scrollItems.size + i
            row?.let {
                it.selected = fi == index
                it.accent = if (fi == index) BrassAccent.BRASS else BrassAccent.DEFAULT
            }
        }
        // The footer is always visible; only scrollable-section rows need revealing.
        if (index < 0 || index >= scrollItems.size) return
        val top = rowTop(index).toFloat()
        val bottom = top + rowH
        if (top < scroll) scroll = top
        else if (bottom > scroll + viewportH - pad) scroll = (bottom - viewportH + pad).coerceAtLeast(0f)
        scroll = scroll.coerceIn(0f, maxScroll())
    }

    fun show(screenRoot: UIComponent, x: Float, y: Float, anchorTop: Float? = null) {
        closeOpen()
        wire(screenRoot)
        root = screenRoot
        openedAt = System.nanoTime()
        val sw = screenRoot.getWidth()
        val sh = screenRoot.getHeight()
        val (h, vh) = if (content == null) rowMenuHeight(sh) else contentHeight to 0f
        currentHeight = h
        viewportH = vh
        scroll = scroll.coerceIn(0f, maxScroll())
        val px = if (x + rowWidth + EDGE > sw) (x - rowWidth).coerceAtLeast(EDGE) else x
        val flipLine = anchorTop ?: y
        val py = if (y + h + EDGE > sh) (flipLine - h).coerceAtLeast(EDGE) else y
        constrain { this.x = px.pixels(); this.y = py.pixels(); this.height = h.pixels() }
        // Attach on the next render pass, not synchronously. Menus open from an Elementa mouse
        // handler (right-click, a button's click) are dispatched inside the Window's own
        // mouseRelease, which locks its children for the whole dispatch; adding a child during that
        // window throws "Cannot modify children while iterating over them" (Elementa's
        // throw-on-invalid-usage mode). The render-operation queue drains at the start of the next
        // frame's draw, when the children are unlocked. If the menu was dismissed before that (a
        // same-frame close), the guard below skips the stale attach.
        Window.Companion.enqueueRenderOperation {
            if (open !== this@BrassContextMenu) return@enqueueRenderOperation
            this@BrassContextMenu childOf screenRoot
            // grabWindowFocus must run after childOf - Elementa walks up to the enclosing Window and
            // a detached component throws "No window parent?" (the 2.2.7 crash). Custom-content menus
            // must NOT grab focus, or they steal keys from their own focused control. The search
            // field is deliberately left unfocused so Escape closes the menu in one press.
            if (content == null) {
                grabWindowFocus()
                setHighlight(allItems.indexOfFirst { !it.isSeparator })
            }
            this@BrassContextMenu.isFloating = true
            BrassLayers.raise(screenRoot, this@BrassContextMenu)
        }
        open = this
    }

    val isOpen: Boolean get() = open === this

    override fun dismiss() {
        if (open === this) open = null
        val r = root ?: return
        root = null
        onDismiss?.invoke()
        // Detach on the next render pass (same reason as the deferred attach in [show]): a menu
        // closed from a mouse handler runs while the Window is iterating its children, and removing
        // a child during that window throws too. A same-frame re-show enqueues its attach AFTER
        // this remove (FIFO), so the menu is re-added cleanly rather than duplicated.
        Window.Companion.enqueueRenderOperation {
            if (r.children.contains(this@BrassContextMenu)) r.removeChild(this@BrassContextMenu)
        }
    }

    private class Panel : BrassWidget(BrassAccent.DEFAULT) {
        init {
            entranceEnabled = false
            roundness = 4f
        }
        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {}
    }

    private inner class MenuScrollbar(
        private val scrollProvider: () -> Float,
        private val onScroll: (Float) -> Unit,
    ) : BrassWidget(BrassAccent.DEFAULT) {
        private val bar = BrassScrollbarModel()
        private var dragging = false
        private var grabOffset = 0f

        init {
            chrome = BrassChrome.NONE
            onMouseClick { e ->
                if (e.mouseButton != 0) return@onMouseClick
                sync()
                if (gripRect() == null) return@onMouseClick
                if (e.relativeX < -BAR_GRAB || e.relativeX > getWidth() + BAR_GRAB) return@onMouseClick
                if (bar.gripContains(scrollProvider(), e.relativeY)) {
                    dragging = true
                    grabOffset = e.relativeY - bar.gripTop(scrollProvider())
                } else {
                    onScroll(bar.pageToward(scrollProvider(), e.relativeY))
                }
            }
            onMouseDrag { _, my, btn ->
                if (!dragging || btn != 0) return@onMouseDrag
                onScroll(bar.offsetForGripTop(my - grabOffset))
            }
            onMouseRelease { dragging = false }
        }

        private fun sync() {
            bar.viewport = getHeight()
            bar.content = scrollRowsHeight().toFloat()
        }

        private fun gripRect(): FloatArray? {
            if (!bar.scrollable) return null
            val x = getLeft()
            val y = getTop()
            val gy = y + bar.gripTop(scrollProvider())
            return floatArrayOf(x, gy, x + getWidth(), gy + bar.gripHeight())
        }

        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
            sync()
            val g = gripRect() ?: return
            BrassPaint.rectSnapped(m, g[0], y.toFloat(), g[2], (y + h).toFloat() - 1f, SCROLL_TRACK)
            BrassCard.grip(m, g[0], g[1], g[2], g[3], if (dragging) 1f else 0f)
        }
    }
}
