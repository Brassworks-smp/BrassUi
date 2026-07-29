package net.swzo.brass.ui.kit.text

import gg.essential.universal.UDesktop
import gg.essential.universal.UKeyboard

/**
 * Anything that is a text field for the purposes of "is the user typing right now".
 *
 * ### Why this exists
 *
 * [net.swzo.brass.ui.BrassScreen] has to know whether a keystroke belongs to a focused field before it
 * treats it as navigation — Tab moves focus, Enter and Space activate the focused control, and all
 * three are characters or editing keys inside a field. That check used to name [BrassTextInput]
 * *concretely*, so when [BrassTextArea] arrived it was invisible to it: a focused text area had every
 * Enter swallowed as "activate the focused control" and every Space with it, which is exactly the bug
 * that made a multi-line field unable to produce a second line.
 *
 * A marker interface is the fix, and it is the right shape for the same reason it was the bug: the
 * screen's question is about a *category* of widget, not about one class, and any future field — a
 * search box, a code editor — joins by implementing this rather than by someone remembering to add a
 * branch two files away.
 */
interface BrassTextField {

    /** Whether this field currently holds focus and is therefore consuming keystrokes. */
    val focused: Boolean
}

/**
 * The caret, the selection, and every edit that acts on them — shared by the single-line
 * [BrassTextInput] and the multi-line [BrassTextArea].
 *
 * ### Why it is factored out
 *
 * These two widgets differ in exactly two things: how a string maps to pixels (one line scrolled
 * horizontally, versus wrapped lines scrolled vertically), and what the vertical keys mean. Everything
 * else — what shift+arrow does to the anchor, where a word boundary is, whether Backspace eats a
 * selection or a character, which modifier is word-wise on a Mac — is identical, and was written twice.
 *
 * It had already drifted. The text area had no selection at all, no clipboard, and no word-wise motion,
 * which its own class comment cheerfully documented as a known gap; so "select some text and copy it"
 * worked in one field and silently did nothing in the other. Sharing the model is what makes the two
 * fields the same field with different layout, which is what a user assumes they already are.
 *
 * ### What it does not own
 *
 * Layout. This works entirely in **character indices** and never measures anything, so it has no
 * opinion about where index 12 is on screen. Hit-testing, scrolling and painting stay with the widget,
 * which is the half that genuinely differs.
 */
class BrassTextEdit(
    /**
     * Whether newlines are legal in this field.
     *
     * Only [paste] consults it — a single-line field flattens pasted newlines to spaces rather than
     * silently swallowing half a clipboard, and a multi-line one keeps them.
     */
    private val multiline: Boolean,
    /** The field's current contents. */
    private val read: () -> String,
    /** Replace the contents. The owner is responsible for notifying listeners and invalidating caches. */
    private val write: (String) -> Unit,
    /** Called after any change to the caret, the selection or the text — for resetting a blink. */
    private val onTouch: () -> Unit = {},
) {

    private val text: String get() = read()

    /** Moving end of the selection, and the insertion point. 0..length. */
    var caret: Int = read().length
        private set

    /** Fixed end of the selection. Equal to [caret] when nothing is selected. */
    var anchor: Int = caret
        private set

    val selStart: Int get() = minOf(caret, anchor)
    val selEnd: Int get() = maxOf(caret, anchor)
    val hasSelection: Boolean get() = caret != anchor

    /** The selected substring, or empty when nothing is selected. */
    val selectedText: String get() = if (hasSelection) text.substring(selStart, selEnd) else ""

    // ---- caret and selection ---------------------------------------------------------------------

    /**
     * Move the caret to [index]. When [extend] is false the anchor collapses onto it (clearing any
     * selection); when true the anchor stays put and the selection grows or shrinks.
     *
     * Every motion goes through here, so there is exactly one place that decides what happens to the
     * anchor rather than each key handler remembering to update it.
     */
    fun moveTo(index: Int, extend: Boolean) {
        caret = index.coerceIn(0, text.length)
        if (!extend) anchor = caret
        onTouch()
    }

    fun selectAll() {
        anchor = 0
        caret = text.length
        onTouch()
    }

    fun selectWordAt(index: Int) {
        val i = index.coerceIn(0, text.length)
        anchor = wordStart(i)
        caret = wordEnd(i)
        onTouch()
    }

    /** Place the caret without disturbing the anchor — for an owner restoring state after an edit. */
    fun setCaret(index: Int, alsoAnchor: Boolean = true) {
        caret = index.coerceIn(0, text.length)
        if (alsoAnchor) anchor = caret
        onTouch()
    }

    /** Force both indices back into range for the current text. */
    fun clamp() {
        caret = caret.coerceIn(0, text.length)
        anchor = anchor.coerceIn(0, text.length)
    }

    // ---- editing -----------------------------------------------------------------------------------

    /** Replace the selection (or insert at the caret) with [s]. */
    fun insert(s: String) {
        val start = selStart
        replace(text.substring(0, start) + s + text.substring(selEnd), start + s.length)
    }

    /** Drop the selection if there is one. Returns whether anything was removed. */
    fun deleteSelection(): Boolean {
        if (!hasSelection) return false
        val start = selStart
        replace(text.removeRange(start, selEnd), start)
        return true
    }

    /**
     * Backspace/Delete. A selection is removed whole; otherwise one character goes, or a whole word
     * when the word-wise modifier is held — matching the arrow keys' granularity.
     */
    fun erase(forward: Boolean) {
        if (deleteSelection()) return
        val to = if (wordWise()) nextWordBoundary(forward) else if (forward) caret + 1 else caret - 1
        val a = minOf(caret, to).coerceIn(0, text.length)
        val b = maxOf(caret, to).coerceIn(0, text.length)
        if (a == b) return
        replace(text.removeRange(a, b), a)
    }

    fun copy() {
        if (hasSelection) runCatching { UDesktop.setClipboardString(selectedText) }
    }

    fun cut() {
        copy()
        deleteSelection()
    }

    /**
     * Paste, flattening newlines in a single-line field.
     *
     * Flattened to spaces rather than dropped: pasting a two-line address into a one-line field should
     * give you the whole address to fix up, not the first line and a silent loss of the rest.
     */
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
        write(next)
        caret = newCaret.coerceIn(0, next.length)
        anchor = caret
        onTouch()
    }

    // ---- motion ------------------------------------------------------------------------------------

    /** True when the platform's word-wise modifier is held: Alt on macOS, Ctrl elsewhere. */
    fun wordWise(): Boolean = if (UDesktop.isMac) UKeyboard.isAltKeyDown() else UKeyboard.isCtrlKeyDown()

    /** True when the platform's line-wise modifier is held. Only macOS has one (Cmd). */
    fun lineWise(): Boolean = UDesktop.isMac && UKeyboard.isCtrlKeyDown()

    /**
     * Horizontal arrow motion, shared verbatim.
     *
     * [lineHome] and [lineEnd] are what the owner considers the ends of the current line — the whole
     * string for a single-line field, the enclosing line for a multi-line one — which is the only part
     * of this that differs between the two.
     */
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

    /**
     * The next word boundary from the caret: skip any run of separators, then the run of word
     * characters beyond it — so repeated presses step word by word rather than stopping on every space.
     */
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
}
