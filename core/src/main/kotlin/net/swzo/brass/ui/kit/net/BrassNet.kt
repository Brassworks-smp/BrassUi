package net.swzo.brass.ui.kit.net

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The unified entry point of the networking module - the only thing UI code talks to directly.
 *
 * One side registers actions by writing `@BrassActionSet` objects (discovered automatically by the
 * bound transport); the client sends them with [send], mirrors permissions with [can], and binds
 * server-pushed state with [state]. The server executes handlers through [dispatch], enforcing the
 * authorizer, rate limits, validation and the protocol version before any handler runs.
 */
object BrassNet {

    /** The wire protocol version. Bump when payload schemas change; mismatched peers get `version.mismatch`. */
    // 2: actions and states can travel chunked (payload schemas gained chunk/transfer fields).
    // 3: payload bodies are BSON bytes instead of gzip'd JSON strings (BrassBson), and the node
    //    graph's native save format is BSON - a protocol-2 peer would misparse every value.
    const val PROTOCOL_VERSION = 3

    /** How long [send] waits for a reply before failing with `timeout`. */
    const val DEFAULT_TIMEOUT_MILLIS: Long = 20_000L

    val registry = BrassActionRegistry()

    @Volatile
    private var transport: BrassNetTransport? = null

    @Volatile
    private var authorizer: BrassAuthorizer = BrassAuthorizers.byOpLevel

    /**
     * Server-side audit hook, invoked once per executed action (after the handler completes, even
     * asynchronously) with the action id, the player, the outcome and the handler duration in ms.
     * Fires on the completing thread - touch widgets only through [onUiThread].
     */
    @Volatile
    var onActionExecuted: ((actionId: String, playerId: String?, result: BrassActionResult, durationMs: Long) -> Unit)? = null

    private val pending = ConcurrentHashMap<Long, (BrassActionResult) -> Unit>()
    private val requestIds = AtomicLong()
    private val states = ConcurrentHashMap<String, BrassNetState<*>>()
    private val values = ConcurrentHashMap<String, BrassNetValue<*>>()
    private val disabled = ConcurrentHashMap.newKeySet<String>()
    private val syncedPermissions = ConcurrentHashMap<String, AuthDecision>()
    private val permissionListeners = CopyOnWriteArrayList<() -> Unit>()
    private val timers = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "brassui-timers").apply { isDaemon = true }
    }

    /** Set when the server rejected a request with `version.mismatch`; sends then fail fast locally. */
    @Volatile
    var protocolMismatch: Boolean = false
        private set

    /** Forget a previous version mismatch - transports call this on login so a rejoined peer is retried. */
    fun resetProtocolMismatch() {
        protocolMismatch = false
    }

    /** Bind a transport (and optionally an authorizer). Called once by each platform at startup. */
    fun bind(transport: BrassNetTransport, authorizer: BrassAuthorizer = BrassAuthorizers.byOpLevel) {
        this.transport = transport
        this.authorizer = authorizer
    }

    fun isBound(): Boolean = transport != null

    /** The bound transport's name, or null. */
    val transportName: String? get() = transport?.name

    /** Who the current user is per the bound transport, or null. */
    val identity: String? get() = transport?.identity

    /** True when handlers run in this process rather than on a game server. */
    fun isLocal(): Boolean = transport?.local == true

    /** Run [runnable] on the UI/render thread, or inline when no transport is bound. */
    fun onUiThread(runnable: Runnable) {
        val t = transport
        if (t != null) t.onUiThread(runnable) else runnable.run()
    }

    /** Internal: run [task] after [delayMillis] on the shared daemon scheduler (coalescing, etc.). */
    internal fun schedule(delayMillis: Long, task: () -> Unit) {
        timers.schedule({ task() }, delayMillis, TimeUnit.MILLISECONDS)
    }

    // ---- permissions ---------------------------------------------------------------------------

    /**
     * The client-side authorization mirror: the server-synced decision when one is known, otherwise
     * the transport's local guess. The server is always the source of truth; this only greys buttons.
     */
    fun can(action: BrassAction<*>): AuthDecision =
        syncedPermissions[action.id] ?: transport?.can(action) ?: AuthDecision.Deny("net.unavailable")

    /** Server-side: the authorizer's decision for [ctx] across every registered action. */
    fun computePermissions(ctx: AuthContext): Map<String, AuthDecision> =
        registry.all().associate { it.id to authorizer.check(it, ctx) }

    /** Client-side: store the server's permission decisions and re-evaluate every mirrored control. */
    fun applyPermissions(entries: Map<String, AuthDecision>) {
        syncedPermissions.clear()
        syncedPermissions.putAll(entries)
        for (listener in permissionListeners) listener()
    }

    /** Ask the transport to refresh the synced permissions (the NeoForge client does this on login). */
    fun refreshPermissions() {
        transport?.requestPermissions()
    }

    /** Run [listener] whenever the synced permissions change; the handle removes it. */
    fun onPermissionsChanged(listener: () -> Unit): () -> Unit {
        permissionListeners.add(listener)
        return { permissionListeners.remove(listener) }
    }

    /** Test/teardown hook: forget the synced permissions. */
    internal fun clearPermissions() {
        syncedPermissions.clear()
    }

    // ---- sending -------------------------------------------------------------------------------

    /**
     * Send [input] for [action]. [onResult] runs on the UI thread when the server replies (or
     * immediately with `net.unavailable` when no transport is bound, `version.mismatch` after the
     * server has rejected the protocol, or `timeout` when [timeoutMillis] elapses first). While a
     * request is in flight the [actionButton] helper keeps its button disabled. Returns the request
     * id, which a host can use to track large chunked transfers (see the transport's progress hook).
     */
    fun <T : Any> send(
        action: BrassAction<T>,
        input: T,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        onResult: (BrassActionResult) -> Unit = {},
    ): Long {
        if (protocolMismatch) {
            onResult(err("version.mismatch", PROTOCOL_VERSION.toString(), "?"))
            return -1L
        }
        val t = transport
        if (t == null) {
            onResult(err("net.unavailable"))
            return -1L
        }
        val requestId = requestIds.incrementAndGet()
        pending[requestId] = onResult
        val data = if (action.inputType == Unit::class.java) null else BrassBson.toBytes(input)
        try {
            t.sendAction(requestId, action.id, data) { result ->
                pending.remove(requestId)
                onResult(result)
            }
            if (timeoutMillis > 0) {
                timers.schedule(
                    {
                        // The reply may still arrive later; whoever removes the pending entry first
                        // wins, so a late reply after a timeout is a harmless no-op.
                        val timedOut = pending.remove(requestId)
                        if (timedOut != null) {
                            t.onUiThread { timedOut(err("timeout", (timeoutMillis / 1000).toString())) }
                        }
                    },
                    timeoutMillis,
                    TimeUnit.MILLISECONDS,
                )
            }
        } catch (throwable: Throwable) {
            pending.remove(requestId)
            onResult(err("send.failed", throwable.javaClass.simpleName))
        }
        return requestId
    }

    /** Transport-side hook: deliver a server reply to the pending callback (called on the UI thread). */
    fun onReply(requestId: Long, result: BrassActionResult) {
        if (!result.ok && (result as? BrassActionResult.Failure)?.code == "version.mismatch") {
            protocolMismatch = true
        }
        pending.remove(requestId)?.invoke(result)
    }

    /** Fail every in-flight request with [code] - used on disconnect so callbacks never dangle. */
    fun failPending(code: String, vararg args: Any?) {
        for (requestId in pending.keys.toList()) {
            val callback = pending.remove(requestId) ?: continue
            onUiThread { callback(err(code, *args)) }
        }
    }

    // ---- state ---------------------------------------------------------------------------------

    /**
     * A client handle on server-pushed state [id]. Widgets bind with [BrassNetState.onChange]; the
     * returned handle unsubscribes when the last listener goes away.
     */
    fun <T : Any> state(id: String, type: Class<T>): BrassNetState<T> {
        @Suppress("UNCHECKED_CAST")
        return states.getOrPut(id) { BrassNetState<T>(id, type) } as BrassNetState<T>
    }

    inline fun <reified T : Any> state(id: String): BrassNetState<T> = state(id, T::class.java)

    /** Server-side: push [value] for [stateId] (see [BrassActionContext.publish]). */
    fun publish(stateId: String, value: Any?, toPlayer: String? = null) {
        transport?.publish(stateId, if (value == null) null else BrassBson.toBytes(value), toPlayer)
    }

    /** Register a server-side [BrassNetValue] (called by [brassValue]). */
    fun registerValue(value: BrassNetValue<*>) {
        values[value.id] = value
    }

    /** The current BSON for [stateId], or null when no [BrassNetValue] with that id is registered. */
    fun snapshot(stateId: String): ByteArray? =
        values[stateId]?.let { BrassBson.toBytes(it.value) }

    /** Internal: register a raw state subscriber on the bound transport. */
    internal fun subscribeState(stateId: String, onUpdate: (ByteArray?) -> Unit): () -> Unit =
        transport?.subscribe(stateId, onUpdate) ?: { }

    // ---- actions -------------------------------------------------------------------------------

    /**
     * Temporarily disable [actionId] server-side (or locally, on the desktop): every request fails
     * with `action.disabled` until [enable] is called. Handy for maintenance windows, event-gated
     * features, or the demo's on/off switch.
     */
    fun disable(actionId: String) {
        disabled += actionId
    }

    fun enable(actionId: String) {
        disabled -= actionId
    }

    fun isDisabled(actionId: String): Boolean = actionId in disabled

    /** Test/teardown hook: re-enable everything. */
    internal fun clearDisabled() {
        disabled.clear()
    }

    /**
     * Server-side dispatch: look up [actionId], check the protocol version, authorize, rate-limit,
     * parse, validate, then run the handler. The returned future completes (possibly on a worker
     * thread, for async handlers) with the result; exceptions inside the handler - synchronous or
     * exceptional completions - become `action.failed` rather than crashing the server.
     *
     * The synchronous checks run on the caller's thread; transports schedule the call onto the server
     * main thread first.
     */
    fun dispatch(actionId: String, data: ByteArray?, ctx: AuthContext): CompletableFuture<BrassActionResult> {
        val action = registry.get<Any>(actionId) ?: return completed(err("action.unknown", actionId))
        if (actionId in disabled) return completed(err("action.disabled", actionId))
        when (val decision = authorizer.check(action, ctx)) {
            is AuthDecision.Deny -> return completed(err("denied", decision.reason))
            AuthDecision.Grant -> {}
        }
        if (!registry.tryAcquire(action, ctx.playerId)) return completed(err("rate.limited"))
        val input = parseInput(action, data) ?: return completed(err("action.malformed"))
        action.validate(input)?.let { return completed(err(it)) }

        val started = System.nanoTime()
        val future = try {
            action.handler(BrassActionContext(ctx.playerId, ctx.opLevel), input)
        } catch (throwable: Throwable) {
            completed(err("action.failed", throwable.javaClass.simpleName ?: "exception", throwable.message ?: ""))
        }
        return future.handle { result, throwable ->
            val outcome = if (throwable != null) {
                err("action.failed", throwable.javaClass.simpleName ?: "exception", throwable.message ?: "")
            } else {
                result
            }
            onActionExecuted?.invoke(actionId, ctx.playerId, outcome, (System.nanoTime() - started) / 1_000_000)
            outcome
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> parseInput(action: BrassAction<T>, data: ByteArray?): T? = when {
        action.inputType == Unit::class.java -> Unit as T
        data == null -> null
        else -> BrassBson.fromBytes(data, action.inputType)
    }

    private fun completed(result: BrassActionResult): CompletableFuture<BrassActionResult> =
        CompletableFuture.completedFuture(result)
}

/**
 * A client-side subscription to a server-pushed value, shared by every caller that asks for the same
 * [stateId]. Updates (including the transport's initial snapshot) arrive on the UI thread and are
 * decoded once into [current].
 *
 * [optimistic] lets the UI apply a change immediately (for a snappy feel) before the server
 * confirms it; the next authoritative update replaces it, and [revert] undoes it if the action fails.
 */
class BrassNetState<T : Any>(private val id: String, private val type: Class<T>) {

    private val listeners = CopyOnWriteArrayList<(T?) -> Unit>()
    private var unsubscribe: (() -> Unit)? = null

    @Volatile
    private var last: T? = null

    @Volatile
    private var lastAuthoritative: T? = null

    @Volatile
    private var pendingOptimistic = false

    /** The most recent value (optimistic or authoritative), or null before the first update. */
    val current: T? get() = last

    /**
     * Call [listener] with the current value immediately and with every subsequent update (both on
     * the UI thread). The first listener subscribes to the transport; the returned handle removes the
     * listener and unsubscribes when the last one is gone.
     */
    fun onChange(listener: (T?) -> Unit): () -> Unit {
        listeners.add(listener)
        if (unsubscribe == null) {
            unsubscribe = BrassNet.subscribeState(id) { onRemote(it) }
        }
        listener(last)
        return {
            listeners.remove(listener)
            if (listeners.isEmpty()) {
                unsubscribe?.invoke()
                unsubscribe = null
            }
        }
    }

    /**
     * Apply [value] locally and notify listeners, without waiting for the server. Marked optimistic:
     * the next authoritative update replaces it, and [revert] restores the last authoritative value.
     */
    fun optimistic(value: T) {
        if (value == last) return
        pendingOptimistic = true
        last = value
        for (listener in listeners) listener(value)
    }

    /** Undo an [optimistic] update - call when the action that produced it failed. */
    fun revert() {
        if (!pendingOptimistic) return
        pendingOptimistic = false
        if (last == lastAuthoritative) return
        last = lastAuthoritative
        for (listener in listeners) listener(last)
    }

    internal fun onRemote(data: ByteArray?) {
        val value = if (data == null) null else BrassBson.fromBytes(data, type)
        pendingOptimistic = false
        lastAuthoritative = value
        if (value == last) return
        last = value
        for (listener in listeners) listener(value)
    }
}
