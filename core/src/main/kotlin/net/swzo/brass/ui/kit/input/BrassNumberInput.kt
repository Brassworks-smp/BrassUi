package net.swzo.brass.ui.kit.input

import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.platform.BrassCursor
import net.swzo.brass.ui.kit.text.BrassFont
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * An exact number: a value with a step button at each end, and a middle you can drag to scrub.
 *
 * ```kotlin
 * BrassNumberInput(BrassRange(0f, 64f, step = 1f), initial = 16f) { count = it.toInt() }
 * ```
 *
 * ### Why not a slider
 *
 * [BrassSlider] maps a range onto a track, which is the right control when the *proportion* is what
 * matters - volume, opacity, a percentage. It is the wrong one when the exact number matters, because
 * hitting 17 out of 0..64 on a 120-pixel track is a game of skill. Here the steppers guarantee an
 * exact value, the drag covers the coarse movement, and holding Shift while dragging slows it down for
 * the last few units.
 *
 * The bounds and quantisation come from [BrassRange], shared with the slider, so `0.1 + 0.2` prints
 * as `0.3` in both rather than each control rounding its own way.
 *
 * ### Chrome
 *
 * The same recessed groove [BrassSlider] sits in, filled to `range.fraction(value)` exactly as the
 * slider fills to its own value - a supporting cue, not the primary read (the number is), which is
 * why it uses the slider's resting colours rather than brightening on hover the way a slider's own
 * fill does. The fill spans the **channel between the two steppers** rather than the whole interior,
 * since the steppers are opaque and a full-width fill simply disappeared under them.
 *
 * The two steppers are [BrassCard.miniKeycap] - real buttons, not a flat card - because a control
 * that mixes a raised button into a recessed groove is exactly what a spinner has always looked like
 * everywhere else it appears. It used to be a raised [BrassChrome.FLAT] keycap with two small flat
 * cards nested inside it, which read as a button standing next to a slider rather than a member of
 * the same family - the whole point [BrassRange] exists is that these controls agree on more than
 * their arithmetic.
 */
class BrassNumberInput(
    /** Bounds and quantisation. */
    val range: BrassRange = BrassRange(0f, 100f, step = 1f),
    initial: Float = 0f,
    /** Text after the number - `ms`, `%`, `blocks`. */
    var suffix: String = "",
    private val onChange: (Float) -> Unit = {},
) : BrassWidget(BrassAccent.DEFAULT), BrassValue<Float>, BrassFocusable {

    private val holder = BrassValueHolder(range.snap(initial))

    override var value: Float
        get() = holder.value
        set(v) { holder.value = range.snap(v) }

    override fun setSilently(value: Float) = holder.setSilently(range.snap(value))
    override fun onChange(listener: (Float) -> Unit) = holder.onChange(listener)
    override fun bind(state: BrassState<Float>) = holder.bind(this, state)

    /** True only while a press that started on the middle is still held. */
    private var scrubbing = false
    private var scrubFrom = 0f
    private var scrubValue = 0f

    /** Which stepper is under the cursor, -1 for down, 1 for up, 0 for neither. */
    private var hotStepper = 0

    init {
        // A default height, because BrassForm.addField sets x, y and width but deliberately not
        // height - a control with no intrinsic height resolves to zero and simply does not appear.
        // BrassLabel and BrassTag self-constrain for the same reason. A caller's own constrain{}
        // still wins, since it is applied after construction.
        constrain { height = DEFAULT_H.pixels() }
        // The control paints its own groove through BrassCard; the keycap base must draw nothing.
        chrome = BrassChrome.NONE
        holder.onChange(onChange)

        onMouseClick { e ->
            if (!active || e.mouseButton != 0) return@onMouseClick
            when (stepperAt(e.relativeX)) {
                -1 -> value = range.nudge(value, -1)
                1 -> value = range.nudge(value, 1)
                else -> { scrubbing = true; scrubFrom = e.relativeX; scrubValue = value }
            }
        }
        // Gated on `scrubbing` because Elementa broadcasts drags to the entire tree - without it,
        // dragging anywhere on screen would move this field.
        onMouseDrag { mx, _, btn ->
            if (!active || !scrubbing || btn != 0) return@onMouseDrag
            // Shift slows the scrub for the last few units, the way every value field in every editor
            // does. Absolute from the press point, not incremental, so rounding cannot accumulate.
            val perPixel = range.span / SCRUB_PIXELS * (if (UKeyboard.isShiftKeyDown()) FINE else 1f)
            value = scrubValue + (mx - scrubFrom) * perPixel
        }
        onMouseRelease { scrubbing = false }
    }

    override fun onKeyPressed(keyCode: Int): Boolean = when (keyCode) {
        GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_RIGHT -> { value = range.nudge(value, 1); true }
        GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_LEFT -> { value = range.nudge(value, -1); true }
        GLFW.GLFW_KEY_PAGE_UP -> { value = range.nudge(value, PAGE); true }
        GLFW.GLFW_KEY_PAGE_DOWN -> { value = range.nudge(value, -PAGE); true }
        GLFW.GLFW_KEY_HOME -> { value = range.min; true }
        GLFW.GLFW_KEY_END -> { value = range.max; true }
        else -> false
    }

    /** Which stepper a local x falls in: -1 down, 1 up, 0 the scrub area. */
    private fun stepperAt(localX: Float): Int = when {
        localX < STEPPER -> -1
        localX > getWidth() - STEPPER -> 1
        else -> 0
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val (mx, _) = getMousePosition()
        hotStepper = if (hoveredState) stepperAt(mx - getLeft()) else 0

        if (hoveredState && hotStepper == 0 && active) BrassCursor.request(BrassCursor.Kind.RESIZE_H)

        val x1 = x.toFloat(); val y1 = y.toFloat()
        val x2 = (x + w).toFloat(); val y2 = (y + h).toFloat()

        // The groove first, filled to the value exactly as BrassSlider fills to its own - the same
        // material, so the two read as one family rather than two different controls that happen to
        // share a range.
        //
        // The fill spans only the *channel between the steppers*, not the whole interior: the
        // steppers are opaque buttons drawn over the track, so a fill running the full width was
        // silently painted under them and the bar appeared to start and end at arbitrary points
        // inside the buttons. Bounding it to the channel is also what makes empty and full read
        // correctly - at 0 the channel is bare, at 1 it is filled edge to edge between the buttons.
        BrassCard.trackShell(m, x1, y1, x2, y2)
        val ch1 = x1 + STEPPER
        val ch2 = x2 - STEPPER
        if (ch2 > ch1) {
            BrassCard.trackBlock(m, x1, y1, x2, y2, ch1, ch1 + (ch2 - ch1) * range.fraction(value))
        }

        // Steppers as real buttons riding the groove, so they read as two controls flanking a field
        // rather than as decoration on one. Inset by the shell's own 1-px border so neither paints
        // over the ring it sits inside.
        drawStepper(m, x1 + 1f, y1 + 1f, ch1, y2 - 1f, BrassIcons.MINUS, hotStepper == -1)
        drawStepper(m, ch2, y1 + 1f, x2 - 1f, y2 - 1f, BrassIcons.PLUS, hotStepper == 1)

        // Capped last, exactly as BrassCard.filledTrack orders shell → block → border, so the ring
        // always reads as the outermost edge rather than something the steppers drew over.
        BrassCard.trackBorder(m, x1, y1, x2, y2)

        val text = range.format(value) + if (suffix.isEmpty()) "" else " $suffix"
        val avail = w - STEPPER * 2 - PAD * 2
        val fitted = BrassFont.fit(this, text, avail)
        val tint = if (scrubbing) Colors.UI_ACCENT_BRIGHT else textColor
        BrassFont.draw(
            m, this, fitted,
            x + (w - BrassFont.width(this, fitted)) / 2f,
            y + (h - BrassFont.LINE) / 2f,
            tint,
        )

        // At the very ends of the range, dim the stepper that can no longer do anything - cheaper to
        // read than clicking it and watching nothing happen. Same inset as the stepper cards, so the
        // dim sits on the card rather than bleeding onto the ring beside it.
        if (abs(value - range.min) < 1e-4f) dim(m, x1 + 1f, y1 + 1f, ch1, y2 - 1f)
        if (abs(value - range.max) < 1e-4f) dim(m, ch2, y1 + 1f, x2 - 1f, y2 - 1f)
    }

    private fun drawStepper(
        m: UMatrixStack,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        icon: BrassIcons.Icon,
        hot: Boolean,
    ) {
        BrassCard.miniKeycap(m, x1, y1, x2, y2, hot)
        BrassIcons.draw(
            m, icon,
            (x1 + x2) / 2f - ICON / 2f,
            (y1 + y2) / 2f - ICON / 2f,
            ICON,
            if (hot) Colors.UI_TEXT_HOVER else Colors.UI_TEXT_DARK,
        )
    }

    private fun dim(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float) =
        net.swzo.brass.ui.kit.paint.BrassPaint.rect(m, x1, y1, x2, y2, DIM)

    companion object : BrassDemoSource {

        /**
         * The steppers clicked up and down.
         *
         * The steppers at either end are painted regions rather than child widgets, so they respond
         * to a press landing on them the same way any part of the control does.
         */
        override fun demo() = BrassDemo("number-input", "Number input", 130f, 18f) {
            BrassNumberInput(BrassRange(0f, 100f, step = 1f), initial = 42f, suffix = "%")
        }

        // ---- widget internals ------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        /**
         * Height a control takes when the caller does not say otherwise. Matches the 20 px
         * `BrassForm.addSlider` gives an ordinary slider, so a form holding both does not step.
         */
        private const val DEFAULT_H = 20f
        private const val STEPPER = 12f
        private const val PAD = 3
        private const val ICON = 6f
        /** Pixels of drag that cover the whole range. */
        private const val SCRUB_PIXELS = 200f
        /** Scrub multiplier while Shift is held. */
        private const val FINE = 0.15f
        /** Steps a Page Up or Down moves. */
        private const val PAGE = 10

        private val DIM: java.awt.Color = java.awt.Color(0, 0, 0, 110)
    }
}
