package net.swzo.brass.ui.neoforge.net

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent
import net.swzo.brass.ui.kit.net.BrassNet

/**
 * Wires the networking module on the mod event bus, on **both** sides: no `Dist` is declared, so a
 * dedicated server registers the payloads and discovers action sets exactly like a client. This is the
 * one class that makes the single brassui jar self-sufficient for UI -> server logic - host mods never
 * register anything themselves.
 */
@EventBusSubscriber(modid = "brassui", bus = EventBusSubscriber.Bus.MOD)
object BrassNetNeoForgeEvents {

    @SubscribeEvent
    @JvmStatic
    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        NeoForgeBrassNet.init(event.registrar("brassui"))
    }

    /**
     * Publish a PermissionAPI node per registered action, so permission mods can override the op-level
     * default. Discovery is idempotent and runs here too because this event can fire before or after
     * [registerPayloads] - whichever comes first, the actions are known by the time nodes are needed.
     */
    @SubscribeEvent
    @JvmStatic
    fun gatherPermissions(event: PermissionGatherEvent.Nodes) {
        NeoForgeNetDiscovery.discoverAndLoad()
        for (action in BrassNet.registry.all()) {
            event.addNodes(NeoForgeAuthorizer.nodeFor(action))
        }
    }
}
