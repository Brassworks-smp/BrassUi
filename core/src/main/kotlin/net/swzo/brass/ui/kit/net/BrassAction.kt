package net.swzo.brass.ui.kit.net

import java.util.concurrent.CompletableFuture

/**
 * A named UI action: a payload type, a permission, an optional rate limit, and the server-side
 * handler that runs when the action arrives.
 *
 * ### The two copies
 *
 * The same class exists on both sides - the client keeps the action so widgets can send it and mirror
 * its permission; the server keeps the action so the handler can run there. What crosses the wire is
 * only the [id] and the JSON input; the handler lambda never travels. That is what makes the handler
 * safe to write "inline" in a file next to a screen: it must not capture UI state, because the server
 * executes its own copy.
 *
 * ### Registration is automatic
 *
 * Constructing an action registers it (idempotently) into [BrassNet.registry], so the only step left
 * is making sure the containing object is *loaded* on each side - which is what
 * [BrassActionSet] discovery does for you. A developer never calls `register`.
 */
class BrassAction<T : Any>(
    val id: String,
    /** Namespace-style permission string, e.g. `brassui.team.rename`. Reserved for future permission APIs. */
    val permission: String,
    val minOpLevel: Int,
    val rateLimit: BrassRateLimit?,
    val inputType: Class<T>,
    /** Runs after parsing, before the handler; a non-null return is a failure code (translated via [BrassMessages]). */
    val validate: (T) -> String?,
    /**
     * Runs on the server (or in-process on the desktop) and may complete asynchronously - the future
     * lets a handler do slow work off the server main thread without blocking the world. Build one
     * with [brassAction] (synchronous) or [brassAsyncAction] (explicit future).
     */
    val handler: (BrassActionContext, T) -> CompletableFuture<BrassActionResult>,
)

/**
 * What a server-side handler knows about the request: who sent it, and how to push state back to
 * clients. Deliberately small - player identity, op level, and [publish]. Anything else (the world,
 * the server) is the host mod's own business, fetched from its own singletons.
 */
class BrassActionContext(
    val playerId: String?,
    val opLevel: Int,
) {
    /**
     * Push [value] to every client subscribed to [stateId] (the wire value is JSON-serialised here).
     * Call this whenever an action changes shared state - the client side binds widgets to the same
     * id with [BrassNet.state].
     */
    fun publish(stateId: String, value: Any?) = BrassNet.publish(stateId, value)

    /**
     * Push [value] for [stateId] to a **single** player instead of broadcasting. Pass the target's
     * player id (the sender's own [playerId] is the common case for private echoes); the desktop
     * transport delivers to all local subscribers since there is no real player separation there.
     */
    fun publishTo(playerId: String?, stateId: String, value: Any?) =
        BrassNet.publish(stateId, value, toPlayer = playerId)
}

/**
 * Declare a server-backed action. Registration, serialization, authorization and rate limiting are
 * all derived from this one declaration - no codecs, no `register` calls.
 *
 * ```
 * @BrassActionSet
 * object TeamActions {
 *     val rename = brassAction<RenameTeam>(
 *         id = "brassui.team.rename",
 *         permission = "brassui.team.rename",
 *         minOpLevel = 3,
 *         rateLimit = BrassRateLimit(max = 10, perSeconds = 5),
 *     ) { ctx, input ->
 *         val team = Teams.get(input.teamId) ?: return@brassAction err("team.missing", input.teamId)
 *         team.name = input.name
 *         ctx.publish("brassui.team.$input.teamId.name", team.name)
 *         ok()
 *     }
 * }
 * ```
 */
inline fun <reified T : Any> brassAction(
    id: String,
    permission: String,
    minOpLevel: Int = 0,
    rateLimit: BrassRateLimit? = null,
    noinline validate: (T) -> String? = { null },
    noinline handler: (BrassActionContext, T) -> BrassActionResult,
): BrassAction<T> = BrassAction(id, permission, minOpLevel, rateLimit, T::class.java, validate) { ctx, input ->
    CompletableFuture.completedFuture(handler(ctx, input))
}.also {
    BrassNet.registry.register(it)
}

/**
 * Declare an **asynchronous** server-backed action. The handler returns a future so slow work (file
 * IO, waiting on another system) never blocks the server's main thread; the reply is sent when the
 * future completes. Everything else - registration, serialization, auth, rate limiting, validation -
 * is identical to [brassAction].
 */
inline fun <reified T : Any> brassAsyncAction(
    id: String,
    permission: String,
    minOpLevel: Int = 0,
    rateLimit: BrassRateLimit? = null,
    noinline validate: (T) -> String? = { null },
    noinline handler: (BrassActionContext, T) -> CompletableFuture<BrassActionResult>,
): BrassAction<T> = BrassAction(id, permission, minOpLevel, rateLimit, T::class.java, validate, handler).also {
    BrassNet.registry.register(it)
}
