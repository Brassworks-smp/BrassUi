package net.swzo.brass.ui.neoforge.net

import com.google.gson.reflect.TypeToken
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
import net.swzo.brass.ui.kit.net.BrassJson
import net.swzo.brass.ui.kit.net.BrassMessages
import net.swzo.brass.ui.kit.net.BrassNet
import net.swzo.brass.ui.kit.net.BrassNetTransport
import net.swzo.brass.ui.kit.net.decodeAuthDecision
import net.swzo.brass.ui.kit.net.encode
import net.swzo.brass.ui.kit.net.err
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
            BrassNet.dispatch(payload.actionId, payload.json.ifEmpty { null }, player.authContext())
                .thenAccept { result ->
                    // The handler may have completed on a worker thread; always send from the server
                    // main thread.
                    player.server.execute { replyTo(player, payload.requestId, result) }
                }
        }
    }

    private fun onReply(payload: BrassReplyPayload, ctx: IPayloadContext) {
        ctx.enqueueWork {
            BrassNet.onReply(payload.requestId, BrassJson.fromWire(payload.json))
        }
    }

    private fun onState(payload: BrassStatePayload, ctx: IPayloadContext) {
        ctx.enqueueWork {
            NeoForgeStateSubscriptions.deliver(payload.stateId, payload.json.ifEmpty { null })
        }
    }

    private fun onSubscribe(payload: BrassSubscribePayload, ctx: IPayloadContext) {
        val player = ctx.player() as? ServerPlayer ?: return
        ctx.enqueueWork {
            // A late subscriber gets the current value, not just future changes. Unknown ids answer
            // nothing and the client keeps whatever placeholder it showed.
            BrassNet.snapshot(payload.stateId)?.let { json ->
                PacketDistributor.sendToPlayer(player, BrassStatePayload(payload.stateId, json))
            }
        }
    }

    private fun onPermsRequest(payload: BrassPermsRequestPayload, ctx: IPayloadContext) {
        val player = ctx.player() as? ServerPlayer ?: return
        ctx.enqueueWork {
            val entries = BrassNet.computePermissions(player.authContext())
            val json = BrassJson.gson.toJson(entries.mapValues { (_, decision) -> decision.encode() })
            PacketDistributor.sendToPlayer(player, BrassPermsPayload(json))
        }
    }

    private fun onPerms(payload: BrassPermsPayload, ctx: IPayloadContext) {
        ctx.enqueueWork {
            val raw = runCatching {
                BrassJson.gson.fromJson<Map<String, String>>(
                    payload.json,
                    object : TypeToken<Map<String, String>>() {}.type,
                )
            }.getOrNull() ?: return@enqueueWork
            BrassNet.applyPermissions(raw.mapValues { (_, encoded) -> decodeAuthDecision(encoded) })
        }
    }

    private fun replyTo(player: ServerPlayer, requestId: Long, result: BrassActionResult) {
        PacketDistributor.sendToPlayer(player, BrassReplyPayload(requestId, BrassJson.toWire(result)))
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
        json: String?,
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
        PacketDistributor.sendToServer(BrassActionPayload(requestId, BrassNet.PROTOCOL_VERSION, actionId, json ?: ""))
    }

    override fun requestPermissions() {
        if (Minecraft.getInstance().connection != null) {
            PacketDistributor.sendToServer(BrassPermsRequestPayload)
        }
    }

    override fun subscribe(stateId: String, onUpdate: (String?) -> Unit): () -> Unit {
        val handle = NeoForgeStateSubscriptions.subscribe(stateId, onUpdate)
        // Ask the server for the current value; the snapshot arrives as a BrassStatePayload.
        PacketDistributor.sendToServer(BrassSubscribePayload(stateId))
        return handle
    }

    override fun publish(stateId: String, json: String?, toPlayer: String?) {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        val payload = BrassStatePayload(stateId, json ?: "")
        // Handlers may be async (worker threads); hop to the server thread before touching the packet
        // distributor, which is not safe from arbitrary threads.
        server.execute {
            val target = toPlayer?.let { id -> runCatching { server.playerList.getPlayer(UUID.fromString(id)) }.getOrNull() }
            if (target != null) {
                PacketDistributor.sendToPlayer(target, payload)
            } else {
                PacketDistributor.sendToAllPlayers(payload)
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

    private val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<(String?) -> Unit>>()

    fun subscribe(stateId: String, onUpdate: (String?) -> Unit): () -> Unit {
        val list = subscribers.computeIfAbsent(stateId) { CopyOnWriteArrayList() }
        list.add(onUpdate)
        return { list.remove(onUpdate) }
    }

    fun deliver(stateId: String, json: String?) {
        subscribers[stateId]?.forEach { it(json) }
    }
}
