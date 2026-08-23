@file:Suppress("unused")
package net.swzo.brass.ui.kit.html

import net.swzo.brass.ui.kit.platform.BrassPlatform

/**
 * The seam between `brassui` and an embedded HTML rendering engine (Ultralight).
 *
 * Exactly like [BrassPlatform] and the other seams, this is an **interface in core, implemented by the
 * platform module, bound at startup**:
 * ```kotlin
 * BrassHtmlEngine.bind(UltralightHtmlEngine)
 * ```
 * The toolkit never names Ultralight directly — everything a widget needs is spelled here in
 * game-free terms, and the engine's own internals live in `kit/html/internal`. Adding a different
 * renderer later (a webview, a headless Chromium) means writing one implementation of this interface,
 * not touching [BrassHtml].
 *
 * ### Lifetime
 * The engine is **lazy and graceful by construction**. Binding it binds a *provider*, not a process:
 * nothing downloads, loads or initialises until the first [createView] call (which the widget only
 * makes once it is actually on screen). A machine that fails to initialise — the Ultralight natives
 * are x64-only and downloaded at runtime, so a 32-bit JVM or an offline box cannot load them — reports
 * [available] `false` instead of crashing, and the widget renders its "engine unavailable" state.
 * `update()` is the per-frame pulse (timers, animations, network, JS GC) and must be called from the
 * render thread at most once per frame; the widget does this itself through its draw pass.
 */
interface BrassHtmlEngine {

    /**
     * Whether a view can currently be created. False before the engine has initialised and after an
     * init failure; a bound-but-broken engine must degrade rather than throw.
     */
    val available: Boolean

    /**
     * Per-frame pulse for the whole engine. Called by the widget on its render thread; safe to call
     * every frame, cheap when nothing is animating.
     */
    fun update()

    /**
     * Create a view rendering [config]'s content. Returns null when the engine is unavailable or the
     * view cannot be created for any reason — the widget treats null as "show the placeholder".
     */
    fun createView(config: BrassHtmlConfig): BrassHtmlView?

    /** Tear the whole engine down (natives unload, views freed). Only a host ever calls this. */
    fun shutdown()

    companion object {
        var current: BrassHtmlEngine? = null
            private set

        /** Bind the active engine. [BrassPlatform] stays unbound until a host binds it; so does this. */
        fun bind(engine: BrassHtmlEngine) {
            current = engine
        }

        /** The bound engine, if any. */
        val available: Boolean get() = current != null
    }
}
