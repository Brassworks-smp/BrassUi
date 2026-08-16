package net.swzo.brass.ui.kit.text

/**
 * The pure, Minecraft/Elementa-free markdown parser behind [BrassMarkdown] - a small visual subset:
 * `#`/`##`/`###` headings, `- ` bullets, `1. ` numbered items, `> ` quotes, `---` rules, fenced
 * code blocks (optionally with a declared language), inline `` `code` ``, `**bold**`, `*italic*`
 * and `[text](url)` links. Keeping it dependency-free means it has real unit tests (see
 * `BrassMarkdownTest`) instead of only being exercised by eyeballing the widget.
 */
object Markdown {

    enum class Style { NORMAL, BOLD, ITALIC, CODE, HEAD, QUOTE, LINK }

    /** One styled span; [url] is set only for LINK runs and [color] overrides the style colour. */
    data class Run(val text: String, val style: Style, val url: String? = null, val color: java.awt.Color? = null)

    /** One visual row of the rendered document. */
    data class Row(val runs: List<Run> = emptyList(), val rule: Boolean = false)

    sealed class Block {
        class Heading(val level: Int, val runs: List<Run>) : Block()
        class Paragraph(val runs: List<Run>) : Block()
        class Bullet(val runs: List<Run>) : Block()
        class Num(val index: Int, val runs: List<Run>) : Block()
        class Quote(val runs: List<Run>) : Block()
        class Code(val language: String?, val lines: List<String>) : Block()
        object Rule : Block()
        object Blank : Block()
    }

    /** Parse [md] into blocks. Blank lines are kept (the widget collapses them). */
    fun parse(md: String): List<Block> {
        val out = ArrayList<Block>()
        val lines = md.split('\n')
        var i = 0
        var inCode = false
        var codeLang: String? = null
        val codeBuffer = ArrayList<String>()
        while (i < lines.size) {
            var line = lines[i]
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCode) {
                    out.add(Block.Code(codeLang, codeBuffer.toList()))
                    codeBuffer.clear()
                    inCode = false
                } else {
                    inCode = true
                    codeLang = trimmed.removePrefix("```").trim().takeIf { it.isNotEmpty() }
                }
                i++
                continue
            }
            if (inCode) { codeBuffer.add(line); i++; continue }
            if (trimmed.isEmpty()) { out.add(Block.Blank); i++; continue }
            val head = Regex("""^(#{1,3})\s+(.*)$""").matchEntire(trimmed)
            if (head != null) { out.add(Block.Heading(head.groupValues[1].length, inline(head.groupValues[2]))); i++; continue }
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") { out.add(Block.Rule); i++; continue }
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                out.add(Block.Bullet(inline(trimmed.drop(2))))
                i++
                continue
            }
            val num = Regex("""^(\d+)\.\s+(.*)$""").matchEntire(trimmed)
            if (num != null) { out.add(Block.Num(num.groupValues[1].toInt(), inline(num.groupValues[2]))); i++; continue }
            if (trimmed.startsWith("> ")) { out.add(Block.Quote(inline(trimmed.drop(2)))); i++; continue }
            out.add(Block.Paragraph(inline(trimmed)))
            i++
        }
        if (inCode && codeBuffer.isNotEmpty()) out.add(Block.Code(codeLang, codeBuffer.toList()))
        return out
    }

    /** Parse inline `**bold**`, `*italic*`, `` `code` `` and `[text](url)` out of prose. */
    fun inline(text: String): List<Run> {
        val runs = ArrayList<Run>()
        val sb = StringBuilder()
        var i = 0
        fun flush() {
            if (sb.isNotEmpty()) { runs.add(Run(sb.toString(), Style.NORMAL)); sb.clear() }
        }
        while (i < text.length) {
            val c = text[i]
            when (c) {
                '`' -> {
                    val close = text.indexOf('`', i + 1)
                    if (close > i) { flush(); runs.add(Run(text.substring(i + 1, close), Style.CODE)); i = close + 1; continue }
                }
                '[' -> {
                    val close = text.indexOf("](", i + 1)
                    if (close > i) {
                        val end = text.indexOf(')', close + 2)
                        if (end > close) {
                            flush()
                            val label = text.substring(i + 1, close)
                            val url = text.substring(close + 2, end)
                            runs.add(Run(label, Style.LINK, url))
                            i = end + 1
                            continue
                        }
                    }
                }
                '*' -> {
                    if (text.startsWith("**", i)) {
                        val close = text.indexOf("**", i + 2)
                        if (close > i) { flush(); runs.add(Run(text.substring(i + 2, close), Style.BOLD)); i = close + 2; continue }
                    } else if (text.getOrNull(i + 1) != '*') {
                        val close = text.indexOf('*', i + 1)
                        if (close > i) { flush(); runs.add(Run(text.substring(i + 1, close), Style.ITALIC)); i = close + 1; continue }
                    }
                }
            }
            sb.append(c)
            i++
        }
        flush()
        return runs
    }

    /**
     * Word-wrap [blocks] into flat visual [Row]s at [width]. [measure] returns the pixel width of a
     * string; [codeHighlight] turns one fenced-code line into styled runs (a host injects real syntax
     * highlighting; the default is plain CODE-coloured text).
     */
    fun wrap(
        blocks: List<Block>,
        width: Float,
        measure: (String) -> Float,
        codeHighlight: (String?, String) -> List<Run> = { _, line -> listOf(Run(line, Style.CODE)) },
    ): List<Row> {
        val rows = ArrayList<Row>()
        var previous: Block? = null
        for (block in blocks) {
            when (block) {
                is Block.Heading, is Block.Code, is Block.Rule ->
                    if (previous != null && previous !is Block.Blank && previous !is Block.Heading) rows.add(Row())
                else -> Unit
            }
            when (block) {
                is Block.Heading -> rows.add(Row(block.runs.map { Run(it.text, Style.HEAD, it.url) }))
                is Block.Paragraph -> addWrapped(rows, block.runs, width, measure)
                is Block.Bullet -> addWrapped(rows, listOf(Run("•  ", Style.NORMAL)) + block.runs, width, measure)
                is Block.Num -> addWrapped(rows, listOf(Run("${block.index}.  ", Style.NORMAL)) + block.runs, width, measure)
                is Block.Quote -> addWrapped(rows, block.runs.map { Run(it.text, Style.QUOTE, it.url) }, width, measure)
                is Block.Code -> block.lines.forEach { line ->
                    // Code lines are kept VERBATIM (never wrapped) - the widget shows a horizontal
                    // scrollbar when one is wider than the viewport, like a real code view.
                    rows.add(Row(listOf(Run("  ", Style.CODE)) + codeHighlight(block.language, line)))
                }
                is Block.Rule -> rows.add(Row(rule = true))
                is Block.Blank -> rows.add(Row())
            }
            previous = block
        }
        return rows
    }

    private fun addWrapped(rows: MutableList<Row>, runs: List<Run>, width: Float, measure: (String) -> Float) {
        // Flatten runs into word entries; a LINK run is one unbreakable unit (its url stays with it).
        val words = ArrayList<Triple<String, Style, String?>>()
        for (run in runs) {
            if (run.url != null) {
                words.add(Triple(run.text, run.style, run.url))
            } else {
                for (word in run.text.split(' ')) if (word.isNotEmpty()) words.add(Triple(word, run.style, null))
            }
        }
        if (words.isEmpty()) { rows.add(Row()); return }
        var cur = ArrayList<Run>()
        var curW = 0f
        fun newLine() { rows.add(Row(cur)); cur = ArrayList(); curW = 0f }
        for (pair in words) {
            val (word, style, url) = pair
            val wordW = measure(word)
            if (wordW > width) {
                // A single unbreakable word wider than the line (a long URL, a token): break it
                // mid-word so it never spills past the card's edge.
                if (cur.isNotEmpty()) newLine()
                var remaining = word
                while (remaining.isNotEmpty()) {
                    var lo = 1
                    var hi = remaining.length
                    var best = 1
                    while (lo <= hi) {
                        val mid = (lo + hi) / 2
                        if (measure(remaining.substring(0, mid)) <= width) { best = mid; lo = mid + 1 }
                        else hi = mid - 1
                    }
                    cur.add(Run(remaining.substring(0, best), style, url))
                    remaining = remaining.substring(best)
                    if (remaining.isNotEmpty()) newLine()
                }
                continue
            }
            val spaceW = if (cur.isEmpty()) 0f else measure(" ")
            if (cur.isNotEmpty() && curW + spaceW + wordW > width) newLine()
            if (cur.isNotEmpty()) { cur.add(Run(" ", style, url)); curW += spaceW }
            cur.add(Run(word, style, url))
            curW += wordW
        }
        if (cur.isNotEmpty()) rows.add(Row(cur))
    }
}
