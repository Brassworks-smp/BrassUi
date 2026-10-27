package net.swzo.brass.ui.kit.quest

import gg.essential.elementa.UIComponent
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.basicYConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.paint.BrassWire
import net.swzo.brass.ui.kit.platform.BrassCursor
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * A living quest tree in the node editor's idiom: quests are tiles on a grid joined by curved pixel-art
 * wires, in a framed, zoomable viewport.
 *
 * - **Wires** are the node editor's own ([BrassWire]) and follow prerequisites. A wire into a quest that is open (its prerequisite finished, the
 *   quest not) carries flowing motes; finished paths drift slowly; locked ones sit dim. Finishing a
 *   quest sends a burst down its wires, and each child pops unlocked when the burst arrives.
 * - **Camera**: wheel zooms toward the cursor, drag pans with an inertia glide, [select] / [centerOn]
 *   glide to a quest. A dotted grid drifts behind everything.
 * - **Nodes** pop in column by column (wires draw in behind them; replayed whenever the tree comes back
 *   into view), lift on hover, breathe when selected, pulse when READY, and show progress when ACTIVE.
 *
 * [animated] = false turns all motion off (static screenshots, reduced-motion settings). Columns come
 * from [BrassQuestTreeLayout] unless a node pins `col`/`row`. Self-framed like `BrassTable`: give it a
 * fixed height and put it under a header, not inside a `BrassPanel`. Pair it with a card through [bind].
 *
 * ```
 * val tree = BrassQuestTree()
 *     .node(iron)
 *     .node(forge, requires = listOf("iron"))
 *     .bind(card)
 * ```
 */
class BrassQuestTree @JvmOverloads constructor(
    var onSelect: ((BrassQuest) -> Unit)? = null,
) : BrassWidget(BrassAccent.DEFAULT) {

    enum class Direction { RIGHT, DOWN }

    class Node(val quest: BrassQuest, val requires: List<String>, val col: Int?, val row: Int?)

    var direction = Direction.RIGHT
        set(value) { field = value; dirty = true }
    var showLabels = true
    var nodeSize = 24f
        set(value) { field = value; dirty = true }
    var cellW = 72f
        set(value) { field = value; dirty = true }
    var cellH = 50f
        set(value) { field = value; dirty = true }

    /** An AVAILABLE quest whose prerequisites are not all finished shows (and acts) as LOCKED. */
    var autoLock = true

    /** Master switch for every animation (entrance, motes, glides, pulses). */
    var animated = true

    var minZoom = 0.5f
    var maxZoom = 2f
    var showGrid = true

    var statusColor: (BrassQuest.Status) -> Color = BrassQuestCard::defaultStatusColor
    var clock: () -> Long = System::currentTimeMillis

    /** The selected quest's id. */
    var selectedId: String? = null
        private set

    /** The current (animated) zoom. */
    val zoom: Float get() = zoomNow

    private val nodes = ArrayList<Node>()
    private val byId = HashMap<String, Node>()
    private var cells: Map<String, BrassQuestTreeLayout.Cell> = emptyMap()
    private var dirty = true

    // camera, in world units at the viewport's top-left
    private var panX = 0f
    private var panY = 0f
    private var zoomNow = 1f
    private var zoomGoal = 1f
    private var zoomAnchor: FloatArray? = null
    private var glide: FloatArray? = null
    private var velX = 0f
    private var velY = 0f
    private var framed = false
    private var press: FloatArray? = null
    private var dragged = false
    private var lastDragNanos = 0L

    // animation state
    private var time = 0f
    private var shownFor = 0f
    private var lastFrame = -1L
    private val anim = HashMap<String, NodeAnim>()
    private val bursts = ArrayList<Burst>()

    private class NodeAnim {
        var hover = 0f
        var sel = 0f
        var flash = 0f
        var unlock = -1f
        var progress = -1f
        var status: BrassQuest.Status? = null
    }

    private class Burst(val from: String, val to: String) { var t = 0f }

    private val canvas = Canvas()

    init {
        chrome = BrassChrome.NONE
        canvas.constrain {
            x = basicXConstraint { this@BrassQuestTree.getLeft() + INSET }
            y = basicYConstraint { this@BrassQuestTree.getTop() + INSET }
            width = basicWidthConstraint { (this@BrassQuestTree.getWidth() - INSET * 2).coerceAtLeast(0f) }
            height = basicHeightConstraint { (this@BrassQuestTree.getHeight() - INSET * 2).coerceAtLeast(0f) }
        } childOf this
    }

    @JvmOverloads
    fun node(quest: BrassQuest, requires: List<String> = emptyList(), col: Int? = null, row: Int? = null): BrassQuestTree = apply {
        nodes.removeAll { it.quest.id == quest.id }
        val n = Node(quest, requires, col, row)
        nodes += n
        byId[quest.id] = n
        dirty = true
    }

    fun remove(id: String): BrassQuestTree = apply {
        nodes.removeAll { it.quest.id == id }
        byId.remove(id)
        dirty = true
    }

    fun clear(): BrassQuestTree = apply {
        nodes.clear(); byId.clear(); anim.clear(); bursts.clear()
        selectedId = null
        dirty = true
    }

    fun quest(id: String): BrassQuest? = byId[id]?.quest

    /** Every quest on the tree, in the order added. */
    fun quests(): List<BrassQuest> = nodes.map { it.quest }

    /** Select [id] (no callback) and glide it into view. */
    fun select(id: String?) {
        selectedId = id
        if (id != null) reveal(id)
    }

    /** Glide so [id] sits in the middle of the viewport. */
    fun centerOn(id: String) {
        val p = worldOf(id) ?: return
        goTo(p[0] + sizeOf(id) / 2f - viewW() / 2f, p[1] + sizeOf(id) / 2f - viewH() / 2f)
    }

    /** Ease the zoom to [z] around the viewport's centre. */
    fun zoomTo(z: Float) {
        zoomGoal = z.coerceIn(minZoom, maxZoom)
        zoomAnchor = floatArrayOf(canvas.getWidth() / 2f, canvas.getHeight() / 2f)
        if (!animated) settleZoom()
    }

    /** Fit the whole tree (zooming out as far as [minZoom] allows) and centre it. */
    fun frameAll() {
        val size = contentSize()
        val vw = canvas.getWidth(); val vh = canvas.getHeight()
        if (vw <= 0f || vh <= 0f) return
        zoomGoal = minOf(1f, vw / size[0], vh / size[1]).coerceIn(minZoom, maxZoom)
        zoomAnchor = null
        zoomNow = zoomGoal
        panX = 0f; panY = 0f
        clampPan()
    }

    /** Play the pop-in again. */
    fun replayEntrance() {
        shownFor = 0f
    }

    /**
     * Drive [card] from this tree: a click shows the clicked quest on it, and the card locks quests
     * exactly as the tree does ([lockReasonOf]). Keeps any [onSelect] already set.
     */
    fun bind(card: BrassQuestCard): BrassQuestTree = apply {
        card.lockOf = ::lockReasonOf
        val previous = onSelect
        onSelect = { q -> card.quest = q; previous?.invoke(q) }
        selectedId?.let { id -> quest(id)?.let { card.quest = it } }
    }

    /** Why [quest] is locked by unfinished prerequisites under [autoLock], or null when it is not. */
    fun lockReasonOf(quest: BrassQuest): String? {
        val n = byId[quest.id]?.takeIf { it.quest === quest } ?: return null
        if (!autoLock || quest.effectiveStatus(clock()) != BrassQuest.Status.AVAILABLE) return null
        val missing = n.requires.filter { !finished(it) }
        if (missing.isEmpty()) return null
        return "Requires " + missing.mapNotNull { quest(it)?.title }.joinToString(", ")
    }

    /** The status a node shows: the quest's own, locked behind unfinished prerequisites under [autoLock]. */
    fun displayStatus(n: Node): BrassQuest.Status {
        val st = n.quest.effectiveStatus(clock())
        if (autoLock && st == BrassQuest.Status.AVAILABLE && n.requires.any { !finished(it) }) return BrassQuest.Status.LOCKED
        return st
    }

    private fun finished(id: String): Boolean {
        val st = quest(id)?.effectiveStatus(clock()) ?: return true
        return st == BrassQuest.Status.CLAIMED || st == BrassQuest.Status.READY
    }

    // ---- geometry ------------------------------------------------------------------------------

    private fun solve() {
        if (!dirty) return
        cells = BrassQuestTreeLayout.solve(nodes.map { BrassQuestTreeLayout.Spec(it.quest.id, it.requires, it.col, it.row) })
        dirty = false
    }

    private fun labelRoom() = if (showLabels) BrassFont.LINE + LABEL_GAP else 0f

    /** Milestones are drawn bigger. */
    private fun sizeOf(id: String): Float = if (byId[id]?.quest?.milestone == true) nodeSize + MILESTONE_GROW else nodeSize

    /** World top-left of a node's tile (centred in its cell whatever its size, so rows stay aligned). */
    private fun worldOf(id: String): FloatArray? {
        solve()
        val c = cells[id] ?: return null
        val gx = if (direction == Direction.RIGHT) c.col else c.row
        val gy = if (direction == Direction.RIGHT) c.row else c.col
        val s = sizeOf(id)
        return floatArrayOf(
            floor(MARGIN + gx * cellW + (cellW - s) / 2f),
            floor(MARGIN + gy * cellH + (cellH - nodeSize - labelRoom()) / 2f - (s - nodeSize) / 2f),
        )
    }

    private fun contentSize(): FloatArray {
        solve()
        val main = (cells.values.maxOfOrNull { it.col } ?: -1) + 1
        val cross = (cells.values.maxOfOrNull { it.row } ?: -1) + 1
        val w = if (direction == Direction.RIGHT) main * cellW else cross * cellW
        val h = if (direction == Direction.RIGHT) cross * cellH else main * cellH
        return floatArrayOf(w + MARGIN * 2, h + MARGIN * 2)
    }

    private fun viewW() = canvas.getWidth() / zoomNow
    private fun viewH() = canvas.getHeight() / zoomNow

    private fun clampPan() {
        val size = contentSize()
        val vw = viewW(); val vh = viewH()
        // Smaller than the viewport: centre it. Larger: never pan past an edge.
        panX = if (size[0] <= vw) -(vw - size[0]) / 2f else panX.coerceIn(0f, size[0] - vw)
        panY = if (size[1] <= vh) -(vh - size[1]) / 2f else panY.coerceIn(0f, size[1] - vh)
    }

    private fun goTo(x: Float, y: Float) {
        velX = 0f; velY = 0f
        val sx = panX; val sy = panY
        panX = x; panY = y
        clampPan()
        if (!animated || canvas.getWidth() <= 0f) { glide = null; return }
        glide = floatArrayOf(panX, panY)
        panX = sx; panY = sy
    }

    private fun reveal(id: String) {
        val p = worldOf(id) ?: return
        val vw = viewW(); val vh = viewH()
        if (vw <= 0f) { framed = false; return }
        // The whole cell, label included - revealing just the tile left its name cut at the edge.
        val left = p[0] - (cellW - sizeOf(id)) / 2f
        val right = left + cellW
        val top = p[1] - 6f
        val bottom = p[1] + sizeOf(id) + labelRoom() + 6f
        var x = panX; var y = panY
        if (left < x + MARGIN) x = left - MARGIN
        if (right > x + vw - MARGIN) x = right - vw + MARGIN
        if (top < y + MARGIN) y = top - MARGIN
        if (bottom > y + vh - MARGIN) y = bottom - vh + MARGIN
        if (x != panX || y != panY) goTo(x, y)
    }

    private fun nodeAt(lx: Float, ly: Float): Node? {
        val wx = lx / zoomNow + panX
        val wy = ly / zoomNow + panY
        return nodes.firstOrNull { n ->
            val p = worldOf(n.quest.id) ?: return@firstOrNull false
            val sz = sizeOf(n.quest.id)
            wx >= p[0] - 2f && wx < p[0] + sz + 2f && wy >= p[1] - 2f && wy < p[1] + sz + 2f
        }
    }

    // ---- animation ------------------------------------------------------------------------------

    private fun animOf(id: String) = anim.getOrPut(id) { NodeAnim() }

    private fun approach(v: Float, target: Float, speed: Float, dt: Float): Float =
        if (!animated) target else v + (target - v) * (1f - exp(-speed * dt))

    private fun step(dt: Float) {
        time += dt
        shownFor += dt

        // camera: zoom ease around its anchor, pan glide, inertia
        if (zoomNow != zoomGoal) {
            val anchor = zoomAnchor
            val wx = anchor?.let { it[0] / zoomNow + panX }
            val wy = anchor?.let { it[1] / zoomNow + panY }
            zoomNow = approach(zoomNow, zoomGoal, ZOOM_SPEED, dt)
            if (abs(zoomNow - zoomGoal) < 0.002f) zoomNow = zoomGoal
            if (anchor != null && wx != null && wy != null) {
                panX = wx - anchor[0] / zoomNow
                panY = wy - anchor[1] / zoomNow
            }
            clampPan()
        }
        glide?.let { g ->
            panX = approach(panX, g[0], GLIDE_SPEED, dt)
            panY = approach(panY, g[1], GLIDE_SPEED, dt)
            if (abs(panX - g[0]) < 0.3f && abs(panY - g[1]) < 0.3f) { panX = g[0]; panY = g[1]; glide = null }
        }
        if (press == null && (abs(velX) > 1f || abs(velY) > 1f)) {
            panX += velX * dt; panY += velY * dt
            val decay = exp(-INERTIA * dt)
            velX *= decay; velY *= decay
            val bx = panX; val by = panY
            clampPan()
            if (bx != panX) velX = 0f
            if (by != panY) velY = 0f
        }

        // status changes: flash, and bursts down the wires of a quest that just finished
        for (n in nodes) {
            val a = animOf(n.quest.id)
            val st = displayStatus(n)
            val prev = a.status
            if (prev != null && prev != st && animated) {
                if (bursts.none { it.to == n.quest.id }) a.flash = 1f
                val wasDone = prev == BrassQuest.Status.READY || prev == BrassQuest.Status.CLAIMED
                if (finished(n.quest.id) && !wasDone) {
                    nodes.filter { n.quest.id in it.requires }.forEach { child -> bursts += Burst(n.quest.id, child.quest.id) }
                }
            }
            a.status = st
        }
        val landed = ArrayList<Burst>()
        for (b in bursts) {
            b.t += dt / BURST_TIME
            if (b.t >= 1f || !animated) landed += b
        }
        for (b in landed) { bursts.remove(b); animOf(b.to).flash = 1f }

        for (n in nodes) {
            val id = n.quest.id
            val a = animOf(id)
            a.flash = if (animated) (a.flash - dt * 1.6f).coerceAtLeast(0f) else 0f
            a.sel = approach(a.sel, if (selectedId == id) 1f else 0f, 14f, dt)
            val locked = displayStatus(n) == BrassQuest.Status.LOCKED || bursts.any { it.to == id }
            val target = if (locked) 0f else 1f
            a.unlock = if (a.unlock < 0f) target else approach(a.unlock, target, 8f, dt)
            val f = n.quest.fraction
            a.progress = if (a.progress < 0f) f else approach(a.progress, f, 6f, dt)
        }
    }

    /** 0..1 how far a node has popped in (column by column, a little stagger down the rows). */
    private fun appear(id: String): Float {
        if (!animated) return 1f
        val c = cells[id] ?: return 1f
        return ((shownFor - (c.col * COLUMN_DELAY + c.row * ROW_DELAY)) / POP_TIME).coerceIn(0f, 1f)
    }

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = t - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    // ---- painting -------------------------------------------------------------------------------

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        BrassCard.panel(m, x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), fill = Colors.UI_BACKGROUND, alpha = entranceFade)
    }

    private fun c(color: Color, a: Float = 1f): Color = withAlpha(color, entranceFade * a.coerceIn(0f, 1f))

    private inner class Canvas : UIComponent() {

        private var hover: Node? = null

        init {
            enableEffect(ScissorEffect())
            BrassTooltip.attachLazy(this, { hover?.quest?.title ?: "" }, { hover?.let { details(it) } })

            onMouseClick { e ->
                if (e.mouseButton != 0) return@onMouseClick
                press = floatArrayOf(e.absoluteX, e.absoluteY, panX, panY)
                lastDragNanos = System.nanoTime()
                dragged = false
                glide = null
                velX = 0f; velY = 0f
            }
            onMouseDrag { mx, my, btn ->
                val p = press ?: return@onMouseDrag
                if (btn != 0) return@onMouseDrag
                val ax = getLeft() + mx
                val ay = getTop() + my
                if (!dragged && abs(ax - p[0]) + abs(ay - p[1]) < DRAG_SLOP) return@onMouseDrag
                dragged = true
                val nx = p[2] - (ax - p[0]) / zoomNow
                val ny = p[3] - (ay - p[1]) / zoomNow
                // velocity for the glide after release, world units per second, smoothed
                val now = System.nanoTime()
                val dt = ((now - lastDragNanos) / 1e9f).coerceIn(0.001f, 0.1f)
                velX = velX * 0.6f + (nx - panX) / dt * 0.4f
                velY = velY * 0.6f + (ny - panY) / dt * 0.4f
                lastDragNanos = now
                panX = nx; panY = ny
                clampPan()
            }
            onMouseRelease {
                val p = press ?: return@onMouseRelease
                press = null
                // a drag that stopped before release should not fling
                if (!animated || System.nanoTime() - lastDragNanos > 80_000_000L) { velX = 0f; velY = 0f }
                if (dragged) return@onMouseRelease
                velX = 0f; velY = 0f
                val n = nodeAt(p[0] - getLeft(), p[1] - getTop()) ?: return@onMouseRelease
                selectedId = n.quest.id
                reveal(n.quest.id)
                onSelect?.invoke(n.quest)
            }
            onMouseScroll { e ->
                val (mx, my) = getMousePosition()
                zoomGoal = (zoomGoal * ZOOM_STEP.pow(e.delta.toFloat())).coerceIn(minZoom, maxZoom)
                zoomAnchor = floatArrayOf(mx - getLeft(), my - getTop())
                glide = null
                if (!animated) settleZoom()
                e.stopPropagation()
            }
        }

        private fun details(n: Node): String {
            val q = n.quest
            val st = displayStatus(n)
            val parts = ArrayList<String>()
            BrassQuestCard.defaultStatusLabel(st)?.let { parts += it.lowercase().replaceFirstChar { c -> c.uppercase() } }
            if (st == BrassQuest.Status.LOCKED) {
                (lockReasonOf(q) ?: q.lockedReason)?.let { parts += it }
            } else {
                val req = q.required.ifEmpty { q.objectives }
                if (req.isNotEmpty()) parts += "${req.count { it.done }}/${req.size} objectives"
            }
            q.giver?.let { parts += it }
            return parts.joinToString(" · ")
        }

        override fun draw(matrixStack: UMatrixStack) {
            beforeDraw(matrixStack)
            solve()
            // Any skipped frame means it was out of view (an inactive tab, a covered or reopened
            // screen): coming back replays the entrance.
            val frame = BrassClock.frame
            if (lastFrame >= 0L && frame - lastFrame > 1L) replayEntrance()
            lastFrame = frame
            if (!framed && getWidth() > 0f) {
                framed = true
                clampPan()
                selectedId?.let { id ->
                    val save = animated
                    animated = false
                    reveal(id)
                    animated = save
                }
            }
            val dt = if (BrassClock.driven) BrassClock.dt else 0.016f
            step(dt)

            val (mx, my) = getMousePosition()
            hover = if (isHovered() && press == null) nodeAt(mx - getLeft(), my - getTop()) else null
            for (n in nodes) {
                val a = animOf(n.quest.id)
                a.hover = approach(a.hover, if (hover === n) 1f else 0f, 16f, dt)
            }
            when {
                dragged && press != null -> BrassCursor.request(BrassCursor.Kind.MOVE)
                hover != null -> BrassCursor.request(BrassCursor.Kind.HAND)
            }

            val m = matrixStack
            m.push()
            m.translate(floor(getLeft()), floor(getTop()), 0f)
            m.scale(zoomNow, zoomNow, 1f)
            m.translate(-panX, -panY, 0f)

            if (showGrid) drawGrid(m)
            val wires = BrassWire.Frame(m, zoomNow, time, panX - 4f, panY - 4f, panX + viewW() + 4f, panY + viewH() + 4f)
            for (n in nodes) for (r in n.requires) if (byId.containsKey(r)) drawWire(wires, r, n)
            // hovered and selected nodes last, so their lift and rings sit on top
            for (n in nodes.sortedBy { animOf(it.quest.id).let { a -> a.hover + a.sel } }) drawNode(m, n)
            for (n in nodes) drawNubs(m, n)

            m.pop()
            super.draw(matrixStack)
        }

        private fun drawGrid(m: UMatrixStack) {
            val fade = ((zoomNow - 0.6f) / 0.3f).coerceIn(0f, 1f)
            if (fade <= 0f) return
            val col = c(Colors.UI_INNER_BORDER, 0.6f * fade)
            val x0 = floor(panX / GRID) * GRID
            val y0 = floor(panY / GRID) * GRID
            val x1 = panX + viewW()
            val y1 = panY + viewH()
            val batch = BrassPaint.QuadBatch(m)
            var gx = x0
            while (gx <= x1) {
                var gy = y0
                while (gy <= y1) {
                    batch.rect(gx, gy, gx + 1f, gy + 1f, col)
                    gy += GRID
                }
                gx += GRID
            }
            batch.flush()
        }

        /** The node editor's own wire ([BrassWire]), port to port, coloured and energised by quest state. */
        private fun drawWire(f: BrassWire.Frame, fromId: String, to: Node) {
            val a = worldOf(fromId) ?: return
            val b = worldOf(to.quest.id) ?: return
            val toId = to.quest.id
            // Draws in from the prerequisite once that node has popped.
            val reveal = if (!animated) 1f else ((appear(fromId) - 0.55f) / 0.45f).coerceIn(0f, 1f)
            if (reveal <= 0f) return

            val fromDone = finished(fromId)
            val toDone = finished(toId)
            val toSt = displayStatus(to)
            val live = fromDone && !toDone && toSt != BrassQuest.Status.LOCKED && toSt != BrassQuest.Status.FAILED
            val lit = (animOf(fromId).hover + animOf(toId).hover + maxOf(animOf(fromId).sel, animOf(toId).sel)).coerceIn(0f, 1f)
            val burst = bursts.firstOrNull { it.from == fromId && it.to == toId }

            val sa = sizeOf(fromId)
            val sb = sizeOf(toId)
            val vertical = direction == Direction.DOWN
            // port to port: from just past the parent's out nub to just before the child's in nub
            val x0 = if (vertical) a[0] + floor(sa / 2f) - 1f else a[0] + sa + NUB
            val y0 = if (vertical) a[1] + sa + NUB else a[1] + floor(sa / 2f) - 1f
            val x3 = if (vertical) b[0] + floor(sb / 2f) - 1f else b[0] - NUB - 2f
            val y3 = if (vertical) b[1] - NUB - 2f else b[1] + floor(sb / 2f) - 1f

            val saved = net.swzo.brass.ui.kit.base.BrassAmbientFade.current
            net.swzo.brass.ui.kit.base.BrassAmbientFade.current = saved * entranceFade
            // Hover/selection brighten the wire in place - a halo or glow widened it into a band.
            fun lift(col: Color) = if (lit > 0f) Colors.mix(col, Colors.UI_TEXT_HOVER, lit * 0.35f) else col
            BrassWire.draw(
                f, x0, y0, x3, y3,
                color = lift(if (fromDone) Colors.UI_ACCENT else Colors.UI_ELEMENT_BORDER_HOVER),
                // finished prerequisites blend into the child's own colour (brass into READY, red into FAILED)
                colorEnd = if (fromDone && !toDone) lift(statusColor(toSt)) else null,
                trail = if (animated) 5 else 0,
                flash = burst?.let { 1f - it.t } ?: 0f,
                // live wires carry a strong signal, finished paths a faint one, locked ones none
                strength = when {
                    !animated -> if (live) 1f else 0f
                    live -> 1f
                    fromDone && toDone -> 0.2f
                    else -> 0f
                },
                moteFade = if (animated) BrassWire.smoothstep(0.3f, 0.5f, zoomNow) else 0f,
                reveal = reveal,
                vertical = vertical,
            )
            net.swzo.brass.ui.kit.base.BrassAmbientFade.current = saved
        }

        /** The square ports where wires meet a tile, like the node editor's: in on one side, out on the other. */
        private fun drawNubs(m: UMatrixStack, n: Node) {
            val id = n.quest.id
            val p = worldOf(id) ?: return
            val ap = appear(id)
            if (ap < 0.6f) return
            val alpha = ((ap - 0.6f) / 0.4f).coerceIn(0f, 1f)
            val s = sizeOf(id)
            val half = floor(s / 2f)
            val lift = floor(2f * animOf(id).hover + 0.5f)
            val vertical = direction == Direction.DOWN
            val st = displayStatus(n)
            if (n.requires.any { byId.containsKey(it) }) {
                val col = when (st) {
                    BrassQuest.Status.LOCKED -> Colors.UI_ELEMENT_BORDER_HOVER
                    BrassQuest.Status.CLAIMED -> Colors.UI_ACCENT
                    else -> statusColor(st)
                }
                if (vertical) nub(m, p[0] + half - 2f, p[1] - lift - NUB, col, alpha)
                else nub(m, p[0] - NUB, p[1] - lift + half - 2f, col, alpha)
            }
            if (nodes.any { id in it.requires }) {
                val col = if (finished(id)) Colors.UI_ACCENT else Colors.UI_ELEMENT_BORDER_HOVER
                if (vertical) nub(m, p[0] + half - 2f, p[1] - lift + s, col, alpha)
                else nub(m, p[0] + s, p[1] - lift + half - 2f, col, alpha)
            }
        }

        private fun nub(m: UMatrixStack, x: Float, y: Float, col: Color, alpha: Float) {
            BrassPaint.rect(m, x - 1f, y - 1f, x + NUB + 1f, y + NUB + 1f, c(Colors.UI_OUTER_BORDER, alpha))
            BrassPaint.rect(m, x, y, x + NUB, y + NUB, c(col, alpha))
            BrassPaint.rect(m, x, y, x + NUB, y + 1f, c(Colors.mix(col, Color.WHITE, 0.35f), alpha))
        }

        /**
         * A milestone's setting: an outer brass ring with diamond studs on each side, and - once it is
         * unlocked - two sparks orbiting the tile.
         */
        private fun milestoneFrame(m: UMatrixStack, x1: Float, y1: Float, s: Float, lockedMix: Float, alpha: Float) {
            val ringCol = c(Colors.mix(BrassQuestCard.gold(), Colors.UI_ELEMENT_BORDER_HOVER, lockedMix), alpha)
            BrassPaint.border(m, x1 - 3f, y1 - 3f, x1 + s + 3f, y1 + s + 3f, ringCol)
            val cx = floor(x1 + s / 2f)
            val cy = floor(y1 + s / 2f)
            val studCol = c(Colors.mix(BrassQuestCard.gold(), Colors.UI_TEXT_DARK, lockedMix), alpha)
            for ((sx, sy) in listOf(cx to y1 - 3f, cx to y1 + s + 3f, x1 - 3f to cy, x1 + s + 3f to cy)) {
                BrassPaint.rect(m, sx - 1f, sy - 2f, sx + 1f, sy + 2f, studCol)
                BrassPaint.rect(m, sx - 2f, sy - 1f, sx + 2f, sy + 1f, studCol)
            }
            if (!animated || lockedMix > 0.5f) return
            val r = s / 2f + 6f
            for (k in 0 until 2) {
                val ang = time * 1.4f + k * PI.toFloat()
                val px = floor(cx + kotlin.math.cos(ang) * r)
                val py = floor(cy + sin(ang) * r)
                BrassPaint.rect(m, px - 1f, py - 1f, px + 2f, py + 2f, c(BrassQuestCard.gold(), 0.35f * alpha))
                BrassPaint.rect(m, px, py, px + 1f, py + 1f, c(Colors.UI_TEXT_HOVER, alpha))
            }
        }

        private fun drawNode(m: UMatrixStack, n: Node) {
            val id = n.quest.id
            val p = worldOf(id) ?: return
            val ap = appear(id)
            if (ap <= 0f) return
            val a = animOf(id)
            val s = sizeOf(id)
            val ms = n.quest.milestone
            val st = displayStatus(n)
            val tint = statusColor(st)
            val lockedMix = 1f - a.unlock.coerceIn(0f, 1f)
            val alpha = ap
            val hot = a.hover

            val x1 = p[0]
            val y1 = p[1] - floor(2f * hot + 0.5f)
            val cx = x1 + s / 2f
            val cy = y1 + s / 2f
            // pop-in, plus a bounce when the node flashes (unlock, status change)
            val bounce = 1f + 0.18f * sin(a.flash * PI.toFloat()) * a.flash
            val scale = (0.4f + 0.6f * easeOutBack(ap)) * bounce
            m.push()
            m.translate(cx, cy, 0f)
            m.scale(scale, scale, 1f)
            m.translate(-cx, -cy, 0f)

            if (st == BrassQuest.Status.READY && animated) {
                val pulse = 0.5f + 0.5f * sin(time * 3.2f)
                BrassPaint.border(m, x1 - 3f, y1 - 3f, x1 + s + 3f, y1 + s + 3f, c(Colors.BRASS_300, (0.2f + 0.4f * pulse) * alpha))
            }
            if (a.sel > 0.02f) {
                val breathe = if (animated) 0.75f + 0.25f * sin(time * 2.6f) else 1f
                val grow = 6f - 3f * a.sel
                BrassPaint.border(m, x1 - grow, y1 - grow, x1 + s + grow, y1 + s + grow, c(Colors.UI_ACCENT_BRIGHT, a.sel * breathe * alpha))
            }
            if (a.flash > 0f) {
                val r = 2f + 9f * (1f - a.flash)
                BrassPaint.border(m, x1 - r, y1 - r, x1 + s + r, y1 + s + r, c(Colors.UI_ACCENT_BRIGHT, a.flash * alpha))
            }

            val baseFill = when {
                ms -> Colors.mix(Colors.UI_ELEMENT_BG, BrassQuestCard.goldDeep(), 0.4f * (1f - lockedMix))
                st == BrassQuest.Status.CLAIMED -> Colors.UI_INNER_BG
                else -> Colors.UI_ELEMENT_BG
            }
            val fill = Colors.mix(baseFill, Colors.UI_ELEMENT_BG_HOVER, hot)
            val ring = if (ms) Colors.mix(BrassQuestCard.goldDim(), BrassQuestCard.gold(), 0.6f * (1f - lockedMix))
                else Colors.mix(Colors.UI_INNER_BORDER, tint, 0.2f + 0.5f * (1f - lockedMix))
            if (ms) milestoneFrame(m, x1, y1, s, lockedMix, alpha)
            BrassCard.dropShadow(m, x1, y1, x1 + s, y1 + s, entranceFade * alpha * (0.35f + 0.65f * hot))
            BrassCard.panel(m, x1, y1, x1 + s, y1 + s, fill = fill, border = Colors.mix(ring, Colors.UI_TEXT_HOVER, hot * 0.35f), alpha = entranceFade * alpha)
            // a soft bevel: light along the top inner edge, a touch of shade along the bottom
            BrassPaint.rect(m, x1 + 2f, y1 + 2f, x1 + s - 2f, y1 + 3f, c(Colors.mix(fill, Colors.UI_TEXT_HOVER, 0.1f), alpha))
            BrassPaint.rect(m, x1 + 2f, y1 + s - 3f, x1 + s - 2f, y1 + s - 2f, c(Colors.mix(fill, Color.BLACK, 0.25f), alpha))

            // the quest's picture cross-fades with the lock as the node unlocks
            if (lockedMix > 0.02f) {
                BrassIcons.draw(m, BrassIcons.LOCK, x1 + floor((s - 8f) / 2f), y1 + floor((s - 8f) / 2f), 8f, c(Colors.UI_TEXT_DARK, lockedMix * alpha))
            }
            if (lockedMix < 0.98f) {
                val iconTint = if (st == BrassQuest.Status.CLAIMED) Colors.mix(Colors.UI_ACCENT, Colors.UI_TEXT_DARK, 0.45f) else tint
                val k = (1f - lockedMix) * alpha
                QuestIcon.draw(m, n.quest, x1, y1, s, if (s >= 22f) 16f else 8f, c(iconTint, k), entranceFade * k, st == BrassQuest.Status.CLAIMED)
            }

            if (st == BrassQuest.Status.ACTIVE && a.progress >= 0f) {
                val bx1 = x1 + 3f; val bx2 = x1 + s - 3f; val by = y1 + s - 4f
                BrassPaint.rect(m, bx1, by, bx2, by + 2f, c(Colors.UI_OUTER_BORDER, alpha))
                BrassPaint.rect(m, bx1, by, bx1 + (bx2 - bx1) * a.progress, by + 2f, c(Colors.UI_ACCENT_BRIGHT, alpha))
            }

            val badge: Pair<BrassIcons.Icon, Color>? = when (st) {
                BrassQuest.Status.CLAIMED -> BrassIcons.CHECK to Colors.BRASS_600
                BrassQuest.Status.READY -> BrassIcons.GIFT to Colors.BRASS_600
                BrassQuest.Status.FAILED -> BrassIcons.CLOSE to Colors.PROGRESS_FAIL
                else -> null
            }
            badge?.let { (icon, bg) ->
                val bob = if (st == BrassQuest.Status.READY && animated) floor(sin(time * 4f) * 1.5f) else 0f
                val bx = x1 + s - 6f
                val by = y1 - 4f + bob
                BrassPaint.rect(m, bx, by, bx + 10f, by + 10f, c(Colors.UI_OUTER_BORDER, alpha))
                BrassPaint.rect(m, bx + 1f, by + 1f, bx + 9f, by + 9f, c(bg, alpha))
                BrassIcons.draw(m, icon, bx + 1f, by + 1f, 8f, c(Colors.UI_TEXT_HOVER, alpha))
            }
            m.pop()

            // labels fade out when zoomed far out, like the node editor's interior chrome
            val labelFade = ((zoomNow - 0.6f) / 0.25f).coerceIn(0f, 1f) * alpha
            if (showLabels && labelFade > 0.02f) {
                val label = BrassFont.fit(this, n.quest.title, cellW - 6f)
                val lw = BrassFont.width(this, label)
                val col = when {
                    a.sel > 0.5f -> Colors.UI_ACCENT_BRIGHT
                    st == BrassQuest.Status.LOCKED -> Colors.UI_TEXT_DARK
                    ms -> Colors.mix(BrassQuestCard.gold(), Colors.UI_TEXT_HOVER, hot * 0.5f)
                    else -> Colors.mix(Colors.UI_TEXT, Colors.UI_TEXT_HOVER, hot)
                }
                // milestones clear their ring and its bottom stud
                val ly = p[1] + s + LABEL_GAP + (if (ms) 4f else 0f)
                BrassFont.draw(m, this, label, floor(x1 + (s - lw) / 2f), ly, c(col, labelFade))
            }
        }
    }

    private fun settleZoom() {
        val anchor = zoomAnchor
        if (anchor != null) {
            val wx = anchor[0] / zoomNow + panX
            val wy = anchor[1] / zoomNow + panY
            zoomNow = zoomGoal
            panX = wx - anchor[0] / zoomNow
            panY = wy - anchor[1] / zoomNow
        } else {
            zoomNow = zoomGoal
        }
        clampPan()
    }

    companion object : BrassDemoSource {
        private const val INSET = 2f
        private const val NUB = 4f
        private const val MILESTONE_GROW = 8f
        private const val MARGIN = 10f
        private const val LABEL_GAP = 4f
        private const val DRAG_SLOP = 3f
        private const val GRID = 12f
        private const val ZOOM_STEP = 1.18f
        private const val ZOOM_SPEED = 14f
        private const val GLIDE_SPEED = 9f
        private const val INERTIA = 5f
        private const val MOTE_SPEED = 0.9f
        private const val BURST_TIME = 0.55f
        private const val POP_TIME = 0.32f
        private const val COLUMN_DELAY = 0.11f
        private const val ROW_DELAY = 0.035f

        override fun demo() = BrassDemo("quest-tree", "Quest tree", 300f, 150f) {
            sampleTree()
        }

        /** The sample tree the demos, gallery and journal share. */
        @JvmStatic
        @JvmOverloads
        fun sampleTree(onSelect: ((BrassQuest) -> Unit)? = null): BrassQuestTree {
            fun q(id: String, title: String, icon: BrassIcons.Icon, item: String, status: BrassQuest.Status, goal: String, target: Int, done: Int = 0) =
                BrassQuest(id, title, icon = icon, status = status).objective(goal, target = target, progress = done).apply { iconItem = item }
            val tree = BrassQuestTree(onSelect)
            tree.node(q("wood", "Getting Wood", BrassIcons.AXE, "minecraft:oak_log", BrassQuest.Status.CLAIMED, "Chop logs", 16, 16))
            tree.node(q("stone", "Stone Age", BrassIcons.PICKAXE, "minecraft:stone_pickaxe", BrassQuest.Status.CLAIMED, "Mine cobblestone", 32, 32).apply { milestone = true }, listOf("wood"))
            tree.node(q("farm", "First Harvest", BrassIcons.BREAD, "minecraft:wheat", BrassQuest.Status.READY, "Harvest wheat", 24, 24), listOf("wood"))
            tree.node(BrassQuestCard.sample().apply { tracked = true }, listOf("stone"))
            tree.node(q("hunt", "Pack Hunt", BrassIcons.SWORD, "minecraft:iron_sword", BrassQuest.Status.ACTIVE, "Defeat wolves", 10, 6), listOf("farm"))
            tree.node(q("forge", "The Forge", BrassIcons.HAMMER, "minecraft:blast_furnace", BrassQuest.Status.AVAILABLE, "Build a blast furnace", 1), listOf("iron-age"))
            tree.node(q("armor", "Iron Armor", BrassIcons.ARMOR, "minecraft:iron_chestplate", BrassQuest.Status.AVAILABLE, "Craft armor pieces", 4), listOf("iron-age", "hunt"))
            tree.node(q("diamond", "Diamonds!", BrassIcons.GEM, "minecraft:diamond", BrassQuest.Status.AVAILABLE, "Mine diamonds", 3), listOf("forge"))
            tree.node(q("nether", "Gate of Embers", BrassIcons.PORTAL, "minecraft:flint_and_steel", BrassQuest.Status.AVAILABLE, "Light a portal", 1).apply { milestone = true }, listOf("diamond"))
            tree.node(q("enchant", "Enchanter", BrassIcons.ENCHANT, "minecraft:enchanting_table", BrassQuest.Status.AVAILABLE, "Enchant a tool", 1), listOf("diamond", "armor"))
            tree.node(q("relay", "Beacon Relay", BrassIcons.BEACON, "minecraft:beacon", BrassQuest.Status.FAILED, "Light the beacons", 3, 1), listOf("hunt"))
            tree.select("iron-age")
            return tree
        }
    }
}
