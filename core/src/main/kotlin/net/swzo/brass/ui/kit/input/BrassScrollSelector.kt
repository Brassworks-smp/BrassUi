package net.swzo.brass.ui.kit.input

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.text.BrassFont
import kotlin.math.abs
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A horizontal option carousel. The selected option sits centred and
 * bright; neighbours fade at the edges. Click the right half to advance, the left half to go back; the
 * strip eases (and wraps) to the new index. Chevrons hint the two hit zones.
 */
class BrassScrollSelector(
    private val options: List<String>,
    initialIndex: Int = 0,
    private val onChange: (Int) -> Unit = {},
) : BrassWidget(BrassAccent.DEFAULT), BrassValue<Int>, BrassFocusable {

    private val holder = BrassValueHolder(initialIndex)

    override var value: Int
        get() = holder.value
        set(v) { if (v in options.indices) holder.value = v }

    override fun setSilently(value: Int) { if (value in options.indices) holder.setSilently(value) }
    override fun onChange(listener: (Int) -> Unit) = holder.onChange(listener)
    override fun bind(state: BrassState<Int>) = holder.bind(this, state)

    /** Index of the centred option. Alias of [value]. */
    val selectedIndex: Int get() = holder.value

    /** Eased carousel position; wrapped across the seam each frame - see [drawContent]. */
    private val strip = BrassEased(initialIndex.toFloat(), speed = STRIP_SPEED)

    companion object : BrassDemoSource {

        /**
         * Options cycled with the wheel, which is the gesture the widget is named for.
         *
         * Worth recording with the wheel rather than with clicks: this control exists precisely so a
         * value can be changed without opening anything, and a recording of it being clicked
         * documents a dropdown.
         */
        override fun demo() = BrassDemo("scroll-selector", "Scroll selector", 150f, 20f) {
            BrassScrollSelector(listOf("Low", "Medium", "High", "Ultra"))
        }

        // ---- widget internals ------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        /** How fast the strip travels to the newly selected option. */
        private const val STRIP_SPEED = 10f
    }

    init {
        onMouseClick { e ->
            if (!active || e.mouseButton != 0 || options.isEmpty()) return@onMouseClick
            if (e.relativeX >= bw / 2f) step(1) else step(-1)
        }
        holder.onChange(onChange)
    }

    /** Left/right step through the options, matching the two click zones. */
    override fun onKeyPressed(keyCode: Int): Boolean = when (keyCode) {
        org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> { step(-1); true }
        org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> { step(1); true }
        else -> false
    }

    private fun step(dir: Int) {
        value = ((selectedIndex + dir) % options.size + options.size) % options.size
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        if (options.isEmpty()) return
        val n = options.size
        strip.target = selectedIndex.toFloat()
        var scrollIndex = strip.advance()
        // keep the eased position near the selection across the wrap seam
        var wrapped = scrollIndex
        while (wrapped - selectedIndex > n / 2f) wrapped -= n
        while (wrapped - selectedIndex < -n / 2f) wrapped += n
        if (wrapped != scrollIndex) { strip.snapTo(wrapped); strip.target = selectedIndex.toFloat() }
        scrollIndex = wrapped

        val maxTextW = options.maxOf { BrassFont.width(this, it) }
        val spacing = maxTextW + 16f
        val center = x + w / 2f
        val ty = y + (h - BrassFont.LINE) / 2 + 1
        // Text lives between the two chevrons; options are faded out as they approach that boundary and
        // dropped once past it, so a neighbour can never spill outside the widget's own box.
        val inset = 11f
        val left = x + inset
        val right = x + w - inset
        for (i in 0 until n) {
            var ring = i - scrollIndex
            if (ring < -n / 2f) ring += n
            if (ring > n / 2f) ring -= n
            val ox = center + ring * spacing
            val s = options[i]
            val tw = BrassFont.width(this, s)
            val tx = ox - tw / 2f
            // how far this option pokes past either edge, as a fraction of its own width
            val over = maxOf(left - tx, (tx + tw) - right, 0f)
            if (over >= tw) continue
            val edge = 1f - (over / tw)
            val fade = (1f - abs(ring) * 0.6f).coerceIn(0.12f, 1f) * edge
            if (fade <= 0.02f) continue
            val isCenter = abs(ring) < 0.5f
            val col = if (isCenter) withAlpha(Colors.UI_ACCENT_BRIGHT, edge)
                      else withAlpha(Colors.UI_TEXT_DARK, fade)
            BrassFont.draw(m, this, s, tx, ty.toFloat(), col, true)
        }
        // chevrons
        val cy = y + (h - 6) / 2f
        BrassIcons.draw(m, BrassIcons.CHEVRON_LEFT, (x + 3).toFloat(), cy, 6f, Colors.UI_TEXT_DARK)
        BrassIcons.draw(m, BrassIcons.CHEVRON_RIGHT, (x + w - 7).toFloat(), cy, 6f, Colors.UI_TEXT_DARK)
    }
}
