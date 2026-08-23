@file:Suppress("unused")
package net.swzo.brass.ui.kit.html

import gg.essential.universal.UMatrixStack
import java.nio.ByteBuffer

/**
 * A frame of surface data handed to a [HtmlSurfaceRenderer]: the BGRA pixel rows of the view's
 * framebuffer and the dirty rectangle that needs re-uploading. Obtained from
 * [BrassHtmlView.lockSurface]; [BrassHtmlView.unlockSurface] must be called when the frame is done.
 */
class HtmlSurfaceFrame(
    val width: Int,
    val height: Int,
    /** BGRA pixels, one row after another; row [i] starts at `i * rowBytes`. */
    val buffer: ByteBuffer,
    val rowBytes: Int,
    /** The dirty rectangle to re-upload (usually the whole frame after a page edit). */
    val x: Int,
    val y: Int,
    val dirtyWidth: Int,
    val dirtyHeight: Int,
)

/**
 * How a [BrassHtmlView]'s framebuffer is painted into the widget box. The widget only ever calls
 * [BrassHtmlView.draw]; everything GL-specific lives behind this interface, which is what lets a host
 * swap the drawing path — an off-screen render target, a shader pass, a capture hook — without
 * touching the widget. The default implementation uploads the dirty region into a per-view texture
 * and draws it through UniversalCraft's UGraphics, so the same code serves both the in-game host and
 * the standalone desktop app.
 */
interface HtmlSurfaceRenderer {

    /**
     * Upload any dirty region ([BrassHtmlView.lockSurface]) and draw the current frame into the rect
     * ([x], [y])..([x]+[w], [y]+[h]) in screen/GUI pixels, under the caller's [matrixStack], faded to
     * [alpha].
     */
    fun draw(view: BrassHtmlView, matrixStack: UMatrixStack, x: Float, y: Float, w: Float, h: Float, alpha: Float)
}
