package net.swzo.brass.ui.kit.node

import gg.essential.elementa.components.Window
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.base.BrassEased
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.surface.BrassContextMenu
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.text.BrassFont
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

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
 * +/- zoom, Shift+A add node.
 */
class BrassNodeEditor(
    val registry: NodeRegistry = defaultRegistry(),
) : BrassWidget(BrassAccent.DEFAULT) {

    val graph = NodeGraph(registry)

    // View transform. world -> local(widget) pixel: local = pan + world * zoom; and back for hit-testing.
    private var panX = 0f
    private var panY = 0f
    private var zoom = 1f
    private var framed = false
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
        class DragNode(val node: GraphNode, val grabX: Float, val grabY: Float) : Mode
        class Wire(val node: GraphNode, val port: Int) : Mode
        class Scrub(val onDrag: (Float) -> Unit) : Mode
        class Box(val startX: Float, val startY: Float) : Mode
    }

    private var mode: Mode = Mode.Idle
    private var lastLX = 0f
    private var lastLY = 0f
    private var wireEndWx = 0f
    private var wireEndWy = 0f
    private var boxCurX = 0f
    private var boxCurY = 0f
    private var pressedField: NodeField? = null

    // hover (for animation + tooltips), recomputed each frame
    private var hoverNode: GraphNode? = null
    private var hoverField: NodeField? = null
    private var hoverPort: Triple<GraphNode, Int, Boolean>? = null
    private var hoverWire: Link? = null
    private var tipTitle: String? = null
    private var tipBody: String? = null

    // undo / clipboard
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    private var clipboard: String? = null

    init {
        chrome = BrassChrome.NONE
        enableEffect(ScissorEffect())
        BrassTooltip.attachLazy(this, { tipTitle ?: "" }, { tipBody })

        onMouseClick { e ->
            grabWindowFocus()
            val lx = e.relativeX; val ly = e.relativeY
            lastLX = lx; lastLY = ly
            val wx = worldX(lx); val wy = worldY(ly)

            if (e.mouseButton == 2) { mode = Mode.Pan; return@onMouseClick }
            if (e.mouseButton == 1) { rightClick(lx, ly, wx, wy); return@onMouseClick }
            if (e.mouseButton != 0) return@onMouseClick
            val shift = UKeyboard.isShiftKeyDown()

            portAt(wx, wy)?.let { (node, port, isOut) ->
                select(node, additive = false)
                if (isOut) { mode = Mode.Wire(node, port); wireEndWx = wx; wireEndWy = wy }
                else graph.links.firstOrNull { it.to === node && it.toPort == port }?.let { ex ->
                    graph.links.remove(ex); mode = Mode.Wire(ex.from, ex.fromPort); wireEndWx = wx; wireEndWy = wy
                }
                return@onMouseClick
            }

            controlAt(wx, wy)?.let { hit ->
                select(hit.node, additive = false)
                pressedField = hit.field
                if (hit.field.opensEditor) {
                    hit.field.showEditor(Window.of(this), this, originX + lx, originY + ly)
                } else {
                    pushUndo()
                    val drag = hit.field.onPress(wx, hit.x1, hit.x2)
                    if (drag != null) mode = Mode.Scrub(drag)
                }
                return@onMouseClick
            }

            nodeAt(wx, wy)?.let { node ->
                if (chevronAt(wx, wy, node)) {
                    node.collapsed = !node.collapsed; select(node, additive = false); return@onMouseClick
                }
                bringToFront(node)
                select(node, additive = shift)
                pushUndo()
                mode = Mode.DragNode(node, wx - node.x, wy - node.y)
                return@onMouseClick
            }

            wireAt(wx, wy)?.let { link ->
                if (!shift) clearSelection()
                link.selected = true
                return@onMouseClick
            }

            if (!shift) clearSelection()
            mode = if (shift) Mode.Box(wx, wy) else Mode.Pan
            boxCurX = wx; boxCurY = wy
        }

        onMouseDrag { mx, my, btn ->
            when (val m = mode) {
                is Mode.Pan -> { panX += mx - lastLX; panY += my - lastLY }
                is Mode.DragNode -> { m.node.x = worldX(mx) - m.grabX; m.node.y = worldY(my) - m.grabY }
                is Mode.Wire -> { wireEndWx = worldX(mx); wireEndWy = worldY(my) }
                is Mode.Scrub -> m.onDrag(worldX(mx))
                is Mode.Box -> { boxCurX = worldX(mx); boxCurY = worldY(my) }
                else -> {}
            }
            lastLX = mx; lastLY = my
        }

        onMouseRelease {
            when (val m = mode) {
                is Mode.Wire -> {
                    val (lx, ly) = mouseLocal()
                    portAt(worldX(lx), worldY(ly))?.let { (node, port, isOut) ->
                        if (!isOut) { pushUndo(); graph.link(m.node, m.port, node, port)?.let { it.flash = 1f } }
                    }
                }
                is Mode.Box -> selectInBox()
                else -> {}
            }
            pressedField = null
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
    fun spawn(typeId: String, wx: Float, wy: Float): GraphNode? = graph.spawn(typeId, wx, wy)

    /** Wire two ports (see [NodeGraph.link]). */
    fun link(from: GraphNode, fromPort: Int, to: GraphNode, toPort: Int): Link? =
        graph.link(from, fromPort, to, toPort)

    /** Serialize the whole graph to the native JSON format. */
    fun save(): String = graph.toJson()

    /** Replace the graph from [json] and frame it. */
    fun load(json: String) { graph.load(json); framed = false }

    // Keyboard

    private fun onKey(keyCode: Int) {
        val ctrl = UKeyboard.isCtrlKeyDown()
        val shift = UKeyboard.isShiftKeyDown()
        when {
            keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE -> deleteSelection()
            ctrl && keyCode == GLFW.GLFW_KEY_D -> duplicateSelection()
            ctrl && keyCode == GLFW.GLFW_KEY_C -> copySelection()
            ctrl && keyCode == GLFW.GLFW_KEY_V -> paste()
            ctrl && shift && keyCode == GLFW.GLFW_KEY_Z -> redo()
            ctrl && keyCode == GLFW.GLFW_KEY_Z -> undo()
            ctrl && keyCode == GLFW.GLFW_KEY_Y -> redo()
            keyCode == GLFW.GLFW_KEY_HOME -> { framed = false }
            keyCode == GLFW.GLFW_KEY_PERIOD -> frameSelected()
            keyCode == GLFW.GLFW_KEY_EQUAL -> zoomBy(1.15f, getWidth() / 2f, getHeight() / 2f)
            keyCode == GLFW.GLFW_KEY_MINUS -> zoomBy(1f / 1.15f, getWidth() / 2f, getHeight() / 2f)
            shift && keyCode == GLFW.GLFW_KEY_A -> {
                val (lx, ly) = mouseLocal(); openAddMenu(lx, ly, worldX(lx), worldY(ly))
            }
        }
    }

    // Selection + mutation

    private fun clearSelection() {
        graph.nodes.forEach { it.selected = false }
        graph.links.forEach { it.selected = false }
    }

    private fun select(node: GraphNode, additive: Boolean) {
        if (!additive) clearSelection()
        node.selected = true
    }

    private fun selectInBox() {
        val x1 = minOf(boxCurX, (mode as? Mode.Box)?.startX ?: boxCurX)
        val x2 = maxOf(boxCurX, (mode as? Mode.Box)?.startX ?: boxCurX)
        val y1 = minOf(boxCurY, (mode as? Mode.Box)?.startY ?: boxCurY)
        val y2 = maxOf(boxCurY, (mode as? Mode.Box)?.startY ?: boxCurY)
        for (n in graph.nodes) {
            val nx2 = n.x + n.width; val ny2 = n.y + NodeLayout.height(n)
            if (n.x < x2 && nx2 > x1 && n.y < y2 && ny2 > y1) n.selected = true
        }
    }

    private fun deleteSelection() {
        val sel = graph.nodes.filter { it.selected }
        val selLinks = graph.links.filter { it.selected }
        if (sel.isEmpty() && selLinks.isEmpty()) return
        pushUndo()
        selLinks.forEach { disconnect(it) }
        for (n in sel) { graph.links.removeAll { it.from === n || it.to === n }; n.closing = true; n.pop.target = 0f }
    }

    private fun duplicateSelection() {
        val sel = graph.nodes.filter { it.selected }
        if (sel.isEmpty()) return
        pushUndo()
        clearSelection()
        for (n in sel) graph.spawn(n.type.id, n.x + 18f, n.y + 18f)?.let { copy -> n.copyValuesTo(copy); copy.selected = true }
    }

    private fun copySelection() {
        val sel = graph.nodes.filter { it.selected }
        if (sel.isEmpty()) return
        val tmp = NodeGraph(registry)
        for (n in sel) tmp.spawn(n.type.id, n.x, n.y)?.let { n.copyValuesTo(it) }
        clipboard = tmp.toJson()
    }

    private fun paste() {
        val json = clipboard ?: return
        pushUndo()
        clearSelection()
        val tmp = NodeGraph.fromJson(registry, json)
        for (n in tmp.nodes) graph.spawn(n.type.id, n.x + 24f, n.y + 24f)?.let { copy -> n.copyValuesTo(copy); copy.selected = true }
    }

    private fun bringToFront(node: GraphNode) {
        if (graph.nodes.lastOrNull() !== node) { graph.nodes.remove(node); graph.nodes.add(node) }
    }

    // Undo

    private fun pushUndo() {
        undo.addLast(graph.toJson()); if (undo.size > 60) undo.removeFirst(); redo.clear()
    }
    private fun undo() { if (undo.isEmpty()) return; redo.addLast(graph.toJson()); graph.load(undo.removeLast()) }
    private fun redo() { if (redo.isEmpty()) return; undo.addLast(graph.toJson()); graph.load(redo.removeLast()) }

    // Context menus

    private fun rightClick(lx: Float, ly: Float, wx: Float, wy: Float) {
        val root = Window.of(this)
        val sx = originX + lx; val sy = originY + ly
        nodeAt(wx, wy)?.let { node ->
            select(node, additive = false)
            BrassContextMenu(listOf(
                BrassContextMenu.Item(if (node.collapsed) "Expand" else "Collapse") { node.collapsed = !node.collapsed },
                BrassContextMenu.Item("Duplicate") { select(node, false); duplicateSelection() },
                BrassContextMenu.Item("Disconnect") { pushUndo(); graph.links.filter { it.from === node || it.to === node }.forEach { disconnect(it) } },
                BrassContextMenu.Item("Delete") { select(node, false); deleteSelection() },
            )).show(root, sx, sy)
            return
        }
        wireAt(wx, wy)?.let { link ->
            BrassContextMenu(listOf(
                BrassContextMenu.Item("Delete wire") { pushUndo(); disconnect(link) },
            )).show(root, sx, sy)
            return
        }
        openAddMenu(lx, ly, wx, wy)
    }

    private fun openAddMenu(lx: Float, ly: Float, wx: Float, wy: Float) {
        val items = registry.all().map { type ->
            BrassContextMenu.Item(type.title) { pushUndo(); graph.spawn(type.id, wx, wy)?.let { select(it, false) } }
        }
        if (items.isNotEmpty()) BrassContextMenu(items, rowWidth = 120).show(Window.of(this), originX + lx, originY + ly)
    }

    // View helpers

    private fun zoomBy(factor: Float, lx: Float, ly: Float) {
        val wx = worldX(lx); val wy = worldY(ly)
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = lx - wx * zoom; panY = ly - wy * zoom
    }

    private fun frameSelected() {
        val sel = graph.nodes.filter { it.selected }
        frameNodes(if (sel.isEmpty()) graph.nodes else sel, maxZoom = 1.25f)
    }

    private fun frameNodes(list: List<GraphNode>, maxZoom: Float = 1f) {
        if (list.isEmpty()) return
        val w = getWidth(); val h = getHeight(); if (w <= 0f || h <= 0f) return
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (n in list) {
            minX = minOf(minX, n.x); minY = minOf(minY, n.y)
            maxX = maxOf(maxX, n.x + n.width); maxY = maxOf(maxY, n.y + NodeLayout.height(n))
        }
        val gw = (maxX - minX).coerceAtLeast(1f); val gh = (maxY - minY).coerceAtLeast(1f)
        zoom = minOf((w - 48f) / gw, (h - 64f) / gh, maxZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = (w - gw * zoom) / 2f - minX * zoom
        panY = (h - gh * zoom) / 2f - minY * zoom
    }

    // Hit testing

    private fun nodeAt(wx: Float, wy: Float): GraphNode? {
        for (n in graph.nodes.asReversed()) {
            if (n.closing) continue
            if (wx >= n.x && wx <= n.x + n.width && wy >= n.y && wy <= n.y + NodeLayout.height(n)) return n
        }
        return null
    }

    private fun portAt(wx: Float, wy: Float): Triple<GraphNode, Int, Boolean>? {
        val r = NodeLayout.PORT_HIT
        for (n in graph.nodes.asReversed()) {
            if (n.closing) continue
            for (i in n.type.inputs.indices)
                if (hypot(wx - NodeLayout.inputX(n), wy - NodeLayout.portY(n, i)) <= r) return Triple(n, i, false)
            for (i in n.type.outputs.indices)
                if (hypot(wx - NodeLayout.outputX(n), wy - NodeLayout.portY(n, i)) <= r) return Triple(n, i, true)
        }
        return null
    }

    private class ControlHit(val node: GraphNode, val field: NodeField, val x1: Float, val x2: Float)

    private fun controlAt(wx: Float, wy: Float): ControlHit? {
        val n = nodeAt(wx, wy) ?: return null
        if (n.roll.value > 0.5f) return null
        val ctrlL = NodeLayout.controlLeft(n)
        for ((row, f) in n.visibleFields().withIndex()) {
            val r = NodeLayout.fieldRow(n, row)
            if (wy in r[1]..r[3] && wx in ctrlL..r[2]) return ControlHit(n, f, ctrlL, r[2])
        }
        return null
    }

    private fun chevronAt(wx: Float, wy: Float, node: GraphNode): Boolean =
        abs(wx - NodeLayout.chevronX(node)) <= 7f && abs(wy - NodeLayout.chevronY(node)) <= 7f

    private fun wireAt(wx: Float, wy: Float): Link? {
        var best: Link? = null; var bestD = 6f
        for (l in graph.links) {
            if (l.closing) continue
            val d = NodeWire.distanceTo(wx, wy,
                NodeLayout.outputX(l.from), NodeLayout.portY(l.from, l.fromPort),
                NodeLayout.inputX(l.to), NodeLayout.portY(l.to, l.toPort))
            if (d < bestD) { bestD = d; best = l }
        }
        return best
    }

    // Per-frame animation

    private fun advance(mouseWx: Float, mouseWy: Float) {
        val idle = mode is Mode.Idle
        hoverPort = if (idle || mode is Mode.Wire) portAt(mouseWx, mouseWy) else null
        hoverField = if (idle && hoverPort == null) controlAt(mouseWx, mouseWy)?.field else null
        hoverNode = if (idle) nodeAt(mouseWx, mouseWy) else (mode as? Mode.DragNode)?.node
        hoverWire = if (idle && hoverPort == null && hoverField == null && hoverNode == null) wireAt(mouseWx, mouseWy) else null
        updateTip()

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
        if (from === to) return false
        val out = from.type.outputs.getOrNull(fromPort) ?: return false
        val inp = to.type.inputs.getOrNull(toPort) ?: return false
        return out.type == inp.type
    }

    /** Begin a wire's disconnect animation; [advance] removes it once it has retracted. */
    private fun disconnect(link: Link) { link.closing = true; link.selected = false }

    private fun updateTip() {
        hoverPort?.let { (n, i, out) ->
            val p = if (out) n.type.outputs[i] else n.type.inputs[i]
            tipTitle = p.name; tipBody = "${p.type.id} ${if (out) "output" else "input"}"; return
        }
        hoverField?.let { tipTitle = it.tip(); tipBody = null; return }
        hoverNode?.let { tipTitle = it.type.title; tipBody = "${it.type.inputs.size} in · ${it.type.outputs.size} out"; return }
        tipTitle = null; tipBody = null
    }

    // Draw

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        time += BrassClock.dt
        originX = getLeft(); originY = getTop()
        val (mlx, mly) = mouseLocal()
        val mouseWx = worldX(mlx); val mouseWy = worldY(mly)

        if (!framed && w > 0 && h > 0 && graph.nodes.isNotEmpty()) { frameNodes(graph.nodes); framed = true }

        advance(mouseWx, mouseWy)

        // recessed canvas frame (screen space)
        val l = x.toFloat(); val t = y.toFloat(); val r = (x + w).toFloat(); val b = (y + h).toFloat()
        BrassCard.panel(m, l, t, r, b, fill = Colors.INK_950)

        // one zoom transform for everything on the canvas
        m.push()
        m.translate(floor(originX + panX), floor(originY + panY), 0f)
        m.scale(zoom, zoom, 1f)
        val ctx = NodeDrawCtx(m, this, zoom, time, mouseWx, mouseWy, originX, originY, panX, panY)

        drawGrid(m, w.toFloat(), h.toFloat())
        for (link in graph.links) drawLink(ctx, link)
        (mode as? Mode.Wire)?.let { wm ->
            val base = wm.node.type.outputs[wm.port].type.color()
            val over = hoverPort
            val col = when {
                over != null && !over.third && canConnect(wm.node, wm.port, over.first, over.second) ->
                    Colors.mix(base, Colors.UI_ACCENT_BRIGHT, 0.55f)
                over != null && over.first !== wm.node -> Colors.mix(base, Colors.DANGER, 0.6f)
                else -> base
            }
            NodeWire.draw(ctx, NodeLayout.outputX(wm.node), NodeLayout.portY(wm.node, wm.port), wireEndWx, wireEndWy,
                col, 0f, 0f, dashed = true)
        }
        for (node in graph.nodes) NodeView.draw(ctx, graph, node)
        (mode as? Mode.Box)?.let { drawBox(m, it) }
        m.pop()

        drawHud(m, l, t)
    }

    private fun drawLink(ctx: NodeDrawCtx, link: Link) {
        val fade = link.fade.value
        if (fade <= 0.001f) return
        val saved = BrassAmbientFade.current
        BrassAmbientFade.current = saved * fade
        NodeWire.draw(
            ctx,
            NodeLayout.outputX(link.from), NodeLayout.portY(link.from, link.fromPort),
            NodeLayout.inputX(link.to), NodeLayout.portY(link.to, link.toPort),
            link.portType().color(), link.sel.value, link.flash,
        )
        BrassAmbientFade.current = saved
    }

    private fun drawBox(m: UMatrixStack, box: Mode.Box) {
        val x1 = minOf(box.startX, boxCurX); val x2 = maxOf(box.startX, boxCurX)
        val y1 = minOf(box.startY, boxCurY); val y2 = maxOf(box.startY, boxCurY)
        BrassPaint.rect(m, x1, y1, x2, y2, Colors.withAlpha(Colors.UI_ACCENT, 40))
        BrassPaint.border(m, x1, y1, x2, y2, Colors.UI_ACCENT)
    }

    private fun drawGrid(m: UMatrixStack, w: Float, h: Float) {
        val step = NodeLayout.GRID
        if (step * zoom < 4f) return
        val minor = Colors.withAlpha(Colors.EDGE, 34)
        val major = Colors.withAlpha(Colors.EDGE, 70)
        val wl = worldX(0f); val wr = worldX(w); val wt = worldY(0f); val wb = worldY(h)
        var k = floor(wl / step).toInt()
        while (k * step <= wr) { val gx = k * step; BrassPaint.rect(m, gx, wt, gx + 1f, wb, if (k % 4 == 0) major else minor); k++ }
        k = floor(wt / step).toInt()
        while (k * step <= wb) { val gy = k * step; BrassPaint.rect(m, wl, gy, wr, gy + 1f, if (k % 4 == 0) major else minor); k++ }
    }

    /**
     * The zoom readout and a one-line hint, pinned to the **top-left** of the canvas. Kept off the
     * bottom edge so it never lands under a host's own footer chrome (the demo browser's button bar),
     * and short enough that it cannot run past the canvas' right edge at any width.
     */
    private fun drawHud(m: UMatrixStack, l: Float, t: Float) {
        BrassFont.draw(m, this, "${(zoom * 100).roundToInt()}%", l + 6f, t + 6f, Colors.UI_TEXT_DARK)
        BrassFont.draw(m, this, "drag ports to wire · scroll to zoom · right-click to add",
            l + 6f, t + 6f + BrassFont.LINE + 2f, Colors.UI_TEXT_DARK)
    }

    // Demo + default registry

    companion object : BrassDemoSource {

        private const val MIN_ZOOM = 0.4f
        private const val MAX_ZOOM = 2.2f

        override fun demo() = BrassDemo("node-editor", "Node editor", 560f, 360f, card = false) { sample() }

        /** The default palette of node types (see [DefaultNodes]) - what a bare editor is built with. */
        fun defaultRegistry(): NodeRegistry = DefaultNodes.registry()

        /** The demo graph: a small signal chain touching every port, field and wire type. */
        fun sample(): BrassNodeEditor {
            val e = BrassNodeEditor()
            val time = e.spawn("time", 0f, 40f)!!
            val noise = e.spawn("noise", 190f, 0f)!!
            val grad = e.spawn("gradient", 190f, 150f)!!
            val xform = e.spawn("transform", 380f, 30f)!!
            val out = e.spawn("output", 560f, 90f)!!
            e.link(time, 0, noise, 0)
            e.link(noise, 0, grad, 0)
            e.link(grad, 0, out, 0)
            e.link(xform, 0, out, 1)
            e.graph.nodes.forEach { it.pop.snapTo(1f) }
            return e
        }
    }
}
