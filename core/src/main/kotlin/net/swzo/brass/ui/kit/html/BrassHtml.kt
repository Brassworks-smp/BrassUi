@file:Suppress("unused")
package net.swzo.brass.ui.kit.html

import gg.essential.elementa.components.Window
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.base.disposeWith
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.html.internal.UltralightHtmlEngine
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.platform.BrassCursor
import net.swzo.brass.ui.kit.platform.BrassPlatform
import net.swzo.brass.ui.kit.text.BrassFont
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/**
 * An embedded HTML widget — a real webview inside a brassui screen, rendered by the Ultralight engine
 * (see [BrassHtmlEngine], the seam this widget is the face of).
 * ```kotlin
 * BrassHtml(html = "<h1>hello</h1><script>brassui.send('hi', {})</script>")
 *     .constrain { width = 300.pixels(); height = 200.pixels() }
 * ```
 * ### What it is
 * The widget hosts a live page: scripts run, `<input>` fields type, links click, CSS animates, and
 * the page can call back into Kotlin through the `window.brassui` bridge — `brassui.send("event",
 * {payload})` surfaces as [onJsEvent]. It is a genuine control, so it slots into the widget grammar:
 * it extends [BrassWidget] (entrance, opacity, theming), requests [BrassCursor] kinds through the
 * platform seam, takes keyboard focus like any other field, and frees its native view when removed.
 * ### Extensibility
 * - **Page bridge** — anything placed in [jsBindings] is bound onto `window.<name>` before scripts
 *   run, so a host hands the page its own API surface.
 * - **Events** — [onJsEvent], [onConsoleMessage], [onTitleChanged], [onFinishLoad], [onFailLoad].
 * - **Engine** — the whole renderer is swappable via [BrassHtmlEngine.bind] (Ultralight is the
 *   default); [UltralightHtmlEngine.surfaceRenderer] swaps just the drawing path.
 * - **Source** — [html], [url] or [evaluateJavascript] drive the page at any time.
 * ### Unavailable, not broken
 * The engine loads lazily on first use (natives are downloaded at runtime) and degrades to a
 * "engine unavailable" card on failure — an offline box, a non-x64 JVM or an unsupported OS still
 * get a working UI, just without the webview. While the natives download (once) the card shows a
 * loading state.
 */
class BrassHtml(
    html: String? = null,
    url: String? = null,
    private var deviceScale: Double = 1.0,
    private val transparentBackground: Boolean = true,
    private val jsBindings: MutableMap<String, Any> = mutableMapOf(),
) : BrassWidget(BrassAccent.DEFAULT) {

    /** The page to load. Set this (or [url]) any time to navigate; `null` keeps whatever is loaded. */
    var html: String? = html
        set(value) {
            if (field == value) return
            field = value
            url = null
            if (value != null) view?.loadHtml(value, baseUrl)
        }

    /** The page URL. Setting it navigates (and clears [html]). */
    var url: String? = url
        set(value) {
            if (field == value) return
            field = value
            html = null
            if (value != null) view?.loadUrl(value)
        }

    /** Base URL for relative links inside [html]; ignored when a [url] drives the page. */
    var baseUrl: String = "file:///"

    /** Run JavaScript in the page. Safe before the page has loaded — Ultralight queues it per view. */
    fun evaluateJavascript(script: String) {
        view?.evaluateJavascript(script)
    }

    // ---- page callbacks -------------------------------------------------------

    var onTitleChanged: ((String) -> Unit)? = null
    var onUrlChanged: ((String) -> Unit)? = null
    var onCursorChanged: ((BrassCursor.Kind?) -> Unit)? = null
    var onConsoleMessage: ((ConsoleLevel, String, Int, Int) -> Unit)? = null
    var onFinishLoad: ((String) -> Unit)? = null
    var onFailLoad: ((String) -> Unit)? = null

    /** A `brassui.send(name, payload)` from the page. */
    var onJsEvent: ((String, Map<String, Any?>) -> Unit)? = null

    /** A `brassui.openUrl(url)` from the page: hand it to an external browser instead of navigating. */
    var onOpenUrl: ((String) -> Unit)? = null

    private var view: BrassHtmlView? = null

    private var lastSurfaceSize: Pair<Int, Int>? = null
    private var relX = 0
    private var relY = 0
    private var heldButton = -1

    init {
        val self = this
        chrome = BrassChrome.NONE
        // Not clickable on purpose: clickable makes the keycap hand cursor win over the page's own
        // cursor. This is a surface; the page decides what the pointer looks like.
        clickable = false

        onMouseClick { e ->
            if (!active) return@onMouseClick
            focused = self
            grabWindowFocus()
            heldButton = e.mouseButton
            view?.fireMouseButton((e.relativeX * scale()).roundToInt(), (e.relativeY * scale()).roundToInt(), e.mouseButton, pressed = true)
        }
        onMouseRelease {
            val v = view ?: return@onMouseRelease
            val button = if (heldButton >= 0) heldButton else 0
            heldButton = -1
            v.fireMouseButton(relX, relY, button, pressed = false)
        }
        onMouseDrag { mx, my, button ->
            val v = view ?: return@onMouseDrag
            relX = (mx * scale()).roundToInt()
            relY = (my * scale()).roundToInt()
            v.fireMouseMove(relX, relY)
        }
        onMouseScroll { e ->
            val v = view ?: return@onMouseScroll
            v.fireScroll((e.scrollX * 32f).roundToInt(), (e.scrollY * 32f).roundToInt())
        }
        onMouseLeave {
            // One move outside the view clears CSS :hover; Ultralight does not leave automatically,
            // and a stuck hover on a "Delete" button is a real misclick.
            view?.fireMouseMove(-1, -1)
        }
        onFocusLost {
            if (focused === self) {
                focused = null
                view?.unfocus()
            }
        }

        // Free the native view when the widget leaves the tree (the lifecycle sweep notices the
        // detachment a couple of frames later — plenty for a webview teardown).
        disposeWith {
            if (focused === self) focused = null
            view?.free()
            view = null
        }
    }

    /** Device pixels per widget pixel: the widget's own scale, times the host's GUI scale. */
    private fun scale(): Float = (deviceScale * (BrassPlatform.current?.guiScale() ?: 1f)).toFloat()

    /**
     * Raw key press from the screen (see BrassScreen.onKeyPressed). The page owns the keyboard while
     * this widget is focused — exactly like a focused text field.
     */
    internal fun onScreenKey(keyCode: Int, typedChar: Char, mods: UKeyboard.Modifiers?): Boolean {
        if (!active) return false
        val v = view ?: return false
        val glfwMods = mods.glfw()
        if (keyCode != 0) {
            v.fireKeyDown(keyCode, 0, glfwMods)
            when (keyCode) {
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> v.fireChar('\r'.code)
                GLFW.GLFW_KEY_TAB -> v.fireChar('\t'.code)
            }
        }
        if (typedChar != Char.MIN_VALUE && !typedChar.isISOControl()) v.fireChar(typedChar.code)
        return true
    }

    internal fun onScreenKeyRelease(keyCode: Int, mods: UKeyboard.Modifiers?): Boolean {
        if (!active || keyCode == 0) return false
        val v = view ?: return false
        v.fireKeyUp(keyCode, 0, mods.glfw())
        return true
    }

    override fun mouseMove(window: Window) {
        super.mouseMove(window)
        val v = view ?: return
        if (!isHovered()) return
        val (mx, my) = getMousePosition()
        relX = (mx * scale()).roundToInt()
        relY = (my * scale()).roundToInt()
        v.fireMouseMove(relX, relY)
    }

    // ---- drawing --------------------------------------------------------------

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val alpha = entranceFade

        val engine = BrassHtmlEngine.current
        if (engine == null) {
            drawUnavailable(m, x, y, w, h, alpha, "No HTML engine bound")
            return
        }

        if (!engine.available) {
            (engine as? UltralightHtmlEngine)?.startInit()
            val status = when {
                engine is UltralightHtmlEngine && engine.failureReason != null -> engine.failureReason
                else -> null
            } ?: "Loading HTML engine…"
            drawUnavailable(m, x, y, w, h, alpha, status)
            return
        }

        var v = view
        if (v == null) {
            v = engine.createView(configFor(w, h)) ?: run {
                drawUnavailable(m, x, y, w, h, alpha, "Could not create the HTML view")
                return
            }
            wire(v)
            view = v
            val pageUrl = url
            if (pageUrl != null) v.loadUrl(pageUrl)
            else html?.let { v.loadHtml(it, baseUrl) }
        }

        engine.update()

        val surfaceW = (w * scale()).roundToInt()
        val surfaceH = (h * scale()).roundToInt()
        if (lastSurfaceSize != surfaceW to surfaceH) {
            lastSurfaceSize = surfaceW to surfaceH
            v.resize(surfaceW, surfaceH)
        }

        v.draw(m, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), alpha)
    }

    private fun configFor(w: Int, h: Int): BrassHtmlConfig = BrassHtmlConfig(
        width = (w * scale()).roundToInt(),
        height = (h * scale()).roundToInt(),
        transparent = transparentBackground,
        deviceScale = 1.0,
        jsBindings = jsBindings,
    )

    private fun wire(v: BrassHtmlView) {
        v.onTitleChanged = onTitleChanged
        v.onUrlChanged = onUrlChanged
        v.onCursorChanged = { kind ->
            onCursorChanged?.invoke(kind)
            kind?.let(BrassCursor::request)
        }
        v.onConsoleMessage = onConsoleMessage
        v.onFinishLoad = onFinishLoad
        v.onFailLoad = onFailLoad
        v.onJsEvent = onJsEvent
    }

    private fun drawUnavailable(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int, alpha: Float, message: String) {
        BrassPaint.rect(m, x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), Colors.UI_ELEMENT_BG)
        val fg = Colors.UI_TEXT_DARK
        val lines = message.split("\n")
        val lineH = BrassFont.LINE
        var ty = y + h / 2f - (lines.size - 1) * lineH / 2f
        for (line in lines) {
            val tw = BrassFont.width(this, line)
            BrassFont.draw(m, this, line, x + (w - tw) / 2f, ty, fg, shadow = false)
            ty += lineH
        }
    }

    private fun UKeyboard.Modifiers?.glfw(): Int {
        var out = 0
        if (this?.isCtrl == true) out = out or GLFW.GLFW_MOD_CONTROL
        if (this?.isShift == true) out = out or GLFW.GLFW_MOD_SHIFT
        if (this?.isAlt == true) out = out or GLFW.GLFW_MOD_ALT
        return out
    }

    companion object : BrassDemoSource {

        /** The widget currently owning the keyboard; the screen routes raw keys to it. */
        var focused: BrassHtml? = null
            internal set

        override fun demo() = BrassDemo(
            "html",
            "HTML",
            DEMO_W,
            DEMO_H,
            card = false,
        ) {
            BrassHtml(html = DEMO_HTML)
        }

        private const val DEMO_W = 320f
        private const val DEMO_H = 180f

        private val DEMO_HTML = """
            <html>
            <head>
              <style>
                body { margin: 0; font-family: sans-serif; background: #1b1a16; color: #e8e2d0;
                       display: flex; flex-direction: column; height: 100vh; box-sizing: border-box; }
                header { padding: 8px 12px; background: #b5862f; color: #16130c; font-weight: bold; }
                main { padding: 12px; flex: 1; overflow: auto; }
                input, button { font-size: 14px; margin: 4px 0; }
                .row { display: flex; gap: 8px; align-items: center; }
                footer { padding: 6px 12px; font-size: 12px; color: #9a927c; border-top: 1px solid #3a362c; }
              </style>
            </head>
            <body>
              <header>brassui · live HTML</header>
              <main>
                <div class="row"><input id="field" placeholder="type here…" style="flex:1"></div>
                <div class="row">
                  <button onclick="brassui.send('button', { id: 'hello' })">send event</button>
                  <button onclick="brassui.send('count', { value: (window.__n = (window.__n || 0) + 1) })">count</button>
                </div>
                <p id="out" style="font-size:13px; color:#c9b98a"></p>
                <script>
                  document.getElementById('field').addEventListener('input', (e) =>
                    document.getElementById('out').textContent = 'typed: ' + e.target.value);
                  document.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter') brassui.send('enter', { value: e.target.value });
                  });
                </script>
              </main>
              <footer>scripts run · callbacks fire · brassui.send bridges to Kotlin</footer>
            </body>
            </html>
        """.trimIndent()
    }
}
