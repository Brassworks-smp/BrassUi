package net.swzo.brass.ui.demo

import gg.essential.elementa.UIComponent
import net.swzo.brass.ui.BrassScreen
import java.awt.Color

/**
 * What the gallery needs from whoever is showing it.
 *
 * The gallery is one screen with two hosts — the standalone desktop app and the in-game `/brassui`
 * command — and almost all of it is identical between them, because the toolkit itself is game-free.
 * This interface is the short list of things that genuinely are not, kept explicit so the difference
 * between the two hosts is *this file* rather than two diverging copies of a 900-line screen.
 *
 * ### Why so little is in here
 *
 * Less than you would expect, because [net.swzo.brass.ui.kit.platform.BrassPlatform] already did the
 * hard part. The item, entity, block, head and effect widgets take **string ids** — `"minecraft:allay"`,
 * not an `EntityType` — precisely so a caller can name game content without linking against the game.
 * The whole "Items" section therefore compiles here unchanged; it only needs [gameWidgets] to say
 * whether the running platform can actually draw any of it.
 *
 * What is left is the genuinely host-shaped remainder: how to close, what to call itself, and the raw
 * canvas cards — which cannot be abstracted because demoing the escape hatch out of the toolkit is
 * their entire point.
 */
interface BrassDemoHost {

    /** Shown under the window title, so it is obvious which host you are looking at. */
    val subtitle: String

    /** Close the gallery — quit the process on the desktop, pop the screen in game. */
    fun close()

    /**
     * Show [screen] in this host's surface, replacing whatever is up.
     *
     * The gallery's Demos section hands off to [net.swzo.brass.ui.kit.demo.BrassDemoBrowser], which is
     * a whole screen rather than a popup — a demo previews at 1:1 and the wider ones do not fit inside
     * a panel floating in the gallery. Swapping screens is host-shaped in exactly the way [close] is:
     * in game it is `setScreen`, on the desktop it is re-rooting the window.
     */
    fun open(screen: BrassScreen)

    /**
     * Backdrop behind the gallery, or null for the toolkit's opaque default.
     *
     * In game this wants to be **translucent**, so the running world shows through the way it does
     * behind an inventory screen. On the desktop there is nothing behind it to show.
     */
    val backdrop: Color? get() = null

    /**
     * Whether the gallery window fills the whole surface rather than floating inside it.
     *
     * True on the desktop, where the OS window *is* the frame: an inset there leaves a band of
     * backdrop framing nothing, and the minimise/maximise/close keys duplicate the OS chrome a level
     * up. In game the window floats over the world, so it keeps its controls and its margin.
     */
    val fillsSurface: Boolean get() = false

    /**
     * Whether the platform can draw game content (items, entities, blocks, heads, effect icons).
     *
     * False on the desktop, where [net.swzo.brass.ui.kit.platform.BrassPlatform] is bound to a
     * cursor-only implementation: the widgets would draw empty boxes, which demos nothing and reads
     * as a bug. The section is left out entirely instead.
     */
    val gameWidgets: Boolean get() = false

    /**
     * A player whose skin the host can actually resolve, for the head widgets.
     *
     * In game this is the local player — the one skin guaranteed to be in the client's player list.
     */
    val playerName: String get() = "Steve"

    /**
     * Extra cards for the "Items" section demonstrating the raw drawing escape hatch, or empty.
     *
     * This is the one thing that resists the string-id treatment above. `BrassCanvas` exists so a
     * caller can drop to the host's own drawing API — `GuiGraphics` in game — inside a widget that
     * still lays out like every other widget. A demo of that is game code by definition, so the host
     * supplies it and the gallery just places whatever it gets.
     */
    fun rawCanvases(): List<DemoCanvas> = emptyList()

    /** One [rawCanvases] card: the component and the row height to give it. */
    class DemoCanvas(val component: UIComponent, val height: Float)
}
