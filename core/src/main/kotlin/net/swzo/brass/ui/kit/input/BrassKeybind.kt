@file:Suppress("unused")
package net.swzo.brass.ui.kit.input

import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.text.BrassFont
import org.lwjgl.glfw.GLFW
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * Click to capture a key or mouse chord - the control every settings screen needs and the toolkit
 * had none of.
 * ```kotlin
 * val bind = BrassKeybind(BrassKeyChord(GLFW.GLFW_KEY_G))
 * bind.conflictsWith = { chord -> otherBinds.any { it.value == chord } }
 * ```
 * ### Capturing
 * Click it and it listens; the next non-modifier press becomes the binding, with whatever modifiers
 * were held. Escape, Delete and the right mouse button all **unbind** - the behaviours every game's
 * controls screen has, which users will try without being told.
 * Escape used to cancel and put the previous chord back, which is the dialog convention and the wrong
 * one here: a control that is already listening for "the next key you press" has no cancel affordance,
 * so Escape is the key a user reaches for to mean *clear this*. Restoring instead left them pressing
 * it repeatedly and watching the binding they wanted gone come straight back.
 * A press of Ctrl alone is deliberately ignored ([BrassKeyChord.isModifierOnly]): otherwise going for
 * Ctrl+S binds Ctrl and stops listening before the S is typed.
 * ### Conflicts
 * [conflictsWith] is asked whether a chord is already taken, and a conflicting binding is drawn in
 * the danger colour with a marker. The widget deliberately does **not** refuse the binding: which of
 * two clashing binds should win is the application's decision, and a control that silently rejects
 * input is worse than one that shows the clash.
 * Keys are routed here by the screen while [capturing] is true - see `BrassScreen`.
 */
class BrassKeybind(
    initial: BrassKeyChord = BrassKeyChord.NONE,
    private val onChange: (BrassKeyChord) -> Unit = {},
) : BrassWidget(BrassAccent.DEFAULT), BrassValue<BrassKeyChord>, BrassFocusable {

    private val holder = BrassValueHolder(initial)

    override var value: BrassKeyChord
        get() = holder.value
        set(v) { holder.value = v }

    override fun setSilently(value: BrassKeyChord) = holder.setSilently(value)
    override fun onChange(listener: (BrassKeyChord) -> Unit) = holder.onChange(listener)
    override fun bind(state: BrassState<BrassKeyChord>) = holder.bind(this, state)

    var capturing: Boolean = false
        private set

    var conflictsWith: (BrassKeyChord) -> Boolean = { false }

    private var beforeCapture: BrassKeyChord = initial

    private var blink = 0f

    init {
        // A default height, because BrassForm.addField sets x, y and width but deliberately not
        // height - a control with no intrinsic height resolves to zero and simply does not appear.
        // BrassLabel and BrassTag self-constrain for the same reason. A caller's own constrain{}
        // still wins, since it is applied after construction.
        constrain { height = DEFAULT_H.pixels() }
        clickable = true
        holder.onChange(onChange)
        onMouseClick { e ->
            if (!active) return@onMouseClick
            when {
                // Right-click clears, whether or not we are capturing - the fastest way to unbind.
                e.mouseButton == 1 -> { value = BrassKeyChord.NONE; capturing = false }
                capturing -> capture(BrassKeyChord.mouse(e.mouseButton, ctrl(), shift(), alt()))
                e.mouseButton == 0 -> beginCapture()
            }
        }
    }

    override fun proxyActivate() { if (active) beginCapture() }

    fun beginCapture() {
        beforeCapture = value
        capturing = true
        blink = 0f
    }

    fun endCapture() { capturing = false }

    fun cancelCapture() {
        value = beforeCapture
        capturing = false
    }

    private fun capture(chord: BrassKeyChord) {
        value = chord
        capturing = false
    }

    override fun onKeyPressed(keyCode: Int): Boolean {
        if (!capturing) return false
        return when {
            keyCode == GLFW.GLFW_KEY_ESCAPE ||
                keyCode == GLFW.GLFW_KEY_DELETE ||
                keyCode == GLFW.GLFW_KEY_BACKSPACE -> { capture(BrassKeyChord.NONE); true }
            // A modifier on its own is on the way to a chord, not the chord itself.
            BrassKeyChord(keyCode).isModifierOnly -> true
            else -> { capture(BrassKeyChord(keyCode, ctrl(), shift(), alt())); true }
        }
    }

    override fun onActivate(): Boolean {
        // Enter while capturing should be bindable like any other key, not re-trigger the capture.
        if (capturing) return false
        beginCapture()
        return true
    }

    override fun onFocusLost() { if (capturing) cancelCapture() }

    private fun ctrl() = UKeyboard.isCtrlKeyDown()
    private fun shift() = UKeyboard.isShiftKeyDown()
    private fun alt() = UKeyboard.isAltKeyDown()

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        blink += BrassClock.dt

        val conflicted = value.bound && !capturing && conflictsWith(value)
        val label = when {
            capturing -> if ((blink * BLINK_HZ).toInt() % 2 == 0) "> press a key <" else "> _ <"
            else -> value.display
        }
        val tint = when {
            capturing -> Colors.UI_ACCENT_BRIGHT
            conflicted -> Colors.DANGER
            !value.bound -> Colors.UI_TEXT_DARK
            else -> textColor
        }

        val fitted = BrassFont.fit(this, label, (w - PAD * 2).toFloat())
        BrassFont.draw(
            m, this, fitted,
            x + (w - BrassFont.width(this, fitted)) / 2f,
            y + (h - BrassFont.LINE) / 2f,
            tint,
        )
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("keybind", "Keybind", 120f, 20f) {
            BrassKeybind(BrassKeyChord.NONE)
        }

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val DEFAULT_H = 16f
        private const val PAD = 4
        private const val BLINK_HZ = 2f
    }
}
