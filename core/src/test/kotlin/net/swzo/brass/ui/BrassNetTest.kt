package net.swzo.brass.ui

import net.swzo.brass.ui.kit.net.AuthContext
import net.swzo.brass.ui.kit.net.AuthDecision
import net.swzo.brass.ui.kit.net.BrassAction
import net.swzo.brass.ui.kit.net.BrassActionResult
import net.swzo.brass.ui.kit.net.BrassActionRegistry
import net.swzo.brass.ui.kit.net.BrassActions
import net.swzo.brass.ui.kit.net.BrassActionSet
import net.swzo.brass.ui.kit.net.BrassActionSets
import net.swzo.brass.ui.kit.net.BrassAuthorizer
import net.swzo.brass.ui.kit.net.BrassJson
import net.swzo.brass.ui.kit.net.BrassMessages
import net.swzo.brass.ui.kit.net.BrassNet
import net.swzo.brass.ui.kit.net.BrassNetTransport
import net.swzo.brass.ui.kit.net.BrassRateLimit
import net.swzo.brass.ui.kit.net.brassAction
import net.swzo.brass.ui.kit.net.brassAsyncAction
import net.swzo.brass.ui.kit.net.brassValue
import net.swzo.brass.ui.kit.net.err
import net.swzo.brass.ui.kit.net.ok
import net.swzo.brass.ui.kit.net.payloadAs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The networking module's pure logic, off-game: registry semantics, authorization, rate limits,
 * validation, timeouts, permission sync, optimistic state, coalescing, protocol versioning and the
 * discovery loader. The transports themselves (FML scan data, classpath scanning, payload codecs) are
 * thin enough to be covered by the platforms that run them.
 */
class BrassNetTest {

    @BeforeEach
    fun bindFakeTransport() {
        FakeTransport.published.clear()
        BrassNet.bind(FakeTransport)
        // Touching the annotated object initialises it, which is what registers its actions - the
        // same side effect discovery relies on. Test order must not matter, so every case gets it.
        @Suppress("UNUSED_EXPRESSION")
        BrassNetTestActions
        // Per-test fixtures live in the shared registry, registered idempotently.
        val shared = BrassNet.registry
        shared.register(action("test.op2", op = 2) { _, _ -> ok() })
        shared.register(action("test.crash", op = 0) { _, _ -> throw IllegalStateException("boom") })
        shared.register(action("test.rate", op = 0, rate = BrassRateLimit(2, 60)) { _, _ -> ok() })
        shared.register(action("test.validate", op = 0, validate = { "demo.name.blank" }) { _, _ -> ok() })
        shared.register(asyncAction("test.async", op = 0) { _, _ ->
            CompletableFuture.supplyAsync(
                { ok("async done") },
                CompletableFuture.delayedExecutor(30, TimeUnit.MILLISECONDS),
            )
        })
        shared.register(asyncAction("test.async.crash", op = 0) { _, _ ->
            CompletableFuture.failedFuture(IllegalStateException("async boom"))
        })
        BrassNet.clearDisabled()
        BrassNet.clearPermissions()
        BrassNet.resetProtocolMismatch()
    }

    @AfterEach
    fun resetMessages() {
        BrassMessages.translator = null
    }

    // ---- registry & dispatch -------------------------------------------------------------------

    @Test
    fun `registry dedupes by id and keeps the first registration`() {
        val registry = BrassActionRegistry()
        val first = action("dup.id", op = 0) { _, _ -> ok() }
        val second = action("dup.id", op = 0) { _, _ -> err("other") }

        assertTrue(registry.register(first))
        assertFalse(registry.register(second))
        assertTrue(registry.get<Any>("dup.id") === first)
    }

    @Test
    fun `dispatch runs the handler and returns its result`() {
        val result = dispatchSync("test.echo", BrassJson.toJson(Echo("hello")), AuthContext("player-1", 0))
        assertTrue(result.ok)
        assertEquals("hello", result.payloadAs(String::class.java))
    }

    @Test
    fun `async handlers complete the dispatch future`() {
        val result = dispatchSync("test.async", null, AuthContext("player-1", 0))
        assertTrue(result.ok)
        assertEquals("async done", result.payloadAs(String::class.java))
    }

    @Test
    fun `exceptional async completions become action failed`() {
        val result = dispatchSync("test.async.crash", null, AuthContext("player-1", 0))
        assertFalse(result.ok)
        assertEquals("action.failed", (result as BrassActionResult.Failure).code)
    }

    @Test
    fun `dispatch denies when the player lacks the op level`() {
        val result = dispatchSync("test.op2", null, AuthContext("player-1", opLevel = 0))
        assertFalse(result.ok)
        assertEquals("denied", (result as BrassActionResult.Failure).code)
        assertEquals("requires op level 2", result.args.first())
    }

    @Test
    fun `dispatch grants when the player meets the op level`() {
        assertTrue(dispatchSync("test.op2", null, AuthContext("player-1", opLevel = 2)).ok)
    }

    @Test
    fun `dispatch rejects unknown actions and malformed input`() {
        assertEquals(
            "action.unknown",
            (dispatchSync("nope", null, AuthContext(null, 0)) as BrassActionResult.Failure).code,
        )
        assertEquals(
            "action.malformed",
            (dispatchSync("test.echo", "{not json", AuthContext(null, 0)) as BrassActionResult.Failure).code,
        )
    }

    @Test
    fun `handler exceptions become action failed failures instead of crashing`() {
        val result = dispatchSync("test.crash", null, AuthContext(null, 0))
        assertFalse(result.ok)
        assertEquals("action.failed", (result as BrassActionResult.Failure).code)
    }

    @Test
    fun `rate limit blocks a player after the budget is spent`() {
        val registry = BrassActionRegistry()
        val limited = action("test.rate", op = 0, rate = BrassRateLimit(2, 60)) { _, _ -> ok() }
        registry.register(limited)

        assertTrue(registry.tryAcquire(limited, "player-1"))
        assertTrue(registry.tryAcquire(limited, "player-1"))
        assertFalse(registry.tryAcquire(limited, "player-1"))
        // A different player has their own budget.
        assertTrue(registry.tryAcquire(limited, "player-2"))
        // Logout cleanup drops the player's windows.
        registry.clearPlayer("player-1")
        assertTrue(registry.tryAcquire(limited, "player-1"))
    }

    @Test
    fun `dispatch enforces the rate limit declared on the action`() {
        assertTrue(dispatchSync("test.rate", null, AuthContext("player-1", 0)).ok)
        assertTrue(dispatchSync("test.rate", null, AuthContext("player-1", 0)).ok)
        val result = dispatchSync("test.rate", null, AuthContext("player-1", 0))
        assertEquals("rate.limited", (result as BrassActionResult.Failure).code)
    }

    @Test
    fun `disabled actions are rejected before authorization or the handler`() {
        BrassNet.disable("test.echo")
        val result = dispatchSync("test.echo", BrassJson.toJson(Echo("hi")), AuthContext("p", 0))
        assertEquals("action.disabled", (result as BrassActionResult.Failure).code)

        BrassNet.enable("test.echo")
        assertTrue(dispatchSync("test.echo", BrassJson.toJson(Echo("hi")), AuthContext("p", 0)).ok)
    }

    @Test
    fun `validation runs before the handler and its code is the failure`() {
        val result = dispatchSync("test.validate", null, AuthContext("p", 0))
        assertFalse(result.ok)
        assertEquals("demo.name.blank", (result as BrassActionResult.Failure).code)
    }

    // ---- messages ------------------------------------------------------------------------------

    @Test
    fun `default failure messages format args and fall back to the raw code`() {
        assertEquals("Unknown action: x.y", err("action.unknown", "x.y").message)
        assertEquals("You don't have permission to do that (requires op level 2)", err("denied", "requires op level 2").message)
        assertEquals("That action is currently disabled", err("action.disabled").message)
        assertEquals("Networking version mismatch: client 1, server 2", err("version.mismatch", "1", "2").message)
        assertEquals("unregistered.code", err("unregistered.code").message)
    }

    @Test
    fun `registered templates override built-in messages`() {
        BrassMessages.register("test.custom.msg", "Custom message: {0}")
        assertEquals("Custom message: 42", err("test.custom.msg", "42").message)
    }

    @Test
    fun `a translator takes precedence over the built-in catalog`() {
        BrassMessages.translator = BrassMessages.Translator { code, _ ->
            if (code == "action.unknown") "Translated!" else null
        }
        assertEquals("Translated!", err("action.unknown", "x").message)
        // Codes the translator does not know fall through to the catalog.
        assertEquals("That action is currently disabled", err("action.disabled").message)
    }

    // ---- sending, timeouts, lifecycle ----------------------------------------------------------

    @Test
    fun `send times out when the server never replies`() {
        val future = CompletableFuture<BrassActionResult>()
        BrassNet.send(
            BrassNetTestActions.echo,
            BrassNetTestActions.Echo("hi"),
            timeoutMillis = 50,
            onResult = { future.complete(it) },
        )
        val result = future.get(2, TimeUnit.SECONDS)
        assertFalse(result.ok)
        assertEquals("timeout", (result as BrassActionResult.Failure).code)
    }

    @Test
    fun `failPending fails every in-flight request`() {
        val future = CompletableFuture<BrassActionResult>()
        BrassNet.send(
            BrassNetTestActions.echo,
            BrassNetTestActions.Echo("hi"),
            timeoutMillis = 60_000,
            onResult = { future.complete(it) },
        )
        BrassNet.failPending("no.connection")
        val result = future.get(2, TimeUnit.SECONDS)
        assertEquals("no.connection", (result as BrassActionResult.Failure).code)
    }

    @Test
    fun `a version mismatch reply makes subsequent sends fail fast until reset`() {
        // The server's reply carries the mismatch; onReply flags it (the request id need not match a
        // live request - the flag is what makes future sends fail fast).
        BrassNet.onReply(999, err("version.mismatch", "2", "1"))
        assertTrue(BrassNet.protocolMismatch)

        val future = CompletableFuture<BrassActionResult>()
        BrassNet.send(
            BrassNetTestActions.echo,
            BrassNetTestActions.Echo("hi"),
            timeoutMillis = 60_000,
            onResult = { future.complete(it) },
        )
        assertEquals("version.mismatch", (future.get(2, TimeUnit.SECONDS) as BrassActionResult.Failure).code)

        BrassNet.resetProtocolMismatch()
        assertFalse(BrassNet.protocolMismatch)
    }

    // ---- permission sync -----------------------------------------------------------------------

    @Test
    fun `synced permissions win over the transport mirror`() {
        val action = BrassNetTestActions.echo
        assertEquals(AuthDecision.Grant, BrassNet.can(action))

        BrassNet.applyPermissions(mapOf("test.echo" to AuthDecision.Deny("banned")))
        assertEquals(AuthDecision.Deny("banned"), BrassNet.can(action))

        BrassNet.applyPermissions(emptyMap())
        assertEquals(AuthDecision.Grant, BrassNet.can(action))
    }

    @Test
    fun `computePermissions applies the authorizer across the registry`() {
        val perms = BrassNet.computePermissions(AuthContext("p", opLevel = 0))
        assertEquals(AuthDecision.Grant, perms["test.echo"])
        assertEquals(AuthDecision.Deny("requires op level 2"), perms["test.op2"])
    }

    // ---- state: optimistic, coalescing ---------------------------------------------------------

    @Test
    fun `optimistic updates are replaced by authoritative values and reverted on failure`() {
        val state = BrassNet.state("test.opt", String::class.java)
        val seen = ArrayList<String?>()
        val handle = state.onChange { seen += it }

        state.onRemote("\"server\"")
        state.optimistic("client")
        assertEquals("client", state.current)
        assertEquals(listOf(null, "server", "client"), seen)

        // The next authoritative update replaces the optimistic value.
        state.onRemote("\"server2\"")
        assertEquals("server2", state.current)
        assertEquals(listOf(null, "server", "client", "server2"), seen)

        // A failed action reverts to the last authoritative value.
        state.optimistic("client2")
        state.revert()
        assertEquals("server2", state.current)
        handle()
    }

    @Test
    fun `coalesced values broadcast only the latest value per window`() {
        val value = brassValue("test.coalesce", 0, coalesceMillis = 100)
        repeat(5) { value.value = it + 1 }

        Thread.sleep(250)
        assertEquals(listOf("test.coalesce" to "5"), FakeTransport.published.toList())

        value.value = 6
        Thread.sleep(250)
        assertEquals(listOf("test.coalesce" to "5", "test.coalesce" to "6"), FakeTransport.published.toList())
    }

    // ---- serialization -------------------------------------------------------------------------

    @Test
    fun `json round-trips actions and wire results`() {
        val input = Echo("round trip")
        assertEquals(input, BrassJson.fromJson(BrassJson.toJson(input), Echo::class.java))

        val failure = err("team.missing", "42")
        assertEquals(failure, BrassJson.fromWire(BrassJson.toWire(failure)))
        assertEquals(
            BrassActionResult.Success("\"payload\""),
            BrassJson.fromWire(BrassJson.toWire(ok("payload"))),
        )
    }

    @Test
    fun `compression round-trips small and large payloads`() {
        assertEquals("small payload", BrassJson.decompress(BrassJson.compress("small payload")))
        val large = "x".repeat(50_000)
        assertEquals(large, BrassJson.decompress(BrassJson.compress(large)))
        // Large payloads actually compress.
        assertTrue(BrassJson.compress(large).size < large.toByteArray().size)
    }

    @Test
    fun `server values snapshot their current state for late subscribers`() {
        val value = brassValue("test.snapshot", "first")
        assertEquals("\"first\"", BrassNet.snapshot("test.snapshot"))

        value.value = "second"
        assertEquals("\"second\"", BrassNet.snapshot("test.snapshot"))
        assertNull(BrassNet.snapshot("test.unknown"))
    }

    @Test
    fun `discovery loads an annotated object and registers its actions`() {
        assertTrue(BrassActionSets.load("net.swzo.brass.ui.BrassNetTestActions"))
        assertNotNull(BrassNet.registry.get<Any>("test.echo"))
        // Loading a non-action-set class is a no-op, not an error.
        assertFalse(BrassActionSets.load("net.swzo.brass.ui.BrassNetTest"))
    }

    private fun dispatchSync(actionId: String, json: String?, ctx: AuthContext): BrassActionResult =
        BrassNet.dispatch(actionId, json, ctx).get(2, TimeUnit.SECONDS)

    private fun action(
        id: String,
        op: Int,
        rate: BrassRateLimit? = null,
        validate: (Unit) -> String? = { null },
        handler: (net.swzo.brass.ui.kit.net.BrassActionContext, Unit) -> BrassActionResult,
    ) = BrassAction(id, "test.$id", op, rate, Unit::class.java, validate) { ctx, input ->
        CompletableFuture.completedFuture(handler(ctx, input))
    }

    private fun asyncAction(
        id: String,
        op: Int,
        handler: (net.swzo.brass.ui.kit.net.BrassActionContext, Unit) -> CompletableFuture<BrassActionResult>,
    ) = BrassAction(id, "test.$id", op, null, Unit::class.java, { null }, handler)

    private data class Echo(val text: String)
}

/** A top-level annotated object, exactly as a host mod would write it. */
@BrassActionSet
object BrassNetTestActions : BrassActions {

    val echo = brassAction<Echo>(
        id = "test.echo",
        permission = "test.echo",
    ) { _, input ->
        ok(input.text)
    }

    data class Echo(val text: String)
}

/** A transport that never moves a byte, for exercising [BrassNet] itself. */
private object FakeTransport : BrassNetTransport {
    override val name = "fake"
    override val identity = "fake"
    val published = CopyOnWriteArrayList<Pair<String, String?>>()

    override fun can(action: BrassAction<*>): AuthDecision = AuthDecision.Grant
    override fun sendAction(requestId: Long, actionId: String, json: String?, reply: (BrassActionResult) -> Unit) = Unit
    override fun subscribe(stateId: String, onUpdate: (String?) -> Unit): () -> Unit = { }
    override fun publish(stateId: String, json: String?, toPlayer: String?) {
        published += stateId to json
    }
    override fun onUiThread(runnable: Runnable) = runnable.run()
}
