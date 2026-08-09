@file:Suppress("unused")
package net.swzo.brass.ui.kit.text

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.layout.BrassVirtualList
import net.swzo.brass.ui.kit.paint.BrassPaint
import java.awt.Color
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * Read-only syntax-highlighted code with a line-number gutter - a log, a config file, a stack trace,
 * a snippet in a docs panel.
 * ```kotlin
 * BrassCodeView(source, language = "kotlin").constrain { width = 100.percent(); height = 200.pixels() }
 * ```
 */
class BrassCodeView(
    code: String = "",
    private var language: String? = null,
    var gutter: Boolean = true,
) : BrassVirtualList<BrassCodeView.Line>(BrassFont.LINE + 2f) {

    class Line(val number: Int, val spans: List<BrassSyntax.Span>)

    private var scrollX = 0f

    private var widest = 0f

    var markers: Set<Int> = emptySet()

    private var copiedAt = 0L

    init {
        setCode(code, language)
        onMouseScroll { e ->
            if (gg.essential.universal.UKeyboard.isShiftKeyDown()) {
                scrollX = (scrollX - e.delta.toFloat() * BrassFont.LINE * 2f)
                    .coerceIn(0f, (widest - textWidth()).coerceAtLeast(0f))
                e.stopPropagation()
            }
        }
    }

    fun setCode(code: String, language: String? = this.language) {
        this.language = language
        val highlighted = BrassSyntax.highlight(language, code)
        setItems(highlighted.mapIndexed { index, spans -> Line(index + 1, spans) })
        widest = items.maxOfOrNull { line -> line.spans.sumOf { BrassFont.width(this, it.text).toDouble() }.toFloat() }
            ?: 0f
        scrollX = 0f
        scrollOffset = 0f
    }

    fun text(): String = items.joinToString("\n") { line -> line.spans.joinToString("") { it.text } }

    private fun chipRect(x: Float, y: Float, w: Float): FloatArray {
        val x2 = x + w - CHIP_MARGIN
        val y1 = y + CHIP_MARGIN
        return floatArrayOf(x2 - BrassCopyChip.SIZE, y1, x2, y1 + BrassCopyChip.SIZE)
    }

    override fun onViewportClick(localX: Float, localY: Float, button: Int): Boolean {
        if (button != 0) return false
        val r = chipRect(0f, 0f, getWidth())
        if (localX < r[0] || localX > r[2] || localY < r[1] || localY > r[3]) return false
        if (BrassCopyChip.copy(text())) copiedAt = System.currentTimeMillis()
        return true
    }

    fun revealLine(number: Int) {
        val index = number - 1
        if (index !in items.indices) return
        select(index)
        scrollTo(index)
    }

    private fun gutterWidth(): Float {
        if (!gutter) return 0f
        val digits = items.size.toString().length.coerceAtLeast(2)
        return GUTTER_PAD * 2 + BrassFont.width(this, "0".repeat(digits))
    }

    private fun textWidth(): Float = (getWidth() - gutterWidth() - PAD * 2).coerceAtLeast(1f)

    override fun rowBackground(index: Int): Color? = null

    override fun paintRow(m: UMatrixStack, item: Line, index: Int, x: Float, y: Float, w: Float) {
        val gw = gutterWidth()
        val ry = y + TOP_PAD // Offset rows down so line 1 doesn't collide with the top border

        // Paint highlight only across the code section
        val bg = when {
            index == selectedIndex -> SELECTED
            item.number in markers -> MARKED
            else -> null
        }
        if (bg != null) {
            BrassPaint.rectSnapped(m, x + gw, ry, x + w - BORDER, ry + rowHeight, bg)
        }

        var cx = x + gw + PAD - scrollX
        val right = x + w - BORDER
        for (span in item.spans) {
            val sw = BrassFont.width(this, span.text)
            if (cx + sw >= x + gw && cx <= right) {
                BrassFont.draw(m, this, span.text, cx, ry + 1f, span.color)
            }
            cx += sw
            if (cx > right) break
        }
    }

    override fun paintOverlay(m: UMatrixStack, x: Float, y: Float, w: Float, h: Float) {
        if (gutter) paintGutter(m, x, y, h)

        val r = chipRect(x, y, w)
        val (mx, my) = getMousePosition()
        val hovered = mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3]
        BrassCopyChip.draw(m, r[0], r[1], hovered, BrassCopyChip.flashing(copiedAt))
    }

    private fun paintGutter(m: UMatrixStack, x: Float, y: Float, h: Float) {
        val gw = gutterWidth()
        val top = y + BORDER
        val bottom = y + h - BORDER
        if (bottom <= top) return

        // No fill of its own: GUTTER_BG was the exact colour of the card's own interior anyway, and
        // painting it over the gutter's width covered the top and bottom rows of the card's inner
        // border right where the gutter sits - those two rules just stopped partway across the card.
        // Leaving the fill out lets the card's own border continue underneath uninterrupted.

        // Hairline vertical divider between gutter and code area.
        BrassPaint.rectSnapped(m, x + gw - BORDER, top, x + gw, bottom, GUTTER_EDGE)

        // Line numbers, aligned with the TOP_PAD offset the rows use.
        val first = kotlin.math.floor(scrollOffset / rowHeight).toInt().coerceAtLeast(0)
        val last = kotlin.math.ceil((scrollOffset + h) / rowHeight).toInt().coerceAtMost(items.size)
        for (i in first until last) {
            val line = items[i]
            val ry = y + TOP_PAD + i * rowHeight - scrollOffset
            if (ry + rowHeight < top || ry > bottom) continue
            val label = line.number.toString()
            BrassFont.draw(
                m, this, label,
                x + gw - GUTTER_PAD - BrassFont.width(this, label),
                ry + 1f,
                if (line.number in markers) Colors.DANGER else Colors.UI_TEXT_DARK,
            )
        }
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("code-view", "Code view", 270f, 110f) {
            val view = BrassCodeView(SAMPLE, language = "kotlin")
            view.markers = setOf(2)
            view
        }

        private val SAMPLE = """
            fun greet(name: String): String {
                val greeting = "Hello, ${'$'}name"
                return greeting.trim()
            }

            fun main() {
                println(greet("world"))
            }
        """.trimIndent()

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val PAD = 4f
        private const val GUTTER_PAD = 4f
        private const val TOP_PAD = 4f
        private const val BORDER = 1f
        private const val CHIP_MARGIN = 3f

        private val GUTTER_EDGE: Color get() = Colors.UI_INNER_BORDER
        private val SELECTED: Color
            get() = Color(Colors.UI_ACCENT.red, Colors.UI_ACCENT.green, Colors.UI_ACCENT.blue, 40)
        private val MARKED: Color
            get() = Color(Colors.DANGER.red, Colors.DANGER.green, Colors.DANGER.blue, 34)
    }
}
