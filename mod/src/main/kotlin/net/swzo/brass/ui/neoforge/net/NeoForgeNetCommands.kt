package net.swzo.brass.ui.neoforge.net

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.swzo.brass.ui.kit.net.BrassJson
import net.swzo.brass.ui.kit.net.BrassNet

/**
 * The `/brassui action <id> <json>` test bridge: send any registered action from chat without a UI,
 * and print its translated outcome. Handy for exercising actions headlessly in singleplayer.
 */
object NeoForgeNetCommands {

    fun sendAction(id: String, json: String): String {
        val action = BrassNet.registry.get<Any>(id)
            ?: return "Unknown action: $id"
        val input = when {
            action.inputType == Unit::class.java -> Unit
            else -> BrassJson.fromJson(json, action.inputType)
                ?: return "Could not parse JSON for $id: $json"
        }
        BrassNet.send(action, input) { result ->
            Minecraft.getInstance().player?.sendSystemMessage(
                Component.literal("[$id] ${result.message}"),
            )
        }
        return "Sent $id"
    }
}
