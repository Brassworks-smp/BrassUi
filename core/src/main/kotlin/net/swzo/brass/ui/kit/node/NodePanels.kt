package net.swzo.brass.ui.kit.node

import gg.essential.universal.UMatrixStack
import gg.essential.elementa.effects.ScissorEffect
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassFont
import kotlin.math.min

/**
 * Optional host-owned minimap. The editor never installs it itself: applications that benefit from a
 * navigator can place it in their own panel, while compact editors keep the canvas completely clean.
 */
class BrassNodeMiniMap(private val editor: BrassNodeEditor) : BrassWidget(BrassAccent.DEFAULT) {
    init {
        chrome = BrassChrome.NONE
        clickable = true
        entranceEnabled = false
        enableEffect(ScissorEffect())
        onMouseClick { event ->
            val snapshot = editor.navigatorSnapshot()
            val projection = projection(snapshot, getLeft(), getTop(), getWidth(), getHeight())
            if (projection.scale <= 0f) return@onMouseClick
            val wx = snapshot.minX + (event.relativeX - projection.offsetX) / projection.scale
            val wy = snapshot.minY + (event.relativeY - projection.offsetY) / projection.scale
            editor.centerAt(wx, wy)
        }
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val left = getLeft()
        val top = getTop()
        val right = getRight()
        val bottom = getBottom()
        BrassCard.panel(m, left, top, right, bottom, fill = Colors.INK_950)
        val snapshot = editor.navigatorSnapshot()
        val p = projection(snapshot, left, top, getWidth(), getHeight())
        snapshot.nodes.forEach { node ->
            val x1 = p.offsetX + (node.x - snapshot.minX) * p.scale
            val y1 = p.offsetY + (node.y - snapshot.minY) * p.scale
            val x2 = x1 + (node.width * p.scale).coerceAtLeast(2f)
            val y2 = y1 + (node.height * p.scale).coerceAtLeast(2f)
            BrassPaint.rect(
                m, left + x1, top + y1, left + x2, top + y2,
                if (node.selected) Colors.UI_ACCENT_BRIGHT else Colors.UI_ELEMENT_BORDER,
            )
        }
        val viewport = editor.viewportWorldBounds()
        val vx1 = left + p.offsetX + (viewport[0] - snapshot.minX) * p.scale
        val vy1 = top + p.offsetY + (viewport[1] - snapshot.minY) * p.scale
        val vx2 = left + p.offsetX + (viewport[2] - snapshot.minX) * p.scale
        val vy2 = top + p.offsetY + (viewport[3] - snapshot.minY) * p.scale
        BrassPaint.border(m, vx1, vy1, vx2, vy2, Colors.withAlpha(Colors.UI_ACCENT_BRIGHT, 170))
    }

    private data class Projection(val scale: Float, val offsetX: Float, val offsetY: Float)

    private fun projection(
        snapshot: NodeNavigatorSnapshot,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
    ): Projection {
        val availableW = (width - PAD * 2f).coerceAtLeast(1f)
        val availableH = (height - PAD * 2f).coerceAtLeast(1f)
        val graphW = (snapshot.maxX - snapshot.minX).coerceAtLeast(1f)
        val graphH = (snapshot.maxY - snapshot.minY).coerceAtLeast(1f)
        val scale = min(availableW / graphW, availableH / graphH)
        return Projection(
            scale,
            PAD + (availableW - graphW * scale) / 2f,
            PAD + (availableH - graphH * scale) / 2f,
        )
    }

    private companion object {
        const val PAD = 5f
    }
}

/**
 * Optional read-only inspector surface for the currently selected node. Mutation stays in the editor
 * command API so this panel cannot accidentally bypass undo, read-only mode or collaboration events.
 */
class BrassNodeInspector(private val editor: BrassNodeEditor) : BrassWidget(BrassAccent.DEFAULT) {
    init {
        chrome = BrassChrome.NONE
        entranceEnabled = false
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val left = getLeft()
        val top = getTop()
        BrassCard.panel(m, left, top, getRight(), getBottom(), fill = Colors.UI_INNER_BG)
        val snapshot = editor.inspectSelection()
        if (snapshot == null) {
            BrassFont.draw(m, this, "No node selected", left + PAD, top + PAD, Colors.UI_TEXT_DARK)
            return
        }
        var lineY = top + PAD
        fun line(label: String, color: java.awt.Color = Colors.UI_TEXT) {
            if (lineY + BrassFont.LINE > getBottom() - PAD) return
            BrassFont.draw(m, this, BrassFont.fit(this, label, getWidth() - PAD * 2f), left + PAD, lineY, color)
            lineY += BrassFont.LINE + GAP
        }
        line(snapshot.title, Colors.UI_ACCENT_BRIGHT)
        line("${snapshot.typeId} · ${snapshot.incoming} in · ${snapshot.outgoing} out", Colors.UI_TEXT_DARK)
        snapshot.runState?.let { line("State: ${it.name.lowercase()}", stateColor(it)) }
        snapshot.fields.forEach { (key, value) -> line("$key: $value") }
        snapshot.diagnostics.forEach { diagnostic ->
            line(diagnostic.message, if (diagnostic.severity == NodeDiagnosticSeverity.ERROR) Colors.DANGER else Colors.WARN)
        }
    }

    private fun stateColor(state: NodeRunState) = when (state) {
        NodeRunState.FAILED -> Colors.DANGER
        NodeRunState.RUNNING -> Colors.UI_ACCENT_BRIGHT
        NodeRunState.COMPLETED -> Colors.PATINA_400
        else -> Colors.UI_TEXT_DARK
    }

    private companion object {
        const val PAD = 7f
        const val GAP = 3f
    }
}
