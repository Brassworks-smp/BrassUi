package net.swzo.brass.ui.kit.quest

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.basicYConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.input.BrassDropdown
import net.swzo.brass.ui.kit.layout.BrassFlow
import net.swzo.brass.ui.kit.layout.BrassPages
import net.swzo.brass.ui.kit.layout.BrassScrollArea
import net.swzo.brass.ui.kit.layout.BrassMasterDetail
import net.swzo.brass.ui.kit.layout.BrassSidebar
import net.swzo.brass.ui.kit.layout.BrassSectionHeader
import net.swzo.brass.ui.kit.layout.BrassSpacing
import net.swzo.brass.ui.kit.layout.BrassVBox
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.surface.BrassEmptyState
import net.swzo.brass.ui.kit.text.BrassFont
import kotlin.math.floor

/**
 * A whole quest book in one widget, laid out as **sidebar | page | detail card**:
 *
 * - The **sidebar** is the only navigation: *Overview* on top, then a MISSIONS group (boards of tasks from
 *   NPCs, players, the server) and a QUEST LINES group (each a [BrassQuestTree]: Getting Started,
 *   Production, Farming...). Rows show what matters at a glance - open missions, done/total and a
 *   progress hairline per line. Narrow journals fold it to icons (names in tooltips).
 * - The **page** slides up or down as you move through the sidebar: the Overview (ready to claim, tracked
 *   quests, a progress tile per line), a mission board grouped Ready / In progress / Available / Completed
 *   (one source dropdown, no filter rows), or a quest line's tree.
 * - The **card** on the right shows the selected quest; it stays put and glides from quest to quest, and
 *   follows a tree's prerequisite locks. Every page remembers what was picked on it.
 * - A line can start **hidden** and arrive later with [reveal]: its row grows into the sidebar with a gold
 *   glow, keeps a NEW dot until visited, and [onReveal] fires (play the sound, tell the website).
 *
 * Give it the whole body of a screen:
 * ```
 * val book = BrassQuestJournal()
 *     .missions("missions", "Missions") { server.missions() }
 *     .questLine("start", "Getting Started", BrassIcons.HOME) { tree -> tree.node(chop).node(craft, listOf("chop")) }
 *     .questLine("story", "World Story", BrassIcons.BOOK, hidden = true) { tree -> ... }
 * book.onReveal = { id -> playSound("ui.reveal") }
 * book.reveal("story")     // when the first artifact is found
 * ```
 */
class BrassQuestJournal @JvmOverloads constructor(
    /** The detail card's width (never more than 40% of the journal). */
    var cardWidth: Float = 210f,
    /** Start on an Overview page (ready to claim, tracked, progress per line). */
    overview: Boolean = true,
    private val missionsLabel: String = "Missions",
    private val linesLabel: String = "Quest lines",
) : UIContainer() {

    /** The shared detail card - restyle it freely (`onAction`, `actionsFor`, `show*` flags...). */
    val card = BrassQuestCard(BrassQuest("", "")).also { it.collapsible = false }

    /** A quest was selected (anywhere). */
    var onSelect: ((BrassQuest) -> Unit)? = null

    /** A hidden line just appeared ([reveal]) - play a sound, notify the website... */
    var onReveal: ((String) -> Unit)? = null

    /** The open page changed (its id; "overview" for the Overview). */
    var onPage: ((String) -> Unit)? = null

    private enum class Kind { OVERVIEW, BOARD, LINE }

    private inner class Entry(val id: String, val title: String, val icon: BrassIcons.Icon, val kind: Kind, var revealed: Boolean) {
        var tree: BrassQuestTree? = null
        var board: Board? = null
        var picked: BrassQuest? = null
        lateinit var page: BrassPages.Page

        fun quests(): List<BrassQuest> = tree?.quests() ?: board?.source?.invoke() ?: emptyList()
    }

    private val entries = ArrayList<Entry>()
    private val pages = BrassPages(lazy = true, slide = BrassPages.Slide.VERTICAL)
    private val side = BrassSidebar(pages).also {
        it.revealColor = { BrassQuestCard.gold() }
        it.compactBelow = SIDE_COMPACT * 2f
    }
    private var lastKind: Kind? = null
    private val pane: BrassScrollArea
    private var hasCard = false
    private var overviewDirty = true
    private val hooked = HashSet<BrassQuest>()
    private var overviewBody: BrassVBox? = null
    private val overviewRows = ArrayList<BrassQuestCard>()
    private var overviewShape = 0

    /**
     * Page | card, adapting like a phone app: side by side while there is room, and one column when the
     * page would get narrower than [MIN_PAGE] - then a player's pick pushes the card over the page with a
     * back key ([BrassMasterDetail]). Placed right of the sidebar and no wider, so it never sits over it.
     */
    private val md = BrassMasterDetail(detailWidth = { minOf(cardWidth, getWidth() * 0.4f) }, minMaster = MIN_PAGE)

    /** Whether a quest's card is pushed over the page (only ever in the one-column layout). */
    val detailShown: Boolean get() = md.detailShown

    /** One column: slide the pushed card back out to the page (the back key and Escape do this). */
    fun closeDetail() = md.close()

    init {
        side.constrain {
            x = 0.pixels(); y = 0.pixels()
            width = basicWidthConstraint { sideW() }
            height = 100.percent()
        } childOf this
        md.constrain {
            x = basicXConstraint { side.getRight() + sideGap() }; y = 0.pixels()
            width = basicWidthConstraint { (this@BrassQuestJournal.getWidth() - sideW() - sideGap()).coerceAtLeast(0f) }
            height = 100.percent()
        } childOf this
        md.backLabel = { entries.getOrNull(pages.selection.value)?.title ?: "Back" }
        pages.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf md.master
        // Lifted by the scroll content's 1-px keycap inset, so the card's top lines up with the page's.
        pane = BrassScrollArea(gap = 0f, bleed = false).constrain {
            x = 0.pixels(); y = (-BrassWidget.BLEED_TOP).pixels()
            width = 100.percent()
            height = basicHeightConstraint { c -> c.parent.getHeight() + BrassWidget.BLEED_TOP }
        } childOf md.detail
        pane.add(card)
        card.fillHeight = { pane.getHeight() - BrassWidget.BLEED_TOP }

        pages.selection.onChange { index ->
            val e = entries.getOrNull(index) ?: return@onChange
            // a new page starts on its list, not on the last quest's pushed card
            md.reset()
            (e.picked ?: firstOf(e))?.let { show(e, it, notify = false) }
            if (e.kind == Kind.OVERVIEW) overviewDirty = true
            onPage?.invoke(e.id)
        }
        if (overview) add(Entry(OVERVIEW, "Overview", BrassIcons.HOME, Kind.OVERVIEW, true).also { e ->
            e.page = pages.page(e.title) { root -> buildOverview(root) }
        })
    }

    private fun compact(): Boolean = getWidth() < COMPACT_BELOW

    private fun sideW(): Float = if (compact()) SIDE_COMPACT else (getWidth() * 0.24f).coerceIn(SIDE_MIN, SIDE_MAX)

    /** Folded to icons, the sidebar sits closer to the page. */
    private fun sideGap(): Float = if (compact()) BrassSpacing.TIGHT else BrassSpacing.GAP

    private fun firstOf(e: Entry): BrassQuest? = when (e.kind) {
        Kind.LINE -> e.tree?.let { t -> t.selectedId?.let { t.quest(it) } } ?: e.quests().firstOrNull()
        Kind.BOARD -> e.board?.shown?.firstOrNull()
        Kind.OVERVIEW -> readyQuests().firstOrNull() ?: trackedQuests().firstOrNull()
    }

    /** A quest line: a sidebar row opening a [BrassQuestTree] that [build] fills. */
    @JvmOverloads
    fun questLine(id: String, title: String, icon: BrassIcons.Icon = BrassIcons.BOOK, hidden: Boolean = false, build: (BrassQuestTree) -> Unit): BrassQuestJournal = apply {
        val e = Entry(id, title, icon, Kind.LINE, !hidden)
        val tree = BrassQuestTree()
        build(tree)
        tree.onSelect = { q -> show(e, q) }
        e.tree = tree
        e.page = pages.page(title) { root ->
            tree.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf root
        }
        add(e)
    }

    /**
     * A mission board: a sidebar row opening [quests] (asked again on every rebuild, so it can be live),
     * grouped Ready to claim / In progress / Available / Completed, with a source dropdown built from the
     * quests' [BrassQuest.source]s (plus Seasonal when any quest has a season).
     */
    @JvmOverloads
    fun missions(id: String, title: String, icon: BrassIcons.Icon = BrassIcons.SCROLL, hidden: Boolean = false, quests: () -> List<BrassQuest>): BrassQuestJournal = apply {
        val e = Entry(id, title, icon, Kind.BOARD, !hidden)
        val board = Board(e, quests)
        e.board = board
        e.page = pages.page(title) { root -> board.build(root) }
        add(e)
    }

    /** Something pinned under the sidebar (a "track all" key, a debug trigger...), [height] tall. */
    fun sidebarFooter(component: UIComponent, height: Float): BrassQuestJournal = apply { side.footer(component, height) }

    private fun add(e: Entry) {
        entries += e
        if (e.kind != lastKind && e.kind != Kind.OVERVIEW) side.header(if (e.kind == Kind.BOARD) missionsLabel else linesLabel)
        lastKind = e.kind
        // what matters at a glance: ready to claim, open missions, a line's done/total and progress
        when (e.kind) {
            Kind.OVERVIEW -> side.item(e.id, e.title, e.icon, e.page, meta = { readyQuests().size.takeIf { it > 0 }?.toString() })
                .item(e.id)?.detail = { readyQuests().size.takeIf { it > 0 }?.let { "$it ready to claim" } }
            Kind.BOARD -> side.item(e.id, e.title, e.icon, e.page, hidden = !e.revealed, meta = { openCount(e).takeIf { it > 0 }?.toString() })
                .item(e.id)?.detail = { openCount(e).takeIf { it > 0 }?.let { "$it open" } }
            Kind.LINE -> side.item(e.id, e.title, e.icon, e.page, hidden = !e.revealed, accent = false,
                meta = { counts(e).let { (d, t) -> "$d/$t" } },
                progress = { counts(e).let { (d, t) -> if (t == 0) 0f else d.toFloat() / t } })
                .item(e.id)?.detail = { counts(e).let { (d, t) -> "$d of $t done" } }
        }
        if (pages.selection.value < 0 && e.revealed) pages.select(entries.indexOf(e))
    }

    private fun openCount(e: Entry) = e.quests().count { it.status == BrassQuest.Status.ACTIVE || it.status == BrassQuest.Status.READY }

    private fun counts(e: Entry): Pair<Int, Int> = e.quests().let { qs -> qs.count { it.status == BrassQuest.Status.CLAIMED } to qs.size }

    /** Show a hidden line: its row grows in with a glow, keeps a NEW dot until visited, opens (with [open]) and [onReveal] fires. */
    @JvmOverloads
    fun reveal(id: String, open: Boolean = true) {
        val e = entries.firstOrNull { it.id == id } ?: return
        if (e.revealed) return
        e.revealed = true
        overviewDirty = true
        side.reveal(id, open)
        onReveal?.invoke(id)
    }

    fun isRevealed(id: String): Boolean = entries.firstOrNull { it.id == id }?.revealed == true

    /** Open page [id] ("overview", a board or a line), sliding like a sidebar click. */
    fun open(id: String) {
        side.select(id)
    }

    /** The tree of quest line [id]. */
    fun tree(id: String): BrassQuestTree? = entries.firstOrNull { it.id == id }?.tree

    /** Rebuild boards and the overview (after adding or removing quests; status changes rebuild on their own). */
    fun refresh() {
        entries.forEach { it.board?.dirty = true }
        overviewDirty = true
    }

    /** Open the page that holds [quest] and show it (a tree also selects its node). */
    fun showQuest(quest: BrassQuest) {
        val e = owner(quest) ?: return
        e.tree?.select(quest.id)
        if (pages.current !== e.page) side.select(e.id)
        show(e, quest)
    }

    private fun owner(q: BrassQuest): Entry? = entries.firstOrNull { it.kind != Kind.OVERVIEW && it.revealed && q in it.quests() }

    private fun show(e: Entry, quest: BrassQuest, notify: Boolean = true) {
        e.picked = quest
        val home = if (e.kind == Kind.OVERVIEW) owner(quest) else e
        card.lockOf = home?.tree?.let { t -> t::lockReasonOf }
        card.quest = quest
        hasCard = true
        e.board?.mark(quest)
        if (e.kind == Kind.OVERVIEW) overviewRows.forEach { it.selected = it.quest === quest }
        if (notify) {
            // a pick by the player opens the card; restoring a page's last pick does not
            md.open()
            onSelect?.invoke(quest)
        }
    }

    private fun allQuests(): List<BrassQuest> = entries.filter { it.kind != Kind.OVERVIEW && it.revealed }.flatMap { it.quests() }.distinct()

    private fun readyQuests() = allQuests().filter { it.effectiveStatus(System.currentTimeMillis()) == BrassQuest.Status.READY }

    private fun trackedQuests() = allQuests().filter { it.tracked && it.status == BrassQuest.Status.ACTIVE }

    override fun draw(matrixStack: UMatrixStack) {
        // Children change here, never from inside a click's dispatch (reveal() may come from one).
        val all = allQuests()
        for (q in all) if (hooked.add(q)) q.onStatus { _, _, _ -> overviewDirty = true; entries.forEach { it.board?.dirty = true } }
        // Tracking (and anything set straight on a quest's fields) fires no hook, so watch what the
        // Overview shows: a quest untracked from the card used to linger under Tracking.
        val now = System.currentTimeMillis()
        var shape = all.size
        for (q in all) shape = shape * 31 + (if (q.tracked) 1 else 0) + q.effectiveStatus(now).ordinal * 2
        if (shape != overviewShape) { overviewShape = shape; overviewDirty = true }
        entries.forEach { e -> e.board?.let { b -> if (b.dirty && b.built) b.rebuild() } }
        if (overviewDirty && overviewBody != null) rebuildOverview()
        if (!hasCard) entries.getOrNull(pages.selection.value)?.let { e -> firstOf(e)?.let { show(e, it, notify = false) } }
        super.draw(matrixStack)
    }

    // ---- overview -----------------------------------------------------------------------------------

    private fun buildOverview(root: BrassPages.Page) {
        val scroll = BrassScrollArea(gap = 0f).constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf root
        val body = BrassVBox(gap = 0f, bleed = false)
        scroll.add(body)
        overviewBody = body
        overviewDirty = true
    }

    private fun rebuildOverview() {
        overviewDirty = false
        val body = overviewBody ?: return
        body.clearChildren()
        overviewRows.clear()
        val ready = readyQuests()
        val tracked = trackedQuests().filter { it !in ready }
        val overview = entries.firstOrNull { it.kind == Kind.OVERVIEW }
        fun row(q: BrassQuest, style: BrassQuestCard.Style): BrassQuestCard =
            BrassQuestCard(q, style) { r -> overview?.let { show(it, r.quest) } }.also { c ->
                c.entranceEnabled = false
                c.selected = overview?.picked === q
                c.constrain { width = 100.percent() }
                overviewRows += c
            }
        fun gap(h: Float) = UIContainer().constrain { width = 100.percent(); height = h.pixels() }
        fun header(title: String, count: Int? = null) = BrassSectionHeader(title, count?.toString()).also { it.constrain { width = 100.percent() } }
        if (ready.isNotEmpty()) {
            body.add(header("Ready to claim", ready.size), gap(4f))
            ready.forEach { body.add(row(it, BrassQuestCard.Style.COMPACT), gap(1f)) }
            body.add(gap(BrassSpacing.SECTION_GAP))
        }
        if (tracked.isNotEmpty()) {
            body.add(header("Tracking", tracked.size), gap(4f))
            tracked.forEach { body.add(row(it, BrassQuestCard.Style.TRACKER), gap(BrassSpacing.TIGHT)) }
            body.add(gap(BrassSpacing.SECTION_GAP - BrassSpacing.TIGHT))
        }
        val lines = entries.filter { it.kind == Kind.LINE && it.revealed }
        if (lines.isNotEmpty()) {
            body.add(header("Quest lines"), gap(4f))
            val flow = BrassFlow(gapX = BrassSpacing.TIGHT, gapY = BrassSpacing.TIGHT, itemHeight = TILE_H, stretch = true)
            lines.forEach { flow.add(LineTile(it), TILE_MIN) }
            flow.constrain { width = 100.percent(); height = basicHeightConstraint { flow.contentHeight() } }
            body.add(flow)
        }
        if (ready.isEmpty() && tracked.isEmpty() && lines.isEmpty()) {
            body.add(BrassEmptyState(BrassIcons.SCROLL, "No quests yet", "New quests show up here").also {
                it.chrome = BrassChrome.NONE
                it.constrain { width = 100.percent(); height = 90.pixels() }
            })
        }
    }

    /** An Overview tile for one quest line: icon, name, done/total, a progress bar; click to open it. */
    private inner class LineTile(val e: Entry) : BrassWidget(BrassAccent.DEFAULT) {
        init {
            chrome = BrassChrome.NONE
            clickable = true
            onMouseClick { ev -> if (ev.mouseButton == 0) side.select(e.id) }
        }

        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
            val fx = x.toFloat(); val fy = y.toFloat(); val fw = w.toFloat(); val fh = h.toFloat()
            val hot = hoveredState
            val qs = e.quests()
            val done = qs.count { it.status == BrassQuest.Status.CLAIMED }
            val f = if (qs.isEmpty()) 0f else done.toFloat() / qs.size
            val complete = qs.isNotEmpty() && done == qs.size
            BrassCard.panel(m, fx, fy, fx + fw, fy + fh,
                fill = if (hot) Colors.UI_ELEMENT_BG_HOVER else Colors.UI_ELEMENT_BG,
                border = when {
                    complete -> BrassQuestCard.gold()
                    hot -> Colors.UI_ELEMENT_BORDER_HOVER
                    else -> Colors.UI_ELEMENT_BORDER
                }, alpha = entranceFade)
            BrassIcons.draw(m, e.icon, fx + 6f, fy + 6f, 8f, withAlpha(if (complete) BrassQuestCard.gold() else Colors.UI_ACCENT, entranceFade))
            val count = "$done/${qs.size}"
            val cw = BrassFont.width(this, count)
            BrassFont.draw(m, this, BrassFont.fit(this, e.title, fw - 30f - cw), fx + 18f, fy + 7f, withAlpha(if (hot) Colors.UI_TEXT_HOVER else Colors.UI_TEXT, entranceFade))
            BrassFont.draw(m, this, count, fx + fw - 6f - cw, fy + 7f, withAlpha(Colors.UI_TEXT_DARK, entranceFade))
            BrassCard.filledTrack(m, fx + 6f, fy + fh - 9f, fx + fw - 6f, fy + fh - 5f, f)
        }
    }

    // ---- mission boards -------------------------------------------------------------------------

    private inner class Board(val e: Entry, val source: () -> List<BrassQuest>) {
        var dirty = true
        var built = false
        var shown: List<BrassQuest> = emptyList()
        private var sourceFilter = ANY
        private lateinit var list: BrassScrollArea
        private lateinit var state: BrassPages
        private val rows = ArrayList<BrassQuestCard>()
        private lateinit var dropdownHolder: UIContainer
        private var sourcesShown: List<String>? = null
        private var toolbarH = 0f

        private fun top(): Float = if (toolbarH > 0f) toolbarH + BrassSpacing.GAP else 0f

        fun build(root: BrassPages.Page) {
            state = BrassPages(lazy = false)
            state.constrain {
                x = 0.pixels(); y = basicYConstraint { c -> c.parent.getTop() + top() }
                width = 100.percent()
                height = basicHeightConstraint { c -> c.parent.getHeight() - top() }
            } childOf root
            state.page("list") { page ->
                list = BrassScrollArea.list().constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf page
            }
            state.page("empty") { page ->
                BrassEmptyState(BrassIcons.SCROLL, "No missions", "New tasks show up here")
                    .constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf page
            }
            // The toolbar goes in AFTER the list: the source menu opens down over it, and Elementa draws
            // and hit-tests later children on top - added first, the menu was under the rows and dead.
            BoardTitle().constrain {
                x = 0.pixels(); y = 0.pixels()
                width = basicWidthConstraint { c -> (c.parent.getWidth() - (if (toolbarH > 0f) DROPDOWN_W + BrassSpacing.GAP else 0f)).coerceAtLeast(0f) }
                height = basicHeightConstraint { toolbarH }
            } childOf root
            dropdownHolder = object : UIContainer() {
                // A click only reaches children inside their parent's bounds; the open menu hangs below
                // this one-row holder, so let the holder answer for wherever its dropdown reaches.
                override fun isPointInside(x: Float, y: Float): Boolean =
                    super.isPointInside(x, y) || children.any { it.isPointInside(x, y) }
            }.constrain {
                x = 0.pixels(alignOpposite = true); y = 0.pixels()
                width = basicWidthConstraint { c -> minOf(DROPDOWN_W, c.parent.getWidth()) }
                height = basicHeightConstraint { toolbarH }
            } childOf root
            built = true
            rebuild()
        }

        fun rebuild() {
            dirty = false
            val all = source()
            val sources = buildList {
                addAll(all.mapNotNull { it.source }.distinct())
                if (all.any { it.season != null }) add(SEASONAL)
            }
            if (sources != sourcesShown) {
                sourcesShown = sources
                dropdownHolder.clearChildren()
                if (sourceFilter != ANY && sourceFilter !in sources) sourceFilter = ANY
                // a dropdown only when there is something to choose between
                toolbarH = if (sources.size > 1) TOOLBAR_H else 0f
                if (toolbarH > 0f) {
                    BrassDropdown(listOf(ANY to "Every source") + sources.map { it to it }, sourceFilter) { id -> sourceFilter = id; dirty = true }
                        .constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf dropdownHolder
                }
            }
            val now = System.currentTimeMillis()
            val filtered = all.filter { q ->
                when (sourceFilter) {
                    ANY -> true
                    SEASONAL -> q.season != null
                    else -> q.source == sourceFilter
                }
            }
            // Grouped by what the player can do about them, most urgent first.
            val groups = listOf<Pair<String, (BrassQuest.Status) -> Boolean>>(
                "Ready to claim" to { s -> s == BrassQuest.Status.READY },
                "In progress" to { s -> s == BrassQuest.Status.ACTIVE || s == BrassQuest.Status.FAILED },
                "Available" to { s -> s == BrassQuest.Status.AVAILABLE || s == BrassQuest.Status.LOCKED },
                "Completed" to { s -> s == BrassQuest.Status.CLAIMED },
            ).map { (title, test) -> title to filtered.filter { test(it.effectiveStatus(now)) } }.filter { it.second.isNotEmpty() }
            shown = groups.flatMap { it.second }
            list.clear()
            rows.clear()
            groups.forEachIndexed { gi, (title, qs) ->
                if (gi > 0) list.add(UIContainer().constrain { width = 100.percent(); height = (BrassSpacing.SECTION_GAP - 2f).pixels() })
                list.add(BrassSectionHeader(title, qs.size.toString()))
                list.add(UIContainer().constrain { width = 100.percent(); height = 3.pixels() })
                for (q in qs) {
                    val row = BrassQuestCard(q, BrassQuestCard.Style.COMPACT) { r -> show(e, r.quest) }
                    row.entranceEnabled = false
                    rows += row
                    list.add(row)
                }
            }
            state.select(if (shown.isEmpty()) 1 else 0)
            e.picked?.let { mark(it) }
            if (e.picked == null || e.picked !in shown) shown.firstOrNull()?.let { first ->
                if (pages.current === e.page) show(e, first, notify = false) else e.picked = first
            }
        }

        fun mark(q: BrassQuest) = rows.forEach { it.selected = it.quest === q }

        /** The board's name and how many of its missions are open, beside the source dropdown. */
        private inner class BoardTitle : BrassWidget(BrassAccent.DEFAULT) {
            init { chrome = BrassChrome.NONE; entranceEnabled = false }

            override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
                if (h < 2) return
                val ty = y + floor((h - BrassFont.LINE) / 2f) + 1f
                val title = BrassFont.fit(this, e.title, w.toFloat())
                BrassFont.draw(m, this, title, x.toFloat(), ty, Colors.UI_TEXT_HOVER)
                val open = shown.count { it.status == BrassQuest.Status.ACTIVE || it.status == BrassQuest.Status.READY }
                val note = "$open open"
                val nx = x + BrassFont.width(this, title) + 6f
                if (nx + BrassFont.width(this, note) <= x + w) BrassFont.draw(m, this, note, nx, ty, Colors.UI_TEXT_DARK)
            }
        }
    }

    companion object {
        private const val OVERVIEW = "overview"
        private const val SIDE_MIN = 104f
        private const val SIDE_MAX = 140f
        private const val SIDE_COMPACT = 24f
        private const val MIN_PAGE = 150f
        private const val COMPACT_BELOW = 440f
        private const val TOOLBAR_H = 18f
        private const val DROPDOWN_W = 110f
        private const val TILE_MIN = 110f
        private const val TILE_H = 30f
        private const val ANY = "*"
        private const val SEASONAL = "Seasonal"
    }
}
