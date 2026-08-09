@file:Suppress("unused")
package net.swzo.brass.ui.kit.text

import gg.essential.elementa.UIComponent
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassStats
import net.swzo.brass.ui.kit.layout.BrassCull
import net.swzo.brass.ui.kit.paint.BrassPaint
import java.awt.Color
import kotlin.math.roundToInt
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * Renders a practical subset of Markdown in the toolkit's palette - for changelogs, help panels, MOTDs
 * and anything else where the text is authored rather than hardcoded into a layout.
 * ### Supported
 * | Syntax | Result |
 * |---|---|
 * | `# ` `## ` `### ` | headings, scaled down by level |
 * | `- ` `* ` `+ ` | bullet list |
 * | `1. ` | ordered list, numbered as written |
 * | `> ` | block quote with a brass bar |
 * | ` ``` ` fences | code block on a recessed panel |
 * | `---` | horizontal rule (a [BrassDivider] in all but name) |
 * | `**b**` `*i*` `` `code` `` | inline emphasis |
 * | `[label](url)` | link - styled, and clickable when [onLink] is set |
 * Blank lines separate paragraphs; consecutive lines within a paragraph are joined and re-wrapped.
 * Everything wraps to the component's current width and re-lays out when that width changes, so this
 * behaves like the rest of the reactive layout pieces ([BrassFlow], [BrassLayout]).
 * ### Height
 * Like [BrassFlow], this measures itself rather than guessing: constrain it with
 * `height = basicHeightConstraint { md.contentHeight() }` and it will grow as the panel narrows and
 * text wraps to more lines. Sizing it any other way will clip the tail of the document.
 * ### Font assumption
 * Bold and italic are emitted as vanilla `§` formatting codes, which the default (vanilla) font
 * provider renders and measures correctly. Under a custom Elementa font provider those codes would
 * show up literally - if the toolkit ever switches fonts, emphasis needs re-implementing here (bold
 * can be faked by drawing twice a pixel apart; italic cannot, without a skew).
 */
class BrassMarkdown(
    markdown: String = "",
    private val onLink: ((String) -> Unit)? = null,
) : UIComponent() {

    var markdown: String = markdown
        set(value) {
            if (field == value) return
            field = value
            blocks = parse(value)
            chipFlash.clear()
            markDirty()
        }

    private var blocks: List<Block> = parse(markdown)

    /** Laid-out output, rebuilt whenever the width or the source changes. */
    private var lines: List<Line> = emptyList()
    private var links: List<LinkRect> = emptyList()
    private var codeChips: List<CodeChip> = emptyList()
    private var measuredHeight = 0f
    private var lastWidth = Float.NaN

    /**
     * When each code block's chip last fired, keyed by the block's index in [blocks] - stable across
     * a width-triggered re-layout, so a flash mid-animation survives the window being resized. Cleared
     * whenever the source text changes, since indices no longer mean the same blocks at that point.
     */
    private val chipFlash = HashMap<Int, Long>()

    init {
        onMouseClick { e ->
            val lx = e.relativeX
            val ly = e.relativeY

            val chip = codeChips.firstOrNull {
                lx >= it.x1 && lx <= it.x2 && ly >= it.y1 && ly <= it.y2
            }
            if (chip != null && e.mouseButton == 0) {
                if (BrassCopyChip.copy(chip.text)) chipFlash[chip.blockIndex] = System.currentTimeMillis()
                return@onMouseClick
            }

            if (onLink == null || e.mouseButton != 0) return@onMouseClick
            links.firstOrNull { lx >= it.x1 && lx <= it.x2 && ly >= it.y1 && ly <= it.y2 }
                ?.let { onLink.invoke(it.url) }
        }
    }

    private fun markDirty() { lastWidth = Float.NaN }

    fun contentHeight(): Float {
        relayoutIfNeeded(getWidth())
        return measuredHeight
    }


    private enum class Kind { PARAGRAPH, HEADING, BULLET, ORDERED, QUOTE, CODE, RULE }

    private class Block(
        val kind: Kind,
        val spans: List<Span> = emptyList(),
        val level: Int = 0,
        val marker: String = "",
        val codeLines: List<String> = emptyList(),
        val codeSpans: List<List<BrassSyntax.Span>> = emptyList(),
    )

    private class Span(val text: String, val bold: Boolean, val italic: Boolean, val code: Boolean, val url: String?)

    private fun parse(src: String): List<Block> {
        val out = ArrayList<Block>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                out += Block(Kind.PARAGRAPH, inline(paragraph.toString().trim()))
                paragraph.setLength(0)
            }
        }

        val src2 = src.replace("\r\n", "\n").replace('\r', '\n')
        val raw = src2.split('\n')
        var i = 0
        while (i < raw.size) {
            val line = raw[i]
            val t = line.trim()

            // fenced code runs to the closing fence, or to the end of the document if unclosed
            if (t.startsWith("```")) {
                flushParagraph()
                // the info string after the fence names the language: ```kotlin
                val language = t.removePrefix("```").trim().substringBefore(' ').ifEmpty { null }
                val body = ArrayList<String>()
                i++
                while (i < raw.size && !raw[i].trim().startsWith("```")) { body += raw[i]; i++ }
                i++ // consume the closing fence
                val highlighted = BrassSyntax.highlight(language, body.joinToString("\n"))
                out += Block(Kind.CODE, codeLines = body, codeSpans = highlighted)
                continue
            }

            when {
                t.isEmpty() -> flushParagraph()

                t.length >= 3 && (t.all { it == '-' } || t.all { it == '*' } || t.all { it == '_' }) -> {
                    flushParagraph()
                    out += Block(Kind.RULE)
                }

                t.startsWith("#") -> {
                    flushParagraph()
                    val level = t.takeWhile { it == '#' }.length.coerceAtMost(3)
                    out += Block(Kind.HEADING, inline(t.drop(level).trim()), level = level)
                }

                t.startsWith("> ") -> {
                    flushParagraph()
                    out += Block(Kind.QUOTE, inline(t.removePrefix("> ").trim()))
                }

                t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") -> {
                    flushParagraph()
                    // no marker string: the dot is drawn as a block, because "•" is not in MC's
                    // default font sheet and would render as a missing-glyph box (same trap as "…")
                    out += Block(Kind.BULLET, inline(t.drop(2).trim()))
                }

                ORDERED.matches(t) -> {
                    flushParagraph()
                    val num = t.takeWhile { it.isDigit() }
                    out += Block(Kind.ORDERED, inline(t.dropWhile { it.isDigit() }.drop(2).trim()), marker = "$num.")
                }

                // a plain line continues the current paragraph and is re-wrapped with it
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(t)
                }
            }
            i++
        }
        flushParagraph()
        return out
    }

    private fun inline(s: String): List<Span> {
        val out = ArrayList<Span>()
        val buf = StringBuilder()
        var bold = false
        var italic = false
        var i = 0

        fun flush() {
            if (buf.isNotEmpty()) {
                out += Span(buf.toString(), bold, italic, code = false, url = null)
                buf.setLength(0)
            }
        }

        while (i < s.length) {
            val rest = s.substring(i)
            when {
                rest.startsWith("`") -> {
                    val end = s.indexOf('`', i + 1)
                    if (end < 0) { buf.append(s[i]); i++ } else {
                        flush()
                        out += Span(s.substring(i + 1, end), bold = false, italic = false, code = true, url = null)
                        i = end + 1
                    }
                }
                rest.startsWith("[") -> {
                    val close = s.indexOf(']', i)
                    val open = if (close >= 0) s.getOrNull(close + 1) else null
                    val end = if (open == '(') s.indexOf(')', close) else -1
                    if (end < 0) { buf.append(s[i]); i++ } else {
                        flush()
                        out += Span(
                            s.substring(i + 1, close), bold, italic,
                            code = false, url = s.substring(close + 2, end),
                        )
                        i = end + 1
                    }
                }
                rest.startsWith("**") || rest.startsWith("__") -> { flush(); bold = !bold; i += 2 }
                rest.startsWith("*") || rest.startsWith("_") -> { flush(); italic = !italic; i += 1 }
                else -> { buf.append(s[i]); i++ }
            }
        }
        flush()
        return out
    }


    private class Placed(
        val span: Span,
        val x: Float,
        val width: Float,
        val scale: Float,
        val color: Color,
        val rendered: String,
    )

    private class Line(
        val y: Float,
        val height: Float,
        val parts: List<Placed>,
        val kind: Kind,
        val indent: Float,
        val marker: String = "",
        val width: Float = 0f,
        val bullet: Boolean = false,
    )

    private class LinkRect(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val url: String)

    private class CodeChip(
        val blockIndex: Int,
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val text: String,
    )

    private fun relayoutIfNeeded(width: Float) {
        if (width == lastWidth) return   // NaN != NaN, so markDirty() always forces a pass
        if (width <= 0f) return
        lastWidth = width
        layout(width)
    }

    private fun layout(width: Float) {
        val out = ArrayList<Line>()
        val hits = ArrayList<LinkRect>()
        val chips = ArrayList<CodeChip>()
        var y = 0f

        for ((index, block) in blocks.withIndex()) {
            if (index > 0) y += gapBefore(block)

            when (block.kind) {
                Kind.RULE -> {
                    out += Line(y, RULE_H, emptyList(), Kind.RULE, 0f, width = width)
                    y += RULE_H
                }

                Kind.CODE -> {
                    // code is not wrapped: wrapping code changes what it means, so long lines are
                    // clipped to the panel instead
                    val pad = CODE_PAD
                    // A bare slab above the first line of code. The panel is tiled one slab per line,
                    // so without this the first line sits flush against the top edge while every other
                    // side has padding - the block looked cropped rather than inset.
                    out += Line(y, CODE_TOP_PAD, emptyList(), Kind.CODE, 0f, width = width)
                    // The chip sits in that same top-right corner, one per block - matching where
                    // BrassCodeView puts its own, so a snippet copies the same way whichever widget
                    // it is read from.
                    chips += CodeChip(
                        index,
                        width - CHIP_MARGIN - BrassCopyChip.SIZE, y + CHIP_MARGIN,
                        width - CHIP_MARGIN, y + CHIP_MARGIN + BrassCopyChip.SIZE,
                        block.codeLines.joinToString("\n"),
                    )
                    y += CODE_TOP_PAD
                    for ((n, raw) in block.codeLines.withIndex()) {
                        // one Placed per highlighted run, laid left to right and cut off at the panel
                        // edge; an unhighlighted block yields a single default-coloured run
                        val runs = block.codeSpans.getOrNull(n)
                            ?: listOf(BrassSyntax.Span(raw, Colors.UI_TEXT))
                        val placed = ArrayList<Placed>(runs.size)
                        var x = pad
                        val limit = width - pad
                        for (run in runs) {
                            if (x >= limit) break
                            val text = BrassFont.fit(this, run.text, limit - x)
                            if (text.isEmpty()) break
                            val w = BrassFont.width(this, text)
                            placed += Placed(
                                Span(text, bold = false, italic = false, code = true, url = null),
                                x, w, 1f, run.color, text,
                            )
                            x += w
                        }
                        out += Line(y, CODE_LINE, placed, Kind.CODE, 0f, width = width)
                        y += CODE_LINE
                    }
                }

                else -> {
                    val scale = scaleFor(block)
                    val color = colorFor(block)
                    val indent = indentFor(block)
                    val avail = (width - indent).coerceAtLeast(1f)
                    val lineH = BrassFont.LINE * scale + LINE_GAP

                    val wrapped = wrap(block.spans, avail, scale)
                    for ((n, parts) in wrapped.withIndex()) {
                        val placed = ArrayList<Placed>(parts.size)
                        var x = indent
                        for (span in parts) {
                            val rendered = render(span)
                            val w = BrassFont.width(this, rendered, scale)
                            placed += Placed(span, x, w, scale, if (span.url != null) LINK else color, rendered)
                            if (span.url != null) {
                                hits += LinkRect(x, y, x + w, y + BrassFont.LINE * scale, span.url)
                            }
                            x += w
                        }
                        out += Line(
                            y, lineH, placed, block.kind, indent,
                            // the marker only belongs on the first line of a list item
                            marker = if (n == 0) block.marker else "",
                            width = width,
                            bullet = block.kind == Kind.BULLET && n == 0,
                        )
                        y += lineH
                    }
                }
            }
        }

        lines = out
        links = hits
        codeChips = chips
        measuredHeight = y
    }

    private fun wrap(spans: List<Span>, avail: Float, scale: Float): List<List<Span>> {
        val out = ArrayList<List<Span>>()
        var current = ArrayList<Span>()
        var x = 0f

        for (span in spans) {
            // keep the trailing space with each word so widths stay honest across span boundaries
            val words = span.text.split(' ')
            for ((i, word) in words.withIndex()) {
                if (word.isEmpty() && i < words.size - 1) continue
                val piece = if (i < words.size - 1) "$word " else word
                if (piece.isEmpty()) continue
                val styled = Span(piece, span.bold, span.italic, span.code, span.url)
                val w = BrassFont.width(this, render(styled), scale)

                if (x + w > avail && current.isNotEmpty()) {
                    out += current
                    current = ArrayList()
                    x = 0f
                    // a wrapped line should not start with the space carried from the previous word
                    if (piece.startsWith(" ")) continue
                }
                current.add(styled)
                x += w
            }
        }
        if (current.isNotEmpty()) out += current
        return if (out.isEmpty()) listOf(emptyList()) else out
    }

    private fun render(span: Span): String {
        if (!span.bold && !span.italic) return span.text
        val sb = StringBuilder()
        if (span.bold) sb.append("§l")
        if (span.italic) sb.append("§o")
        sb.append(span.text)
        return sb.toString()
    }

    private fun scaleFor(b: Block): Float = when {
        b.kind != Kind.HEADING -> 1f
        b.level <= 1 -> 1.6f
        b.level == 2 -> 1.3f
        else -> 1.15f
    }

    private fun colorFor(b: Block): Color = when (b.kind) {
        Kind.HEADING -> Colors.UI_TEXT_HOVER
        Kind.QUOTE -> Colors.UI_TEXT_DARK
        else -> Colors.UI_TEXT
    }

    private fun indentFor(b: Block): Float = when (b.kind) {
        Kind.BULLET, Kind.ORDERED -> LIST_INDENT
        Kind.QUOTE -> QUOTE_INDENT
        else -> 0f
    }

    private fun gapBefore(b: Block): Float = when (b.kind) {
        Kind.HEADING -> 8f
        Kind.RULE -> 6f
        Kind.CODE -> 6f
        Kind.BULLET, Kind.ORDERED, Kind.QUOTE -> 2f
        else -> 6f
    }


    override fun draw(matrixStack: UMatrixStack) {
        beforeDraw(matrixStack)
        relayoutIfNeeded(getWidth())

        val ox = getLeft()
        val oy = getTop()

        // One clip fetch for the whole document, then a cheap rect test per line. A long changelog
        // in a small panel is mostly off-screen, and every hidden line was previously costing a
        // background quad plus a glyph run per span.
        val clip = BrassCull.clipOf(this)

        for (line in lines) {
            val ly = oy + line.y
            if (!BrassCull.rectVisible(clip, ox, ly, ox + line.width, ly + line.height)) {
                BrassStats.culled()
                continue
            }
            BrassStats.painted()

            when (line.kind) {
                Kind.RULE -> {
                    val mid = (ly + RULE_H / 2f).roundToInt().toFloat()
                    fill(matrixStack, ox, mid, ox + line.width, mid + 1f, RULE_SHADE)
                    fill(matrixStack, ox, mid + 1f, ox + line.width, mid + 2f, RULE_HIGHLIGHT)
                }

                Kind.CODE -> {
                    // one panel slab per line: adjacent lines tile into a continuous block. Sized from
                    // the line's own height so the padding slab above the first line tiles in too.
                    fill(matrixStack, ox, ly, ox + line.width, ly + line.height, CODE_BG)
                }

                Kind.QUOTE -> {
                    fill(matrixStack, ox, ly, ox + 2f, ly + line.height, Colors.UI_ACCENT)
                }

                else -> {}
            }

            if (line.bullet) {
                val dy = ly + BrassFont.LINE / 2f - 1f
                fill(matrixStack, ox + MARKER_X + 1f, dy, ox + MARKER_X + 3f, dy + 2f, Colors.UI_ACCENT)
            }

            if (line.marker.isNotEmpty()) {
                BrassFont.draw(
                    matrixStack, this, line.marker,
                    ox + MARKER_X, ly, Colors.UI_TEXT_DARK, true, 1f,
                )
            }

            for (p in line.parts) {
                val px = ox + p.x
                if (p.span.code && line.kind != Kind.CODE) {
                    // inline code gets its own chip behind the glyphs
                    fill(matrixStack, px - 1f, ly - 1f, px + p.width + 1f, ly + BrassFont.LINE, CODE_BG)
                }
                val color = if (p.span.code && line.kind != Kind.CODE) Colors.UI_ACCENT_BRIGHT else p.color
                BrassFont.draw(matrixStack, this, p.rendered, px, ly, color, true, p.scale)

                if (p.span.url != null) {
                    val uy = ly + BrassFont.LINE * p.scale - 1f
                    fill(matrixStack, px, uy, px + p.width, uy + 1f, LINK)
                }
            }
        }

        // A pass of its own rather than inside the line loop above: a chip is one per *block*, not
        // per line, and the line it happens to be anchored to may itself be culled while the chip
        // (drawn a little proud of it) is not.
        val (mx, my) = getMousePosition()
        for (chip in codeChips) {
            val cy = oy + chip.y1
            if (!BrassCull.rectVisible(clip, ox + chip.x1, cy, ox + chip.x2, oy + chip.y2)) continue
            val hovered = mx >= ox + chip.x1 && mx <= ox + chip.x2 && my >= cy && my <= oy + chip.y2
            BrassCopyChip.draw(
                matrixStack, ox + chip.x1, cy, hovered,
                BrassCopyChip.flashing(chipFlash[chip.blockIndex] ?: 0L),
            )
        }

        super.draw(matrixStack)
    }

    // Routed through BrassPaint, which counts the quad. The hand-rolled version here did not, so a
    // screenful of markdown contributed nothing to the quad total the dev overlay reports.
    private fun fill(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) =
        BrassPaint.rect(m, x1, y1, x2, y2, c)

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("markdown", "Markdown", 270f, 130f) {
            BrassMarkdown(SAMPLE)
        }

        private val SAMPLE = """
            # Heading

            A paragraph with **bold** and *italic* text, plus `inline code`
            and a [link](https://example.com) to hover.

            - first item
            - second item
        """.trimIndent()

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private val ORDERED = Regex("^\\d+\\. .*")

        private const val LINE_GAP = 2f
        private const val LIST_INDENT = 10f
        private const val QUOTE_INDENT = 8f
        private const val MARKER_X = 1f
        private const val RULE_H = 5f
        private const val CODE_LINE = 11f
        private const val CODE_PAD = 4f
        private const val CODE_TOP_PAD = 2f
        private const val CHIP_MARGIN = 3f

        private val CODE_BG: Color get() = Colors.CODE_BG
        private val RULE_SHADE: Color get() = Colors.DIVIDER_SHADE
        private val RULE_HIGHLIGHT: Color get() = Colors.DIVIDER_HIGHLIGHT
        private val LINK: Color get() = Colors.UI_ACCENT
    }
}
