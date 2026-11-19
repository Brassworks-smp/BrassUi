package net.swzo.brass.ui.desktop

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import gg.essential.universal.UResolution
import net.swzo.brass.ui.BrassScreen
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.demo.BrassDemoCard
import net.swzo.brass.ui.kit.demo.BrassDemos
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.layout.BrassNavStack
import net.swzo.brass.ui.kit.layout.BrassPages
import net.swzo.brass.ui.kit.layout.BrassScrollArea
import net.swzo.brass.ui.kit.layout.BrassVBox
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.surface.BrassCommandPalette
import net.swzo.brass.ui.kit.surface.BrassContextMenu
import net.swzo.brass.ui.kit.surface.BrassListRow
import net.swzo.brass.ui.kit.surface.BrassModal
import net.swzo.brass.ui.kit.surface.BrassPanel
import net.swzo.brass.ui.kit.surface.BrassToast
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassWrappedLabel
import org.lwjgl.glfw.GLFW
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class WikiCapture(which: String, private val glfwId: Long) : BrassScreen() {

    private class Job(
        val name: String,
        val width: Float,
        val height: Float,
        val card: Boolean,
        val padBottom: Float = 0f,
        val padSide: Float = 0f,
        val settle: Int = 40,
        val build: (WikiCapture, UIComponent) -> UIComponent,
    )

    private val outDir = File(System.getProperty("brassui.shots.dir") ?: (System.getProperty("user.dir") + "/screenshots/wiki"))
    private val capture = DesktopDemoCapture()
    private val queue: MutableList<Job>
    private val stage = UIContainer()
    private val writers = ArrayList<Thread>()

    private var job: Job? = null
    private var target: UIComponent? = null
    private var frame = 0
    private var script: Script? = null
    private var t = 0f
    private var sinceFrame = 0f
    private var frames: MutableList<BufferedImage>? = null

    private var cx = -1f
    private var cy = -1f
    private var pressed = false
    private var clickFlash = 0f

    init {
        backdropColor = net.swzo.brass.ui.Colors.UI_BACKGROUND
        stage childOf window
        val all = BrassDemos.ALL.filter { !it.worldRequired && it.name != "icons" }.map { d ->
            Job(d.name, d.outerWidth, d.outerHeight, false, padBottom = PAD_BOTTOM[d.name] ?: 0f, padSide = PAD_SIDE[d.name] ?: 0f,
                settle = if (d.name == "player-head" || d.name == "skeleton") 160 else 40) { _, _ ->
                val content = d.build()
                ContentHolder(if (d.card) BrassDemoCard(content, d.fitCard) else content, content)
            }
        } + scenes()
        val wanted = which.split(',').map { it.trim().lowercase() }
        queue = if ("all" in wanted) all.toMutableList() else all.filter { it.name in wanted }.toMutableList()
        outDir.mkdirs()
        next()
    }

    private class ContentHolder(val shown: UIComponent, val content: UIComponent) : UIContainer()

    private fun next() {
        stage.clearChildren()
        val j = queue.removeFirstOrNull()
        if (j == null) {
            writers.forEach { it.join() }
            println("[wiki] done -> ${outDir.absolutePath}")
            GLFW.glfwSetWindowShouldClose(glfwId, true)
            job = null
            return
        }
        job = j
        stage.constrain {
            x = CenterConstraint(); y = CenterConstraint()
            width = j.width.pixels(); height = j.height.pixels()
        }
        val built = j.build(this, stage)
        val shown = (built as? ContentHolder)?.shown ?: built
        target = (built as? ContentHolder)?.content ?: built
        shown.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf stage
        frame = 0
        script = SCRIPTS[j.name]?.let { Script().apply(it) }
        t = 0f
        frames = null
        moveTo(1f, 1f)
        pressed = false
    }

    override fun onDrawScreen(matrixStack: UMatrixStack, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val j = job
        val s = script
        if (j != null && frames != null && s != null) {
            t += BrassClock.dt
            s.run(this, t)
        }
        super.onDrawScreen(matrixStack, mouseX, mouseY, partialTicks)
        if (j == null) return
        frame++
        val rec = frames
        if (rec == null) {
            if (frame == j.settle) {
                grab(false)?.let { ImageIO.write(it, "png", File(outDir, "${j.name}.png")) }
                if (s == null) next() else { frames = ArrayList(); t = 0f; sinceFrame = 1f }
            }
            return
        }
        sinceFrame += BrassClock.dt
        if (sinceFrame >= 1f / FPS) {
            sinceFrame = if (sinceFrame >= 2f / FPS) 0f else sinceFrame - 1f / FPS
            grab(true)?.let { rec.add(it) }
        }
        clickFlash = (clickFlash - BrassClock.dt * 4f).coerceAtLeast(0f)
        if (t >= s!!.length) {
            val name = j.name
            val shots = rec.toList()
            frames = null
            Thread {
                runCatching { capture.writeGif(name, shots, FPS) }
                    .onFailure { System.err.println("[wiki] $name gif failed: ${it.message}") }
                println("[wiki] $name.gif (${shots.size} frames)")
            }.also { writers += it }.start()
            next()
        }
    }

    private fun region(): FloatArray {
        val j = job!!
        val m = 6f
        return floatArrayOf(
            stage.getLeft() - m - j.padSide, stage.getTop() - m,
            stage.getWidth() + 2 * m + 2 * j.padSide, stage.getHeight() + 2 * m + j.padBottom,
        )
    }

    private fun grab(withPointer: Boolean): BufferedImage? {
        val (x, y, w, h) = region()
        val img = capture.grab(x, y, w, h) ?: return null
        if (withPointer && cx >= 0f) drawPointer(img, (cx - x) * img.width / w, (cy - y) * img.height / h, img.width / w)
        return img
    }

    private fun drawPointer(img: BufferedImage, px: Float, py: Float, s: Float) =
        Pointer.draw(img, px, py, s, pressed, clickFlash)


    private fun point(fx: Float, fy: Float): Pair<Float, Float> {
        val c = target ?: stage
        return c.getLeft() + c.getWidth() * fx to c.getTop() + c.getHeight() * fy
    }

    fun moveTo(x: Float, y: Float) {
        cx = x; cy = y
        val k = UResolution.windowWidth.toDouble() / UResolution.scaledWidth.toDouble()
        val cb = GLFW.glfwSetCursorPosCallback(glfwId, null)
        GLFW.glfwSetCursorPosCallback(glfwId, cb)
        cb?.invoke(glfwId, x * k, y * k)
        if (pressed) onMouseDragged(x.toDouble(), y.toDouble(), 0, 0L)
    }

    fun down(button: Int = 0) {
        pressed = button == 0
        clickFlash = 1f
        onMouseClicked(cx.toDouble(), cy.toDouble(), button)
    }

    fun up(button: Int = 0) {
        pressed = false
        onMouseReleased(cx.toDouble(), cy.toDouble(), button)
    }

    fun type(c: Char) = onKeyPressed(0, c, null)

    fun key(code: Int) = onKeyPressed(code, '\u0000', null)


    class Script {
        private class Step(val at: Float, val dur: Float, val act: (WikiCapture, Float) -> Unit) { var done = false }

        private val steps = ArrayList<Step>()
        private var at = 0.3f
        val length: Float get() = at + 0.6f

        private var lastX = 1.1f
        private var lastY = 0.5f

        fun wait(s: Float) { at += s }

        fun move(fx: Float, fy: Float, dur: Float = 0.45f) {
            val fromX = lastX; val fromY = lastY
            steps += Step(at, dur) { w, p ->
                val e = p * p * (3 - 2 * p)
                val (x, y) = w.point(fromX + (fx - fromX) * e, fromY + (fy - fromY) * e)
                w.moveTo(x, y)
            }
            lastX = fx; lastY = fy
            at += dur
        }

        fun press(button: Int = 0) { steps += Step(at, 0f) { w, _ -> w.down(button) } }

        fun release(button: Int = 0) { steps += Step(at, 0f) { w, _ -> w.up(button) } }

        fun click(fx: Float, fy: Float, after: Float = 0.6f) {
            move(fx, fy)
            wait(0.1f)
            press(); wait(0.09f); release()
            wait(after)
        }

        fun drag(fx: Float, fy: Float, dur: Float = 0.8f) = move(fx, fy, dur)

        fun type(text: String, per: Float = 0.08f) {
            text.forEach { c -> steps += Step(at, 0f) { w, _ -> w.type(c) }; at += per }
        }

        fun key(code: Int) { steps += Step(at, 0f) { w, _ -> w.key(code) } }

        fun run(w: WikiCapture, t: Float) {
            for (s in steps) {
                if (s.done || t < s.at) continue
                val p = if (s.dur <= 0f) 1f else ((t - s.at) / s.dur).coerceIn(0f, 1f)
                s.act(w, p)
                if (p >= 1f) s.done = true
            }
        }
    }

    private fun scenes(): List<Job> {
        fun button(label: String, accent: BrassAccent = BrassAccent.DEFAULT, onClick: () -> Unit) =
            BrassButton(label, accent) { onClick() }.constrain { x = CenterConstraint(); y = CenterConstraint(); width = 110.pixels(); height = 20.pixels() }

        return listOf(
            Job("modal", 300f, 170f, false) { _, root ->
                val host = UIContainer()
                button("Delete world", BrassAccent.DANGER) {
                    BrassModal("Delete \"Survival\"?", 220f, 96f)
                        .body { c -> BrassWrappedLabel("The world and its backups are removed.").constrain { width = 100.percent() } childOf c }
                        .footer(BrassButton("Cancel") {}, BrassButton("Delete", BrassAccent.DANGER) {})
                        .show(root)
                } childOf host
                host
            },
            Job("context-menu", 260f, 190f, false) { _, root ->
                val host = UIContainer()
                button("Actions") {
                    BrassContextMenu(
                        listOf(
                            BrassContextMenu.Item("Rename") {}, BrassContextMenu.Item("Duplicate") {},
                            BrassContextMenu.Item("Move to folder") {}, BrassContextMenu.Item.separator(),
                            BrassContextMenu.Item("Delete") {},
                        ),
                    ).show(window, root.getLeft() + 75f, root.getTop() + 106f)
                } childOf host
                host
            },
            Job("toast", 300f, 150f, false) { _, root ->
                val host = UIContainer()
                BrassButton("Save") { BrassToast.show(root, "Settings saved", BrassToast.Type.SUCCESS) }
                    .constrain { x = 10.pixels(); y = 10.pixels(); width = 80.pixels(); height = 20.pixels() } childOf host
                BrassButton("Fail") { BrassToast.show(root, "Server unreachable", BrassToast.Type.ERROR) }
                    .constrain { x = 10.pixels(); y = 36.pixels(); width = 80.pixels(); height = 20.pixels() } childOf host
                host
            },
            Job("tooltip", 240f, 110f, false) { _, _ ->
                val host = UIContainer()
                val b = button("Hover me") {}
                b childOf host
                BrassTooltip.attach(b, "Backups", "Kept for seven days, newest first.")
                host
            },
            Job("command-palette", 320f, 200f, false) { _, root ->
                val host = UIContainer()
                button("Open palette") {
                    BrassCommandPalette(
                        listOf("Open world", "Open settings", "Theme: Ocean", "Theme: Forest", "Reload resources", "Toggle inspector")
                            .map { BrassCommandPalette.Command(it, if (it.startsWith("Theme")) "Appearance" else "General") {} },
                    ).show(root)
                } childOf host
                host
            },
            Job("nav-stack", 240f, 170f, false) { _, _ -> navStackScene() },
            Job("pages-slide", 260f, 150f, false) { _, _ -> pagesScene(BrassPages.Slide.HORIZONTAL) },
        )
    }

    private fun navStackScene(): UIComponent {
        val card = BrassPanel("WORLDS", layout = BrassPanel.Layout.FREE)
        val nav = BrassNavStack().constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf card.content
        lateinit var list: UIComponent
        fun detail(name: String): UIComponent {
            val c = UIContainer()
            BrassButton("Back") { nav.pop() }.constrain { x = 0.pixels(); y = 1.pixels(); width = 50.pixels(); height = 18.pixels() } childOf c
            BrassLabel(name, net.swzo.brass.ui.Colors.UI_TEXT_HOVER).constrain { x = 58.pixels(); y = 6.pixels() } childOf c
            val rows = BrassVBox(gap = 1f, bleed = false).constrain { x = 0.pixels(); y = 26.pixels(); width = 100.percent() } childOf c
            listOf(BrassListRow("Game rules", "12 changed", BrassIcons.GEAR) {}, BrassListRow("Backups", "3 kept", BrassIcons.ARCHIVE) {}, BrassListRow("Players", "8 online", BrassIcons.USERS) {})
                .forEach { r -> rows.add(r.constrain { width = 100.percent() }) }
            return c
        }
        list = BrassScrollArea.list().also { a ->
            listOf("Survival" to "Day 214", "Creative" to "Flat", "Skyblock" to "Island 3").forEach { (n, s) ->
                a.add(BrassListRow(n, s, BrassIcons.GLOBE) { nav.push(detail(n)) })
            }
        }
        nav.push(list, animate = false)
        return card
    }

    private fun pagesScene(slide: BrassPages.Slide): UIComponent {
        val host = UIContainer()
        val pages = BrassPages(slide = slide).constrain { x = 0.pixels(); y = 26.pixels(); width = 100.percent(); height = 100.percent() - 26.pixels() }
        listOf("Overview", "Members", "Logs").forEach { title ->
            pages.page(title) { p ->
                BrassPanel(title.uppercase()).add(BrassWrappedLabel("$title page").constrain { width = 100.percent() })
                    .constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() - 4.pixels() } childOf p
            }
        }
        pages.tabs(equalWidths = true).constrain { x = 0.pixels(); y = 1.pixels(); width = 100.percent(); height = 18.pixels() } childOf host
        pages childOf host
        return host
    }

    private companion object {
        const val FPS = 20

        val PAD_BOTTOM = mapOf("dropdown" to 10f, "chips" to 30f, "list-row" to 20f, "tab-switch" to 30f)
        val PAD_SIDE = mapOf<String, Float>()

        val SCRIPTS: Map<String, Script.() -> Unit> = mapOf(
            "button" to { click(0.5f, 0.5f, 0.8f) },
            "icon-button" to { click(0.5f, 0.5f, 0.8f) },
            "square-button" to { click(0.5f, 0.5f, 0.8f) },
            "toggle" to { click(0.5f, 0.5f, 0.8f); click(0.5f, 0.5f, 0.8f) },
            "checkbox" to { click(0.5f, 0.5f, 0.7f); click(0.5f, 0.5f, 0.7f) },
            "radio-group" to { click(0.08f, 0.1f, 0.7f); click(0.08f, 0.3f, 0.7f) },
            "slider" to { move(0.24f, 0.5f); press(); drag(0.85f, 0.5f); wait(0.2f); drag(0.4f, 0.5f); release(); wait(0.6f) },
            "range-slider" to { move(0.15f, 0.5f); press(); drag(0.35f, 0.5f); release(); wait(0.2f); move(0.85f, 0.5f); press(); drag(0.62f, 0.5f); release(); wait(0.6f) },
            "number-input" to { click(0.95f, 0.5f, 0.3f); click(0.95f, 0.5f, 0.3f); click(0.95f, 0.5f, 0.3f); click(0.05f, 0.5f, 0.6f) },
            "tab-switch" to { click(0.5f, 0.5f, 0.7f); click(0.83f, 0.5f, 0.7f); click(0.17f, 0.5f, 0.7f) },
            "nav-rail" to { click(0.5f, 0.3f, 0.6f); click(0.5f, 0.5f, 0.6f); click(0.5f, 0.68f, 0.6f); click(0.5f, 0.08f, 0.7f) },
            "dropdown" to { click(0.5f, 0.1f, 0.6f); move(0.5f, 0.38f); wait(0.4f); move(0.5f, 0.5f); wait(0.4f); click(0.5f, 0.5f, 0.8f) },
            "confirm-slider" to { move(0.5f, 0.5f); press(); wait(1.7f); release(); wait(1.2f) },
            "scroll-selector" to { click(0.9f, 0.5f, 0.5f); click(0.9f, 0.5f, 0.5f); click(0.1f, 0.5f, 0.7f) },
            "color-picker" to { click(0.3f, 0.25f, 0.3f); move(0.3f, 0.25f); press(); drag(0.75f, 0.35f); release(); wait(0.7f) },
            "keybind" to { click(0.5f, 0.5f, 0.6f); key(GLFW.GLFW_KEY_G); wait(0.9f) },
            "search-field" to { click(0.5f, 0.5f, 0.3f); type("diamond"); wait(0.8f) },
            "text-input" to { click(0.5f, 0.5f, 0.3f); type("Ironforge"); wait(0.8f) },
            "chips" to { move(0.2f, 0.5f); wait(0.5f); move(0.5f, 0.5f); wait(0.5f); move(0.8f, 0.5f); wait(0.6f) },
            "accordion" to { click(0.5f, 0.07f, 0.8f); click(0.5f, 0.2f, 0.9f) },
            "split-pane" to { move(0.38f, 0.5f); press(); drag(0.6f, 0.5f); wait(0.2f); drag(0.3f, 0.5f); release(); wait(0.6f) },
            "pagination" to { click(0.95f, 0.5f, 0.4f); click(0.95f, 0.5f, 0.4f); click(0.05f, 0.5f, 0.7f) },
            "list-row" to { move(0.5f, 0.2f); wait(0.6f); move(0.5f, 0.5f); wait(0.6f); move(0.5f, 0.8f); wait(0.6f) },
            "table" to { click(0.2f, 0.05f, 0.6f); click(0.5f, 0.3f, 0.5f); click(0.5f, 0.45f, 0.8f) },
            "bar-chart" to { move(0.2f, 0.6f); wait(0.4f); move(0.5f, 0.6f); wait(0.4f); move(0.8f, 0.6f); wait(0.6f) },
            "chart" to { move(0.3f, 0.5f); wait(0.5f); move(0.7f, 0.5f); wait(1f) },
            "loading" to { wait(2.4f) },
            "progress-bar" to { wait(2.4f) },
            "skeleton" to { wait(2f) },
            "quest-tree" to { wait(0.5f); move(0.5f, 0.5f); wait(1.2f) },
            "sidebar" to { click(0.5f, 0.35f, 0.7f); click(0.5f, 0.5f, 0.7f); click(0.5f, 0.15f, 0.8f) },
            "master-detail" to { click(0.2f, 0.2f, 0.8f); click(0.2f, 0.35f, 0.8f) },
            "modal" to { click(0.5f, 0.5f, 1.4f); click(0.33f, 0.62f, 0.9f) },
            "context-menu" to { click(0.5f, 0.5f, 0.5f); move(0.45f, 0.62f); wait(0.4f); move(0.45f, 0.75f); wait(0.6f); key(GLFW.GLFW_KEY_ESCAPE); key(GLFW.GLFW_KEY_ESCAPE); wait(0.5f) },
            "toast" to { click(0.17f, 0.13f, 0.7f); click(0.17f, 0.3f, 2f) },
            "tooltip" to { move(0.5f, 0.5f); wait(1.6f); move(0.95f, 0.95f); wait(0.4f) },
            "command-palette" to { click(0.5f, 0.5f, 0.5f); type("theme", 0.12f); wait(0.5f); key(GLFW.GLFW_KEY_DOWN); wait(0.8f) },
            "nav-stack" to { click(0.5f, 0.2f, 1.2f); click(0.14f, 0.19f, 1.0f); click(0.5f, 0.36f, 1.2f) },
            "pages-slide" to { click(0.5f, 0.06f, 0.9f); click(0.83f, 0.06f, 0.9f); click(0.17f, 0.06f, 0.9f) },
        )
    }
}
