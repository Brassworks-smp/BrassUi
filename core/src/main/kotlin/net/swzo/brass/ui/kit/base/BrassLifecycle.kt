package net.swzo.brass.ui.kit.base

import gg.essential.elementa.UIComponent
import net.swzo.brass.ui.kit.base.BrassLifecycle.SWEEP_INTERVAL
import java.util.*

/**
 * Teardown for components, which Elementa does not provide.
 *
 * ### Why this exists
 *
 * Nothing in the toolkit could learn that it had been removed from the tree, and the cost of that
 * showed up as workarounds in five different files:
 *
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
 *
 * One hook removes the need for all five.
 *
 * ### How detachment is detected
 *
 * By polling, because there is nothing to hook. Registered components are swept every
 * [SWEEP_INTERVAL] frames and anything no longer genuinely connected to a root (see
 * [BrassTree.isAttached] - a removed component keeps its stale `parent` pointer, so the cheap walk
 * is not enough) has its disposers run and is dropped.
 *
 * Polling is affordable precisely because the set is small: only components that actually registered
 * something appear in it, not the whole tree. A screen with a dozen bindings sweeps a dozen ancestor
 * walks twice a second.
 */
object BrassLifecycle {

    /** Frames between sweeps. Teardown is not urgent; it only has to happen. */
    const val SWEEP_INTERVAL = 30L

    private class Registration {
        val disposers = ArrayList<() -> Unit>()

        /**
         * Whether this component has ever been seen in the tree.
         *
         * Registration usually happens during construction - `BrassProgressBar(...).bind(state)` runs
         * before `childOf` - so a component is legitimately unattached for its first few frames.
         * Sweeping on that would dispose every binding the moment it was made.
         */
        var everAttached = false
    }

    private val registered = WeakHashMap<UIComponent, Registration>()
    private var lastSweep = 0L

    /**
     * Run [action] once [c] leaves the component tree.
     *
     * Returns a handle that cancels the registration, for the rare case where the caller wants to
     * dispose early and by hand.
     */
    fun onDetach(c: UIComponent, action: () -> Unit): () -> Unit {
        val reg = registered.getOrPut(c) { Registration() }
        reg.disposers.add(action)
        return { reg.disposers.remove(action) }
    }

    /**
     * Run [c]'s disposers now and forget it - for a component being torn down deliberately, where
     * waiting for the next sweep would leave a listener live across the gap.
     */
    fun disposeNow(c: UIComponent) {
        val reg = registered.remove(c) ?: return
        runAll(reg)
    }

    /**
     * Dispose everything registered anywhere under [root], attached or not - the teardown a screen
     * runs when it closes, which is the one moment the poll cannot catch (the whole tree goes at
     * once, and nothing draws again to notice).
     */
    fun disposeTree(root: UIComponent) {
        val doomed = registered.keys.filter { it === root || BrassTree.isDescendantOf(it, root) }
        for (c in doomed) registered.remove(c)?.let(::runAll)
    }

    /**
     * Check the registered components and dispose any that have left the tree. Called once per frame
     * by the screen; does real work only every [SWEEP_INTERVAL] frames.
     */
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

    /** Number of components currently holding a disposer - for the dev overlay and for tests. */
    val registrationCount: Int get() = registered.size

    private fun runAll(reg: Registration) {
        // A throwing disposer must not strand the ones after it: teardown is the one place where
        // half-finishing is worse than failing loudly.
        for (d in reg.disposers) runCatching { d() }
        reg.disposers.clear()
    }
}

/**
 * Run [action] when this component leaves the tree - see [BrassLifecycle].
 *
 * ```kotlin
 * val stop = someState.onChange { label.text = it }
 * label.disposeWith(stop)
 * ```
 */
fun UIComponent.disposeWith(action: () -> Unit): () -> Unit = BrassLifecycle.onDetach(this, action)
