package net.swzo.brass.ui.kit.node

/**
 * One **undoable edit** to a [NodeGraph]. A command is created *already applied* (the editor performs the
 * edit live and hands the finished command to the [CommandStack]); the stack only ever [revert]s it and
 * [apply]s it again as the user walks undo/redo.
 *
 * ### The thin-command + snapshot approach
 *
 * Fine-grained commands ([MoveNodesCommand]) exist where they buy something concrete - dragging a node
 * should not clone the whole graph on every mouse-up, and moving via a command keeps the selection and
 * the running animations rather than reloading the graph out from under them. Everything structural
 * (add, delete, wire, paste) rides [SnapshotCommand]: a before/after pair of the native JSON. That is
 * far less code than a bespoke command per mutation and cannot drift from the real save format, and the
 * cost - reloading the graph on undo - only lands on edits rare enough not to care.
 *
 * A library user can add their own command for a custom bulk edit; the editor never switches on the
 * concrete type.
 */
interface GraphCommand {
    /** A short human name, for a history panel or a tooltip. */
    val label: String

    /** Re-perform the edit (redo). The graph is in the state it was in just before the edit. */
    fun apply(graph: NodeGraph)

    /** Undo the edit. The graph is in the state it was in just after the edit. */
    fun revert(graph: NodeGraph)
}

/**
 * The coarse fallback: the whole graph serialized [before] and [after] an edit. Reloading is O(graph),
 * so this is for structural edits (add / delete / wire / paste) that are infrequent next to a drag.
 */
class SnapshotCommand(
    private val before: String,
    private val after: String,
    override val label: String = "Edit",
) : GraphCommand {
    override fun apply(graph: NodeGraph) { graph.load(after) }
    override fun revert(graph: NodeGraph) { graph.load(before) }
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

    /** True if any node actually moved - a click that did not drag produces nothing worth recording. */
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

    /** Record an edit that has **already been applied** to the graph. */
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

    /**
     * Run [block] as one structural edit, capturing the graph before and after and pushing a
     * [SnapshotCommand] only if it actually changed. The convenience for the many `record { mutate }`
     * call sites that used to open-code a snapshot.
     */
    fun record(label: String, block: () -> Unit) {
        val before = graph.toJson()
        block()
        val after = graph.toJson()
        if (before != after) push(SnapshotCommand(before, after, label))
    }
}
