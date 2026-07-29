package net.swzo.brass.ui.desktop

import gg.essential.universal.UResolution
import net.swzo.brass.ui.kit.demo.BrassDemoCapture
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream

/**
 * The desktop end of the [BrassDemoCapture] seam: read a rectangle of the demo straight off the GL
 * framebuffer and write it to a file. This is what turns the demo browser's shutter on outside the game,
 * so the wiki screenshots can be generated from the standalone app with no Minecraft running.
 *
 * ### Why it reads the framebuffer rather than re-drawing
 *
 * Same reason the in-game host does (see [BrassDemoCapture.grab]): the widget is already on screen with
 * every scissor clip resolved against the real backing buffer, and `glReadPixels` on the region it
 * occupies gets those exact pixels. A demo that masks its contents comes out right because it is
 * photographed where it actually rendered.
 *
 * ### No transparency, on purpose
 *
 * The capture is the window as shown, background and all. The frames are written as opaque RGB, so a
 * shot is just a picture of that part of the window rather than a widget floating on a checkerboard,
 * which is what a page wants anyway.
 *
 * ### Where files land
 *
 * Under [outputDir], which defaults to `screenshots/` beside the working directory and can be pointed
 * at the wiki with `-Dbrassui.shots.dir=/path/to/BrassUiWiki/public/screenshots`. The file name is the
 * demo's stable id, so a shot of the "number-input" demo overwrites `number-input.png` and drops
 * straight into the slot the wiki already looks for.
 */
class DesktopDemoCapture : BrassDemoCapture {

    private val outputDir: File =
        File(System.getProperty("brassui.shots.dir") ?: (System.getProperty("user.dir") + "/screenshots"))

    /**
     * Framebuffer pixels per GUI unit. Elementa lays out in GUI units; `glReadPixels` wants backing
     * pixels, and on a HiDPI display those differ by the gui scale. This ratio bridges them.
     */
    private fun scale(): Double =
        UResolution.viewportWidth.toDouble() / UResolution.scaledWidth.toDouble()

    override fun grab(x: Float, y: Float, width: Float, height: Float): BufferedImage? {
        val s = scale()
        val px = Math.round(x * s).toInt()
        val pw = Math.round(width * s).toInt()
        val ph = Math.round(height * s).toInt()
        if (pw <= 0 || ph <= 0) return null

        // GL's origin is the bottom-left of the framebuffer; the UI's is the top-left. Flip the y of the
        // region's bottom edge into GL space before reading.
        val fbY = UResolution.viewportHeight - Math.round((y + height) * s).toInt()
        if (px < 0 || fbY < 0) return null

        val buf = BufferUtils.createByteBuffer(pw * ph * 4)
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1)
        GL11.glReadPixels(px, fbY, pw, ph, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf)

        val image = BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB)
        for (row in 0 until ph) {
            // Row 0 of the GL read is the bottom of the region; flip back so the image is upright.
            val src = ph - 1 - row
            var i = src * pw * 4
            for (col in 0 until pw) {
                val r = buf.get(i).toInt() and 0xFF
                val g = buf.get(i + 1).toInt() and 0xFF
                val b = buf.get(i + 2).toInt() and 0xFF
                image.setRGB(col, row, (r shl 16) or (g shl 8) or b)
                i += 4
            }
        }
        return image
    }

    override fun writePng(name: String, image: BufferedImage): String? {
        return try {
            outputDir.mkdirs()
            val file = File(outputDir, "${slug(name)}.png")
            ImageIO.write(image, "png", file)
            file.absolutePath
        } catch (e: Exception) {
            System.err.println("[brassui] png write failed: ${e.message}")
            null
        }
    }

    override fun writeGif(name: String, frames: List<BufferedImage>, fps: Int): String? {
        if (frames.isEmpty()) return null
        return try {
            outputDir.mkdirs()
            val file = File(outputDir, "${slug(name)}.gif")
            writeAnimatedGif(file, frames, fps)
            file.absolutePath
        } catch (e: Exception) {
            // GIF is the nice-to-have; a still is the thing that matters. Fall back to a PNG of frame one
            // rather than losing the capture entirely.
            System.err.println("[brassui] gif write failed (${e.message}); writing a still instead")
            writePng(name, frames.first())
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** File-safe stem: the demo id is already kebab-case, this just guards against a stray character. */
    private fun slug(name: String): String =
        name.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]+"), "-").trim('-').ifEmpty { "demo" }

    /** Encode an animated, looping GIF with a per-frame delay from [fps], via ImageIO's GIF writer. */
    private fun writeAnimatedGif(file: File, frames: List<BufferedImage>, fps: Int) {
        val writer = ImageIO.getImageWritersBySuffix("gif").next()
        val params = writer.defaultWriteParam
        val type = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB)
        val meta = writer.getDefaultImageMetadata(type, params)
        val format = meta.nativeMetadataFormatName
        val root = meta.getAsTree(format) as IIOMetadataNode

        val delayHundredths = Math.max(1, Math.round(100.0 / fps)).toString()
        graphicsControl(root).setAttribute("delayTime", delayHundredths)
        applicationLoop(root)
        meta.setFromTree(format, root)

        FileImageOutputStream(file).use { out ->
            writer.output = out
            writer.prepareWriteSequence(null)
            for ((i, frame) in frames.withIndex()) {
                val rgb = if (frame.type == BufferedImage.TYPE_INT_RGB) frame else frame.toRgb()
                writer.writeToSequence(IIOImage(rgb, null, if (i == 0) meta else null), params.also {
                    it.compressionMode = ImageWriteParam.MODE_DEFAULT
                })
            }
            writer.endWriteSequence()
        }
        writer.dispose()
    }

    private fun graphicsControl(root: IIOMetadataNode): IIOMetadataNode =
        child(root, "GraphicControlExtension").apply {
            setAttribute("disposalMethod", "none")
            setAttribute("userInputFlag", "FALSE")
            setAttribute("transparentColorFlag", "FALSE")
            setAttribute("transparentColorIndex", "0")
        }

    /** The NETSCAPE2.0 application extension that makes a GIF loop forever. */
    private fun applicationLoop(root: IIOMetadataNode) {
        val apps = child(root, "ApplicationExtensions")
        val node = IIOMetadataNode("ApplicationExtension")
        node.setAttribute("applicationID", "NETSCAPE")
        node.setAttribute("authenticationCode", "2.0")
        node.userObject = byteArrayOf(0x1, 0x0, 0x0) // loop count 0 = infinite
        apps.appendChild(node)
    }

    private fun child(root: IIOMetadataNode, name: String): IIOMetadataNode {
        for (i in 0 until root.length) {
            val n = root.item(i) as IIOMetadataNode
            if (n.nodeName.equals(name, ignoreCase = true)) return n
        }
        val n = IIOMetadataNode(name)
        root.appendChild(n)
        return n
    }

    private fun BufferedImage.toRgb(): BufferedImage {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.drawImage(this, 0, 0, null)
        g.dispose()
        return out
    }
}
