package net.swzo.brass.ui.kit.input

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.base.disposeWith
import net.swzo.brass.ui.kit.surface.BrassContextMenu
import net.swzo.brass.ui.kit.surface.BrassListRow
import net.swzo.brass.ui.kit.text.BrassTextInput
import org.lwjgl.glfw.GLFW

class BrassAutocomplete(
    private val input: BrassTextInput,
    private val layer: () -> UIComponent,
    private val max: Int = 6,
    private val suggest: (String) -> List<Suggestion>,
    private val onPick: (Suggestion) -> Unit = { input.setTextSilently(it.value) },
) {
    class Suggestion(val value: String, val label: String = value, val detail: String? = null, val visual: (() -> UIComponent)? = null)

    private var menu: BrassContextMenu? = null
    private var shown: List<Suggestion> = emptyList()
    private var rows: List<BrassListRow> = emptyList()
    private var highlight = -1

    init {
        input.onChange { text -> refresh(text) }
        input.onFocusLost { gg.essential.elementa.components.Window.enqueueRenderOperation { close() } }
        input.onInputKey = { _, key -> key(key) }
        input.disposeWith { close() }
    }

    val isOpen: Boolean get() = menu?.isOpen == true

    private fun key(code: Int): Boolean {
        if (!isOpen || shown.isEmpty()) return false
        when (code) {
            GLFW.GLFW_KEY_DOWN -> move(1)
            GLFW.GLFW_KEY_UP -> move(-1)
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> {
                if (highlight < 0) return code == GLFW.GLFW_KEY_TAB && pick(0)
                pick(highlight)
            }
            GLFW.GLFW_KEY_ESCAPE -> close()
            else -> return false
        }
        return true
    }

    private fun move(d: Int) {
        highlight = if (highlight < 0) (if (d > 0) 0 else shown.size - 1) else (highlight + d).mod(shown.size)
        rows.forEachIndexed { i, r -> r.selected = i == highlight }
    }

    private fun pick(i: Int): Boolean {
        val s = shown.getOrNull(i) ?: return false
        close()
        onPick(s)
        return true
    }

    fun close() {
        menu?.dismiss()
        menu = null
        shown = emptyList()
        rows = emptyList()
        highlight = -1
    }

    private fun refresh(text: String) {
        val next = if (text.isBlank()) emptyList() else suggest(text).take(max)
        if (next.isEmpty() || (next.size == 1 && next[0].value == text)) {
            close()
            return
        }
        if (isOpen && next.map { it.value } == shown.map { it.value }) return
        menu?.dismiss()
        shown = next
        highlight = -1
        val column = UIContainer().constrain { width = 100.percent() }
        rows = next.mapIndexed { i, s ->
            val row = BrassListRow(s.label, s.detail) { pick(i) }
            s.visual?.let { row.leading(it(), 16f) }
            row.entranceEnabled = false
            row.constrain { x = 0.pixels(); y = SiblingConstraint(1f); width = 100.percent() }
            row childOf column
            row
        }
        val h = rows.size * (if (next.any { it.detail != null }) ROW_TALL else ROW) + rows.size + PAD * 2
        column.constrain { x = 0.pixels(); y = PAD.pixels(); height = (h - PAD * 2).pixels() }
        val m = BrassContextMenu.custom(column, input.getWidth().toInt().coerceAtLeast(MIN_W), h.toInt())
        menu = m
        m.show(layer(), input.getLeft(), input.getBottom() + GAP, anchorTop = input.getTop() - GAP)
    }

    private companion object {
        const val ROW = 18f
        const val ROW_TALL = 26f
        const val PAD = 2f
        const val GAP = 2f
        const val MIN_W = 120
    }
}
