package net.swzo.brass.ui.neoforge.net

import net.minecraft.server.level.ServerPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.swzo.brass.ui.kit.net.BrassNet

/**
 * Connection lifecycle wiring. On login the client refreshes its permission mirror (so buttons reflect
 * the server's real decisions) and forgets any stale version mismatch; on logout every in-flight
 * request fails with `no.connection` instead of dangling until its timeout. On the server, a player's
 * rate-limit windows are dropped when they leave so state cannot linger.
 */
@EventBusSubscriber(modid = "brassui", value = [Dist.CLIENT])
object NeoForgeNetClientEvents {

    @SubscribeEvent
    @JvmStatic
    fun onLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
        BrassNet.resetProtocolMismatch()
        BrassNet.refreshPermissions()
    }

    @SubscribeEvent
    @JvmStatic
    fun onLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
        BrassNet.failPending("no.connection")
    }
}

@EventBusSubscriber(modid = "brassui")
object NeoForgeNetServerEvents {

    @SubscribeEvent
    @JvmStatic
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        BrassNet.registry.clearPlayer(player.gameProfile.id.toString())
    }
}
