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
import net.swzo.brass.ui.kit.dev.BrassLayoutAudit
import org.lwjgl.glfw.GLFW
import java.io.File
import javax.imageio.ImageIO

class ReferenceHarness(private val which: String, private val glfwId: Long) : ReferenceDriver {

    private val outDir = File(System.getProperty("ui.referenceDir") ?: (System.getProperty("user.dir") + "/screenshots/reference"))
    private val capture = DesktopDemoCapture()
    private val queue = (if (which.equals("all", true)) BrassReferences.ALL else listOfNotNull(BrassReferences.find(which)))
        .toMutableList()
    private val report = StringBuilder()

    var errors = 0
        private set

    private var screen: BrassReferenceScreen? = null
    private var steps: List<ReferenceStep> = emptyList()
    private var stepIndex = 0
    private var wait = 0

    fun start() {
        require(queue.isNotEmpty()) { "unknown reference '$which' - one of ${BrassReferences.ALL.joinToString { it.id }} or all" }
        outDir.mkdirs()
        outDir.listFiles { f -> f.name.endsWith(".png") }?.forEach { it.delete() }
        BrassScreen.frameHook = { s, _ -> if (s === screen) tick() }
        next()
    }

    private fun next() {
        val ref = queue.removeFirstOrNull()
        if (ref == null) {
            finish()
            return
        }
        val s = ref.create(BrassAppScreen.Fit.FLOATING)
        screen = s
        steps = listOf(ReferenceStep("initial", frames = 45) {}) + s.scenario()
        stepIndex = 0
        UScreen.displayScreen(s)
        begin()
    }

    private fun begin() {
        val step = steps[stepIndex]
        unhover()
        step.act(this)
        wait = step.frames
    }

    private fun tick() {
        if (--wait > 0) return
        val s = screen ?: return
        val step = steps[stepIndex]
        val name = "${s.id}-$stepIndex-${step.name}"

        val w = UResolution.scaledWidth.toFloat()
        val h = UResolution.scaledHeight.toFloat()
        capture.grab(0f, 0f, w, h)?.let { ImageIO.write(it, "png", File(outDir, "$name.png")) }

        val findings = BrassLayoutAudit.run(s.window)
        val stepErrors = findings.count { it.severity == net.swzo.brass.ui.kit.dev.BrassLayoutRules.Severity.ERROR }
        errors += stepErrors
        val line = "$name: " + BrassLayoutAudit.report(findings)
        report.append(line).append('\n')
        println("[reference] $line")

        stepIndex++
        if (stepIndex < steps.size) begin() else next()
    }

    private fun finish() {
        BrassScreen.frameHook = null
        report.append(if (errors == 0) "RESULT: PASS\n" else "RESULT: FAIL ($errors errors)\n")
        File(outDir, "audit.txt").writeText(report.toString())
        println("[reference] ${if (errors == 0) "PASS" else "FAIL ($errors errors)"} - ${outDir.absolutePath}")
        GLFW.glfwSetWindowShouldClose(glfwId, true)
    }

    override fun hover(component: UIComponent) {
        val cx = (component.getLeft() + component.getRight()) / 2f
        val cy = (component.getTop() + component.getBottom()) / 2f
        val toWindow = UResolution.windowWidth.toDouble() / UResolution.scaledWidth.toDouble()
        moveCursor(cx * toWindow, cy * toWindow)
    }

    override fun unhover() = moveCursor(1.0, 1.0)

    private fun moveCursor(x: Double, y: Double) {
        GLFW.glfwSetCursorPos(glfwId, x, y)
        val callback = GLFW.glfwSetCursorPosCallback(glfwId, null)
        GLFW.glfwSetCursorPosCallback(glfwId, callback)
        callback?.invoke(glfwId, x, y)
    }
}
