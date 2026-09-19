package net.swzo.brass.ui.neoforge

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.swzo.brass.ui.kit.platform.BrassPlatform

/**
 * Binds the Minecraft-backed [BrassPlatform] seam as soon as the client finishes setup, so item
 * rendering and tooltips work in any screen from the very first frame. (The same bind also happens
 * later inside BrassUiClientCommands - idempotent, but never rely on a command event for something a
 * screen needs at open time.)
 */
// MC 26.x unified the mod/game event buses, so @EventBusSubscriber dropped the `bus` param + Bus enum.
//? if >=26.1 {
/*@EventBusSubscriber(modid = "brassui", value = [Dist.CLIENT])*/
//?} else {
@EventBusSubscriber(modid = "brassui", bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
//?}
object NeoForgePlatformSetup {

    @SubscribeEvent
    @JvmStatic
    fun onClientSetup(event: FMLClientSetupEvent) {
        BrassPlatform.bind(NeoForgePlatform)
    }
}
