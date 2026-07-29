package net.swzo.brass.ui.kit.base

/**
 * A value widgets can **watch**. Assign to [value] and everything bound to it updates.
 *
 * The alternative it replaces is driving the UI from a tick callback - `addUpdateFunc { bar.progress =
 * job.progress }` - which works but puts the wiring in the wrong place: the screen ends up polling
 * state it does not own, once a frame, whether or not anything changed, and every new widget that
 * cares about the same value adds another line to the same loop. With a state object the value is
 * pushed once, when it actually changes, to however many widgets are listening.
 *
 * ```
 * val progress = BrassState(0f)
 * BrassProgressBar("Downloading").bind(progress) childOf card
 * // ...from wherever the work happens:
 * progress.value = downloaded.toFloat() / total
 * ```
 *
 * ### Lifetime
 *
 * A binding is a strong reference from the state to the widget, so a state that outlives a screen
 * keeps that screen's widgets alive with it. For the usual case - a state created by the screen that
 * uses it - both die together and there is nothing to do. For a **long-lived** state (an object that
 * outlives any one screen), keep the handle [onChange] returns and call it when the screen closes.
 *
 * Not thread-safe by design: bindings touch components, and Elementa components must only be touched
 * from the render thread. Set [value] from there. Work happening off-thread should hop back first -
 * the same rule that already applies to touching any widget.
 */
class BrassState<T>(initial: T) {

    private val listeners = ArrayList<(T) -> Unit>()

    /**
     * The current value. Setting it notifies every listener, but only when the value actually
     * changes - assigning the same value in a loop costs nothing, which is what makes it safe to
     * write this from code that runs every frame.
     */
    var value: T = initial
        set(newValue) {
            if (field == newValue) return
            field = newValue
            // Iterate a copy: a listener is allowed to bind or unbind in response, and would
            // otherwise mutate the list mid-notification.
            for (listener in listeners.toList()) listener(newValue)
        }

    /**
     * Call [listener] whenever the value changes, and once immediately with the current value so a
     * freshly bound widget shows the right thing without waiting for the first change.
     *
     * Returns a handle that removes the listener. Ignore it for a state scoped to one screen; keep it
     * for a state that outlives one (see the lifetime note above).
     */
    fun onChange(listener: (T) -> Unit): () -> Unit {
        listeners.add(listener)
        listener(value)
        return { listeners.remove(listener) }
    }

    /** Replace the value by applying [transform] to it - `count.update { it + 1 }`. */
    fun update(transform: (T) -> T) {
        value = transform(value)
    }

    /**
     * A read-only state carrying [transform] of this one's value, kept up to date.
     *
     * Every non-trivial UI ends up with values derived from other values - a label showing
     * `"$done / $total"`, a button enabled only when a field is non-empty - and without this each one
     * is a hand-written `onChange` that has to be remembered, wired and torn down.
     */
    fun <R> map(transform: (T) -> R): BrassState<R> {
        val derived = BrassState(transform(value))
        onChange { derived.value = transform(it) }
        return derived
    }

    /** A state carrying [combine] of this one and [other], recomputed when either changes. */
    fun <U, R> combine(other: BrassState<U>, combine: (T, U) -> R): BrassState<R> {
        val derived = BrassState(combine(value, other.value))
        onChange { derived.value = combine(it, other.value) }
        other.onChange { derived.value = combine(value, it) }
        return derived
    }

    /** A state that only reports values passing [predicate], keeping the last that did. */
    fun filter(predicate: (T) -> Boolean): BrassState<T> {
        val derived = BrassState(value)
        onChange { if (predicate(it)) derived.value = it }
        return derived
    }

    /** Remove every listener - the blunt teardown, for a state going out of scope. */
    fun unbindAll() = listeners.clear()

    override fun toString(): String = "BrassState($value)"
}
