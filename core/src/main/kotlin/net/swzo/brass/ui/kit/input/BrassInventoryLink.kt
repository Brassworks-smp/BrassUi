package net.swzo.brass.ui.kit.input

import net.swzo.brass.ui.kit.platform.BrassPlatform

/**
 * What a group of [BrassInventoryGrid]s share: the stack on the cursor, and any drag in progress.
 *
 * ### Why this is not on the grid
 *
 * A player inventory screen is two grids - the container and the hotbar - and every interesting
 * interaction crosses between them. Shift-clicking moves an item from one to the other; picking a
 * stack up in one and dropping it in the other is the *normal* case, not an edge case; and a
 * left-drag distributing a stack can start in one grid and finish in another.
 *
 * None of that works if each grid owns its own held stack, because there is only ever **one** cursor.
 * So the cursor's contents and the drag being performed with it live here, and grids join a link
 * rather than owning one:
 *
 * ```kotlin
 * val chest = BrassInventoryGrid(columns = 9, rows = 3)
 * val hotbar = BrassInventoryGrid(columns = 9, rows = 1)
 * hotbar.linkTo(chest)   // now shift-click and cross-grid drags work between them
 * ```
 *
 * A grid that is never linked still has a link of its own, so there is no null case and no branch
 * between "linked" and "standalone" behaviour.
 */
class BrassInventoryLink {

    /** The stack currently on the cursor, or null. Drawn following the pointer. */
    var held: BrassInventoryGrid.Slot? = null
        internal set

    /** Grids sharing this link, in the order they joined. */
    internal val members = ArrayList<BrassInventoryGrid>()

    /** What a drag with a held stack does to the slots it passes over. */
    enum class Drag {
        /** Nothing in progress. */
        NONE,

        /** Left button: spread the held stack evenly across every slot touched. */
        EVEN,

        /** Right button: leave exactly one item in every slot touched. */
        SINGLE,

        /**
         * Shift held: quick-move every slot swept over, as Inventory Tweaks does.
         *
         * Unlike the other two this carries nothing on the cursor and resolves **per slot as it is
         * touched** rather than on release - the point of the gesture is emptying a row in one motion
         * and seeing it happen, and a sweep that only landed when you let go would be a guess.
         */
        SWEEP,
    }

    internal var drag: Drag = Drag.NONE
        private set

    /**
     * Slots painted so far by the current drag, as `grid to index`.
     *
     * A **set**, so passing back and forth over the same slot does not give it two shares - vanilla
     * behaves the same way and it is the difference between a predictable spread and a lottery.
     * Ordered, so the remainder after an uneven division lands on the slots touched first.
     */
    internal val painted = LinkedHashSet<Pair<BrassInventoryGrid, Int>>()

    /** The stack the drag started with, so each slot's share is computed against the original. */
    internal var dragStack: BrassInventoryGrid.Slot? = null

    /** Called whenever the held stack or any linked grid's contents change. */
    var onChange: (() -> Unit)? = null

    // ---- the cursor ------------------------------------------------------------------------------

    /** Put [slot] on the cursor (or clear it) and notify. */
    internal fun hold(slot: BrassInventoryGrid.Slot?) {
        held = if (slot == null || slot.count <= 0) null else slot
        onChange?.invoke()
    }

    /** How many of [itemId] fit in one stack, via the platform. */
    internal fun maxStack(itemId: String): Int =
        (BrassPlatform.current?.maxStackSize(itemId) ?: DEFAULT_STACK).coerceAtLeast(1)

    // ---- drags -----------------------------------------------------------------------------------

    /**
     * Begin a drag of [kind] with whatever is held. Ignored with an empty cursor - dragging nothing
     * across slots should not disturb them.
     */
    internal fun beginDrag(kind: Drag) {
        val stack = held ?: return
        drag = kind
        dragStack = stack
        painted.clear()
    }

    internal fun paint(grid: BrassInventoryGrid, index: Int) {
        if (drag == Drag.NONE) return
        // A set, so sweeping back over a slot does not move it twice.
        if (!painted.add(grid to index)) return
        if (drag == Drag.SWEEP) grid.quickMove(index)
    }

    /** Begin a shift-sweep, which needs no held stack - see [Drag.SWEEP]. */
    internal fun beginSweep() {
        drag = Drag.SWEEP
        dragStack = null
        painted.clear()
    }

    /**
     * Finish the drag, distributing the held stack across the painted slots.
     *
     * A drag over a **single** slot is deliberately treated as no drag at all: it is indistinguishable
     * from a click with a slightly unsteady hand, and vanilla resolves it the same way - the click
     * that started it has already done the right thing.
     */
    internal fun endDrag() {
        val kind = drag
        val stack = dragStack
        drag = Drag.NONE
        dragStack = null
        val slots = painted.toList()
        painted.clear()

        // A sweep has already done its work slot by slot; there is nothing to distribute.
        if (kind == Drag.SWEEP || kind == Drag.NONE) return
        if (stack == null || slots.size < 2) return

        val limit = maxStack(stack.itemId)
        // EVEN splits the stack between the slots; SINGLE always places one, however many there are.
        val share = when (kind) {
            Drag.EVEN -> (stack.count / slots.size).coerceAtLeast(1)
            Drag.SINGLE -> 1
            // Both already returned above; the branches exist so a new Drag kind cannot be added
            // without the compiler asking what it distributes.
            Drag.SWEEP, Drag.NONE -> return
        }

        var remaining = stack.count
        for ((grid, index) in slots) {
            if (remaining <= 0) break
            val existing = grid.slot(index)
            if (existing != null && existing.itemId != stack.itemId) continue
            val already = existing?.count ?: 0
            val room = (limit - already).coerceAtLeast(0)
            val give = minOf(share, room, remaining)
            if (give <= 0) continue
            grid.putDirect(index, BrassInventoryGrid.Slot(stack.itemId, already + give))
            remaining -= give
        }

        hold(if (remaining > 0) BrassInventoryGrid.Slot(stack.itemId, remaining) else null)
    }

    /** Whether [index] of [grid] is part of the drag currently being painted. */
    internal fun isPainted(grid: BrassInventoryGrid, index: Int): Boolean =
        drag != Drag.NONE && painted.contains(grid to index)

    private companion object {
        const val DEFAULT_STACK = 64
    }
}
