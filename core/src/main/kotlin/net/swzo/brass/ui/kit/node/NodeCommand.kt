package net.swzo.brass.ui.kit.node

/**
 * One **undoable edit** to a [NodeGraph]. A command is created *already applied* (the editor performs the
 * edit live and hands the finished command to the [CommandStack]); the stack only ever [revert]s it and
 * [apply]s it again as the user walks undo/redo.
 * ### The thin-command + snapshot approach
 * Fine-grained commands ([MoveNodesCommand]) exist where they buy something concrete - dragging a node
 * should not clone the whole graph on every mouse-up, and moving via a command keeps the selection and
 * the running animations rather than reloading the graph out from under them. Everything structural
 * (add, delete, wire, paste) rides [SnapshotCommand]: a before/after pair of the native BSON. That is
 * far less code than a bespoke command per mutation and cannot drift from the real save format, and the
 * cost - reloading the graph on undo - only lands on edits rare enough not to care.
 * A library user can add their own command for a custom bulk edit; the editor never switches on the
 * concrete type.
 */
interface GraphCommand {
    val label: String

    fun apply(graph: NodeGraph)

    fun revert(graph: NodeGraph)
}

/**
 * The coarse fallback: the whole graph serialized [before] and [after] an edit. Reloading is O(graph),
 * so this is for structural edits (add / delete / wire / paste) that are infrequent next to a drag.
 */
class SnapshotCommand(
    private val before: ByteArray,
    private val after: ByteArray,
    override val label: String = "Edit",
) : GraphCommand {
    override fun apply(graph: NodeGraph) { graph.loadBson(after) }
    override fun revert(graph: NodeGraph) { graph.loadBson(before) }
}

/**
 * Move a set of nodes, by stable id, from an old to a new position. The fine-grained case: it touches
 * only the moved nodes' coordinates, so undoing a drag leaves the selection and every easing untouched
 * rather than reloading the graph.
 */
class MoveNodesCommand(private val moves: List<Move>, override val label: String = "Move") : GraphCommand {
    class Move(val id: Int, val fromX: Float, val fromY: Float, val toX: Float, val toY: Float)

    override fun apply(graph: NodeGraph) {
        for (m in moves) graph.byId(m.id)?.let { it.x = m.toX; it.y = m.toY }
    }

    override fun revert(graph: NodeGraph) {
        for (m in moves) graph.byId(m.id)?.let { it.x = m.fromX; it.y = m.fromY }
    }

    val moved: Boolean get() = moves.any { it.fromX != it.toX || it.fromY != it.toY }
}

/**
 * Move a set of notes (comments) by stable id - the note twin of [MoveNodesCommand], so a group
 * drag that includes notes stays a fine-grained undoable step.
 */
class MoveCommentsCommand(
    private val moves: List<Move>,
    override val label: String = "Move notes",
) : GraphCommand {
    class Move(val id: Int, val fromX: Float, val fromY: Float, val toX: Float, val toY: Float)

    override fun apply(graph: NodeGraph) {
        for (m in moves) graph.comments.firstOrNull { it.id == m.id }?.let {
            it.x = m.toX; it.y = m.toY
        }
    }

    override fun revert(graph: NodeGraph) {
        for (m in moves) graph.comments.firstOrNull { it.id == m.id }?.let {
            it.x = m.fromX; it.y = m.fromY
        }
    }

    val moved: Boolean get() = moves.any { it.fromX != it.toX || it.fromY != it.toY }
}

/**
 * The undo/redo history for a [graph]. Commands are pushed already-applied; [undo] reverts the newest and
 * parks it for [redo], and any fresh [push] clears the redo branch - the usual linear history.
 */
class CommandStack(
    private val graph: NodeGraph,
    private val limit: Int = 120,
    private val onChange: (String) -> Unit = {},
) {
    private val undoStack = ArrayDeque<GraphCommand>()
    private val redoStack = ArrayDeque<GraphCommand>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(command: GraphCommand) {
        undoStack.addLast(command)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
        onChange(command.label)
    }

    fun undo() {
        val c = undoStack.removeLastOrNull() ?: return
        c.revert(graph)
        redoStack.addLast(c)
        onChange("Undo ${c.label}")
    }

    fun redo() {
        val c = redoStack.removeLastOrNull() ?: return
        c.apply(graph)
        undoStack.addLast(c)
        onChange("Redo ${c.label}")
    }

    fun clear() { undoStack.clear(); redoStack.clear() }

    fun record(label: String, block: () -> Unit) {
        val before = graph.toBson()
        block()
        val after = graph.toBson()
        if (!before.contentEquals(after)) push(SnapshotCommand(before, after, label))
    }
}
