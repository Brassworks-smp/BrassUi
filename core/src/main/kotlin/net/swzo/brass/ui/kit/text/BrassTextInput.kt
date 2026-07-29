package net.swzo.brass.ui.kit.text

import gg.essential.universal.UDesktop
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.platform.BrassCursor
import org.lwjgl.glfw.GLFW
import java.awt.Color
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A text field with the editing behaviour people expect from a real one: click or drag to select,
 * double-click to select a word, shift+arrows to extend a selection, word- and line-wise motion with
 * the platform's modifiers, and cut/copy/paste/select-all.
 *
 * ### Selection model
 *
 * Owned by [BrassTextEdit], shared with the multi-line [BrassTextArea]: caret and anchor, word
 * boundaries, the clipboard, and every edit. What stays here is the single-line half — a horizontal
 * scroll offset, an advance table for hit-testing, and the rule that vertical keys mean "the ends".
 *
 * ### Platform keys
 *
 * MC maps Cmd to "ctrl" on macOS, so [UKeyboard.isCtrlKeyDown] is the platform's primary modifier
 * either way and Cmd+C/V/X/A work unchanged. The arrow keys differ though, and the mapping follows
 * each platform's convention:
 *
 * | | macOS | elsewhere |
 * |---|---|---|
 * | word-wise | Alt+arrow | Ctrl+arrow |
 * | line-wise | Cmd+arrow | Home / End |
 *
 * Home/End and Up/Down always go to the start/end, since this is a single-line field.
 */
class BrassTextInput(
    // NOTE: this parameter must NOT be called `text`. A constructor parameter stays in scope through
    // the whole class body and *shadows* a member property of the same name inside `init` blocks - so
    // `text` read from the key handler below resolved to this immutable initial value, not the live
    // property. Every keystroke then rebuilt the string from the original text, which is why typing
    // appeared to overwrite the last character forever and eventually ran the caret off the end.
    initial: String = "",
    private val placeholder: String = "",
    private val onChange: (String) -> Unit = {},
) : BrassWidget(BrassAccent.DEFAULT), BrassValue<String>, BrassFocusable, BrassTextField {

    private val holder = BrassValueHolder(initial)

    override var value: String
        get() = holder.value
        set(v) { setInternal(v, v.length, silent = false) }

    override fun setSilently(value: String) = setInternal(value, caret, silent = true)
    override fun onChange(listener: (String) -> Unit) = holder.onChange(listener)
    override fun bind(state: BrassState<String>) = holder.bind(this, state)

    /** The field's contents. Alias of [value]. */
    var text: String
        get() = holder.value
        private set(v) { holder.setSilently(v) }

    /** Replace the contents and re-clamp every index, notifying unless [silent]. */
    private fun setInternal(next: String, newCaret: Int, silent: Boolean) {
        if (next == holder.value) return
        if (silent) holder.setSilently(next) else holder.value = next
        edit.setCaret(newCaret)
        scroll = scroll.coerceIn(0, next.length)
        resetBlink()
    }

    /**
     * Replace the contents programmatically **without** firing [onChange] - for syncing this field from
     * another control (e.g. the colour picker writing back the hex as you scrub). Leaves the caret where
     * it sensibly can, and never re-enters the change callback that a two-way binding would loop through.
     */
    fun setTextSilently(value: String) = setSilently(value)

    /**
     * Called when Enter is pressed, with the field's current text.
     *
     * Null - the default - keeps the original behaviour: Enter drops focus, the right thing for a form
     * field the user has finished filling in. Set it for a **send-on-Enter** field - a chat box or a
     * command line - where Enter submits and the field stays focused and ready for the next line. The
     * callback is responsible for clearing the field if it wants to; that is not assumed, because a
     * command line that re-runs the last entry does not.
     */
    var onSubmit: ((String) -> Unit)? = null

    /** The caret, the selection and every edit - see [BrassTextEdit]. */
    private val edit = BrassTextEdit(
        multiline = false,
        read = { holder.value },
        write = { holder.value = it },
        onTouch = { resetBlink() },
    )

    private val caret: Int get() = edit.caret

    /** First visible character - the field scrolls horizontally to keep the caret in view. */
    private var scroll: Int = 0

    override var focused = false
        private set

    private var caretLitAt = System.currentTimeMillis()

    /**
     * Set while the mouse is down inside this field. Elementa broadcasts drags to the whole tree, so
     * without this every field on screen would select text when you dragged anywhere at all (the same
     * gate BrassSlider needs for scrubbing).
     */
    private var selecting = false

    private var lastClickAt = 0L

    init {
        onMouseClick { e ->
            if (!active || e.mouseButton != 0) return@onMouseClick
            grabWindowFocus()
            val index = caretForX(e.relativeX)
            val now = System.currentTimeMillis()

            when {
                // double-click selects the word under the cursor
                now - lastClickAt < DOUBLE_CLICK_MS -> edit.selectWordAt(index)
                // shift+click extends from the existing anchor, like every other text field
                UKeyboard.isShiftKeyDown() -> edit.moveTo(index, extend = true)
                else -> edit.moveTo(index, extend = false)
            }

            lastClickAt = now
            selecting = true
            resetBlink()
        }

        onMouseDrag { mx, _, button ->
            if (!selecting || button != 0) return@onMouseDrag
            edit.moveTo(caretForX(mx), extend = true)
        }

        onMouseRelease { selecting = false }

        onFocus { focused = true; resetBlink() }
        onFocusLost { focused = false; selecting = false; edit.moveTo(caret, extend = false) }

        holder.onChange(onChange)

        onKeyType { typedChar, keyCode ->
            // only the focused field edits - Elementa hands key events to the whole tree otherwise
            if (!active || !focused) return@onKeyType
            // Re-clamp before every edit: caret/anchor/scroll are indices into `text` but are also
            // touched from click handling and from drawing, and an interrupted frame can leave them
            // stale. An out-of-range index here means substring/removeRange throws.
            clampIndices()

            val shift = UKeyboard.isShiftKeyDown()

            when {
                UKeyboard.isKeyComboCtrlA(keyCode) -> edit.selectAll()
                UKeyboard.isKeyComboCtrlC(keyCode) -> edit.copy()
                UKeyboard.isKeyComboCtrlX(keyCode) -> edit.cut()
                UKeyboard.isKeyComboCtrlV(keyCode) -> edit.paste()

                // A single-line field's "line" is the whole string, which is what makes the shared
                // arrow handling collapse to the right thing here.
                keyCode == GLFW.GLFW_KEY_LEFT ->
                    edit.arrow(forward = false, shift = shift, lineHome = { 0 }, lineEnd = { text.length })
                keyCode == GLFW.GLFW_KEY_RIGHT ->
                    edit.arrow(forward = true, shift = shift, lineHome = { 0 }, lineEnd = { text.length })

                // single-line field: vertical motion and Home/End all mean "the ends"
                keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_UP -> edit.moveTo(0, shift)
                keyCode == GLFW.GLFW_KEY_END || keyCode == GLFW.GLFW_KEY_DOWN -> edit.moveTo(text.length, shift)

                keyCode == GLFW.GLFW_KEY_BACKSPACE -> edit.erase(forward = false)
                keyCode == GLFW.GLFW_KEY_DELETE -> edit.erase(forward = true)

                keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER -> {
                    val submit = onSubmit
                    if (submit != null) submit(text) else loseFocus()
                }

                !typedChar.isISOControl() && typedChar.code >= 32 -> edit.insert(typedChar.toString())
            }
        }
    }

    // ---- selection ------------------------------------------------------------------------------

    private val selStart: Int get() = edit.selStart
    private val selEnd: Int get() = edit.selEnd
    private val hasSelection: Boolean get() = edit.hasSelection

    /** The selected substring, or empty when nothing is selected. */
    val selectedText: String get() = edit.selectedText

    fun selectAll() = edit.selectAll()

    // ---- bookkeeping ----------------------------------------------------------------------------

    /** Force every index back into range for the current [text]. */
    private fun clampIndices() {
        edit.clamp()
        scroll = scroll.coerceIn(0, text.length)
        if (scroll > caret) scroll = caret
    }

    /** Keep the caret solid for a moment after any edit, so it doesn't blink out mid-keystroke. */
    private fun resetBlink() { caretLitAt = System.currentTimeMillis() }

    /**
     * The caret index nearest a click at [localX] pixels from the field's left edge.
     *
     * Indices are clamped first: if [scroll] were stale and past the end, `scroll..text.length` would
     * be an *empty* range, the loop would never run, and this would return the out-of-range starting
     * value - putting the caret beyond the text and making the next Backspace throw.
     */
    private fun caretForX(localX: Float): Int {
        clampIndices()
        val target = localX - PAD_L + advanceTo(scroll)
        // Binary search the cumulative advance table for the nearest character boundary. The previous
        // version measured `text.substring(scroll, i)` for every i - quadratic in the field's length,
        // with a String allocation per step - so clicking into a pasted URL or JSON blob did millions
        // of character measurements and stalled the frame.
        val advances = advances()
        var lo = 0
        var hi = text.length
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (advances[mid] < target) lo = mid + 1 else hi = mid
        }
        // `lo` is the first boundary at or past the target; the one before it may be nearer.
        val before = (lo - 1).coerceAtLeast(0)
        val best = if (lo > 0 && kotlin.math.abs(advances[before] - target) <= kotlin.math.abs(advances[lo] - target)) before else lo
        return best.coerceIn(0, text.length)
    }

    // ---- advance table ---------------------------------------------------------------------------

    /**
     * `advances[i]` is the pixel width of `text.take(i)`, so the width of any substring is one
     * subtraction and any caret position is a binary search.
     *
     * Rebuilt only when the text actually changes. Everything that used to walk the string measuring
     * a fresh substring per step - hit-testing a click, scrolling the view to keep the caret visible,
     * placing the selection highlight, positioning the caret - now reads it directly.
     */
    private var advanceTable: FloatArray = FloatArray(1)
    private var advancesFor: String? = null

    private fun advances(): FloatArray {
        if (advancesFor === text) return advanceTable
        val out = FloatArray(text.length + 1)
        var acc = 0f
        for (i in text.indices) {
            acc += BrassFont.width(this, text[i].toString())
            out[i + 1] = acc
        }
        advanceTable = out
        advancesFor = text
        return out
    }

    /** Width of `text.take(i)`. */
    private fun advanceTo(i: Int): Float = advances()[i.coerceIn(0, text.length)]

    /** Width of `text.substring(from, to)`. */
    private fun advanceBetween(from: Int, to: Int): Float = advanceTo(to) - advanceTo(from)

    // ---- render ---------------------------------------------------------------------------------

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        if (hoveredState && active) BrassCursor.request(BrassCursor.Kind.TEXT)
        clampIndices()
        val avail = (w - PAD_L - PAD_R).coerceAtLeast(0).toFloat()

        // scroll the view so the caret stays inside the field
        if (caret < scroll) scroll = caret
        while (scroll < caret && advanceBetween(scroll, caret) > avail) scroll++
        // don't leave dead space on the right when the tail would fit
        while (scroll > 0 && advanceBetween(scroll - 1, text.length) <= avail) scroll--
        scroll = scroll.coerceIn(0, text.length)

        val showAll = text.isEmpty()
        val body = if (showAll) placeholder else text.substring(scroll)
        val visible = clipToWidth(body, avail)
        val color = if (showAll) Colors.UI_TEXT_DARK else textColor

        val tx = (x + PAD_L).toFloat()
        val ty = y + (h - BrassFont.LINE) / 2 + 1
        val clipRight = (x + w - PAD_R).toFloat()

        // selection sits behind the glyphs, clipped to the visible window of the string
        if (hasSelection && !showAll) {
            val from = maxOf(selStart, scroll)
            val to = maxOf(selEnd, scroll)
            if (to > from) {
                val sx = (tx + advanceBetween(scroll, from)).coerceAtMost(clipRight)
                val ex = (tx + advanceBetween(scroll, to)).coerceAtMost(clipRight)
                if (ex > sx) BrassPaint.rect(m, sx, (y + 2).toFloat(), ex, (y + h - 2).toFloat(), SELECTION)
            }
        }

        BrassFont.draw(m, this, visible, tx, ty.toFloat(), color, true)

        // a caret would be ambiguous next to a selection highlight, so it only shows for a plain
        // insertion point
        if (focused && !hasSelection) {
            // solid right after an edit, blinking once idle
            val since = System.currentTimeMillis() - caretLitAt
            val lit = since < 500 || (since / 500) % 2 == 0L
            if (lit) {
                val cx = tx + advanceBetween(scroll, caret.coerceAtLeast(scroll))
                val cxc = cx.coerceAtMost(clipRight)
                BrassPaint.rect(m, cxc, (y + 3).toFloat(), cxc + 1f, (y + h - 3).toFloat(), Colors.UI_ACCENT_BRIGHT)
            }
        }
    }

    /** Longest prefix of [s] that fits in [avail] pixels. */
    private fun clipToWidth(s: String, avail: Float): String {
        if (BrassFont.width(this, s) <= avail) return s
        var t = s
        while (t.isNotEmpty() && BrassFont.width(this, t) > avail) t = t.dropLast(1)
        return t
    }

    companion object : BrassDemoSource {

        /**
         * Focused, typed into, and corrected with a backspace.
         *
         * Typed a character at a time so the caret advances and blinks the way it does under a real
         * hand; the backspace is there because a text field that only ever grows hides the one
         * behaviour people actually check for.
         */
        override fun demo() = BrassDemo("text-input", "Text input", 190f, 18f) {
            BrassTextInput(placeholder = "Server name")
        }

        // ---- widget internals ------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val PAD_L = 5
        private const val PAD_R = 5
        private const val DOUBLE_CLICK_MS = BrassMetrics.DOUBLE_CLICK_MS

        /** Selection wash: the accent at low alpha, so glyphs stay legible on top of it. */
        private val SELECTION: Color = Color(Colors.UI_ACCENT.red, Colors.UI_ACCENT.green, Colors.UI_ACCENT.blue, 90)
    }
}
