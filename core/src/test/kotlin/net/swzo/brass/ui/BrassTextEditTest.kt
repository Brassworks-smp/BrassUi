package net.swzo.brass.ui

import net.swzo.brass.ui.kit.text.BrassTextEdit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrassTextEditTest {

    private fun edit(initial: String = ""): Pair<BrassTextEdit, StringBuilder> {
        val buf = StringBuilder(initial)
        val edit = BrassTextEdit(multiline = true, read = { buf.toString() }, write = { buf.setLength(0); buf.append(it) })
        edit.setCaret(buf.length)
        return edit to buf
    }

    @Test
    fun `typing groups into one undo step`() {
        val (e, buf) = edit()
        e.insert("he")
        e.insert("llo")   // still within the grouping window
        assertEquals("hello", buf.toString())

        assertTrue(e.canUndo())
        e.undo()
        assertEquals("", buf.toString())
        assertFalse(e.canUndo())

        e.redo()
        assertEquals("hello", buf.toString())
    }

    @Test
    fun `edits across a caret move make separate undo steps`() {
        val (e, buf) = edit()
        e.insert("ab")
        e.moveTo(e.caret, extend = false)   // a move closes the typing session
        e.insert("cd")
        assertEquals("abcd", buf.toString())

        e.undo()
        assertEquals("ab", buf.toString())
        e.undo()
        assertEquals("", buf.toString())
        assertFalse(e.canUndo())
    }

    @Test
    fun `replaceRange and insert are undoable`() {
        val (e, buf) = edit()
        e.insert("hello world")
        e.moveTo(5, extend = false)
        e.insert("X")
        e.replaceRange(6, 8, "there")
        assertEquals("helloXthereorld", buf.toString())
        // "X" and the replace happened in one typing session -> one undo step back to the move.
        e.undo()
        assertEquals("hello world", buf.toString())
        e.undo()
        assertEquals("", buf.toString())
        assertFalse(e.canUndo())
    }

    @Test
    fun `deleteLine removes the whole source line`() {
        val (e, buf) = edit("one\ntwo\nthree")
        e.moveTo(5, extend = false)          // inside "two"
        e.deleteLine()
        assertEquals("one\nthree", buf.toString())
        assertEquals(4, e.caret)             // where "two" started

        // The last line deletes its preceding newline too.
        e.moveTo(buf.length, extend = false) // end of "three"
        e.deleteLine()
        assertEquals("one", buf.toString())

        // A lone line just empties.
        e.moveTo(0, extend = false)
        e.deleteLine()
        assertEquals("", buf.toString())
    }

    @Test
    fun `deleteWord removes the word before and after the caret`() {
        val (e, buf) = edit("alpha beta gamma")
        e.moveTo(6, extend = false)          // on the first char of "beta"
        e.deleteWord(forward = true)         // eats "beta"
        assertEquals("alpha  gamma", buf.toString())

        e.moveTo(7, extend = false)          // before "gamma"
        e.deleteWord(forward = false)        // eats the spaces + the word behind
        assertEquals("gamma", buf.toString())
    }

    @Test
    fun `deleteWord from inside a word eats to the word boundary`() {
        val (e, buf) = edit("alpha beta")
        e.moveTo(3, extend = false)          // middle of "alpha"
        e.deleteWord(forward = true)         // eats the rest of "alpha"
        assertEquals("alp beta", buf.toString())

        e.moveTo(0, extend = false)
        e.deleteWord(forward = false)        // nothing behind the caret
        assertEquals("alp beta", buf.toString())
    }

    @Test
    fun `selectRange selects the exact span`() {
        val (e, buf) = edit("local function f()\nend")
        e.moveTo(buf.length, extend = false)
        e.selectRange(0, 6)
        assertEquals("local ", e.selectedText)
    }

    @Test
    fun `selectLineAt selects the whole source line`() {
        val (e, buf) = edit("alpha\nbeta gamma\ndelta")
        e.moveTo(9, extend = false)           // inside "beta gamma"
        e.selectLineAt(e.caret)
        assertEquals("beta gamma", e.selectedText)
        e.selectLineAt(buf.length)            // last line (no trailing newline)
        assertEquals("delta", e.selectedText)
    }

    @Test
    fun `deleteToLineStart removes everything before the caret on the line`() {
        val (e, buf) = edit("local a = 1\nlocal b = 2")
        e.moveTo(18, extend = false)          // on the 'b' of "local b = 2"
        e.deleteToLineStart()
        assertEquals("local a = 1\nb = 2", buf.toString())
        assertEquals(12, e.caret)             // start of the second line

        // At the very start of a line it is a no-op.
        e.deleteToLineStart()
        assertEquals("local a = 1\nb = 2", buf.toString())
    }
}
