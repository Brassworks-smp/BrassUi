package net.swzo.brass.ui.kit.demo

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UDesktop
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.BrassScreen
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.layout.BrassLayout
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassLabel
import org.lwjgl.glfw.GLFW
import java.awt.image.BufferedImage

/**
 * The demo browser: pick a widget, drive it yourself, capture what you see.
 *
 * ### Why capturing is manual
 *
 * There used to be a batch mode: one command captured every demo in every theme and quit. It read as
 * the obviously right design and was wrong twice over. Nobody ever wanted *all* of it — a widget's demo
 * changes and regenerating four hundred files to pick up the two that moved makes the diff unreadable.
 * And a sweep gives you no chance to *look*: a demo whose sample data is cropped produces a perfectly
 * valid file that nobody sees until it is on a page.
 *
 * The demos were also **scripted** — each carried a timeline saying to open a section at 0.3s and
 * release a drag at 1.7s — and that went for a related reason. Every animation worth recording had to
 * be predicted in advance and written as numbers, so tuning a clip meant editing Kotlin, rebuilding,
 * relaunching, watching, and adjusting a decimal. Here the widget is **live and takes the real mouse**:
 * you open the accordion at the pace you want and hold the record button around it.
 *
 * The theme dimension went the same way. Whatever palette the game is in is the one you capture.
 *
 * ### How the same pixels end up in the file
 *
 * The demo is a **real child of this screen**, laid out inside the preview card — which is what makes
 * hover, clicks and drags land on it normally. Capturing then reads that same on-screen rectangle back
 * out of the framebuffer. There is no second copy and no separate state: the frame written to disk is
 * the component you are looking at, one draw later — every clip already resolved against the real screen.
 *
 * ### What works without a host
 *
 * Everything except writing files. With no [BrassDemoCapture] bound this is still a working browser —
 * which is what the desktop app gets — and the shutter and record button disable themselves with a
 * reason rather than vanishing.
 */
class BrassDemoBrowser(
    private val onClose: () -> Unit = {},
) : BrassScreen() {

    private val demos = BrassDemos.ALL

    // Not `selected`: BrassWidget already has a `selected` flag, and this class's inner widgets would
    // resolve the wrong one — silently, since both are in scope.
    private var current: BrassDemo = demos.first()

    /** The live demo instance, or null before the first mount. */
    private var mounted: UIComponent? = null

    /** Frames accumulated by an in-progress recording, or null when not recording. */
    private var recording: MutableList<BufferedImage>? = null

    /** Real seconds accumulated toward the next recorded frame, so capture is paced to [FPS]. */
    private var sinceFrame: Float = 0f

    /**
     * Encodes and writes finished recordings, off the render thread.
     *
     * One thread, not a pool: writes are rare and sequential by nature, and two of them racing to
     * [BrassDemoCapture]'s unique-name check would be how two takes land on the same file. Daemon, so a
     * half-written capture never keeps the game from quitting.
     */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "brassui-demo-write").apply { isDaemon = true }
    }

    /** A result from [writer], picked up by the next frame. See [finishRecording]. */
    private val pendingStatus = java.util.concurrent.atomic.AtomicReference<String?>()

    private val status = BrassLabel("").also { it.entranceEnabled = false }
    private val caption = BrassLabel("").also { it.entranceEnabled = false }

    /** Where the demo is parented. Its box is the demo's outer size, so capture can measure from it. */
    private val stage = Stage()

    private val navRows = ArrayList<BrassButton>()

    private lateinit var shutter: BrassButton
    private lateinit var record: BrassButton

    init {
        buildChrome()
        select(0)
    }

    // ---- layout ----------------------------------------------------------------------------------

    private fun buildChrome() {
        BrassLabel("Demo browser", Colors.UI_TEXT_HOVER).also { it.entranceEnabled = false }
            .constrain { x = 12.pixels(); y = 10.pixels() } childOf background

        val listCard = Card().constrain {
            x = 12.pixels(); y = 28.pixels()
            width = LIST_W.pixels()
            height = 100.percent() - 76.pixels()
        } childOf background

        val listScroll = ScrollComponent().constrain {
            x = 6.pixels(); y = 6.pixels()
            width = 100.percent() - 12.pixels()
            height = 100.percent() - 12.pixels()
        } childOf listCard

        val listInner = UIContainer().constrain {
            x = 0.pixels(); y = 0.pixels()
            width = 100.percent()
            height = BrassLayout.contentHeight()
        } childOf listScroll

        demos.forEachIndexed { i, demo ->
            val row = BrassButton(demo.title, BrassAccent.DEFAULT) { select(i) }.apply {
                centered = false
                chrome = BrassChrome.FLAT
                selectable = true
                entranceEnabled = false
            }
            row.constrain {
                x = 0.pixels()
                y = if (i == 0) 0.pixels() else SiblingConstraint(2f)
                width = 100.percent(); height = 18.pixels()
            } childOf listInner
            navRows.add(row)
        }

        val stageCard = Card().constrain {
            x = basicXConstraint { listCard.getRight() + 12f }
            y = 28.pixels()
            width = 100.percent() - (LIST_W + 36f).pixels()
            height = 100.percent() - 76.pixels()
        } childOf background

        // Sized to the demo and centred, rather than filling the card: the demo has to be laid out at
        // exactly its declared size for the capture to match, and centring is what stops a small
        // control being stranded in the corner of a large area.
        stage.constrain {
            x = CenterConstraint(); y = CenterConstraint()
            width = 1.pixels(); height = 1.pixels()
        } childOf stageCard

        val bar = UIContainer().constrain {
            x = 12.pixels()
            y = 100.percent() - 42.pixels()
            width = 100.percent() - 24.pixels()
            height = 20.pixels()
        } childOf background

        var slot = 0
        fun control(label: String, accent: BrassAccent = BrassAccent.DEFAULT, action: () -> Unit): BrassButton {
            val b = BrassButton(label, accent) { action() }.apply { entranceEnabled = false }
            b.constrain {
                x = (slot * (BTN_W + 6f)).pixels(); y = 0.pixels()
                width = BTN_W.pixels(); height = 20.pixels()
            } childOf bar
            slot++
            return b
        }

        shutter = control("Screenshot", BrassAccent.BRASS) { screenshot() }
        BrassTooltip.attach(
            shutter,
            "Screenshot  ($MOD+S)",
            "Writes a PNG of the demo exactly as it looks right now.",
        )

        record = control("Record", BrassAccent.DANGER) { toggleRecord() }
        BrassTooltip.attach(
            record,
            "Record  ($MOD+R)",
            "Captures until you stop. Use the key rather than the button: the pointer is needed on the widget.",
        )
        // Rebuilds the widget from its demo. The one control that exists purely because the demos are
        // hand-driven now: once you have clicked a demo into some state there is otherwise no way back
        // to the untouched version short of leaving the screen and returning.
        control("Reset") { mount() }
        control("Close") { onClose() }

        if (!BrassDemoCapture.available) {
            // Disabled with a reason rather than hidden: the buttons being visibly off is what tells
            // someone on the desktop app that capturing exists and that this host cannot do it.
            shutter.disable("No capture host — run this in game")
            record.disable("No capture host — run this in game")
        }

        caption.constrain { x = 12.pixels(); y = 100.percent() - 18.pixels() } childOf background
        status.constrain { x = 260.pixels(); y = 100.percent() - 18.pixels() } childOf background
    }

    // ---- selection --------------------------------------------------------------------------------

    private fun select(index: Int) {
        current = demos[index]
        navRows.forEachIndexed { i, row -> row.selected = i == index }

        // A recording belongs to the demo it started on; carrying one across a selection would splice
        // two widgets into a single GIF, so finish it before switching.
        if (recording != null) finishRecording()

        mount()

        caption.text = buildString {
            append(current.name)
            append("  ·  ").append(current.outerWidth.toInt()).append(" x ").append(current.outerHeight.toInt())
            // The one thing worth saying *before* the shutter rather than after: these widgets draw
            // through the game's entity renderer and come out as a placeholder with no world loaded, so
            // capturing one from a menu produces a valid file of nothing.
            if (current.worldRequired) append("  ·  needs a loaded world")
        }
    }

    /** Build the demo fresh and put it on the stage. */
    private fun mount() {
        stage.clearChildren()
        stage.constrain {
            width = current.outerWidth.pixels()
            height = current.outerHeight.pixels()
        }
        val content = current.build()
        val root: UIComponent = if (current.card) BrassDemoCard(content, current.fitCard) else content
        root.constrain {
            x = 0.pixels(); y = 0.pixels()
            width = 100.percent(); height = 100.percent()
        } childOf stage
        mounted = root
    }

    // ---- capture ----------------------------------------------------------------------------------

    /**
     * Read the demo back out of the frame that was just drawn.
     *
     * The demo is a real child of this screen, laid out inside [stage] — so its on-screen rectangle is
     * exactly [stage]'s box, and handing that to [BrassDemoCapture.grab] captures the widget as shown,
     * every clip rectangle already resolved against the real framebuffer. There is no second draw and
     * no translation to get wrong: the pixels in the file are the pixels on screen, one frame later.
     */
    private fun grab(): BufferedImage? {
        val host = BrassDemoCapture.current ?: return null
        mounted ?: return null
        return host.grab(stage.getLeft(), stage.getTop(), stage.getWidth(), stage.getHeight())
    }

    private fun screenshot() {
        val host = BrassDemoCapture.current ?: return
        val image = grab() ?: run { say("capture failed"); return }
        say(host.writePng(current.name, image)?.let { "wrote $it" } ?: "write failed")
    }

    /** Start capturing, or stop. Prefer the key over the button so the pointer stays on the widget. */
    private fun toggleRecord() {
        when {
            recording != null -> finishRecording()
            BrassDemoCapture.available -> beginRecording()
        }
    }

    /** Begin capturing. */
    private fun beginRecording() {
        recording = ArrayList()
        sinceFrame = 0f
        record.label = "Stop"
        say("recording ${current.name}…")
    }

    /**
     * Stop capturing **now**, and write in the background.
     *
     * ### Why the write is off-thread
     *
     * Because it is slow enough to be felt. Encoding a GIF means building a shared palette across every
     * frame and quantising all of them against it, which for a few hundred frames is a second or two of
     * pure CPU — and it was running on the render thread, so pressing Stop froze the game until the file
     * was on disk. That reads as the button not working, and the natural response is to press it again.
     *
     * So stopping is now only the three lines that matter interactively: drop the frame list, restore
     * the button, say what is happening. The frames are plain images with no GL handles once
     * [BrassDemoCapture.grab] has read them back, so they hand off safely to a worker while the browser
     * carries on drawing.
     *
     * ### A GIF with no motion is a PNG
     *
     * A recording where nothing happened — the button held down while the widget sat still — is a run
     * of identical frames. Writing that as an animation is wasteful and, worse, misleading: it implies
     * motion that is not there. So an all-identical run collapses to a single PNG, which also keeps the
     * full alpha channel that a GIF would have flattened to one bit.
     */
    private fun finishRecording() {
        val frames = recording
        recording = null
        record.label = "Record"

        val host = BrassDemoCapture.current ?: return
        if (frames.isNullOrEmpty()) { say("nothing recorded"); return }

        val name = current.name
        say("writing ${frames.size} frames…")
        writer.execute {
            val written = if (frames.size == 1 || allIdentical(frames)) {
                host.writePng(name, frames[0])
            } else {
                host.writeGif(name, frames, FPS)
            }
            // Handed back through a reference the draw loop drains, not written straight to the label:
            // this is a worker thread, and Elementa's components are the render thread's alone.
            pendingStatus.set(written?.let { "wrote $it (${frames.size} frames)" } ?: "write failed")
        }
    }

    private fun allIdentical(frames: List<BufferedImage>): Boolean {
        val first = frames[0]
        val w = first.width
        val h = first.height
        val ref = first.getRGB(0, 0, w, h, null, 0, w)
        val buf = IntArray(w * h)
        for (i in 1 until frames.size) {
            val f = frames[i]
            if (f.width != w || f.height != h) return false
            f.getRGB(0, 0, w, h, buf, 0, w)
            if (!ref.contentEquals(buf)) return false
        }
        return true
    }

    private fun say(text: String) {
        status.text = text
    }

    /**
     * Ctrl+R to record, Ctrl+S to shoot.
     *
     * ### Why modified letters rather than function keys
     *
     * These were F9 and F10, chosen because nothing else claims them. On a Mac laptop that is a bad
     * binding: the top row is media keys by default, so a "single key" shortcut is really Fn plus a key
     * on the far edge of the keyboard — the opposite of reachable while your other hand is driving a
     * widget with the trackpad.
     *
     * A modified letter is reachable and mnemonic, and **Ctrl is the right modifier on every platform**
     * because Minecraft maps Cmd to it on macOS (the toolkit relies on this everywhere — see
     * [net.swzo.brass.ui.kit.text.BrassTextInput]). So this is Cmd+R / Cmd+S on a Mac and Ctrl+R /
     * Ctrl+S elsewhere, from one branchless check.
     *
     * ### Why they can be letters at all
     *
     * A demo is a live widget and one of them is a text field, so an *unmodified* letter would be
     * swallowed mid-edit — or worse, fire and type. Two things make the modified form safe: the screen
     * sees the key before the focused component does, and these two combinations are claimed by nothing
     * in the toolkit ([net.swzo.brass.ui.BrassScreen] takes Ctrl+D, Ctrl+M, Ctrl+W and Ctrl+C; the text
     * widgets take the usual editing set, which does not include R or S). Returning early means the
     * keystroke never reaches the demo.
     */
    override fun onKeyPressed(keyCode: Int, typedChar: Char, modifiers: UKeyboard.Modifiers?) {
        if (modifiers?.isCtrl == true) {
            when (keyCode) {
                GLFW.GLFW_KEY_R -> { toggleRecord(); return }
                GLFW.GLFW_KEY_S -> { screenshot(); return }
            }
        }
        super.onKeyPressed(keyCode, typedChar, modifiers)
    }

    // ---- per-frame ---------------------------------------------------------------------------------

    override fun onDrawScreen(matrixStack: UMatrixStack, mouseX: Int, mouseY: Int, partialTicks: Float) {
        pendingStatus.getAndSet(null)?.let { say(it) }

        super.onDrawScreen(matrixStack, mouseX, mouseY, partialTicks)

        // After the screen has drawn, so the frame captured is the one just shown rather than the
        // widget's state a frame ahead of it. Paced to FPS by real time rather than grabbing every
        // rendered frame: the game draws at 60+ fps, so one-frame-per-draw at a 20 fps GIF delay played
        // back in 3x slow motion. Accumulating dt and grabbing on the interval keeps the recording, and
        // so the GIF, at real speed regardless of the render rate.
        recording?.let { frames ->
            sinceFrame += BrassClock.dt
            val interval = 1f / FPS
            if (sinceFrame >= interval) {
                // Drop the whole backlog rather than bursting several catch-up grabs after a hitch,
                // which would look like a stutter of duplicated frames.
                sinceFrame = if (sinceFrame >= interval * 2f) 0f else sinceFrame - interval
                grab()?.let { frames.add(it) }
            }
            if (frames.size >= MAX_FRAMES) {
                say("stopped at $MAX_FRAMES frames")
                finishRecording()
            }
        }
    }

    /**
     * Release any capture resources on the way out.
     *
     * Reading the screen holds nothing, so the bound host's [BrassDemoCapture.release] is usually a
     * no-op — but it stays on the interface for a host that does allocate, and leaving the screen is
     * the one moment that reliably signals capturing is done. An in-flight recording is finished first,
     * so its frames are written rather than thrown away.
     */
    override fun onScreenClose() {
        if (recording != null) finishRecording()
        BrassDemoCapture.current?.release()
        super.onScreenClose()
    }

    /** The card the browser's own panels are made of. */
    private class Card : BrassWidget(BrassAccent.DEFAULT) {
        init {
            chrome = BrassChrome.NONE
        }

        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) =
            BrassCard.draw(m, x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), shadow = true)
    }

    /**
     * The box the demo is parented into.
     *
     * A component of its own rather than parenting the demo straight into the preview card, so the
     * demo's position and size are known exactly — [grab] reads them to work out the translation, and a
     * demo sitting inside a card with its own padding would make that arithmetic depend on the card.
     * Paints a size readout under itself while recording, so it is obvious a take is running.
     */
    private inner class Stage : BrassWidget(BrassAccent.DEFAULT) {
        init {
            chrome = BrassChrome.NONE
        }

        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
            // Under the demo while running, so a take is never obscured by the indicator that it is a
            // take — the whole area inside the stage is about to be in the file.
            val frames = recording ?: return
            BrassFont.draw(
                m, this, "REC ${frames.size}",
                x.toFloat(), (y + h).toFloat() + 2f, Colors.DANGER,
            )
        }
    }

    private companion object {
        const val LIST_W = 150f
        const val BTN_W = 76f

        /**
         * Frames per second a recording captures *and* declares.
         *
         * These have to be the same number, and once were not: the browser grabbed one frame per
         * rendered game frame (60+ fps) while the GIF's delay claimed 20, so every clip played back in
         * ~3x slow motion. Now the capture is paced to this rate by real elapsed time (see the recording
         * block in [onDrawScreen]), so the same 20 is the sampling rate and the playback rate and a clip
         * runs at the speed it was performed. A machine drawing slower than 20 fps captures every frame
         * it can and plays back slightly fast — a fair trade for not stepping a synthetic clock.
         */
        const val FPS = 20

        /**
         * Hard stop on a recording's length.
         *
         * Every frame is a full-size image held in memory, so a record button left on by accident is a
         * slow out-of-memory rather than a large file. At [FPS] this is half a minute, which is far
         * longer than any widget demonstration and short enough to be survivable.
         */
        const val MAX_FRAMES = 600

        /**
         * What to call the primary modifier in a tooltip.
         *
         * The key is the same one either way — Minecraft reports Cmd as ctrl on macOS — but writing
         * "Ctrl+R" on a Mac would send someone hunting for a key that is not the one that works.
         */
        val MOD: String get() = if (UDesktop.isMac) "Cmd" else "Ctrl"
    }
}
