package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.input.BrassSquareButton
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.text.BrassLabel

/**
 * The window chrome: a flat panel (fill + inner border + near-black outer ring) with a **title bar** -
 * title, subtitle, and three **working** square control keys on the right (minimise rolls the window
 * up to its title bar, maximise toggles full size, close fires [onClose]). Fill [content] with the
 * body; the keys and chrome manage themselves.
 *
 * Drag, collapse, maximise, resize and the open/close animation all come from [BrassFrameBase] - see
 * there for why they are no longer implemented here.
 */
class BrassWindow(
    title: String,
    subtitle: String? = null,
    private val onClose: () -> Unit = {},
    /**
     * Title bar height. Sized to the control keycaps plus a couple of pixels of breathing room - a
     * title bar is chrome and should not spend more of the frame than the controls in it need.
     */
    titleBarH: Int = 20,
    /**
     * Whether the title bar carries the minimise / maximise / close keys.
     *
     * A screen's *only* window has nowhere to minimise to, nothing to maximise past and nothing
     * behind it to close back to - the keys would be three controls that either do nothing useful or
     * strand the user. Such a window passes `false` and keeps the bar as a plain title strip.
     */
    private val controls: Boolean = true,
    /** Minimum size the window can be dragged down to. */
    minW: Float = 260f,
    minH: Float = 160f,
) : BrassFrameBase(titleBarH, minW, minH) {

    private var maximizeButton: BrassSquareButton? = null
    private var closeFired = false

    init {
        val titleBar = UIContainer().constrain {
            x = 0.pixels(); y = 0.pixels()
            width = 100.percent(); height = titleBarH.pixels()
        } childOf this

        // Drag the title bar to move the window, and double-click it to maximise/restore, exactly as
        // every desktop window manager does. A screen's main window used to be the one frame in the
        // toolkit you could not move, which made it the odd one out for no reason.
        installDrag(titleBar)

        // No app chip. It was a decorative icon in the corner of every window that never meant
        // anything, and with the title bar down to 20 px it was the biggest thing in it.
        val titleText = BrassLabel(title, Colors.UI_TEXT_HOVER).also { it.entranceEnabled = false }
            .constrain { x = 8.pixels(); y = CenterConstraint() } childOf titleBar
        if (subtitle != null) {
            BrassLabel("/  $subtitle", Colors.UI_TEXT_DARK).also { it.entranceEnabled = false }
                .constrain {
                    x = basicXConstraint { titleText.getRight() + 8f }
                    y = CenterConstraint()
                } childOf titleBar
        }

        // Right-aligned square keycap controls: minimise / maximise / close.
        //
        // No glyphs. At this size the icons were a couple of pixels of noise inside an already small
        // key, and three near-identical smudges do not read as three different actions - position and
        // the close key's danger accent do that work on their own, the way a row of plain keys on a
        // title bar has always been read. The tooltips say the rest.
        if (controls) {
            val span = CONTROL_W * 3 + CONTROL_GAP * 2
            val keys = UIContainer().constrain {
                x = 5.pixels(true); y = CenterConstraint()
                width = span.pixels(); height = CONTROL_H.pixels()
            } childOf titleBar

            val close = BrassSquareButton(BrassIcons.NONE, BrassAccent.DANGER) { requestClose() }
            maximizeButton = BrassSquareButton(BrassIcons.NONE, BrassAccent.DEFAULT) { toggleMaximize() }
                .also { it.selectable = true }
            val minB = BrassSquareButton(BrassIcons.NONE, BrassAccent.DEFAULT) { toggleCollapse() }
            listOf(
                minB to "Minimise",
                maximizeButton!! to "Maximise",
                close to "Close",
            ).forEachIndexed { i, (b, tip) ->
                b.entranceEnabled = false
                BrassTooltip.attach(b, tip)
                b.constrain {
                    x = (i * (CONTROL_W + CONTROL_GAP)).pixels(); y = 0.pixels()
                    width = CONTROL_W.pixels(); height = CONTROL_H.pixels()
                } childOf keys
            }

            // Tells the base class which subtree must not arm a drag or read as a double-click.
            controlsBar = keys
        }
    }

    /** The maximise key has no glyph to swap, so it reports the state by staying lit instead. */
    override fun onMaximizeChanged(maximized: Boolean) {
        maximizeButton?.selected = maximized
    }

    /**
     * Fired once, not once per frame: the window stays in the tree until whatever [onClose] does
     * takes the screen down, and the animation keeps reporting finished the whole time.
     */
    override fun onClosed() {
        if (closeFired) return
        closeFired = true
        onClose()
    }

    private companion object {
        // Title-bar control keys: wider than they are tall. A square key is as tall as the bar itself
        // once its lip and ring are counted, which is what made them look oversized; a landscape key
        // sits inside the bar with room to spare and still gives a comfortable click target.
        const val CONTROL_W = 18
        const val CONTROL_H = 10
        const val CONTROL_GAP = 4
    }
}
