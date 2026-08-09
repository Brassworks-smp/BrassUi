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

/** The soft pastel tint [BrassInventoryGrid.highlight] slots wear - the accent heavily lightened. */
private val HIGHLIGHT_TINT: Color get() = Colors.mix(Colors.UI_ACCENT, Color.WHITE, 0.62f)

/**
 * A grid of item slots with drag-and-drop between them - a chest, a hotbar, a creative palette, a
 * loadout editor.
 *
 * ```kotlin
 * val chest = BrassInventoryGrid(columns = 9, rows = 3)
 * chest.setSlot(0, BrassInventoryGrid.Slot("minecraft:diamond", 12))
 * chest.setSlot(1, BrassInventoryGrid.Slot("minecraft:iron_ingot", 64))
 *
 * val hotbar = BrassInventoryGrid(columns = 9, rows = 1)
 * hotbar.linkTo(chest)   // one cursor between them; shift-click and drags cross
 * ```
 *
 * ### Interactions
 *
 * The set vanilla has, because these are the gestures a player already knows and any subset of them
 * reads as broken:
 *
 * | gesture | effect |
 * |---|---|
 * | left click | take the stack, put it down, merge into a matching one, or swap |
 * | right click | take **half** a stack, or place a single item onto one |
 * | shift + click | send the stack to the linked grid, merging before filling empties |
 * | left drag | spread the held stack evenly over every slot dragged across |
 * | right drag | leave exactly one item in every slot dragged across |
 *
 * Cross-grid drags work because Elementa broadcasts drag events to the whole tree: a grid is told
 * about a drag that began in its neighbour and paints whichever of its own slots the cursor is over.
 * The shared state lives in [BrassInventoryLink].
 *
 * ### Why one component, not a grid of `BrassItem`s
 *
 * A double chest is 54 slots. As components that is 54 widgets, each with its own constraints, hover
 * listeners, tooltip and entrance animation, and a drag between two of them has to be choreographed
 * across two components that do not know about each other. Painting the slots directly - the same
 * decision [net.swzo.brass.ui.kit.surface.BrassTable] makes about rows - means the grid owns the drag
 * outright: it knows which slot the press started in, what is under the cursor now, and where to draw
 * the item that is currently in flight.
 *
 * ### Changes are the application's to approve
 *
 * A grid backed by a real container cannot move stacks locally and hope the server agrees. [canPlace]
 * refuses a placement outright, [onQuickMove] decides where a shift-click actually sends something,
 * and [onChange] fires after every mutation so a caller can send the packet its container needs. Left
 * alone they all do the obvious local thing, which is what a palette or a loadout editor wants.
 */
open class BrassInventoryGrid(
    /** Slots per row. */
    var columns: Int = 9,
    /** Number of rows. */
    var rows: Int = 3,
    /** Edge length of one slot, in UI pixels. */
    var slotSize: Float = 18f,
    /** Gap between slots. */
    var gap: Float = 1f,
) : BrassWidget(BrassAccent.DEFAULT) {

    /** What is in a slot. */
    data class Slot(val itemId: String, val count: Int = 1)

    /** Slot contents by index, row-major. Absent means empty. */
    private val contents = HashMap<Int, Slot>()

    /**
     * The cursor and drag state this grid shares with any grid it is linked to. See
     * [BrassInventoryLink] - a grid always has one, so there is no standalone special case.
     */
    var link: BrassInventoryLink = BrassInventoryLink()
        private set

    /**
     * Join [other]'s link, so the two behave as one container: one cursor between them, shift-click
     * moving items across, and a drag that starts in one finishing in the other.
     */
    fun linkTo(other: BrassInventoryGrid) {
        link.members.remove(this)
        link = other.link
        if (!link.members.contains(this)) link.members.add(this)
    }

    /**
     * Whether [slot] may go in [index]. Return false to reject the placement outright.
     *
     * For a grid that only accepts certain things - a fuel slot, a filter, a sell offer - where the
     * alternative is letting the item in and taking it out again a frame later, which flickers.
     */
    var canPlace: (index: Int, slot: Slot) -> Boolean = { _, _ -> true }

    /** Called after this grid's contents change, for whatever the change was. */
    var onChange: (() -> Unit)? = null

    /**
     * Where a shift-click sends the stack from [index].
     *
     * Defaults to the first *other* grid on the link, which is what an inventory-and-hotbar pair
     * wants and what a single unlinked grid correctly turns into a no-op. Return false to say the
     * move could not be made, and the stack stays where it is.
     */
    var onQuickMove: (from: BrassInventoryGrid, index: Int) -> Boolean = { from, index ->
        val target = link.members.firstOrNull { it !== from }
        if (target == null) false else from.transferTo(target, index)
    }

    /** Called when a slot is clicked without a drag, after the click has been applied. */
    var onClick: ((index: Int, slot: Slot?, button: Int) -> Unit)? = null

    /** Whether slots may be picked up at all. A read-only view of a container sets this false. */
    var interactive: Boolean = true

    /**
     * Draw stack counts on items. Off for single-item selectors (a filter slot, a frequency picker)
     * where the number is meaningless noise.
     */
    var showCounts: Boolean = true

    /**
     * Force every stored stack to a count of 1. For a selector that only cares about *which* item is
     * in the slot: shift-clicking a stack of 64 still lands a single item, and merges never build up.
     */
    var singleStack: Boolean = false

    /**
     * Per-slot accent tints (index -> colour), mixed into the well fill. Lets a selector mark its
     * slots - a redstone link's red first slot and blue second, a fuel slot's flame - without giving
     * up the shared drag-and-drop machinery.
     */
    var slotTints: Map<Int, Color> = emptyMap()

    /**
     * Paint the wells as raised keycaps instead of flat recessed slots - the same chrome a node's
     * inline controls wear. Tinted slots keep their accent (fill, lightened border, darkened lip);
     * untinted ones fall back to the neutral element keycap.
     */
    var keycapSlots: Boolean = false

    /**
     * Optional per-slot tooltip: given the slot under the cursor and its index, return coloured lines
     * (or null to suppress). Lets a host show an item's Minecraft tooltip in a brassui card without the
     * toolkit knowing anything about Minecraft. When null, [showItemTooltips] decides whether to fall
     * back to the platform's own tooltip for the hovered item.
     */
    var itemTooltip: ((Slot, Int) -> List<Pair<String, Color>>?)? = null

    /**
     * Show each hovered slot's item tooltip through [BrassPlatform.itemTooltip] - the game's real
     * tooltip, for free, on any grid. On by default, because an item slot that hides its tooltip
     * reads as broken; turn it off for a grid whose slots mean something other than "this item"
     * (a filter well, a frequency slot).
     */
    var showItemTooltips: Boolean = true

    /**
     * Optional predicate marking slots that should stand out - the well is tinted with a soft pastel
     * version of the accent and the border picks the tint up too, so a matching slot reads as one
     * gentle highlight rather than a loud fill with a neutral rim. Lets a picker point at the linkers
     * (or items) that satisfy what it is looking for without the caller having to restyle the slots.
     */
    var highlight: ((Slot, Int) -> Boolean)? = null

    /**
     * Stretch the block of slots to the component's full width, deriving [slotSize] from it each frame
     * instead of using the fixed value. For a grid that should fill the card it sits in - a picker's
     * inventory that must span the whole modal - rather than sitting at a fixed size with slack around
     * it. [slotSize] is then the maximum (fallback) used before the layout has given the grid a width.
     */
    var fillWidth: Boolean = false

    /** Effective slot edge this frame: derived from the component's width when [fillWidth]. */
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

    /**
     * True while a stack picked up by [carry] (rather than by a press on a real slot) is on the cursor,
     * so [onMouseRelease] knows to drop it into the slot under the pointer.
     */
    private var externalCarry = false

    /**
     * Put [slot] on the shared cursor as if it had been picked up, so it follows the pointer and can be
     * dropped into this grid (or any linked one). Lets a non-grid source - an item catalogue the player
     * drags from - start a carry that the grids finish: releasing over a slot drops it, and a plain
     * click on a slot places it, exactly like a stack picked up from a real slot.
     */
    fun carry(slot: Slot?) {
        link.hold(slot)
        externalCarry = slot != null
    }

    /** When and where the last click landed, for double-click detection. */
    private var lastClickAt = 0L
    private var lastClickSlot = -1

    /** Slot under the cursor this frame (-1 for none), published by [drawContent] for the tooltip. */
    protected var hoverIndex = -1

    /**
     * Send every stack matching the one in [index] to the linked grid.
     *
     * Iterated over a snapshot of the indices, because [onQuickMove] mutates the map as it goes.
     */
    fun moveAllMatching(index: Int) {
        val id = contents[index]?.itemId ?: return
        for (i in (0 until slotCount).toList()) {
            if (contents[i]?.itemId == id) onQuickMove(this, i)
        }
    }

    /**
     * Pull matching items from every linked grid into the held stack, up to one full stack.
     *
     * Smallest stacks first, which is what makes this useful: it consolidates the scattered leftovers
     * rather than emptying a full stack that was already tidy.
     */
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

    /** Quick-move [index] through [onQuickMove]. Called by the link while sweeping. */
    internal fun quickMove(index: Int) {
        if (contents[index] != null) onQuickMove(this, index)
    }

    // ---- click semantics -------------------------------------------------------------------------

    /**
     * Left click: take the whole stack, put the whole stack down, merge into a matching one, or swap.
     *
     * The four cases vanilla has, in the order it resolves them.
     */
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

    /**
     * Right click: split a stack in half, or place a single item.
     *
     * The halving rounds **up** for the half taken, so right-clicking a stack of 1 picks it up rather
     * than doing nothing, and an odd stack leaves the smaller part behind - both as vanilla does.
     */
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

    /**
     * Move the stack at [index] into [target], merging into matching stacks before taking an empty
     * slot - the order that makes a shift-click consolidate rather than scatter.
     *
     * Returns whether anything moved.
     */
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

    /**
     * Write a slot without any of the interaction rules - used by the link when a drag lands.
     *
     * Also the single place a **landing** can start from, which is why the animation hooks in here
     * rather than in the click handlers: every path that puts something into a slot — a left click, a
     * right-click single, a spread drag, a shift-click transfer — funnels through this one method, so
     * hooking it once covers all of them and cannot be forgotten by whichever gesture is added next.
     */
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

    /** Total slots in the grid. */
    open val slotCount: Int get() = columns * rows

    /** Where the block of slots sits when the component is wider than it needs to be. */
    enum class Align { LEFT, CENTER, RIGHT }

    /**
     * How the slots are placed inside the component's box.
     *
     * A grid is almost always laid out by something that hands it the full width - `BrassForm.addField`
     * sets `width = 100.percent()` unconditionally - so the block of slots is narrower than the
     * component and something has to decide where the slack goes. Everything below (the card, the
     * hit-testing, the wells) is measured from [originX], so the three can never disagree.
     *
     * Left by default, so a grid lines up with the other controls in a form instead of sitting adrift
     * in the middle of a row whose width it never asked for.
     */
    var align: Align = Align.LEFT

    /** Draw a card behind the block of slots. */
    var card: Boolean = true

    /** Space the card takes on each side of the slot block, or zero when there is no card. */
    protected open fun pad(): Float = if (card) CARD_PAD else 0f

    /** Left edge of the slot **block** - inside the card, which starts [pad] further out. */
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

    // ---- contents --------------------------------------------------------------------------------

    /** Put [slot] in [index], or clear it with null. */
    fun setSlot(index: Int, slot: Slot?) {
        if (index !in 0 until slotCount) return
        if (slot == null) contents.remove(index) else contents[index] = slot
    }

    fun slot(index: Int): Slot? = contents[index]

    /** Replace every slot at once. Indices outside the grid are ignored. */
    fun setContents(next: Map<Int, Slot>) {
        contents.clear()
        next.forEach { (i, s) -> if (i in 0 until slotCount) contents[i] = s }
    }

    fun clear() = contents.clear()

    // ---- geometry --------------------------------------------------------------------------------

    /** Width of the slot block alone, without the card around it. */
    private fun slotsWidth(): Float = columns * sz() + (columns - 1) * gap

    /** Height of the slot block alone, without the card around it. */
    private fun slotsHeight(): Float = rows * sz() + (rows - 1) * gap

    /**
     * Total width the grid wants, for a constraint - the **card**, not just the slots.
     *
     * Sizing a component to the slot block alone left the card, which is drawn around it, hanging a
     * couple of pixels outside the box on every side.
     */
    fun contentWidth(): Float = slotsWidth() + pad() * 2

    /** Total height the grid wants, for a constraint. Includes the card's heavier bottom edge. */
    fun contentHeight(): Float = slotsHeight() + pad() * 2 + if (card) CARD_FOOT else 0f

    /** Slot index at a point measured from the component's top-left, or -1. */
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

    /** The slot under the cursor, or -1 - the index any linked member can ask about. */
    internal fun cursorSlot(): Int = slotUnderCursor()

    /** Whether the cursor is over one of this grid's slots. */
    internal fun cursorOverSlot(): Boolean = cursorSlot() >= 0

    // ---- animation ---------------------------------------------------------------------------------

    /**
     * Per-slot 0..1 for the hover wash and the drag-paint wash, eased.
     *
     * Two flat arrays rather than a [BrassEased] per slot: a double chest is 54 slots and two eased
     * objects each would be 108 allocations holding a float apiece, walked every frame. The easing
     * here is one lerp, so open-coding it against the shared clock costs less than the objects would
     * and keeps the whole animation state to two arrays that grow only when the grid is resized.
     *
     * Sized lazily from [slotCount] because `columns` and `rows` are public and a caller may change
     * them after construction.
     */
    private var hoverAmount = FloatArray(0)
    private var paintAmount = FloatArray(0)

    /**
     * Where the held stack is drawn, easing toward the cursor.
     *
     * Null until something is picked up, so the first frame of a carry starts *at* the cursor rather
     * than flying in from wherever the last one was put down.
     */
    private var heldX: Float? = null
    private var heldY: Float? = null

    /**
     * Slots holding a stack that is still flying in, keyed by index, with where it started.
     *
     * Keyed rather than a list so a slot filled twice in quick succession replaces its own animation
     * instead of accumulating two of them drawing the same item at different places.
     */
    private val landings = HashMap<Int, Landing>()

    /** A stack that has just been placed and has not finished arriving. */
    private class Landing(val fromX: Float, val fromY: Float) {
        /** 0..1 along the flight. */
        var t = 0f
    }

    /**
     * Start a stack flying from the cursor into [index].
     *
     * Only when something is actually being carried: [heldX] is null unless a held stack has been
     * drawn, so a slot filled by anything other than a drop — a shift-click transfer landing in the
     * far grid, a caller's [setSlot] — simply appears, which is right. There is no cursor for it to
     * have come from, and inventing one would send items flying out of the corner of the widget.
     */
    private fun beginLanding(index: Int) {
        val fx = heldX ?: return
        val fy = heldY ?: return
        landings[index] = Landing(fx, fy)
    }

    /**
     * Start a stack flying into [index] from an explicit point, in absolute coordinates.
     *
     * For a placement with no cursor behind it — a shift-click transfer, which moves a stack between
     * two linked grids without ever picking it up. [beginLanding]'s cursor origin is null in that case,
     * so the item would otherwise blink into the destination with nothing to connect it to the slot it
     * came from, and a quick-move across a full double chest reads as items appearing at random.
     *
     * Absolute rather than slot-relative because the source is usually a *different widget*: the whole
     * point of the gesture is that it crosses from one grid to its neighbour, and the flight has to
     * cross the same gap on screen.
     */
    internal fun beginLandingFrom(index: Int, fromX: Float, fromY: Float) {
        if (index !in 0 until slotCount) return
        landings[index] = Landing(fromX, fromY)
    }

    /** Advance every in-flight stack, dropping the ones that have arrived. */
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

    /**
     * Where slot [index]'s item should actually be drawn this frame.
     *
     * The slot's own position once it has arrived; somewhere between the cursor and the slot while it
     * is still in flight, eased out so it decelerates into place rather than arriving at full speed.
     */
    protected fun itemPos(index: Int, x: Float, y: Float): Pair<Float, Float> {
        val landing = landings[index] ?: return x to y
        // Cubic ease-out: fast away from the cursor, settling into the slot. An item that moved at a
        // constant rate and stopped dead read as a teleport with extra steps.
        val e = 1f - (1f - landing.t.coerceIn(0f, 1f)).let { it * it * it }
        return landing.fromX + (x - landing.fromX) * e to landing.fromY + (y - landing.fromY) * e
    }

    /** Advance every per-slot wash toward where this frame says it should be. */
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

    // ---- paint -----------------------------------------------------------------------------------

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
        //
        // Drawn by *every* linked grid rather than by one nominated owner. It has to appear above
        // whichever grid the cursor happens to be over, and Elementa's draw order is the child order -
        // so a single owner would be painted under its neighbour half the time. An item is one quad;
        // drawing it twice in the same place is invisible and cheaper than the alternatives.
        paintHeld(matrixStack, platform)
    }

    /**
     * Paint the stack on the shared cursor, trailing the pointer, with its landing animation state.
     *
     * Every linked grid paints it, so it stays above whichever grid the cursor is over; a
     * virtualized sibling (a long one-column list, say) calls this from its own draw to get the
     * same carry rendering without reimplementing the easing.
     */
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

    /**
     * One slot: the recessed well, then whichever wash is showing.
     *
     * [hover] and [paint] are 0..1 rather than booleans. The washes used to snap on and off, so
     * sweeping the cursor across a row strobed and a distribute-drag flashed its slots in and out —
     * both of which read as the grid struggling to keep up rather than as feedback. Fading them makes
     * the highlight follow the pointer instead of chasing it.
     *
     * The paint wash wins where both are showing, and is drawn *over* the hover rather than instead of
     * it: a slot the drag has claimed is also the slot under the cursor, and cross-fading between two
     * washes of different colours dips through a muddy midpoint on the way.
     */
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

        /**
         * A chest over a hotbar, and a stack actually moved between them.
         *
         * ### Why the demo has two grids
         *
         * Because one grid cannot show what this widget is for. A single grid demonstrates slots; the
         * behaviour worth documenting — a shared cursor, a stack carried across a boundary, a
         * shift-click that finds its own destination — only exists once two grids are linked, and
         * [linkTo] is the line that makes them one container. So the demo declares both, the way a
         * real screen would.
         *
         * ### What is worth recording
         *
         * The gestures, one at a time, because that is how anyone looks this up: what a left click
         * does, what a right click does, what a drag does. Left-click a stack and carry it to the
         * hotbar; right-click one to take half; hold left and sweep across empty slots to spread a
         * held stack over them. The held stack is painted at the cursor, so the carry is most of what
         * makes any of these legible — move deliberately rather than snapping between slots.
         */
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

        /**
         * Horizontal centre of column [index] as a fraction of a nine-wide grid's box.
         *
         * A fraction rather than a pixel offset so a demo script never restates the geometry the
         * widget already owns — slot size, gap and card padding are all private, and a script that
         * hard-coded them would drift silently the first time one changed.
         */
        private fun slotFx(index: Int): Float = (index + 0.5f) / 9f

        /** Vertical centre of row [row] of [rows], as a fraction of the grid's box. */
        private fun slotFy(row: Int, rows: Int): Float = (row + 0.5f) / rows

        /** What the demo chest starts with. Spaced out, so there is somewhere to move things to. */
        private val STOCK = mapOf(
            0 to Slot("minecraft:diamond", 12),
            2 to Slot("minecraft:iron_ingot", 64),
            4 to Slot("minecraft:bread", 16),
            6 to Slot("minecraft:ender_pearl", 8),
            11 to Slot("minecraft:oak_planks", 32),
            13 to Slot("minecraft:redstone", 24),
        )

        // ---- widget internals ------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        /** How fast a hover wash fades in and out, and how fast a paint wash fades out. */
        private const val WASH_SPEED = 14f

        /**
         * How fast a paint wash fades *in*.
         *
         * Faster than it fades out. A distribute-drag sweeps across slots quickly and the highlight has
         * to arrive with the cursor, but leaving slowly is what makes the trail of claimed slots
         * legible after the pointer has moved on.
         */
        private const val PAINT_SPEED = 30f

        /**
         * How fast the held stack catches up to the cursor.
         *
         * Brisk enough not to feel like lag, slow enough that the stack visibly trails. Below about 20
         * it reads as the game stuttering rather than as weight.
         */
        private const val CARRY_SPEED = 26f

        /**
         * How long a placed stack takes to reach its slot, in seconds.
         *
         * Short. This is confirmation that a drop landed where you aimed, not a flourish — much past
         * this and a run of quick placements has stacks still in the air from two clicks ago, which
         * makes the grid look like it is lagging behind the player rather than responding to them.
         */
        private const val LANDING_SECONDS = 0.12f

        /** Below this a wash is invisible and not worth a quad. */
        private const val WASH_EPSILON = 0.01f

        /** Margin between the card and the block of slots. */
        private const val CARD_PAD = 2f
        /** The extra pixel the card carries at the bottom. */
        private const val CARD_FOOT = 1f
    }
}
