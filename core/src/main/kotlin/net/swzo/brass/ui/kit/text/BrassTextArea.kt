package net.swzo.brass.ui.kit.text

import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.layout.BrassScrollbarModel
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.platform.BrassCursor
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.floor

/**
 * A **multi-line** text field: word-wrapped, scrollable, selectable, with a caret you can move by
 * clicking, dragging or with the arrow keys.
 *
 * ### Why this exists
 *
 * [BrassTextInput] is explicitly single-line — vertical motion and Home/End all mean "the ends" — so
 * anything needing a description, a MOTD, a command block or a note had nowhere to go.
 *
 * ### Same editor, different layout
 *
 * Selection, word motion, the clipboard and every edit come from [BrassTextEdit], shared with
 * [BrassTextInput]. This class owns only the part that genuinely differs: turning a string into
 * wrapped lines, mapping a click to an index, and scrolling vertically instead of horizontally.
 *
 * That split is new. This widget used to reimplement the editing half and, in doing so, shipped
 * without selection, without a clipboard and without word-wise motion — so the same keystrokes did
 * different things in the two fields. They are now the same field with different geometry, which is
 * what anyone using them already assumed.
 */
class BrassTextArea(
    initial: String = "",
    private val placeholder: String = "",
    /**
     * A [BrassSyntax] language id (e.g. `"json"`, `"kotlin"`) for syntax-highlighted content, painted
     * with the same roles as markdown's fenced code blocks. Null keeps the field plain. The highlight is
     * rebuilt only when [text] changes, never per frame.
     */
    val language: String? = null,
    private val onChange: (String) -> Unit = {},
) : BrassWidget(BrassAccent.DEFAULT), BrassValue<String>, BrassFocusable, BrassTextField {

    private val holder = BrassValueHolder(initial) { invalidateWrap() }

    override var value: String
        get() = holder.value
        set(v) { holder.value = v; edit.clamp() }

    override fun setSilently(value: String) {
        holder.setSilently(value)
        edit.clamp()
    }

    override fun onChange(listener: (String) -> Unit) = holder.onChange(listener)
    override fun bind(state: BrassState<String>) = holder.bind(this, state)

    /** The field's contents. Alias of [value]. */
    val text: String get() = holder.value

    /** Select the complete note/body so the next typed character replaces placeholder-like content. */
    fun selectAll() = edit.selectAll()

    /**
     * Scale the text metrics and padding together. Normally 1; immediate-mode canvases can match their
     * own zoom so entering edit mode does not make the same text visibly change size.
     */
    var contentScale: Float = 1f
        set(value) {
            val next = value.coerceIn(0.4f, 2.2f)
            if (field == next) return
            field = next
            invalidateWrap()
        }

    /**
     * Set this to make the field a **composer**: Enter submits and Shift+Enter inserts the newline.
     *
     * That is the opposite of the field's default polarity, and deliberately so. A text area whose job
     * is a description or a MOTD wants Enter to mean "new paragraph", because there is no other way to
     * get one and nothing is waiting on the text. A chat composer is the other case entirely — every
     * message ends with Enter and only the occasional one has a second line — and a user who has to
     * reach for the mouse to send, or who sends by pressing a key that visibly does nothing else in
     * every other field, will notice immediately.
     *
     * Rather than have callers re-handle keys (and get selection, wrapping and the caret wrong doing
     * it), setting this swaps the two bindings and leaves everything else alone. The field is still the
     * same editor; only what Enter means changes, and it changes exactly when a caller has said there
     * is something for it to submit to.
     */
    var onSubmit: ((String) -> Unit)? = null

    /**
     * How many **visual** lines the text currently occupies — wrapped lines included, not just the
     * newlines in it. For a composer that grows with what is typed; see [heightForLines].
     */
    val lineCount: Int get() = layout(innerWidth()).size.coerceAtLeast(1)

    override var focused = false
        private set

    /** The caret, the selection and every edit — see [BrassTextEdit]. */
    private val edit = BrassTextEdit(
        multiline = true,
        read = { holder.value },
        write = { holder.value = it; invalidateWrap() },
        onTouch = { resetBlink() },
    )

    private var scrollY = 0f
    private val bar = BrassScrollbarModel()

    private var caretLitAt = System.currentTimeMillis()

    /**
     * Set while the mouse is down inside this field, so a drag selects.
     *
     * Elementa broadcasts drags to the whole tree, so without this gate every field on screen would
     * select text when you dragged anywhere at all.
     */
    private var selecting = false
    private var lastClickAt = 0L

    private var wrapped: List<String> = emptyList()
    private var starts: List<Int> = emptyList()
    private var wrappedFor = -1f
    private var wrappedText: String? = null

    /** Highlighted source lines and each line's absolute start offset, keyed to the exact [text]. */
    private var highlighted: List<List<BrassSyntax.Span>> = emptyList()
    private var sourceLineStarts: IntArray = IntArray(0)
    private var highlightedFor: String? = null

    init {
        holder.onChange(onChange)

        onMouseClick { e ->
            if (!active || e.mouseButton != 0) return@onMouseClick
            grabWindowFocus()
            val index = caretAt(e.relativeX, e.relativeY)
            val now = System.currentTimeMillis()
            when {
                now - lastClickAt < BrassMetrics.DOUBLE_CLICK_MS -> edit.selectWordAt(index)
                UKeyboard.isShiftKeyDown() -> edit.moveTo(index, extend = true)
                else -> edit.moveTo(index, extend = false)
            }
            lastClickAt = now
            selecting = true
        }

        onMouseDrag { mx, my, button ->
            if (!selecting || button != 0) return@onMouseDrag
            edit.moveTo(caretAt(mx, my), extend = true)
        }

        onMouseRelease { selecting = false }

        onMouseScroll { e ->
            scrollY = bar.clamp(scrollY - e.delta.toFloat() * lineHeight() * 2f)
            e.stopPropagation()
        }

        onFocus { focused = true; resetBlink() }
        onFocusLost { focused = false; selecting = false; edit.moveTo(edit.caret, extend = false) }

        onKeyType { typedChar, keyCode ->
            if (!active || !focused) return@onKeyType
            edit.clamp()
            val shift = UKeyboard.isShiftKeyDown()

            when {
                UKeyboard.isKeyComboCtrlA(keyCode) -> edit.selectAll()
                UKeyboard.isKeyComboCtrlC(keyCode) -> edit.copy()
                UKeyboard.isKeyComboCtrlX(keyCode) -> edit.cut()
                UKeyboard.isKeyComboCtrlV(keyCode) -> edit.paste()

                keyCode == GLFW.GLFW_KEY_LEFT ->
                    edit.arrow(forward = false, shift = shift, lineHome = ::lineHome, lineEnd = ::lineTail)
                keyCode == GLFW.GLFW_KEY_RIGHT ->
                    edit.arrow(forward = true, shift = shift, lineHome = ::lineHome, lineEnd = ::lineTail)

                keyCode == GLFW.GLFW_KEY_UP -> moveLine(-1, shift)
                keyCode == GLFW.GLFW_KEY_DOWN -> moveLine(1, shift)
                keyCode == GLFW.GLFW_KEY_HOME -> edit.moveTo(lineHome(), shift)
                keyCode == GLFW.GLFW_KEY_END -> edit.moveTo(lineTail(), shift)

                keyCode == GLFW.GLFW_KEY_BACKSPACE -> edit.erase(forward = false)
                keyCode == GLFW.GLFW_KEY_DELETE -> edit.erase(forward = true)

                // Enter inserts a newline — the whole point of the widget — unless the field has been
                // given something to submit to, which swaps the two bindings. See [onSubmit].
                keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER -> {
                    val submit = onSubmit
                    when {
                        // No submit hook: Shift+Enter drops focus so Tab order carries on sensibly.
                        submit == null -> if (shift) loseFocus() else edit.insert("\n")
                        shift -> edit.insert("\n")
                        else -> submit(text)
                    }
                }

                !typedChar.isISOControl() && typedChar.code >= 32 -> edit.insert(typedChar.toString())
            }
            revealCaret()
        }
    }

    /** A text area keeps Tab for the focus ring rather than inserting one — Tab must always escape. */
    override val focusable: Boolean get() = active

    private fun resetBlink() { caretLitAt = System.currentTimeMillis() }

    private fun invalidateWrap() { wrappedFor = -1f; wrappedText = null }

    /**
     * The current text tokenized as [language]. Rebuilt only when [text] actually changes (an identity
     * check, like [wrapped] — every edit produces a fresh String), never per frame. [sourceLineStarts]
     * is built in the same pass so a wrapped visual line can find its own colour runs; a visual line
     * never crosses a paragraph break, so it maps to exactly one source line.
     */
    private fun highlightedLines(): List<List<BrassSyntax.Span>> {
        if (highlightedFor !== text) {
            highlightedFor = text
            highlighted = BrassSyntax.highlight(language, text)
            sourceLineStarts = IntArray(highlighted.size)
            var offset = 0
            for (i in highlighted.indices) {
                sourceLineStarts[i] = offset
                offset += highlighted[i].sumOf { it.text.length } + 1   // +1 for the newline that ended it
            }
        }
        return highlighted
    }

    // No ScissorEffect here, deliberately. It is the obvious way to guarantee nothing escapes the box,
    // and it would clip the widget's *own* chrome: a keycap bleeds BLEED_X to the sides and
    // BLEED_BOTTOM below its bounds for the outer ring and lip, and a scissor set to those same bounds
    // cuts all of it away — the field would lose its border to fix an overflow that no longer happens.
    // Text is kept inside by construction instead: `layout` breaks even an unbreakable word at the
    // field's width, and `drawContent` skips lines outside the viewport.

    // ---- wrapping ----------------------------------------------------------------------------------

    /**
     * The visual lines at the current width, and the character index each begins at.
     *
     * Explicit newlines are honoured first and each paragraph is then greedily wrapped. The line starts
     * are built *here*, in the same pass — they used to be recomputed separately by walking the wrapped
     * lines and guessing which separator each break had consumed, which is only correct while every
     * break falls on a space. It does not: a word longer than the field is broken mid-word (see below),
     * and the guess then desynchronised every index after it, putting the caret and the selection on
     * the wrong characters for the rest of the field.
     */
    private fun layout(width: Float): List<String> {
        if (width == wrappedFor && wrappedText === text) return wrapped

        val lines = ArrayList<String>()
        val begins = ArrayList<Int>()
        var index = 0

        for (paragraph in text.split('\n')) {
            if (paragraph.isEmpty()) {
                lines.add(""); begins.add(index); index++   // step over the newline
                continue
            }
            var line = StringBuilder()
            var lineStart = index
            var cursor = index
            // Whether any non-space content has landed on the current paragraph yet. Until it has, the
            // spaces at the front are the line's *indentation* and are kept; once wrapping has begun, a
            // space left at the start of a fresh visual line is a soft-wrap seam and is dropped instead.
            var contentSeen = false

            fun flush() {
                lines.add(line.toString())
                begins.add(lineStart)
                line = StringBuilder()
            }

            for ((w, word) in paragraph.split(' ').withIndex()) {
                if (w > 0) {
                    // The space that joins this word to the previous one. On an empty line it is either
                    // indentation (keep it) or the seam of a soft wrap (drop it) - see [contentSeen].
                    when {
                        line.isNotEmpty() -> line.append(' ')
                        contentSeen -> lineStart = cursor + 1
                        else -> line.append(' ')
                    }
                    cursor++
                }
                if (word.isNotEmpty()) contentSeen = true
                var remaining = word
                while (remaining.isNotEmpty()) {
                    val candidate = line.toString() + remaining
                    if (textWidth(candidate) <= width) {
                        if (line.isEmpty()) lineStart = cursor
                        line.append(remaining)
                        cursor += remaining.length
                        remaining = ""
                    } else if (line.isNotEmpty()) {
                        // Try the word on a line of its own first.
                        flush()
                        lineStart = cursor
                    } else {
                        // A single word wider than the whole field. Break it by character rather than
                        // letting it run off the edge — the old code special-cased `line.isEmpty()` to
                        // accept any word whatever its width, which is why a long unbroken string (a
                        // URL, a hash, a German compound) drew straight out of the widget.
                        val fits = longestPrefix(remaining, width)
                        line.append(remaining.take(fits))
                        cursor += fits
                        remaining = remaining.drop(fits)
                        flush()
                        lineStart = cursor
                    }
                }
            }
            flush()
            index = cursor + 1   // step over the newline that ended this paragraph
        }

        wrappedFor = width
        wrappedText = text
        wrapped = lines
        starts = begins
        return lines
    }

    /** How many leading characters of [s] fit in [width]. At least one, or a break makes no progress. */
    private fun longestPrefix(s: String, width: Float): Int {
        var n = 0
        var acc = 0f
        while (n < s.length) {
            val next = acc + textWidth(s[n].toString())
            if (next > width && n > 0) break
            acc = next
            n++
        }
        return n.coerceAtLeast(1)
    }

    private fun lineStartsList(width: Float): List<Int> {
        layout(width)
        return starts
    }

    /** Index of the visual line containing [index]. */
    private fun lineOf(index: Int, width: Float): Int {
        val s = lineStartsList(width)
        for (i in s.indices.reversed()) if (index >= s[i]) return i
        return 0
    }

    /** Start of the visual line the caret is on. */
    private fun lineHome(): Int {
        val width = innerWidth()
        return lineStartsList(width).getOrElse(lineOf(edit.caret, width)) { 0 }
    }

    /** End of the visual line the caret is on. */
    private fun lineTail(): Int {
        val width = innerWidth()
        val row = lineOf(edit.caret, width)
        val s = lineStartsList(width)
        return (s.getOrElse(row) { 0 } + layout(width).getOrElse(row) { "" }.length).coerceAtMost(text.length)
    }

    private fun moveLine(delta: Int, extend: Boolean) {
        val width = innerWidth()
        val lines = layout(width)
        val s = lineStartsList(width)
        val current = lineOf(edit.caret, width)
        val target = (current + delta).coerceIn(0, (s.size - 1).coerceAtLeast(0))
        if (target == current) return
        val column = edit.caret - s[current]
        val length = lines.getOrElse(target) { "" }.length
        edit.moveTo(s[target] + column.coerceAtMost(length), extend)
    }

    private fun caretAt(localX: Float, localY: Float): Int {
        val width = innerWidth()
        val lines = layout(width)
        if (lines.isEmpty()) return 0
        val s = lineStartsList(width)
        val row = floor((localY - scaledPad() + scrollY) / lineHeight()).toInt()
            .coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        val line = lines[row]
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (i in 0..line.length) {
            val d = kotlin.math.abs(textWidth(line.substring(0, i)) - (localX - scaledPad()))
            if (d < bestDist) { bestDist = d; best = i }
        }
        return (s[row] + best).coerceIn(0, text.length)
    }

    private fun innerWidth(): Float = (bw - scaledPad() * 2f - GRIP_W - 2f).coerceAtLeast(1f)
    private fun scaledPad(): Float = PAD * contentScale
    private fun lineHeight(): Float = BrassFont.LINE * contentScale
    private fun textWidth(value: String): Float = BrassFont.width(this, value) * contentScale

    /** Scroll so the caret's line is inside the viewport, after a keystroke moved it. */
    private fun revealCaret() {
        val width = innerWidth()
        val row = lineOf(edit.caret, width)
        val lineHeight = lineHeight()
        val top = row * lineHeight
        val viewport = bar.viewport
        if (top < scrollY) scrollY = top
        else if (top + lineHeight > scrollY + viewport) scrollY = top + lineHeight - viewport
        scrollY = bar.clamp(scrollY)
    }

    // ---- render ------------------------------------------------------------------------------------

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        if (hoveredState && active) BrassCursor.request(BrassCursor.Kind.TEXT)
        edit.clamp()

        val width = innerWidth()
        val lines = layout(width)
        val s = lineStartsList(width)
        val pad = scaledPad()
        val lineHeight = lineHeight()

        bar.viewport = h - pad * 2f
        bar.content = lines.size * lineHeight
        scrollY = bar.clamp(scrollY)

        val showPlaceholder = text.isEmpty() && placeholder.isNotEmpty()
        val body = if (showPlaceholder) listOf(placeholder) else lines
        val tint = if (showPlaceholder) Colors.UI_TEXT_DARK else textColor

        // Clip the text (and the caret) to the padded viewport, so a line only half inside the box is
        // cut cleanly at the edge instead of spilling over the border. Scoped to the text draw only -
        // the chrome and scrollbar are drawn outside it - so unlike a component-wide ScissorEffect it
        // does not eat the keycap's own bleeding border (see the note above [layout]).
        val clip = ScissorEffect(x.toFloat(), y + pad, (x + w).toFloat(), y + h - pad, true)
        clip.beforeDraw(m)

        var ly = y + pad - scrollY
        for ((row, line) in body.withIndex()) {
            // Only the lines actually in the viewport are drawn — the field may hold far more.
            if (ly + lineHeight >= y && ly <= y + h) {
                if (!showPlaceholder && edit.hasSelection) {
                    paintSelection(m, row, line, s, x, ly)
                }
                drawLine(m, line, s[row], x + pad, ly, tint, showPlaceholder)
            }
            ly += lineHeight
        }

        // A caret would be ambiguous next to a selection highlight, so it only shows for a plain
        // insertion point — the same rule the single-line field uses.
        //
        // It does show over the placeholder, which it used to not. An empty field that had just been
        // clicked looked exactly like one that had not: greyed prompt text, no caret, no sign the
        // click had landed — so the only way to find out whether the thing was a text field was to
        // type at it and see. The caret is the affordance, and it is needed most in the one state
        // that has nothing else in it.
        if (focused && !edit.hasSelection) {
            val since = System.currentTimeMillis() - caretLitAt
            if (since < BLINK_MS || (since / BLINK_MS) % 2 == 0L) {
                // An empty field has no line to measure into, so the caret sits at the text origin.
                val cx: Float
                val cy: Float
                if (showPlaceholder) {
                    cx = x + pad
                    cy = y + pad - scrollY
                } else {
                    val row = lineOf(edit.caret, width)
                    val column = (edit.caret - s.getOrElse(row) { 0 }).coerceAtLeast(0)
                    val lineText = lines.getOrElse(row) { "" }
                    cx = x + pad + textWidth(lineText.take(column.coerceAtMost(lineText.length)))
                    cy = y + pad - scrollY + row * lineHeight
                }
                if (cy >= y && cy <= y + h - lineHeight) {
                    BrassPaint.rect(m, cx, cy, cx + 1f, cy + lineHeight, Colors.UI_ACCENT_BRIGHT)
                }
            }
        }

        clip.afterDraw(m)

        if (bar.scrollable) {
            val gx = x + w - GRIP_W - 2f
            val gy = y + pad + bar.gripTop(scrollY)
            BrassPaint.rect(m, gx, y + pad, gx + GRIP_W, y + h - pad, TRACK)
            net.swzo.brass.ui.kit.paint.BrassCard.grip(m, gx, gy, gx + GRIP_W, gy + bar.gripHeight())
        }
    }

    /**
     * One visual line of the field, honouring [contentScale]. With a [language] set, the line is painted
     * as its syntax-coloured runs clipped to this visual line's slice of the source — a soft wrap inside
     * a token keeps that token's colour across the seam (a long string stays amber over the break).
     * Placeholder prompts drop the shadow so they read as a ghost behind real text.
     */
    private fun drawLine(
        m: UMatrixStack,
        line: String,
        start: Int,
        x: Float,
        y: Float,
        tint: Color,
        placeholder: Boolean,
    ) {
        if (placeholder || line.isEmpty() || language.isNullOrEmpty()) {
            BrassFont.draw(m, this, line, x, y, tint, shadow = !placeholder, scale = contentScale)
            return
        }
        val sourceIndex = sourceIndexFor(start)
        val spans = highlightedLines().getOrNull(sourceIndex) ?: run {
            BrassFont.draw(m, this, line, x, y, tint, shadow = true, scale = contentScale)
            return
        }
        val lineStart = sourceLineStarts[sourceIndex]
        val end = start + line.length
        var sx = x
        var offset = 0
        for (span in spans) {
            val clipFrom = maxOf(start - lineStart, offset)
            val clipTo = minOf(end - lineStart, offset + span.text.length)
            if (clipTo > clipFrom) {
                val piece = span.text.substring(clipFrom - offset, clipTo - offset)
                BrassFont.draw(m, this, piece, sx, y, span.color, shadow = true, scale = contentScale)
                sx += textWidth(piece)
            }
            offset += span.text.length
            if (offset >= end - lineStart) break
        }
    }

    /** The source line containing absolute offset [start] — binary search over [sourceLineStarts]. */
    private fun sourceIndexFor(start: Int): Int {
        val probe = sourceLineStarts.binarySearch(start)
        return if (probe >= 0) probe else -probe - 2
    }

    /**
     * The selection wash across one visual line.
     *
     * Painted per line rather than as one rectangle because a selection spanning three lines is three
     * separate runs, and the middle one has to extend to the line's own end rather than to the widest
     * line's — otherwise a multi-line selection reads as a ragged block with holes in it.
     */
    private fun paintSelection(m: UMatrixStack, row: Int, line: String, starts: List<Int>, x: Int, ly: Float) {
        val lineStart = starts.getOrElse(row) { return }
        val lineEnd = lineStart + line.length
        val from = maxOf(edit.selStart, lineStart)
        val to = minOf(edit.selEnd, lineEnd)
        if (to < from) return

        val pad = scaledPad()
        val sx = x + pad + textWidth(line.take((from - lineStart).coerceIn(0, line.length)))
        val ex = x + pad + textWidth(line.take((to - lineStart).coerceIn(0, line.length)))
        // A selection that runs *through* this line (rather than ending on it) covers the newline too,
        // so a sliver past the last glyph is what shows the line break is included.
        val tail = if (edit.selEnd > lineEnd) NEWLINE_SLIVER * contentScale else 0f
        if (ex + tail > sx) BrassPaint.rect(m, sx, ly, ex + tail, ly + lineHeight(), SELECTION)
    }

    companion object : BrassDemoSource {

        /** Multi-line entry — type into it, press Enter, select across the line break. */
        override fun demo() = BrassDemo("text-area", "Text area", 220f, 70f) {
            BrassTextArea(placeholder = "Description")
        }

        /**
         * The height a field needs to show [lines] of text without scrolling — the padding above and
         * below plus the lines themselves.
         *
         * Exposed so a composer can grow with what is typed (`heightForLines(lineCount.coerceAtMost(4))`)
         * without duplicating the padding, which is private and would otherwise be guessed at.
         */
        fun heightForLines(lines: Int): Float = lines.coerceAtLeast(1) * BrassFont.LINE + PAD * 2

        // ---- widget internals ------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val PAD = 5f
        private const val GRIP_W = 3f

        /** How long the caret stays lit, and then dark, once idle. */
        private const val BLINK_MS = 500L

        /** Width of the stub marking a selected line break at the end of a line. */
        private const val NEWLINE_SLIVER = 3f

        private val TRACK: Color get() = Colors.SCROLL_TRACK

        /** Selection wash: the accent at low alpha, so glyphs stay legible on top of it. */
        private val SELECTION: Color
            get() = Color(Colors.UI_ACCENT.red, Colors.UI_ACCENT.green, Colors.UI_ACCENT.blue, 90)
    }
}
