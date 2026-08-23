package net.swzo.brass.ui.kit.html.internal

import com.labymedia.ultralight.UltralightView
import com.labymedia.ultralight.input.UltralightCursor
import com.labymedia.ultralight.math.IntRect
import com.labymedia.ultralight.plugin.loading.UltralightLoadListener
import com.labymedia.ultralight.plugin.view.MessageLevel
import com.labymedia.ultralight.plugin.view.MessageSource
import com.labymedia.ultralight.plugin.view.UltralightViewListener
import net.swzo.brass.ui.kit.html.BrassHtmlView
import net.swzo.brass.ui.kit.html.ConsoleLevel

/**
 * Ultralight's view-level callbacks, surfaced onto [BrassHtmlView]'s Kotlin lambdas. The cursor maps
 * through [HtmlKeys] into brassui's own [net.swzo.brass.ui.kit.platform.BrassCursor.Kind] vocabulary.
 */
internal class HtmlViewListener(
    private val view: BrassHtmlView,
) : UltralightViewListener {

    override fun onChangeTitle(title: String) {
        view.onTitleChanged?.invoke(title)
    }

    override fun onChangeURL(url: String) {
        view.onUrlChanged?.invoke(url)
    }

    override fun onChangeTooltip(tooltip: String) {}

    override fun onChangeCursor(cursor: UltralightCursor) {
        view.onCursorChanged?.invoke(HtmlKeys.brassCursor(cursor))
    }

    override fun onAddConsoleMessage(
        source: MessageSource,
        level: MessageLevel,
        message: String,
        lineNumber: Long,
        columnNumber: Long,
        sourceId: String,
    ) {
        val mapped = when (level) {
            MessageLevel.LOG, MessageLevel.DEBUG, MessageLevel.INFO -> ConsoleLevel.LOG
            MessageLevel.WARNING -> ConsoleLevel.WARNING
            MessageLevel.ERROR -> ConsoleLevel.ERROR
        }
        view.onConsoleMessage?.invoke(mapped, message, lineNumber.toInt(), columnNumber.toInt())
    }

    override fun onCreateChildView(
        openerUrl: String,
        targetUrl: String,
        isPopup: Boolean,
        popupRect: IntRect,
    ): UltralightView? = null
}

/**
 * Load lifecycle: the window-object-ready moment is where the JS bridge (and every host binding) is
 * installed, so a page always sees `window.brassui` and friends before its scripts run.
 */
internal class HtmlLoadListener(
    private val htmlView: HtmlView,
) : UltralightLoadListener {

    override fun onBeginLoading(frameId: Long, isMainFrame: Boolean, url: String) {}

    override fun onFinishLoading(frameId: Long, isMainFrame: Boolean, url: String) {
        if (isMainFrame) htmlView.onFinishLoad?.invoke(url)
    }

    override fun onFailLoading(
        frameId: Long,
        isMainFrame: Boolean,
        url: String,
        description: String,
        errorDomain: String,
        errorCode: Int,
    ) {
        if (isMainFrame) htmlView.onFailLoad?.invoke(description)
    }

    override fun onUpdateHistory() {}

    override fun onWindowObjectReady(frameId: Long, isMainFrame: Boolean, url: String) {
        htmlView.ultralightView?.lockJavascriptContext()?.use { lock ->
            htmlView.setupContext(lock.context)
        }
    }

    override fun onDOMReady(frameId: Long, isMainFrame: Boolean, url: String) {}
}
