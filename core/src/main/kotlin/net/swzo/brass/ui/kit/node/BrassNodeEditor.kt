package net.swzo.brass.ui.kit.node

import gg.essential.elementa.components.Window
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.base.BrassEased
import net.swzo.brass.ui.kit.base.BrassFocus
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoCapture
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.input.BrassColorPicker
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassKeycap
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.platform.BrassCursor
import net.swzo.brass.ui.kit.surface.BrassCommandPalette
import net.swzo.brass.ui.kit.surface.BrassContextMenu
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassTextArea
import net.swzo.brass.ui.kit.text.BrassTextInput
import net.swzo.brass.ui.kit.text.BrassTextField
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A snapshot of the editor camera: the world → widget-pixel pan and the zoom (1f = 100%). Hosts use
 * [BrassNodeEditor.view] to capture the camera when a screen closes and [BrassNodeEditor.setView] to
 * restore it when the same graph reopens, so the user's pan and zoom survive UI round-trips.
 */
data class EditorView(val panX: Float, val panY: Float, val zoom: Float) {
    companion object {
        /** The default camera: 100% zoom with the world origin at the widget's top-left. */
        val DEFAULT = EditorView(0f, 0f, 1f)
    }
}

/**
 * A **node graph editor** in the spirit of Blender's shader nodes, Unreal Blueprints or n8n: a pannable,
 * zoomable canvas of nodes wired by their typed ports, where each node is a miniature modal carrying its
 * own panel of settings.
 *
 * ### The look is the toolkit's look
 *
 * Every node is a real toolkit card with a stacked window header; every port nub is a keycap; every
 * inline control is drawn with the same [BrassCard] painters and animated with the same
 * [net.swzo.brass.ui.kit.base.BrassEased] the real widgets use. The whole canvas is drawn under **one
 * zoom transform**, so borders, nubs, curves and text all scale at the same rate and stay crisp - the
 * thing that looks off when a widget scales its outlines and its fill by different amounts. Hit-testing
 * runs in canvas space, so a click lands exactly on the pixel it hit at any zoom.
 *
 * ### The model is pure data
 *
 * The graph ([NodeGraph]) is plain values, which is what makes save/load ([save]/[load]), copy/paste,
 * duplicate and undo trivial. Node **types** live in a [NodeRegistry]; register your own (with your own
 * [NodeField]s) and they are first-class in the add menu, the panel and the saved file.
 *
 * ### Interactions
 *
 * Drag a node to move it; drag from a port to wire (drop on a compatible port); drag from an input to
 * re-route it; drag empty canvas to pan (Shift+drag box-selects, middle-drag also pans); scroll to zoom
 * toward the cursor; click controls to edit; the header chevron rolls the node up; right-click for the
 * add menu / node menu / wire menu (real [BrassContextMenu]s), and hover for [BrassTooltip]s. Keyboard:
 * Delete, Ctrl+D duplicate, Ctrl+C/V, Ctrl+Z / Ctrl+Shift+Z undo/redo, Home frame-all, "." frame-selected,
 * +/- eased zoom, Shift+A add node, F5 run, F6 step, Shift+F5 stop, F9 breakpoint, and Tab node navigation.
 */
class BrassNodeEditor(
    val registry: NodeRegistry = defaultRegistry(),
) : BrassWidget(BrassAccent.DEFAULT), NodeCollaborativeDocument {

    val graph = NodeGraph(registry)
    val scheduler = GraphScheduler(graph)
    val selection = NodeSelectionController(graph)
    val workflow = NodeWorkflowService(registry, graph, selection)
    val hitTester = NodeHitTester(graph)

    // View transform. world -> local(widget) pixel: local = pan + world * zoom; and back for hit-testing.
    private var panX = 0f
    private var panY = 0f
    private var zoom = 1f
    /** Eased zoom for host chrome (the +/- buttons): animates toward its target every frame. */
    private val zoomEase = BrassEased(1f, speed = 10f)
    private var zoomAnchorLX = 0f
    private var zoomAnchorLY = 0f
    private var zoomAnchorWX = 0f
    private var zoomAnchorWY = 0f
    private var framed = false
    private var viewportW = 0f
    private var viewportH = 0f
    private var originX = 0f
    private var originY = 0f
    private var time = 0f

    private fun worldX(localX: Float) = (localX - panX) / zoom
    private fun worldY(localY: Float) = (localY - panY) / zoom
    private fun mouseLocal(): Pair<Float, Float> {
        val (mx, my) = getMousePosition(); return (mx - originX) to (my - originY)
    }

    // Interaction state

    private sealed interface Mode {
        object Idle : Mode
        object Pan : Mode
        class DragNode(
            val node: GraphNode,
            val grabX: Float,
            val grabY: Float,
            val starts: Map<Int, Pair<Float, Float>>,
        ) : Mode
        /** Dragging empty canvas *inside* the selection box moves the whole group. */
        class DragGroup(
            val startX: Float,
            val startY: Float,
            val starts: Map<Int, Pair<Float, Float>>,
            /** The persistent marquee box at drag start, so it follows the group; null when none. */
            val boxStart: FloatArray?,
        ) : Mode
        class Wire(val node: GraphNode, val port: Int) : Mode
        class DragReroute(val link: Link, val point: Int) : Mode
        class DragComment(val comment: GraphComment, val grabX: Float, val grabY: Float) : Mode
        class DragFrame(
            val frame: GraphFrame,
            val grabX: Float,
            val grabY: Float,
            val startX: Float,
            val startY: Float,
            val starts: Map<Int, Pair<Float, Float>>,
            val frameStarts: Map<Int, Pair<Float, Float>>,
        ) : Mode
        class Scrub(val onDrag: (Float) -> Unit) : Mode
        class Box(val startX: Float, val startY: Float, val additive: Boolean = false) : Mode
    }

    private var mode: Mode = Mode.Idle
    private var lastLX = 0f
    private var lastLY = 0f
    private var wireEndWx = 0f
    private var wireEndWy = 0f
    private var boxCurX = 0f
    private var boxCurY = 0f
    private var guideX: Float? = null
    private var guideY: Float? = null
    private var pressedField: NodeField? = null
    private var lastFrameClickId: Int? = null
    private var lastFrameClickAt = 0L
    private var noteEditor: BrassTextArea? = null
    private var editingCommentId: Int? = null
    private var noteEditBefore: ByteArray? = null

    // hover (for animation + tooltips), recomputed each frame
    private var hoverNode: GraphNode? = null
    private var hoverField: NodeField? = null
    private var hoverPort: Triple<GraphNode, Int, Boolean>? = null
    private var hoverWire: Link? = null
    private var tipTitle: String? = null
    private var tipBody: String? = null

    /** The field whose custom tooltip is currently attached, so the entry is only swapped on change. */
    private var customTipField: NodeField? = null

    // history / clipboard / palette memory
    private val changeListeners = CopyOnWriteArrayList<(GraphChange) -> Unit>()
    private var revision = 0L
    private var diagnosticRevision = -1L
    private var diagnosticCache = emptyList<NodeDiagnostic>()
    private val history = CommandStack(graph, onChange = ::emitGraphChange)
    /** The graph snapshot taken when a live edit (a scrub, a wire drag) began, pushed on release. */
    private var editBefore: ByteArray? = null
    private var clipboard: String? = null
    val favoriteTypeIds: MutableSet<String> get() = workflow.favoriteTypeIds
    val templates: MutableMap<String, NodeTemplate> get() = workflow.templates
    val previewedOutputs = LinkedHashSet<PortRef>()

    /** Node ids fully covered by a node drawn above them - invisible, so their render is skipped. */
    private val occludedNodes = HashSet<Int>()

    /** Per-node lists of above-drawn node rects that overlap it, for per-widget occlusion. */
    private val coverers = HashMap<Int, ArrayList<FloatArray>>()

    /** Dirty-check state for the occlusion sweep: skip the whole pass when nothing moved. */
    private var occlusionStamp = Long.MIN_VALUE
    private var occlusionValid = false

    /**
     * The rectangle of the last **right-drag** marquee, kept visible after release exactly as it was
     * dragged (never re-fitted to the nodes). Normal click/shift selection shows no box at all.
     */
    private var marqueeBox: FloatArray? = null

    // ---- auto-layout animation ------------------------------------------------------------------

    /** In-flight auto-layout glides: node id -> move for nodes, frame id -> move for frames. */
    private var layoutAnim: HashMap<Int, EasedMove>? = null
    private var frameAnim: HashMap<Int, EasedMove>? = null
    private var layoutAnimRevision = -1L

    /** One eased flight of a node (x,y) or a frame (x,y,w,h) from its current to its target box. */
    private class EasedMove(val from: FloatArray, val to: FloatArray, val start: Float, val duration: Float)

    /** A smooth camera glide: pan and zoom ease together so a frame/auto-layout centres itself. */
    private var cameraAnim: CameraFlight? = null

    private class CameraFlight(
        val fromPanX: Float, val fromPanY: Float, val fromZoom: Float,
        val toPanX: Float, val toPanY: Float, val toZoom: Float,
        val start: Float, val duration: Float,
    )

    // ---- right-drag box selection ---------------------------------------------------------------

    /** True between a right press and its release; a right *drag* on empty canvas becomes a box. */
    private var rightDown = false
    private var rightMoved = false
    private var rightPressLX = 0f
    private var rightPressLY = 0f
    private var rightPressWX = 0f
    private var rightPressWY = 0f
    private var rightPressOnControl = false

    /** When enabled, moved nodes settle onto [NodeLayout.GRID]. */
    var snapToGrid: Boolean = false

    /**
     * Re-fit content when the host viewport changes. Useful for responsive previews; off by default so
     * an application editor never loses a user's deliberate pan and zoom when its window is resized.
     */
    var reframeOnResize: Boolean = false

    /** A presentation-safe mode: navigation and selection remain available, mutations do not. */
    var readOnly: Boolean = false

    /**
     * Live signal strength 0..1 for a wire, driving its brightness and the speed of its travelling
     * motes. Defaults to full strength so a host that does not care keeps the classic bright, animated
     * wire.
     */
    var wireStrength: (Link) -> Float = { 1f }

    /**
     * Live output value for a node, drawn as a small badge in its header. Null (the default) hides the
     * badge entirely. The badge is tinted by the node's first output port type, so a number node glows
     * the number colour, a signal node red, and a host can hide string outputs by returning null.
     */
    var nodeValue: (GraphNode) -> Any? = { null }

    /**
     * When set, hovering a wire shows its signal strength in the editor's tooltip (the value is also
     * drawn as the tooltip body). Return null to leave the default tooltip for the wire.
     */
    var wireStrengthTooltip: ((Link) -> String?)? = null

    /** Draw the port type's symbol at the midpoint of each wire. Signal wires usually turn this off. */
    var showWireSymbols: Boolean = true

    /** Include the node-type list in the blank-canvas right-click menu. */
    var canvasMenuAddNodes: Boolean = true

    /** Include the Run graph / Run one node / Continue / Stop entries in the canvas menu. */
    var canvasMenuRun: Boolean = true

    /** Include breakpoint / watch-outputs / preview-outputs entries in the node menu. */
    var nodeMenuDebug: Boolean = true

    init {
        chrome = BrassChrome.NONE
        enableEffect(ScissorEffect())
        BrassTooltip.attachLazy(this, { tipTitle ?: "" }, { tipBody })

        onMouseClick { e ->
            grabWindowFocus()
            val lx = e.relativeX; val ly = e.relativeY
            lastLX = lx; lastLY = ly
            val wx = worldX(lx); val wy = worldY(ly)

            if (e.mouseButton == 2) {
                cameraAnim = null
                zoomEase.snapTo(zoom)
                mode = Mode.Pan
                return@onMouseClick
            }
            if (e.mouseButton == 1) {
                // Defer the context menu to release: a right *drag* on empty canvas becomes a box
                // selection (bulk select, then drag/delete/copy the group).
                rightDown = true
                rightMoved = false
                rightPressLX = lx; rightPressLY = ly
                rightPressWX = wx; rightPressWY = wy
                rightPressOnControl = controlAt(wx, wy) != null || rerouteAt(wx, wy) != null ||
                    nodeAt(wx, wy) != null || wireAt(wx, wy) != null ||
                    commentAt(wx, wy) != null || frameHeaderAt(wx, wy) != null
                return@onMouseClick
            }
            if (e.mouseButton != 0) return@onMouseClick
            val shift = UKeyboard.isShiftKeyDown()
            val ctrl = UKeyboard.isCtrlKeyDown()

            rerouteAt(wx, wy)?.let { (link, point) ->
                if (readOnly) return@onMouseClick
                editBefore = graph.toBson()
                mode = Mode.DragReroute(link, point)
                return@onMouseClick
            }

            portAt(wx, wy)?.let { (node, port, isOut) ->
                if (readOnly) { select(node, additive = ctrl || shift, toggle = ctrl); return@onMouseClick }
                select(node, additive = false)
                // Capture now; a completed wire or a wire dragged off an input is pushed on release.
                editBefore = graph.toBson()
                if (isOut) { mode = Mode.Wire(node, port); wireEndWx = wx; wireEndWy = wy }
                else graph.links.firstOrNull { it.to === node && it.toPort == port }?.let { ex ->
                    graph.links.remove(ex); mode = Mode.Wire(ex.from, ex.fromPort); wireEndWx = wx; wireEndWy = wy
                }
                return@onMouseClick
            }

            controlAt(wx, wy)?.let { hit ->
                if (readOnly) return@onMouseClick
                select(hit.node, additive = false)
                pressedField = hit.field
                if (hit.field.opensEditor) {
                    hit.field.showEditor(Window.of(this), this, originX + lx, originY + ly)
                } else {
                    editBefore = graph.toBson()
                    val drag = hit.field.onPress(wx, hit.x1, hit.x2)
                    if (drag != null) mode = Mode.Scrub(drag) else pushEditSnapshot("Edit")
                }
                return@onMouseClick
            }

            nodeAt(wx, wy)?.let { node ->
                if (chevronAt(wx, wy, node)) {
                    history.record("Collapse") { node.collapsed = !node.collapsed }; select(node, additive = false); return@onMouseClick
                }
                bringToFront(node)
                // A node that is already part of the selection keeps the whole group when dragged
                // (no shift needed); clicking an unselected node narrows to it as usual.
                select(node, additive = shift || ctrl || node.selected, toggle = ctrl)
                if (!readOnly && node.selected) {
                    val starts = graph.nodes.filter { it.selected }.associate { it.id to (it.x to it.y) }
                    mode = Mode.DragNode(node, wx - node.x, wy - node.y, starts)
                }
                return@onMouseClick
            }

            commentAt(wx, wy)?.let { comment ->
                if (readOnly) return@onMouseClick
                if (!shift && !ctrl) clearSelection()
                if (noteMenuHit(comment, wx, wy)) {
                    openCommentMenu(comment, originX + lx, originY + ly)
                } else if (wy <= comment.y + NOTE_HEADER) {
                    editBefore = graph.toBson()
                    mode = Mode.DragComment(comment, wx - comment.x, wy - comment.y)
                } else {
                    beginNoteEditing(comment)
                }
                return@onMouseClick
            }

            frameHeaderAt(wx, wy)?.let { frame ->
                if (readOnly) return@onMouseClick
                if (!shift && !ctrl) clearSelection()
                if (frameMenuHit(frame, wx, wy)) {
                    openFrameMenu(frame, originX + lx, originY + ly)
                    return@onMouseClick
                }
                val now = System.currentTimeMillis()
                if (lastFrameClickId == frame.id && now - lastFrameClickAt <= DOUBLE_CLICK_MS) {
                    lastFrameClickId = null
                    openFrameEditor(frame, originX + lx, originY + ly)
                    return@onMouseClick
                }
                lastFrameClickId = frame.id
                lastFrameClickAt = now
                editBefore = graph.toBson()
                val starts = frameNodeIds(frame).mapNotNull { id ->
                    graph.byId(id)?.let { id to (it.x to it.y) }
                }.toMap()
                val frameStarts = frameTree(frame).associate { it.id to (it.x to it.y) }
                mode = Mode.DragFrame(
                    frame, wx - frame.x, wy - frame.y, frame.x, frame.y, starts, frameStarts,
                )
                return@onMouseClick
            }

            wireAt(wx, wy)?.let { link ->
                if (!shift) clearSelection()
                link.selected = true
                return@onMouseClick
            }

            cameraAnim = null
            zoomEase.snapTo(zoom)
            val insideSelection = selectionBox()?.let { (minX, minY, maxX, maxY) ->
                wx in minX..maxX && wy in minY..maxY
            } == true
            mode = when {
                shift || ctrl -> {
                    marqueeBox = null
                    Mode.Box(wx, wy, additive = true)
                }
                insideSelection -> {
                    // Dragging anywhere inside the selection box moves the whole group.
                    val starts = graph.nodes.filter { it.selected }.associate { it.id to (it.x to it.y) }
                    Mode.DragGroup(wx, wy, starts, marqueeBox?.copyOf())
                }
                else -> {
                    clearSelection()
                    Mode.Pan
                }
            }
            boxCurX = wx; boxCurY = wy
        }

        onMouseDrag { mx, my, btn ->
            // A right-drag that leaves the press point on empty canvas becomes a box selection.
            if (btn == 1 && rightDown && !rightMoved && !rightPressOnControl) {
                val dx = mx - rightPressLX
                val dy = my - rightPressLY
                if (dx * dx + dy * dy > BOX_DRAG_THRESHOLD * BOX_DRAG_THRESHOLD) {
                    rightMoved = true
                    cameraAnim = null
                    zoomEase.snapTo(zoom)
                    marqueeBox = null
                    mode = Mode.Box(rightPressWX, rightPressWY, additive = UKeyboard.isShiftKeyDown())
                }
            }
            when (val m = mode) {
                is Mode.Pan -> { panX += mx - lastLX; panY += my - lastLY }
                is Mode.DragNode -> {
                    val anchor = m.starts[m.node.id] ?: (m.node.x to m.node.y)
                    val dx = worldX(mx) - m.grabX - anchor.first
                    val dy = worldY(my) - m.grabY - anchor.second
                    val adjusted = smartDragDelta(m, dx, dy)
                    for ((id, start) in m.starts) graph.byId(id)?.let {
                        it.x = start.first + adjusted.first
                        it.y = start.second + adjusted.second
                    }
                }
                is Mode.DragGroup -> {
                    val dx = worldX(mx) - m.startX
                    val dy = worldY(my) - m.startY
                    for ((id, start) in m.starts) graph.byId(id)?.let {
                        it.x = start.first + dx
                        it.y = start.second + dy
                    }
                    m.boxStart?.let { b ->
                        marqueeBox = floatArrayOf(b[0] + dx, b[1] + dy, b[2] + dx, b[3] + dy)
                    }
                }
                is Mode.Wire -> { wireEndWx = worldX(mx); wireEndWy = worldY(my) }
                is Mode.DragReroute -> m.link.reroutes.getOrNull(m.point)?.let {
                    it.x = worldX(mx)
                    it.y = worldY(my)
                }
                is Mode.DragComment -> {
                    m.comment.x = worldX(mx) - m.grabX
                    m.comment.y = worldY(my) - m.grabY
                }
                is Mode.DragFrame -> {
                    val nx = worldX(mx) - m.grabX
                    val ny = worldY(my) - m.grabY
                    val dx = nx - m.startX
                    val dy = ny - m.startY
                    for ((id, start) in m.starts) graph.byId(id)?.let {
                        it.x = start.first + dx
                        it.y = start.second + dy
                    }
                    for ((id, start) in m.frameStarts) {
                        graph.frames.firstOrNull { it.id == id }?.let { moved ->
                            if (!moved.autoResize || frameNodeIds(moved).isEmpty()) {
                                moved.x = start.first + dx
                                moved.y = start.second + dy
                            }
                        }
                    }
                }
                is Mode.Scrub -> m.onDrag(worldX(mx))
                is Mode.Box -> {
                    boxCurX = worldX(mx); boxCurY = worldY(my)
                    liveBoxSelect(m.additive)
                }
                else -> {}
            }
            lastLX = mx; lastLY = my
        }

        onMouseRelease {
            if (rightDown) {
                rightDown = false
                val boxMode = mode as? Mode.Box
                if (boxMode != null && rightMoved) {
                    // Right-drag box replaces the selection; holding shift adds to it.
                    if (!UKeyboard.isShiftKeyDown()) selection.clear()
                    selectInBox()
                    mode = Mode.Idle
                    // Keep the box exactly as it was dragged (not re-fitted to the nodes).
                    marqueeBox = floatArrayOf(
                        minOf(boxMode.startX, boxCurX), minOf(boxMode.startY, boxCurY),
                        maxOf(boxMode.startX, boxCurX), maxOf(boxMode.startY, boxCurY),
                    )
                } else if (!rightMoved) {
                    rightClick(rightPressLX, rightPressLY, rightPressWX, rightPressWY)
                }
                return@onMouseRelease
            }
            when (val m = mode) {
                is Mode.Wire -> {
                    val (lx, ly) = mouseLocal()
                    val wx = worldX(lx); val wy = worldY(ly)
                    val target = portAt(wx, wy)
                    target?.let { (node, port, isOut) ->
                        if (!isOut) graph.link(m.node, m.port, node, port)?.let { it.flash = 1f }
                    }
                    if (target == null) openCompatibleMenu(m, lx, ly, wx, wy)
                    // Covers a completed wire and a wire pulled off an input and dropped in empty space.
                    pushEditSnapshot("Wire")
                }
                is Mode.DragNode -> {
                    if (snapToGrid) {
                        for (id in m.starts.keys) graph.byId(id)?.let {
                            it.x = (it.x / NodeLayout.GRID).roundToInt() * NodeLayout.GRID
                            it.y = (it.y / NodeLayout.GRID).roundToInt() * NodeLayout.GRID
                        }
                    }
                    val moves = m.starts.mapNotNull { (id, start) ->
                        graph.byId(id)?.let { MoveNodesCommand.Move(id, start.first, start.second, it.x, it.y) }
                    }
                    val cmd = MoveNodesCommand(moves)
                    if (cmd.moved) history.push(cmd)
                }
                is Mode.DragGroup -> {
                    if (snapToGrid) {
                        for (id in m.starts.keys) graph.byId(id)?.let {
                            it.x = (it.x / NodeLayout.GRID).roundToInt() * NodeLayout.GRID
                            it.y = (it.y / NodeLayout.GRID).roundToInt() * NodeLayout.GRID
                        }
                    }
                    val moves = m.starts.mapNotNull { (id, start) ->
                        graph.byId(id)?.let { MoveNodesCommand.Move(id, start.first, start.second, it.x, it.y) }
                    }
                    val cmd = MoveNodesCommand(moves)
                    if (cmd.moved) history.push(cmd)
                }
                is Mode.Scrub -> pushEditSnapshot("Edit")
                is Mode.DragReroute -> pushEditSnapshot("Move reroute")
                is Mode.DragComment -> pushEditSnapshot("Move note")
                is Mode.DragFrame -> pushEditSnapshot("Move group")
                is Mode.Box -> selectInBox()
                else -> {}
            }
            editBefore = null
            pressedField?.onRelease()
            pressedField = null
            guideX = null; guideY = null
            mode = Mode.Idle
        }

        onMouseScroll { e ->
            val (lx, ly) = mouseLocal()
            zoomBy(1f + e.delta.toFloat() * 0.12f, lx, ly)
            e.stopPropagation()
        }

        onKeyType { _, keyCode -> onKey(keyCode) }
    }

    // Public API

    /** Spawn a node of [typeId] at canvas ([wx],[wy]). */
    fun spawn(typeId: String, wx: Float, wy: Float): GraphNode? =
        graph.spawn(typeId, wx, wy)?.also { rememberType(typeId); emitGraphChange("Add ${it.type.title}") }

    /** Wire two ports (see [NodeGraph.link]). */
    fun link(from: GraphNode, fromPort: Int, to: GraphNode, toPort: Int): Link? =
        graph.link(from, fromPort, to, toPort)?.also { emitGraphChange("Wire") }

    /** Serialize the whole graph to the portable JSON format - export/import, clipboard, hand-editing. */
    fun save(): String = graph.toJson()

    /** Replace the graph from [json] and frame it; the undo history starts fresh with the new graph. */
    fun load(json: String): Boolean {
        finishNoteEditing()
        if (!graph.load(json)) return false
        framed = false; history.clear(); emitGraphChange("Load")
        return true
    }

    /** Serialize the whole graph to its native BSON format - the fast save path for the wire. */
    fun saveBson(): ByteArray = graph.toBson()

    /** Replace the graph from [bytes] (BSON) and frame it; the undo history starts fresh. */
    fun loadBson(bytes: ByteArray): Boolean {
        finishNoteEditing()
        if (!graph.loadBson(bytes)) return false
        framed = false; history.clear(); emitGraphChange("Load")
        return true
    }

    /** Apply a plugin-owned mutation as one undoable structural edit. */
    fun edit(label: String, mutation: (NodeGraph) -> Unit) {
        if (!readOnly) history.record(label) { mutation(graph) }
    }

    /**
     * Algorithmically re-arrange every node: connected components become layered, crossing-minimized
     * flows (feedback cycles collapse into one band), isolated nodes park in a tidy grid to the
     * side, and groups follow their members. The camera stays anchored on the same content, and the
     * move glides into place with a staggered ease - one undoable "Auto layout" step. Returns false
     * when there is nothing to arrange (or the editor is read-only).
     */
    fun autoLayout(animate: Boolean = true): Boolean {
        if (readOnly) return false
        val nodes = graph.nodes.filter { !it.closing }
        if (nodes.isEmpty()) return false

        val model = nodes.map { NodeAutoLayout.LayoutNode(it.id, it.width, NodeLayout.height(it)) }
        val edges = graph.links.map { NodeAutoLayout.LayoutEdge(it.from.id, it.to.id) }
        val result = NodeAutoLayout.layout(model, edges)
        if (result.positions.size != nodes.size) return false

        // Keep the layout anchored to the graph's top-left, so repeated runs stay put relative to
        // the origin (centroid anchoring drifts when the layout is more compact than the scatter).
        val oldMinX = nodes.minOf { it.x }
        val oldMinY = nodes.minOf { it.y }
        val b = result.bounds
        val dx = oldMinX - b[0]
        val dy = oldMinY - b[1]

        val nodeTargets = HashMap<Int, FloatArray>()
        result.positions.forEach { (id, p) -> nodeTargets[id] = floatArrayOf(p.first + dx, p.second + dy) }

        // Frames follow their members: each frame's target bounds enclose its nodes' final spots.
        val frameTargets = HashMap<Int, FloatArray>()
        for (frame in graph.frames) {
            val members = frameNodeIds(frame).mapNotNull(graph::byId)
            if (members.isEmpty()) continue
            val xs = members.flatMap { listOf(nodeTargets.getValue(it.id)[0], nodeTargets.getValue(it.id)[0] + it.width) }
            val ys = members.flatMap { listOf(nodeTargets.getValue(it.id)[1], nodeTargets.getValue(it.id)[1] + NodeLayout.height(it)) }
            val pad = 18f
            val fx = xs.min() - pad
            val fy = ys.min() - pad - 12f
            frameTargets[frame.id] = floatArrayOf(fx, fy, xs.max() - fx + pad, ys.max() - fy + pad)
        }

        // Capture the start positions BEFORE the undo step applies the final ones - the glide's
        // "from" must be the pre-layout spots, or every flight would be final→final and snap.
        val nodeStarts = HashMap<Int, FloatArray>()
        nodes.forEach { nodeStarts[it.id] = floatArrayOf(it.x, it.y) }
        val frameStarts = HashMap<Int, FloatArray>()
        graph.frames.forEach { frameStarts[it.id] = floatArrayOf(it.x, it.y, it.width, it.height) }

        // One undoable step that lands at the final positions; the glide mutates live x/y after.
        edit("Auto layout") { g ->
            nodeTargets.forEach { (id, t) -> g.byId(id)?.let { it.x = t[0]; it.y = t[1] } }
            frameTargets.forEach { (id, t) ->
                g.frames.firstOrNull { it.id == id }?.let {
                    it.x = t[0]; it.y = t[1]; it.width = t[2]; it.height = t[3]
                }
            }
        }

        // Glide the camera to centre the freshly arranged graph (always, even without node glide).
        smoothCameraTo(b[0] + dx, b[1] + dy, b[2] + dx, b[3] + dy)

        if (animate) {
            // A gentle left-to-right, top-to-bottom wave: far-away nodes start a beat later.
            val ordered = nodeTargets.entries.sortedBy { it.value[1] * 10000f + it.value[0] }
            val nodeAnim = HashMap<Int, EasedMove>()
            ordered.forEachIndexed { i, (id, t) ->
                val from = nodeStarts[id] ?: return@forEachIndexed
                val delay = minOf(i, 26) * 0.016f
                nodeAnim[id] = EasedMove(from, t, time + delay, 0.5f)
            }
            val frames = HashMap<Int, EasedMove>()
            frameTargets.forEach { (id, t) ->
                val from = frameStarts[id] ?: return@forEach
                frames[id] = EasedMove(from, t, time, 0.5f)
            }
            layoutAnim = nodeAnim
            frameAnim = frames
            layoutAnimRevision = revision
        }
        return true
    }

    fun groupSelection(title: String = "Group"): GraphFrame? {
        val ids = graph.nodes.filter { it.selected && !it.closing }.map { it.id }
        if (readOnly || ids.isEmpty()) return null
        var created: GraphFrame? = null
        history.record("Group") { created = graph.frame(title, ids) }
        return created
    }

    fun addComment(text: String, wx: Float, wy: Float): GraphComment? {
        if (readOnly) return null
        var created: GraphComment? = null
        history.record("Comment") { created = graph.comment(text, wx, wy) }
        return created
    }

    fun saveBookmark(name: String) {
        if (!readOnly) history.record("Bookmark") { graph.bookmark(name, panX, panY, zoom) }
    }

    fun goToBookmark(name: String): Boolean {
        val bookmark = graph.bookmarks.firstOrNull { it.name == name } ?: return false
        panX = bookmark.panX; panY = bookmark.panY; zoom = bookmark.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        cameraAnim = null
        zoomEase.snapTo(zoom)
        return true
    }

    fun toggleBreakpoint(node: GraphNode): Boolean {
        if (!scheduler.breakpoints.add(node.id)) scheduler.breakpoints.remove(node.id)
        return node.id in scheduler.breakpoints
    }

    fun watch(node: GraphNode, outputPort: Int, enabled: Boolean = true) {
        val ref = PortRef(node.id, outputPort)
        if (enabled) scheduler.watches += ref else scheduler.watches -= ref
    }

    fun preview(node: GraphNode, outputPort: Int, enabled: Boolean = true) {
        val ref = PortRef(node.id, outputPort)
        if (enabled) {
            previewedOutputs += ref
            scheduler.watches += ref
        } else {
            previewedOutputs -= ref
        }
    }

    fun runGraph() = when (scheduler.state) {
        ExecutionState.PAUSED -> { scheduler.continueExecution(); null }
        ExecutionState.RUNNING -> null
        else -> scheduler.run()
    }

    fun stepGraph() = when (scheduler.state) {
        ExecutionState.PAUSED -> { scheduler.step(); null }
        ExecutionState.RUNNING -> null
        else -> scheduler.runStep()
    }

    fun cancelRun() = scheduler.cancel()

    fun setFavorite(typeId: String, favorite: Boolean = true): Boolean {
        return workflow.setFavorite(typeId, favorite)
    }

    fun recentTypes(): List<NodeType> = workflow.recentTypes()

    fun createTemplate(name: String): NodeTemplate? {
        return workflow.createTemplate(name)
    }

    fun instantiateTemplate(name: String, wx: Float, wy: Float): List<GraphNode> {
        val template = templates[name] ?: return emptyList()
        if (readOnly) return emptyList()
        var created = emptyList<GraphNode>()
        history.record("Template $name") { created = workflow.instantiate(template.name, wx, wy) }
        return created
    }

    fun nestFrame(child: GraphFrame, parent: GraphFrame?): Boolean {
        if (readOnly || child === parent) return false
        var ancestor = parent
        while (ancestor != null) {
            if (ancestor.id == child.id) return false
            val parentId = ancestor.parentFrameId
            ancestor = parentId?.let { id -> graph.frames.firstOrNull { it.id == id } }
        }
        history.record("Nest group") { child.parentFrameId = parent?.id }
        return true
    }

    fun editComment(comment: GraphComment, text: String) {
        if (!readOnly) history.record("Edit note") { comment.text = text }
    }

    override fun onGraphChange(listener: (GraphChange) -> Unit): () -> Unit {
        changeListeners += listener
        return { changeListeners -= listener }
    }

    /** Apply a collaborator's authoritative snapshot without adding it to local undo history. */
    override fun applyRemoteSnapshot(bytes: ByteArray, label: String) {
        finishNoteEditing()
        if (!graph.loadBson(bytes)) return
        history.clear()
        framed = false
        emitGraphChange(label)
    }

    fun acceptDroppedFiles(paths: List<Path>): NodeImportResult {
        val path = paths.firstOrNull { it.fileName.toString().endsWith(".json", ignoreCase = true) }
            ?: return NodeImportResult.Rejected(paths.firstOrNull(), "Drop a node-graph JSON file")
        val json = runCatching { Files.readString(path) }.getOrElse {
            return NodeImportResult.Rejected(path, it.message ?: "Could not read file")
        }
        val compatibility = NodeIO.compatibility(json)
        if (compatibility == NodeIO.Compatibility.INVALID)
            return NodeImportResult.Rejected(path, "Not a versioned node graph")
        val candidate = runCatching { NodeGraph.fromJson(registry, json) }.getOrElse {
            return NodeImportResult.Rejected(path, it.message ?: "Invalid node graph")
        }
        if (candidate.nodes.isEmpty() && !json.contains("\"nodes\""))
            return NodeImportResult.Rejected(path, "Not a node graph")
        load(json)
        return NodeImportResult.Imported(path, graph.nodes.size, graph.links.size, compatibility)
    }

    fun exportSvg(path: Path): Path = NodeGraphExport.writeSvg(graph, path)

    fun exportPng(name: String = "node-graph"): String? {
        val capture = BrassDemoCapture.current ?: return null
        val image = capture.grab(getLeft(), getTop(), getWidth(), getHeight()) ?: return null
        return capture.writePng(name, image)
    }

    fun accessibilityEntries(): List<NodeAccessibilityEntry> = graph.nodes.filterNot { it.closing }.map { node ->
        val required = node.type.inputs.count { !it.optional }
        NodeAccessibilityEntry(
            node.id,
            node.type.title,
            "${node.type.inputs.size} inputs, ${node.type.outputs.size} outputs, $required required",
            node.selected,
        )
    }

    fun accessibilitySummary(): String {
        val selected = graph.nodes.count { it.selected && !it.closing }
        return "${graph.nodes.count { !it.closing }} nodes, ${graph.links.count { !it.closing }} links, $selected selected"
    }

    fun diagnostics(): List<NodeDiagnostic> {
        if (diagnosticRevision != revision) {
            diagnosticCache = NodeGraphDiagnostics.inspect(graph)
            diagnosticRevision = revision
        }
        return diagnosticCache
    }

    fun navigatorSnapshot(): NodeNavigatorSnapshot = NodeGraphNavigator.snapshot(graph)

    fun inspect(node: GraphNode): NodeInspectorSnapshot = NodeGraphInspector.inspect(graph, scheduler, node)

    fun inspectSelection(): NodeInspectorSnapshot? =
        graph.nodes.firstOrNull { it.selected && !it.closing }?.let(::inspect)

    fun focusNode(nodeId: Int): Boolean {
        val node = graph.byId(nodeId)?.takeUnless { it.closing } ?: return false
        selection.select(node)
        panX = getWidth() / 2f - (node.x + node.width / 2f) * zoom
        panY = getHeight() / 2f - (node.y + NodeLayout.height(node) / 2f) * zoom
        cameraAnim = null
        zoomEase.snapTo(zoom)
        return true
    }

    fun centerAt(wx: Float, wy: Float) {
        panX = getWidth() / 2f - wx * zoom
        panY = getHeight() / 2f - wy * zoom
        cameraAnim = null
        zoomEase.snapTo(zoom)
    }

    /** The current camera, for persistence. Read on close, restore with [setView] on open. */
    val view: EditorView get() = EditorView(panX, panY, zoom)

    /**
     * Restore a camera previously captured with [view]; zoom is clamped to the canvas limits. The
     * editor is marked as already framed, so the restored camera wins over the open-time auto-frame
     * (call [frameAll] explicitly if framing is wanted).
     */
    fun setView(view: EditorView) {
        zoom = view.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = view.panX
        panY = view.panY
        cameraAnim = null
        zoomEase.snapTo(zoom)
        framed = true
    }

    /** The current canvas zoom, where 1f is 100%. Read for host chrome such as a zoom readout. */
    val zoomLevel: Float get() = zoom

    /**
     * Smoothly zoom by [factor] around a widget-local anchor point. Hosts use this for header zoom
     * controls (the default anchor is the canvas centre, so a bare call behaves like the +/− keys);
     * the zoom eases to the new level over a few frames instead of snapping, keeping the world point
     * under the anchor fixed throughout. Wheel zooming stays instant so it tracks the cursor.
     */
    fun zoomAt(factor: Float, localX: Float = getWidth() / 2f, localY: Float = getHeight() / 2f) {
        cameraAnim = null
        zoomAnchorLX = localX
        zoomAnchorLY = localY
        zoomAnchorWX = worldX(localX)
        zoomAnchorWY = worldY(localY)
        zoomEase.target = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /** Frame every node, comment and group in the graph. */
    fun frameAll() {
        framed = false
    }

    /** Frame the current selection. */
    fun frameSelection() = frameSelected()

    /**
     * Smoothly glide the camera so every node in [list] is centred and fully visible (zoom clamped
     * to the canvas limits and [maxZoom]). The first open of a graph and the auto-layout both route
     * through this, so the camera never jumps.
     */
    fun smoothFrame(list: List<GraphNode>, maxZoom: Float = 1f): Boolean {
        if (list.isEmpty()) return false
        return smoothCameraTo(
            list.minOf { it.x },
            list.minOf { it.y },
            list.maxOf { it.x + it.width },
            list.maxOf { it.y + NodeLayout.height(it) },
            maxZoom,
        )
    }

    /** Glide the camera to centre and fit the world rect `[minX, minY]..[maxX, maxY]`. */
    private fun smoothCameraTo(
        minX: Float, minY: Float, maxX: Float, maxY: Float,
        maxZoom: Float = 1f,
    ): Boolean {
        val w = getWidth(); val h = getHeight()
        if (w <= 0f || h <= 0f) return false
        val gw = (maxX - minX).coerceAtLeast(1f)
        val gh = (maxY - minY).coerceAtLeast(1f)
        val targetZoom = minOf((w - 48f) / gw, (h - 64f) / gh, maxZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        cameraAnim = CameraFlight(
            panX, panY, zoom,
            w / 2f - cx * targetZoom, h / 2f - cy * targetZoom, targetZoom,
            time, 0.5f,
        )
        return true
    }

    fun viewportWorldBounds(): FloatArray =
        floatArrayOf(worldX(0f), worldY(0f), worldX(getWidth()), worldY(getHeight()))

    /**
     * Open the scalable, keyboard-first command surface. It is transient and instant because this is
     * a high-frequency action; the canvas itself remains free of permanent navigation chrome.
     */
    fun showCommandPalette(wx: Float? = null, wy: Float? = null) {
        val (mouseX, mouseY) = mouseLocal()
        val spawnX = wx ?: worldX(mouseX)
        val spawnY = wy ?: worldY(mouseY)
        val commands = mutableListOf<BrassCommandPalette.Command>()
        graph.nodes.filterNot { it.closing }.forEach { node ->
            commands += BrassCommandPalette.Command(
                "Go to ${node.type.title} #${node.id}", "Nodes",
            ) { focusNode(node.id) }
        }
        if (!readOnly) registry.all().forEach { type ->
            commands += BrassCommandPalette.Command(
                "Add ${type.title}", "Nodes",
            ) {
                history.record("Add ${type.title}") {
                    graph.spawn(type.id, spawnX, spawnY)?.let {
                        rememberType(type.id)
                        selection.select(it)
                    }
                }
            }
        }
        commands += BrassCommandPalette.Command("Frame all", "View", "Home") { framed = false }
        commands += BrassCommandPalette.Command("Frame selection", "View", ".") { frameSelected() }
        commands += BrassCommandPalette.Command(
            if (snapToGrid) "Disable grid snapping" else "Enable grid snapping", "View", "G",
        ) { snapToGrid = !snapToGrid }
        commands += BrassCommandPalette.Command("Run graph", "Execution", "F5") { runGraph() }
        commands += BrassCommandPalette.Command("Step graph", "Execution", "F6") { stepGraph() }
        if (scheduler.state == ExecutionState.RUNNING || scheduler.state == ExecutionState.PAUSED)
            commands += BrassCommandPalette.Command("Stop graph", "Execution", "Shift+F5") { cancelRun() }
        graph.bookmarks.forEach { bookmark ->
            commands += BrassCommandPalette.Command("Go to bookmark ${bookmark.name}", "Bookmarks") {
                goToBookmark(bookmark.name)
            }
        }
        diagnostics().forEach { diagnostic ->
            diagnostic.nodeId?.let { nodeId ->
                commands += BrassCommandPalette.Command(
                    "Issue: ${diagnostic.message}", diagnostic.severity.name.lowercase(),
                ) { focusNode(nodeId) }
            }
        }
        BrassCommandPalette(commands, "Search nodes and commands…").also {
            it.entranceEnabled = false
            it.show(Window.of(this))
        }
    }

    // Keyboard

    private fun onKey(keyCode: Int) {
        // A floating text input (quick-entry menu, frame rename) owns the keyboard - none of the
        // editor shortcuts (delete, arrows, …) should fire while someone is typing.
        if (BrassFocus.focused is BrassTextField) return
        val ctrl = UKeyboard.isCtrlKeyDown()
        val shift = UKeyboard.isShiftKeyDown()
        when {
            ctrl && keyCode == GLFW.GLFW_KEY_P -> showCommandPalette()
            shift && keyCode == GLFW.GLFW_KEY_F5 -> cancelRun()
            keyCode == GLFW.GLFW_KEY_F5 -> runGraph()
            keyCode == GLFW.GLFW_KEY_F6 -> stepGraph()
            !readOnly && keyCode == GLFW.GLFW_KEY_F9 ->
                graph.nodes.filter { it.selected && !it.closing }.forEach(::toggleBreakpoint)
            keyCode == GLFW.GLFW_KEY_TAB -> cycleSelection(reverse = shift)
            !readOnly && !ctrl && keyCode in setOf(
                GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN,
            ) -> nudgeSelection(keyCode, if (shift) NodeLayout.GRID else 1f)
            ctrl && keyCode in GLFW.GLFW_KEY_1..GLFW.GLFW_KEY_9 -> {
                val name = (keyCode - GLFW.GLFW_KEY_0).toString()
                if (!readOnly && shift) saveBookmark(name) else goToBookmark(name)
            }
            !readOnly && (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) -> deleteSelection()
            keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER -> selection.clear()
            ctrl && keyCode == GLFW.GLFW_KEY_A -> selectAll()
            ctrl && shift && keyCode == GLFW.GLFW_KEY_I -> invertSelection()
            !readOnly && ctrl && keyCode == GLFW.GLFW_KEY_D -> duplicateSelection()
            !readOnly && ctrl && keyCode == GLFW.GLFW_KEY_G -> {
                val (lx, ly) = mouseLocal()
                groupSelection()?.let { openFrameEditor(it, originX + lx, originY + ly) }
            }
            ctrl && keyCode == GLFW.GLFW_KEY_C -> copySelection()
            !readOnly && ctrl && keyCode == GLFW.GLFW_KEY_V -> paste()
            !readOnly && ctrl && shift && keyCode == GLFW.GLFW_KEY_Z -> redo()
            !readOnly && ctrl && keyCode == GLFW.GLFW_KEY_Z -> undo()
            !readOnly && ctrl && keyCode == GLFW.GLFW_KEY_Y -> redo()
            keyCode == GLFW.GLFW_KEY_HOME -> { framed = false }
            keyCode == GLFW.GLFW_KEY_PERIOD -> frameSelected()
            keyCode == GLFW.GLFW_KEY_G -> snapToGrid = !snapToGrid
            keyCode == GLFW.GLFW_KEY_EQUAL -> zoomAt(1.15f)
            keyCode == GLFW.GLFW_KEY_MINUS -> zoomAt(1f / 1.15f)
            !readOnly && shift && keyCode == GLFW.GLFW_KEY_A -> {
                val (lx, ly) = mouseLocal(); openAddMenu(lx, ly, worldX(lx), worldY(ly))
            }
        }
    }

    // Selection + mutation

    private fun clearSelection() {
        marqueeBox = null
        selection.clear()
    }

    private fun select(node: GraphNode, additive: Boolean, toggle: Boolean = false) {
        // Any direct click-based selection is a "normal" selection - no persistent box.
        marqueeBox = null
        selection.select(node, additive, toggle)
    }

    private fun selectAll() {
        selection.all()
    }

    private fun invertSelection() {
        selection.invert()
    }

    private fun cycleSelection(reverse: Boolean) {
        val nodes = graph.nodes.filterNot { it.closing }
        if (nodes.isEmpty()) return
        val current = nodes.indexOfFirst { it.selected }
        val next = when {
            current < 0 -> if (reverse) nodes.lastIndex else 0
            reverse -> (current - 1 + nodes.size) % nodes.size
            else -> (current + 1) % nodes.size
        }
        clearSelection()
        nodes[next].selected = true
    }

    private fun nudgeSelection(keyCode: Int, amount: Float) {
        val selected = graph.nodes.filter { it.selected && !it.closing }
        if (selected.isEmpty()) return
        val dx = when (keyCode) {
            GLFW.GLFW_KEY_LEFT -> -amount
            GLFW.GLFW_KEY_RIGHT -> amount
            else -> 0f
        }
        val dy = when (keyCode) {
            GLFW.GLFW_KEY_UP -> -amount
            GLFW.GLFW_KEY_DOWN -> amount
            else -> 0f
        }
        val moves = selected.map {
            MoveNodesCommand.Move(it.id, it.x, it.y, it.x + dx, it.y + dy)
        }
        history.push(MoveNodesCommand(moves, "Nudge"))
    }

    private fun selectInBox() {
        val box = mode as? Mode.Box ?: return
        selection.inBox(box.startX, box.startY, boxCurX, boxCurY)
    }

    /** Select the nodes inside the live drag box; a non-additive box replaces each frame. */
    private fun liveBoxSelect(additive: Boolean) {
        val box = mode as? Mode.Box ?: return
        if (!additive) graph.nodes.forEach { it.selected = false }
        selection.inBox(box.startX, box.startY, boxCurX, boxCurY)
    }

    /** Bounds of the current node selection `[minX, minY, maxX, maxY]`, or null when empty. */
    private fun selectionBox(): FloatArray? {
        val sel = graph.nodes.filter { it.selected && !it.closing }
        if (sel.isEmpty()) return null
        return floatArrayOf(
            sel.minOf { it.x }, sel.minOf { it.y },
            sel.maxOf { it.x + it.width }, sel.maxOf { it.y + NodeLayout.height(it) },
        )
    }

    private fun deleteSelection() {
        val sel = graph.nodes.filter { it.selected }
        val selLinks = graph.links.filter { it.selected }
        if (sel.isEmpty() && selLinks.isEmpty()) return
        history.record("Delete") {
            selLinks.forEach { disconnect(it) }
            for (n in sel) { graph.links.removeAll { it.from === n || it.to === n }; n.closing = true; n.pop.target = 0f }
        }
    }

    private fun duplicateSelection() {
        val sel = graph.nodes.filter { it.selected }
        if (sel.isEmpty()) return
        history.record("Duplicate") {
            clearSelection()
            val copies = LinkedHashMap<Int, GraphNode>()
            for (n in sel) graph.spawn(n.type.id, n.x + 18f, n.y + 18f)?.let { copy ->
                n.copyValuesTo(copy); copy.selected = true; copies[n.id] = copy
            }
            val internal = graph.links.filter { it.from in sel && it.to in sel && !it.closing }.toList()
            for (link in internal) {
                val from = copies[link.from.id] ?: continue
                val to = copies[link.to.id] ?: continue
                graph.link(from, link.fromPort, to, link.toPort)?.reroutes?.addAll(
                    link.reroutes.map { ReroutePoint(it.x + 18f, it.y + 18f) },
                )
            }
        }
    }

    private fun copySelection() {
        val sel = graph.nodes.filter { it.selected }
        if (sel.isEmpty()) return
        val tmp = NodeGraph(registry)
        val copies = LinkedHashMap<Int, GraphNode>()
        for (n in sel) tmp.spawn(n.type.id, n.x, n.y)?.let { copy -> n.copyValuesTo(copy); copies[n.id] = copy }
        graph.links.filter { it.from in sel && it.to in sel && !it.closing }.forEach { link ->
            val from = copies[link.from.id] ?: return@forEach
            val to = copies[link.to.id] ?: return@forEach
            tmp.link(from, link.fromPort, to, link.toPort)?.reroutes?.addAll(
                link.reroutes.map { ReroutePoint(it.x, it.y) },
            )
        }
        clipboard = tmp.toJson()
    }

    private fun paste() {
        val json = clipboard ?: return
        history.record("Paste") {
            clearSelection()
            val tmp = NodeGraph.fromJson(registry, json)
            val copies = LinkedHashMap<Int, GraphNode>()
            for (n in tmp.nodes) graph.spawn(n.type.id, n.x + 24f, n.y + 24f)?.let { copy ->
                n.copyValuesTo(copy); copy.selected = true; copies[n.id] = copy
            }
            for (link in tmp.links) {
                val from = copies[link.from.id] ?: continue
                val to = copies[link.to.id] ?: continue
                graph.link(from, link.fromPort, to, link.toPort)?.reroutes?.addAll(
                    link.reroutes.map { ReroutePoint(it.x + 24f, it.y + 24f) },
                )
            }
        }
    }

    enum class Alignment { LEFT, CENTER_X, RIGHT, TOP, CENTER_Y, BOTTOM }

    fun alignSelection(alignment: Alignment) {
        val selected = graph.nodes.filter { it.selected && !it.closing }
        if (readOnly || selected.size < 2) return
        val before = selected.associate { it.id to (it.x to it.y) }
        when (alignment) {
            Alignment.LEFT -> selected.forEach { it.x = selected.minOf { n -> n.x } }
            Alignment.CENTER_X -> {
                val center = selected.map { it.x + it.width / 2f }.average().toFloat()
                selected.forEach { it.x = center - it.width / 2f }
            }
            Alignment.RIGHT -> {
                val right = selected.maxOf { it.x + it.width }
                selected.forEach { it.x = right - it.width }
            }
            Alignment.TOP -> selected.forEach { it.y = selected.minOf { n -> n.y } }
            Alignment.CENTER_Y -> {
                val center = selected.map { it.y + NodeLayout.height(it) / 2f }.average().toFloat()
                selected.forEach { it.y = center - NodeLayout.height(it) / 2f }
            }
            Alignment.BOTTOM -> {
                val bottom = selected.maxOf { it.y + NodeLayout.height(it) }
                selected.forEach { it.y = bottom - NodeLayout.height(it) }
            }
        }
        val moves = selected.map { node ->
            val start = before.getValue(node.id)
            MoveNodesCommand.Move(node.id, start.first, start.second, node.x, node.y)
        }
        MoveNodesCommand(moves, "Align").takeIf { it.moved }?.let(history::push)
    }

    private fun bringToFront(node: GraphNode) {
        if (graph.nodes.lastOrNull() !== node) { graph.nodes.remove(node); graph.nodes.add(node) }
    }

    // History

    private fun undo() { finishNoteEditing(); history.undo() }
    private fun redo() { finishNoteEditing(); history.redo() }

    /** Push a [SnapshotCommand] for a live edit that began at [editBefore], if it changed anything. */
    private fun pushEditSnapshot(label: String) {
        val before = editBefore ?: return
        editBefore = null
        val after = graph.toBson()
        if (!before.contentEquals(after)) history.push(SnapshotCommand(before, after, label))
    }

    private fun emitGraphChange(label: String) {
        val change = GraphChange(++revision, label, graph.toBson())
        changeListeners.forEach { listener -> runCatching { listener(change) } }
    }

    private fun rememberType(typeId: String) {
        workflow.remember(typeId)
    }

    private fun smartDragDelta(mode: Mode.DragNode, dx: Float, dy: Float): Pair<Float, Float> {
        val anchorStart = mode.starts[mode.node.id] ?: return dx to dy
        val nodeW = mode.node.width
        val nodeH = NodeLayout.height(mode.node)
        val xAnchors = floatArrayOf(anchorStart.first + dx, anchorStart.first + dx + nodeW / 2f, anchorStart.first + dx + nodeW)
        val yAnchors = floatArrayOf(anchorStart.second + dy, anchorStart.second + dy + nodeH / 2f, anchorStart.second + dy + nodeH)
        val threshold = 4f / zoom
        var bestX: Pair<Float, Float>? = null
        var bestY: Pair<Float, Float>? = null
        graph.nodes.filter { it.id !in mode.starts && !it.closing }.forEach { other ->
            val otherX = floatArrayOf(other.x, other.x + other.width / 2f, other.x + other.width)
            val otherH = NodeLayout.height(other)
            val otherY = floatArrayOf(other.y, other.y + otherH / 2f, other.y + otherH)
            xAnchors.forEach { moving ->
                otherX.forEach { target ->
                    val correction = target - moving
                    if (abs(correction) <= threshold && (bestX == null || abs(correction) < abs(bestX!!.first)))
                        bestX = correction to target
                }
            }
            yAnchors.forEach { moving ->
                otherY.forEach { target ->
                    val correction = target - moving
                    if (abs(correction) <= threshold && (bestY == null || abs(correction) < abs(bestY!!.first)))
                        bestY = correction to target
                }
            }
        }
        guideX = bestX?.second
        guideY = bestY?.second
        return (dx + (bestX?.first ?: 0f)) to (dy + (bestY?.first ?: 0f))
    }

    // Context menus

    private fun openCompatibleMenu(wire: Mode.Wire, lx: Float, ly: Float, wx: Float, wy: Float) {
        if (readOnly) return
        val output = wire.node.type.outputs.getOrNull(wire.port) ?: return
        val compatible = registry.all().mapNotNull { type ->
            val input = type.inputs.indexOfFirst { !it.hidden && it.type.accepts(output.type) }
            if (input >= 0) type to input else null
        }
        if (compatible.isEmpty()) return
        val items = compatible.map { (type, input) ->
            BrassContextMenu.Item(type.title) {
                history.record("Auto-connect ${type.title}") {
                    graph.spawn(type.id, wx, wy - NodeLayout.HEADER)?.let { created ->
                        rememberType(type.id)
                        clearSelection()
                        created.selected = true
                        graph.link(wire.node, wire.port, created, input)?.flash = 1f
                    }
                }
            }
        }
        BrassContextMenu(items, rowWidth = 142).show(Window.of(this), originX + lx, originY + ly)
    }

    private fun beginNoteEditing(comment: GraphComment) {
        if (editingCommentId == comment.id) return
        finishNoteEditing()
        val root = Window.of(this)
        noteEditBefore = graph.toBson()
        editingCommentId = comment.id
        val input = BrassTextArea(comment.text, "Add note…") { comment.text = it }.apply {
            chrome = BrassChrome.NONE
            entranceEnabled = false
            contentScale = zoom
        }
        input.constrain {
            x = basicXConstraint { originX + panX + (comment.x + 1f) * zoom }
            y = basicYConstraint { originY + panY + (comment.y + NOTE_HEADER) * zoom }
            width = basicWidthConstraint { ((comment.width - 2f) * zoom).coerceAtLeast(48f) }
            height = basicHeightConstraint {
                ((comment.height - NOTE_HEADER - 1f) * zoom).coerceAtLeast(20f)
            }
        } childOf root
        input.onFocusLost { finishNoteEditing() }
        noteEditor = input
        BrassFocus.focus(input)
        if (comment.text.isNotEmpty()) input.selectAll()
    }

    private fun finishNoteEditing() {
        val input = noteEditor ?: return
        noteEditor = null
        editingCommentId = null
        val before = noteEditBefore
        noteEditBefore = null
        input.parent.children.takeIf { input in it }?.let { input.parent.removeChild(input) }
        val after = graph.toBson()
        if (before != null && !before.contentEquals(after)) history.push(SnapshotCommand(before, after, "Edit note"))
    }

    private fun openFrameEditor(frame: GraphFrame, sx: Float, sy: Float) {
        val input = BrassTextInput(frame.title, "Group name")
        input.onSubmit = { text ->
            val title = text.trim().ifEmpty { "Group" }
            if (title != frame.title) history.record("Rename group") { frame.title = title }
            BrassContextMenu.closeOpen()
        }
        BrassContextMenu.custom(input, 174, 27).show(Window.of(this), sx, sy)
    }

    private fun openCommentMenu(comment: GraphComment, sx: Float, sy: Float) {
        BrassContextMenu(listOf(
            BrassContextMenu.Item("Edit text") { beginNoteEditing(comment) },
            BrassContextMenu.Item("Change color") {
                openDecorationPalette(
                    comment.tone, comment.customColor, comment.color(), sx, sy, "Note color",
                    onTone = { tone ->
                        comment.tone = tone
                        comment.customColor = null
                    },
                    onCustom = { comment.customColor = it.rgb },
                )
            },
            BrassContextMenu.Item("Remove note") {
                if (editingCommentId == comment.id) finishNoteEditing()
                history.record("Remove note") { graph.comments.remove(comment) }
            },
        )).show(Window.of(this), sx, sy)
    }

    private fun openFrameMenu(frame: GraphFrame, sx: Float, sy: Float) {
        val parent = graph.frames.firstOrNull { candidate ->
            candidate !== frame && frame.x >= candidate.x && frame.y >= candidate.y &&
                frame.x + frame.width <= candidate.x + candidate.width &&
                frame.y + frame.height <= candidate.y + candidate.height
        }
        val items = mutableListOf(
            BrassContextMenu.Item("Rename group") { openFrameEditor(frame, sx, sy) },
            BrassContextMenu.Item("Change color") {
                openDecorationPalette(
                    frame.tone, frame.customColor, frame.color(), sx, sy, "Group color",
                    onTone = { tone ->
                        frame.tone = tone
                        frame.customColor = null
                    },
                    onCustom = { frame.customColor = it.rgb },
                )
            },
            BrassContextMenu.Item(if (frame.autoResize) "Use fixed frame" else "Keep fitted to nodes") {
                history.record("Resize group") {
                    frame.autoResize = !frame.autoResize
                    frame.resizeToContents(graph)
                }
            },
        )
        if (frame.parentFrameId != null) {
            items += BrassContextMenu.Item("Move out of parent group") { nestFrame(frame, null) }
        } else if (parent != null) {
            items += BrassContextMenu.Item("Move into containing group") { nestFrame(frame, parent) }
        }
        items += BrassContextMenu.Item("Remove group (keep nodes)") {
            history.record("Delete group") {
                graph.frames.remove(frame)
                graph.frames.filter { it.parentFrameId == frame.id }.forEach { it.parentFrameId = null }
            }
        }
        BrassContextMenu(items).show(Window.of(this), sx, sy)
    }

    private fun openDecorationPalette(
        tone: FrameTone,
        customColor: Int?,
        current: Color,
        sx: Float,
        sy: Float,
        label: String,
        onTone: (FrameTone) -> Unit,
        onCustom: (Color) -> Unit,
    ) {
        val root = Window.of(this)
        val palette = NodeDecorationPalette(
            selectedTone = tone,
            customColor = customColor,
            onTone = { selected ->
                history.record(label) { onTone(selected) }
                BrassContextMenu.closeOpen()
            },
            onCustom = { pickerX, pickerY, pickerTop ->
                val before = graph.toBson()
                val picker = BrassColorPicker(current, onCustom)
                BrassContextMenu.custom(picker, width = 150, height = 150).also { menu ->
                    menu.onDismiss = {
                        val after = graph.toBson()
                        if (!before.contentEquals(after)) history.push(SnapshotCommand(before, after, label))
                    }
                    menu.show(root, pickerX, pickerY, anchorTop = pickerTop)
                }
            },
        )
        BrassContextMenu.custom(palette, width = 104, height = 43).show(root, sx, sy)
    }

    /** Bulk actions for a box-selected group: duplicate, frame it, disconnect, delete. */
    private fun openBulkMenu(sx: Float, sy: Float) {
        val root = Window.of(this) ?: return
        BrassContextMenu(
            listOf(
                BrassContextMenu.Item("Duplicate") { duplicateSelection() },
                BrassContextMenu.Item("Group") { groupSelection() },
                BrassContextMenu.Item("Disconnect") {
                    history.record("Disconnect") {
                        graph.links.filter { it.from.selected || it.to.selected }.forEach { disconnect(it) }
                    }
                },
                BrassContextMenu.Item("Delete") { deleteSelection() },
            ),
            rowWidth = 150,
        ).show(root, sx, sy)
    }

    private fun rightClick(lx: Float, ly: Float, wx: Float, wy: Float) {
        val root = Window.of(this)
        val sx = originX + lx; val sy = originY + ly
        // A right-press on a field control opens its quick-entry menu (dropdown for enums, a
        // focused text entry for values) instead of the node's context menu.
        controlAt(wx, wy)?.let { hit ->
            if (!readOnly && hit.field.onRightPress(root, this, sx, sy)) return
        }
        rerouteAt(wx, wy)?.let { (link, point) ->
            if (readOnly) return
            BrassContextMenu(listOf(
                BrassContextMenu.Item("Remove reroute") {
                    history.record("Remove reroute") { link.reroutes.removeAt(point) }
                },
            )).show(root, sx, sy)
            return
        }
        nodeAt(wx, wy)?.let { node ->
            select(node, additive = false)
            if (readOnly) return
            val items = mutableListOf(
                BrassContextMenu.Item(if (node.collapsed) "Expand" else "Collapse") { history.record("Collapse") { node.collapsed = !node.collapsed } },
                BrassContextMenu.Item("Duplicate") { select(node, false); duplicateSelection() },
                BrassContextMenu.Item("Disconnect") { history.record("Disconnect") { graph.links.filter { it.from === node || it.to === node }.forEach { disconnect(it) } } },
                BrassContextMenu.Item("Delete") { select(node, false); deleteSelection() },
            )
            if (nodeMenuDebug) {
                items += BrassContextMenu.Item(
                    if (node.id in scheduler.breakpoints) "Remove breakpoint" else "Add breakpoint",
                ) { toggleBreakpoint(node) }
            }
            items += BrassContextMenu.Item(
                if (node.type.id in favoriteTypeIds) "Remove favorite" else "Add favorite",
            ) { setFavorite(node.type.id, node.type.id !in favoriteTypeIds) }
            if (nodeMenuDebug && node.type.outputs.isNotEmpty()) {
                val watching = node.type.outputs.indices.any { PortRef(node.id, it) in scheduler.watches }
                items += BrassContextMenu.Item(if (watching) "Unwatch outputs" else "Watch outputs") {
                    node.type.outputs.indices.forEach { watch(node, it, !watching) }
                }
                val previewing = node.type.outputs.indices.any { PortRef(node.id, it) in previewedOutputs }
                items += BrassContextMenu.Item(if (previewing) "Hide preview" else "Preview outputs") {
                    node.type.outputs.indices.forEach { preview(node, it, !previewing) }
                }
            }
            registry.nodeActions.forEach { action ->
                items += BrassContextMenu.Item(action.label) { action.perform(this, node) }
            }
            BrassContextMenu(items).show(root, sx, sy)
            return
        }
        commentAt(wx, wy)?.let { comment ->
            if (readOnly) return
            openCommentMenu(comment, sx, sy)
            return
        }
        frameHeaderAt(wx, wy)?.let { frame ->
            if (readOnly) return
            openFrameMenu(frame, sx, sy)
            return
        }
        wireAt(wx, wy)?.let { link ->
            if (readOnly) return
            BrassContextMenu(listOf(
                BrassContextMenu.Item("Add reroute") {
                    history.record("Add reroute") { graph.reroute(link, wx, wy) }
                },
                BrassContextMenu.Item("Delete wire") { history.record("Delete wire") { disconnect(link) } },
            )).show(root, sx, sy)
            return
        }
        // A right-click on empty canvas with nodes selected offers the bulk actions for the group;
        // otherwise it is the plain add-node menu.
        if (graph.nodes.any { it.selected && !it.closing }) {
            openBulkMenu(sx, sy)
        } else if (!readOnly) {
            openAddMenu(lx, ly, wx, wy)
        }
    }

    private fun openAddMenu(lx: Float, ly: Float, wx: Float, wy: Float) {
        val items = mutableListOf<BrassContextMenu.Item>()
        if (canvasMenuAddNodes) {
            val recent = workflow.recentIds().withIndex().associate { it.value to it.index }
            val types = registry.all().sortedWith(
                compareBy<NodeType> {
                    when {
                        it.id in favoriteTypeIds -> -100
                        it.id in recent -> recent.getValue(it.id)
                        else -> 100
                    }
                }.thenBy { it.title },
            )
            types.forEach { type ->
                val marker = when {
                    type.id in favoriteTypeIds -> "★ "
                    type.id in recent -> "↺ "
                    else -> ""
                }
                items += BrassContextMenu.Item(marker + type.title) {
                    history.record("Add ${type.title}") {
                        graph.spawn(type.id, wx, wy)?.let {
                            rememberType(type.id)
                            select(it, false)
                        }
                    }
                }
            }
        }
        if (graph.nodes.any { it.selected && !it.closing })
            items += BrassContextMenu.Item("Create template") {
                createTemplate("Template ${templates.size + 1}")
            }
        templates.values.forEach { template ->
            items += BrassContextMenu.Item("Template: ${template.name}") {
                instantiateTemplate(template.name, wx, wy)
            }
        }
        items += BrassContextMenu.Item("Add note") {
            addComment("", wx, wy)?.let(::beginNoteEditing)
        }
        if (graph.nodes.any { it.selected && !it.closing })
            items += BrassContextMenu.Item("Group selected nodes") {
                groupSelection()?.let { openFrameEditor(it, originX + lx, originY + ly) }
            }
        if (canvasMenuRun) {
            when (scheduler.state) {
                ExecutionState.PAUSED -> {
                    items += BrassContextMenu.Item("Continue") { runGraph() }
                    items += BrassContextMenu.Item("Run next node") { stepGraph() }
                    items += BrassContextMenu.Item("Stop") { cancelRun() }
                }
                ExecutionState.RUNNING -> items += BrassContextMenu.Item("Stop") { cancelRun() }
                else -> {
                    items += BrassContextMenu.Item("Run graph") { runGraph() }
                    items += BrassContextMenu.Item("Run one node") { stepGraph() }
                }
            }
        }
        if (BrassDemoCapture.current != null)
            items += BrassContextMenu.Item("Export PNG") { exportPng() }
        registry.canvasActions.forEach { action ->
            items += BrassContextMenu.Item(action.label) { action.perform(this, null) }
        }
        if (items.isNotEmpty()) BrassContextMenu(items, rowWidth = 142).show(Window.of(this), originX + lx, originY + ly)
    }

    // View helpers

    private fun zoomBy(factor: Float, lx: Float, ly: Float) {
        cameraAnim = null
        val wx = worldX(lx); val wy = worldY(ly)
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = lx - wx * zoom; panY = ly - wy * zoom
        zoomEase.snapTo(zoom)
    }

    private fun frameSelected() {
        val sel = graph.nodes.filter { it.selected }
        frameNodes(if (sel.isEmpty()) graph.nodes else sel, maxZoom = 1.25f)
    }

    fun centerSelection() {
        val selected = graph.nodes.filter { it.selected && !it.closing }
        if (selected.isEmpty()) return
        val minX = selected.minOf { it.x }
        val maxX = selected.maxOf { it.x + it.width }
        val minY = selected.minOf { it.y }
        val maxY = selected.maxOf { it.y + NodeLayout.height(it) }
        panX = getWidth() / 2f - (minX + maxX) / 2f * zoom
        panY = getHeight() / 2f - (minY + maxY) / 2f * zoom
        cameraAnim = null
        zoomEase.snapTo(zoom)
    }

    private fun frameNodes(list: List<GraphNode>, maxZoom: Float = 1f) {
        if (list.isEmpty()) return
        val w = getWidth(); val h = getHeight(); if (w <= 0f || h <= 0f) return
        // The gallery and a few host panels reserve a narrow action gutter on the right. Frame within
        // the guaranteed canvas instead of centring under that gutter, so the final node never clips.
        val viewportW = (w - 40f).coerceAtLeast(120f)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (n in list) {
            minX = minOf(minX, n.x); minY = minOf(minY, n.y)
            maxX = maxOf(maxX, n.x + n.width); maxY = maxOf(maxY, n.y + NodeLayout.height(n))
        }
        val gw = (maxX - minX).coerceAtLeast(1f); val gh = (maxY - minY).coerceAtLeast(1f)
        zoom = minOf((viewportW - 48f) / gw, (h - 64f) / gh, maxZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = (viewportW - gw * zoom) / 2f - minX * zoom
        panY = (h - gh * zoom) / 2f - minY * zoom
        zoomEase.snapTo(zoom)
    }

    // Hit testing

    private fun nodeAt(wx: Float, wy: Float): GraphNode? {
        return hitTester.nodeAt(wx, wy)
    }

    private fun portAt(wx: Float, wy: Float): Triple<GraphNode, Int, Boolean>? =
        hitTester.portAt(wx, wy)

    private class ControlHit(val node: GraphNode, val field: NodeField, val x1: Float, val x2: Float)

    private fun controlAt(wx: Float, wy: Float): ControlHit? {
        val n = nodeAt(wx, wy) ?: return null
        if (n.roll.value > 0.5f) return null
        for (f in n.fields.filter { it.visibleWhen() && it.reveal.value > 0.5f }) {
            val r = NodeLayout.fieldRow(n, f)
            val ctrlL = NodeLayout.controlLeft(n)
            if (wy in r[1]..r[3] && wx in ctrlL..r[2]) return ControlHit(n, f, ctrlL, r[2])
        }
        return null
    }

    private fun chevronAt(wx: Float, wy: Float, node: GraphNode): Boolean =
        abs(wx - NodeLayout.chevronX(node)) <= 7f && abs(wy - NodeLayout.chevronY(node)) <= 7f

    private fun wireAt(wx: Float, wy: Float): Link? = hitTester.wireAt(wx, wy)

    private fun rerouteAt(wx: Float, wy: Float): Pair<Link, Int>? =
        hitTester.rerouteAt(wx, wy, REROUTE_HIT)

    private fun commentAt(wx: Float, wy: Float): GraphComment? =
        hitTester.commentAt(wx, wy)

    private fun frameHeaderAt(wx: Float, wy: Float): GraphFrame? =
        hitTester.frameHeaderAt(wx, wy, FRAME_HEADER)

    private fun noteMenuHit(comment: GraphComment, wx: Float, wy: Float): Boolean =
        wy in comment.y..(comment.y + NOTE_HEADER) &&
            wx in (comment.x + comment.width - MENU_HIT_W)..(comment.x + comment.width)

    private fun frameMenuHit(frame: GraphFrame, wx: Float, wy: Float): Boolean =
        wy in frame.y..(frame.y + FRAME_HEADER) &&
            wx in (frame.x + frame.width - MENU_HIT_W)..(frame.x + frame.width)

    private fun frameNodeIds(frame: GraphFrame): Set<Int> {
        val ids = LinkedHashSet(frame.nodeIds)
        graph.frames.filter { it.parentFrameId == frame.id }.forEach { ids += frameNodeIds(it) }
        return ids
    }

    private fun frameTree(frame: GraphFrame): List<GraphFrame> = buildList {
        add(frame)
        graph.frames.filter { it.parentFrameId == frame.id }.forEach { addAll(frameTree(it)) }
    }

    // Per-frame animation

    private fun advance(mouseWx: Float, mouseWy: Float) {
        // Drive the smooth camera glide (first-open frame, Home, auto-layout centring). A user press
        // that starts panning cancels it; the ease runs before the zoom ease so they stay in sync.
        cameraAnim?.let { f ->
            if (mode !is Mode.Idle) {
                cameraAnim = null
            } else {
                val t = ((time - f.start) / f.duration).coerceIn(0f, 1f)
                val e = 1f - (1f - t) * (1f - t) * (1f - t)
                panX = f.fromPanX + (f.toPanX - f.fromPanX) * e
                panY = f.fromPanY + (f.toPanY - f.fromPanY) * e
                zoom = f.fromZoom + (f.toZoom - f.fromZoom) * e
                zoomEase.snapTo(zoom)
                if (t >= 1f) cameraAnim = null
            }
        }
        // Drive the auto-layout glide: nodes ease to their target spots; frames follow their
        // members. Any graph change (undo/redo/load/edit) or a user press that starts a drag drops
        // the animation without touching positions, so history's snapshot or the drag stays
        // authoritative and the glide can never fight back.
        layoutAnim?.let { anim ->
            if (revision != layoutAnimRevision || mode !is Mode.Idle) {
                layoutAnim = null
                frameAnim = null
            } else {
                var done = true
                for ((id, f) in anim) {
                    val node = graph.byId(id) ?: continue
                    val t = ((time - f.start) / f.duration).coerceIn(0f, 1f)
                    val e = 1f - (1f - t) * (1f - t) * (1f - t)
                    node.x = f.from[0] + (f.to[0] - f.from[0]) * e
                    node.y = f.from[1] + (f.to[1] - f.from[1]) * e
                    if (t < 1f) done = false
                }
                if (done) layoutAnim = null
            }
        }
        frameAnim?.let { anim ->
            if (revision != layoutAnimRevision || mode !is Mode.Idle) {
                layoutAnim = null
                frameAnim = null
            } else {
                var done = true
                for ((id, f) in anim) {
                    val frame = graph.frames.firstOrNull { it.id == id } ?: continue
                    val t = ((time - f.start) / f.duration).coerceIn(0f, 1f)
                    val e = 1f - (1f - t) * (1f - t) * (1f - t)
                    frame.x = f.from[0] + (f.to[0] - f.from[0]) * e
                    frame.y = f.from[1] + (f.to[1] - f.from[1]) * e
                    frame.width = f.from[2] + (f.to[2] - f.from[2]) * e
                    frame.height = f.from[3] + (f.to[3] - f.from[3]) * e
                    if (t < 1f) done = false
                }
                if (done) frameAnim = null
            }
        }
        // Drive the eased zoom from the toolbar +/- buttons. The world point under the captured
        // anchor stays fixed while the zoom eases, so the canvas grows/shrinks around that point.
        val easedZoom = zoomEase.advance()
        if (easedZoom != zoom) {
            zoom = easedZoom
            panX = zoomAnchorLX - zoomAnchorWX * zoom
            panY = zoomAnchorLY - zoomAnchorWY * zoom
        }
        noteEditor?.contentScale = zoom
        val idle = mode is Mode.Idle
        hoverPort = if (idle || mode is Mode.Wire) portAt(mouseWx, mouseWy) else null
        hoverField = if (idle && hoverPort == null) controlAt(mouseWx, mouseWy)?.field else null
        hoverNode = if (idle) nodeAt(mouseWx, mouseWy) else (mode as? Mode.DragNode)?.node
        hoverWire = if (idle && hoverPort == null && hoverField == null && hoverNode == null) wireAt(mouseWx, mouseWy) else null
        updateTip()
        updateCursor()

        val draggingNode = (mode as? Mode.DragNode)?.node
        val wiring = mode as? Mode.Wire
        val ct = (14f * BrassClock.dt).coerceAtMost(1f)
        for (n in graph.nodes) {
            n.hover.target = if (n === hoverNode && !n.closing) 1f else 0f
            n.lift.target = if (n === hoverNode || n === draggingNode) 1f else 0f
            n.sel.target = if (n.selected) 1f else 0f
            n.roll.target = if (n.collapsed) 1f else 0f
            n.hover.advance(); n.lift.advance(); n.sel.advance(); n.roll.advance(); n.pop.advance()
            // Inputs: a hovered input glows green when the dragged wire could land there, reddens when it
            // could not. With no wire in flight, any hovered port simply glows.
            for (i in n.glowIn.indices) {
                val hovered = hoverPort?.let { it.first === n && it.second == i && !it.third } == true
                val valid = hovered && (wiring == null || canConnect(wiring.node, wiring.port, n, i))
                val reject = hovered && wiring != null && !valid
                n.glowIn[i] += ((if (valid) 1f else 0f) - n.glowIn[i]) * ct
                n.rejectIn[i] += ((if (reject) 1f else 0f) - n.rejectIn[i]) * ct
            }
            // Outputs are never a valid drop for a wire dragged from an output, so they reject while wiring.
            for (i in n.glowOut.indices) {
                val hovered = hoverPort?.let { it.first === n && it.second == i && it.third } == true
                val self = wiring != null && wiring.node === n && wiring.port == i
                val valid = hovered && wiring == null
                val reject = hovered && wiring != null && !self
                n.glowOut[i] += ((if (valid) 1f else 0f) - n.glowOut[i]) * ct
                n.rejectOut[i] += ((if (reject) 1f else 0f) - n.rejectOut[i]) * ct
            }
            for (f in n.fields) {
                f.reveal.target = if (f.visibleWhen()) 1f else 0f
                f.reveal.advance()
                f.hover.target = if (f === hoverField) 1f else 0f
                f.press.target = if (f === pressedField) 1f else 0f
                f.hover.advance(); f.press.advance()
            }
        }
        for (l in graph.links) {
            l.sel.target = if (l.selected || l === hoverWire) 1f else 0f
            l.sel.advance(); l.flash = (l.flash - BrassClock.dt * 2f).coerceAtLeast(0f)
            if (l.closing) l.fade.target = 0f
            l.fade.advance()
        }
        graph.links.removeAll { it.closing && it.fade.value < 0.02f }
        graph.nodes.removeAll { it.closing && it.pop.value < 0.02f }
    }

    /** Whether an output port could legally wire to an input port - the rule [NodeGraph.link] enforces. */
    private fun canConnect(from: GraphNode, fromPort: Int, to: GraphNode, toPort: Int): Boolean {
        return graph.validateLink(from, fromPort, to, toPort).allowed
    }

    /** Begin a wire's disconnect animation; [advance] removes it once it has retracted. */
    private fun disconnect(link: Link) { link.closing = true; link.selected = false }

    private fun updateTip() {
        // A field can ask for a fully custom tooltip (a frequency field paints its item slots). Swap the
        // tooltip entry to a custom one only when the field changes, then let its own draw run per frame.
        hoverField?.let { f ->
            if (f.tooltipSize(this) != null) {
                if (customTipField !== f) {
                    BrassTooltip.attachCustom(
                        this,
                        BrassTooltip.Custom(
                            size = { f.tooltipSize(this) ?: floatArrayOf(0f, 0f) },
                            draw = { m, x, y, a -> f.drawTooltip(m, this, x, y, a) },
                        ),
                    )
                    customTipField = f
                }
                return
            }
        }
        // Any other target uses the default text entry - restore it if a custom one was showing.
        restoreTextTip()

        hoverWire?.let { link ->
            val text = wireStrengthTooltip?.invoke(link)
            if (text != null) { tipTitle = "Wire"; tipBody = text; return }
        }
        hoverPort?.let { (n, i, out) ->
            val p = if (out) n.type.outputs[i] else n.type.inputs[i]
            tipTitle = p.name
            tipBody = p.description ?: "${p.type.label ?: p.type.id} · ${if (out) "output" else "input"}"
            return
        }
        hoverField?.let { tipTitle = it.tip(); tipBody = it.description; return }
        hoverNode?.let {
            tipTitle = it.type.title
            tipBody = it.type.description ?: "${it.type.inputs.size} in · ${it.type.outputs.size} out"
            return
        }
        tipTitle = null; tipBody = null
    }

    /** Put the default text tooltip entry back after a custom one was shown for a field. */
    private fun restoreTextTip() {
        if (customTipField != null) {
            customTipField = null
            BrassTooltip.attachLazy(this, { tipTitle ?: "" }, { tipBody })
        }
    }

    /** The cursor follows what the mouse is doing: crosshair while marqueeing, move while dragging
     *  (or hovering a grabbable node), arrow elsewhere. Gated on the cursor being over the canvas. */
    private fun updateCursor() {
        val (mx, my) = getMousePosition()
        if (mx < getLeft() || mx > getRight() || my < getTop() || my > getBottom()) return
        when {
            mode is Mode.Box -> BrassCursor.request(BrassCursor.Kind.CROSSHAIR)
            mode is Mode.DragNode || mode is Mode.DragGroup || mode is Mode.DragFrame ||
                mode is Mode.DragComment || mode is Mode.DragReroute ->
                BrassCursor.request(BrassCursor.Kind.MOVE)
            hoverNode != null -> BrassCursor.request(BrassCursor.Kind.MOVE)
            else -> BrassCursor.request(BrassCursor.Kind.ARROW)
        }
    }

    // Draw

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        time += BrassClock.dt
        originX = getLeft(); originY = getTop()
        val (mlx, mly) = mouseLocal()
        val mouseWx = worldX(mlx); val mouseWy = worldY(mly)

        if (!framed && w > 0 && h > 0 && graph.nodes.isNotEmpty()) {
            // A fresh graph (or Home) glides the camera onto all the nodes instead of jumping, so a
            // new player is never lost at the start.
            smoothFrame(graph.nodes)
            framed = true
        } else if (viewportW > 0f && viewportH > 0f && (w.toFloat() != viewportW || h.toFloat() != viewportH)) {
            if (reframeOnResize && graph.nodes.isNotEmpty()) {
                frameNodes(graph.nodes)
            } else {
                // Keep the same world point centred as a host window changes the viewport. Zoom
                // remains untouched, so resizing never fights an intentional user view.
                panX += (w - viewportW) / 2f
                panY += (h - viewportH) / 2f
                cameraAnim = null
                zoomEase.snapTo(zoom)
            }
        }
        viewportW = w.toFloat()
        viewportH = h.toFloat()

        advance(mouseWx, mouseWy)

        // recessed canvas frame (screen space)
        val l = x.toFloat(); val t = y.toFloat(); val r = (x + w).toFloat(); val b = (y + h).toFloat()
        BrassCard.panel(m, l, t, r, b, fill = Colors.INK_950)

        // one zoom transform for everything on the canvas
        m.push()
        m.translate(floor(originX + panX), floor(originY + panY), 0f)
        m.scale(zoom, zoom, 1f)
        // Visible world rectangle, with a margin so a node's shadow/halo just off the edge is not clipped.
        // Everything on the canvas is culled against this - the point of the exercise when zoomed in.
        val margin = CULL_MARGIN / zoom
        val ctx = NodeDrawCtx(
            m, this, zoom, time, mouseWx, mouseWy, originX, originY, panX, panY,
            worldX(0f) - margin, worldY(0f) - margin,
            worldX(w.toFloat()) + margin, worldY(h.toFloat()) + margin,
            detail = NodeDrawCtx.smoothstep(0.04f, 0.55f, zoom),
        )
        // One shared quad batch for every flat overview rect and straight wire line this frame, so a
        // massive tree at low zoom costs a handful of draw calls instead of one per node/wire.
        val lodRects = BrassPaint.QuadBatch(ctx.m)
        ctx.lodRects = lodRects

        drawGrid(m, w.toFloat(), h.toFloat())

        // A 2-px gutter around the editor: everything except the grid is clipped just inside the
        // widget's edges, so a node or wire can never sit flush against the border. The scissor is
        // screen-space, so it holds no matter how the canvas is zoomed or panned.
        val gutter = ScissorEffect(
            x + GUTTER_PX, y + GUTTER_PX,
            x + w - GUTTER_PX, y + h - GUTTER_PX,
            true,
        )
        gutter.beforeDraw(m)
        drawOrganization(ctx)
        drawGuides(m, w.toFloat(), h.toFloat())
        for (link in graph.links) if (linkVisible(ctx, link)) drawLink(ctx, link)

        // LOD pass first: every visible node's flat rect joins one batched draw call, so the chrome
        // cards drawn next can cross-fade over a cheap, crisp silhouette instead of hundreds of
        // per-node tessellator starts.
        // Occlusion only matters when node contents are actually drawn (zoomed in); the LOD
        // overview is all batched rects, so the sweep would cost more than it saves.
        if (ctx.detail >= OCCLUSION_MIN_DETAIL) {
            computeOcclusion()
            ctx.coveredBy = { node, rx1, ry1, rx2, ry2 ->
                coverers[node.id]?.any { it[0] <= rx1 && it[1] <= ry1 && it[2] >= rx2 && it[3] >= ry2 } == true
            }
        } else {
            occludedNodes.clear()
            coverers.clear()
            occlusionValid = false
            ctx.coveredBy = null
        }
        for (node in graph.nodes) if (node.id !in occludedNodes && nodeVisible(ctx, node)) NodeView.drawLod(ctx, node)
        lodRects.flush()

        (mode as? Mode.Wire)?.let { wm ->
            val base = wm.node.type.outputs[wm.port].type.color()
            val over = hoverPort
            val col = when {
                over != null && !over.third && canConnect(wm.node, wm.port, over.first, over.second) ->
                    Colors.mix(base, Colors.UI_ACCENT_BRIGHT, 0.55f)
                over != null && over.first !== wm.node -> Colors.mix(base, Colors.DANGER, 0.6f)
                else -> base
            }
            NodeWire.draw(ctx, NodeLayout.outputX(wm.node), NodeLayout.outputY(wm.node, wm.port), wireEndWx, wireEndWy,
                col, 0f, 0f, dashed = true)
        }
        for (node in graph.nodes) if (node.id !in occludedNodes && nodeVisible(ctx, node)) {
            NodeView.draw(ctx, graph, node, nodeValue(node))
        }
        drawSelectionOutline(m)
        drawDebugger(ctx)
        gutter.afterDraw(m)
        (mode as? Mode.Box)?.let { drawBox(m, it) }
        m.pop()

    }

    /**
     * Occlusion: a node fully covered by another node drawn above it (later in the draw list) is
     * invisible, so its card and widgets are skipped. Purely a rendering optimisation - the visuals
     * are identical, but a pile of stacked nodes no longer pays for hidden glyphs and cards.
     * Near-linear: nodes are bucketed into a coarse spatial hash (each node joins every cell its box
     * overlaps), so each node only tests candidates that could actually cover it, with a
     * size pre-reject before the containment test. Skipped entirely when zoomed out (the LOD pass
     * is batched rects there) and above a node-count cap.
     */
    private fun computeOcclusion() {
        val nodes = graph.nodes
        if (nodes.size < 2 || nodes.size > OCCLUSION_MAX_NODES) {
            occludedNodes.clear()
            coverers.clear()
            occlusionValid = false
            return
        }
        // Cheap dirty check: a static graph's occlusion never changes, so hash revision + positions
        // and skip the whole sweep when nothing moved since the last frame.
        var hash = revision.toLong()
        for (n in nodes) if (!n.closing) {
            hash = hash * 31 + java.lang.Double.doubleToRawLongBits((n.x * 7f + n.y * 13f).toDouble())
        }
        if (occlusionValid && hash == occlusionStamp) return
        occlusionStamp = hash
        occlusionValid = false
        occludedNodes.clear()
        coverers.clear()
        val n = nodes.size
        // Cache geometry once so the sweep never recomputes eased heights.
        val x1 = FloatArray(n)
        val y1 = FloatArray(n)
        val x2 = FloatArray(n)
        val y2 = FloatArray(n)
        for (i in 0 until n) {
            val node = nodes[i]
            x1[i] = node.x
            y1[i] = node.y
            x2[i] = node.x + node.width
            y2[i] = node.y + NodeLayout.height(node)
        }
        val cell = OCCLUSION_CELL
        val buckets = HashMap<Long, MutableList<Int>>()
        fun cellKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)
        // Proximity gate: no pair can overlap (and thus no node can cover another) unless two nodes
        // share a spatial cell. When nothing is close, the sweep ends here for free.
        var anyClose = false
        for (i in 0 until n) {
            if (nodes[i].closing) continue
            for (cx in floor(x1[i] / cell).toInt()..floor(x2[i] / cell).toInt())
                for (cy in floor(y1[i] / cell).toInt()..floor(y2[i] / cell).toInt()) {
                    val key = cellKey(cx, cy)
                    val list = buckets.getOrPut(key) { ArrayList() }
                    if (list.isNotEmpty()) anyClose = true
                    list.add(i)
                }
        }
        if (!anyClose) {
            occlusionValid = true
            return
        }
        // Stamp-based dedupe (a large node sits in several of a's cells): O(1) per a, no per-a fill.
        val tested = IntArray(n)
        var stamp = 0
        for (i in 0 until n) {
            if (nodes[i].closing) continue
            stamp++
            val aw = x2[i] - x1[i]
            val ah = y2[i] - y1[i]
            outer@ for (cx in floor(x1[i] / cell).toInt()..floor(x2[i] / cell).toInt())
                for (cy in floor(y1[i] / cell).toInt()..floor(y2[i] / cell).toInt()) {
                    val candidates = buckets[cellKey(cx, cy)] ?: continue
                    for (j in candidates) {
                        if (j <= i || tested[j] == stamp) continue
                        tested[j] = stamp
                        if (nodes[j].closing) continue
                        // Disjoint boxes can never cover anything; reject before the rest.
                        if (x2[j] < x1[i] || x1[j] > x2[i] || y2[j] < y1[i] || y1[j] > y2[i]) continue
                        coverers.getOrPut(nodes[i].id) { ArrayList() }
                            .add(floatArrayOf(x1[j], y1[j], x2[j], y2[j]))
                        // A covering node must be at least as large in both dimensions.
                        if (x2[j] - x1[j] < aw || y2[j] - y1[j] < ah) continue
                        if (x1[j] <= x1[i] && y1[j] <= y1[i] && x2[j] >= x2[i] && y2[j] >= y2[i]) {
                            occludedNodes.add(nodes[i].id)
                            break@outer
                        }
                    }
                }
        }
        occlusionValid = true
    }

    /** The right-drag marquee rectangle kept after release - exactly as it was dragged, never
     *  re-fitted to the nodes. Normal click/shift selections show nothing. Same look as the drag
     *  marquee, with the border clamped to a minimum of one screen pixel so it never thins to
     *  sub-pixel and clips in and out when zoomed way out. */
    private fun drawSelectionOutline(m: UMatrixStack) {
        val b = marqueeBox ?: return
        val (minX, minY, maxX, maxY) = b
        BrassPaint.rect(m, minX, minY, maxX, maxY, Colors.withAlpha(Colors.UI_ACCENT, 40))
        BrassPaint.border(m, minX, minY, maxX, maxY, Colors.UI_ACCENT, minPx())
    }

    /** World-unit thickness that renders at least one screen pixel at the current zoom. */
    private fun minPx(): Float = maxOf(1f, 1f / zoom)

    /** Whether [node]'s card (plus a little slack for its shadow and selection halo) is on screen. */
    private fun nodeVisible(ctx: NodeDrawCtx, node: GraphNode): Boolean =
        ctx.visible(node.x - 4f, node.y - 6f, node.x + node.width + 4f, node.y + NodeLayout.height(node) + 6f)

    /**
     * Whether [link]'s curve could touch the viewport. The bounding box of the routed points is widened
     * horizontally by the Bézier handle (which bulges the curve sideways past its endpoints, most of all
     * on a right-to-left wire) so a wire is never culled while a visible part of its bend is still on
     * screen.
     */
    private fun linkVisible(ctx: NodeDrawCtx, link: Link): Boolean {
        if (link.fade.value <= 0.001f) return false
        val pts = linkPoints(link)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var bulge = 30f
        for ((a, b) in pts.zipWithNext()) bulge = maxOf(bulge, abs(b.first - a.first) * 0.5f)
        for ((px, py) in pts) {
            if (px < minX) minX = px; if (px > maxX) maxX = px
            if (py < minY) minY = py; if (py > maxY) maxY = py
        }
        return ctx.visible(minX - bulge - 4f, minY - 4f, maxX + bulge + 4f, maxY + 4f)
    }

    private fun drawLink(ctx: NodeDrawCtx, link: Link) {
        val fade = link.fade.value
        if (fade <= 0.001f) return
        val saved = BrassAmbientFade.current
        val points = linkPoints(link)
        val type = link.portType()
        val color = type.color()
        // The full-quality curve is drawn at every zoom - NodeWire scales its cells to at least one
        // screen pixel, so there is no line/curve switch and nothing sub-pixel to fragment.
        BrassAmbientFade.current = saved * fade
        for ((index, segment) in points.zipWithNext().withIndex()) {
            NodeWire.draw(
                ctx, segment.first.first, segment.first.second, segment.second.first, segment.second.second,
                color, link.sel.value, link.flash,
                dashed = type.wireStyle == WireStyle.DASHED,
                arrow = type.arrow && index == points.size - 2,
                symbol = if (index == points.size - 2) type.symbol else null,
                strength = wireStrength(link),
                showSymbol = showWireSymbols,
                moteFade = NodeDrawCtx.smoothstep(0.3f, 0.5f, zoom),
            )
        }
        for (point in link.reroutes) {
            val r = 3f
            val hot = hypot(ctx.mouseWx - point.x, ctx.mouseWy - point.y) <= REROUTE_HIT
            BrassKeycap.draw(
                ctx.m, point.x - r, point.y - r, r * 2f, r * 2f,
                bg = type.color(),
                border = if (hot) Colors.UI_TEXT_HOVER else type.color(),
                outer = Colors.UI_OUTER_BORDER,
                bottom = Colors.INK_900, flat = false, defaultAccent = false,
            )
        }
        link.to.type.inputs.getOrNull(link.toPort)?.endLabel?.let { label ->
            val tx = NodeLayout.inputX(link.to) - BrassFont.width(this, label) - 8f
            BrassFont.draw(
                ctx.m, this, label, tx,
                NodeLayout.inputY(link.to, link.toPort) - BrassFont.LINE - 2f, Colors.UI_TEXT_DARK,
            )
        }
        BrassAmbientFade.current = saved
    }

    private fun linkPoints(link: Link): List<Pair<Float, Float>> = buildList {
        add(NodeLayout.outputX(link.from) to NodeLayout.outputY(link.from, link.fromPort))
        link.reroutes.forEach { add(it.x to it.y) }
        add(NodeLayout.inputX(link.to) to NodeLayout.inputY(link.to, link.toPort))
    }

    private fun drawBox(m: UMatrixStack, box: Mode.Box) {
        val x1 = minOf(box.startX, boxCurX); val x2 = maxOf(box.startX, boxCurX)
        val y1 = minOf(box.startY, boxCurY); val y2 = maxOf(box.startY, boxCurY)
        BrassPaint.rect(m, x1, y1, x2, y2, Colors.withAlpha(Colors.UI_ACCENT, 40))
        // The marquee border stays at least one screen pixel thick when zoomed out.
        BrassPaint.border(m, x1, y1, x2, y2, Colors.UI_ACCENT, minPx())
    }

    private fun drawOrganization(ctx: NodeDrawCtx) {
        for (frame in graph.frames) {
            frame.resizeToContents(graph)
            val color = frame.color()
            BrassPaint.rect(
                ctx.m, frame.x, frame.y, frame.x + frame.width, frame.y + frame.height,
                Colors.withAlpha(color, 18),
            )
            BrassPaint.border(ctx.m, frame.x, frame.y, frame.x + frame.width, frame.y + frame.height,
                Colors.withAlpha(color, 105))
            BrassPaint.rect(
                ctx.m, frame.x + 1f, frame.y + 1f,
                frame.x + frame.width - 1f, frame.y + FRAME_HEADER,
                Colors.withAlpha(color, 34),
            )
            val count = frame.nodeIds.count { graph.byId(it)?.closing == false }
            val title = BrassFont.fit(this, "${frame.title} · $count", frame.width - 27f)
            BrassFont.draw(ctx.m, this, title, frame.x + 6f, frame.y + 5f, color)
            val gripX = frame.x + frame.width - 14f
            repeat(3) { i ->
                BrassPaint.rect(
                    ctx.m, gripX + i * 3f, frame.y + 7f,
                    gripX + i * 3f + 1f, frame.y + 8f,
                    Colors.withAlpha(color, 150),
                )
            }
        }
        for (comment in graph.comments) {
            val color = comment.color()
            BrassPaint.rect(
                ctx.m, comment.x, comment.y,
                comment.x + comment.width, comment.y + comment.height,
                Colors.withAlpha(Colors.UI_ELEMENT_BG, 238),
            )
            BrassPaint.border(
                ctx.m, comment.x, comment.y,
                comment.x + comment.width, comment.y + comment.height,
                Colors.withAlpha(color, 125),
            )
            BrassPaint.rect(
                ctx.m, comment.x + 1f, comment.y + 1f,
                comment.x + comment.width - 1f, comment.y + NOTE_HEADER,
                Colors.withAlpha(color, 38),
            )
            BrassFont.draw(ctx.m, this, "NOTE", comment.x + 5f, comment.y + 4f, color)
            val gripX = comment.x + comment.width - 14f
            repeat(3) { i ->
                BrassPaint.rect(
                    ctx.m, gripX + i * 3f, comment.y + 6f,
                    gripX + i * 3f + 1f, comment.y + 7f,
                    Colors.withAlpha(color, 150),
                )
            }
            if (editingCommentId != comment.id) {
                val empty = comment.text.isBlank()
                noteLines(comment.text, comment.width - 10f, 2).forEachIndexed { index, line ->
                    BrassFont.draw(
                        ctx.m, this, line, comment.x + 5f,
                        comment.y + NOTE_HEADER + 5f + index * BrassFont.LINE,
                        if (empty) Colors.UI_TEXT_DARK else Colors.UI_TEXT,
                    )
                }
            }
        }
    }

    private fun noteLines(text: String, width: Float, limit: Int): List<String> {
        val lines = ArrayList<String>()
        for (paragraph in text.lines()) {
            var rest = paragraph.trim()
            if (rest.isEmpty()) {
                lines += ""
                continue
            }
            while (rest.isNotEmpty() && lines.size < limit) {
                var cut = rest.length
                while (cut > 1 && BrassFont.width(this, rest.take(cut)) > width) {
                    val word = rest.lastIndexOf(' ', cut - 1)
                    cut = if (word > 0) word else cut - 1
                }
                val last = lines.size == limit - 1 && cut < rest.length
                lines += if (last) BrassFont.fit(this, rest, width) else rest.take(cut).trim()
                rest = rest.drop(cut).trimStart()
            }
            if (lines.size >= limit) break
        }
        return lines.filterNot { it.isEmpty() }.ifEmpty { listOf("Add note…") }
    }

    private fun drawDebugger(ctx: NodeDrawCtx) {
        for (node in graph.nodes) {
            val runState = scheduler.nodeStates[node.id]
            val outline = when {
                scheduler.pausedAt == node.id -> Colors.WARN
                runState == NodeRunState.FAILED -> Colors.DANGER
                runState == NodeRunState.RUNNING -> Colors.UI_ACCENT_BRIGHT
                scheduler.lastExecutedNodeId == node.id &&
                    scheduler.state in setOf(ExecutionState.PAUSED, ExecutionState.COMPLETED) ->
                    Colors.PATINA_400
                else -> null
            }
            outline?.let {
                BrassPaint.border(
                    ctx.m, node.x - 2f, node.y - 2f,
                    node.x + node.width + 2f, node.y + NodeLayout.height(node) + 2f,
                    Colors.withAlpha(it, 190),
                )
            }
            if (node.id in scheduler.breakpoints) {
                val color = if (scheduler.pausedAt == node.id) Colors.WARN else Colors.DANGER
                BrassPaint.rect(ctx.m, node.x + 4f, node.y + 4f, node.x + 8f, node.y + 8f, color)
            }
            val watched = scheduler.watchedValues.filterKeys {
                it.nodeId == node.id && it in previewedOutputs
            }
            if (watched.isNotEmpty() && zoom > 0.65f) {
                val text = watched.entries.joinToString(" · ") { (ref, value) ->
                    "${node.type.outputs.getOrNull(ref.port)?.name ?: ref.port}=$value"
                }
                val fit = BrassFont.fit(this, text, node.width - 12f)
                val top = node.y + NodeLayout.height(node) + 3f
                BrassCard.miniKeycap(ctx.m, node.x, top, node.x + node.width, top + BrassFont.LINE + 8f, hot = false)
                BrassFont.draw(
                    ctx.m, this, fit, node.x + 6f, top + 4f,
                    Colors.UI_ACCENT_BRIGHT,
                )
            }
        }
    }

    private fun drawGuides(m: UMatrixStack, w: Float, h: Float) {
        val left = worldX(0f)
        val right = worldX(w)
        val top = worldY(0f)
        val bottom = worldY(h)
        val color = Colors.withAlpha(Colors.UI_ACCENT_BRIGHT, 140)
        guideX?.let { BrassPaint.rect(m, it, top, it + 1f / zoom, bottom, color) }
        guideY?.let { BrassPaint.rect(m, left, it, right, it + 1f / zoom, color) }
    }

    private fun drawGrid(m: UMatrixStack, w: Float, h: Float) {
        val step = NodeLayout.GRID
        val spacing = step * zoom
        if (spacing < 1.5f) return
        // The grid fades out smoothly as lines crowd together, then thins before it vanishes - it
        // never pops out of existence the way a hard threshold did.
        val fade = NodeDrawCtx.smoothstep(2.5f, 8f, spacing)
        val stride = if (spacing < 4f) 2 else 1
        val minor = Colors.withAlpha(Colors.EDGE, (34 * fade).toInt())
        val major = Colors.withAlpha(Colors.EDGE, (70 * fade).toInt())
        val wl = worldX(0f); val wr = worldX(w); val wt = worldY(0f); val wb = worldY(h)
        var k = floor(wl / step).toInt()
        while (k * step <= wr) {
            val gx = k * step
            if (k % stride == 0) BrassPaint.rect(m, gx, wt, gx + 1f, wb, if (k % 4 == 0) major else minor)
            k++
        }
        k = floor(wt / step).toInt()
        while (k * step <= wb) {
            val gy = k * step
            if (k % stride == 0) BrassPaint.rect(m, wl, gy, wr, gy + 1f, if (k % 4 == 0) major else minor)
            k++
        }
    }

    // Demo + default registry

    companion object : BrassDemoSource {

        /** Screen pixels a right-drag must travel before it becomes a box selection. */
        private const val BOX_DRAG_THRESHOLD = 4f

        /** The empty gutter around the canvas edges - content clips 2 px inside the widget. */
        private const val GUTTER_PX = 2f

        /** Above this many nodes the occlusion sweep is skipped (the check would cost more than it saves). */
        private const val OCCLUSION_MAX_NODES = 3000
        /** Only run occlusion once node contents are drawn - the LOD overview is all batched rects. */
        private const val OCCLUSION_MIN_DETAIL = 0.22f
        /** Spatial-hash cell for the occlusion sweep - coarser than a node, so covers are found fast. */
        private const val OCCLUSION_CELL = 256f

        /** Deep zoom-out floor - far enough to survey a very large tree as a whole. */
        private const val MIN_ZOOM = 0.02f
        private const val MAX_ZOOM = 2.2f
        /** Screen-pixel slack added around the viewport before culling, so nothing pops at the edge. */
        private const val CULL_MARGIN = 64f
        private const val REROUTE_HIT = 7f
        private const val FRAME_HEADER = 16f
        private const val NOTE_HEADER = 14f
        private const val MENU_HIT_W = 22f
        private const val DOUBLE_CLICK_MS = 300L

        override fun demo() = BrassDemo(
            "node-editor", "Node editor", 440f, 300f,
            card = false, shrinkToFit = true,
        ) { sample().apply { reframeOnResize = true } }

        /** The default palette of node types (see [DefaultNodes]) - what a bare editor is built with. */
        fun defaultRegistry(): NodeRegistry = DefaultNodes.registry()

        /** The demo graph: a small signal chain touching every port, field and wire type. */
        fun sample(): BrassNodeEditor {
            val e = BrassNodeEditor()
            val time = e.spawn("time", 0f, 40f)!!
            val noise = e.spawn("noise", 190f, 0f)!!
            val grad = e.spawn("gradient", 190f, 150f)!!
            val xform = e.spawn("transform", 380f, 30f)!!
            val out = e.spawn("output", 380f, 190f)!!
            e.spawn("sequence", 0f, 175f)!!
            e.link(time, 0, noise, 0)
            e.link(noise, 0, grad, 0)
            val colourLink = e.link(grad, 0, out, 0)!!
            e.graph.reroute(colourLink, 485f, 185f)
            e.link(xform, 0, out, 1)
            e.graph.frame("SIGNAL PIPELINE", listOf(noise.id, grad.id), FrameTone.PATINA)
            e.graph.nodes.forEach { it.pop.snapTo(1f) }
            return e
        }
    }
}
