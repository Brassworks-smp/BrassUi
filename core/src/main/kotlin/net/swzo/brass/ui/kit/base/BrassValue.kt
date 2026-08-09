package net.swzo.brass.ui.kit.base

import gg.essential.elementa.UIComponent

/**
 * One contract for every control that holds a value.
 * ### Why this exists
 * The twelve stateful widgets had twelve different answers to "read it, write it, listen to it":
 * | | read | write | notifies? |
 * |---|---|---|---|
 * | `BrassToggle` | `toggled` | `set(v)` | yes |
 * | `BrassCheckbox` | `checked` | `set(v)` | yes |
 * | `BrassTabSwitch` | `selectedIndex` | `select(i)` | yes |
 * | `BrassDropdown` | `selected` | - | - |
 * | `BrassSlider` | `value` | - | - |
 * | `BrassScrollSelector` | `selectedIndex` | - | - |
 * | `BrassTextInput` | `text` | `setTextSilently(v)` | **no** |
 * | `BrassColorPicker` | `picked` | `set(c)` | **no** |
 * Three separate problems in one table. Four controls were **write-only from the caller's side** - a
 * slider could not be moved, a dropdown could not be selected, so "restore the saved settings into
 * this form" was simply not expressible. `set` fired the callback on two widgets and deliberately did
 * not on two others, so the same method name meant opposite things. And the constructor callback was
 * the only listener slot there was: no second listener, no removal, no registering one after
 * construction.
 * Here `value` always notifies, [setSilently] never does, and [onChange] returns a handle. The old
 * names survive as aliases so nothing at a call site has to change at once.
 */
interface BrassValue<T> {

    var value: T

    fun setSilently(value: T)

    /**
     * Call [listener] whenever the value changes. Returns a handle that removes it.
     * Unlike the constructor callback this can be called any number of times, at any point in the
     * widget's life.
     */
    fun onChange(listener: (T) -> Unit): () -> Unit

    fun bind(state: BrassState<T>): () -> Unit
}

/**
 * The state and plumbing behind [BrassValue], for a widget to compose.
 * Kotlin interfaces cannot hold state, and the alternative - repeating the listener list, the
 * equality guard and the re-entrancy guard in twelve widgets - is exactly the duplication this whole
 * contract exists to remove.
 * @param initial the starting value
 * @param onApply called when the value changes, to update whatever the widget draws from
 */
class BrassValueHolder<T>(
    initial: T,
    private val onApply: (T) -> Unit = {},
) {

    private val listeners = ArrayList<(T) -> Unit>()

    private var notifying = false
    private var current: T = initial

    /**
     * The three writes are deliberately separate rather than one setter with flags:
     * - [value] - the widget must show it *and* announce it (a user interaction, or code setting it)
     * - [setSilently] - show it, do not announce it (syncing one control from another)
     * - [notifyNow] - announce it, do **not** re-apply it
     */
    var value: T
        get() = current
        set(newValue) {
            if (current == newValue) return
            current = newValue
            onApply(newValue)
            notifyAll(newValue)
        }

    fun setSilently(newValue: T) {
        if (current == newValue) return
        current = newValue
        onApply(newValue)
    }

    fun onChange(listener: (T) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun notifyNow(v: T) {
        current = v
        notifyAll(v)
    }

    private fun notifyAll(v: T) {
        if (notifying) return
        notifying = true
        try {
            // Iterate a copy: a listener may add or remove one in response.
            for (l in listeners.toList()) l(v)
        } finally {
            notifying = false
        }
    }

    fun bind(owner: UIComponent, state: BrassState<T>): () -> Unit {
        val fromState = state.onChange { setSilently(it) }
        val toState = onChange { state.value = it }
        val stop = {
            fromState()
            toState()
        }
        owner.disposeWith(stop)
        return stop
    }
}
