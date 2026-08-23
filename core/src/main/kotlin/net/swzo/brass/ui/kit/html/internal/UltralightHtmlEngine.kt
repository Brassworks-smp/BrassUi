package net.swzo.brass.ui.kit.html.internal

import com.labymedia.ultralight.UltralightJava
import com.labymedia.ultralight.UltralightPlatform
import com.labymedia.ultralight.UltralightRenderer
import com.labymedia.ultralight.config.FontHinting
import com.labymedia.ultralight.config.UltralightConfig
import com.labymedia.ultralight.gpu.UltralightGPUDriverNativeUtil
import com.labymedia.ultralight.plugin.logging.UltralightLogLevel
import net.swzo.brass.ui.kit.html.BrassHtmlConfig
import net.swzo.brass.ui.kit.html.BrassHtmlEngine
import net.swzo.brass.ui.kit.html.BrassHtmlView
import net.swzo.brass.ui.kit.html.HtmlSurfaceRenderer
import org.lwjgl.glfw.GLFW

/**
 * The brassui binding of the Ultralight engine, ported from UltralightFabric (which itself came from
 * LiquidBounce's webview). This is the object hosts bind onto the [BrassHtmlEngine] seam:
 * ```kotlin
 * BrassHtmlEngine.bind(UltralightHtmlEngine)
 * ```
 * Everything Ultralight-specific lives behind this object; the widget and the seam see only
 * game-free terms.
 *
 * ### Initialisation is lazy, asynchronous and crash-free
 * The first frame of the first [BrassHtml] widget on screen triggers [startInit]: the natives are
 * downloaded on a background thread (they are a ~60 MB per-OS zip), and the native libraries load and
 * the platform/renderer construct **on the render thread** once the download lands. A download or
 * load failure flips the engine to [State.FAILED] and the widget shows its "engine unavailable"
 * placeholder — it never throws into the game.
 *
 * The default [surfaceRenderer] draws through UniversalCraft's [gg.essential.universal.UGraphics],
 * so the same implementation serves both the in-game host and the standalone desktop app. A host that
 * wants a different drawing path (an off-screen capture, a shader of its own) swaps it here.
 */
object UltralightHtmlEngine : BrassHtmlEngine {

    enum class State { IDLE, DOWNLOADING, READY, FAILED }

    private val logger = java.util.logging.Logger.getLogger("brassui.html")

    @Volatile
    var state: State = State.IDLE
        private set

    /** Why the engine is unavailable, when it is; shown by the widget's placeholder. */
    @Volatile
    var failureReason: String? = null
        private set

    /** The surface drawing path. Swap to customise rendering without touching the widget. */
    @Volatile
    var surfaceRenderer: HtmlSurfaceRenderer = UltralightSurfaceRenderer()

    private var ultralightRenderer: UltralightRenderer? = null
    private val views = mutableListOf<HtmlView>()
    private var downloadDone = false
    private var initTried = false
    private var refreshRate = 60

    override val available: Boolean get() = state == State.READY

    /**
     * Where the natives live on disk. Set this before the first view is created (the mod points it at
     * the game's config dir; the desktop app defaults to `~/.brassui`).
     */
    fun configure(resourcesDir: java.io.File) {
        if (state == State.IDLE) HtmlResources.rootDir = resourcesDir
    }

    /**
     * Kick off initialisation. Called by the widget on the first frame it exists; safe to call
     * repeatedly. The download runs on a worker; [update] finishes construction on the render thread.
     */
    fun startInit() {
        if (state != State.IDLE || initTried) return
        initTried = true
        state = State.DOWNLOADING
        failureReason = null
        Thread({ downloadNatives() }, "brassui-html-bootstrap").start()
    }

    private fun downloadNatives() {
        try {
            HtmlResources.ensure()
            downloadDone = true
        } catch (t: Throwable) {
            failureReason = bootstrapFailure(t)
            state = State.FAILED
            logger.warning(failureReason!!)
        }
    }

    private fun bootstrapFailure(t: Throwable): String {
        val hint = if (HtmlResources.archLabel() == "arm64") {
            "\nThis JVM is arm64, but Ultralight only publishes x64 natives. Run under Rosetta " +
                "(an x86_64 JVM), or provide arm64 natives via -Dbrassui.html.resourcesDir=…"
        } else {
            ""
        }
        return "could not fetch the Ultralight natives: ${t.message ?: t::class.simpleName}$hint"
    }

    override fun update() {
        when (state) {
            State.DOWNLOADING -> if (downloadDone) runCatching(::finishInit).onFailure {
                failureReason = "could not load the Ultralight natives (${HtmlResources.archLabel()}): " +
                    "${it.message ?: it::class.simpleName}" +
                    (if (HtmlResources.archLabel() == "arm64") "\nThis JVM is arm64, but Ultralight only " +
                        "publishes x64 natives. Run under Rosetta (an x86_64 JVM), or provide arm64 " +
                        "natives via -Dbrassui.html.resourcesDir=…" else "")
                state = State.FAILED
                logger.warning(failureReason!!)
            }
            State.READY -> {
                ultralightRenderer?.update()
                views.toList().forEach(HtmlView::update)
            }
            else -> Unit
        }
    }

    /**
     * Finish engine construction on the render thread (the download has landed). Loading natives and
     * creating the platform/renderer is quick; it must stay off the worker because Ultralight's
     * objects are owned by whatever thread created them.
     */
    private fun finishInit() {
        if (state != State.DOWNLOADING) return
        refreshRate = detectRefreshRate()
        logger.info("Loading Ultralight natives from ${HtmlResources.binDir}")
        UltralightJava.load(HtmlResources.binDir.toPath())
        UltralightGPUDriverNativeUtil.load(HtmlResources.binDir.toPath())

        val platform = UltralightPlatform.instance()
        platform.setConfig(
            UltralightConfig()
                .animationTimerDelay(1.0 / refreshRate)
                .scrollTimerDelay(1.0 / refreshRate)
                .resourcePath(HtmlResources.cacheDir.absolutePath)
                .cachePath(HtmlResources.cacheDir.absolutePath)
                .fontHinting(FontHinting.SMOOTH),
        )
        platform.usePlatformFontLoader()
        platform.setFileSystem(HtmlFileSystem())
        platform.setClipboard(HtmlClipboard())
        platform.setLogger { level, message ->
            when (level) {
                UltralightLogLevel.ERROR -> logger.info("[ultralight/err] $message")
                UltralightLogLevel.WARNING -> logger.info("[ultralight/warn] $message")
                UltralightLogLevel.INFO -> logger.info("[ultralight/info] $message")
            }
        }

        ultralightRenderer = UltralightRenderer.create()
        state = State.READY
        logger.info("Ultralight ready ($refreshRate Hz)")
    }

    override fun createView(config: BrassHtmlConfig): BrassHtmlView? {
        if (state != State.READY) return null
        val renderer = ultralightRenderer ?: return null
        return runCatching { HtmlView(this, renderer, config) }
            .onSuccess { views += it }
            .getOrNull()
    }

    internal fun removeView(view: HtmlView) {
        views.remove(view)
    }

    override fun shutdown() {
        views.toList().forEach(HtmlView::free)
        views.clear()
        ultralightRenderer = null
        state = State.IDLE
        initTried = false
        downloadDone = false
    }

    private fun detectRefreshRate(): Int = runCatching {
        val monitor = GLFW.glfwGetPrimaryMonitor()
        if (monitor != 0L) {
            val mode = GLFW.glfwGetVideoMode(monitor)
            if (mode != null) mode.refreshRate().coerceAtLeast(30) else 60
        } else 60
    }.getOrDefault(60)
}
