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
 *
 * This is the whole of what the gallery needs from Minecraft. Everything else in that screen — every
 * widget, every section, including the item slots, mobs, block models and inventory grids — compiles
 * in a module with no Minecraft on its classpath, because the toolkit names game content with string
 * ids and resolves them through [net.swzo.brass.ui.kit.platform.BrassPlatform].
 *
 * What is left is the part that genuinely cannot be abstracted: the raw-canvas cards, whose entire
 * purpose is to show a caller dropping out of the toolkit into `GuiGraphics`.
 */
class NeoForgeDemoHost : BrassDemoHost {

    override val subtitle = "widget toolkit"

    /** The world keeps running behind an in-game screen, so the backdrop is translucent. */
    override val backdrop: Color = SCREEN_DIM

    /** The platform seam is bound to NeoForge here, so all the game-content widgets can draw. */
    override val gameWidgets = true

    /** The local player is the one skin guaranteed to be in the client's player list. */
    override val playerName: String
        get() = Minecraft.getInstance().player?.gameProfile?.name ?: "Steve"

    override fun close() {
        Minecraft.getInstance().setScreen(null)
    }

    override fun open(screen: net.swzo.brass.ui.BrassScreen) {
        Minecraft.getInstance().setScreen(screen)
    }

    /**
     * Two canvases: one on a card and clipped to it, one with no chrome at all.
     *
     * Nothing here goes through the toolkit's painter — these are raw `GuiGraphics` calls, landing in
     * the right place because the platform adopted Elementa's matrix and translated to the widget.
     */
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
        /**
         * Translucent app fill for the in-game gallery: the launcher ink at partial alpha, so the
         * running game shows through behind the toolkit the way it does behind an inventory screen.
         */
        val SCREEN_DIM: Color = Color(
            Colors.UI_BACKGROUND.red, Colors.UI_BACKGROUND.green, Colors.UI_BACKGROUND.blue, 150,
        )
    }
}
