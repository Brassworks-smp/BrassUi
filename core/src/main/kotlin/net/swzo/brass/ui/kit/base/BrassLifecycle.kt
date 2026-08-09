@file:Suppress("unused")
package net.swzo.brass.ui.kit.base

import gg.essential.elementa.UIComponent
import net.swzo.brass.ui.kit.base.BrassLifecycle.SWEEP_INTERVAL
import java.util.*

/**
 * Teardown for components, which Elementa does not provide.
 * ### Why this exists
 * Nothing in the toolkit could learn that it had been removed from the tree, and the cost of that
 * showed up as workarounds in five different files:
 * - `BrassState.bind` handed back an unsubscribe handle that both of its callers discarded, so a
 *   state outliving a screen kept every widget bound to it - and through them the whole screen -
 *   alive for the process lifetime. Its own docs told the caller to hold the handle and call it
 *   "when the screen closes", with no way to know when that was.
 * - `BrassThemeCard` registered a theme listener that unsubscribed itself *the next time it
 *   happened to fire*, so a card on a closed screen stayed registered until some unrelated retheme.
 * - `BrassContextMenu` kept the open menu in a static field cleared only by `dismiss()`, so closing
 *   a screen with a menu up retained that screen forever.
 * - `BrassTooltip`, `BrassDevMode` and the toast stack all reached for `WeakHashMap` or static
 *   references as a substitute for being told when to let go.
 * One hook removes the need for all five.
 * ### How detachment is detected
 * By polling, because there is nothing to hook. Registered components are swept every
 * [SWEEP_INTERVAL] frames and anything no longer genuinely connected to a root (see
 * [BrassTree.isAttached] - a removed component keeps its stale `parent` pointer, so the cheap walk
 * is not enough) has its disposers run and is dropped.
 * Polling is affordable precisely because the set is small: only components that actually registered
 * something appear in it, not the whole tree. A screen with a dozen bindings sweeps a dozen ancestor
 * walks twice a second.
 */
object BrassLifecycle {

    const val SWEEP_INTERVAL = 30L

    private class Registration {
        val disposers = ArrayList<() -> Unit>()

        var everAttached = false
    }

    private val registered = WeakHashMap<UIComponent, Registration>()
    private var lastSweep = 0L

    fun onDetach(c: UIComponent, action: () -> Unit): () -> Unit {
        val reg = registered.getOrPut(c) { Registration() }
        reg.disposers.add(action)
        return { reg.disposers.remove(action) }
    }

    fun disposeNow(c: UIComponent) {
        val reg = registered.remove(c) ?: return
        runAll(reg)
    }

    fun disposeTree(root: UIComponent) {
        val doomed = registered.keys.filter { it === root || BrassTree.isDescendantOf(it, root) }
        for (c in doomed) registered.remove(c)?.let(::runAll)
    }

    fun sweep() {
        if (BrassClock.frame - lastSweep < SWEEP_INTERVAL) return
        lastSweep = BrassClock.frame

        val gone = ArrayList<UIComponent>()
        for ((c, reg) in registered) {
            if (BrassTree.isAttached(c)) {
                reg.everAttached = true
            } else if (reg.everAttached) {
                gone.add(c)
            }
        }
        for (c in gone) registered.remove(c)?.let(::runAll)
    }

    val registrationCount: Int get() = registered.size

    private fun runAll(reg: Registration) {
        // A throwing disposer must not strand the ones after it: teardown is the one place where
        // half-finishing is worse than failing loudly.
        for (d in reg.disposers) runCatching { d() }
        reg.disposers.clear()
    }
}

fun UIComponent.disposeWith(action: () -> Unit): () -> Unit = BrassLifecycle.onDetach(this, action)
