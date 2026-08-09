package net.swzo.brass.ui.kit.net

/**
 * The seam between the unified action API and a specific runtime - the networking analogue of
 * [net.swzo.brass.ui.kit.platform.BrassPlatform]. A NeoForge implementation sends real payloads over
 * the game connection; the desktop implementation runs handlers in-process.
 * Contract: [sendAction] and [subscribe] callbacks are invoked **on the UI/render thread** - each
 * transport is responsible for marshaling from its network/worker thread, because widgets and
 * [net.swzo.brass.ui.kit.base.BrassState] may only be touched there.
 */
interface BrassNetTransport {

    val name: String

    val identity: String

    val local: Boolean get() = false

    fun can(action: BrassAction<*>): AuthDecision

    /**
     * Send an action request. The reply is delivered to [reply] on the UI thread (possibly much
     * later - the button helper keeps the control disabled until then).
     */
    fun sendAction(requestId: Long, actionId: String, data: ByteArray?, reply: (BrassActionResult) -> Unit)

    /**
     * Subscribe to server-pushed state [stateId]. [onUpdate] receives the BSON value (or null for
     * "no value") on the UI thread - transports must deliver a **snapshot of the current value**
     * shortly after subscribe, so a client opening a screen sees existing state without waiting for
     * the next change. The returned handle unsubscribes.
     */
    fun subscribe(stateId: String, onUpdate: (ByteArray?) -> Unit): () -> Unit

    fun publish(stateId: String, data: ByteArray?, toPlayer: String? = null)

    /** Run [runnable] on the UI/render thread. */
    fun onUiThread(runnable: Runnable)

    /**
     * Ask the authoritative side for the current permission decisions for every registered action, so
     * the client mirror ([BrassNet.can]) reflects what the server would actually decide rather than a
     * local guess. No-op on transports where the local answer is already authoritative (the desktop).
     */
    fun requestPermissions() {}
}
