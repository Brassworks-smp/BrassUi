package net.swzo.brass.ui.kit.input

import net.swzo.brass.ui.kit.input.BrassKeyChord.Companion.MOUSE_BASE
import org.lwjgl.glfw.GLFW

/**
 * A key or mouse binding: one main input plus whichever modifiers were held with it.
 * ### Why this is a value type
 * A binding stored as a bare `Int` cannot express `Ctrl+S` at all, and cannot distinguish "unbound"
 * from "key 0". It also has no name - every config screen that has ever stored keys as ints has grown
 * its own `keyName` function, and they disagree about `GLFW_KEY_GRAVE_ACCENT`. Making the chord the
 * value means [display] is defined once and comparison for conflict detection is `==`.
 * Mouse buttons live in the same space via [MOUSE_BASE], so a binding can be "middle click" without
 * the surrounding code needing a second field to say which kind it is.
 */
data class BrassKeyChord(
    val code: Int = GLFW.GLFW_KEY_UNKNOWN,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
) {

    val bound: Boolean get() = code != GLFW.GLFW_KEY_UNKNOWN

    val isMouse: Boolean get() = code >= MOUSE_BASE

    val display: String get() {
        if (!bound) return "Unbound"
        val parts = ArrayList<String>(4)
        if (ctrl) parts.add("Ctrl")
        if (shift) parts.add("Shift")
        if (alt) parts.add("Alt")
        parts.add(baseName)
        return parts.joinToString("+")
    }

    val baseName: String get() = when {
        !bound -> "Unbound"
        isMouse -> when (val button = code - MOUSE_BASE) {
            0 -> "Left Click"
            1 -> "Right Click"
            2 -> "Middle Click"
            else -> "Mouse ${button + 1}"
        }
        // glfwGetKeyName is a *native* call and needs GLFW initialised. That holds in game and does
        // not in a unit test - and a key name is never worth an UnsatisfiedLinkError, so a failure
        // falls through to the raw code rather than propagating.
        else -> NAMES[code]
            ?: runCatching { GLFW.glfwGetKeyName(code, 0) }.getOrNull()?.uppercase()
            ?: "Key $code"
    }

    /**
     * Whether this chord is only a modifier - `Ctrl` with nothing else.
     * A capture widget must reject these: pressing Ctrl on the way to Ctrl+S would otherwise bind
     * Ctrl and stop listening before the S arrived.
     */
    val isModifierOnly: Boolean get() = code in MODIFIER_KEYS

    companion object {
        const val MOUSE_BASE = 1_000

        val NONE = BrassKeyChord()

        fun mouse(button: Int, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false) =
            BrassKeyChord(MOUSE_BASE + button, ctrl, shift, alt)

        private val MODIFIER_KEYS = setOf(
            GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL,
            GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT,
            GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT,
            GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER,
        )

        private val NAMES: Map<Int, String> = buildMap {
            put(GLFW.GLFW_KEY_SPACE, "Space")
            put(GLFW.GLFW_KEY_ENTER, "Enter")
            put(GLFW.GLFW_KEY_TAB, "Tab")
            put(GLFW.GLFW_KEY_BACKSPACE, "Backspace")
            put(GLFW.GLFW_KEY_INSERT, "Insert")
            put(GLFW.GLFW_KEY_DELETE, "Delete")
            put(GLFW.GLFW_KEY_RIGHT, "Right")
            put(GLFW.GLFW_KEY_LEFT, "Left")
            put(GLFW.GLFW_KEY_DOWN, "Down")
            put(GLFW.GLFW_KEY_UP, "Up")
            put(GLFW.GLFW_KEY_PAGE_UP, "Page Up")
            put(GLFW.GLFW_KEY_PAGE_DOWN, "Page Down")
            put(GLFW.GLFW_KEY_HOME, "Home")
            put(GLFW.GLFW_KEY_END, "End")
            put(GLFW.GLFW_KEY_CAPS_LOCK, "Caps Lock")
            put(GLFW.GLFW_KEY_ESCAPE, "Escape")
            // Letters and digits are what people actually bind, and glfwGetKeyName only answers for
            // them once GLFW is initialised - which is never, on a dedicated server or in a test. They
            // are also the one group whose name is knowable without asking the keyboard layout.
            for (c in 'A'..'Z') put(GLFW.GLFW_KEY_A + (c - 'A'), c.toString())
            for (d in '0'..'9') put(GLFW.GLFW_KEY_0 + (d - '0'), d.toString())
            for (i in 1..25) put(GLFW.GLFW_KEY_F1 + i - 1, "F$i")
            for (i in 0..9) put(GLFW.GLFW_KEY_KP_0 + i, "Num $i")
            put(GLFW.GLFW_KEY_KP_ADD, "Num +")
            put(GLFW.GLFW_KEY_KP_SUBTRACT, "Num -")
            put(GLFW.GLFW_KEY_KP_MULTIPLY, "Num *")
            put(GLFW.GLFW_KEY_KP_DIVIDE, "Num /")
            put(GLFW.GLFW_KEY_KP_ENTER, "Num Enter")
            put(GLFW.GLFW_KEY_KP_DECIMAL, "Num .")
        }
    }
}
