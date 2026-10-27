package net.swzo.brass.ui

import net.swzo.brass.ui.kit.quest.BrassQuestTreeLayout
import net.swzo.brass.ui.kit.quest.BrassQuestTreeLayout.Cell
import net.swzo.brass.ui.kit.quest.BrassQuestTreeLayout.Spec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrassQuestTreeLayoutTest {

    private fun spec(id: String, vararg requires: String) = Spec(id, requires.toList())

    @Test
    fun columnIsTheLongestPrerequisiteChain() {
        val cells = BrassQuestTreeLayout.solve(listOf(spec("a"), spec("b", "a"), spec("c", "b"), spec("d", "a", "c")))
        assertEquals(0, cells.getValue("a").col)
        assertEquals(1, cells.getValue("b").col)
        assertEquals(2, cells.getValue("c").col)
        assertEquals(3, cells.getValue("d").col)
    }

    @Test
    fun aChainStaysOnOneRow() {
        val cells = BrassQuestTreeLayout.solve(listOf(spec("a"), spec("b", "a"), spec("c", "b")))
        assertTrue(cells.values.all { it.row == 0 })
    }

    @Test
    fun noTwoNodesShareACell() {
        val specs = listOf(spec("r1"), spec("r2"), spec("x", "r1"), spec("y", "r1"), spec("z", "r2"), spec("w", "x", "z"))
        val cells = BrassQuestTreeLayout.solve(specs)
        assertEquals(specs.size, cells.values.toSet().size)
    }

    @Test
    fun childrenSitNearTheirParents() {
        val cells = BrassQuestTreeLayout.solve(listOf(spec("r1"), spec("r2"), spec("r3"), spec("c", "r3")))
        assertEquals(cells.getValue("r3").row, cells.getValue("c").row)
    }

    @Test
    fun pinnedCellsWinAndOthersFlowAround() {
        val cells = BrassQuestTreeLayout.solve(listOf(Spec("a", emptyList(), col = 0, row = 0), spec("b")))
        assertEquals(Cell(0, 0), cells.getValue("a"))
        assertEquals(Cell(0, 1), cells.getValue("b"))
    }

    @Test
    fun cyclesAndUnknownIdsDoNotHang() {
        val cells = BrassQuestTreeLayout.solve(listOf(spec("a", "b"), spec("b", "a"), spec("c", "ghost")))
        assertEquals(3, cells.size)
        assertEquals(0, cells.getValue("c").col)
    }
}
