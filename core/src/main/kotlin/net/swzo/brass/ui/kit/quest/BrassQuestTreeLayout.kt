package net.swzo.brass.ui.kit.quest

import kotlin.math.roundToInt

/**
 * Grid placement for a quest tree; pure, so tested off-game. A node's column is the length of its
 * longest prerequisite chain (roots in column 0); inside a column nodes sit as close as they can to the
 * average row of their prerequisites, in that order, never sharing a cell. Explicit `col`/`row` win.
 * Prerequisite cycles and unknown ids are ignored rather than looping.
 */
object BrassQuestTreeLayout {

    class Spec(val id: String, val requires: List<String>, val col: Int? = null, val row: Int? = null)

    data class Cell(val col: Int, val row: Int)

    fun solve(specs: List<Spec>): Map<String, Cell> {
        val byId = specs.associateBy { it.id }
        val depth = HashMap<String, Int>()
        val visiting = HashSet<String>()

        fun depthOf(id: String): Int {
            depth[id]?.let { return it }
            val s = byId[id] ?: return 0
            s.col?.let { depth[id] = it; return it }
            if (!visiting.add(id)) return 0
            val d = s.requires.filter { it in byId && it != id }.maxOfOrNull { depthOf(it) + 1 } ?: 0
            visiting.remove(id)
            depth[id] = d
            return d
        }
        specs.forEach { depthOf(it.id) }

        val out = LinkedHashMap<String, Cell>()
        val taken = HashMap<Int, HashSet<Int>>()
        // Pinned cells first, so automatic ones flow around them.
        for (s in specs) if (s.row != null) {
            val c = depth.getValue(s.id)
            out[s.id] = Cell(c, s.row)
            taken.getOrPut(c) { HashSet() } += s.row
        }
        val columns = specs.filter { it.id !in out }.groupBy { depth.getValue(it.id) }.toSortedMap()
        for ((col, members) in columns) {
            val used = taken.getOrPut(col) { HashSet() }
            val wanted = members.map { s ->
                val parents = s.requires.mapNotNull { out[it]?.row }
                s to (if (parents.isEmpty()) null else parents.average())
            }
            // Parented nodes in parent order; roots keep declaration order after them... unless the
            // column has no parents at all, where declaration order is the only order.
            val ordered = wanted.sortedWith(compareBy({ it.second == null }, { it.second ?: 0.0 }))
            var next = 0
            for ((s, avg) in ordered) {
                var r = maxOf(next, avg?.roundToInt() ?: next)
                while (r in used) r++
                used += r
                out[s.id] = Cell(col, r)
                next = r + 1
            }
        }
        return out
    }
}
