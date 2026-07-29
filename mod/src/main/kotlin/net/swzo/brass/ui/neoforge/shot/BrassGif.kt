package net.swzo.brass.ui.neoforge.shot

import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.awt.image.IndexColorModel
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream

/**
 * Writing an animated, **transparent** GIF, using the encoder already in the JDK.
 *
 * ### Transparency
 *
 * GIF's transparency is one bit — a pixel is either fully clear or fully opaque, there are no partial
 * alphas. So the input's alpha channel is thresholded: anything below [ALPHA_CUTOFF] becomes the
 * transparent palette index, everything else is drawn opaque. The cost is a hard edge wherever the
 * toolkit anti-aliases (a widget's 1-px borders are crisp already; only curved glyph edges show it),
 * which is the accepted trade for a GIF that drops onto any wiki background instead of carrying a baked
 * theme-coloured rectangle around with it.
 *
 * Nothing the toolkit draws *relies* on partial alpha surviving this. That is not luck — the demo card
 * deliberately renders without its drop shadow for exactly this reason (see
 * [net.swzo.brass.ui.kit.demo.BrassDemoCard]), because a shadow is the one thing a one-bit format
 * cannot carry, and a PNG that had one while the GIF beside it did not was worse than neither having
 * it.
 *
 * ### Palette
 *
 * GIF allows 256 colours per frame. The toolkit's palette is flat fills and a short accent ramp, so the
 * opaque colours across an animation nearly always fit exactly — [buildPalette] collects them and, only
 * if there are more than 255, quantises by dropping low colour bits until they do. Index 0 is reserved
 * for transparency throughout.
 *
 * ### Why this is more than three lines
 *
 * `ImageIO` has always been able to write animated GIFs, but exposes no API for the animation: frame
 * delay and looping live in extension blocks reachable only by hand-building the metadata tree, with
 * attribute names from the `javax_imageio_gif_image_1.0` DTD. That, plus the indexing above, is the
 * rest of this file — all to avoid jar-in-jar'ing a GIF library for something the runtime can do.
 */
object BrassGif {

    /** Alpha at or above which a pixel is drawn; below it the pixel is transparent. */
    private const val ALPHA_CUTOFF = 128

    /**
     * Write [frames] to [path] at [fps], looping forever, with transparency.
     *
     * Returns false if the JDK has no GIF writer, rather than throwing — a failed capture should not
     * take the client down mid-run.
     */
    fun write(path: Path, frames: List<BufferedImage>, fps: Int): Boolean {
        if (frames.isEmpty()) return false

        val writer = ImageIO.getImageWritersByFormatName("gif").let {
            if (it.hasNext()) it.next() else return false
        }

        val palette = buildPalette(frames)
        val indexed = frames.map { toIndexed(it, palette) }

        // Hundredths of a second — the unit the GIF format uses. At least 2 (=50fps): most decoders
        // clamp anything faster, so a "0 delay" GIF would play at the viewer's whim instead of timed.
        val delay = (100.0 / fps).toInt().coerceAtLeast(2)

        FileImageOutputStream(path.toFile()).use { out ->
            writer.output = out
            val param: ImageWriteParam = writer.defaultWriteParam
            val type = ImageTypeSpecifier(indexed.first())
            val metadata = writer.getDefaultImageMetadata(type, param)
            applyAnimation(metadata, delay)

            writer.prepareWriteSequence(null)
            indexed.forEach { writer.writeToSequence(IIOImage(it, null, metadata), param) }
            writer.endWriteSequence()
        }

        writer.dispose()
        return true
    }

    /**
     * The colour model for the whole animation: index 0 transparent, then every opaque colour used.
     *
     * A single palette shared by all frames rather than one per frame — GIF allows per-frame palettes,
     * but a shared one keeps the file small and the encoder simple, and the toolkit does not use enough
     * colours for the shared cap to bite.
     */
    private fun buildPalette(frames: List<BufferedImage>): IndexColorModel {
        val colours = LinkedHashSet<Int>()
        var shift = 0
        // Try at full precision; if too many colours, drop a bit off each channel and recount, until
        // the opaque set fits in the 255 slots that index 0 (transparent) leaves.
        while (true) {
            colours.clear()
            var overflow = false
            for (frame in frames) {
                for (y in 0 until frame.height) {
                    for (x in 0 until frame.width) {
                        val argb = frame.getRGB(x, y)
                        if ((argb ushr 24) and 0xFF < ALPHA_CUTOFF) continue
                        colours.add(quantise(argb, shift))
                        if (colours.size > 255) { overflow = true; break }
                    }
                    if (overflow) break
                }
                if (overflow) break
            }
            if (!overflow) break
            shift++
        }

        // Index 0 is transparent; the opaque colours follow.
        val size = colours.size + 1
        val r = ByteArray(size)
        val g = ByteArray(size)
        val b = ByteArray(size)
        r[0] = 0; g[0] = 0; b[0] = 0
        colours.forEachIndexed { i, c ->
            r[i + 1] = ((c ushr 16) and 0xFF).toByte()
            g[i + 1] = ((c ushr 8) and 0xFF).toByte()
            b[i + 1] = (c and 0xFF).toByte()
        }
        val bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(size - 1))
        return IndexColorModel(bits, size, r, g, b, 0)
    }

    /** Map an ARGB frame onto [palette], sending transparent pixels to index 0. */
    private fun toIndexed(frame: BufferedImage, palette: IndexColorModel): BufferedImage {
        val out = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_BYTE_INDEXED, palette)
        val pixels = (out.raster.dataBuffer as DataBufferByte).data
        val lookup = HashMap<Int, Int>()
        val size = palette.mapSize
        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                val argb = frame.getRGB(x, y)
                val idx = if ((argb ushr 24) and 0xFF < ALPHA_CUTOFF) {
                    0
                } else {
                    lookup.getOrPut(argb and 0xFFFFFF) { nearest(argb, palette, size) }
                }
                pixels[y * frame.width + x] = idx.toByte()
            }
        }
        return out
    }

    /** The palette index closest to [argb]'s colour (never index 0, which is transparent). */
    private fun nearest(argb: Int, palette: IndexColorModel, size: Int): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        var best = 1
        var bestDist = Int.MAX_VALUE
        for (i in 1 until size) {
            val dr = r - palette.getRed(i)
            val dg = g - palette.getGreen(i)
            val db = b - palette.getBlue(i)
            val d = dr * dr + dg * dg + db * db
            if (d < bestDist) { bestDist = d; best = i; if (d == 0) break }
        }
        return best
    }

    /** Drop [shift] low bits off each channel, for the quantise-until-it-fits loop. */
    private fun quantise(argb: Int, shift: Int): Int {
        if (shift == 0) return argb and 0xFFFFFF
        val mask = (0xFF shl shift) and 0xFF
        val r = ((argb ushr 16) and mask)
        val g = ((argb ushr 8) and mask)
        val b = (argb and mask)
        return (r shl 16) or (g shl 8) or b
    }

    /** Set the per-frame delay and the loop-forever marker on a frame's metadata tree. */
    private fun applyAnimation(metadata: IIOMetadata, delayHundredths: Int) {
        val format = metadata.nativeMetadataFormatName
        val root = metadata.getAsTree(format) as IIOMetadataNode

        child(root, "GraphicControlExtension").apply {
            setAttribute("disposalMethod", "restoreToBackgroundColor")
            setAttribute("userInputFlag", "FALSE")
            // The transparency the whole file exists for: honour the transparent index in the palette.
            setAttribute("transparentColorFlag", "TRUE")
            setAttribute("delayTime", delayHundredths.toString())
            setAttribute("transparentColorIndex", "0")
        }

        // Looping is an "application extension" — a vendor block the format never standardised, whose
        // de-facto definition is Netscape's from 1995. The three bytes are: sub-block id 1, then a
        // little-endian repeat count of 0 meaning forever.
        val extensions = child(root, "ApplicationExtensions")
        val loop = IIOMetadataNode("ApplicationExtension").apply {
            setAttribute("applicationID", "NETSCAPE")
            setAttribute("authenticationCode", "2.0")
            userObject = byteArrayOf(0x1, 0x0, 0x0)
        }
        extensions.appendChild(loop)

        metadata.setFromTree(format, root)
    }

    /** The existing child named [name], or a new empty one appended to [parent]. */
    private fun child(parent: IIOMetadataNode, name: String): IIOMetadataNode {
        for (i in 0 until parent.length) {
            val node = parent.item(i)
            if (node.nodeName.equals(name, ignoreCase = true)) return node as IIOMetadataNode
        }
        return IIOMetadataNode(name).also { parent.appendChild(it) }
    }
}
