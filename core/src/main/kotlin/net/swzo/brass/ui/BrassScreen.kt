package net.swzo.brass.ui

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.WindowScreen
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.basicColorConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.demo.BrassDemoCapture
import net.swzo.brass.ui.kit.dev.BrassDevMode
import net.swzo.brass.ui.kit.html.BrassHtml
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.platform.BrassCursor
import net.swzo.brass.ui.kit.surface.BrassContextMenu
import net.swzo.brass.ui.kit.surface.BrassToast
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTextField
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/**
 * Base Elementa screen with a full-bleed [backdropColor] already laid into the window; subclasses
 * add their content to [background]. A translucent backdrop lets the running game show through.
 */
open class BrassScreen(
    private val backdropColor: Color? = null,
) : WindowScreen(ElementaVersion.V8, drawDefaultBackground = false) {

    private val backdrop: UIBlock = UIBlock(basicColorConstraint { backdropColor ?: Colors.UI_BACKGROUND }).constrain {
        width = 100.percent()
        height = 100.percent()
    } childOf window

    val background: UIContainer = UIContainer().constrain {
        width = 100.percent()
        height = 100.percent()
    } childOf backdrop


    private var showcaseArmed = false

    override fun onKeyPressed(keyCode: Int, typedChar: Char, modifiers: UKeyboard.Modifiers?) {
        // Ctrl+Shift+D toggles the layout inspector (see BrassDevMode)
        if (keyCode == GLFW.GLFW_KEY_D && modifiers?.isCtrl == true && modifiers.isShift) {
            BrassDevMode.toggle()
            return
        }
        // Ctrl+Shift+S captures the whole screen onto a background card, game world dropped (see
        // captureShowcase). Checked here, above the focus guard, so it fires while a field is focused too.
        if (keyCode == GLFW.GLFW_KEY_S && modifiers?.isCtrl == true && modifiers.isShift) {
            captureShowcase()
            return
        }
        // A focused HTML widget owns the keyboard, exactly like a focused text field: every key (Tab,
        // Enter, arrows, Escape) goes to the page, and the screen's own shortcuts are skipped while
        // it has the keys. Elementa cannot deliver raw key events (it has no key-up), so the raw
        // channel lives here, on the screen - see BrassHtml.onScreenKey.
        val html = BrassHtml.focused
        if (html != null && html.onScreenKey(keyCode, typedChar, modifiers)) return

        // Window shortcuts, routed to the topmost frame (see BrassFrame). Skipped while a text field has
        // focus so Cmd+W-style combos can never eat a keystroke mid-edit.
        if (findFocusedInput(window) == null) {
            val frame = topmostFrame()
            if (frame != null) {
                val ctrl = modifiers?.isCtrl == true
                when {
                    keyCode == GLFW.GLFW_KEY_F11 -> { frame.toggleMaximize(); return }
                    ctrl && keyCode == GLFW.GLFW_KEY_M -> { frame.toggleCollapse(); return }
                    ctrl && keyCode == GLFW.GLFW_KEY_W -> { frame.requestClose(); return }
                }
            }
        }

        // Before navigation, before Escape, before the focused field. A command palette's up/down and
        // Enter belong to its result list, not to the query box they are typed into - and the block
        // below is skipped entirely while a field has focus, so there is no later point at which such
        // a layer could be offered the key. Only the topmost is asked; see BrassKeyLayer.
        val keyLayer = deepChildren(window, BrassKeyLayer::class.java).lastOrNull()
        if (keyLayer != null && keyLayer.onLayerKey(keyCode, modifiers)) return

        // Skipped entirely while a text field is focused: Tab and Enter belong to the field, and
        // Space is a character.
        val typingNow = findFocusedInput(window)
        if (typingNow == null) {
            when {
                keyCode == GLFW.GLFW_KEY_TAB -> {
                    BrassFocus.moveNext(window, backwards = modifiers?.isShift == true)
                    return
                }
                keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER ||
                    keyCode == GLFW.GLFW_KEY_SPACE -> {
                    // proxyActivate is what a *click* means, so this makes every control that was
                    // already clickable operable from the keyboard for free - see BrassFocus.
                    if (BrassFocus.activate()) return
                }
                // Ctrl/Cmd+C copies a selected read-only label - see BrassLabel.copyable.
                keyCode == GLFW.GLFW_KEY_C && modifiers?.isCtrl == true -> {
                    if ((BrassFocus.focused as? BrassLabel)?.copy() == true) return
                }
                BrassFocus.handleKey(keyCode) -> return
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Escape unwinds one step at a time: first a focused text field, then the topmost floating
            // layer, and only then the screen itself.
            val typing = findFocusedInput(window)
            if (typing != null) {
                // Cast rather than putting loseFocus on BrassTextField: it is Elementa's own method on
                // UIComponent, and every text field is one — declaring it on the marker interface would
                // be restating a member the implementers already have.
                (typing as? UIComponent)?.loseFocus()
                return
            }
            // Escape also drops keyboard focus before it starts closing layers, so the ring does not
            // linger over a screen the user is on their way out of.
            if (BrassFocus.focused != null) {
                BrassFocus.clear()
                return
            }
            // Skip layers already animating out - otherwise a second Escape lands on the same
            // half-closed popup instead of peeling off the one beneath it - and layers that have
            // opted out of Escape entirely (a dialog that must be answered).
            val topmost = deepChildren(window, BrassDismissable::class.java)
                .lastOrNull { !it.dismissing && it.escapeDismissable }
            if (topmost != null) {
                // swallow the key: close this layer only, leaving the screen open
                topmost.dismiss()
                return
            }
            // Nothing is layered over the screen: close it here rather than letting the key fall through
            // to the superclass, which dispatches it to the focused component first - a focused canvas or
            // node editor consumes the key and Escape would otherwise never close the screen.
            if (shouldCloseOnEsc()) {
                onClose()
                return
            }
        }
        super.onKeyPressed(keyCode, typedChar, modifiers)
    }

    /**
     * Key-up half of the HTML raw-key channel (see the routing in [onKeyPressed]). Elementa has no
     * release event, so without this an embedded page would never see keys go back up.
     */
    override fun onKeyReleased(keyCode: Int, typedChar: Char, modifiers: UKeyboard.Modifiers?) {
        val html = BrassHtml.focused
        if (html != null && html.onScreenKeyRelease(keyCode, modifiers)) return
        super.onKeyReleased(keyCode, typedChar, modifiers)
    }

    private fun topmostFrame(): BrassFrame? =
        deepChildren(window, BrassFrame::class.java).lastOrNull()

    /**
     * Every descendant of [root] of type [type], in draw order - so the **last** entry is whatever
     * is visually on top.
     * Deliberately a deep walk rather than a scan of `background.children`. A floating layer is not
     * necessarily a direct child of the content area: a [net.swzo.brass.ui.kit.surface.BrassContextMenu]
     * parents itself to whatever its opener resolves as the root, which walks all the way up to the
     * Elementa window - one level *above* [background]. The shallow scan therefore never saw it, so
     * Escape fell straight through to closing the whole screen while a colour picker was open, which
     * is exactly what [BrassDismissable] exists to prevent.
     */
    private fun <T : Any> deepChildren(root: UIComponent, type: Class<T>): List<T> =
        BrassTree.descendantsOfType(root, type)

    override fun onScreenClose() {
        super.onScreenClose()
        BrassLifecycle.disposeTree(window)
        BrassContextMenu.closeOpen()
        BrassToast.clear(background)
        BrassDevMode.forgetScreen()
        // Put the per-session globals back, so the next screen does not inherit a half-finished fade
        // or a stale entrance phase from this one's closing animation.
        BrassFocus.clear()
        BrassUiSession.reset()
    }

    private fun findFocusedInput(root: UIComponent): BrassTextField? {
        if (root is BrassTextField && root.focused) return root
        for (child in root.children) {
            findFocusedInput(child)?.let { return it }
        }
        return null
    }

    override fun onDrawScreen(matrixStack: UMatrixStack, mouseX: Int, mouseY: Int, partialTicks: Float) {
        // Counters reset before anything draws, so the numbers the overlay reports describe this
        // frame. Collection stays on with dev mode off - it is a few integer increments, and stats
        // that only exist while you are watching cannot catch a regression.
        // One clock reading for the whole frame; everything that eases reads it (see BrassClock).
        BrassClock.beginFrame()
        // Dispose anything that has left the tree since the last sweep - see BrassLifecycle.
        BrassLifecycle.sweep()
        BrassStats.beginFrame(window)
        BrassTooltip.cursor(mouseX.toFloat(), mouseY.toFloat())

        // Attach/detach the docked dev panel before the tree draws, so it renders as part of the normal
        // pass (under a BrassDevLayer that keeps it out of the stats) and shrinks the content beside it.
        BrassDevMode.sync(window, background)

        super.onDrawScreen(matrixStack, mouseX, mouseY, partialTicks)

        // Showcase capture. Done here, after the content has drawn once, but before the tooltip and dev
        // overlay below so neither lands in the shot. It repaints the UI twice more over known
        // backgrounds and reads each back — see captureShowcase.
        if (showcaseArmed) {
            showcaseArmed = false
            captureShowcaseNow(matrixStack)
        }

        // Tooltips draw above everything, from a single shared instance rather than a component in
        // the tree - no z-order fight with popups, and nothing that reparents itself mid-frame.
        BrassTooltip.draw(matrixStack, window, window.getWidth(), window.getHeight())

        if (BrassDevMode.enabled) {
            BrassDevMode.drawOverlay(
                matrixStack, window, window.getWidth(), window.getHeight(),
                mouseX.toFloat(), mouseY.toFloat(),
            )
        }

        // Widgets request a cursor while hovered; the strongest request wins and is applied once,
        // here, after everything has had its say.
        BrassCursor.apply()
    }


    protected open val showcaseName: String get() = "showcase"

    /**
     * Photograph the whole screen for a showcase page: **just the UI, isolated onto transparency**, the
     * game world (or anything else behind it) dropped rather than baked in.
     * ### How a transparent cut-out comes off an opaque framebuffer
     * The framebuffer has no usable alpha once the GUI has drawn — a widget's pixels and the world
     * showing through beside them are the same opaque colour, so a single read cannot tell them apart.
     * The way out is to photograph the UI **twice over known backgrounds** and solve for the coverage
     * that must have produced both: paint the region black and read it, paint it white and read it, and
     * for every pixel the gap between the two readings is exactly how much background still showed
     * through — i.e. `1 − alpha`. That recovers a true per-pixel alpha, so anti-aliased corners and the
     * window's own soft shadow come out as real partial transparency rather than a hard, fringed key.
     * Both repaints happen **inside one frame**, right here in [captureShowcaseNow], so the UI is in the
     * identical state for both — a live progress bar or chart cannot move between the two reads and
     * smear the matte. And because each read is off the real framebuffer (see [BrassDemoCapture]), every
     * scissor clip is already resolved: scrolled and clipped widgets isolate correctly, which an
     * offscreen re-render could not manage.
     * ### Why it arms rather than grabs
     * The controlled backgrounds are not on screen when this is called — a key press runs before the
     * frame draws. So this only *arms*; [onDrawScreen] does the two repaints and reads at the end of the
     * pass and reports where the file landed. With no [BrassDemoCapture] bound there is nothing to write
     * to, so it says so and does nothing — same as the demo browser's dimmed shutter off-game.
     */
    protected fun captureShowcase() {
        if (BrassDemoCapture.current == null) {
            BrassToast.show(background, "No capture host — run this in game or the desktop app", BrassToast.Type.ERROR)
            return
        }
        showcaseArmed = true
    }

    private fun captureShowcaseNow(m: UMatrixStack) {
        val host = BrassDemoCapture.current ?: return
        val b = contentBounds() ?: run {
            BrassToast.show(background, "Nothing on screen to capture", BrassToast.Type.ERROR)
            return
        }
        val sw = window.getWidth()
        val sh = window.getHeight()
        // Pad past the content so the window's drop shadow and anti-aliased edges are inside the frame,
        // then clamp to the screen so a UI that runs to the edge reads what fits rather than out of bounds.
        val x1 = (b[0] - EDGE_PAD).coerceAtLeast(0f)
        val y1 = (b[1] - EDGE_PAD).coerceAtLeast(0f)
        val x2 = (b[2] + EDGE_PAD).coerceAtMost(sw)
        val y2 = (b[3] + EDGE_PAD).coerceAtMost(sh)
        val w = x2 - x1
        val h = y2 - y1
        if (w <= 0f || h <= 0f) {
            BrassToast.show(background, "Nothing on screen to capture", BrassToast.Type.ERROR)
            return
        }

        val onBlack = paintBackdropAndContent(m, x1, y1, x2, y2, Color.BLACK)?.let { host.grab(x1, y1, w, h) }
        val onWhite = paintBackdropAndContent(m, x1, y1, x2, y2, Color.WHITE)?.let { host.grab(x1, y1, w, h) }
        if (onBlack == null || onWhite == null) {
            BrassToast.show(background, "Showcase capture failed", BrassToast.Type.ERROR)
            return
        }

        val cut = isolate(onBlack, onWhite)
        val path = host.writePng(showcaseName, cut)
        BrassToast.show(
            background,
            path?.let { "Showcase saved: $it" } ?: "Showcase write failed",
            if (path != null) BrassToast.Type.SUCCESS else BrassToast.Type.ERROR,
        )
    }

    private fun paintBackdropAndContent(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        backdrop: Color,
    ): Unit? {
        BrassPaint.rect(m, x1, y1, x2, y2, backdrop)
        // Snapshot the list: a child's draw must never be able to reparent mid-iteration.
        val children = background.children.toList()
        if (children.isEmpty()) return null
        for (child in children) child.draw(m)
        return Unit
    }

    private fun contentBounds(): FloatArray? {
        var x1 = Float.MAX_VALUE
        var y1 = Float.MAX_VALUE
        var x2 = -Float.MAX_VALUE
        var y2 = -Float.MAX_VALUE
        var found = false
        for (child in background.children) {
            if (child.getWidth() <= 0f || child.getHeight() <= 0f) continue
            found = true
            x1 = minOf(x1, child.getLeft())
            y1 = minOf(y1, child.getTop())
            x2 = maxOf(x2, child.getRight())
            y2 = maxOf(y2, child.getBottom())
        }
        return if (found) floatArrayOf(x1, y1, x2, y2) else null
    }

    /**
     * Solve two opaque readings of the same UI — one over black, one over white — for a straight-alpha
     * cut-out.
     * For a pixel that ends up covering the UI with coverage `a`, the reading over a background `k` is
     * `a·fg + (1 − a)·k`. Subtracting the black reading (`k = 0`) from the white one (`k = 255`) leaves
     * `(1 − a)·255`, independent of the UI colour — so `a` falls straight out of the gap between them,
     * and the unmultiplied colour is the black reading divided back through `a`. A pixel the UI never
     * touched reads black-vs-white at the full gap and drops to fully transparent.
     */
    private fun isolate(onBlack: BufferedImage, onWhite: BufferedImage): BufferedImage {
        val w = minOf(onBlack.width, onWhite.width)
        val h = minOf(onBlack.height, onWhite.height)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val cb = onBlack.getRGB(x, y)
                val cw = onWhite.getRGB(x, y)
                val br = (cb ushr 16) and 0xFF; val bg = (cb ushr 8) and 0xFF; val bb = cb and 0xFF
                val wr = (cw ushr 16) and 0xFF; val wg = (cw ushr 8) and 0xFF; val wb = cw and 0xFF

                // Average the per-channel gap: (1 - alpha) * 255, so alpha is 1 minus that fraction.
                val gap = ((wr - br) + (wg - bg) + (wb - bb)) / 3f
                val alpha = (1f - gap / 255f).coerceIn(0f, 1f)
                if (alpha <= 0.004f) {
                    out.setRGB(x, y, 0)
                    continue
                }
                // Straight (un-premultiplied) colour: the over-black reading is alpha*fg, so divide back.
                fun straight(c: Int): Int = (c / alpha).roundToInt().coerceIn(0, 255)
                val a8 = (alpha * 255f).roundToInt().coerceIn(0, 255)
                out.setRGB(x, y, (a8 shl 24) or (straight(br) shl 16) or (straight(bg) shl 8) or straight(bb))
            }
        }
        return out
    }

    private companion object {
        const val EDGE_PAD = 10f
    }
}
