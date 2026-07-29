package net.swzo.brass.ui.desktop

import gg.essential.universal.UMinecraft
import gg.essential.universal.UResolution
import gg.essential.universal.UScreen
import gg.essential.universal.standalone.runUniversalCraft
import net.swzo.brass.ui.demo.BrassGalleryScreen
import net.swzo.brass.ui.kit.demo.BrassDemoCapture
import net.swzo.brass.ui.kit.platform.BrassPlatform

/**
 * Entry point for the standalone desktop demo.
 *
 * [runUniversalCraft] opens a real GLFW/LWJGL window with an OpenGL context and a NanoVG-backed font
 * (all from `universalcraft-standalone`, no Minecraft), then hands back a [gg.essential.universal.standalone.UCWindow].
 * We display a Brass Elementa screen on it and drive the render loop until the window is closed — the
 * desktop equivalent of the in-game `/brassui` showcase, proving the toolkit runs unchanged off-game.
 *
 * Window geometry and scale are system properties so the Rust wrapper (and IDE run configs) can drive
 * them without a rebuild, matching how EssentialInstaller does it.
 */
private val width = System.getProperty("ui.width")?.toIntOrNull() ?: 1280
private val height = System.getProperty("ui.height")?.toIntOrNull() ?: 800
private val scaleFactor = System.getProperty("ui.scaleFactor")?.toIntOrNull() ?: 2
private val resizable = System.getProperty("ui.resizable")?.toBoolean() ?: true

fun main() {
    // Window size is in SCREEN coordinates and deliberately NOT multiplied by the scale factor.
    // EssentialInstaller does multiply, but its window is small and fixed-size; ours is a full demo
    // window, and 2x of it would be larger than a laptop display. Here the scale factor is pure zoom:
    // the window keeps its size and the UI inside it gets bigger, with less fitting on screen. Both
    // panes are scrollable, so the content that no longer fits scrolls instead of being clipped.
    runUniversalCraft("brassui desktop demo", width, height, resizable) { window ->
        // Elementa sizes everything in GUI units, which are framebuffer pixels divided by the gui scale.
        // Without this the toolkit draws at one unit per physical pixel, so on a HiDPI/Retina display
        // (framebuffer 2x the window) the whole UI comes out at half size. viewport/window is that
        // backing-scale ratio, so this makes one GUI unit equal one screen point, times any extra zoom
        // the caller asked for via -Dui.scaleFactor.
        UMinecraft.guiScale = scaleFactor * (UResolution.viewportWidth / UResolution.windowWidth)

        println(
            "[brassui] window ${UResolution.windowWidth}x${UResolution.windowHeight}, " +
                "framebuffer ${UResolution.viewportWidth}x${UResolution.viewportHeight}, " +
                "guiScale ${UMinecraft.guiScale} -> ${UResolution.scaledWidth}x${UResolution.scaledHeight} GUI units"
        )

        // Bind the platform seam before any screen opens, so hover cursors work from the first frame.
        // The GLFW window handle is the only thing the desktop end needs.
        BrassPlatform.bind(DesktopPlatform(window.glfwWindow.glfwId))

        // Bind the capture seam too, so the demo browser's shutter works off-game: it reads the region
        // straight off the GL framebuffer. Point output at the wiki with -Dbrassui.shots.dir=...
        BrassDemoCapture.bind(DesktopDemoCapture())

        UScreen.displayScreen(BrassGalleryScreen(DesktopDemoHost()))
        window.renderScreenUntilClosed()
    }
}
