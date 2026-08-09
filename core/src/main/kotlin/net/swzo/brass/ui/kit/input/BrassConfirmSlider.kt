package net.swzo.brass.ui.kit.input

import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.base.BrassFocusable
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * Hold it down to confirm - for the one action you cannot take back.
 * ```kotlin
 * BrassConfirmSlider("Hold to sell 40,000") { market.sell(order) }
 * ```
 * ### Why not a dialog
 * A confirmation dialog asks a question the user answers by clicking a button in the same place the
 * first button was, which is exactly the muscle memory that got them there. It is dismissed by
 * reflex. A hold is a *deliberate* gesture: it cannot be produced by a double-click, it cannot be
 * produced by the Enter key landing on a focused default, and it takes long enough that an accident
 * is caught part-way - let go and it drains back to nothing.
 * ### Why not a slide
 * This was a slide-the-handle-across control, and the handle was the problem. It had to be *found*
 * before the gesture could start (a press on the track did nothing, which reads as a dead control),
 * it needed a drag, which is the one gesture that is awkward on a trackpad and impossible on a
 * controller, and once slid it left a grip parked at the far end with nothing left to do - a
 * confirmed action still wearing an interactive-looking handle. A hold has none of that: the whole
 * control is the target, press is the whole gesture, and the confirmed state is just a filled button.
 * ### Chrome
 * A plain keycap - the same raised fill, ring and bottom lip as [BrassButton], coloured by [accent]
 * exactly the way any other danger button is. It used to paint its own recessed track through
 * BrassCard, on the theory that a hold-to-confirm was closer kin to a progress bar than to a
 * button. It reads the other way around: this *is* a button, one with an unusually deliberate press,
 * and a groove in the middle of a row of ordinary buttons looked like a different control entirely
 * rather than a stricter version of the one beside it. The hold still needs to be *visible* while it
 * runs, so the progress is a fill swept left to right over the keycap's own interior - the base class
 * paints the button, [drawContent] only adds the sweep and the label.
 * ### After it fires
 * The button stays [confirmed] until [reset] is called, so a caller doing async work can leave the
 * control showing what happened rather than snapping back to an armed state that invites a second go.
 */
class BrassConfirmSlider(
    var label: String = "Hold to confirm",
    var confirmedLabel: String = "Confirmed",
    accent: BrassAccent = BrassAccent.DANGER,
    /** How long the button must be held, in seconds. */
    var holdSeconds: Float = DEFAULT_HOLD,
    private val onConfirm: () -> Unit = {},
) : BrassWidget(accent), BrassFocusable {

    var confirmed: Boolean = false
        private set

    private var holding = false

    private var progress = 0f

    init {
        // chrome is left at its default (KEYCAP) - the base class paints the whole button; see the
        // class docs for why this is no longer a BrassCard track.
        clickable = true
        constrain { height = DEFAULT_H.pixels() }

        onMouseClick { e ->
            if (!active || confirmed || e.mouseButton != 0) return@onMouseClick
            // Anywhere on the control. There is no handle to hunt for - that was the slide's flaw.
            holding = true
        }

        // Elementa broadcasts releases to the whole tree, which is exactly right here: a press that
        // ends with the cursor dragged off the button must still count as letting go.
        onMouseRelease { holding = false }
    }

    private fun complete() {
        if (confirmed) return
        confirmed = true
        holding = false
        progress = 1f
        onConfirm()
    }

    fun reset() {
        confirmed = false
        holding = false
        progress = 0f
    }

    override fun onActivate(): Boolean = true

    override fun proxyActivate() {}

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        if (!confirmed) {
            // Drains faster than it fills, so an abandoned hold is unmistakably abandoned rather than
            // sitting there looking half-armed. Both rates are per second, off the shared clock.
            val rate = if (holding && active) 1f / holdSeconds.coerceAtLeast(0.05f) else -DRAIN_RATE
            progress = (progress + rate * BrassClock.dt).coerceIn(0f, 1f)
            if (progress >= 1f) complete()
        }

        // The sweep, inset by the keycap's own 1-px border so it never paints over the ring - the
        // base class has already drawn the button by the time this runs. Confirmed simply means full,
        // which is why the confirmed state needs no special-case paint of its own: the fill just
        // finishes where it was heading.
        if (progress > 0.001f) {
            val ix1 = x + 1f
            val ix2 = (x + w - 1).toFloat()
            val iy1 = y + 1f
            val iy2 = (y + h - 1).toFloat()
            val fx = ix1 + (ix2 - ix1) * progress
            if (fx > ix1) {
                BrassPaint.rect(m, ix1, iy1, fx, iy2, if (confirmed) accent.accent else fillFor(progress))
                // A lit leading edge, the same read BrassCard.trackBlock gives a filled region - the
                // one piece of that language worth keeping now that the rest of the control is a
                // button rather than a track.
                if (!confirmed) BrassPaint.rect(m, (fx - 1f).coerceAtLeast(ix1), iy1, fx, iy2, accent.accentHover)
            }
        }

        val text = if (confirmed) confirmedLabel else label
        val fitted = BrassFont.fit(this, text, w - PAD * 2f)
        BrassFont.draw(
            m, this, fitted,
            x + (w - BrassFont.width(this, fitted)) / 2f,
            y + (h - BrassFont.LINE) / 2f,
            if (confirmed) Colors.lighten(accent.accent, TEXT_LIFT) else textColor,
            true,
        )
    }

    private fun fillFor(t: Float): Color = Colors.mix(accent.dark, accent.accent, t)

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("confirm-slider", "Confirm slider", 190f, 20f) {
            BrassConfirmSlider("Hold to delete world")
        }

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val DEFAULT_H = 18f
        private const val PAD = 4f
        private const val DEFAULT_HOLD = 1.1f
        private const val DRAIN_RATE = 2.2f
        private const val TEXT_LIFT = 0.55f
    }
}
