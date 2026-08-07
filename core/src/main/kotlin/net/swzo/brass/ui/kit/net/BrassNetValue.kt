package net.swzo.brass.ui.kit.net

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Server-side shared state: a value that broadcasts every change to subscribed clients and answers
 * snapshot requests when a client subscribes late.
 *
 * Declare one inside an action set (or anywhere server-side), then mutate [value] from action
 * handlers - the publish happens automatically:
 *
 * ```
 * val teamName = brassValue("brassui.team.$id.name", "Brassworks")
 * // in a handler:
 * teamName.value = input.name
 * ```
 *
 * Clients read the same id with [BrassNet.state] and get the current value immediately, whether or not
 * they were connected when it last changed.
 *
 * Pass [coalesceMillis] > 0 to throttle high-frequency state (timers, live positions): rapid changes
 * within the window update [value] but only the **latest** value is broadcast when the window elapses,
 * so a 20 Hz loop pushes at most `1000 / coalesceMillis` packets per second.
 */
class BrassNetValue<T : Any>(
    val id: String,
    initial: T,
    val type: Class<T>,
    private val coalesceMillis: Long = 0,
) {

    @Volatile
    var value: T = initial
        set(newValue) {
            if (field == newValue) return
            field = newValue
            if (coalesceMillis <= 0) {
                BrassNet.publish(id, newValue)
            } else if (flushPending.compareAndSet(false, true)) {
                BrassNet.schedule(coalesceMillis) {
                    flushPending.set(false)
                    BrassNet.publish(id, value)
                }
            }
        }

    private val flushPending = AtomicBoolean(false)
}

/** Declare a server-side [BrassNetValue]; registration (and broadcast-on-change) is automatic. */
inline fun <reified T : Any> brassValue(id: String, initial: T, coalesceMillis: Long = 0): BrassNetValue<T> =
    BrassNetValue(id, initial, T::class.java, coalesceMillis).also { BrassNet.registerValue(it) }
