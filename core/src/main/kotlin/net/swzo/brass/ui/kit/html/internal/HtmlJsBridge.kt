package net.swzo.brass.ui.kit.html.internal

import com.labymedia.ultralight.UltralightView
import com.labymedia.ultralight.databind.Databind
import com.labymedia.ultralight.databind.DatabindConfiguration
import com.labymedia.ultralight.databind.context.ContextProvider
import com.labymedia.ultralight.databind.context.ContextProviderFactory
import com.labymedia.ultralight.javascript.JavascriptContext
import com.labymedia.ultralight.javascript.JavascriptContextLock
import com.labymedia.ultralight.javascript.JavascriptValue
import java.util.function.Consumer

/**
 * The page-facing side of the bridge. Every view gets its own instance, bound on `window.brassui`, so
 * a page can do:
 * ```js
 * brassui.send('click', { button: 'ok' })
 * brassui.openUrl('https://brassworks.smp')
 * brassui.log('hello from the page')
 * ```
 * The `send` payload is marshalled by Ultralight's databind (Gson-shaped), so plain values, arrays
 * and maps round-trip; [net.swzo.brass.ui.kit.html.BrassHtmlView.onJsEvent] delivers it.
 */
internal class HtmlJsUi(private val bridge: HtmlJsBridge) {

    /** Fire a named event from the page; [net.swzo.brass.ui.kit.html.BrassHtmlView.onJsEvent] receives it. */
    fun send(event: String, payload: Any?) {
        bridge.view.onJsEvent?.invoke(event, payloadToMap(payload))
    }

    /** Navigate the page itself, or hand the URL to the widget's [net.swzo.brass.ui.kit.html.BrassHtml.onOpenUrl]. */
    fun openUrl(url: String) {
        val external = bridge.onOpenUrl
        if (external != null) external(url) else bridge.view.loadUrl(url)
    }

    /** A page-side console line, surfaced to [net.swzo.brass.ui.kit.html.BrassHtmlView.onConsoleMessage]. */
    fun log(message: String) {
        bridge.view.onConsoleMessage?.invoke(net.swzo.brass.ui.kit.html.ConsoleLevel.LOG, message, 0, 0)
    }

    @Suppress("UNCHECKED_CAST")
    private fun payloadToMap(payload: Any?): Map<String, Any?> = when (payload) {
        is Map<*, *> -> payload.entries.associate { it.key.toString() to it.value }
        is List<*> -> payload.withIndex().associate { it.index.toString() to it.value }
        null -> emptyMap()
        else -> mapOf("value" to payload)
    }
}

/**
 * One databind instance per view. [setupContext] runs on every page's window-object-ready (see
 * [HtmlLoadListener]) and installs the bridge plus any host bindings from
 * [net.swzo.brass.ui.kit.html.BrassHtmlConfig.jsBindings].
 */
internal class HtmlJsBridge(
    /** The [net.swzo.brass.ui.kit.html.BrassHtmlView] implementation this bridge belongs to. */
    val view: HtmlView,
    /** Resolve the live Ultralight view (may be null between free() and recreation). */
    private val ultralightView: () -> UltralightView?,
    /** Host-injected `window.<name>` bindings, re-read on every page load. */
    private val bindings: () -> Map<String, Any>,
    /** Called when the page asks to navigate away (browser-button handling); null navigates the view. */
    val onOpenUrl: ((String) -> Unit)?,
) {
    private val ui = HtmlJsUi(this)

    private val databind = Databind(
        DatabindConfiguration.builder()
            .contextProviderFactory(HtmlContextProviderFactory { ultralightView() })
            .build(),
    )

    fun setupContext(context: JavascriptContext) {
        val global = context.globalContext.globalObject
        global.setProperty("brassui", databind.conversionUtils.toJavascript(context, ui), 0)
        for ((name, value) in bindings()) {
            global.setProperty(name, databind.conversionUtils.toJavascript(context, value), 0)
        }
    }
}

private class HtmlContextProviderFactory(
    private val view: () -> UltralightView?,
) : ContextProviderFactory {
    override fun bindProvider(value: JavascriptValue): ContextProvider = HtmlContextProvider { view() }
}

private class HtmlContextProvider(
    private val view: () -> UltralightView?,
) : ContextProvider {
    override fun syncWithJavascript(callback: Consumer<JavascriptContextLock>) {
        val v = view() ?: return
        v.lockJavascriptContext().use { lock -> callback.accept(lock) }
    }
}
