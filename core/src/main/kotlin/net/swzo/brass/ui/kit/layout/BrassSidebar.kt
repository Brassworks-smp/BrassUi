package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.base.BrassMotion
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color
import kotlin.math.floor
import kotlin.math.sin

/**
 * A sidebar for a screen with many sections: **group captions** and **rows** that say what matters at a
 * glance - an icon, a name, a live count on the right (accent for "needs you", dark for "status"), an
 * optional progress hairline - with the selected row barred in the accent. The one level of navigation
 * for the screen: pair it with [BrassPages] (`slide = VERTICAL`) and clicking a row slides its page in.
 *
 * - A row can start **hidden** and arrive with [reveal]: it grows into place with a glow in [revealColor]
 *   and keeps a NEW dot until it is visited (a chapter unlocked, a feature switched on).
 * - Narrower than [compactBelow], it folds to icons; names and counts move to tooltips.
 * - [footer] pins one control under the rows (a primary action, a settings key).
 *
 * ```
 * val pages = BrassPages(slide = BrassPages.Slide.VERTICAL)
 * val bar = BrassSidebar(pages)
 *     .item("home", "Overview", BrassIcons.HOME, pages.page("Overview") { ... }, meta = { "${ready()}" })
 *     .header("Areas")
 *     .item("farm", "Farming", BrassIcons.LEAF, pages.page("Farming") { ... }, meta = { "3/6" }, accent = false, progress = { 0.5f })
 * ```
 * Give it a width (104-140 reads well; 24 for icons only) and the region's full height.
 */
class BrassSidebar @JvmOverloads constructor(
    private val pages: BrassPages? = null,
) : UIContainer() {

    /** One row. Everything after [icon] is read live every frame. */
    inner class Item internal constructor(
        val id: String,
        val title: String,
        val icon: BrassIcons.Icon,
        internal val page: BrassPages.Page?,
        val meta: (() -> String?)?,
        /** The count reads as "needs you" (accent) rather than plain status (dark). */
        val accent: Boolean,
        val progress: (() -> Float?)?,
        hidden: Boolean,
        internal val onClick: (() -> Unit)?,
    ) {
        var revealed: Boolean = !hidden
            internal set

        /** Shows a NEW dot until the row is next selected. */
        var fresh: Boolean = false

        /** Tooltip body in the folded sidebar (defaults to the count). */
        var detail: (() -> String?)? = null

        internal var grow = if (hidden) 0f else 1f
        internal var glow = 0f
        internal lateinit var row: Row
    }

    /** Fired when a row is chosen (by click, [select] or a page change). */
    var onSelect: ((Item) -> Unit)? = null

    /** The glow a revealed row arrives with. */
    var revealColor: () -> Color = { Colors.mix(Colors.WARN, Colors.UI_TEXT_HOVER, 0.15f) }

    /** Fold to icons below this width. */
    var compactBelow = 60f

    private val parts = ArrayList<Any>()
    // No reserved scrollbar gutter: a sidebar rarely scrolls, and folded to icons the gutter was a third
    // of its width - empty space between the icons and the page.
    private val list = BrassScrollArea(gap = 1f, bleed = false, reserveBar = false)
    private val footerBox = UIContainer()
    private var footerH = 0f
    private var dirty = true
    private var selectedId: String? = null

    val items: List<Item> get() = parts.filterIsInstance<Item>()

    init {
        footerBox.constrain {
            x = 0.pixels(); y = 0.pixels(alignOpposite = true); width = 100.percent()
            height = basicHeightConstraint { footerH }
        } childOf this
        list.constrain {
            x = 0.pixels(); y = 0.pixels(); width = 100.percent()
            height = basicHeightConstraint { (this@BrassSidebar.getHeight() - (if (footerH > 0f) footerH + BrassSpacing.GAP else 0f)).coerceAtLeast(0f) }
        } childOf this
        pages?.selection?.onChange { index ->
            val item = items.firstOrNull { it.page != null && pages.pages.indexOf(it.page) == index } ?: return@onChange
            mark(item)
        }
    }

    /** A group caption (just a hairline when folded). */
    fun header(title: String): BrassSidebar = apply { parts += title; dirty = true }

    /**
     * A row. With a [page] (of this sidebar's [BrassPages]), clicking slides it in; [onClick] runs either
     * way. [meta] is the count on the right, [progress] (0..1) a hairline under the name.
     */
    @JvmOverloads
    fun item(
        id: String,
        title: String,
        icon: BrassIcons.Icon,
        page: BrassPages.Page? = null,
        meta: (() -> String?)? = null,
        accent: Boolean = true,
        progress: (() -> Float?)? = null,
        hidden: Boolean = false,
        onClick: (() -> Unit)? = null,
    ): BrassSidebar = apply {
        val item = Item(id, title, icon, page, meta, accent, progress, hidden, onClick)
        item.row = Row(item)
        parts += item
        dirty = true
    }

    fun item(id: String): Item? = items.firstOrNull { it.id == id }

    /** Something pinned under the rows, [height] tall. */
    fun footer(component: UIComponent, height: Float): BrassSidebar = apply {
        footerH = height
        component.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); this.height = 100.percent() } childOf footerBox
    }

    /** Bring a hidden row in: it grows into place with a glow, shows a NEW dot, and (with [open]) is selected. */
    @JvmOverloads
    fun reveal(id: String, open: Boolean = true) {
        val item = item(id) ?: return
        if (item.revealed) return
        item.revealed = true
        item.fresh = true
        item.glow = 1f
        if (open) select(id)
    }

    /** Choose row [id] as the player would (its page slides in). */
    fun select(id: String) {
        val item = item(id)?.takeIf { it.revealed } ?: return
        choose(item)
    }

    val selected: Item? get() = selectedId?.let { item(it) }

    private fun choose(item: Item) {
        val page = item.page
        if (page != null && pages != null) pages.pick(pages.pages.indexOf(page))
        mark(item)
        item.onClick?.invoke()
    }

    private fun mark(item: Item) {
        if (selectedId == item.id && !item.fresh) return
        selectedId = item.id
        item.fresh = false
        items.forEach { it.row.selected = it === item }
        onSelect?.invoke(item)
    }

    private fun compact(): Boolean = getWidth() < compactBelow

    override fun draw(matrixStack: UMatrixStack) {
        // A hidden row is 0 tall - and a 0-tall widget never draws - so the reveal clocks run here.
        val dt = if (BrassClock.driven) BrassClock.dt else 0.016f
        for (item in items) {
            if (item.revealed && item.grow < 1f) item.grow = (item.grow + dt / GROW_TIME).coerceAtMost(1f)
            if (item.glow > 0f && item.grow >= 1f) item.glow = (item.glow - dt / GLOW_TIME).coerceAtLeast(0f)
        }
        if (dirty) rebuild()
        super.draw(matrixStack)
    }

    private fun rebuild() {
        dirty = false
        list.clear()
        for (p in parts) when (p) {
            is String -> list.add(Header(p))
            is Item -> list.add(p.row)
        }
        val current = pages?.current
        items.forEach { it.row.selected = (current != null && it.page === current) || it.id == selectedId }
        if (selectedId == null) items.firstOrNull { it.row.selected }?.let { selectedId = it.id }
    }

    private inner class Header(val title: String) : BrassWidget(BrassAccent.DEFAULT) {
        init {
            chrome = BrassChrome.NONE
            entranceEnabled = false
            constrain { height = HEADER_H.pixels() }
        }

        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
            val fx = x.toFloat(); val fy = y.toFloat()
            if (compact()) {
                BrassPaint.rect(m, fx + 4f, fy + h - 4f, fx + w - 4f, fy + h - 3f, Colors.UI_INNER_BORDER)
                return
            }
            BrassFont.draw(m, this, BrassFont.fit(this, title.uppercase(), w - 8f), fx + 4f, fy + h - BrassFont.LINE - 1f, Colors.UI_TEXT_DARK)
        }
    }

    internal inner class Row(val item: Item) : BrassWidget(BrassAccent.DEFAULT) {
        init {
            chrome = BrassChrome.NONE
            clickable = true
            selectable = true
            entranceEnabled = false
            constrain { height = basicHeightConstraint { floor(ROW_H * BrassMotion.spring(item.grow)) } }
            onMouseClick { ev -> if (ev.mouseButton == 0 && item.revealed) choose(item) }
            BrassTooltip.attachLazy(this, { if (compact() && item.revealed) item.title else "" }, { if (compact()) (item.detail ?: item.meta)?.invoke() else null })
        }

        override fun isPointInside(x: Float, y: Float): Boolean = item.revealed && super.isPointInside(x, y)

        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
            if (h < 2) return
            val fx = x.toFloat(); val fy = y.toFloat(); val fw = w.toFloat(); val fh = h.toFloat()
            val a = (fh / ROW_H).coerceIn(0f, 1f) * entranceFade
            val hot = hoveredState && active
            when {
                selected -> {
                    BrassPaint.rect(m, fx, fy, fx + fw, fy + fh, withAlpha(Colors.UI_INNER_BG_SELECTED, a))
                    BrassPaint.rect(m, fx, fy, fx + 2f, fy + fh, withAlpha(Colors.UI_ACCENT, a))
                }
                hot -> BrassPaint.rect(m, fx, fy, fx + fw, fy + fh, withAlpha(Colors.ROW_HOVER, a))
            }
            if (item.glow > 0f) {
                val pulse = 0.55f + 0.45f * sin((1f - item.glow) * 16f)
                BrassPaint.border(m, fx, fy, fx + fw, fy + fh, withAlpha(revealColor(), item.glow * pulse))
            }
            val iconCol = when {
                item.glow > 0.05f -> revealColor()
                selected -> Colors.UI_ACCENT_BRIGHT
                else -> Colors.UI_TEXT_DARK
            }
            if (compact()) {
                BrassIcons.draw(m, item.icon, fx + floor((fw - 8f) / 2f), fy + floor((fh - 8f) / 2f), 8f, withAlpha(iconCol, a))
                if (item.fresh) dot(m, fx + fw - 6f, fy + 3f, a)
                return
            }
            val ty = fy + floor((fh - BrassFont.LINE) / 2f) + 1f
            BrassIcons.draw(m, item.icon, fx + 6f, fy + floor((fh - 8f) / 2f), 8f, withAlpha(iconCol, a))
            val textX = fx + TEXT_X
            val meta = item.meta?.invoke()
            val mw = meta?.let { BrassFont.width(this, it) } ?: 0f
            val room = fx + fw - 6f - (if (mw > 0f) mw + 6f else 0f) - textX - (if (item.fresh) 7f else 0f)
            val title = BrassFont.fit(this, item.title, room)
            val titleCol = when {
                selected -> Colors.UI_ACCENT_BRIGHT
                hot -> Colors.UI_TEXT_HOVER
                else -> Colors.UI_TEXT
            }
            BrassFont.draw(m, this, title, textX, ty, withAlpha(titleCol, a))
            if (item.fresh) dot(m, textX + BrassFont.width(this, title) + 3f, ty, a)
            meta?.let { BrassFont.draw(m, this, it, fx + fw - 6f - mw, ty, withAlpha(if (item.accent) Colors.UI_ACCENT else Colors.UI_TEXT_DARK, a)) }
            item.progress?.invoke()?.let { f ->
                val by = fy + fh - 3f
                val bx2 = fx + fw - 6f
                BrassPaint.rect(m, textX, by, bx2, by + 1f, withAlpha(Colors.UI_INNER_BORDER, a))
                val k = f.coerceIn(0f, 1f)
                if (k > 0f) BrassPaint.rect(m, textX, by, textX + (bx2 - textX) * k, by + 1f, withAlpha(Colors.UI_ACCENT, a))
            }
        }

        private fun dot(m: UMatrixStack, x: Float, y: Float, a: Float) {
            BrassPaint.rect(m, x - 1f, y - 1f, x + 4f, y + 4f, withAlpha(Colors.UI_OUTER_BORDER, a))
            BrassPaint.rect(m, x, y, x + 3f, y + 3f, withAlpha(revealColor(), a))
        }
    }

    companion object : BrassDemoSource {
        const val ROW_H = 20f
        const val HEADER_H = 16f
        private const val TEXT_X = 18f
        private const val GROW_TIME = 0.5f
        private const val GLOW_TIME = 2.2f

        override fun demo() = BrassDemo("sidebar", "Sidebar", 140f, 170f) {
            BrassSidebar()
                .item("home", "Overview", BrassIcons.HOME, meta = { "2" })
                .header("Inbox")
                .item("mail", "Messages", BrassIcons.MAIL, meta = { "5" })
                .header("Areas")
                .item("farm", "Farming", BrassIcons.LEAF, meta = { "3/6" }, accent = false, progress = { 0.5f })
                .item("forge", "Production", BrassIcons.HAMMER, meta = { "1/6" }, accent = false, progress = { 0.17f })
                .item("lore", "World Story", BrassIcons.BOOK, meta = { "0/3" }, accent = false, progress = { 0f }, hidden = true)
                .also { bar -> bar.select("farm"); bar.reveal("lore", open = false) }
        }
    }
}
