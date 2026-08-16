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
 * ### Why this exists
 * [BrassTextInput] is explicitly single-line — vertical motion and Home/End all mean "the ends" — so
 * anything needing a description, a MOTD, a command block or a note had nowhere to go.
 * ### Same editor, different layout
 * Selection, word motion, the clipboard and every edit come from [BrassTextEdit], shared with
 * [BrassTextInput]. This class owns only the part that genuinely differs: turning a string into
 * wrapped lines, mapping a click to an index, and scrolling vertically instead of horizontally.
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
    /**
     * Draw a source-line-number gutter on the left. Soft-wrapped continuation rows leave the number
     * blank, so the numbers always match the source lines the caret is really editing.
     */
    var lineNumbers: Boolean = false,
    /**
     * Tab indents (4 spaces, or dedents with Shift) like a code editor. Defaults OFF so plain text
     * fields keep the old Tab-for-focus-ring behaviour; a code host (the Lua IDE) opts in.
     * Undo/redo (Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z) are always active.
     */
    var indentOnTab: Boolean = false,
    /**
     * Soft-wrap long lines (default, for prose fields). `false` keeps one source line per row and
     * adds a horizontal scrollbar - a code editor's layout, like [BrassCodeView].
     */
    var wrap: Boolean = true,
    private val onChange: (String) -> Unit = {},
) : BrassWidget(BrassAccent.DEFAULT), BrassValue<String>, BrassFocusable, BrassTextField {

    private val holder = BrassValueHolder(initial) { invalidateWrap() }

    /** Source line numbers (1-based) to tint red - a diagnostics marker, like BrassCodeView's. */
    var markers: Set<Int> = emptySet()

    override var value: String
        get() = holder.value
        set(v) { holder.value = v; edit.clamp() }

    override fun setSilently(value: String) {
        holder.setSilently(value)
        edit.clamp()
    }

    override fun onChange(listener: (String) -> Unit) = holder.onChange(listener)
    override fun bind(state: BrassState<String>) = holder.bind(this, state)

    val text: String get() = holder.value

    /** The caret's character index (for hosts: autocomplete, diagnostics, goto-definition). */
    val caret: Int get() = edit.caret

    fun selectAll() = edit.selectAll()

    fun selectRange(start: Int, end: Int) = edit.selectRange(start, end)

    /** Select the whole source line containing [index] (a triple-click). */
    fun selectLineAt(index: Int) = edit.selectLineAt(index)

    /** Delete everything before the caret on the current line (Ctrl+Backspace). */
    fun deleteToLineStart() = edit.deleteToLineStart()

    /** Delete the whole source line the caret is on. */
    fun deleteLine() { forgetColumn(); edit.deleteLine() }

    /** Delete a whole word: [forward] eats the word ahead, otherwise the word behind the caret. */
    fun deleteWord(forward: Boolean) { forgetColumn(); edit.deleteWord(forward) }

    /** Replace the character range [start]..[end] with [replacement] (undoable) and move the caret. */
    fun replaceRange(start: Int, end: Int, replacement: String, newCaret: Int? = null) {
        edit.replaceRange(start, end, replacement, newCaret ?: (start + replacement.length))
    }

    var contentScale: Float = 1f
        set(value) {
            val next = value.coerceIn(0.4f, 2.2f)
            if (field == next) return
            field = next
            invalidateWrap()
        }

    var onSubmit: ((String) -> Unit)? = null

    /**
     * A hook a host (a code IDE) installs to intercept keys before the editor's own bindings run.
     * Returning true consumes the key entirely (an autocomplete popup owns Up/Down/Enter while it is
     * open); returning false (or null) lets the normal editing keys handle it.
     */
    var onEditorKey: ((typedChar: Char, keyCode: Int) -> Boolean)? = null

    /** Fired after the caret moves or the text changes (the edit model's onTouch). */
    var onCaretMoved: (() -> Unit)? = null

    /** Fired with the character offset under the mouse on every mouse move (for Ctrl+hover underlines). */
    var onHoverOffset: ((Int) -> Unit)? = null

    /**
     * A character range to draw an underline under (Ctrl+hover: a symbol that has a definition).
     * Only drawn while Ctrl is held, so it vanishes the moment the modifier is released.
     */
    var hoverUnderline: IntRange? = null

    val hasSelection: Boolean get() = edit.hasSelection
    val selectedText: String get() = edit.selectedText
    val selStart: Int get() = edit.selStart
    val selEnd: Int get() = edit.selEnd
    fun copySelection() = edit.copy()
    fun cutSelection() = edit.cut()
    fun pasteClipboard() = edit.paste()

    /**
     * Replaces [BrassSyntax.highlight] for this field - a host (a code IDE) with a real parser injects
     * semantic spans (locals vs functions vs constants), returning one list of spans per source line.
     * Returning null falls back to the built-in language highlighter.
     */
    var highlightOverride: ((String) -> List<List<BrassSyntax.Span>>?)? = null

    val lineCount: Int get() = layout(innerWidth()).size.coerceAtLeast(1)

    override var focused = false
        private set

    private val edit = BrassTextEdit(
        multiline = true,
        read = { holder.value },
        write = { holder.value = it; invalidateWrap() },
        onTouch = {
            resetBlink()
            onCaretMoved?.invoke()
        },
    )

    private var scrollY = 0f
    private var scrollX = 0f
    private val bar = BrassScrollbarModel()
    private val hBar = BrassScrollbarModel()

    private var caretLitAt = System.currentTimeMillis()

    private var selecting = false
    private var lastClickAt = 0L
    /** Consecutive clicks within the double-click window: 2 = word, 3 = whole line. */
    private var consecutiveClicks = 0
    private var lastHoverOffset = -1

    /** Scrollbar drag state: 0 = none, 1 = vertical grip, 2 = horizontal grip. */
    private var barGrab = 0
    private var barGrabOffset = 0f

    /**
     * The remembered horizontal column for Up/Down movement. A vertical run anchors its column on the
     * FIRST move and keeps it, so a short line never re-anchors the caret when you come back up (the
     * classic "down then back up" drift every IDE fixes). Any horizontal repositioning - a left/right
     * arrow, Home/End, a click, or an edit - clears it. Null means "anchor from the current caret".
     */
    private var goalColumn: Int? = null

    private fun forgetColumn() { goalColumn = null }

    private var wrapped: List<String> = emptyList()
    private var starts: List<Int> = emptyList()
    private var wrappedFor = -1f
    private var wrappedText: String? = null

    private var highlighted: List<List<BrassSyntax.Span>> = emptyList()
    private var sourceStartsCache: IntArray = IntArray(0)
    private var sourceStartsFor: String? = null
    private var highlightedFor: String? = null

    init {
        holder.onChange(onChange)

        onMouseClick { e ->
            if (!active || e.mouseButton != 0) return@onMouseClick
            val rx = e.relativeX
            val ry = e.relativeY
            val bw = getWidth()
            val bh = getHeight()
            val pad = scaledPad()

            // Vertical scrollbar: grabbing the grip drags; clicking the track pages toward the click.
            if (bar.scrollable && rx >= bw - GRIP_W - 2f - BAR_GRAB && rx <= bw - 2f + BAR_GRAB) {
                val gripY = pad + bar.gripTop(scrollY)
                if (ry >= gripY - BAR_GRAB && ry <= gripY + bar.gripHeight() + BAR_GRAB) {
                    barGrab = 1
                    barGrabOffset = ry - gripY
                    return@onMouseClick
                } else if (ry >= pad && ry <= bh - pad) {
                    scrollY = bar.pageToward(scrollY, ry - pad)
                    return@onMouseClick
                }
            }

            // Horizontal scrollbar (code mode): same grab/page treatment.
            if (!wrap && hBar.scrollable && ry >= bh - pad - H_BAR_H - BAR_GRAB && ry <= bh - pad + H_BAR_H + BAR_GRAB) {
                val gx = pad + gutterWidth() + hBar.gripTop(scrollX)
                val gw = hBar.gripHeight().coerceAtLeast(4f)
                if (rx >= gx - BAR_GRAB && rx <= gx + gw + BAR_GRAB) {
                    barGrab = 2
                    barGrabOffset = rx - gx
                    return@onMouseClick
                } else if (rx >= pad + gutterWidth() && rx <= bw - pad - GRIP_W - 2f) {
                    scrollX = hBar.pageToward(scrollX, rx - (pad + gutterWidth()))
                    return@onMouseClick
                }
            }

            grabWindowFocus()
            forgetColumn()
            val index = caretAt(rx, ry)
            val now = System.currentTimeMillis()
            consecutiveClicks = if (now - lastClickAt < BrassMetrics.DOUBLE_CLICK_MS) consecutiveClicks + 1 else 1
            lastClickAt = now
            when {
                // A fast third click selects the whole source line (classic triple-click).
                consecutiveClicks >= 3 -> edit.selectLineAt(index)
                consecutiveClicks == 2 -> edit.selectWordAt(index)
                UKeyboard.isShiftKeyDown() -> edit.moveTo(index, extend = true)
                else -> edit.moveTo(index, extend = false)
            }
            selecting = true
        }

        onMouseDrag { mx, my, button ->
            if (button != 0) return@onMouseDrag
            when (barGrab) {
                1 -> scrollY = bar.offsetForGripTop(my - barGrabOffset - scaledPad())
                2 -> scrollX = hBar.offsetForGripTop(mx - barGrabOffset - (scaledPad() + gutterWidth()))
                else -> {
                    if (!selecting) return@onMouseDrag
                    forgetColumn()
                    edit.moveTo(caretAt(mx, my), extend = true)
                }
            }
        }

        onMouseRelease {
            selecting = false
            barGrab = 0
        }

        onMouseScroll { e ->
            if (!wrap && UKeyboard.isShiftKeyDown()) {
                scrollX = hBar.clamp(scrollX - e.delta.toFloat() * lineHeight() * 2f)
            } else {
                scrollY = bar.clamp(scrollY - e.delta.toFloat() * lineHeight() * 2f)
            }
            e.stopPropagation()
        }

        onFocus { focused = true; resetBlink() }
        onFocusLost { focused = false; selecting = false; edit.moveTo(edit.caret, extend = false) }

        onKeyType { typedChar, keyCode ->
            if (!active || !focused) return@onKeyType
            if (onEditorKey?.invoke(typedChar, keyCode) == true) return@onKeyType
            edit.clamp()
            val shift = UKeyboard.isShiftKeyDown()

            when {
                // A code editor's core bindings.
                UKeyboard.isKeyComboCtrlZ(keyCode) -> { edit.undo(); forgetColumn() }
                UKeyboard.isKeyComboCtrlShiftZ(keyCode) || UKeyboard.isKeyComboCtrlY(keyCode) -> { edit.redo(); forgetColumn() }
                keyCode == GLFW.GLFW_KEY_TAB -> if (indentOnTab) { indentSelection(shift); forgetColumn() } else return@onKeyType

                UKeyboard.isKeyComboCtrlA(keyCode) -> edit.selectAll()
                UKeyboard.isKeyComboCtrlC(keyCode) -> edit.copy()
                UKeyboard.isKeyComboCtrlX(keyCode) -> edit.cut()
                UKeyboard.isKeyComboCtrlV(keyCode) -> edit.paste()

                keyCode == GLFW.GLFW_KEY_LEFT -> { forgetColumn(); edit.arrow(forward = false, shift = shift, lineHome = ::lineHome, lineEnd = ::lineTail) }
                keyCode == GLFW.GLFW_KEY_RIGHT -> { forgetColumn(); edit.arrow(forward = true, shift = shift, lineHome = ::lineHome, lineEnd = ::lineTail) }

                keyCode == GLFW.GLFW_KEY_UP -> moveLine(-1, shift)
                keyCode == GLFW.GLFW_KEY_DOWN -> moveLine(1, shift)
                keyCode == GLFW.GLFW_KEY_HOME -> { forgetColumn(); edit.moveTo(lineHome(), shift) }
                keyCode == GLFW.GLFW_KEY_END -> { forgetColumn(); edit.moveTo(lineTail(), shift) }

                keyCode == GLFW.GLFW_KEY_BACKSPACE -> { forgetColumn(); edit.erase(forward = false) }
                keyCode == GLFW.GLFW_KEY_DELETE -> { forgetColumn(); edit.erase(forward = true) }

                // Enter inserts a newline — the whole point of the widget — unless the field has been
                // given something to submit to, which swaps the two bindings. See [onSubmit].
                keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER -> {
                    val submit = onSubmit
                    when {
                        // No submit hook: Shift+Enter drops focus so Tab order carries on sensibly.
                        submit == null -> if (shift) loseFocus() else { edit.insert("\n"); forgetColumn() }
                        shift -> { edit.insert("\n"); forgetColumn() }
                        else -> submit(text)
                    }
                }

                !typedChar.isISOControl() && typedChar.code >= 32 -> { edit.insert(typedChar.toString()); forgetColumn() }
            }
            revealCaret()
        }
    }

    /** A text area keeps Tab for the focus ring rather than inserting one — Tab must always escape. */
    override val focusable: Boolean get() = active

    private fun resetBlink() { caretLitAt = System.currentTimeMillis() }

    private fun invalidateWrap() { wrappedFor = -1f; wrappedText = null }

    /**
     * The character index where each source line begins. Built once per [text] (an identity check),
     * shared by the highlighter (which finds the colour runs for a visual row) and the gutter (which
     * numbers the source lines). A visual line never crosses a newline, so one visual row always maps
     * to exactly one source line.
     */
    private fun sourceLineStarts(): IntArray {
        if (sourceStartsFor !== text) {
            sourceStartsFor = text
            val starts = ArrayList<Int>()
            var i = 0
            while (i < text.length) {
                starts.add(i)
                val nl = text.indexOf('\n', i)
                if (nl < 0) break
                i = nl + 1
            }
            sourceStartsCache = starts.toIntArray()
        }
        return sourceStartsCache
    }

    /**
     * The current text tokenized as [language]. Rebuilt only when [text] actually changes (an identity
     * check, like [wrapped] — every edit produces a fresh String), never per frame.
     */
    private fun highlightedLines(): List<List<BrassSyntax.Span>> {
        if (highlightedFor !== text) {
            highlightedFor = text
            highlighted = highlightOverride?.invoke(text) ?: BrassSyntax.highlight(language, text)
        }
        return highlighted
    }

    // No ScissorEffect here, deliberately. It is the obvious way to guarantee nothing escapes the box,
    // and it would clip the widget's *own* chrome: a keycap bleeds BLEED_X to the sides and
    // BLEED_BOTTOM below its bounds for the outer ring and lip, and a scissor set to those same bounds
    // cuts all of it away — the field would lose its border to fix an overflow that no longer happens.
    // Text is kept inside by construction instead: `layout` breaks even an unbreakable word at the
    // field's width, and `drawContent` skips lines outside the viewport.


    private fun layout(width: Float): List<String> {
        if (width == wrappedFor && wrappedText === text) return wrapped

        val lines = ArrayList<String>()
        val begins = ArrayList<Int>()

        // Code mode (wrap == false): one source line per row, never word-wrapped. Long lines extend
        // past the viewport and scroll horizontally - the same reading model as BrassCodeView.
        if (!wrap) {
            var index = 0
            for (paragraph in text.split('\n')) {
                lines.add(paragraph)
                begins.add(index)
                index += paragraph.length + 1
            }
            wrappedFor = width
            wrappedText = text
            wrapped = lines
            starts = begins
            return lines
        }

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

    private fun lineOf(index: Int, width: Float): Int {
        val s = lineStartsList(width)
        for (i in s.indices.reversed()) if (index >= s[i]) return i
        return 0
    }

    private fun lineHome(): Int {
        val width = innerWidth()
        return lineStartsList(width).getOrElse(lineOf(edit.caret, width)) { 0 }
    }

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
        // Anchor the column once per vertical run and keep it for the whole run, so a line shorter
        // than the column clamps the caret without re-anchoring it - coming back up returns to the
        // column, exactly like a real IDE.
        if (goalColumn == null) goalColumn = edit.caret - s[current]
        val column = goalColumn!!
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
            val d = kotlin.math.abs(textWidth(line.substring(0, i)) - (localX - textOriginX() + scrollX))
            if (d < bestDist) { bestDist = d; best = i }
        }
        return (s[row] + best).coerceIn(0, text.length)
    }

    private fun innerWidth(): Float = (bw - scaledPad() * 2f - GRIP_W - 2f - gutterWidth()).coerceAtLeast(1f)
    private fun scaledPad(): Float = PAD * contentScale
    private fun lineHeight(): Float = BrassFont.LINE * contentScale
    private fun textWidth(value: String): Float = BrassFont.width(this, value) * contentScale

    private fun gutterWidth(): Float {
        if (!lineNumbers) return 0f
        val digits = (text.count { it == '\n' } + 1).toString().length.coerceAtLeast(2)
        return GUTTER_PAD * 2 + BrassFont.width(this, "0".repeat(digits))
    }

    /** The x origin of the editable text area, after the left padding and any line-number gutter. */
    private fun textX(x: Int): Float = x + textOriginX()

    /** The widget-relative text origin (pad + gutter + a hair of breathing room). */
    private fun textOriginX(): Float = scaledPad() + gutterWidth() + (if (lineNumbers) GUTTER_GAP else 0f)

    /** The horizontal extent of the content: the viewport when wrapping, the widest line otherwise. */
    private fun contentWidth(width: Float): Float = if (wrap) width else {
        layout(width).maxOfOrNull { textWidth(it) } ?: 0f
    }

    /**
     * Tab / Shift+Tab: insert INDENT spaces at the caret, or indent/dedent every source line the
     * selection touches when it spans multiple lines. Operates on SOURCE lines (a soft-wrapped row is
     * part of its source line), like a real code editor.
     */
    private fun indentSelection(dedent: Boolean) {
        val indent = " ".repeat(INDENT)
        val src = sourceLineStarts()
        val text = this.text
        val multiLine = edit.hasSelection && text.substring(edit.selStart, edit.selEnd).contains('\n')
        if (!multiLine) {
            if (dedent) {
                val lineStart = src.getOrElse(sourceIndexFor(edit.caret)) { 0 }
                var n = 0
                while (n < INDENT && lineStart + n < edit.caret && text[lineStart + n] == ' ') n++
                if (n > 0) edit.replaceRange(lineStart, lineStart + n, "", edit.caret - n)
            } else {
                edit.insert(indent)
            }
            return
        }
        val first = sourceIndexFor(edit.selStart)
        val last = sourceIndexFor(edit.selEnd - 1).coerceAtLeast(first)
        val blockStart = src[first]
        val blockEnd = src.getOrElse(last + 1) { text.length }
        val block = text.substring(blockStart, blockEnd)
        val rebuilt = block.lineSequence().joinToString("\n") { line ->
            if (dedent) line.dropWhileIndent(INDENT) else indent + line
        }
        edit.replaceRange(blockStart, blockEnd, rebuilt, blockStart + rebuilt.length)
    }

    /** Remove up to [n] leading spaces, leaving any further indentation intact. */
    private fun String.dropWhileIndent(n: Int): String {
        var i = 0
        while (i < n && i < length && this[i] == ' ') i++
        return substring(i)
    }

    fun revealLine(number: Int) {
        val target = sourceLineStarts().getOrNull(number - 1) ?: return
        setCaretAndReveal(target)
    }

    /** Move the caret to [index] and scroll it into view (used by goto-definition and error jumps). */
    fun setCaretAndReveal(index: Int) {
        forgetColumn()
        edit.setCaret(index.coerceIn(0, text.length))
        revealCaret()
    }

    /** The caret's position in widget-local coordinates (top-left = 0,0), for overlays like autocomplete. */
    fun caretLocalPosition(): Pair<Float, Float> {
        if (text.isEmpty()) return textOriginX() to scaledPad()
        val width = innerWidth()
        val lines = layout(width)
        val s = lineStartsList(width)
        val row = lineOf(edit.caret, width)
        val column = (edit.caret - s.getOrElse(row) { 0 }).coerceAtLeast(0)
        val lineText = lines.getOrElse(row) { "" }
        val cx = textOriginX() + textWidth(lineText.take(column.coerceAtMost(lineText.length))) - scrollX
        val cy = scaledPad() - scrollY + row * lineHeight()
        return cx to cy
    }

    private fun revealCaret() {
        val width = innerWidth()
        val row = lineOf(edit.caret, width)
        val lineHeight = lineHeight()
        val top = row * lineHeight
        val viewport = bar.viewport
        if (top < scrollY) scrollY = top
        else if (top + lineHeight > scrollY + viewport) scrollY = top + lineHeight - viewport
        scrollY = bar.clamp(scrollY)

        // Reveal the caret horizontally in code mode (long lines scroll sideways).
        if (!wrap) {
            val s = lineStartsList(width)
            val column = (edit.caret - s.getOrElse(row) { 0 }).coerceAtLeast(0)
            val lineText = layout(width).getOrElse(row) { "" }
            val caretX = textWidth(lineText.take(column.coerceAtMost(lineText.length)))
            val hViewport = hBar.viewport
            if (caretX < scrollX) scrollX = caretX
            else if (caretX > scrollX + hViewport) scrollX = caretX - hViewport
            scrollX = hBar.clamp(scrollX)
        }
    }


    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        if (hoveredState && active) BrassCursor.request(BrassCursor.Kind.TEXT)
        // Ctrl+hover over a symbol that has a definition (the IDE sets hoverUnderline for exactly
        // that state) asks for the pointer cursor, so the "click me" affordance reads correctly.
        if (hoverUnderline != null) BrassCursor.request(BrassCursor.Kind.HAND)
        edit.clamp()

        val width = innerWidth()
        val lines = layout(width)
        val s = lineStartsList(width)
        val pad = scaledPad()
        val lineHeight = lineHeight()

        bar.viewport = h - pad * 2f
        bar.content = lines.size * lineHeight
        scrollY = bar.clamp(scrollY)

        // Horizontal scrolling only in code mode (wrap == false).
        if (!wrap) {
            hBar.viewport = (w - pad * 2f - GRIP_W - 2f - gutterWidth()).coerceAtLeast(1f)
            hBar.content = contentWidth(width)
            scrollX = hBar.clamp(scrollX)
        } else {
            scrollX = 0f
        }
        val scrollOffsetX = if (wrap) 0f else scrollX

        val showPlaceholder = text.isEmpty() && placeholder.isNotEmpty()
        val body = if (showPlaceholder) listOf(placeholder) else lines
        val tint = if (showPlaceholder) Colors.UI_TEXT_DARK else textColor

        // Ctrl+hover tracking: Elementa has no mouse-move hook, so poll the offset while Ctrl is held
        // (and fire a -1 sentinel when it leaves / the modifier is released) - cheap, and only while
        // Ctrl is actually down.
        if (active && hoveredState && UKeyboard.isCtrlKeyDown()) {
            val (mx, my) = getMousePosition()
            val offset = caretAt(mx - getLeft(), my - getTop())
            if (offset != lastHoverOffset) {
                lastHoverOffset = offset
                onHoverOffset?.invoke(offset)
            }
        } else if (lastHoverOffset != -1) {
            lastHoverOffset = -1
            onHoverOffset?.invoke(-1)
        }

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
                    paintSelection(m, row, line, s, x, ly, scrollOffsetX)
                }
                drawLine(m, line, s[row], textX(x) - scrollOffsetX, ly, tint, showPlaceholder)
            }
            ly += lineHeight
        }

        // A caret would be ambiguous next to a selection highlight, so it only shows for a plain
        // insertion point — the same rule the single-line field uses.
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
                    cx = textX(x) - scrollOffsetX + textWidth(lineText.take(column.coerceAtMost(lineText.length)))
                    cy = y + pad - scrollY + row * lineHeight
                }
                if (cy >= y && cy <= y + h - lineHeight) {
                    BrassPaint.rect(m, cx, cy, cx + 1f, cy + lineHeight, Colors.UI_ACCENT_BRIGHT)
                }
            }
        }

        // Ctrl+hover underline: a thin accent line under the glyphs of a symbol that has a definition.
        hoverUnderline?.let { range ->
            if (UKeyboard.isCtrlKeyDown()) {
                for (row in lines.indices) {
                    if (row >= s.size) break
                    val lineStart = s[row]
                    val lineEnd = lineStart + lines[row].length
                    val from = maxOf(range.first, lineStart)
                    val to = minOf(range.last + 1, lineEnd)
                    if (to <= from) continue
                    val ly = y + pad - scrollY + row * lineHeight
                    if (ly + lineHeight < y || ly > y + h) continue
                    val lx = textX(x) - scrollOffsetX
                    val sx = lx + textWidth(lines[row].take(from - lineStart))
                    val ex = lx + textWidth(lines[row].take(to - lineStart))
                    BrassPaint.rect(m, sx, ly + lineHeight - 1.5f, ex, ly + lineHeight - 0.5f, Colors.UI_ACCENT_BRIGHT)
                }
            }
        }

        clip.afterDraw(m)

        if (lineNumbers) paintGutter(m, x, y, w, h, s, scrollY)

        if (bar.scrollable) {
            val gx = x + w - GRIP_W - 2f
            val gy = y + pad + bar.gripTop(scrollY)
            BrassPaint.rect(m, gx, y + pad, gx + GRIP_W, y + h - pad, TRACK)
            net.swzo.brass.ui.kit.paint.BrassCard.grip(m, gx, gy, gx + GRIP_W, gy + bar.gripHeight())
        }

        // Horizontal scrollbar (code mode only), a thin track along the bottom.
        if (!wrap && hBar.scrollable) {
            val hy = y + h - pad - H_BAR_H
            val hx0 = x + pad + gutterWidth()
            val hx1 = x + w - pad - GRIP_W - 2f
            BrassPaint.rect(m, hx0, hy, hx1, hy + H_BAR_H, TRACK)
            val gx = hx0 + hBar.gripTop(scrollX)
            val gw = hBar.gripHeight().coerceAtLeast(4f)
            net.swzo.brass.ui.kit.paint.BrassCard.grip(m, gx, hy, gx + gw, hy + H_BAR_H)
        }
    }

    /**
     * The source-line-number gutter: numbers aligned with each visual row that begins a source line
     * (soft-wrapped continuation rows stay blank), markers (error lines) in danger red, and a hairline
     * divider before the text. The text already starts at textX(x) = x + pad + gutter, so this only
     * paints the strip on the left.
     */
    private fun paintGutter(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int, s: List<Int>, scrollY: Float) {
        val gw = gutterWidth()
        if (gw <= 0f) return
        val pad = scaledPad()
        val lineHeight = lineHeight()
        val left = x + pad
        val right = left + gw

        // Hairline divider between gutter and code.
        BrassPaint.rectSnapped(m, right, y.toFloat(), right + 1f, y + h.toFloat(), GUTTER_EDGE)

        val sourceStarts = sourceLineStarts()
        val viewport = h - pad * 2f
        val first = (scrollY / lineHeight).toInt().coerceAtLeast(0)
        val last = ((scrollY + viewport) / lineHeight).toInt().coerceAtMost(s.size)
        for (row in first until last) {
            if (row >= s.size) break
            val start = s[row]
            // Only rows that begin a source line carry a number.
            val sourceIndex = sourceIndexFor(start)
            if (sourceStarts.getOrNull(sourceIndex) != start) continue
            val number = sourceIndex + 1
            val ry = y + pad - scrollY + row * lineHeight
            if (ry + lineHeight < y || ry > y + h) continue
            val label = number.toString()
            BrassFont.draw(
                m, this, label,
                right - GUTTER_PAD - BrassFont.width(this, label),
                ry + 1f,
                if (number in markers) Colors.DANGER else Colors.UI_TEXT_DARK,
            )
        }
    }

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
        val lineStart = sourceLineStarts()[sourceIndex]
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

    private fun sourceIndexFor(start: Int): Int {
        val probe = sourceLineStarts().binarySearch(start)
        return if (probe >= 0) probe else -probe - 2
    }

    private fun paintSelection(m: UMatrixStack, row: Int, line: String, starts: List<Int>, x: Int, ly: Float, scrollX: Float = 0f) {
        val lineStart = starts.getOrElse(row) { return }
        val lineEnd = lineStart + line.length
        val from = maxOf(edit.selStart, lineStart)
        val to = minOf(edit.selEnd, lineEnd)
        if (to < from) return

        val origin = textX(x) - scrollX
        val sx = origin + textWidth(line.take((from - lineStart).coerceIn(0, line.length)))
        val ex = origin + textWidth(line.take((to - lineStart).coerceIn(0, line.length)))
        // A selection that runs *through* this line (rather than ending on it) covers the newline too,
        // so a sliver past the last glyph is what shows the line break is included.
        val tail = if (edit.selEnd > lineEnd) NEWLINE_SLIVER * contentScale else 0f
        if (ex + tail > sx) BrassPaint.rect(m, sx, ly, ex + tail, ly + lineHeight(), SELECTION)
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("text-area", "Text area", 220f, 70f) {
            BrassTextArea(placeholder = "Description")
        }

        fun heightForLines(lines: Int): Float = lines.coerceAtLeast(1) * BrassFont.LINE + PAD * 2

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val PAD = 5f
        private const val GRIP_W = 3f
        private const val GUTTER_PAD = 4f
        private const val GUTTER_GAP = 4f
        private const val H_BAR_H = 3f
        private const val BAR_GRAB = 3f
        private const val INDENT = 4

        private const val BLINK_MS = 500L

        private const val NEWLINE_SLIVER = 3f

        private val TRACK: Color get() = Colors.SCROLL_TRACK
        private val GUTTER_EDGE: Color get() = Colors.UI_INNER_BORDER

        private val SELECTION: Color
            get() = Color(Colors.UI_ACCENT.red, Colors.UI_ACCENT.green, Colors.UI_ACCENT.blue, 90)
    }
}
