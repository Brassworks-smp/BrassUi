package net.swzo.brass.ui.kit.text

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.layout.BrassScrollbar
import net.swzo.brass.ui.kit.layout.BrassVirtualList
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.platform.BrassCursor
import java.awt.Color

/**
 * A read-only **markdown** text view, built on [BrassVirtualList] so long documents scroll like any
 * other list. Parsing lives in the pure, testable [Markdown] object; this widget adds the wrapping
 * (re-laid out on resize), the paint, link hit-testing (a `[label](url)` fires [onLink]) and the
 * ability to syntax-highlight fenced code blocks. Long lines **wrap** to the widget width, and
 * [heightFor] reports the natural height so a host (a popup) can size itself to the content.
 */
class BrassMarkdown(
    initial: String = "",
    private val onLink: (String) -> Unit = {},
) : BrassVirtualList<Markdown.Row>(BrassFont.LINE + 2f) {

    private var md = initial
    private var blocks: List<Markdown.Block> = Markdown.parse(initial)
    private var wrappedFor = -1f

    /** Set the markdown text (re-parsed and re-wrapped). */
    fun setText(text: String) {
        md = text
        blocks = Markdown.parse(text)
        wrappedFor = -1f
        setItems(emptyList())
    }

    fun setCode(text: String, language: String?) = setText(text)

    /** The natural height this document needs at [width], so a host can size a popup to it. */
    fun heightFor(width: Float): Float {
        ensureWrapped(width)
        return (items.size * rowHeight).coerceAtLeast(rowHeight)
    }

    /** The total content height at the current wrap width (the scrollable document). */
    fun contentHeight(): Float = items.size * rowHeight

    private fun ensureWrapped(width: Float) {
        // Text is drawn x+6 from the card edge, so the wrap width gives it that margin on both sides.
        val avail = (width - BrassScrollbar.WIDTH - 18f).coerceAtLeast(60f)
        if (wrappedFor != avail) {
            wrappedFor = avail
            val rows = Markdown.wrap(blocks, avail, { BrassFont.width(this, it) }) { lang, line ->
                if (lang == null) listOf(Markdown.Run(line, Markdown.Style.CODE))
                else BrassSyntax.highlight(lang, line).firstOrNull()?.map { Markdown.Run(it.text, Markdown.Style.CODE, color = it.color) }
                    ?: listOf(Markdown.Run(line, Markdown.Style.CODE))
            }
            setItems(rows)
            // Code lines are kept verbatim, so the widest row decides the horizontal scroll range.
            var widest = 0f
            for (row in rows) {
                var rowW = 6f
                for (run in row.runs) rowW += BrassFont.width(this, run.text)
                if (rowW > widest) widest = rowW
            }
            hContent = widest
        }
    }

    override fun drawContent(m: UMatrixStack, bx: Int, by: Int, bw: Int, bh: Int) {
        ensureWrapped(bw.toFloat())
        // Hovering a link shows the hand cursor, like a hyperlink.
        if (hoveredRow >= 0 && items.getOrNull(hoveredRow)?.runs?.any { it.url != null } == true) {
            BrassCursor.request(BrassCursor.Kind.HAND)
        }
        super.drawContent(m, bx, by, bw, bh)
    }

    override fun paintRow(m: UMatrixStack, item: Markdown.Row, index: Int, x: Float, y: Float, w: Float) {
        if (item.rule) {
            val midY = y + rowHeight / 2f
            BrassPaint.rect(m, x + 6f, midY, x + w - 6f, midY + 1f, Colors.UI_ELEMENT_BORDER)
            return
        }
        // Text is inset past the card's 1px border + ring on every side - never drawn onto the chrome.
        var cx = x + 6f
        for (run in item.runs) {
            BrassFont.draw(m, this, run.text, cx, y + 2f, colorFor(run))
            cx += BrassFont.width(this, run.text)
        }
    }

    override fun onRowClick(index: Int, localX: Float, button: Int): Boolean {
        if (button != 0) return false
        val row = items.getOrNull(index) ?: return false
        var cx = 6f - hScroll
        for (run in row.runs) {
            val rw = BrassFont.width(this, run.text)
            if (run.url != null && localX >= cx && localX <= cx + rw) {
                onLink(run.url)
                return true
            }
            cx += rw
        }
        return false
    }

    private fun colorFor(run: Markdown.Run): Color = when (run.style) {
        Markdown.Style.NORMAL -> Colors.UI_TEXT
        Markdown.Style.BOLD -> Colors.UI_TEXT_HOVER
        Markdown.Style.ITALIC -> Colors.UI_TEXT_DARK
        Markdown.Style.CODE -> run.color ?: CODE_COLOR
        Markdown.Style.HEAD -> Colors.BRASS_400
        Markdown.Style.QUOTE -> Colors.UI_TEXT_DARK
        Markdown.Style.LINK -> Colors.UI_ACCENT_BRIGHT
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("markdown", "Markdown", 300f, 190f) {
            BrassMarkdown(SAMPLE) { }
        }

        private val SAMPLE = """
            # Heading
            Some **bold** and `code` prose that wraps nicely at the edge of the view, and a [link](brassui).

            - a bullet
            - another bullet

            ```lua
            local x = input("x", 0)
            output("y", x * 2)
            ```
        """.trimIndent()

        private val CODE_COLOR: Color = Color(0xE5C07B)
    }
}
