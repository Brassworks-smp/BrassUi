package net.swzo.brass.ui.neoforge.shot

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.swzo.brass.ui.kit.demo.BrassDemoCapture
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * The game-side half of [BrassDemoCapture]: read a rectangle back off the frame, and encode PNGs/GIFs.
 * ### Why it reads the screen rather than rendering offscreen
 * The widget is already on screen — the browser is previewing it — drawn by the normal GUI pass with
 * every one of its clip rectangles resolved against the real framebuffer. An earlier version rendered
 * the component a second time into a small offscreen target, and everything that scissors its contents
 * (the tree, the table, the code view, the chart, the accordion, the dropdown) came back blank, because
 * `ScissorEffect` computes its clip in screen pixels and that surface was not the screen. Reading the
 * region the widget occupies sidesteps the whole problem: whatever rendered correctly is what gets
 * captured, and there is nothing to allocate or free between grabs.
 */
object NeoForgeDemoCapture : BrassDemoCapture {

    private fun outDir(): Path =
        System.getProperty("brassui.shots.dir")?.let { Path.of(it) }
            ?: Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("brassui")

    override fun grab(x: Float, y: Float, width: Float, height: Float): BufferedImage? = runCatching {
        val mc = Minecraft.getInstance()
        val target = mc.mainRenderTarget
        val fbW = target.width
        val fbH = target.height
        val gs = mc.window.guiScale

        // Clamp into the framebuffer: a widget dragged partly off-screen must still produce a valid
        // file of the part that is on-screen rather than reading out of bounds.
        val px = (x * gs).roundToInt().coerceIn(0, fbW)
        val py = (y * gs).roundToInt().coerceIn(0, fbH)
        val pw = (width * gs).roundToInt().coerceIn(1, (fbW - px).coerceAtLeast(1))
        val ph = (height * gs).roundToInt().coerceIn(1, (fbH - py).coerceAtLeast(1))

        NativeImage(fbW, fbH, false).use { image ->
            target.bindRead()
            image.downloadTexture(0, false)
            // GL textures start bottom-left; images start top-left. Flip once so (px, py) is measured
            // from the top, matching the GUI coordinates the browser handed in.
            image.flipY()
            target.unbindRead()

            val out = BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB)
            for (row in 0 until ph) {
                for (col in 0 until pw) {
                    // NativeImage packs ABGR little-endian; BufferedImage wants ARGB, so red and blue
                    // swap. The main framebuffer's alpha is meaningless by the time the GUI has drawn,
                    // so force the pixel opaque rather than carrying through a transparency that is not
                    // real and would punch holes in the asset.
                    val abgr = image.getPixelRGBA(px + col, py + row)
                    val b = (abgr ushr 16) and 0xFF
                    val g = (abgr ushr 8) and 0xFF
                    val r = abgr and 0xFF
                    out.setRGB(col, row, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
                }
            }
            out
        }
    }.getOrNull()

    override fun writePng(name: String, image: BufferedImage): String? = runCatching {
        val path = unique(name, "png")
        Files.createDirectories(path.parent)
        ImageIO.write(image, "png", path.toFile())
        display(path)
    }.getOrNull()

    override fun writeGif(name: String, frames: List<BufferedImage>, fps: Int): String? = runCatching {
        if (frames.isEmpty()) return null
        val path = unique(name, "gif")
        Files.createDirectories(path.parent)
        if (!BrassGif.write(path, frames, fps)) return null
        display(path)
    }.getOrNull()

    private fun unique(name: String, ext: String): Path {
        val dir = outDir()
        val first = dir.resolve("$name.$ext")
        if (!Files.exists(first)) return first
        var n = 2
        while (Files.exists(dir.resolve("$name-$n.$ext"))) n++
        return dir.resolve("$name-$n.$ext")
    }

    private fun display(path: Path): String =
        runCatching { Minecraft.getInstance().gameDirectory.toPath().relativize(path).toString() }
            .getOrDefault(path.fileName.toString())
}
