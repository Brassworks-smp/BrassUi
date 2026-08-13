@file:Suppress("unused")
package net.swzo.brass.ui.kit.media

import gg.essential.elementa.components.UIImage
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import kotlin.math.min
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * An image loaded from a URL, a local file or a classpath resource, with a **skeleton placeholder**
 * while it is in flight.
 * ```kotlin
 * BrassImage("https://example.com/icon.png")
 *     .constrain { width = 24.pixels(); height = 24.pixels() }
 * ```
 * Three states, and the widget moves through them on its own:
 * - **Loading** - a shimmering skeleton block, the usual "content is coming" affordance.
 * - **Ready** - the decoded image, scaled to fit and centred, never stretched.
 * - **Failed** - a quiet placeholder mark. A dead URL must look deliberately empty, not broken.
 * ### Sources
 * - `https://...` - a remote image; constructing one **makes an HTTPS request**. Plain `http` is
 *   rejected outright, responses are capped at MAX_BYTES, and the request has a short timeout - a
 *   hung CDN must not be able to pin a loader thread for the session.
 * - `file:///…` or a plain filesystem path (`/absolute/path.png`, `relative/icon.png`) - a local
 *   image file, read off the worker thread with the same size cap.
 * - `:…` (a bare path that is not a file also falls back to it) - a Java classpath resource, so
 *   images can ship inside a jar (`:/assets/mod/icon.png`).
 * The loader resolves a bare path against the filesystem first and falls back to the classpath, so
 * either spelling works for resources bundled with the app.
 * ### Formats
 * Decoding goes through [ImageIO], so **PNG, JPEG, GIF and BMP work and WebP does not** - the JDK
 * ships no WebP reader, and a `.webp` URL will land in the failed state. Anything that needs WebP has
 * to add a decoder plugin to the classpath first.
 * ### Caching
 * Results are cached per URL for the process lifetime, so the same icon in twenty rows is fetched once
 * and reopening a screen costs nothing. The cache holds decoded images, not widgets, so it survives
 * the components that asked for them.
 */
class BrassImage(
    url: String,
    private val shimmer: Boolean = true,
    private val card: Boolean = false,
) : BrassWidget(BrassAccent.DEFAULT) {

    var url: String = url
        set(value) {
            if (field == value) return
            field = value
            pending = BrassImageLoader.load(value)
            texture = null
        }

    private var pending: CompletableFuture<BufferedImage?> = BrassImageLoader.load(url)

    /**
     * The uploaded texture, built on the first frame after the download lands.
     * Deliberately created during [drawContent] rather than in the loader's callback: [UIImage] uploads
     * to the GPU, which must happen on the render thread, and the loader completes on a worker.
     */
    private var texture: UIImage? = null

    init {
        chrome = BrassChrome.NONE
        // Without a card there is no ring or lip below the picture, so the keycap bleed that reserves
        // room for one below every other widget makes no sense here - mark it flat so nothing (the dev
        // inspector, layout that accounts for the bleed) leaves a phantom strip of padding under a plain
        // image. A carded image keeps the bleed, because its card genuinely casts a shadow down there.
        chrome = if (card) BrassChrome.FLAT else BrassChrome.NONE
    }

    val ready: Boolean get() = pending.isDone && pending.getNow(null) != null

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return

        // The image (skeleton, decoded picture or failed mark) is drawn into this inner box. With a card
        // it is the card's interior, inset by the padding; without one it is the whole widget.
        var ix = x; var iy = y; var iw = w; var ih = h
        if (card) {
            BrassCard.draw(m, x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), shadow = true)
            ix = x + CARD_PAD; iy = y + CARD_PAD; iw = w - CARD_PAD * 2; ih = h - CARD_PAD * 2
            if (iw <= 0 || ih <= 0) return
        }

        if (!pending.isDone) {
            drawSkeleton(m, ix.toFloat(), iy.toFloat(), iw.toFloat(), ih.toFloat())
            return
        }

        val decoded = pending.getNow(null)
        if (decoded == null) {
            drawFailed(m, ix.toFloat(), iy.toFloat(), iw.toFloat(), ih.toFloat())
            return
        }

        val tex = texture ?: UIImage(CompletableFuture.completedFuture(decoded)).also {
            // Icons are small and land on the pixel grid; NEAREST keeps them crisp rather than smeared
            // at the scales a Minecraft GUI runs at.
            it.textureMinFilter = UIImage.TextureScalingMode.NEAREST
            it.textureMagFilter = UIImage.TextureScalingMode.NEAREST
            texture = it
        }

        // Fit inside the box, preserving aspect - a stretched mod icon looks worse than a smaller one.
        val scale = min(iw.toFloat() / decoded.width, ih.toFloat() / decoded.height)
        val dw = decoded.width * scale
        val dh = decoded.height * scale
        val dx = ix + (iw - dw) / 2f
        val dy = iy + (ih - dh) / 2f

        BrassStats.quad()
        tex.drawImage(
            m, dx.toDouble(), dy.toDouble(), dw.toDouble(), dh.toDouble(),
            BrassAmbientFade.apply(Color.WHITE),
        )
    }

    private fun drawSkeleton(m: UMatrixStack, x: Float, y: Float, w: Float, h: Float) =
        BrassSkeleton.draw(m, x, y, w, h, shimmer)

    private fun drawFailed(m: UMatrixStack, x: Float, y: Float, w: Float, h: Float) {
        fill(m, x, y, x + w, y + h, SKELETON_BG)
        val cx = x + w / 2f
        val cy = y + h / 2f
        val r = min(w, h) * 0.18f
        fill(m, cx - r, cy - 1f, cx + r, cy + 1f, FAILED_MARK)
        fill(m, cx - 1f, cy - r, cx + 1f, cy + r, FAILED_MARK)
    }

    private fun fill(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) =
        BrassPaint.rect(m, x1, y1, x2, y2, c)

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo(
            "image",
            "Image",
            SOURCE_W + CARD_PAD * 2f,
            SOURCE_H + CARD_PAD * 2f,
            card = false,
        ) {
            BrassImage(SOURCE_URL, shimmer = true, card = true)
        }

        private const val SOURCE_URL = "https://brassworks.opnsoc.org/images/seasons/season2.png"
        private const val SOURCE_W = 257f
        private const val SOURCE_H = 69f

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val CARD_PAD = 4

        private val SKELETON_BG: Color get() = Colors.UI_ELEMENT_BG
        private val FAILED_MARK: Color get() = Colors.IMAGE_FAILED
    }
}
