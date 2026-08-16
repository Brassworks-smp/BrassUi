package net.swzo.brass.ui.kit.text

import gg.essential.universal.UDesktop
import gg.essential.universal.UKeyboard
import java.util.ArrayDeque

/**
 * Anything that is a text field for the purposes of "is the user typing right now".
 * ### Why this exists
 * [net.swzo.brass.ui.BrassScreen] has to know whether a keystroke belongs to a focused field before it
 * treats it as navigation — Tab moves focus, Enter and Space activate the focused control, and all
 * three are characters or editing keys inside a field. That check used to name [BrassTextInput]
 * *concretely*, so when [BrassTextArea] arrived it was invisible to it: a focused text area had every
 * Enter swallowed as "activate the focused control" and every Space with it, which is exactly the bug
 * that made a multi-line field unable to produce a second line.
 * A marker interface is the fix, and it is the right shape for the same reason it was the bug: the
 * screen's question is about a *category* of widget, not about one class, and any future field — a
 * search box, a code editor — joins by implementing this rather than by someone remembering to add a
 * branch two files away.
 */
interface BrassTextField {

    val focused: Boolean
}

/**
 * The caret, the selection, and every edit that acts on them — shared by the single-line
 * [BrassTextInput] and the multi-line [BrassTextArea].
 * ### Why it is factored out
 * These two widgets differ in exactly two things: how a string maps to pixels (one line scrolled
 * horizontally, versus wrapped lines scrolled vertically), and what the vertical keys mean. Everything
 * else — what shift+arrow does to the anchor, where a word boundary is, whether Backspace eats a
 * selection or a character, which modifier is word-wise on a Mac — is identical, and was written twice.
 * It had already drifted. The text area had no selection at all, no clipboard, and no word-wise motion,
 * which its own class comment cheerfully documented as a known gap; so "select some text and copy it"
 * worked in one field and silently did nothing in the other. Sharing the model is what makes the two
 * fields the same field with different layout, which is what a user assumes they already are.
 * ### What it does not own
 * Layout. This works entirely in **character indices** and never measures anything, so it has no
 * opinion about where index 12 is on screen. Hit-testing, scrolling and painting stay with the widget,
 * which is the half that genuinely differs.
 */
class BrassTextEdit(
    private val multiline: Boolean,
    private val read: () -> String,
    private val write: (String) -> Unit,
    private val onTouch: () -> Unit = {},
) {

    private val text: String get() = read()

    var caret: Int = read().length
        private set

    var anchor: Int = caret
        private set

    val selStart: Int get() = minOf(caret, anchor)
    val selEnd: Int get() = maxOf(caret, anchor)
    val hasSelection: Boolean get() = caret != anchor

    val selectedText: String get() = if (hasSelection) text.substring(selStart, selEnd) else ""

    // ---- undo / redo -------------------------------------------------------
    // Undo is grouped by *typing session*: consecutive edits within GROUP_NS of each other collapse
    // into one undo step (VS Code style), so Ctrl+Z unwinds a word or a pasted blob instead of one
    // keystroke. A pause, a selection move, or a paste-after-a-pause opens a new session. The first
    // undo of the very first session works because closeSession() pushes the open group first.

    private data class State(val text: String, val caret: Int, val anchor: Int)

    private val undoStack = ArrayDeque<State>()
    private val redoStack = ArrayDeque<State>()

    private fun push(stack: ArrayDeque<State>, state: State) {
        stack.addLast(state)
        while (stack.size > MAX_UNDO) stack.removeFirst()   // bounded: full-text snapshots must not leak
    }

    private var groupBefore: State? = null
    private var lastEditAt = 0L

    fun canUndo(): Boolean = groupBefore != null || undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        closeSession()
        if (undoStack.isEmpty()) return
        val state = undoStack.removeLast()
        push(redoStack, State(text, caret, anchor))
        restore(state)
    }

    fun redo() {
        closeSession()
        if (redoStack.isEmpty()) return
        val state = redoStack.removeLast()
        push(undoStack, State(text, caret, anchor))
        restore(state)
    }

    private fun closeSession() {
        groupBefore?.let {
            push(undoStack, it)
            groupBefore = null
        }
    }

    private fun restore(state: State) {
        write(state.text)
        caret = state.caret.coerceIn(0, text.length)
        anchor = state.anchor.coerceIn(0, text.length)
        onTouch()
    }

    /** Begin (or continue) the current typing session; called once per edit, before it is applied. */
    private fun noteEdit() {
        val now = System.nanoTime()
        // Continuous only while a session is actually open AND we're within the window - a caret move
        // (which closes the session) always starts a fresh undo group even if the next keystroke is fast.
        val continuous = groupBefore != null && lastEditAt != 0L && now - lastEditAt < GROUP_NS
        if (!continuous) {
            closeSession()
            groupBefore = State(text, caret, anchor)
            redoStack.clear()
        }
        lastEditAt = now
    }


    fun moveTo(index: Int, extend: Boolean) {
        caret = index.coerceIn(0, text.length)
        if (!extend) {
            anchor = caret
            // A deliberate caret move breaks the typing group (undo starts a fresh step after it).
            closeSession()
        }
        onTouch()
    }

    fun selectAll() {
        closeSession()
        anchor = 0
        caret = text.length
        onTouch()
    }

    fun selectWordAt(index: Int) {
        closeSession()
        val i = index.coerceIn(0, text.length)
        anchor = wordStart(i)
        caret = wordEnd(i)
        onTouch()
    }

    /** Select the whole source line containing [index] (triple-click). */
    fun selectLineAt(index: Int) {
        closeSession()
        val i = index.coerceIn(0, text.length)
        val start = lineStartOf(i)
        val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        anchor = start
        caret = end
        onTouch()
    }

    /** Select the exact character range [start]..[end] (used to reveal a whole function body). */
    fun selectRange(start: Int, end: Int) {
        closeSession()
        anchor = start.coerceIn(0, text.length)
        caret = end.coerceIn(0, text.length)
        onTouch()
    }

    fun setCaret(index: Int, alsoAnchor: Boolean = true) {
        caret = index.coerceIn(0, text.length)
        if (alsoAnchor) anchor = caret
        closeSession()
        onTouch()
    }

    fun clamp() {
        caret = caret.coerceIn(0, text.length)
        anchor = anchor.coerceIn(0, text.length)
    }


    fun insert(s: String) {
        val start = selStart
        replace(text.substring(0, start) + s + text.substring(selEnd), start + s.length)
    }

    /** Replace [start]..[end] with [replacement] and move the caret to [newCaret]. Undoable. */
    fun replaceRange(start: Int, end: Int, replacement: String, newCaret: Int = start + replacement.length) {
        val a = start.coerceIn(0, text.length)
        val b = end.coerceIn(a, text.length)
        replace(text.substring(0, a) + replacement + text.substring(b), newCaret.coerceIn(0, (text.length - (b - a) + replacement.length).coerceAtLeast(0)))
    }

    fun deleteSelection(): Boolean {
        if (!hasSelection) return false
        val start = selStart
        replace(text.removeRange(start, selEnd), start)
        return true
    }

    fun erase(forward: Boolean) {
        if (deleteSelection()) return
        val to = if (wordWise()) nextWordBoundary(forward) else if (forward) caret + 1 else caret - 1
        val a = minOf(caret, to).coerceIn(0, text.length)
        val b = maxOf(caret, to).coerceIn(0, text.length)
        if (a == b) return
        replace(text.removeRange(a, b), a)
    }

    /**
     * Delete the whole source line the caret is on (the line plus its trailing newline; for the last
     * line, the newline that precedes it). A middle line "a\nb\nc" with the caret on b leaves "a\nc".
     */
    fun deleteLine() {
        if (text.isEmpty()) return
        val start = lineStartOf(caret)
        val nl = text.indexOf('\n', start)
        if (nl >= 0) {
            replace(text.removeRange(start, nl + 1), start)
        } else {
            // Last line: remove it together with the newline that precedes it (a lone first line
            // simply empties). The caret lands where the line used to start.
            val from = if (start > 0) start - 1 else start
            replace(text.removeRange(from, text.length), from)
        }
    }

    /** Delete a whole word: forward eats the word after the caret, backward eats the word before it. */
    fun deleteWord(forward: Boolean) {
        if (deleteSelection()) return
        val to = nextWordBoundary(forward)
        val a = minOf(caret, to).coerceIn(0, text.length)
        val b = maxOf(caret, to).coerceIn(0, text.length)
        if (a == b) return
        replace(text.removeRange(a, b), a)
    }

    /** Delete EVERYTHING before the caret on the current source line (Ctrl+Backspace), not a word. */
    fun deleteToLineStart() {
        if (deleteSelection()) return
        val start = lineStartOf(caret)
        if (start == caret) return
        replace(text.removeRange(start, caret), start)
    }

    private fun lineStartOf(index: Int): Int {
        var i = index.coerceIn(0, text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    fun copy() {
        if (hasSelection) runCatching { UDesktop.setClipboardString(selectedText) }
    }

    fun cut() {
        copy()
        deleteSelection()
    }

    fun paste() {
        val clip = runCatching { UDesktop.getClipboardString() }.getOrNull() ?: return
        val clean = if (multiline) {
            clip.replace("\r\n", "\n").replace('\r', '\n').filter { it == '\n' || !it.isISOControl() }
        } else {
            clip.replace('\n', ' ').replace('\r', ' ').filter { !it.isISOControl() }
        }
        if (clean.isNotEmpty()) insert(clean)
    }

    private fun replace(next: String, newCaret: Int) {
        noteEdit()
        write(next)
        caret = newCaret.coerceIn(0, next.length)
        anchor = caret
        onTouch()
    }


    fun wordWise(): Boolean = if (UDesktop.isMac) UKeyboard.isAltKeyDown() else UKeyboard.isCtrlKeyDown()

    fun lineWise(): Boolean = UDesktop.isMac && UKeyboard.isCtrlKeyDown()

    fun arrow(forward: Boolean, shift: Boolean, lineHome: () -> Int, lineEnd: () -> Int) {
        // An unshifted arrow with a selection collapses to that edge rather than moving — standard
        // behaviour, and the reason this cannot just be `caret ± 1`.
        if (!shift && hasSelection && !lineWise() && !wordWise()) {
            moveTo(if (forward) selEnd else selStart, extend = false)
            return
        }
        val target = when {
            lineWise() -> if (forward) lineEnd() else lineHome()
            wordWise() -> nextWordBoundary(forward)
            else -> caret + if (forward) 1 else -1
        }
        moveTo(target, extend = shift)
    }

    fun nextWordBoundary(forward: Boolean): Int {
        var i = caret.coerceIn(0, text.length)
        if (forward) {
            while (i < text.length && !text[i].isWordChar()) i++
            while (i < text.length && text[i].isWordChar()) i++
        } else {
            while (i > 0 && !text[i - 1].isWordChar()) i--
            while (i > 0 && text[i - 1].isWordChar()) i--
        }
        return i
    }

    fun wordStart(index: Int): Int {
        var i = index.coerceIn(0, text.length)
        while (i > 0 && text[i - 1].isWordChar()) i--
        return i
    }

    fun wordEnd(index: Int): Int {
        var i = index.coerceIn(0, text.length)
        while (i < text.length && text[i].isWordChar()) i++
        return i
    }

    private fun Char.isWordChar() = isLetterOrDigit() || this == '_'

    private companion object {
        /** Edits within this window collapse into one undo step. */
        const val GROUP_NS = 900_000_000L

        /** Undo/redo history is capped so full-text snapshots never grow without bound. */
        const val MAX_UNDO = 100
    }
}
