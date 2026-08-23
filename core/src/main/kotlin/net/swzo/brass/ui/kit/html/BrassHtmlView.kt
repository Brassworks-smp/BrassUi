@file:Suppress("unused")
package net.swzo.brass.ui.kit.html

import net.swzo.brass.ui.kit.platform.BrassCursor
import java.awt.image.BufferedImage

/**
 * A single HTML surface, as seen by [BrassHtml]. All methods must be called from the render thread.
 *
 * The type is deliberately thin and game-free: positions are plain pixels, mouse buttons are
 * GLFW-style 0/1/2 (left/middle/right, which is what Elementa and the hosts hand over), key codes are
 * GLFW codes, and [draw] takes a UC [gg.essential.universal.UMatrixStack] exactly like every other
 * brassui widget. Hosts implement the surface plumbing (texture upload, quad draw) underneath, but the
 * widget never sees any of it.
 */
interface BrassHtmlView {

    /** True once the underlying renderer has produced its first frame. */
    val ready: Boolean

    /** Whether this view currently owns Ultralight's keyboard focus. */
    val focused: Boolean

    // ---- content -------------------------------------------------------------

    fun loadHtml(html: String, baseUrl: String? = null)

    fun loadUrl(url: String)

    fun reload()

    fun evaluateJavascript(script: String)

    /** Inject (or replace) a JS-visible binding on `window.<name>` before the next load. */
    fun addJsBinding(name: String, value: Any)

    /** Resize the rendering surface to [width] x [height] device pixels. */
    fun resize(width: Int, height: Int)

    // ---- input ---------------------------------------------------------------

    /** Cursor moved to ([x], [y]) inside the view. */
    fun fireMouseMove(x: Int, y: Int)

    /** Mouse button press/release at ([x], [y]); [button] is GLFW-style 0/1/2. */
    fun fireMouseButton(x: Int, y: Int, button: Int, pressed: Boolean)

    /** Scroll by ([x], [y]) pixels. */
    fun fireScroll(x: Int, y: Int)

    /**
     * Key down at [keyCode] (GLFW code) with [mods] (GLFW `GLFW_MOD_*` bitmask). The view maps both
     * into Ultralight's own vocabulary. Tab/Enter produce a separate char event via [fireChar].
     */
    fun fireKeyDown(keyCode: Int, scanCode: Int, mods: Int)

    fun fireKeyUp(keyCode: Int, scanCode: Int, mods: Int)

    /** A typed character (already char-converted by the caller). */
    fun fireChar(codePoint: Int)

    fun focus()

    fun unfocus()

    // ---- drawing -------------------------------------------------------------

    /**
     * Draw the current frame into the widget box at absolute screen coords ([x], [y]) sized
     * [w] x [h] (GUI pixels), faded to [alpha]. The matrix stack is Elementa's current model stack,
     * already positioned at the widget.
     */
    fun draw(matrixStack: gg.essential.universal.UMatrixStack, x: Float, y: Float, w: Float, h: Float, alpha: Float)

    /**
     * Begin a read of the surface's dirty region. Returns null when nothing changed since the last
     * read; otherwise the caller must consume [HtmlSurfaceFrame] and call [unlockSurface] after.
     * Custom [HtmlSurfaceRenderer]s read through this.
     */
    fun lockSurface(): HtmlSurfaceFrame?

    fun unlockSurface()

    /** The surface's dimensions in device pixels. */
    val surfaceWidth: Int
    val surfaceHeight: Int

    /** A full-resolution snapshot of the current frame, for capture/preview. Null before the first paint. */
    fun snapshot(): BufferedImage?

    /** Release the underlying view and all native resources. Idempotent. */
    fun free()

    // ---- callbacks -----------------------------------------------------------

    var onTitleChanged: ((String) -> Unit)?
    var onUrlChanged: ((String) -> Unit)?
    var onCursorChanged: ((BrassCursor.Kind?) -> Unit)?
    var onConsoleMessage: ((ConsoleLevel, String, Int, Int) -> Unit)?
    var onFinishLoad: ((String) -> Unit)?
    var onFailLoad: ((String) -> Unit)?
    var onJsEvent: ((String, Map<String, Any?>) -> Unit)?
}

/** Severity of a message the page's JavaScript console emitted. */
enum class ConsoleLevel { LOG, WARNING, ERROR }
