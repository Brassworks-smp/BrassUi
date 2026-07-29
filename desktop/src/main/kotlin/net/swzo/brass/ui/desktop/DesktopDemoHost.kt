package net.swzo.brass.ui.desktop

import net.swzo.brass.ui.demo.BrassDemoHost
import kotlin.system.exitProcess

/**
 * The gallery as a desktop application.
 *
 * Everything here is a default except the two things that genuinely are: closing means ending the
 * process, and the window is the app rather than something floating inside it.
 *
 * [BrassDemoHost.gameWidgets] stays false because [DesktopPlatform] binds only the cursor — there is
 * no item renderer off-game, so the section that draws game content is left out rather than shown
 * empty. Wiring a desktop implementation of those draw calls (a texture-atlas reader, say) is the one
 * change that would make the desktop gallery complete, and this flag is where it would turn on.
 */
class DesktopDemoHost : BrassDemoHost {

    override val subtitle = "desktop demo"

    override val fillsSurface = true

    override fun close() {
        exitProcess(0)
    }

    /**
     * Swap the displayed screen.
     *
     * `UScreen.displayScreen` is what `Main` used to put the gallery up in the first place, and the
     * standalone render loop draws whatever is current — so re-displaying is the whole of it. `Main`
     * binds a [DesktopDemoCapture], so the browser this opens has a working shutter that reads the demo
     * off the GL framebuffer — the desktop way to generate the wiki's screenshots.
     */
    override fun open(screen: net.swzo.brass.ui.BrassScreen) {
        gg.essential.universal.UScreen.displayScreen(screen)
    }
}
