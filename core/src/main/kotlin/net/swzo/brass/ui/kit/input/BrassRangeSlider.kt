package net.swzo.brass.ui.kit.input

import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.platform.BrassCursor
import net.swzo.brass.ui.kit.text.BrassFont
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.roundToInt
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A slider with two handles - a filter's "between 20 and 60", a price band, a level range.
 *
 * ```kotlin
 * BrassRangeSlider(BrassRange(0f, 100f, step = 1f), low = 20f, high = 60f) { lo, hi -> filter(lo, hi) }
 * ```
 *
 * ### Which handle you get
 *
 * A press grabs whichever handle is **nearer**, which is the behaviour that needs no explanation. The
 * one exception is when both sit on the same value: then the side of the press decides, so a collapsed
 * range can still be opened in either direction rather than being stuck.
 *
 * The two handles cannot cross. Pushing one past the other clamps it, rather than swapping them -
 * swapping means the handle under your cursor is suddenly the other one, and the drag you are halfway
 * through starts doing the opposite of what it was.
 *
 * Bounds and quantisation come from [BrassRange], shared with [BrassSlider] and [BrassNumberInput].
 *
 * ### Chrome
 *
 * [BrassSlider] with a second handle, drawn the same way: the same grip riding a pixel proud of the
 * groove, the same hover glow eased in rather than snapped, the same 1-px inner border capping the
 * band, and the same centred readout. It used to be a near-miss of the slider rather than a real
 * match - a flush handle instead of a proud one, a static wash instead of a glow, and no inner border
 * at all, so its band ran to a bare edge where every other track in the toolkit had a rule. No one of
 * those looked *wrong* alone, but together they made a control that was clearly not quite the same
 * thing as the one beside it.
 */
class BrassRangeSlider(
    val range: BrassRange = BrassRange(0f, 1f),
    low: Float = 0f,
    high: Float = 1f,
    /** Text after each number in the readout. */
    var suffix: String = "",
    private val onChange: (low: Float, high: Float) -> Unit = { _, _ -> },
) : BrassWidget(BrassAccent.DEFAULT), BrassFocusable {

    /** Bottom of the selected band. Never above [high]. */
    var low: Float = range.snap(low)
        set(value) {
            val next = range.snap(value).coerceAtMost(high)
            if (next == field) return
            field = next
            onChange(field, high)
        }

    /** Top of the selected band. Never below [low]. */
    var high: Float = range.snap(high)
        set(value) {
            val next = range.snap(value).coerceAtLeast(this.low)
            if (next == field) return
            field = next
            onChange(this.low, field)
        }

    /** Which handle a drag is moving: -1 low, 1 high, 0 none. */
    private var grabbed = 0

    /** Which handle the cursor is nearest while merely hovering (not dragging), or 0. */
    private var hovered = 0

    /** Per-handle brightness, eased exactly as [BrassSlider]'s single one is. */
    private val lowGlow = BrassEased(0f, speed = GLOW_SPEED)
    private val highGlow = BrassEased(0f, speed = GLOW_SPEED)

    init {
        // A default height, because BrassForm.addField sets x, y and width but deliberately not
        // height - a control with no intrinsic height resolves to zero and simply does not appear.
        // BrassLabel and BrassTag self-constrain for the same reason. A caller's own constrain{}
        // still wins, since it is applied after construction.
        constrain { height = DEFAULT_H.pixels() }
        // The widget paints its own track through BrassCard; the keycap base must draw nothing.
        chrome = BrassChrome.NONE

        onMouseClick { e ->
            if (!active || e.mouseButton != 0) return@onMouseClick
            grabbed = nearestHandle(e.relativeX)
            setFromLocal(e.relativeX)
        }
        // Gated on `grabbed`, because Elementa broadcasts drags to the whole tree.
        onMouseDrag { mx, _, btn -> if (active && btn == 0 && grabbed != 0) setFromLocal(mx) }
        onMouseRelease { grabbed = 0 }
    }

    /**
     * Whichever handle is nearer the press, or - when they coincide - whichever one the press is on
     * the far side of, so a collapsed range can be reopened.
     */
    private fun nearestHandle(localX: Float): Int {
        val target = range.valueAt(fractionAt(localX))
        val toLow = abs(target - low)
        val toHigh = abs(target - high)
        if (toLow == toHigh) return if (target < low) -1 else 1
        return if (toLow < toHigh) -1 else 1
    }

    private fun fractionAt(localX: Float): Float {
        val avail = (getWidth() - 2f).coerceAtLeast(1f)
        return ((localX - 1f) / avail).coerceIn(0f, 1f)
    }

    private fun setFromLocal(localX: Float) {
        val target = range.valueAt(fractionAt(localX))
        if (grabbed < 0) low = target else if (grabbed > 0) high = target
    }

    /** Left and right move the handle that was last grabbed, defaulting to the low one. */
    override fun onKeyPressed(keyCode: Int): Boolean {
        val handle = if (grabbed == 0) -1 else grabbed
        return when (keyCode) {
            GLFW.GLFW_KEY_LEFT -> { step(handle, -1); true }
            GLFW.GLFW_KEY_RIGHT -> { step(handle, 1); true }
            GLFW.GLFW_KEY_TAB -> { grabbed = -handle; true }
            else -> false
        }
    }

    private fun step(handle: Int, direction: Int) {
        if (handle < 0) low = range.nudge(low, direction) else high = range.nudge(high, direction)
    }

    /**
     * Handle width - the same formula [BrassSlider] uses, rounded to a whole pixel so the edge is
     * crisp rather than blurred across two. Capped tighter than the single-handle slider's `w / 4`:
     * two handles have to fit side by side without touching, which one never does.
     */
    private fun handleWidth(w: Int, h: Int): Int =
        (h * 0.42f).roundToInt().coerceIn(5, (w / 6).coerceAtLeast(5))

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        if (hoveredState && active) BrassCursor.request(BrassCursor.Kind.RESIZE_H)

        val (mx, _) = getMousePosition()
        hovered = if (hoveredState) nearestHandle(mx - getLeft()) else 0

        val x1 = x.toFloat(); val y1 = y.toFloat()
        val x2 = (x + w).toFloat(); val y2 = (y + h).toFloat()

        // The empty track, then the selected band over it - the same recessed card the slider and the
        // progress bar use, so the three read as the same material.
        BrassCard.trackShell(m, x1, y1, x2, y2)

        val ix1 = x1 + 1f
        val ix2 = x2 - 1f
        val span = ix2 - ix1
        val lowX = ix1 + span * range.fraction(low)
        val highX = ix1 + span * range.fraction(high)

        // The band spans lowX..highX inside the full track, which is what trackBlock's bx1/bx2 are
        // for - the fill knows the card's own inset, so the band cannot spill over its border.
        if (highX > lowX) BrassCard.trackBlock(m, x1, y1, x2, y2, lowX, highX)

        // The card's own 1-px inner border, capping the fill - the step this control was missing.
        // BrassSlider gets it free inside filledTrack and BrassNumberInput calls it explicitly, so
        // this was the only track in the toolkit whose band ran to a bare edge instead of a rule.
        BrassCard.trackBorder(m, x1, y1, x2, y2)

        val gw = handleWidth(w, h).toFloat()
        // Eased rather than snapped, exactly like BrassSlider's own glow - a handle lighting up
        // instantly on hover read as flickering next to a slider's smooth brighten.
        lowGlow.target = if (grabbed < 0 || hovered < 0) 1f else 0f
        highGlow.target = if (grabbed > 0 || hovered > 0) 1f else 0f
        drawHandle(m, lowX, y1, y2, gw, ix1, ix2, lowGlow.advance())
        drawHandle(m, highX, y1, y2, gw, ix1, ix2, highGlow.advance())

        // Centred and unbacked, the same readout BrassSlider and BrassNumberInput draw.
        val label = "${range.format(low)}–${range.format(high)}${if (suffix.isEmpty()) "" else " $suffix"}"
        val fitted = BrassFont.fit(this, label, w - PAD * 2f)
        if (fitted.isNotEmpty()) {
            BrassFont.draw(
                m, this, fitted,
                x + (w - BrassFont.width(this, fitted)) / 2f,
                y + (h - BrassFont.LINE) / 2f,
                if (grabbed != 0) Colors.UI_ACCENT_BRIGHT else Colors.UI_TEXT_HOVER,
                true,
            )
        }
    }

    /** One handle - [BrassCard.grip], standing a pixel proud top and bottom exactly as the slider's does. */
    private fun drawHandle(
        m: UMatrixStack,
        centre: Float,
        y1: Float,
        y2: Float,
        width: Float,
        left: Float,
        right: Float,
        glow: Float,
    ) {
        // Clamped so a handle at either extreme still sits fully inside the track rather than half
        // outside it.
        val gx1 = (centre - width / 2f).coerceIn(left, (right - width).coerceAtLeast(left))
        BrassCard.grip(m, gx1, y1 - 1f, gx1 + width, y2 + 1f, glow)
    }

    companion object : BrassDemoSource {

        /**
         * Both handles moved, so the demo shows the span narrowing rather than a bar with two ticks.
         *
         * Worth moving both handles: a range slider is two controls sharing a track, and a recording
         * that only ever moves one of them documents an ordinary slider with extra decoration.
         */
        override fun demo() = BrassDemo("range-slider", "Range slider", 200f, 22f) {
            BrassRangeSlider(BrassRange(0f, 100f), low = 15f, high = 85f, suffix = "%")
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
        /** How fast a handle brightens on hover or grab - the same speed BrassSlider's glow uses. */
        private const val GLOW_SPEED = 12f
        /** Room left either side of the centred readout, matching BrassNumberInput's. */
        private const val PAD = 3f
    }
}
