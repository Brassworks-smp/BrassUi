package net.swzo.brass.ui.demo

import gg.essential.elementa.UIComponent
import net.swzo.brass.ui.BrassScreen
import java.awt.Color

/**
 * What the gallery needs from whoever is showing it.
 * The gallery is one screen with two hosts — the standalone desktop app and the in-game `/brassui`
 * command — and almost all of it is identical between them, because the toolkit itself is game-free.
 * This interface is the short list of things that genuinely are not, kept explicit so the difference
 * between the two hosts is *this file* rather than two diverging copies of a 900-line screen.
 * ### Why so little is in here
 * Less than you would expect, because [net.swzo.brass.ui.kit.platform.BrassPlatform] already did the
 * hard part. The item, entity, block, head and effect widgets take **string ids** — `"minecraft:allay"`,
 * not an `EntityType` — precisely so a caller can name game content without linking against the game.
 * The whole "Items" section therefore compiles here unchanged; it only needs [gameWidgets] to say
 * whether the running platform can actually draw any of it.
 * What is left is the genuinely host-shaped remainder: how to close, what to call itself, and the raw
 * canvas cards — which cannot be abstracted because demoing the escape hatch out of the toolkit is
 * their entire point.
 */
interface BrassDemoHost {

    val subtitle: String

    fun close()

    fun open(screen: BrassScreen)

    val backdrop: Color? get() = null

    val fillsSurface: Boolean get() = false

    val gameWidgets: Boolean get() = false

    val playerName: String get() = "Steve"

    fun rawCanvases(): List<DemoCanvas> = emptyList()

    class DemoCanvas(val component: UIComponent, val height: Float)
}
