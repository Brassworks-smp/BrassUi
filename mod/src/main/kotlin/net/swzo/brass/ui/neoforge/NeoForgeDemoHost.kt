package net.swzo.brass.ui.neoforge

import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.demo.BrassDemoHost
import net.swzo.brass.ui.kit.base.BrassChrome
import java.awt.Color

/**
 * The gallery as the in-game `/brassui` screen.
 * This is the whole of what the gallery needs from Minecraft. Everything else in that screen — every
 * widget, every section, including the item slots, mobs, block models and inventory grids — compiles
 * in a module with no Minecraft on its classpath, because the toolkit names game content with string
 * ids and resolves them through [net.swzo.brass.ui.kit.platform.BrassPlatform].
 * What is left is the part that genuinely cannot be abstracted: the raw-canvas cards, whose entire
 * purpose is to show a caller dropping out of the toolkit into `GuiGraphics`.
 */
class NeoForgeDemoHost : BrassDemoHost {

    override val subtitle = "widget toolkit"

    override val backdrop: Color = SCREEN_DIM

    override val gameWidgets = true

    override val playerName: String
        get() = Minecraft.getInstance().player?.gameProfile?.name ?: "Steve"

    override fun close() {
        Minecraft.getInstance().setScreen(null)
    }

    override fun open(screen: net.swzo.brass.ui.BrassScreen) {
        Minecraft.getInstance().setScreen(screen)
    }

    override fun rawCanvases(): List<BrassDemoHost.DemoCanvas> = listOf(
        BrassDemoHost.DemoCanvas(
            // The oversized item deliberately overruns the box, to show the clip doing its job.
            brassCanvas { g, w, h ->
                g.pose().pushPose()
                g.pose().translate(w / 2.0, h / 2.0, 0.0)
                g.pose().scale(4f, 4f, 1f)
                g.renderItem(ItemStack(Items.NETHER_STAR), -8, -8)
                g.pose().popPose()
            },
            48f,
        ),
        BrassDemoHost.DemoCanvas(
            // No card at all: a bare rectangle of vanilla drawing, sized by constraints like anything else.
            brassCanvas(chrome = BrassChrome.NONE, depth = false) { g, w, h ->
                g.fillGradient(0, 0, w.toInt(), h.toInt(), 0xFF1B3A2A.toInt(), 0xFF0E1A14.toInt())
                g.drawString(Minecraft.getInstance().font, "vanilla text", 4, 4, 0xFF7BD88F.toInt())
            },
            72f,
        ),
    )

    private companion object {
        val SCREEN_DIM: Color = Color(
            Colors.UI_BACKGROUND.red, Colors.UI_BACKGROUND.green, Colors.UI_BACKGROUND.blue, 150,
        )
    }
}
