package net.swzo.brass.ui.neoforge.net

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import net.neoforged.neoforge.server.ServerLifecycleHooks
import net.neoforged.neoforge.server.permission.PermissionAPI
import net.neoforged.neoforge.server.permission.handler.DefaultPermissionHandler
import net.neoforged.neoforge.server.permission.nodes.PermissionNode
import net.neoforged.neoforge.server.permission.nodes.PermissionNode.PermissionResolver
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes
import net.swzo.brass.ui.kit.net.AuthContext
import net.swzo.brass.ui.kit.net.AuthDecision
import net.swzo.brass.ui.kit.net.BrassAction
import net.swzo.brass.ui.kit.net.BrassActionResult
import net.swzo.brass.ui.kit.net.BrassAuthorizer
import net.swzo.brass.ui.kit.net.BrassBson
import net.swzo.brass.ui.kit.net.BrassChunking
import net.swzo.brass.ui.kit.net.BrassMessages
import net.swzo.brass.ui.kit.net.BrassNet
import net.swzo.brass.ui.kit.net.BrassNetTransport
import net.swzo.brass.ui.kit.net.decodeAuthDecision
import net.swzo.brass.ui.kit.net.encode
import net.swzo.brass.ui.kit.net.err
import java.util.UUID
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * The NeoForge end of the networking seam: registers the payloads, discovers action sets from FML scan
 * data, adapts the game's threads to the unified API, and checks the protocol version so mixed
 * client/server brassui versions fail loudly instead of corrupting each other's state.
 *
 * Payload handlers arrive on the network thread and are [IPayloadContext.enqueueWork]'d onto the game
 * thread. Dispatch itself may complete asynchronously (a handler's future), so the reply is sent from
 * the server main thread when that future completes - never from a handler's worker thread.
 */
object NeoForgeBrassNet {

    /** How long a half-received chunked value is kept before it is dropped. */
    private const val CHUNK_TTL_MS = 15_000L

    /** Per-state publish sequence, so each chunked state update has a unique transfer identity. */
    internal val stateSeq = AtomicLong()

    /** Chunked actions per player (requestId -> buffer); state chunks keyed by stateId|transferId. */
    private val actionChunks = ConcurrentHashMap<UUID, HashMap<Long, ChunkBuffer>>()
    private val stateChunks = ConcurrentHashMap<String, ChunkBuffer>()

    /**
     * Progress of a large state download (stateId -> received/total chunks), fired on the game
     * thread as each piece lands. Hosts show a loading bar while a big machine streams in.
     */
    @Volatile
    var onStateChunkProgress: ((String, Int, Int) -> Unit)? = null

    /**
     * Progress of a large action upload (requestId -> sent/total chunks), fired on the client thread
     * as each batch goes out. Hosts show a save percentage.
     */
    @Volatile
    var onActionChunkProgress: ((Long, Int, Int) -> Unit)? = null

    /** How many wire chunks leave per throttle batch, and the pause between batches. */
    private const val CHUNKS_PER_BATCH = 3
    private const val CHUNK_BATCH_DELAY_MS = 50L

    /** Collects one logical value from its wire chunks. */
    private class ChunkBuffer(private val total: Int) {
        /** Last chunk arrival - a sliding window, so a long active transfer never "expires". */
        var lastChunkAt = System.currentTimeMillis()
        private val parts = arrayOfNulls<ByteArray>(total)
        private var received = 0

        val receivedCount: Int get() = received

        fun touch() {
            lastChunkAt = System.currentTimeMillis()
        }

        fun put(index: Int, data: ByteArray) {
            if (index < 0 || index >= total) return
            if (parts[index] == null) {
                parts[index] = data
                received++
            }
        }

        val complete: Boolean get() = received == total

        fun join(): ByteArray? {
            val totalBytes = parts.sumOf { it?.size ?: 0 }
            if (totalBytes > BrassChunking.MAX_TOTAL_BYTES) return null
            val out = ByteArrayOutputStream(totalBytes)
            parts.forEach { it?.let(out::write) }
            return out.toByteArray()
        }
    }

    /**
     * Called once per side from the mod event bus. Idempotent: discovery and registration are both
     * safe to repeat, and the payload types are registered exactly once per registrar.
     */
    fun init(registrar: PayloadRegistrar) {
        NeoForgeNetDiscovery.discoverAndLoad()
        if (!BrassNet.isBound()) {
            BrassNet.bind(NeoForgeBrassNetTransport, NeoForgeAuthorizer)
        }
        if (FMLEnvironment.dist.isClient) {
            // Resolve failure messages through Minecraft's own language system, so resource packs
            // translate the built-in codes (keys ship in assets/brassui/lang/en_us.json). A key with
            // no translation resolves to itself, which falls back to the core English catalog.
            BrassMessages.translator = BrassMessages.Translator { code, args ->
                val key = "brassui.net.error.$code"
                val params = arrayOfNulls<Any?>(args.size)
                args.forEachIndexed { index, arg -> params[index] = arg }
                val translated = Component.translatable(key, *params).string
                if (translated == key) null else translated
            }
        }
        registrar.playToServer(BrassActionPayload.TYPE, BrassActionPayload.CODEC, ::onAction)
        registrar.playToServer(BrassSubscribePayload.TYPE, BrassSubscribePayload.CODEC, ::onSubscribe)
        registrar.playToServer(BrassPermsRequestPayload.TYPE, BrassPermsRequestPayload.CODEC, ::onPermsRequest)
        registrar.playToClient(BrassReplyPayload.TYPE, BrassReplyPayload.CODEC, ::onReply)
        registrar.playToClient(BrassStatePayload.TYPE, BrassStatePayload.CODEC, ::onState)
        registrar.playToClient(BrassPermsPayload.TYPE, BrassPermsPayload.CODEC, ::onPerms)
    }

    private fun onAction(payload: BrassActionPayload, ctx: IPayloadContext) {
        val player = ctx.player() as? ServerPlayer ?: return
        ctx.enqueueWork {
            if (payload.version != BrassNet.PROTOCOL_VERSION) {
                replyTo(player, payload.requestId, err(
                    "version.mismatch",
                    payload.version.toString(),
                    BrassNet.PROTOCOL_VERSION.toString(),
                ))
                return@enqueueWork
            }
            // Large actions arrive in pieces; dispatch only once the whole value is here.
            val data = assembleActionChunks(player, payload) ?: return@enqueueWork
            BrassNet.dispatch(payload.actionId, data.takeIf { it.isNotEmpty() }, player.authContext())
                .thenAccept { result ->
                    // The handler may have completed on a worker thread; always send from the server
                    // main thread.
                    player.server.execute { replyTo(player, payload.requestId, result) }
                }
        }
    }

    private fun onReply(payload: BrassReplyPayload, ctx: IPayloadContext) {
        ctx.enqueueWork {
            BrassNet.onReply(payload.requestId, BrassBson.fromWire(payload.data))
        }
    }

    private fun onState(payload: BrassStatePayload, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val data = assembleStateChunks(payload) ?: return@enqueueWork
            NeoForgeStateSubscriptions.deliver(payload.stateId, data.takeIf { it.isNotEmpty() })
        }
    }

    private fun assembleActionChunks(player: ServerPlayer, payload: BrassActionPayload): ByteArray? {
        if (payload.chunks <= 1) return payload.data
        if (payload.chunks.toLong() * BrassChunking.CHUNK_BYTES > BrassChunking.MAX_TOTAL_BYTES) return null
        val byPlayer = actionChunks.computeIfAbsent(player.uuid) { HashMap() }
        val buffer = byPlayer.getOrPut(payload.requestId) { ChunkBuffer(payload.chunks) }
        buffer.put(payload.chunk, payload.data)
        buffer.touch()
        if (System.currentTimeMillis() - buffer.lastChunkAt > CHUNK_TTL_MS) {
            byPlayer.remove(payload.requestId)
            return null
        }
        if (byPlayer.size > 32) {
            // Crude eviction: drop the oldest pending transfer so a hostile flood cannot grow unbounded.
                    byPlayer.entries.minByOrNull { it.value.lastChunkAt }?.let { byPlayer.remove(it.key) }
        }
        if (!buffer.complete) return null
        byPlayer.remove(payload.requestId)
        return buffer.join()
    }

    private fun assembleStateChunks(payload: BrassStatePayload): ByteArray? {
        if (payload.chunks <= 1) return payload.data
        if (payload.chunks.toLong() * BrassChunking.CHUNK_BYTES > BrassChunking.MAX_TOTAL_BYTES) return null
        val key = "${payload.stateId}|${payload.transferId}"
        val buffer = stateChunks.getOrPut(key) { ChunkBuffer(payload.chunks) }
        buffer.put(payload.chunk, payload.data)
        buffer.touch()
        onStateChunkProgress?.invoke(payload.stateId, buffer.receivedCount, payload.chunks)
        if (System.currentTimeMillis() - buffer.lastChunkAt > CHUNK_TTL_MS) {
            stateChunks.remove(key)
            return null
        }
        if (stateChunks.size > 128) stateChunks.clear()
        if (!buffer.complete) return null
        stateChunks.remove(key)
        return buffer.join()
    }

    /** Send [data] as one logical state update to [player], chunked when it is large. */
    internal fun sendStateTo(player: ServerPlayer, stateId: String, data: ByteArray) {
        val pieces = BrassChunking.split(data)
        if (pieces.size == 1) {
            PacketDistributor.sendToPlayer(player, BrassStatePayload(stateId, pieces[0]))
        } else {
            val transferId = stateSeq.incrementAndGet()
            sendChunked(
                count = pieces.size,
                runOnTarget = { block -> player.server.execute(block) },
                sendOne = { i ->
                    PacketDistributor.sendToPlayer(player, BrassStatePayload(stateId, pieces[i], i, pieces.size, transferId))
                },
            )
        }
    }

    /** Broadcast [data] to every player as one logical state update, chunked when it is large. */
    internal fun sendStateToAll(stateId: String, data: ByteArray) {
        val pieces = BrassChunking.split(data)
        if (pieces.size == 1) {
            PacketDistributor.sendToAllPlayers(BrassStatePayload(stateId, pieces[0]))
        } else {
            val transferId = stateSeq.incrementAndGet()
            sendChunked(
                count = pieces.size,
                runOnTarget = { block -> ServerLifecycleHooks.getCurrentServer()?.execute(block) },
                sendOne = { i ->
                    PacketDistributor.sendToAllPlayers(BrassStatePayload(stateId, pieces[i], i, pieces.size, transferId))
                },
            )
        }
    }

    /**
     * Send [count] chunks in small batches on [runOnTarget]'s thread (the server or client main
     * thread), pausing between batches so a very large transfer never hammers the server in one
     * tick - and a host can show progress between batches.
     */
    internal fun sendChunked(
        count: Int,
        runOnTarget: (() -> Unit) -> Unit,
        sendOne: (Int) -> Unit,
        onProgress: ((Int) -> Unit)? = null,
    ) {
        if (count <= CHUNKS_PER_BATCH) {
            runOnTarget {
                for (i in 0 until count) sendOne(i)
                onProgress?.invoke(count)
            }
            return
        }
        Thread {
            var i = 0
            while (i < count) {
                // Capture the batch range in fresh vals: the runnable is deferred to the target
                // thread, so it must not read the loop var `i` by reference (it would have advanced
                // by the time it runs, sending the wrong chunk indexes).
                val start = i
                val end = minOf(i + CHUNKS_PER_BATCH, count)
                runOnTarget {
                    for (k in start until end) sendOne(k)
                    onProgress?.invoke(end)
                }
                i = end
                if (i < count) Thread.sleep(CHUNK_BATCH_DELAY_MS)
            }
        }.apply {
            isDaemon = true
            name = "brassui-chunked-send"
        }.start()
    }

    private fun onSubscribe(payload: BrassSubscribePayload, ctx: IPayloadContext) {
        val player = ctx.player() as? ServerPlayer ?: return
        ctx.enqueueWork {
            // A late subscriber gets the current value, not just future changes. Unknown ids answer
            // nothing and the client keeps whatever placeholder it showed.
            BrassNet.snapshot(payload.stateId)?.let { data ->
                // Chunk the snapshot exactly like a live publish - an unbounded single payload here
                // is how a very large machine kicked its owner on open.
                sendStateTo(player, payload.stateId, data)
            }
        }
    }

    private fun onPermsRequest(payload: BrassPermsRequestPayload, ctx: IPayloadContext) {
        val player = ctx.player() as? ServerPlayer ?: return
        ctx.enqueueWork {
            val entries = BrassNet.computePermissions(player.authContext())
            val data = BrassBson.toBytes(entries.mapValues { (_, decision) -> decision.encode() })
            PacketDistributor.sendToPlayer(player, BrassPermsPayload(data))
        }
    }

    private fun onPerms(payload: BrassPermsPayload, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val raw = BrassBson.fromBytes(payload.data, Map::class.java) as? Map<*, *> ?: return@enqueueWork
            val decoded = HashMap<String, AuthDecision>()
            for ((key, value) in raw) {
                val encoded = value?.toString() ?: return@enqueueWork
                decoded[key.toString()] = decodeAuthDecision(encoded)
            }
            BrassNet.applyPermissions(decoded)
        }
    }

    private fun replyTo(player: ServerPlayer, requestId: Long, result: BrassActionResult) {
        PacketDistributor.sendToPlayer(player, BrassReplyPayload(requestId, BrassBson.toWire(result)))
    }
}

/** The NeoForge [BrassNetTransport]: the game connection as the wire, the game thread as the UI thread. */
object NeoForgeBrassNetTransport : BrassNetTransport {

    override val name = "NeoForge"
    override val local = false

    override val identity: String
        get() = Minecraft.getInstance().player?.gameProfile?.name ?: "—"

    override fun can(action: BrassAction<*>): AuthDecision {
        val player = Minecraft.getInstance().player ?: return AuthDecision.Deny("no player")
        return if (player.getPermissionLevel() >= action.minOpLevel) {
            AuthDecision.Grant
        } else {
            AuthDecision.Deny("requires op level ${action.minOpLevel}")
        }
    }

    override fun sendAction(
        requestId: Long,
        actionId: String,
        data: ByteArray?,
        reply: (BrassActionResult) -> Unit,
    ) {
        val connection = Minecraft.getInstance().connection
        if (connection == null) {
            // Nothing to send to - fail with a friendly code instead of throwing inside the network
            // stack. (The main menu has no connection; the integrated server does.)
            onUiThread { reply(err("no.connection")) }
            return
        }
        // The reply is not produced here: it arrives asynchronously via onReply and the pending map.
        val pieces = BrassChunking.split(data ?: ByteArray(0))
        NeoForgeBrassNet.sendChunked(
            count = pieces.size,
            runOnTarget = { block -> Minecraft.getInstance().execute(block) },
            sendOne = { i ->
                PacketDistributor.sendToServer(
                    BrassActionPayload(requestId, BrassNet.PROTOCOL_VERSION, actionId, pieces[i], i, pieces.size),
                )
            },
            onProgress = { sent -> NeoForgeBrassNet.onActionChunkProgress?.invoke(requestId, sent, pieces.size) },
        )
    }

    override fun requestPermissions() {
        if (Minecraft.getInstance().connection != null) {
            PacketDistributor.sendToServer(BrassPermsRequestPayload)
        }
    }

    override fun subscribe(stateId: String, onUpdate: (ByteArray?) -> Unit): () -> Unit {
        val handle = NeoForgeStateSubscriptions.subscribe(stateId, onUpdate)
        // Ask the server for the current value; the snapshot arrives as a BrassStatePayload.
        PacketDistributor.sendToServer(BrassSubscribePayload(stateId))
        return handle
    }

    override fun publish(stateId: String, data: ByteArray?, toPlayer: String?) {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        // Handlers may be async (worker threads); hop to the server thread before touching the packet
        // distributor, which is not safe from arbitrary threads.
        server.execute {
            val target = toPlayer?.let { id -> runCatching { server.playerList.getPlayer(UUID.fromString(id)) }.getOrNull() }
            if (target != null) {
                NeoForgeBrassNet.sendStateTo(target, stateId, data ?: ByteArray(0))
            } else {
                NeoForgeBrassNet.sendStateToAll(stateId, data ?: ByteArray(0))
            }
        }
    }

    override fun onUiThread(runnable: Runnable) {
        Minecraft.getInstance().execute(runnable)
    }
}

/**
 * The server-side authorizer. When a custom permission handler is active (a permission mod, say), the
 * action's declared permission node is resolved through PermissionAPI and its answer wins. With the
 * default handler - or no handler at all - the fallback is the op-level check, exactly like
 * [net.swzo.brass.ui.kit.net.BrassAuthorizers.byOpLevel].
 */
object NeoForgeAuthorizer : BrassAuthorizer {

    private val nodes = ConcurrentHashMap<String, PermissionNode<Boolean>>()

    /** The PermissionAPI node backing [action], created once per permission string. */
    internal fun nodeFor(action: BrassAction<*>): PermissionNode<Boolean> = nodes.getOrPut(action.permission) {
        PermissionNode(
            "brassui",
            action.permission,
            PermissionTypes.BOOLEAN,
            PermissionResolver { player, _, _ ->
                player?.hasPermissions(action.minOpLevel) == true
            },
        )
    }

    override fun check(action: BrassAction<*>, ctx: AuthContext): AuthDecision {
        val player = ctx.playerId?.let { id ->
            runCatching { ServerLifecycleHooks.getCurrentServer()?.playerList?.getPlayer(UUID.fromString(id)) }.getOrNull()
        }
        if (player != null && PermissionAPI.getActivePermissionHandler() != DefaultPermissionHandler.IDENTIFIER) {
            val allowed = runCatching { PermissionAPI.getPermission(player, nodeFor(action)) }.getOrNull()
            if (allowed != null) {
                return if (allowed) AuthDecision.Grant else AuthDecision.Deny("permission denied")
            }
        }
        return if (ctx.opLevel >= action.minOpLevel) AuthDecision.Grant
        else AuthDecision.Deny("requires op level ${action.minOpLevel}")
    }
}

private fun ServerPlayer.authContext(): AuthContext {
    // The ops list is the server's own permission data - getProfilePermissions is the same lookup the
    // login handler uses to decide operator status.
    val opLevel = runCatching { server.getProfilePermissions(gameProfile) }.getOrDefault(0)
    return AuthContext(gameProfile.id.toString(), opLevel)
}

/**
 * Client-side state subscriptions, keyed by state id. [NeoForgeBrassNet.onState] delivers into this
 * on the render thread, so widget updates never race the network thread.
 */
object NeoForgeStateSubscriptions {

    private val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<(ByteArray?) -> Unit>>()

    fun subscribe(stateId: String, onUpdate: (ByteArray?) -> Unit): () -> Unit {
        val list = subscribers.computeIfAbsent(stateId) { CopyOnWriteArrayList() }
        list.add(onUpdate)
        return { list.remove(onUpdate) }
    }

    fun deliver(stateId: String, data: ByteArray?) {
        subscribers[stateId]?.forEach { it(data) }
    }
}
