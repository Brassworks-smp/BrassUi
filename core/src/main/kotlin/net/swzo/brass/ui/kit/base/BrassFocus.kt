package net.swzo.brass.ui.kit.base

import gg.essential.elementa.UIComponent

/**
 * Keyboard focus: who has it, how it moves, and what activates.
 * ### Why this exists
 * The toolkit had no focus model at all. `BrassTextInput.focused` was the only notion of it, and
 * `BrassScreen` found the focused field by walking the **entire component tree on every keypress**.
 * Beyond that there was no Tab traversal, no focus ring, no Enter or Space activation, no arrow-key
 * navigation, and no way to focus a control from code - which made every form in the toolkit
 * mouse-only.
 * The activation hook already existed: [BrassWidget.proxyActivate] is what a click *means*, which is
 * exactly what Enter and Space should do. It simply had no keyboard route.
 */
object BrassFocus {

    /** The component holding focus, or null. Weak: focus must not keep a closed screen alive. */
    private var focusedRef: java.lang.ref.WeakReference<UIComponent>? = null

    var showRing: Boolean = false
        private set

    val focused: UIComponent? get() = focusedRef?.get()

    fun isFocused(c: UIComponent): Boolean = focused === c

    fun focus(c: UIComponent?, fromKeyboard: Boolean = false) {
        val previous = focused
        if (previous === c) return
        (previous as? BrassFocusable)?.onFocusLost()
        focusedRef = c?.let { java.lang.ref.WeakReference(it) }
        showRing = fromKeyboard
        (c as? BrassFocusable)?.onFocusGained()
    }

    fun clear() = focus(null)

    fun moveNext(root: UIComponent, backwards: Boolean = false): Boolean {
        val stops = tabStops(root)
        if (stops.isEmpty()) return false
        val current = focused
        val index = stops.indexOfFirst { it === current }
        val next = when {
            index < 0 -> if (backwards) stops.last() else stops.first()
            else -> stops[((index + if (backwards) -1 else 1) + stops.size) % stops.size]
        }
        focus(next, fromKeyboard = true)
        return true
    }

    fun activate(): Boolean {
        val c = focused ?: return false
        if (c is BrassFocusable && c.onActivate()) return true
        if (c is BrassWidget && c.active) { c.proxyActivate(); return true }
        return false
    }

    fun handleKey(keyCode: Int): Boolean {
        val c = focused as? BrassFocusable ?: return false
        return c.onKeyPressed(keyCode)
    }

    fun tabStops(root: UIComponent): List<UIComponent> {
        val out = ArrayList<UIComponent>()
        fun walk(c: UIComponent) {
            for (child in c.children) {
                if (child is BrassFocusable && child.focusable) out.add(child)
                walk(child)
            }
        }
        walk(root)
        return out
    }
}

/**
 * A component that can take keyboard focus.
 * Implement it to join the Tab order. The default implementations make a plain widget behave
 * sensibly - it takes focus, draws a ring, and Enter/Space activate it - so most controls need only
 * declare the interface.
 */
interface BrassFocusable {

    val focusable: Boolean get() = true

    fun onFocusGained() {}

    fun onFocusLost() {}

    fun onActivate(): Boolean = false

    fun onKeyPressed(keyCode: Int): Boolean = false
}
