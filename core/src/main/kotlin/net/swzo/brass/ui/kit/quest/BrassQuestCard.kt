package net.swzo.brass.ui.kit.quest

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.Window
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
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.base.BrassMotion
import net.swzo.brass.ui.kit.base.BrassEased
import net.swzo.brass.ui.kit.base.BrassFocusable
import net.swzo.brass.ui.kit.base.BrassInert
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.layout.BrassSpacing
import net.swzo.brass.ui.kit.layout.BrassVBox
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.platform.BrassCursor
import net.swzo.brass.ui.kit.platform.BrassPlatform
import net.swzo.brass.ui.kit.surface.BrassModal
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassTag
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.text.BrassWrappedLabel
import java.awt.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * A quest or mission drawn from a live [BrassQuest], in one of three [Style]s:
 * - [Style.FULL] - the quest card: icon, title, giver, status, deadline, summary, objective checklist
 *   with progress, reward slots (hover for names) and the status's actions (Accept / Track, Abandon /
 *   Claim / Retry). The header collapses it to a single progress line.
 * - [Style.COMPACT] - a 26-px quest-log row (like `BrassListRow`): selectable, with a mini progress bar.
 *   Put several in `BrassScrollArea.list()`.
 * - [Style.TRACKER] - a dense objective list for a HUD corner or a side pane.
 *
 * Everything is optional (`show*` flags) and recolourable ([statusColor], [statusLabel]); the actions
 * come from [actionsFor] (replace it for custom buttons). By default an action updates the quest
 * itself ([applyLocally]) and then tells [onAction] - a networked host turns [applyLocally] off and
 * sends the action to its server instead. Abandon asks for confirmation ([confirmAbandon]).
 */
class BrassQuestCard @JvmOverloads constructor(
    var quest: BrassQuest,
    var style: Style = Style.FULL,
    private val onClick: ((BrassQuestCard) -> Unit)? = null,
) : BrassWidget(BrassAccent.DEFAULT), BrassFocusable {

    enum class Style { FULL, COMPACT, TRACKER }

    enum class Kind { ACCEPT, TRACK, ABANDON, CLAIM, RETRY, CUSTOM }

    /** One footer key. A non-null [disabledReason] greys it out and becomes its tooltip. */
    class Action @JvmOverloads constructor(
        val label: String,
        val accent: BrassAccent = BrassAccent.DEFAULT,
        val icon: BrassIcons.Icon? = null,
        val kind: Kind = Kind.CUSTOM,
        val tooltip: String? = null,
        val disabledReason: String? = null,
        val selected: Boolean = false,
        val onPress: (() -> Unit)? = null,
    )

    var showSummary = true
    var maxSummaryLines = 3
    var showMeta = true
    var showTimer = true
    var showDifficulty = true
    var showObjectives = true
    var showObjectiveBars = true
    var showRewards = true
    var showAdvancement = true
    var showProgress = true
    var showActions = true

    /** Objectives shown before "+N more" (TRACKER defaults to 4 via [maxObjectivesFor]). FULL: click it to expand. */
    var maxObjectives = Int.MAX_VALUE
    var objectivesExpanded = false

    /**
     * FULL only: the height to fill when the content is shorter (a detail pane) - the footer then sits
     * at the bottom. `{ pane.getHeight() }`, say.
     */
    var fillHeight: (() -> Float)? = null

    /** Milestones only: a slow band of light sweeping across the header every few seconds (off by default). */
    var shimmer = false

    /**
     * FULL only: swapping [quest] glides the new one in (content rises and fades in, the frame stays put);
     * a milestone gets a grander reveal - a gold flash, ornaments drawing in, a sweep of light.
     */
    var animateSwaps = true

    /** FULL only: clicking the header folds the card to its title and progress. */
    var collapsible = true
    var collapsed = false

    var applyLocally = true
    var confirmAbandon = true
    var onAction: ((Kind, BrassQuest) -> Unit)? = null

    var statusColor: (BrassQuest.Status) -> Color = ::defaultStatusColor
    var statusLabel: (BrassQuest.Status) -> String? = ::defaultStatusLabel
    var actionsFor: (BrassQuest, BrassQuest.Status) -> List<Action> = { q, s -> defaultActions(q, s, lockReason()) }

    /**
     * Prerequisites from outside the quest: a non-null answer shows an AVAILABLE quest as LOCKED with
     * that reason. [BrassQuestTree.bind] sets it, so a card beside a tree agrees with it.
     */
    var lockOf: ((BrassQuest) -> String?)? = null

    /** The time deadlines are measured against; replace for a game clock or a fixed screenshot. */
    var clock: () -> Long = System::currentTimeMillis

    private val shownFraction = BrassEased(quest.fraction, speed = 9f)
    private val objectiveEase = HashMap<BrassQuest.Objective, BrassEased>()

    private var actions: List<Action> = emptyList()
    private val keys = List(MAX_KEYS) { ActionKey(it) }
    private val strip = RewardStrip()

    private var geoFrame = -1L
    private var geoWidth = -1f
    private var geo = Geo()

    override val focusable: Boolean get() = onClick != null

    init {
        chrome = BrassChrome.NONE
        clickable = onClick != null
        selectable = true
        constrain { height = basicHeightConstraint { layout().height } }

        strip.constrain {
            x = basicXConstraint { this@BrassQuestCard.getLeft() + PAD }
            y = basicYConstraint { this@BrassQuestCard.getTop() + layout().rewardsY }
            width = basicWidthConstraint { (this@BrassQuestCard.getWidth() - PAD * 2).coerceAtLeast(0f) }
            height = basicHeightConstraint { layout().rewardsH }
        } childOf this
        BrassTooltip.attachLazy(strip, { strip.hovered()?.let { r -> r.itemId?.let { BrassPlatform.current?.itemName(it) } ?: r.label } ?: "" }, { strip.hovered()?.detail })

        keys.forEach { key ->
            key.entranceEnabled = false
            key.constrain {
                x = basicXConstraint { this@BrassQuestCard.getLeft() + layout().keyX.getOrElse(key.slot) { 0f } }
                y = basicYConstraint { this@BrassQuestCard.getTop() + layout().footerY + layout().keyRow.getOrElse(key.slot) { 0f } }
                width = basicWidthConstraint { layout().keyW.getOrElse(key.slot) { 0f } }
                height = KEY_H.pixels()
            } childOf this
            BrassTooltip.attachLazy(
                key,
                { actions.getOrNull(key.slot)?.let { it.disabledReason ?: it.tooltip } ?: "" },
            )
        }

        onMouseClick { e ->
            if (e.mouseButton != 0 || e.target !== this@BrassQuestCard) return@onMouseClick
            if (moreRowAt(e.absoluteX, e.absoluteY) != null) {
                objectivesExpanded = !objectivesExpanded
                return@onMouseClick
            }
            if (style == Style.FULL && collapsible && e.absoluteY - getTop() < layout().headerBottom) {
                collapsed = !collapsed
            } else if (active) {
                onClick?.invoke(this@BrassQuestCard)
            }
        }
    }

    override fun proxyActivate() {
        if (active) onClick?.invoke(this)
    }

    override fun onActivate(): Boolean {
        if (!active || onClick == null) return false
        onClick.invoke(this)
        return true
    }

    /** Run [kind] as if its key were pressed (scripted demos, keyboard shortcuts). */
    fun perform(kind: Kind) {
        actions.firstOrNull { it.kind == kind }?.let { press(it) }
    }

    private fun now(): Long = clock()

    /** The "+N more" / "Show less" row under the pointer, FULL only. */
    private fun moreRowAt(ax: Float, ay: Float): Row? {
        if (style != Style.FULL || collapsed) return null
        val lx = ax - getLeft()
        val ly = ay - getTop()
        if (lx < PAD || lx > getWidth() - PAD) return null
        return layout().rows.firstOrNull { it.objective == null && it.more != 0 && ly >= it.y - 2f && ly < it.y + BrassFont.LINE + 2f }
    }

    private fun status(): BrassQuest.Status {
        val st = quest.effectiveStatus(now())
        return if (st == BrassQuest.Status.AVAILABLE && lockOf?.invoke(quest) != null) BrassQuest.Status.LOCKED else st
    }

    private fun lockReason(): String? = lockOf?.invoke(quest) ?: quest.lockedReason

    private fun maxObjectivesFor(): Int = if (style == Style.TRACKER) minOf(maxObjectives, 4) else maxObjectives

    private fun press(a: Action) {
        if (a.disabledReason != null) return
        if (a.kind == Kind.ABANDON && confirmAbandon) {
            confirm(a)
            return
        }
        apply(a)
    }

    private fun apply(a: Action) {
        val q = quest
        if (applyLocally) when (a.kind) {
            Kind.ACCEPT -> q.status = BrassQuest.Status.ACTIVE
            Kind.TRACK -> q.tracked = !q.tracked
            Kind.ABANDON -> { q.reset(); q.status = BrassQuest.Status.AVAILABLE; q.tracked = false }
            Kind.CLAIM -> { q.status = BrassQuest.Status.CLAIMED; q.tracked = false }
            Kind.RETRY -> { q.reset(); q.expiresAt = null; q.status = BrassQuest.Status.ACTIVE }
            Kind.CUSTOM -> {}
        }
        a.onPress?.invoke()
        onAction?.invoke(a.kind, q)
    }

    private fun confirm(a: Action) {
        val root = runCatching { Window.of(this) }.getOrNull() ?: return apply(a)
        lateinit var modal: BrassModal
        modal = BrassModal("Abandon ${quest.title}?", 220f, 104f)
            .body { b ->
                BrassWrappedLabel("Progress on every objective is lost.", Colors.UI_TEXT)
                    .constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent() } childOf b
            }
            .footer(
                BrassButton("Keep") { modal.dismiss() },
                BrassButton("Abandon", BrassAccent.DANGER) { modal.dismiss(); apply(a) },
            )
        modal.show(root)
    }

    // ---- geometry ------------------------------------------------------------------------------

    private class Row(val y: Float, val objective: BrassQuest.Objective?, val more: Int = 0)

    private class Geo(
        val height: Float = 0f,
        val headerBottom: Float = 0f,
        val dividerY: Float = -1f,
        val summary: List<String> = emptyList(),
        val summaryY: Float = 0f,
        val objectivesY: Float = -1f,
        val rows: List<Row> = emptyList(),
        val rewardsY: Float = 0f,
        val rewardsH: Float = 0f,
        val chips: List<FloatArray> = emptyList(),
        val footerY: Float = 0f,
        val barY: Float = -1f,
        val barRight: Float = 0f,
        val keyX: List<Float> = emptyList(),
        val keyW: List<Float> = emptyList(),
        val barRowY: Float = -1f,
        val advancementY: Float = -1f,
        val keyRow: List<Float> = emptyList(),
    )

    private fun layout(): Geo {
        val w = getWidth()
        if (BrassClock.driven && geoFrame == BrassClock.frame && geoWidth == w) return geo
        geoFrame = BrassClock.frame
        geoWidth = w
        actions = if (showActions && style == Style.FULL && !collapsed) actionsFor(quest, status()).take(MAX_KEYS) else emptyList()
        geo = when (style) {
            Style.FULL -> layoutFull(w)
            Style.COMPACT -> Geo(height = COMPACT_H, headerBottom = COMPACT_H)
            Style.TRACKER -> layoutTracker()
        }
        return geo
    }

    private fun visibleObjectives(): Pair<List<BrassQuest.Objective>, Int> {
        val all = quest.objectives
        val cap = maxObjectivesFor()
        return when {
            all.size <= cap -> all to 0
            // expanded: everything, then a "Show less" row (more = -1)
            style == Style.FULL && objectivesExpanded -> all to -1
            else -> all.take(cap) to all.size - cap
        }
    }

    private fun objectiveRowH(o: BrassQuest.Objective): Float =
        if (showObjectiveBars && o.target > 1 && style == Style.FULL) BrassFont.LINE + BAR_GAP + BAR_H else BrassFont.LINE.toFloat()

    private fun layoutFull(w: Float): Geo {
        val inner = (w - PAD * 2).coerceAtLeast(0f)
        var y = PAD + TILE
        val headerBottom = y + PAD / 2
        if (collapsed) {
            val barY = y + SECTION
            return Geo(height = barY + BAR_H + PAD, headerBottom = headerBottom, barY = barY, barRight = w - PAD)
        }
        y += SECTION
        val dividerY = y
        y += 1f + SECTION

        var summary = emptyList<String>()
        var summaryY = 0f
        val text = quest.summary
        if (showSummary && !text.isNullOrBlank() && maxSummaryLines > 0) {
            val lines = BrassFont.wrap(this, text, inner)
            summary = if (lines.size <= maxSummaryLines) lines else lines.take(maxSummaryLines - 1) +
                BrassFont.fit(this, lines.drop(maxSummaryLines - 1).joinToString(" ") + " ...", inner)
            summaryY = y
            y += summary.size * BrassFont.LINE + SECTION_GAP
        }

        var objectivesY = -1f
        val rows = ArrayList<Row>()
        if (showObjectives && quest.objectives.isNotEmpty()) {
            objectivesY = y
            y += BrassFont.LINE + CAPTION_GAP
            val (shown, more) = visibleObjectives()
            for (o in shown) {
                rows += Row(y, o)
                y += objectiveRowH(o) + ROW_GAP
            }
            if (more != 0) {
                rows += Row(y, null, more)
                y += BrassFont.LINE + ROW_GAP
            }
            y += SECTION_GAP - ROW_GAP
        }

        var advancementY = -1f
        if (showAdvancement && (quest.advancementTitle ?: quest.advancement) != null) {
            y += BrassFont.LINE + CAPTION_GAP
            advancementY = y
            y += CHIP_H + SECTION_GAP
        }

        var rewardsY = 0f
        var rewardsH = 0f
        val chips = ArrayList<FloatArray>()
        if (showRewards && quest.rewards.isNotEmpty()) {
            y += BrassFont.LINE + CAPTION_GAP
            rewardsY = y
            var cx = 0f
            var cy = 0f
            for (r in quest.rewards) {
                val cw = chipWidth(r).coerceAtMost(inner)
                if (cx > 0f && cx + cw > inner) { cx = 0f; cy += CHIP_H + CHIP_GAP }
                chips += floatArrayOf(cx, cy, cw)
                cx += cw + CHIP_GAP
            }
            rewardsH = cy + CHIP_H
            y += rewardsH + SECTION_GAP
        }

        val st = status()
        val wantsBar = showProgress && quest.objectives.isNotEmpty() &&
            (st == BrassQuest.Status.ACTIVE || st == BrassQuest.Status.READY || st == BrassQuest.Status.FAILED)
        var keyW = actions.map { a -> keyWidth(a) }
        val keyX = ArrayList<Float>()
        val keyRow = ArrayList<Float>()
        // Keys sit in one row at the right; when they don't fit (a narrow pane), they stack full width.
        val stacked = keyW.isNotEmpty() && keyW.sum() + KEY_GAP * (keyW.size - 1) > inner
        var keysLeft = w - PAD
        if (stacked) {
            keyW = keyW.map { inner }
            keyW.indices.forEach { i -> keyX += PAD; keyRow += i * (KEY_H + KEY_GAP + BrassWidget.BLEED_BOTTOM) }
            keysLeft = PAD
        } else {
            for (kw in keyW.asReversed()) { keysLeft -= kw; keyX.add(0, keysLeft); keyRow += 0f; keysLeft -= KEY_GAP }
            if (keyW.isNotEmpty()) keysLeft += KEY_GAP
        }
        // Beside the keys when it fits; on its own row above them when the keys crowd it out, so the
        // percentage never disappears (Tracking + Abandon in a narrow pane used to hide it).
        val barRight = keysLeft - (if (keyW.isEmpty()) 0f else SECTION_GAP)
        val pctRoom = BrassFont.width(this, "100%") + 6f
        val inline = wantsBar && !stacked && barRight - PAD - pctRoom >= MIN_BAR
        val ownRow = wantsBar && !inline
        var barRowY = -1f
        if (ownRow) { barRowY = y; y += BrassFont.LINE + BAR_ROW_GAP }
        val hasFooter = keyW.isNotEmpty() || inline
        var footerY = y
        val keyRows = if (stacked) keyW.size else 1
        if (hasFooter) y += keyRows * (KEY_H + BrassWidget.BLEED_BOTTOM) + (keyRows - 1) * KEY_GAP else y -= if (ownRow) BAR_ROW_GAP else SECTION_GAP
        // Fill a taller pane: everything above stays put, the bar row and footer drop to the bottom.
        val extra = ((fillHeight?.invoke() ?: 0f) - (y + PAD)).coerceAtLeast(0f)
        footerY += extra
        if (barRowY >= 0f) barRowY += extra
        y += extra
        return Geo(
            height = y + PAD,
            barRowY = barRowY,
            advancementY = advancementY,
            headerBottom = headerBottom,
            dividerY = dividerY,
            summary = summary,
            summaryY = summaryY,
            objectivesY = objectivesY,
            rows = rows,
            rewardsY = rewardsY,
            rewardsH = rewardsH,
            chips = chips,
            footerY = footerY,
            barY = if (inline) footerY + floor((KEY_H - BAR_H - 2f) / 2f) else -1f,
            barRight = barRight,
            keyX = keyX,
            keyW = keyW,
            keyRow = keyRow,
        )
    }

    private fun layoutTracker(): Geo {
        var y = PAD + BrassFont.LINE + CAPTION_GAP
        val rows = ArrayList<Row>()
        val st = status()
        if (st == BrassQuest.Status.READY || st == BrassQuest.Status.FAILED || quest.objectives.isEmpty()) {
            rows += Row(y, null)
            y += BrassFont.LINE + TRACK_GAP
        } else {
            val (shown, more) = visibleObjectives()
            for (o in shown) { rows += Row(y, o); y += BrassFont.LINE + TRACK_GAP }
            if (more > 0) { rows += Row(y, null, more); y += BrassFont.LINE + TRACK_GAP }
        }
        return Geo(height = y - TRACK_GAP + PAD, headerBottom = PAD + BrassFont.LINE, rows = rows)
    }

    private fun keyWidth(a: Action): Float =
        (BrassFont.width(this, a.label) + 14f + if (a.icon != null) 13f else 0f).coerceAtLeast(MIN_KEY)

    private fun chipWidth(r: BrassQuest.Reward): Float =
        (if (r.itemId != null) ITEM_LEAD else CHIP_LEAD) + BrassFont.width(this, chipText(r)) + 5f

    private fun chipText(r: BrassQuest.Reward): String {
        val text = if (r.itemId != null && r.amount > 1) "${r.amount}x ${r.label}" else r.label
        return BrassFont.fit(this, text, MAX_UNLOCK_TEXT)
    }

    // ---- painting -------------------------------------------------------------------------------

    private var shownQuest: BrassQuest? = null
    private var swapT = 1f
    private var swapMilestone = false
    private var swapDy = 0f
    private var ambientBase = 1f

    override fun draw(matrixStack: UMatrixStack) {
        if (shownQuest !== quest) {
            if (shownQuest != null && animateSwaps && style == Style.FULL) {
                swapT = 0f
                swapMilestone = quest.milestone
                objectivesExpanded = false
            }
            shownQuest = quest
        }
        if (swapT < 1f) swapT = (swapT + (if (BrassClock.driven) BrassClock.dt else 0.016f) / (if (swapMilestone) SWAP_MILESTONE else SWAP_TIME)).coerceAtMost(1f)
        layout()
        syncKeys()
        if (swapT >= 1f) { swapDy = 0f; super.draw(matrixStack); return }
        // The content (and the keys, which are children) rises into place and fades in; the frame is
        // drawn back at rest in drawFull, so the card itself never jumps.
        swapDy = BrassMotion.arrive(swapT, 1, SWAP_RISE / BrassMotion.TRAVEL)
        ambientBase = BrassAmbientFade.current
        matrixStack.push()
        matrixStack.translate(0f, swapDy, 0f)
        BrassAmbientFade.current = ambientBase * BrassMotion.fadeIn(swapT)
        super.draw(matrixStack)
        BrassAmbientFade.current = ambientBase
        matrixStack.pop()
    }

    /** Draw [block] with the swap's rise and fade undone (the card's frame stays put). */
    private inline fun atRest(m: UMatrixStack, block: () -> Unit) {
        if (swapT >= 1f) { block(); return }
        val faded = BrassAmbientFade.current
        m.push()
        m.translate(0f, -swapDy, 0f)
        BrassAmbientFade.current = ambientBase
        block()
        BrassAmbientFade.current = faded
        m.pop()
    }

    private fun syncKeys() {
        keys.forEach { key ->
            val a = actions.getOrNull(key.slot) ?: return@forEach
            key.label = a.label
            key.icon = a.icon
            key.accent = if (a.selected && a.accent == BrassAccent.DEFAULT) BrassAccent.BRASS else a.accent
            key.selectable = a.selected
            key.selected = a.selected
            key.active = a.disabledReason == null
        }
    }

    private var time = 0f
    private var celebrate = 0f
    private var seenQuest: BrassQuest? = null
    private var seenStatus: BrassQuest.Status? = null

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val g = layout()
        val st = status()
        val dt = if (BrassClock.driven) BrassClock.dt else 0.016f
        time += dt
        // Celebrate a claim seen happen - not a quest that arrives already claimed (a swapped detail card).
        if (seenQuest !== quest) { seenQuest = quest; seenStatus = st; celebrate = 0f }
        else if (seenStatus != st) {
            if (st == BrassQuest.Status.CLAIMED) celebrate = 1f
            seenStatus = st
        }
        celebrate = (celebrate - dt / CELEBRATE_TIME).coerceAtLeast(0f)
        shownFraction.target = if (st == BrassQuest.Status.READY || st == BrassQuest.Status.CLAIMED) 1f else quest.fraction
        when (style) {
            Style.FULL -> drawFull(m, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), g, st)
            Style.COMPACT -> drawCompact(m, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), st)
            Style.TRACKER -> drawTracker(m, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), g, st)
        }
        if (style == Style.FULL && celebrate > 0f) drawCelebration(m, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
    }

    private fun c(color: Color): Color = withAlpha(color, entranceFade)

    private fun hot(): Boolean = (hoveredState || proxyHovered) && active

    private fun drawFull(m: UMatrixStack, x: Float, y: Float, w: Float, h: Float, g: Geo, st: BrassQuest.Status) {
        val tint = statusColor(st)
        val dim = st == BrassQuest.Status.CLAIMED || st == BrassQuest.Status.LOCKED
        val ms = quest.milestone
        val border = when {
            selected -> Colors.UI_ACCENT
            ms -> Colors.mix(Colors.UI_INNER_BORDER, gold(), if (hot()) 0.3f else 0.1f)
            hot() && (onClick != null || collapsible) -> Colors.UI_ELEMENT_BORDER_HOVER
            else -> Colors.UI_INNER_BORDER
        }
        val reveal = if (swapMilestone && ms && swapT < 1f) swapT else 1f
        atRest(m) {
            BrassCard.panel(m, x, y, x + w, y + h, fill = if (selected) Colors.UI_INNER_BG_SELECTED else Colors.UI_INNER_BG, border = border, alpha = entranceFade)
            if (ms) ornaments(m, x, y, w, h, g.headerBottom, BrassMotion.spring(reveal))
            if (reveal < 1f) {
                // the milestone reveal: a gold flash round the frame and a sweep of light over the header
                val flash = 1f - BrassMotion.smooth(reveal)
                BrassPaint.border(m, x + 1f, y + 1f, x + w - 1f, y + h - 1f, withAlpha(gold(), entranceFade * flash))
                BrassPaint.border(m, x + 2f, y + 2f, x + w - 2f, y + h - 2f, withAlpha(gold(), entranceFade * flash * 0.5f))
                sweep(m, x, y, w, g.headerBottom, at = BrassMotion.smooth(reveal) * (w + SWEEP_W * 2f) - SWEEP_W, strength = 0.4f)
            }
        }
        drawHeader(m, x, y, w, st, tint, dim)
        if (ms && shimmer && !dim && reveal >= 1f) sweep(m, x, y, w, g.headerBottom)

        if (collapsed) {
            if (g.barY >= 0f) bar(m, x + PAD, y + g.barY, x + g.barRight, shownFraction.advance(), st)
            return
        }
        if (g.dividerY >= 0f) tell(m, x + PAD, y + g.dividerY, x + w - PAD, if (ms) gold() else tint, if (ms) TELL_W * 2f else TELL_W)

        g.summary.forEachIndexed { i, line ->
            BrassFont.draw(m, this, line, x + PAD, y + g.summaryY + i * BrassFont.LINE, c(if (dim) Colors.UI_TEXT_DARK else Colors.UI_TEXT))
        }

        if (g.objectivesY >= 0f) {
            caption(m, "OBJECTIVES", x + PAD, y + g.objectivesY)
            val count = quest.required.ifEmpty { quest.objectives }
            val done = count.count { it.done }
            val tally = "$done/${count.size}"
            BrassFont.draw(m, this, tally, x + w - PAD - BrassFont.width(this, tally), y + g.objectivesY,
                c(if (done == count.size) Colors.UI_ACCENT_BRIGHT else Colors.UI_ACCENT))
            for (row in g.rows) drawObjective(m, row, x + PAD, y, w - PAD * 2, st, bars = true)
        }

        if (g.advancementY >= 0f) drawAdvancement(m, x + PAD, y + g.advancementY, w - PAD * 2, st)
        if (g.rewardsH > 0f) caption(m, "REWARDS", x + PAD, y + g.rewardsY - BrassFont.LINE - CAPTION_GAP)

        if (g.barRowY >= 0f) {
            val f = shownFraction.advance()
            val pct = "${(f * 100).roundToInt()}%"
            val pw = BrassFont.width(this, pct)
            bar(m, x + PAD, y + g.barRowY + 2f, x + w - PAD - pw - 6f, f, st)
            BrassFont.draw(m, this, pct, x + w - PAD - pw, y + g.barRowY, c(Colors.UI_TEXT_HOVER))
        }
        if (g.barY >= 0f) {
            val f = shownFraction.advance()
            val pct = "${(f * 100).roundToInt()}%"
            val pw = BrassFont.width(this, pct)
            bar(m, x + PAD, y + g.barY + 1f, x + g.barRight - pw - 6f, f, st)
            BrassFont.draw(m, this, pct, x + g.barRight - pw, y + g.footerY + floor((KEY_H - BrassFont.LINE) / 2f) + 1f, c(Colors.UI_TEXT_HOVER))
        }
    }

    private fun drawHeader(m: UMatrixStack, x: Float, y: Float, w: Float, st: BrassQuest.Status, tint: Color, dim: Boolean) {
        val tx = x + PAD
        val ty = y + PAD
        val ms = quest.milestone
        BrassCard.panel(m, tx, ty, tx + TILE, ty + TILE,
            fill = if (ms) Colors.mix(Colors.UI_BACKGROUND, goldDeep(), 0.5f) else Colors.UI_BACKGROUND,
            border = if (ms) gold() else Colors.mix(Colors.UI_INNER_BORDER, tint, 0.55f), alpha = entranceFade)
        QuestIcon.draw(m, quest, tx, ty, TILE, 16f, c(if (dim) Colors.UI_TEXT_DARK else tint), entranceFade, dim)

        // right column: [tracked pin] [status tag] [chevron] / [stars] [timer]
        var right = x + w - PAD
        val line1 = ty + 2f
        val line2 = ty + 2f + BrassFont.LINE + 3f
        if (collapsible) {
            right -= 8f
            BrassIcons.draw(m, if (collapsed) BrassIcons.CHEVRON_DOWN else BrassIcons.CHEVRON_UP, right, line1 - 1f, 8f,
                c(if (hot() && hoveredHeader()) Colors.UI_TEXT_HOVER else Colors.UI_TEXT_DARK))
            right -= 5f
        }
        val textX = tx + TILE + 6f
        right = badges(m, st, tint, textX, right, line1)
        val titleColor = when {
            dim -> Colors.UI_TEXT_DARK
            selected -> Colors.UI_ACCENT_BRIGHT
            ms -> gold()
            else -> Colors.UI_TEXT_HOVER
        }
        val titleX = milestoneMark(m, textX, line1)
        BrassFont.draw(m, this, BrassFont.fit(this, quest.title, right - titleX), titleX, line1, c(titleColor))

        var right2 = x + w - PAD
        timerText(st)?.let { (text, col) ->
            val tw = BrassFont.width(this, text)
            right2 -= tw
            BrassFont.draw(m, this, text, right2, line2, c(col))
            right2 -= 10f
            BrassIcons.draw(m, BrassIcons.CLOCK, right2, line2 - 1f, 8f, c(col))
            right2 -= 6f
        }
        val starsN = quest.difficultyMax.coerceAtLeast(quest.difficulty)
        val metaRoom = right2 - textX - starsN * STAR_STEP - 6f
        if (showDifficulty && quest.difficulty > 0 && (metaText(st) == null || metaRoom >= MIN_TITLE)) {
            val n = starsN
            right2 -= n * STAR_STEP - 1f
            for (i in 0 until n) {
                val on = i < quest.difficulty
                BrassIcons.draw(m, if (on) BrassIcons.STAR else BrassIcons.STAR_OUTLINE, right2 + i * STAR_STEP, line2 - 1f, 8f,
                    c(if (on) (if (quest.milestone) gold() else Colors.BRASS_300) else Colors.UI_TEXT_DARK))
            }
            right2 -= 6f
        }
        var mx = textX
        val season = quest.season
        if (season != null && st != BrassQuest.Status.LOCKED && right2 - mx > 40f) {
            // a seasonal quest wears its season first, in the info colour
            BrassIcons.draw(m, BrassIcons.SPARKLE, mx, line2 - 1f, 8f, c(Colors.PATINA_400))
            mx += 10f
            val shown = BrassFont.fit(this, season, right2 - mx)
            BrassFont.draw(m, this, shown, mx, line2, c(Colors.PATINA_400))
            mx += BrassFont.width(this, shown) + 4f
        }
        metaText(st)?.let { meta ->
            val text = if (mx > textX) "· $meta" else meta
            if (right2 - mx > 8f) BrassFont.draw(m, this, BrassFont.fit(this, text, right2 - mx), mx, line2, c(Colors.UI_TEXT_DARK))
        }
    }

    /**
     * The status tag and tracked pin, right-aligned before [right]; returns the new right edge. When
     * the title would be left under [MIN_TITLE] the pin goes first, then the tag - the tile's colour
     * still says the status, and a readable title matters more than a chip.
     */
    private fun badges(m: UMatrixStack, st: BrassQuest.Status, tint: Color, textX: Float, right: Float, line1: Float): Float {
        val label = statusLabel(st)
        val tagW = label?.let { BrassTag.measure(this, it) + 5f } ?: 0f
        val pinW = if (quest.tracked && st == BrassQuest.Status.ACTIVE) 13f else 0f
        val room = right - textX
        val showTag = tagW > 0f && room - tagW >= MIN_TITLE
        val showPin = pinW > 0f && room - pinW - (if (showTag) tagW else 0f) >= MIN_TITLE
        var r = right
        if (showTag && label != null) {
            r -= tagW - 5f
            BrassTag.drawPill(m, this, r, line1 - 1f, label, tint, entranceFade)
            r -= 5f
        }
        if (showPin) {
            r -= 8f
            BrassIcons.draw(m, BrassIcons.PIN, r, line1 - 1f, 8f, c(Colors.UI_ACCENT))
            r -= 5f
        }
        return r
    }

    /** The advancement this quest grants: locked until the quest is claimed. */
    private fun drawAdvancement(m: UMatrixStack, x: Float, y: Float, w: Float, st: BrassQuest.Status) {
        caption(m, "ADVANCEMENT", x, y - BrassFont.LINE - CAPTION_GAP)
        val unlocked = st == BrassQuest.Status.CLAIMED
        val tint = if (quest.milestone) gold() else Colors.UI_ACCENT
        BrassCard.panel(m, x, y, x + w, y + CHIP_H,
            fill = if (unlocked) Colors.mix(Colors.UI_ELEMENT_BG, tint, 0.12f) else Colors.UI_ELEMENT_BG,
            border = if (unlocked) Colors.mix(Colors.UI_ELEMENT_BORDER, tint, 0.6f) else Colors.UI_ELEMENT_BORDER, alpha = entranceFade)
        BrassIcons.draw(m, BrassIcons.TROPHY, x + 4f, y + 4f, 8f, c(if (unlocked) tint else Colors.UI_TEXT_DARK))
        val state = if (unlocked) "Unlocked" else "Locked"
        val sw = BrassFont.width(this, state)
        val ty = y + floor((CHIP_H - BrassFont.LINE) / 2f) + 1f
        BrassFont.draw(m, this, state, x + w - 5f - sw, ty, c(if (unlocked) tint else Colors.UI_TEXT_DARK))
        if (!unlocked) BrassIcons.draw(m, BrassIcons.LOCK, x + w - 5f - sw - 11f, ty - 1f, 8f, c(Colors.UI_TEXT_DARK))
        val title = quest.advancementTitle ?: quest.advancement ?: return
        BrassFont.draw(m, this, BrassFont.fit(this, title, w - 15f - sw - 22f), x + CHIP_LEAD, ty, c(if (unlocked) Colors.UI_TEXT_HOVER else Colors.UI_TEXT))
    }

    /** Milestones get a trophy before the title; returns where the title starts. */
    private fun milestoneMark(m: UMatrixStack, x: Float, line: Float): Float {
        if (!quest.milestone) return x
        BrassIcons.draw(m, BrassIcons.TROPHY, x, line - 1f, 8f, c(gold()))
        return x + 11f
    }

    private fun hoveredHeader(): Boolean {
        val (_, my) = getMousePosition()
        return my - getTop() < layout().headerBottom
    }

    private fun metaText(st: BrassQuest.Status): String? {
        if (st == BrassQuest.Status.LOCKED) lockReason()?.let { return it }
        if (!showMeta) return null
        return listOfNotNull(quest.giver, quest.category).joinToString(" · ").ifEmpty { null }
    }

    private fun timerText(st: BrassQuest.Status): Pair<String, Color>? {
        if (!showTimer) return null
        if (st == BrassQuest.Status.CLAIMED || st == BrassQuest.Status.READY || st == BrassQuest.Status.LOCKED) return null
        val left = quest.remainingMs(now()) ?: return null
        val col = when {
            left <= 0L -> Colors.DANGER
            left < URGENT_MS -> Colors.WARN
            else -> Colors.UI_TEXT_DARK
        }
        return BrassQuestFormat.remaining(left) to col
    }

    private fun caption(m: UMatrixStack, text: String, x: Float, y: Float) =
        BrassFont.draw(m, this, text, x, y, c(Colors.UI_TEXT_DARK))

    private fun drawObjective(m: UMatrixStack, row: Row, x: Float, top: Float, w: Float, st: BrassQuest.Status, bars: Boolean) {
        val ry = top + row.y
        val o = row.objective
        if (o == null) {
            val text = when {
                row.more > 0 -> "+${row.more} more"
                row.more < 0 -> "Show less"
                else -> ""
            }
            if (text.isEmpty()) return
            // FULL: a link that expands / folds the list; TRACKER: a plain count
            val link = style == Style.FULL
            val hover = link && hoveredState && moreRowAt(getMousePosition().first, getMousePosition().second) === row
            if (hover) BrassCursor.request(BrassCursor.Kind.HAND)
            val col = when {
                !link -> Colors.UI_TEXT_DARK
                hover -> Colors.UI_ACCENT_BRIGHT
                else -> Colors.UI_ACCENT
            }
            val lx = x + CHECK + 4f
            BrassIcons.draw(m, if (row.more > 0) BrassIcons.CHEVRON_DOWN else BrassIcons.CHEVRON_UP, x, ry - 1f, 8f, c(col))
            BrassFont.draw(m, this, text, lx, ry, c(col))
            if (hover) BrassPaint.rect(m, lx, ry + BrassFont.LINE - 1f, lx + BrassFont.width(this, text), ry + BrassFont.LINE, c(col))
            return
        }
        val failed = st == BrassQuest.Status.FAILED && !o.done
        check(m, x, ry - 1f, o.done, failed, o.optional)
        val counter = if (o.hidden) "" else o.counter()
        val cw = if (counter.isEmpty()) 0f else BrassFont.width(this, counter)
        val lx = x + CHECK + 4f
        var room = x + w - lx - (if (cw > 0f) cw + 6f else 0f)
        val label = if (o.hidden) "???" else o.label
        val col = when {
            o.done || failed -> Colors.UI_TEXT_DARK
            o.optional -> Colors.UI_TEXT
            else -> Colors.UI_TEXT
        }
        val shown = BrassFont.fit(this, label, room)
        BrassFont.draw(m, this, shown, lx, ry, c(col))
        if (o.done && shown.isNotEmpty()) {
            val sw = BrassFont.width(this, shown)
            BrassPaint.rect(m, lx, ry + 3f, lx + sw, ry + 4f, c(Colors.UI_TEXT_DARK))
        }
        if (o.optional) {
            room -= BrassFont.width(this, shown) + 4f
            val opt = "optional"
            if (BrassFont.width(this, opt) <= room) BrassFont.draw(m, this, opt, lx + BrassFont.width(this, shown) + 4f, ry, c(Colors.UI_ELEMENT_BORDER_HOVER))
        }
        if (cw > 0f) {
            BrassFont.draw(m, this, counter, x + w - cw, ry, c(if (o.done) Colors.UI_ACCENT else Colors.UI_TEXT_DARK))
        }
        if (bars && style == Style.FULL && showObjectiveBars && o.target > 1) {
            val ease = objectiveEase.getOrPut(o) { BrassEased(o.fraction, speed = 9f) }
            ease.target = o.fraction
            val by = ry + BrassFont.LINE + BAR_GAP
            if (o.done) BrassCard.filledTrack(m, lx, by, x + w, by + BAR_H, ease.advance())
            else BrassCard.filledTrack(m, lx, by, x + w, by + BAR_H, ease.advance(), Colors.BRASS_700, Colors.BRASS_500)
        }
    }

    /** Pending: a small ring. Done: a bare check. Failed: a bare cross. [y] is the text line's top - 1. */
    private fun check(m: UMatrixStack, x: Float, y: Float, done: Boolean, failed: Boolean, optional: Boolean) {
        when {
            done -> BrassIcons.draw(m, BrassIcons.CHECK, x, y, 8f, c(Colors.UI_ACCENT))
            failed -> BrassIcons.draw(m, BrassIcons.CLOSE, x, y, 8f, c(Colors.DANGER))
            else -> {
                BrassPaint.rect(m, x + 2f, y + 2f, x + 7f, y + 7f, c(Colors.UI_OUTER_BORDER))
                BrassPaint.border(m, x + 1f, y + 1f, x + 7f, y + 7f, c(if (optional) Colors.UI_ELEMENT_BORDER_HOVER else Colors.UI_TEXT_DARK))
            }
        }
    }

    /** A hairline with the status colour on its first stretch - the card header's brass seam, in the status's colour. */
    private fun tell(m: UMatrixStack, x1: Float, y: Float, x2: Float, tint: Color, width: Float = TELL_W) {
        if (x2 <= x1) return
        BrassPaint.rect(m, x1, y, x2, y + 1f, c(Colors.UI_INNER_BORDER))
        BrassPaint.rect(m, x1, y - 1f, x1 + minOf(width, x2 - x1), y + 1f, c(tint))
    }

    /**
     * Milestones wear the selected-row look in gold: a bar down the left edge lit on its inner side with a
     * notch at the header, a stepped wash fading right across the header, and gold top and bottom edges
     * fading out the same way. [grow] (0..1) draws it in during the reveal.
     */
    private fun ornaments(m: UMatrixStack, x: Float, y: Float, w: Float, h: Float, headerBottom: Float, grow: Float = 1f) {
        if (grow <= 0f) return
        val gold = gold()
        val lit = Colors.mix(gold, Colors.UI_TEXT_HOVER, 0.35f)
        val a = entranceFade
        val top = y + 3f
        val bottom = top + floor((h - 6f) * grow)
        BrassPaint.rect(m, x + 1f, top, x + 2f, bottom, withAlpha(gold, a))
        BrassPaint.rect(m, x + 2f, top, x + 3f, bottom, withAlpha(lit, a))
        val cy = floor(y + PAD + TILE / 2f)
        if (cy + 2f <= bottom) {
            BrassPaint.rect(m, x + 3f, cy - 1f, x + 4f, cy + 2f, withAlpha(lit, a))
            BrassPaint.rect(m, x + 4f, cy, x + 5f, cy + 1f, withAlpha(lit, a))
        }
        val wash = minOf(GOLD_WASH, w / 3f) * grow
        for (i in 0 until GOLD_BANDS) {
            val k = 1f - i / GOLD_BANDS.toFloat()
            val bx = x + 1f + i * wash / GOLD_BANDS
            BrassPaint.rect(m, bx, y + 1f, bx + wash / GOLD_BANDS, y + headerBottom, withAlpha(gold, a * 0.16f * k * k))
        }
        val edge = (w - 2f) * EDGE_REACH * grow
        for (i in 0 until GOLD_BANDS) {
            val k = 1f - i / GOLD_BANDS.toFloat()
            val ex = x + 1f + i * edge / GOLD_BANDS
            val c = withAlpha(gold, a * k)
            BrassPaint.rect(m, ex, y + 1f, ex + edge / GOLD_BANDS, y + 2f, c)
            BrassPaint.rect(m, ex, y + h - 2f, ex + edge / GOLD_BANDS, y + h - 1f, c)
        }
    }

    /** Milestones: a soft band of light crossing the header every few seconds. */
    private fun sweep(
        m: UMatrixStack, x: Float, y: Float, w: Float, bottom: Float,
        at: Float = ((time % SWEEP_PERIOD) / SWEEP_PERIOD) * (w + SWEEP_W * 2f) * 1.6f - SWEEP_W,
        strength: Float = 0.16f,
    ) {
        if (at > w + SWEEP_W) return
        for (i in 0 until SWEEP_W.toInt()) {
            val bx = floor(x + at + i)
            if (bx < x + 3f || bx > x + w - 4f) continue
            val k = 1f - abs(i - SWEEP_W / 2f) / (SWEEP_W / 2f)
            BrassPaint.rect(m, bx, y + 3f, bx + 1f, y + bottom - 1f, withAlpha(gold(), entranceFade * strength * k))
        }
    }

    /** A just-claimed quest: a brass flash round the card; milestones also shower sparkles inside it. */
    private fun drawCelebration(m: UMatrixStack, x: Float, y: Float, w: Float, h: Float) {
        val k = celebrate
        BrassPaint.border(m, x + 1f, y + 1f, x + w - 1f, y + h - 1f, withAlpha(if (quest.milestone) gold() else Colors.BRASS_300, entranceFade * k))
        if (!quest.milestone) return
        val t = 1f - k
        for (i in 0 until 14) {
            val seed = (i * 7919 + quest.id.hashCode()) and 0xFFFF
            val fx = (seed % 997) / 997f
            val speed = 0.6f + (seed % 13) / 13f * 0.6f
            val py = y + h - 10f - t * speed * (h - 18f)
            val px = x + 6f + fx * (w - 20f) + sin(t * 6f + i) * 3f
            if (py < y + 3f) continue
            BrassIcons.draw(m, if (i % 3 == 0) BrassIcons.STAR else BrassIcons.SPARKLE, floor(px), floor(py), 8f,
                withAlpha(if (i % 2 == 0) gold() else Colors.UI_TEXT_HOVER, entranceFade * k))
        }
    }

    private fun bar(m: UMatrixStack, x1: Float, y1: Float, x2: Float, f: Float, st: BrassQuest.Status) {
        if (x2 - x1 < 4f) return
        if (st == BrassQuest.Status.FAILED) BrassCard.filledTrack(m, x1, y1, x2, y1 + BAR_H, f, Colors.PROGRESS_FAIL, Colors.PROGRESS_FAIL_LIT)
        else BrassCard.filledTrack(m, x1, y1, x2, y1 + BAR_H, f)
    }

    private fun drawCompact(m: UMatrixStack, x: Float, y: Float, w: Float, h: Float, st: BrassQuest.Status) {
        val tint = statusColor(st)
        val dim = st == BrassQuest.Status.CLAIMED || st == BrassQuest.Status.LOCKED
        when {
            selected -> BrassPaint.selection(m, x, y, x + w, y + h, entranceFade)
            hot() -> BrassPaint.rect(m, x, y, x + w, y + h, c(Colors.ROW_HOVER))
        }
        val tx = x + PAD
        val ty = y + floor((h - COMPACT_TILE) / 2f)
        BrassCard.panel(m, tx, ty, tx + COMPACT_TILE, ty + COMPACT_TILE,
            fill = if (quest.milestone) Colors.mix(Colors.UI_BACKGROUND, goldDeep(), 0.5f) else Colors.UI_BACKGROUND,
            border = if (quest.milestone) gold() else Colors.mix(Colors.UI_INNER_BORDER, tint, 0.55f), alpha = entranceFade)
        QuestIcon.draw(m, quest, tx + 1f, ty + 1f, COMPACT_TILE - 2f, 8f, c(if (dim) Colors.UI_TEXT_DARK else tint), entranceFade, dim)

        val line1 = y + 4f
        val line2 = line1 + BrassFont.LINE + 2f
        var right = x + w - PAD
        val textX = tx + COMPACT_TILE + 6f
        right = badges(m, st, tint, textX, right, line1)
        val titleCol = when {
            dim -> Colors.UI_TEXT_DARK
            selected -> Colors.UI_ACCENT_BRIGHT
            quest.milestone -> gold()
            else -> Colors.UI_TEXT
        }
        val titleX = milestoneMark(m, textX, line1)
        BrassFont.draw(m, this, BrassFont.fit(this, quest.title, right - titleX), titleX, line1, c(titleCol))

        var right2 = x + w - PAD
        val showBar = showProgress && quest.objectives.isNotEmpty() &&
            (st == BrassQuest.Status.ACTIVE || st == BrassQuest.Status.READY || st == BrassQuest.Status.FAILED)
        if (showBar && w > 150f) {
            val bw = MINI_BAR
            bar(m, right2 - bw, line2 + 3f, right2, shownFraction.advance(), st)
            right2 -= bw + 6f
        }
        BrassFont.draw(m, this, BrassFont.fit(this, compactSubtitle(st), right2 - textX), textX, line2, c(Colors.UI_TEXT_DARK))
    }

    private fun compactSubtitle(st: BrassQuest.Status): String {
        val parts = ArrayList<String>()
        when (st) {
            BrassQuest.Status.LOCKED -> parts += lockReason() ?: "Locked"
            BrassQuest.Status.AVAILABLE -> metaText(st)?.let { parts += it }
            BrassQuest.Status.READY -> parts += quest.giver?.let { "Return to $it" } ?: "Reward ready"
            BrassQuest.Status.CLAIMED -> parts += "Completed"
            else -> {
                val req = quest.required.ifEmpty { quest.objectives }
                val timed = timerText(st) != null
                if (req.isNotEmpty()) parts += "${req.count { it.done }}/${req.size}" + if (timed) "" else " objectives"
            }
        }
        timerText(st)?.let { parts += it.first }
        return parts.joinToString(" · ")
    }

    private fun drawTracker(m: UMatrixStack, x: Float, y: Float, w: Float, h: Float, g: Geo, st: BrassQuest.Status) {
        val tint = statusColor(st)
        BrassCard.panel(m, x, y, x + w, y + h, fill = Colors.withAlpha(Colors.UI_BACKGROUND, 224), alpha = entranceFade)
        val ty = y + PAD
        tell(m, x + PAD, ty + BrassFont.LINE + 1f, x + w - PAD, tint)
        QuestIcon.draw(m, quest, x + PAD, ty - 1f, 8f, 8f, c(tint), entranceFade)
        var right = x + w - PAD
        timerText(st)?.let { (text, col) ->
            val tw = BrassFont.width(this, text)
            right -= tw
            BrassFont.draw(m, this, text, right, ty, c(col))
            right -= 6f
        }
        val textX = x + PAD + 12f
        BrassFont.draw(m, this, BrassFont.fit(this, quest.title, right - textX), textX, ty, c(if (quest.milestone) gold() else Colors.UI_TEXT_HOVER))

        for (row in g.rows) {
            if (row.objective == null && row.more == 0) {
                val (text, col) = when (st) {
                    BrassQuest.Status.READY -> (quest.giver?.let { "Return to $it" } ?: "Claim your reward") to Colors.UI_ACCENT_BRIGHT
                    BrassQuest.Status.FAILED -> "Failed" to Colors.DANGER
                    else -> (quest.summary ?: "") to Colors.UI_TEXT_DARK
                }
                BrassFont.draw(m, this, BrassFont.fit(this, text, w - PAD * 2), x + PAD, y + row.y, c(col))
            } else {
                drawObjective(m, row, x + PAD, y, w - PAD * 2, st, bars = false)
            }
        }
    }

    // ---- children -------------------------------------------------------------------------------

    private fun fullOpen(): Boolean = style == Style.FULL && !collapsed

    private inner class ActionKey(val slot: Int) : BrassButton("", BrassAccent.DEFAULT, { actions.getOrNull(slot)?.let { press(it) } }), BrassInert {
        override val inert: Boolean get() = !fullOpen() || slot >= actions.size
        override fun draw(matrixStack: UMatrixStack) { if (!inert) super.draw(matrixStack) }
        override fun isPointInside(x: Float, y: Float): Boolean = !inert && super.isPointInside(x, y)
        override fun mouseMove(window: Window) { if (!inert) super.mouseMove(window) }
    }

    private inner class RewardStrip : UIComponent(), BrassInert {
        override val inert: Boolean get() = !fullOpen() || !showRewards || quest.rewards.isEmpty()

        fun hovered(): BrassQuest.Reward? {
            if (inert) return null
            val (mx, my) = getMousePosition()
            val lx = mx - getLeft()
            val ly = my - getTop()
            val chips = layout().chips
            for (i in chips.indices) {
                val ch = chips[i]
                if (lx >= ch[0] && lx < ch[0] + ch[2] && ly >= ch[1] && ly < ch[1] + CHIP_H) return quest.rewards.getOrNull(i)
            }
            return null
        }

        override fun isPointInside(x: Float, y: Float): Boolean = !inert && super.isPointInside(x, y)
        override fun mouseMove(window: Window) { if (!inert) super.mouseMove(window) }

        override fun draw(matrixStack: UMatrixStack) {
            if (inert) return
            beforeDraw(matrixStack)
            val chips = layout().chips
            val ox = getLeft().roundToInt().toFloat()
            val oy = getTop().roundToInt().toFloat()
            val hover = hovered()
            val a = entranceFade
            quest.rewards.forEachIndexed { i, r ->
                val ch = chips.getOrNull(i) ?: return@forEachIndexed
                val sx = ox + ch[0]
                val sy = oy + ch[1]
                val hot = hover === r
                BrassCard.panel(
                    matrixStack, sx, sy, sx + ch[2], sy + CHIP_H,
                    fill = if (hot) Colors.UI_ELEMENT_BG_HOVER else Colors.UI_ELEMENT_BG,
                    border = when {
                        hot -> Colors.UI_ELEMENT_BORDER_HOVER
                        quest.milestone -> Colors.mix(Colors.UI_ELEMENT_BORDER, gold(), 0.5f)
                        else -> Colors.UI_ELEMENT_BORDER
                    },
                    alpha = a,
                )
                val id = r.itemId
                // A real item sits on the chip at full size; off-game it falls back to an icon like the rest.
                val drawn = id != null && BrassPlatform.current?.drawItem(matrixStack, id, sx + 1f, sy, 16f, 1, a) == true
                if (!drawn) {
                    BrassIcons.draw(matrixStack, r.icon ?: BrassIcons.CUBE, sx + 4f, sy + 4f, 8f,
                        withAlpha(if (r.icon != null) Colors.BRASS_300 else Colors.UI_TEXT_DARK, a))
                }
                val lead = if (id != null) ITEM_LEAD else CHIP_LEAD
                BrassFont.draw(matrixStack, this, BrassFont.fit(this, chipText(r), ch[2] - lead - 4f), sx + lead,
                    sy + floor((CHIP_H - BrassFont.LINE) / 2f) + 1f, withAlpha(if (hot) Colors.UI_TEXT_HOVER else Colors.UI_TEXT, a))
            }
            super.draw(matrixStack)
        }
    }

    companion object : BrassDemoSource {
        const val COMPACT_H = 26f

        private val PAD = BrassSpacing.PAD
        private const val TILE = 22f
        private const val COMPACT_TILE = 18f
        private const val SEAM = 2f
        private const val CHIP_H = 16f
        private const val CHIP_LEAD = 15f
        private const val ITEM_LEAD = 19f
        private const val SECTION = 6f
        private const val SECTION_GAP = 9f
        private const val CAPTION_GAP = 5f
        private const val ROW_GAP = 4f
        private const val TRACK_GAP = 3f
        private const val CHECK = 8f
        private const val TELL_W = 40f
        private const val BAR_ROW_GAP = 6f
        private const val GOLD_WASH = 48f
        private const val GOLD_BANDS = 6
        private const val EDGE_REACH = 0.6f
        private const val SWEEP_W = 14f
        private const val SWEEP_PERIOD = 4.5f
        private const val CELEBRATE_TIME = 1.4f
        private const val SWAP_TIME = 0.45f
        private const val SWAP_MILESTONE = 0.9f
        private const val SWAP_RISE = 10f
        private const val BAR_H = 4f
        private const val BAR_GAP = 3f
        private const val MIN_BAR = 40f
        private const val MINI_BAR = 40f
        private const val CHIP_GAP = 4f
        private const val CHIP_TEXT_GAP = 4f
        private const val MAX_UNLOCK_TEXT = 110f
        private const val KEY_H = 18f
        private const val KEY_GAP = 4f
        private const val MIN_KEY = 48f
        private const val MAX_KEYS = 4
        private const val STAR_STEP = 9f
        private const val MIN_TITLE = 56f
        private const val URGENT_MS = 10 * 60_000L

        /**
         * The colour milestones are dressed in, everywhere (card, row, tracker, tree). Defaults to a
         * gold drawn from the theme's warm tone - the theme's "brass" is its accent (green in the
         * default theme), which would make a milestone look like any other quest.
         */
        @JvmStatic
        var milestoneColor: () -> Color = { Colors.mix(Colors.WARN, Colors.UI_TEXT_HOVER, 0.15f) }

        internal fun gold(): Color = milestoneColor()
        internal fun goldDim(): Color = Colors.mix(milestoneColor(), Colors.UI_OUTER_BORDER, 0.4f)
        internal fun goldDeep(): Color = Colors.mix(milestoneColor(), Colors.UI_OUTER_BORDER, 0.75f)

        fun defaultStatusColor(s: BrassQuest.Status): Color = when (s) {
            BrassQuest.Status.LOCKED -> Colors.UI_TEXT_DARK
            BrassQuest.Status.AVAILABLE -> Colors.PATINA_400
            BrassQuest.Status.ACTIVE -> Colors.UI_ACCENT
            BrassQuest.Status.READY -> Colors.BRASS_300
            BrassQuest.Status.CLAIMED -> Colors.UI_ELEMENT_BORDER_HOVER
            BrassQuest.Status.FAILED -> Colors.DANGER
        }

        fun defaultStatusLabel(s: BrassQuest.Status): String? = when (s) {
            BrassQuest.Status.LOCKED -> "LOCKED"
            BrassQuest.Status.AVAILABLE -> "NEW"
            BrassQuest.Status.ACTIVE -> "ACTIVE"
            BrassQuest.Status.READY -> "READY"
            BrassQuest.Status.CLAIMED -> "DONE"
            BrassQuest.Status.FAILED -> "FAILED"
        }

        @JvmStatic @JvmOverloads
        fun defaultActions(q: BrassQuest, s: BrassQuest.Status, lockedReason: String? = q.lockedReason): List<Action> = when (s) {
            BrassQuest.Status.LOCKED -> listOf(Action("Locked", icon = BrassIcons.LOCK, disabledReason = lockedReason ?: "Not available yet"))
            BrassQuest.Status.AVAILABLE -> listOf(Action("Accept", BrassAccent.BRASS, BrassIcons.CHECK, Kind.ACCEPT))
            BrassQuest.Status.ACTIVE -> listOf(
                Action("Abandon", BrassAccent.DANGER, kind = Kind.ABANDON, tooltip = "Give up this quest"),
                Action(if (q.tracked) "Tracking" else "Track", icon = BrassIcons.PIN, kind = Kind.TRACK,
                    tooltip = if (q.tracked) "Stop tracking" else "Show it in the tracker", selected = q.tracked),
            )
            BrassQuest.Status.READY -> listOf(Action("Claim", BrassAccent.BRASS, BrassIcons.GIFT, Kind.CLAIM))
            BrassQuest.Status.CLAIMED -> emptyList()
            BrassQuest.Status.FAILED -> listOf(Action("Retry", icon = BrassIcons.REFRESH, kind = Kind.RETRY))
        }

        /** The sample quest the demos and the gallery share. */
        @JvmStatic
        fun sample(): BrassQuest = BrassQuest(
            "iron-age", "The Iron Age", "The smith needs ore before the caravan leaves at dawn.", BrassIcons.PICKAXE,
        ).apply {
            giver = "Smith Hilda"
            iconItem = "minecraft:iron_pickaxe"
            category = "Main story"
            difficulty = 2
            objective("Mine iron ore", target = 32, progress = 24)
            objective("Smelt iron ingots", target = 16, progress = 16)
            objective("Bring the ingots to Hilda")
            objective("Find a diamond", optional = true)
            reward(BrassQuest.Reward.item("minecraft:iron_pickaxe"))
            reward(BrassQuest.Reward.item("minecraft:bread", 8))
            reward(BrassQuest.Reward.xp(250))
            reward(BrassQuest.Reward.coins(40))
        }

        override fun demo() = BrassDemo("quest", "Quest card", 250f, 226f) {
            val card = BrassQuestCard(sample())
            BrassVBox(gap = 0f, bleed = false).add(card)
                .also { box -> card.constrain { width = basicWidthConstraint { box.getWidth() } } }
        }
    }
}
