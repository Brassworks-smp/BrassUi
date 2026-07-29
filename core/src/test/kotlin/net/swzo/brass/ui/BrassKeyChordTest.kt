package net.swzo.brass.ui

import net.swzo.brass.ui.kit.input.BrassKeyChord
import org.lwjgl.glfw.GLFW
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A binding's *name* and its equality are the two things every config screen depends on — the first
 * is what the user reads, the second is how a conflict is detected.
 */
class BrassKeyChordTest {

    @Test
    fun `an unbound chord says so`() {
        assertFalse(BrassKeyChord.NONE.bound)
        assertEquals("Unbound", BrassKeyChord.NONE.display)
    }

    @Test
    fun `modifiers print in a fixed order`() {
        // Whichever order they were actually pressed in, the same chord must always read the same.
        val chord = BrassKeyChord(GLFW.GLFW_KEY_S, ctrl = true, shift = true, alt = true)
        assertEquals("Ctrl+Shift+Alt+S", chord.display)
    }

    @Test
    fun `named keys use the table rather than glfw`() {
        // glfwGetKeyName returns null for every one of these, which is most of what anyone binds.
        assertEquals("F5", BrassKeyChord(GLFW.GLFW_KEY_F5).baseName)
        assertEquals("Page Up", BrassKeyChord(GLFW.GLFW_KEY_PAGE_UP).baseName)
        assertEquals("Space", BrassKeyChord(GLFW.GLFW_KEY_SPACE).baseName)
        assertEquals("Num 7", BrassKeyChord(GLFW.GLFW_KEY_KP_7).baseName)
    }

    @Test
    fun `mouse buttons live in the same space as keys`() {
        val middle = BrassKeyChord.mouse(2)
        assertTrue(middle.isMouse)
        assertTrue(middle.bound)
        assertEquals("Middle Click", middle.baseName)
        assertEquals("Mouse 5", BrassKeyChord.mouse(4).baseName)
    }

    @Test
    fun `a mouse chord carries modifiers too`() {
        assertEquals("Ctrl+Right Click", BrassKeyChord.mouse(1, ctrl = true).display)
    }

    @Test
    fun `keys are never mistaken for mouse buttons`() {
        // The two share one int space, so the boundary is the thing that would silently break.
        assertFalse(BrassKeyChord(GLFW.GLFW_KEY_LAST).isMouse)
        assertTrue(BrassKeyChord(BrassKeyChord.MOUSE_BASE).isMouse)
    }

    @Test
    fun `modifier keys are recognised as such`() {
        // A capture widget must reject these, or Ctrl on the way to Ctrl+S binds Ctrl.
        assertTrue(BrassKeyChord(GLFW.GLFW_KEY_LEFT_CONTROL).isModifierOnly)
        assertTrue(BrassKeyChord(GLFW.GLFW_KEY_RIGHT_SHIFT).isModifierOnly)
        assertFalse(BrassKeyChord(GLFW.GLFW_KEY_S).isModifierOnly)
    }

    @Test
    fun `equality distinguishes chords that differ only by a modifier`() {
        // This is exactly what conflict detection relies on.
        val plain = BrassKeyChord(GLFW.GLFW_KEY_S)
        assertEquals(plain, BrassKeyChord(GLFW.GLFW_KEY_S))
        assertTrue(plain != BrassKeyChord(GLFW.GLFW_KEY_S, ctrl = true))
    }
}
