package net.swzo.brass.ui.desktop

import gg.essential.elementa.UIComponent
import gg.essential.universal.UResolution
import gg.essential.universal.UScreen
import net.swzo.brass.ui.BrassAppScreen
import net.swzo.brass.ui.BrassScreen
import net.swzo.brass.ui.demo.reference.BrassReferenceScreen
import net.swzo.brass.ui.demo.reference.BrassReferences
import net.swzo.brass.ui.demo.reference.ReferenceDriver
import net.swzo.brass.ui.demo.reference.ReferenceStep
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.dev.BrassLayoutAudit
import org.lwjgl.glfw.GLFW
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class ReferenceHarness(private val which: String, private val glfwId: Long) : ReferenceDriver {

    private val outDir = File(System.getProperty("ui.referenceDir") ?: (System.getProperty("user.dir") + "/screenshots/reference"))
    private val capture = DesktopDemoCapture()
    private val queue = when {
        which.equals("all", true) -> BrassReferences.ALL
        which.equals("examples", true) -> BrassReferences.EXAMPLES
        else -> listOfNotNull(BrassReferences.find(which))
    }.toMutableList()
    private val report = StringBuilder()
    private val record = System.getProperty("ui.record") == "true"

    var errors = 0
        private set

    private var screen: BrassReferenceScreen? = null
    private var steps: List<ReferenceStep> = emptyList()
    private var stepIndex = 0
    private var wait = 0

    private var region: FloatArray? = null
    private var frames: MutableList<BufferedImage>? = null
    private var sinceFrame = 0f
    private val writers = ArrayList<Thread>()

    private var px = -1f
    private var py = -1f
    private var flash = 0f
    private var glide: Glide? = null

    private class Glide(val fromX: Float, val fromY: Float, val toX: Float, val toY: Float, val click: Boolean) {
        var t = 0f
    }

    fun start() {
        require(queue.isNotEmpty()) {
            "unknown reference '$which' - one of ${(BrassReferences.ALL + BrassReferences.EXAMPLES).joinToString { it.id }}, all or examples"
        }
        outDir.mkdirs()
        outDir.listFiles { f -> f.name.endsWith(".png") }?.forEach { it.delete() }
        BrassScreen.frameHook = { s, _ -> if (s === screen) tick() }
        next()
    }

    private fun next() {
        finishRecording()
        val ref = queue.removeFirstOrNull()
        if (ref == null) {
            finish()
            return
        }
        val s = ref.create(BrassAppScreen.Fit.FLOATING)
        screen = s
        steps = listOf(ReferenceStep("initial", frames = 45) {}) + s.scenario()
        stepIndex = 0
        region = null
        frames = if (record) ArrayList() else null
        px = -1f; py = -1f
        UScreen.displayScreen(s)
        begin()
    }

    private fun begin() {
        val step = steps[stepIndex]
        if (!record) unhover()
        step.act(this)
        wait = if (record) maxOf(step.frames, 36) + 24 else step.frames
    }

    private fun tick() {
        val s = screen ?: return
        if (region == null) {
            val f = s.frame
            val m = 8f
            region = if (System.getProperty("ui.recordFull") == "true") {
                floatArrayOf(0f, 0f, UResolution.scaledWidth.toFloat(), UResolution.scaledHeight.toFloat())
            } else {
                floatArrayOf(f.getLeft() - m, f.getTop() - m, f.getWidth() + 2 * m, f.getHeight() + 2 * m)
            }
        }
        recordFrame()
        glide?.let { g ->
            g.t = (g.t + BrassClock.dt / GLIDE_S).coerceAtMost(1f)
            val e = g.t * g.t * (3 - 2 * g.t)
            moveTo(g.fromX + (g.toX - g.fromX) * e, g.fromY + (g.toY - g.fromY) * e)
            if (g.t >= 1f) {
                glide = null
                if (g.click) press(g.toX, g.toY)
            }
            return
        }
        if (--wait > 0) return
        val step = steps[stepIndex]
        val name = "${s.id}-$stepIndex-${step.name}"

        val shot = if (record) region!!.let { (x, y, w, h) -> capture.grab(x, y, w, h) }
        else capture.grab(0f, 0f, UResolution.scaledWidth.toFloat(), UResolution.scaledHeight.toFloat())
        shot?.let { ImageIO.write(it, "png", File(outDir, "$name.png")) }

        val findings = BrassLayoutAudit.run(s.window)
        val stepErrors = findings.count { it.severity == net.swzo.brass.ui.kit.dev.BrassLayoutRules.Severity.ERROR }
        errors += stepErrors
        val line = "$name: " + BrassLayoutAudit.report(findings)
        report.append(line).append('\n')
        println("[reference] $line")

        stepIndex++
        if (stepIndex < steps.size) begin() else next()
    }

    private fun recordFrame() {
        val list = frames ?: return
        val (x, y, w, h) = region ?: return
        flash = (flash - BrassClock.dt * 4f).coerceAtLeast(0f)
        sinceFrame += BrassClock.dt
        if (sinceFrame < 1f / FPS) return
        sinceFrame = if (sinceFrame >= 2f / FPS) 0f else sinceFrame - 1f / FPS
        val img = capture.grab(x, y, w, h) ?: return
        if (px >= 0f) Pointer.draw(img, (px - x) * img.width / w, (py - y) * img.height / h, img.width / w, false, flash)
        list.add(img)
    }

    private fun finishRecording() {
        val list = frames ?: return
        frames = null
        val id = screen?.id ?: return
        if (list.isEmpty()) return
        writers += Thread {
            capture.writeGif(id, list, FPS)
            println("[reference] $id.gif (${list.size} frames)")
        }.also { it.start() }
    }

    private fun finish() {
        writers.forEach { it.join() }
        BrassScreen.frameHook = null
        report.append(if (errors == 0) "RESULT: PASS\n" else "RESULT: FAIL ($errors errors)\n")
        File(outDir, "audit.txt").writeText(report.toString())
        println("[reference] ${if (errors == 0) "PASS" else "FAIL ($errors errors)"} - ${outDir.absolutePath}")
        GLFW.glfwSetWindowShouldClose(glfwId, true)
    }

    override fun hover(component: UIComponent) {
        val cx = (component.getLeft() + component.getRight()) / 2f
        val cy = (component.getTop() + component.getBottom()) / 2f
        if (record) glideTo(cx, cy, click = false) else moveTo(cx, cy)
    }

    override fun unhover() = moveTo(1f, 1f)

    override fun clickAt(x: Float, y: Float) {
        if (record) glideTo(x, y, click = true) else { moveTo(x, y); press(x, y) }
    }

    private fun glideTo(x: Float, y: Float, click: Boolean) {
        val s = screen
        val fromX = if (px >= 0f) px else s?.frame?.let { it.getRight() - 20f } ?: x
        val fromY = if (py >= 0f) py else s?.frame?.let { it.getBottom() - 20f } ?: y
        glide = Glide(fromX, fromY, x, y, click)
    }

    private fun press(x: Float, y: Float) {
        val s = screen ?: return
        flash = 1f
        s.window.mouseClick(x.toDouble(), y.toDouble(), 0)
        s.window.mouseRelease()
    }

    private fun moveTo(x: Float, y: Float) {
        px = x; py = y
        val toWindow = UResolution.windowWidth.toDouble() / UResolution.scaledWidth.toDouble()
        moveCursor(x * toWindow, y * toWindow)
    }

    private fun moveCursor(x: Double, y: Double) {
        GLFW.glfwSetCursorPos(glfwId, x, y)
        val callback = GLFW.glfwSetCursorPosCallback(glfwId, null)
        GLFW.glfwSetCursorPosCallback(glfwId, callback)
        callback?.invoke(glfwId, x, y)
    }

    private companion object {
        const val FPS = 15
        const val GLIDE_S = 0.45f
    }
}
