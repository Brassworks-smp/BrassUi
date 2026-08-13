package net.swzo.brass.ui.kit.input

import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.layout.BrassCull
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassKeycap
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.platform.BrassPlatform
import net.swzo.brass.ui.kit.surface.BrassTooltip
import kotlin.math.floor
import java.awt.Color
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels

private val HIGHLIGHT_TINT: Color get() = Colors.mix(Colors.UI_ACCENT, Color.WHITE, 0.62f)

/**
 * A grid of item slots with drag-and-drop between them. Supports the vanilla gestures (click,
 * right-click half, shift-click, spread drags) and links several grids into one container via
 * [linkTo]. Slots are painted directly rather than one widget per slot, so the grid owns its drag
 * outright. Container-backed hosts approve changes through [canPlace]/[onQuickMove]/[onChange].
 */
open class BrassInventoryGrid(
    var columns: Int = 9,
    var rows: Int = 3,
    var slotSize: Float = 18f,
    var gap: Float = 1f,
) : BrassWidget(BrassAccent.DEFAULT) {

    data class Slot(val itemId: String, val count: Int = 1)

    private val contents = HashMap<Int, Slot>()

    var link: BrassInventoryLink = BrassInventoryLink()
        private set

    fun linkTo(other: BrassInventoryGrid) {
        link.members.remove(this)
        link = other.link
        if (!link.members.contains(this)) link.members.add(this)
    }

    var canPlace: (index: Int, slot: Slot) -> Boolean = { _, _ -> true }

    var onChange: (() -> Unit)? = null

    /**
     * Optional full control over a drag that ends with the cursor over one of this grid's slots.
     * When set and returning true, the drop is taken HERE instead of the generic spread/split
     * landing - the carried stack is then cleared. The old behavior is untouched when this is left
     * as the default, so hosts that want click-like single-item drops (a frequency selector, say)
     * can have them without changing how every other grid distributes stacks.
     */
    var onDrop: (index: Int, held: Slot) -> Boolean = { _, _ -> false }

    var onQuickMove: (from: BrassInventoryGrid, index: Int) -> Boolean = { from, index ->
        val target = link.members.firstOrNull { it !== from }
        if (target == null) false else from.transferTo(target, index)
    }

    var onClick: ((index: Int, slot: Slot?, button: Int) -> Unit)? = null

    var interactive: Boolean = true

    var showCounts: Boolean = true

    /**
     * Force every stored stack to a count of 1. For a selector that only cares about *which* item is
     * in the slot: shift-clicking a stack of 64 still lands a single item, and merges never build up.
     */
    var singleStack: Boolean = false

    var slotTints: Map<Int, Color> = emptyMap()

    var keycapSlots: Boolean = false

    var itemTooltip: ((Slot, Int) -> List<Pair<String, Color>>?)? = null

    var showItemTooltips: Boolean = true

    var highlight: ((Slot, Int) -> Boolean)? = null

    /**
     * Stretch the block of slots to the component's full width, deriving [slotSize] from it each frame
     * instead of using the fixed value. For a grid that should fill the card it sits in - a picker's
     * inventory that must span the whole modal - rather than sitting at a fixed size with slack around
     * it. [slotSize] is then the maximum (fallback) used before the layout has given the grid a width.
     */
    var fillWidth: Boolean = false

    protected open fun sz(): Float =
        if (fillWidth && getWidth() > 0f)
            ((getWidth() - pad() * 2f - (columns - 1) * gap) / columns).coerceAtLeast(1f)
        else slotSize

    init {
        // The grid paints its own card around the slot block (see drawContent), so the base class must
        // paint nothing. Left on the default keycap it drew a second, *full-width* card behind
        // everything - the component's box is whatever the layout handed it, not the size of the slots -
        // and the real card then looked like a small panel floating in the middle of a big empty one.
        chrome = BrassChrome.NONE
        link.members.add(this)

        // Wire the item tooltip ONCE, here, with a supplier that reads the slot under the cursor each
        // frame ([hoverIndex], published by drawContent). BrassTooltip installs its enter/leave
        // listeners on the first attach; doing that lazily from the draw loop only ran on a frame the
        // cursor was already inside a slot, so the listeners missed the very entry that should have
        // shown the first tooltip. An empty result (no supplier, empty slot) simply draws nothing.
        BrassTooltip.attachRich(this, {
            val tip = itemTooltip
            val i = hoverIndex
            val slot = if (tip != null && i in 0 until slotCount) contents[i] else null
            if (slot == null) return@attachRich emptyList()
            val lines = tip?.invoke(slot, i)
                ?: if (showItemTooltips) BrassPlatform.current?.itemTooltip(slot.itemId) else null
            lines ?: emptyList()
        })

        onMouseClick { e ->
            if (!interactive) return@onMouseClick
            // Measured from the slot block's own origin, not the component's - the block is inset by
            // the card and aligned inside a box whose width the layout chose, so the two differ on
            // both axes. Same arithmetic as slotUnderCursor, deliberately.
            val index = slotAt(getLeft() + e.relativeX - originX(), getTop() + e.relativeY - originY())
            if (index < 0) return@onMouseClick

            val now = System.currentTimeMillis()
            val doubled = index == lastClickSlot && now - lastClickAt < BrassMetrics.DOUBLE_CLICK_MS
            lastClickSlot = index
            lastClickAt = now

            if (UKeyboard.isShiftKeyDown()) {
                // Shift + double-click sends *every* stack of that type, which is the Inventory Tweaks
                // gesture for clearing out one item without hunting for each stack of it.
                if (doubled) moveAllMatching(index) else if (contents[index] != null) {
                    onQuickMove(this@BrassInventoryGrid, index)
                }
                // …and a shift-drag from here sweeps whole rows, which is the point: emptying a row
                // is one motion instead of nine clicks.
                link.beginSweep()
                link.paint(this@BrassInventoryGrid, index)
                onClick?.invoke(index, contents[index], e.mouseButton)
                return@onMouseClick
            }

            // Double-click with a stack held gathers every loose stack of the same item into it, up to
            // one full stack - vanilla's behaviour, and the fastest way to consolidate.
            if (doubled && e.mouseButton == 0 && link.held != null) {
                gatherInto()
                onClick?.invoke(index, contents[index], e.mouseButton)
                return@onMouseClick
            }

            when (e.mouseButton) {
                0 -> leftClick(index)
                1 -> rightClick(index)
            }
            // A press with a stack held begins a drag. It only *becomes* one once a second slot is
            // painted - see BrassInventoryLink.endDrag - so a plain click is unaffected.
            if (link.held != null) {
                link.beginDrag(
                    if (e.mouseButton == 1) BrassInventoryLink.Drag.SINGLE else BrassInventoryLink.Drag.EVEN,
                )
                link.paint(this@BrassInventoryGrid, index)
            }
            onClick?.invoke(index, contents[index], e.mouseButton)
        }

        // Elementa broadcasts drags to the entire tree, which is exactly what makes a cross-grid drag
        // work: this grid is told about a drag that began in its neighbour, and paints whichever of
        // its own slots the cursor is over.
        onMouseDrag { _, _, _ ->
            if (!interactive || link.drag == BrassInventoryLink.Drag.NONE) return@onMouseDrag
            @Suppress("UNUSED_EXPRESSION")
            val index = slotUnderCursor()
            if (index >= 0) link.paint(this@BrassInventoryGrid, index)
        }

        // Also broadcast, so whichever grid the cursor ended over finishes the drag. Idempotent.
        onMouseRelease {
            if (link.drag != BrassInventoryLink.Drag.NONE) {
                link.endDrag()
                onChange?.invoke()
                // A drag that ended with a remainder over dead space (or a drag that never reached a
                // second slot) leaves the stack on the cursor; release over nothing discards it, so a
                // carried stack is never stuck to the pointer.
                if (link.held != null && !link.cursorOverSlot()) link.hold(null)
            } else if (externalCarry) {
                // A carry started outside a grid (an item dragged from a catalogue): drop it into
                // whichever linked member's slot the cursor is over on release, so it behaves like a
                // real drag-and-drop even into a virtualized sibling list.
                val held = link.held
                val target = if (interactive && held != null) {
                    link.members.firstOrNull { it.cursorSlot() >= 0 }
                } else {
                    null
                }
                val index = target?.cursorSlot() ?: -1
                if (held != null && target != null && index >= 0 && target.canPlace(index, held)) {
                    val previous = target.slot(index)
                    target.putDirect(index, held)
                    link.hold(previous)
                    onChange?.invoke()
                } else if (held != null && !link.cursorOverSlot()) {
                    // Released over empty UI rather than a slot: discard the carried stack instead of
                    // leaving it stuck to the cursor.
                    link.hold(null)
                }
                externalCarry = false
            } else if (interactive && link.held != null && !link.cursorOverSlot()) {
                // A stack picked up from a slot and released over dead space: discard it. Releasing
                // over any linked grid's slot keeps it (a press there resolves it normally).
                link.hold(null)
            }
        }
    }

    private var externalCarry = false

    fun carry(slot: Slot?) {
        link.hold(slot)
        externalCarry = slot != null
    }

    private var lastClickAt = 0L
    private var lastClickSlot = -1

    protected var hoverIndex = -1

    fun moveAllMatching(index: Int) {
        val id = contents[index]?.itemId ?: return
        for (i in (0 until slotCount).toList()) {
            if (contents[i]?.itemId == id) onQuickMove(this, i)
        }
    }

    fun gatherInto() {
        val held = link.held ?: return
        val limit = link.maxStack(held.itemId)
        var have = held.count
        if (have >= limit) return

        val sources = link.members.flatMap { grid ->
            (0 until grid.slotCount).mapNotNull { i ->
                val slot = grid.slot(i) ?: return@mapNotNull null
                if (slot.itemId != held.itemId) null else Triple(grid, i, slot.count)
            }
        }.sortedBy { it.third }

        for ((grid, i, count) in sources) {
            if (have >= limit) break
            val take = minOf(count, limit - have)
            have += take
            val left = count - take
            grid.putDirect(i, if (left > 0) Slot(held.itemId, left) else null)
            grid.onChange?.invoke()
        }
        link.hold(Slot(held.itemId, have))
    }

    internal fun quickMove(index: Int) {
        if (contents[index] != null) onQuickMove(this, index)
    }


    private fun leftClick(index: Int) {
        val held = link.held
        val here = contents[index]
        when {
            held == null -> {
                if (here != null) { contents.remove(index); link.hold(here) }
            }
            here == null -> {
                if (canPlace(index, held)) { putDirect(index, held); link.hold(null) }
            }
            here.itemId == held.itemId -> {
                // Merge as much as fits; whatever will not fit stays on the cursor.
                val limit = link.maxStack(held.itemId)
                val move = minOf(held.count, (limit - here.count).coerceAtLeast(0))
                if (move > 0) {
                    putDirect(index, Slot(here.itemId, here.count + move))
                    link.hold(if (held.count - move > 0) Slot(held.itemId, held.count - move) else null)
                }
            }
            else -> {
                // Swap - the cursor takes what was in the slot and vice versa.
                if (canPlace(index, held)) { putDirect(index, held); link.hold(here) }
            }
        }
        onChange?.invoke()
    }

    private fun rightClick(index: Int) {
        val held = link.held
        val here = contents[index]
        when {
            held == null -> {
                if (here != null) {
                    val take = (here.count + 1) / 2
                    val leave = here.count - take
                    if (leave > 0) putDirect(index, Slot(here.itemId, leave)) else contents.remove(index)
                    link.hold(Slot(here.itemId, take))
                }
            }
            here == null -> {
                if (canPlace(index, held)) {
                    putDirect(index, Slot(held.itemId, 1))
                    link.hold(if (held.count > 1) Slot(held.itemId, held.count - 1) else null)
                }
            }
            here.itemId == held.itemId -> {
                if (here.count < link.maxStack(held.itemId)) {
                    putDirect(index, Slot(here.itemId, here.count + 1))
                    link.hold(if (held.count > 1) Slot(held.itemId, held.count - 1) else null)
                }
            }
        }
        onChange?.invoke()
    }

    fun transferTo(target: BrassInventoryGrid, index: Int): Boolean {
        val stack = contents[index] ?: return false
        val limit = link.maxStack(stack.itemId)
        var remaining = stack.count

        for (pass in 0..1) {
            for (i in 0 until target.slotCount) {
                if (remaining <= 0) break
                val there = target.slot(i)
                // Pass 0 merges into existing stacks; pass 1 fills empties.
                if (pass == 0 && (there == null || there.itemId != stack.itemId)) continue
                if (pass == 1 && there != null) continue
                if (!target.canPlace(i, Slot(stack.itemId, remaining))) continue
                val already = there?.count ?: 0
                val give = minOf(remaining, (limit - already).coerceAtLeast(0))
                if (give <= 0) continue
                target.putDirect(i, Slot(stack.itemId, already + give))
                // …and make it visibly come *from* the slot it left. Registered after putDirect, which
                // starts no landing of its own here: a shift-click carries nothing, so the cursor-based
                // origin it would use is null. Positions are absolute, so a flight into the linked grid
                // crosses the gap between the two widgets rather than being clamped to either.
                target.beginLandingFrom(i, slotX(index), slotY(index))
                remaining -= give
            }
        }

        if (remaining == stack.count) return false
        if (remaining > 0) putDirect(index, Slot(stack.itemId, remaining)) else contents.remove(index)
        onChange?.invoke()
        target.onChange?.invoke()
        return true
    }

    internal fun putDirect(index: Int, slot: Slot?) {
        if (index !in 0 until slotCount) return
        val normalized = if (slot == null || slot.count <= 0) null
        else if (singleStack) Slot(slot.itemId, 1)
        else slot
        if (normalized == null) {
            contents.remove(index)
            landings.remove(index)
        } else {
            contents[index] = normalized
            beginLanding(index)
        }
    }

    open val slotCount: Int get() = columns * rows

    enum class Align { LEFT, CENTER, RIGHT }

    /**
     * How the slots are placed inside the component's box.
     * A grid is almost always laid out by something that hands it the full width - `BrassForm.addField`
     * sets `width = 100.percent()` unconditionally - so the block of slots is narrower than the
     * component and something has to decide where the slack goes. Everything below (the card, the
     * hit-testing, the wells) is measured from [originX], so the three can never disagree.
     * Left by default, so a grid lines up with the other controls in a form instead of sitting adrift
     * in the middle of a row whose width it never asked for.
     */
    var align: Align = Align.LEFT

    var card: Boolean = true

    protected open fun pad(): Float = if (card) CARD_PAD else 0f

    protected open fun originX(): Float {
        val slack = (getWidth() - contentWidth()).coerceAtLeast(0f)
        val cardLeft = getLeft() + when (align) {
            Align.LEFT -> 0f
            Align.CENTER -> slack / 2f
            Align.RIGHT -> slack
        }
        return cardLeft + pad()
    }

    protected open fun originY(): Float = getTop() + pad()


    fun setSlot(index: Int, slot: Slot?) {
        if (index !in 0 until slotCount) return
        if (slot == null) contents.remove(index) else contents[index] = slot
    }

    fun slot(index: Int): Slot? = contents[index]

    fun setContents(next: Map<Int, Slot>) {
        contents.clear()
        next.forEach { (i, s) -> if (i in 0 until slotCount) contents[i] = s }
    }

    fun clear() = contents.clear()


    private fun slotsWidth(): Float = columns * sz() + (columns - 1) * gap

    private fun slotsHeight(): Float = rows * sz() + (rows - 1) * gap

    fun contentWidth(): Float = slotsWidth() + pad() * 2

    fun contentHeight(): Float = slotsHeight() + pad() * 2 + if (card) CARD_FOOT else 0f

    protected open fun slotAt(localX: Float, localY: Float): Int {
        val slot = sz()
        val pitch = slot + gap
        val col = floor(localX / pitch).toInt()
        val row = floor(localY / pitch).toInt()
        if (col !in 0 until columns || row !in 0 until rows) return -1
        // Reject the gap between slots, so dropping on a seam is a miss rather than a neighbour.
        if (localX - col * pitch > slot || localY - row * pitch > slot) return -1
        return row * columns + col
    }

    protected open fun slotUnderCursor(): Int {
        val (mx, my) = getMousePosition()
        if (!BrassCull.visible(this)) return -1
        return slotAt(mx - originX(), my - originY())
    }

    protected open fun slotX(index: Int): Float = originX() + (index % columns) * (sz() + gap)
    protected open fun slotY(index: Int): Float = originY() + (index / columns) * (sz() + gap)

    internal fun cursorSlot(): Int = slotUnderCursor()

    internal fun cursorOverSlot(): Boolean = cursorSlot() >= 0


    private var hoverAmount = FloatArray(0)
    private var paintAmount = FloatArray(0)

    private var heldX: Float? = null
    private var heldY: Float? = null

    private val landings = HashMap<Int, Landing>()

    private class Landing(val fromX: Float, val fromY: Float) {
        var t = 0f
    }

    private fun beginLanding(index: Int) {
        val fx = heldX ?: return
        val fy = heldY ?: return
        landings[index] = Landing(fx, fy)
    }

    internal fun beginLandingFrom(index: Int, fromX: Float, fromY: Float) {
        if (index !in 0 until slotCount) return
        landings[index] = Landing(fromX, fromY)
    }

    protected fun advanceLandings() {
        if (landings.isEmpty()) return
        val step = BrassClock.dt / LANDING_SECONDS
        val done = ArrayList<Int>(landings.size)
        for ((index, landing) in landings) {
            landing.t += step
            if (landing.t >= 1f) done.add(index)
        }
        done.forEach { landings.remove(it) }
    }

    protected fun itemPos(index: Int, x: Float, y: Float): Pair<Float, Float> {
        val landing = landings[index] ?: return x to y
        // Cubic ease-out: fast away from the cursor, settling into the slot. An item that moved at a
        // constant rate and stopped dead read as a teleport with extra steps.
        val e = 1f - (1f - landing.t.coerceIn(0f, 1f)).let { it * it * it }
        return landing.fromX + (x - landing.fromX) * e to landing.fromY + (y - landing.fromY) * e
    }

    private fun advanceWashes(hovered: Int) {
        if (hoverAmount.size != slotCount) {
            hoverAmount = FloatArray(slotCount)
            paintAmount = FloatArray(slotCount)
        }
        // A frame-rate independent approach: the same exponential BrassEased uses, so a slot's wash
        // and a widget's colour ease at visibly the same rate.
        val k = 1f - kotlin.math.exp(-WASH_SPEED * BrassClock.dt)
        for (i in 0 until slotCount) {
            val hoverTarget = if (i == hovered) 1f else 0f
            val paintTarget = if (link.isPainted(this, i)) 1f else 0f
            hoverAmount[i] += (hoverTarget - hoverAmount[i]) * k
            // The paint wash rises fast and falls at the same rate as hover. A distribute-drag sweeps
            // across slots quickly, and a slow rise would have the highlight lagging behind the cursor
            // that is causing it - which reads as the drag not registering.
            val pk = if (paintTarget > paintAmount[i]) 1f - kotlin.math.exp(-PAINT_SPEED * BrassClock.dt) else k
            paintAmount[i] += (paintTarget - paintAmount[i]) * pk
        }
    }


    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun drawContent(matrixStack: UMatrixStack, bx: Int, by: Int, bw: Int, bh: Int) {

        val platform = BrassPlatform.current
        val hovered = slotUnderCursor()
        advanceWashes(hovered)
        advanceLandings()

        // Which slot the item tooltip should describe. Published for the supplier wired once in init:
        // re-attaching from here every frame registers the tooltip's enter/leave listeners only on the
        // frame the cursor is already inside a slot, so the very first hover never fired them and no
        // tooltip showed until you left and came back. Wiring once and reading this closes that gap.
        hoverIndex = hovered

        if (card) {
            // Hugs the slot block rather than the component. Left to the component's own width it
            // stretched to whatever the form handed it and trailed off into empty space beside the
            // slots. The extra pixel at the bottom balances the card visually against the row of
            // wells, which are inset by the gap on every other side.
            val cx = originX() - CARD_PAD
            val cy = originY() - CARD_PAD
            BrassCard.draw(
                matrixStack,
                cx, cy,
                cx + contentWidth(), cy + contentHeight(),
                shadow = false,
            )
        }

        for (index in 0 until slotCount) {
            val x = slotX(index)
            val y = slotY(index)
            paintWell(matrixStack, index, x, y, hoverAmount[index], paintAmount[index])
            contents[index]?.let {
                // Wells are painted at the slot; the item may still be on its way to one.
                val (ix, iy) = itemPos(index, x, y)
                paintItem(matrixStack, platform, it, ix, iy)
            }
        }

        // The held stack, following the cursor.
        // Drawn by *every* linked grid rather than by one nominated owner. It has to appear above
        // whichever grid the cursor happens to be over, and Elementa's draw order is the child order -
        // so a single owner would be painted under its neighbour half the time. An item is one quad;
        // drawing it twice in the same place is invisible and cheaper than the alternatives.
        paintHeld(matrixStack, platform)
    }

    protected open fun paintHeld(matrixStack: UMatrixStack, platform: BrassPlatform?) {
        val held = link.held
        if (held == null) {
            // Forgotten on release, so the next pick-up starts at the cursor instead of sliding in
            // from wherever the last stack was set down.
            heldX = null
            heldY = null
        } else {
            val (mx, my) = getMousePosition()
            val tx = mx - sz() / 2f
            val ty = my - sz() / 2f
            // Trails the cursor slightly rather than being pinned to it. A stack welded to the pointer
            // reads as part of the cursor; a stack that lags a few pixels and catches up reads as an
            // object being carried, which is the whole illusion the drag is selling.
            val k = 1f - kotlin.math.exp(-CARRY_SPEED * BrassClock.dt)
            heldX = heldX?.let { it + (tx - it) * k } ?: tx
            heldY = heldY?.let { it + (ty - it) * k } ?: ty
            paintItem(matrixStack, platform, held, heldX!!, heldY!!)
        }
    }

    protected open fun paintWell(m: UMatrixStack, index: Int, x: Float, y: Float, hover: Float, paint: Float) {
        val slotSize = sz()
        val highlighted = contents[index]?.let { highlight?.invoke(it, index) == true } == true
        val tint = slotTints[index] ?: if (highlighted) HIGHLIGHT_TINT else null
        if (keycapSlots) {
            // A highlighted slot wears the accent itself (its tinted fill + border + lip) rather
            // than a ring around the well - so the matching linkers read as accent-coloured slots.
            val bg: Color
            val border: Color
            val lip: Color
            if (tint != null) {
                bg = tint
                border = Colors.mix(tint, Color.WHITE, 0.28f)
                lip = Colors.mix(tint, Color.BLACK, 0.42f)
            } else {
                bg = Colors.UI_ELEMENT_BG
                border = Colors.UI_ELEMENT_BORDER
                lip = Colors.KEYCAP_BOTTOM
            }
            BrassKeycap.draw(
                m, x, y, slotSize, slotSize,
                bg = if (hover > 0.01f) Colors.mix(bg, Color.WHITE, 0.14f) else bg,
                border = border,
                outer = Colors.UI_OUTER_BORDER,
                bottom = lip,
                defaultAccent = tint == null,
                lip = 0f,
            )
            if (paint > WASH_EPSILON) {
                BrassPaint.rect(m, x, y, x + slotSize, y + slotSize, BrassPaint.fade(Colors.UI_SELECTION, paint))
            }
            return
        }
        val base = if (tint != null) Colors.mix(Colors.UI_INNER_BG, tint, 0.55f) else Colors.UI_INNER_BG
        BrassCard.flat(m, x, y, x + slotSize, y + slotSize, fill = base)
        if (tint != null) {
            // The flat well normally keeps its neutral inner border; a tinted slot wears the tint on
            // the border too, so the highlight reads as one coloured slot instead of a coloured well
            // sitting behind a grey rim.
            BrassPaint.border(m, x, y, x + slotSize, y + slotSize, Colors.mix(tint, Color.WHITE, 0.22f))
        }
        if (hover > WASH_EPSILON) {
            BrassPaint.rect(m, x, y, x + slotSize, y + slotSize, BrassPaint.fade(Colors.ROW_HOVER, hover))
        }
        // A slot the current drag has painted, so the spread is visible *before* the button is
        // released - otherwise a distribute-drag is a guess until it lands.
        if (paint > WASH_EPSILON) {
            BrassPaint.rect(m, x, y, x + slotSize, y + slotSize, BrassPaint.fade(Colors.UI_SELECTION, paint))
        }
    }

    protected fun paintItem(m: UMatrixStack, platform: BrassPlatform?, slot: Slot, x: Float, y: Float) {
        val slotSize = sz()
        val inset = (slotSize * 0.12f).coerceIn(1f, 3f)
        // Vanilla content fades by darkening and is dropped halfway through a closing frame - the
        // same earlyOut every BrassPlatformVisual uses.
        val fade = BrassAmbientFade.earlyOut(BrassAmbientFade.current)
        if (fade <= 0f) return
        runCatching {
            platform?.drawItem(
                m, slot.itemId, x + inset, y + inset, slotSize - inset * 2f,
                if (showCounts) slot.count else 1, fade,
            )
        }
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo(
            "inventory-grid",
            "Inventory grid",
            190f,
            76f,
            // The grid paints its own card around the slot block — see drawContent. A demo card around
            // that would be a second border a couple of pixels outside the first.
            card = false,
        ) {
            val chest = BrassInventoryGrid(columns = 9, rows = 2)
            val hotbar = BrassInventoryGrid(columns = 9, rows = 1)
            hotbar.linkTo(chest)

            STOCK.forEach { (index, slot) -> chest.setSlot(index, slot) }
            hotbar.setSlot(0, Slot("minecraft:torch", 48))

            val stack = UIContainer()
            chest.constrain {
                x = 0.pixels(); y = 0.pixels()
                width = basicWidthConstraint { chest.contentWidth() }
                height = basicHeightConstraint { chest.contentHeight() }
            } childOf stack
            hotbar.constrain {
                x = 0.pixels(); y = SiblingConstraint(6f)
                width = basicWidthConstraint { hotbar.contentWidth() }
                height = basicHeightConstraint { hotbar.contentHeight() }
            } childOf stack
            stack
        }

        private val STOCK = mapOf(
            0 to Slot("minecraft:diamond", 12),
            2 to Slot("minecraft:iron_ingot", 64),
            4 to Slot("minecraft:bread", 16),
            6 to Slot("minecraft:ender_pearl", 8),
            11 to Slot("minecraft:oak_planks", 32),
            13 to Slot("minecraft:redstone", 24),
        )

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val WASH_SPEED = 14f

        private const val PAINT_SPEED = 30f

        private const val CARRY_SPEED = 26f

        private const val LANDING_SECONDS = 0.12f

        private const val WASH_EPSILON = 0.01f

        private const val CARD_PAD = 2f
        private const val CARD_FOOT = 1f
    }
}
