package net.swzo.brass.ui.kit.html.internal

import com.labymedia.ultralight.UltralightRenderer
import com.labymedia.ultralight.UltralightView
import com.labymedia.ultralight.bitmap.UltralightBitmapSurface
import com.labymedia.ultralight.config.UltralightViewConfig
import com.labymedia.ultralight.input.UltralightKeyEvent
import com.labymedia.ultralight.input.UltralightKeyEventType
import com.labymedia.ultralight.input.UltralightMouseEvent
import com.labymedia.ultralight.input.UltralightMouseEventButton
import com.labymedia.ultralight.input.UltralightMouseEventType
import com.labymedia.ultralight.input.UltralightScrollEvent
import com.labymedia.ultralight.input.UltralightScrollEventType
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.html.BrassHtmlConfig
import net.swzo.brass.ui.kit.html.BrassHtmlView
import net.swzo.brass.ui.kit.html.ConsoleLevel
import net.swzo.brass.ui.kit.html.HtmlSurfaceFrame
import net.swzo.brass.ui.kit.html.HtmlSurfaceRenderer
import net.swzo.brass.ui.kit.platform.BrassCursor
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

/**
 * The concrete [BrassHtmlView] implementation over one Ultralight view. All the Ultralight-specific
 * wiring — input translation, JS bridge, load listeners, bitmap surface access — is contained here,
 * so the widget and the seam never see a `com.labymedia` type.
 */
internal class HtmlView(
    private val engine: UltralightHtmlEngine,
    renderer: UltralightRenderer,
    private val config: BrassHtmlConfig,
) : BrassHtmlView {

    internal var ultralightView: UltralightView? = null
    private var surfaceLocked = false
    private var jsGarbageCollectedAt = 0L

    private val bridge: HtmlJsBridge = HtmlJsBridge(
        view = this,
        ultralightView = { ultralightView },
        bindings = { config.jsBindings },
        onOpenUrl = null,
    )

    override var onTitleChanged: ((String) -> Unit)? = null
    override var onUrlChanged: ((String) -> Unit)? = null
    override var onCursorChanged: ((BrassCursor.Kind?) -> Unit)? = null
    override var onConsoleMessage: ((ConsoleLevel, String, Int, Int) -> Unit)? = null
    override var onFinishLoad: ((String) -> Unit)? = null
    override var onFailLoad: ((String) -> Unit)? = null
    override var onJsEvent: ((String, Map<String, Any?>) -> Unit)? = null

    init {
        val viewConfig = UltralightViewConfig()
            .isTransparent(config.transparent)
            .initialDeviceScale(config.deviceScale)
        config.userAgent?.let { viewConfig.userAgent(it) }
        ultralightView = renderer.createView(
            config.width.coerceAtLeast(16).toLong(),
            config.height.coerceAtLeast(16).toLong(),
            viewConfig,
        )
        ultralightView?.setViewListener(HtmlViewListener(this))
        ultralightView?.setLoadListener(HtmlLoadListener(this))
    }

    // ---- BrassHtmlView -------------------------------------------------------

    override val ready: Boolean get() = surfaced

    override val focused: Boolean get() = ultralightView?.hasFocus() == true

    private var surfaced = false

    override fun loadHtml(html: String, baseUrl: String?) {
        ultralightView?.loadHTML(html, baseUrl ?: "file:///")
    }

    override fun loadUrl(url: String) {
        ultralightView?.loadURL(url)
    }

    override fun reload() {
        ultralightView?.reload()
    }

    override fun evaluateJavascript(script: String) {
        runCatching { ultralightView?.evaluateScript(script) }
    }

    override fun addJsBinding(name: String, value: Any) {
        config.jsBindings[name] = value
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val v = ultralightView ?: return
        if (v.width().toInt() == width && v.height().toInt() == height) return
        v.resize(width.toLong(), height.toLong())
    }

    // ---- input ---------------------------------------------------------------

    override fun fireMouseMove(x: Int, y: Int) {
        val v = ultralightView ?: return
        v.fireMouseEvent(
            UltralightMouseEvent()
                .type(UltralightMouseEventType.MOVED)
                .x(x)
                .y(y),
        )
    }

    override fun fireMouseButton(x: Int, y: Int, button: Int, pressed: Boolean) {
        val v = ultralightView ?: return
        v.fireMouseEvent(
            UltralightMouseEvent()
                .type(if (pressed) UltralightMouseEventType.DOWN else UltralightMouseEventType.UP)
                .x(x)
                .y(y)
                .button(
                    when (button) {
                        1 -> UltralightMouseEventButton.RIGHT
                        2 -> UltralightMouseEventButton.MIDDLE
                        else -> UltralightMouseEventButton.LEFT
                    },
                ),
        )
    }

    override fun fireScroll(x: Int, y: Int) {
        val v = ultralightView ?: return
        v.fireScrollEvent(
            UltralightScrollEvent()
                .type(UltralightScrollEventType.BY_PIXEL)
                .deltaX(x)
                .deltaY(y),
        )
    }

    override fun fireKeyDown(keyCode: Int, scanCode: Int, mods: Int) {
        val v = ultralightView ?: return
        val translated = HtmlKeys.glfwToUltralightKey(keyCode)
        v.fireKeyEvent(
            UltralightKeyEvent()
                .type(UltralightKeyEventType.RAW_DOWN)
                .virtualKeyCode(translated)
                .nativeKeyCode(scanCode)
                .keyIdentifier(UltralightKeyEvent.getKeyIdentifierFromVirtualKeyCode(translated))
                .modifiers(HtmlKeys.ultralightModifiers(mods)),
        )
    }

    override fun fireKeyUp(keyCode: Int, scanCode: Int, mods: Int) {
        val v = ultralightView ?: return
        val translated = HtmlKeys.glfwToUltralightKey(keyCode)
        v.fireKeyEvent(
            UltralightKeyEvent()
                .type(UltralightKeyEventType.UP)
                .virtualKeyCode(translated)
                .nativeKeyCode(scanCode)
                .keyIdentifier(UltralightKeyEvent.getKeyIdentifierFromVirtualKeyCode(translated))
                .modifiers(HtmlKeys.ultralightModifiers(mods)),
        )
    }

    override fun fireChar(codePoint: Int) {
        val v = ultralightView ?: return
        val text = if (codePoint in 0..0x10FFFF) String(Character.toChars(codePoint)) else ""
        v.fireKeyEvent(
            UltralightKeyEvent()
                .type(UltralightKeyEventType.CHAR)
                .text(text)
                .unmodifiedText(text),
        )
    }

    override fun focus() {
        ultralightView?.focus()
    }

    override fun unfocus() {
        ultralightView?.unfocus()
    }

    // ---- drawing -------------------------------------------------------------

    override fun draw(matrixStack: UMatrixStack, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
        engine.surfaceRenderer.draw(this, matrixStack, x, y, w, h, alpha)
    }

    override fun snapshot(): BufferedImage? {
        val v = ultralightView ?: return null
        val surface = v.surface() as? UltralightBitmapSurface ?: return null
        val bitmap = surface.bitmap()
        val w = v.width().toInt()
        val h = v.height().toInt()
        if (w <= 0 || h <= 0) return null

        if (surfaceLocked) unlockSurface()
        val buffer = bitmap.lockPixels()
        val rowBytes = bitmap.rowBytes().toInt()
        return try {
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val pixels = IntArray(w * h)
            for (yy in 0 until h) {
                var off = yy * rowBytes
                var px = yy * w
                for (xx in 0 until w) {
                    val b = buffer.get(off).toInt() and 0xFF
                    val g = buffer.get(off + 1).toInt() and 0xFF
                    val r = buffer.get(off + 2).toInt() and 0xFF
                    val a = buffer.get(off + 3).toInt() and 0xFF
                    off += 4
                    pixels[px++] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            img.setRGB(0, 0, w, h, pixels, 0, w)
            img
        } finally {
            bitmap.unlockPixels()
        }
    }

    override fun free() {
        runCatching {
            ultralightView?.unfocus()
            ultralightView?.stop()
        }
        ultralightView = null
        engine.removeView(this)
    }

    override val surfaceWidth: Int get() = ultralightView?.width()?.toInt() ?: 0
    override val surfaceHeight: Int get() = ultralightView?.height()?.toInt() ?: 0

    /** Lock the surface for a renderer read; returns null when nothing changed this frame. */
    override fun lockSurface(): HtmlSurfaceFrame? {
        val v = ultralightView ?: return null
        val surface = v.surface() as? UltralightBitmapSurface ?: return null
        val dirty = surface.dirtyBounds()
        if (!dirty.isValid) return null
        val bitmap = surface.bitmap()
        val buffer = bitmap.lockPixels()
        surfaceLocked = true
        surfaced = true
        return HtmlSurfaceFrame(
            width = v.width().toInt(),
            height = v.height().toInt(),
            buffer = buffer,
            rowBytes = bitmap.rowBytes().toInt(),
            x = dirty.x(),
            y = dirty.y(),
            dirtyWidth = dirty.width(),
            dirtyHeight = dirty.height(),
        )
    }

    override fun unlockSurface() {
        val v = ultralightView ?: return
        val surface = v.surface() as? UltralightBitmapSurface ?: return
        surface.bitmap().unlockPixels()
        surface.clearDirtyBounds()
        surfaceLocked = false
    }

    /** The engine's per-frame pulse for this view: JS garbage collection. */
    fun update() {
        val now = System.currentTimeMillis()
        if (now - jsGarbageCollectedAt < 1000) return
        jsGarbageCollectedAt = now
        runCatching {
            ultralightView?.lockJavascriptContext()?.use { lock -> lock.context.garbageCollect() }
        }
    }

    internal fun setupContext(context: com.labymedia.ultralight.javascript.JavascriptContext) {
        bridge.setupContext(context)
    }
}
