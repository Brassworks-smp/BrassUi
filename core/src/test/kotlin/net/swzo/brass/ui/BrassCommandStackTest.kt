package net.swzo.brass.ui

import net.swzo.brass.ui.kit.node.CommandStack
import net.swzo.brass.ui.kit.node.DefaultNodes
import net.swzo.brass.ui.kit.node.MoveNodesCommand
import net.swzo.brass.ui.kit.node.NodeGraph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The undo/redo layer is pure model work - no GL, no input - so the whole thing is exercised here off
 * game: a fine-grained move command, the snapshot fallback, and the linear redo-branch rule.
 */
class BrassCommandStackTest {

    private fun registry() = DefaultNodes.registry()

    @Test
    fun `a move command undoes and redoes a node's position`() {
        val g = NodeGraph(registry())
        val n = g.spawn("time", 10f, 20f)!!
        val stack = CommandStack(g)

        n.x = 90f; n.y = 60f
        stack.push(MoveNodesCommand(listOf(MoveNodesCommand.Move(n.id, 10f, 20f, 90f, 60f))))

        stack.undo()
        assertEquals(10f, g.byId(n.id)!!.x, "undo restores the old position")
        assertEquals(20f, g.byId(n.id)!!.y)

        stack.redo()
        assertEquals(90f, g.byId(n.id)!!.x, "redo re-applies the move")
        assertEquals(60f, g.byId(n.id)!!.y)
    }

    @Test
    fun `a recorded structural edit round-trips through undo and redo`() {
        val g = NodeGraph(registry())
        val a = g.spawn("time", 0f, 0f)!!
        val noise = g.spawn("noise", 200f, 0f)!!
        val stack = CommandStack(g)

        stack.record("Wire") { g.link(a, 0, noise, 0) }
        assertEquals(1, g.links.size, "the edit applied")

        stack.undo()
        assertEquals(0, g.links.size, "undo removes the wire")
        stack.redo()
        assertEquals(1, g.links.size, "redo restores the wire")
    }

    @Test
    fun `record pushes nothing when the graph did not change`() {
        val g = NodeGraph(registry())
        g.spawn("time", 0f, 0f)!!
        val stack = CommandStack(g)
        stack.record("No-op") { /* touch nothing */ }
        assertFalse(stack.canUndo, "an edit that changed nothing leaves no history")
    }

    @Test
    fun `a fresh edit clears the redo branch`() {
        val g = NodeGraph(registry())
        val n = g.spawn("time", 0f, 0f)!!
        val stack = CommandStack(g)

        n.x = 50f
        stack.push(MoveNodesCommand(listOf(MoveNodesCommand.Move(n.id, 0f, 0f, 50f, 0f))))
        stack.undo()
        assertTrue(stack.canRedo, "there is a redo to take")

        n.x = 80f
        stack.push(MoveNodesCommand(listOf(MoveNodesCommand.Move(n.id, 0f, 0f, 80f, 0f))))
        assertFalse(stack.canRedo, "a new edit drops the redo branch")
    }

    @Test
    fun `command stack reports edits undo and redo for collaboration`() {
        val g = NodeGraph(registry())
        val n = g.spawn("time", 0f, 0f)!!
        val labels = mutableListOf<String>()
        val stack = CommandStack(g, onChange = labels::add)

        n.x = 20f
        stack.push(MoveNodesCommand(listOf(MoveNodesCommand.Move(n.id, 0f, 0f, 20f, 0f))))
        stack.undo()
        stack.redo()

        assertEquals(listOf("Move", "Undo Move", "Redo Move"), labels)
    }
}
