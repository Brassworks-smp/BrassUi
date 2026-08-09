package net.swzo.brass.ui.kit.input

import net.swzo.brass.ui.kit.platform.BrassPlatform

/**
 * What a group of [BrassInventoryGrid]s share: the stack on the cursor, and any drag in progress.
 * ### Why this is not on the grid
 * A player inventory screen is two grids - the container and the hotbar - and every interesting
 * interaction crosses between them. Shift-clicking moves an item from one to the other; picking a
 * stack up in one and dropping it in the other is the *normal* case, not an edge case; and a
 * left-drag distributing a stack can start in one grid and finish in another.
 * None of that works if each grid owns its own held stack, because there is only ever **one** cursor.
 * So the cursor's contents and the drag being performed with it live here, and grids join a link
 * rather than owning one:
 * ```kotlin
 * val chest = BrassInventoryGrid(columns = 9, rows = 3)
 * val hotbar = BrassInventoryGrid(columns = 9, rows = 1)
 * hotbar.linkTo(chest)   // now shift-click and cross-grid drags work between them
 * ```
 * A grid that is never linked still has a link of its own, so there is no null case and no branch
 * between "linked" and "standalone" behaviour.
 */
class BrassInventoryLink {

    var held: BrassInventoryGrid.Slot? = null
        internal set

    internal val members = ArrayList<BrassInventoryGrid>()

    enum class Drag {
        NONE,

        EVEN,

        SINGLE,

        SWEEP,
    }

    internal var drag: Drag = Drag.NONE
        private set

    internal val painted = LinkedHashSet<Pair<BrassInventoryGrid, Int>>()

    internal var dragStack: BrassInventoryGrid.Slot? = null

    /** Called whenever the held stack or any linked grid's contents change. */
    var onChange: (() -> Unit)? = null


    internal fun hold(slot: BrassInventoryGrid.Slot?) {
        held = if (slot == null || slot.count <= 0) null else slot
        onChange?.invoke()
    }

    internal fun maxStack(itemId: String): Int =
        (BrassPlatform.current?.maxStackSize(itemId) ?: DEFAULT_STACK).coerceAtLeast(1)

    internal fun cursorOverSlot(): Boolean = members.any { it.cursorOverSlot() }


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

    internal fun beginSweep() {
        drag = Drag.SWEEP
        dragStack = null
        painted.clear()
    }

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

        // Notify every grid the drag painted, not just the one whose release handler happened to run
        // first. A release is broadcast to the whole tree and the first handler consumes the drag, so
        // the destination grid's onChange only fired by luck (whichever sibling drew/released first).
        // A host that applies a grid's contents on change (a frequency picker writing its slots back
        // to the graph) silently lost every drag that landed on it. Firing per painted grid makes the
        // notification deterministic: whoever was touched hears about it, idempotently.
        slots.forEach { (grid, _) -> grid.onChange?.invoke() }
    }

    internal fun isPainted(grid: BrassInventoryGrid, index: Int): Boolean =
        drag != Drag.NONE && painted.contains(grid to index)

    private companion object {
        const val DEFAULT_STACK = 64
    }
}
