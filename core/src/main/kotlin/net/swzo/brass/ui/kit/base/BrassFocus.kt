package net.swzo.brass.ui.kit.base

import gg.essential.elementa.UIComponent

/**
 * Keyboard focus: who has it, how it moves, and what activates.
 *
 * ### Why this exists
 *
 * The toolkit had no focus model at all. `BrassTextInput.focused` was the only notion of it, and
 * `BrassScreen` found the focused field by walking the **entire component tree on every keypress**.
 * Beyond that there was no Tab traversal, no focus ring, no Enter or Space activation, no arrow-key
 * navigation, and no way to focus a control from code - which made every form in the toolkit
 * mouse-only.
 *
 * The activation hook already existed: [BrassWidget.proxyActivate] is what a click *means*, which is
 * exactly what Enter and Space should do. It simply had no keyboard route.
 */
object BrassFocus {

    /** The component holding focus, or null. Weak: focus must not keep a closed screen alive. */
    private var focusedRef: java.lang.ref.WeakReference<UIComponent>? = null

    /**
     * True while focus arrived from the keyboard.
     *
     * The focus ring is drawn only then, which is the convention every desktop UI follows: a ring
     * around a button you *clicked* is noise, because you already know where you clicked, while a
     * ring around one you tabbed to is the only way to tell where you are.
     */
    var showRing: Boolean = false
        private set

    val focused: UIComponent? get() = focusedRef?.get()

    /** Whether [c] currently holds focus. */
    fun isFocused(c: UIComponent): Boolean = focused === c

    /** Give [c] focus. [fromKeyboard] decides whether the ring is drawn. */
    fun focus(c: UIComponent?, fromKeyboard: Boolean = false) {
        val previous = focused
        if (previous === c) return
        (previous as? BrassFocusable)?.onFocusLost()
        focusedRef = c?.let { java.lang.ref.WeakReference(it) }
        showRing = fromKeyboard
        (c as? BrassFocusable)?.onFocusGained()
    }

    fun clear() = focus(null)

    /**
     * Move focus to the next (or with [backwards], previous) focusable component under [root], in
     * tree order - which is reading order for any layout built top-to-bottom.
     *
     * Wraps around, so Tab from the last control returns to the first rather than dropping focus
     * into nothing.
     */
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

    /**
     * Activate the focused component, as Enter or Space would. Returns whether anything happened.
     *
     * Routes to [BrassWidget.proxyActivate] - the same entry point [BrassProxy] uses to let a caption
     * drive its checkbox - so every control that was already clickable by proxy becomes operable from
     * the keyboard with no further work.
     */
    fun activate(): Boolean {
        val c = focused ?: return false
        if (c is BrassFocusable && c.onActivate()) return true
        if (c is BrassWidget && c.active) { c.proxyActivate(); return true }
        return false
    }

    /** Send a key to the focused component, for a control with its own navigation (a list, a table). */
    fun handleKey(keyCode: Int): Boolean {
        val c = focused as? BrassFocusable ?: return false
        return c.onKeyPressed(keyCode)
    }

    /**
     * Focusable components under [root], in tree order.
     *
     * Skips anything that is inactive, not currently visible, or belongs to the dev overlay - Tab
     * should not walk into the inspector, and it should not stop on a control scrolled out of view.
     */
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
 *
 * Implement it to join the Tab order. The default implementations make a plain widget behave
 * sensibly - it takes focus, draws a ring, and Enter/Space activate it - so most controls need only
 * declare the interface.
 */
interface BrassFocusable {

    /** Whether this component should appear in the Tab order right now. */
    val focusable: Boolean get() = true

    /** Focus arrived. */
    fun onFocusGained() {}

    /** Focus left. */
    fun onFocusLost() {}

    /**
     * Enter or Space was pressed while focused. Return true if it was handled; false falls through to
     * [BrassWidget.proxyActivate].
     */
    fun onActivate(): Boolean = false

    /**
     * A key was pressed while focused, other than Tab and the activation keys. Return true to consume
     * it - for a control with its own navigation, like a table's up/down.
     */
    fun onKeyPressed(keyCode: Int): Boolean = false
}
