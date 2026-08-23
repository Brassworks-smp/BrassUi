package net.swzo.brass.ui.kit.html.internal

import com.labymedia.ultralight.input.UltralightInputModifier
import com.labymedia.ultralight.input.UltralightKey
import net.swzo.brass.ui.kit.platform.BrassCursor
import org.lwjgl.glfw.GLFW

/**
 * The two translations the view needs on the boundary between brassui's (GLFW-flavoured) input and
 * Ultralight's own key/cursor vocabulary, ported from UltralightFabric's GLFW adapters.
 */
internal object HtmlKeys {

    /** GLFW modifier bitmask -> Ultralight's bitmask. */
    fun ultralightModifiers(mods: Int): Int {
        var out = 0
        if (mods and GLFW.GLFW_MOD_ALT != 0) out = out or UltralightInputModifier.ALT_KEY
        if (mods and GLFW.GLFW_MOD_CONTROL != 0) out = out or UltralightInputModifier.CTRL_KEY
        if (mods and GLFW.GLFW_MOD_SUPER != 0) out = out or UltralightInputModifier.META_KEY
        if (mods and GLFW.GLFW_MOD_SHIFT != 0) out = out or UltralightInputModifier.SHIFT_KEY
        return out
    }

    /** UKeyboard's (ctrl/shift/alt) trio -> Ultralight's bitmask. */
    fun ultralightModifiers(ctrl: Boolean, shift: Boolean, alt: Boolean): Int {
        var out = 0
        if (alt) out = out or UltralightInputModifier.ALT_KEY
        if (ctrl) out = out or UltralightInputModifier.CTRL_KEY
        if (shift) out = out or UltralightInputModifier.SHIFT_KEY
        return out
    }

    /** A GLFW-style modifier bitmask (the seam's integer form) -> Ultralight's bitmask. */
    fun ultralightModifiers(ctrl: Boolean, shift: Boolean, alt: Boolean, superKey: Boolean): Int {
        var out = ultralightModifiers(ctrl, shift, alt)
        if (superKey) out = out or UltralightInputModifier.META_KEY
        return out
    }

    fun glfwToUltralightKey(key: Int): UltralightKey = when (key) {
        GLFW.GLFW_KEY_SPACE -> UltralightKey.SPACE
        GLFW.GLFW_KEY_APOSTROPHE -> UltralightKey.OEM_7
        GLFW.GLFW_KEY_COMMA -> UltralightKey.OEM_COMMA
        GLFW.GLFW_KEY_MINUS -> UltralightKey.OEM_MINUS
        GLFW.GLFW_KEY_PERIOD -> UltralightKey.OEM_PERIOD
        GLFW.GLFW_KEY_SLASH -> UltralightKey.OEM_2
        GLFW.GLFW_KEY_0 -> UltralightKey.NUM_0
        GLFW.GLFW_KEY_1 -> UltralightKey.NUM_1
        GLFW.GLFW_KEY_2 -> UltralightKey.NUM_2
        GLFW.GLFW_KEY_3 -> UltralightKey.NUM_3
        GLFW.GLFW_KEY_4 -> UltralightKey.NUM_4
        GLFW.GLFW_KEY_5 -> UltralightKey.NUM_5
        GLFW.GLFW_KEY_6 -> UltralightKey.NUM_6
        GLFW.GLFW_KEY_7 -> UltralightKey.NUM_7
        GLFW.GLFW_KEY_8 -> UltralightKey.NUM_8
        GLFW.GLFW_KEY_9 -> UltralightKey.NUM_9
        GLFW.GLFW_KEY_SEMICOLON -> UltralightKey.OEM_1
        GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_EQUAL -> UltralightKey.OEM_PLUS
        GLFW.GLFW_KEY_A -> UltralightKey.A
        GLFW.GLFW_KEY_B -> UltralightKey.B
        GLFW.GLFW_KEY_C -> UltralightKey.C
        GLFW.GLFW_KEY_D -> UltralightKey.D
        GLFW.GLFW_KEY_E -> UltralightKey.E
        GLFW.GLFW_KEY_F -> UltralightKey.F
        GLFW.GLFW_KEY_G -> UltralightKey.G
        GLFW.GLFW_KEY_H -> UltralightKey.H
        GLFW.GLFW_KEY_I -> UltralightKey.I
        GLFW.GLFW_KEY_J -> UltralightKey.J
        GLFW.GLFW_KEY_K -> UltralightKey.K
        GLFW.GLFW_KEY_L -> UltralightKey.L
        GLFW.GLFW_KEY_M -> UltralightKey.M
        GLFW.GLFW_KEY_N -> UltralightKey.N
        GLFW.GLFW_KEY_O -> UltralightKey.O
        GLFW.GLFW_KEY_P -> UltralightKey.P
        GLFW.GLFW_KEY_Q -> UltralightKey.Q
        GLFW.GLFW_KEY_R -> UltralightKey.R
        GLFW.GLFW_KEY_S -> UltralightKey.S
        GLFW.GLFW_KEY_T -> UltralightKey.T
        GLFW.GLFW_KEY_U -> UltralightKey.U
        GLFW.GLFW_KEY_V -> UltralightKey.V
        GLFW.GLFW_KEY_W -> UltralightKey.W
        GLFW.GLFW_KEY_X -> UltralightKey.X
        GLFW.GLFW_KEY_Y -> UltralightKey.Y
        GLFW.GLFW_KEY_Z -> UltralightKey.Z
        GLFW.GLFW_KEY_LEFT_BRACKET -> UltralightKey.OEM_4
        GLFW.GLFW_KEY_BACKSLASH -> UltralightKey.OEM_5
        GLFW.GLFW_KEY_RIGHT_BRACKET -> UltralightKey.OEM_6
        GLFW.GLFW_KEY_GRAVE_ACCENT -> UltralightKey.OEM_3
        GLFW.GLFW_KEY_ESCAPE -> UltralightKey.ESCAPE
        GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> UltralightKey.RETURN
        GLFW.GLFW_KEY_TAB -> UltralightKey.TAB
        GLFW.GLFW_KEY_BACKSPACE -> UltralightKey.BACK
        GLFW.GLFW_KEY_INSERT -> UltralightKey.INSERT
        GLFW.GLFW_KEY_DELETE -> UltralightKey.DELETE
        GLFW.GLFW_KEY_RIGHT -> UltralightKey.RIGHT
        GLFW.GLFW_KEY_LEFT -> UltralightKey.LEFT
        GLFW.GLFW_KEY_DOWN -> UltralightKey.DOWN
        GLFW.GLFW_KEY_UP -> UltralightKey.UP
        GLFW.GLFW_KEY_PAGE_UP -> UltralightKey.PRIOR
        GLFW.GLFW_KEY_PAGE_DOWN -> UltralightKey.NEXT
        GLFW.GLFW_KEY_HOME -> UltralightKey.HOME
        GLFW.GLFW_KEY_END -> UltralightKey.END
        GLFW.GLFW_KEY_CAPS_LOCK -> UltralightKey.CAPITAL
        GLFW.GLFW_KEY_SCROLL_LOCK -> UltralightKey.SCROLL
        GLFW.GLFW_KEY_NUM_LOCK -> UltralightKey.NUMLOCK
        GLFW.GLFW_KEY_PRINT_SCREEN -> UltralightKey.SNAPSHOT
        GLFW.GLFW_KEY_PAUSE -> UltralightKey.PAUSE
        GLFW.GLFW_KEY_F1 -> UltralightKey.F1
        GLFW.GLFW_KEY_F2 -> UltralightKey.F2
        GLFW.GLFW_KEY_F3 -> UltralightKey.F3
        GLFW.GLFW_KEY_F4 -> UltralightKey.F4
        GLFW.GLFW_KEY_F5 -> UltralightKey.F5
        GLFW.GLFW_KEY_F6 -> UltralightKey.F6
        GLFW.GLFW_KEY_F7 -> UltralightKey.F7
        GLFW.GLFW_KEY_F8 -> UltralightKey.F8
        GLFW.GLFW_KEY_F9 -> UltralightKey.F9
        GLFW.GLFW_KEY_F10 -> UltralightKey.F10
        GLFW.GLFW_KEY_F11 -> UltralightKey.F11
        GLFW.GLFW_KEY_F12 -> UltralightKey.F12
        GLFW.GLFW_KEY_F13 -> UltralightKey.F13
        GLFW.GLFW_KEY_F14 -> UltralightKey.F14
        GLFW.GLFW_KEY_F15 -> UltralightKey.F15
        GLFW.GLFW_KEY_F16 -> UltralightKey.F16
        GLFW.GLFW_KEY_F17 -> UltralightKey.F17
        GLFW.GLFW_KEY_F18 -> UltralightKey.F18
        GLFW.GLFW_KEY_F19 -> UltralightKey.F19
        GLFW.GLFW_KEY_F20 -> UltralightKey.F20
        GLFW.GLFW_KEY_F21 -> UltralightKey.F21
        GLFW.GLFW_KEY_F22 -> UltralightKey.F22
        GLFW.GLFW_KEY_F23 -> UltralightKey.F23
        GLFW.GLFW_KEY_F24 -> UltralightKey.F24
        GLFW.GLFW_KEY_KP_0 -> UltralightKey.NUMPAD0
        GLFW.GLFW_KEY_KP_1 -> UltralightKey.NUMPAD1
        GLFW.GLFW_KEY_KP_2 -> UltralightKey.NUMPAD2
        GLFW.GLFW_KEY_KP_3 -> UltralightKey.NUMPAD3
        GLFW.GLFW_KEY_KP_4 -> UltralightKey.NUMPAD4
        GLFW.GLFW_KEY_KP_5 -> UltralightKey.NUMPAD5
        GLFW.GLFW_KEY_KP_6 -> UltralightKey.NUMPAD6
        GLFW.GLFW_KEY_KP_7 -> UltralightKey.NUMPAD7
        GLFW.GLFW_KEY_KP_8 -> UltralightKey.NUMPAD8
        GLFW.GLFW_KEY_KP_9 -> UltralightKey.NUMPAD9
        GLFW.GLFW_KEY_KP_DECIMAL -> UltralightKey.DECIMAL
        GLFW.GLFW_KEY_KP_DIVIDE -> UltralightKey.DIVIDE
        GLFW.GLFW_KEY_KP_MULTIPLY -> UltralightKey.MULTIPLY
        GLFW.GLFW_KEY_KP_SUBTRACT -> UltralightKey.SUBTRACT
        GLFW.GLFW_KEY_KP_ADD -> UltralightKey.ADD
        GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> UltralightKey.SHIFT
        GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> UltralightKey.CONTROL
        GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> UltralightKey.MENU
        GLFW.GLFW_KEY_LEFT_SUPER -> UltralightKey.LWIN
        GLFW.GLFW_KEY_RIGHT_SUPER -> UltralightKey.RWIN
        else -> UltralightKey.UNKNOWN
    }

    /** Ultralight's cursor vocabulary -> the handful brassui exposes, or null for "arrow/default". */
    fun brassCursor(cursor: com.labymedia.ultralight.input.UltralightCursor): net.swzo.brass.ui.kit.platform.BrassCursor.Kind? =
        when (cursor) {
            com.labymedia.ultralight.input.UltralightCursor.CROSS -> BrassCursor.Kind.CROSSHAIR
            com.labymedia.ultralight.input.UltralightCursor.HAND -> BrassCursor.Kind.HAND
            com.labymedia.ultralight.input.UltralightCursor.I_BEAM -> BrassCursor.Kind.TEXT
            com.labymedia.ultralight.input.UltralightCursor.EAST_WEST_RESIZE -> BrassCursor.Kind.RESIZE_H
            com.labymedia.ultralight.input.UltralightCursor.NORTH_SOUTH_RESIZE -> BrassCursor.Kind.RESIZE_V
            else -> null
        }
}
