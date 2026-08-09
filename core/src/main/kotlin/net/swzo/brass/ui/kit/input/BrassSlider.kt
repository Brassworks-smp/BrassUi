package net.swzo.brass.ui.kit.input

import gg.essential.universal.UMatrixStack
import gg.essential.universal.UKeyboard
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.platform.BrassCursor
import net.swzo.brass.ui.kit.text.BrassFont
import kotlin.math.roundToInt
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A slider built from the toolkit's **filled-card** shape (see [BrassCard.filledTrack]) - the same
 * recessed card the progress bar uses, filled with brass up to the value. A slim grip handle
 * ([BrassCard.grip], the scrollbar's handle) rides the fill edge, and the value is drawn centred over
 * the track, the same readout [BrassRangeSlider] and [BrassNumberInput] use. No Bedrock/OreUI
 * bevelling: it reads as a card, matching the toggle and the progress bar.
 * Drawn directly rather than through the keycap base - the control should read as sunk into the panel,
 * not raised off it - so it sets `chrome = BrassChrome.NONE`.
 */
class BrassSlider(
    private val min: Float,
    private val max: Float,
    initial: Float,
    private val step: Float = 0f,
    private val format: (Float) -> String = { String.format("%.2f", it) },
    private val onChange: (Float) -> Unit = {},
) : BrassWidget(BrassAccent.DEFAULT), BrassValue<Float>, BrassFocusable {

    val range = BrassRange(min, max, step)

    private val holder = BrassValueHolder(range.snap(initial))

    override var value: Float
        get() = holder.value
        set(v) { holder.value = snap(v) }

    override fun setSilently(value: Float) = holder.setSilently(snap(value))
    override fun onChange(listener: (Float) -> Unit) = holder.onChange(listener)
    override fun bind(state: BrassState<Float>) = holder.bind(this, state)

    private fun snap(v: Float): Float = range.snap(v)

    private var scrubbing = false
    private var scrubStartValue = 0f
    private var scrubStartX = 0f

    private val knob = BrassEased(0f, speed = KNOB_SPEED)
    private var knobPrimed = false
    private val glowValue = BrassEased(0f, speed = GLOW_SPEED)

    init {
        // The slider paints its own card through BrassCard; the keycap base must draw nothing behind it.
        chrome = BrassChrome.NONE
        // Deliberately NOT `clickable`: that flag drives the keycap's press-sink animation, and a
        // slider dropping a pixel when you grab it fights the handle you are trying to aim. The
        // pointer cursor is requested directly instead, in drawContent.
        onMouseClick { e ->
            if (active && e.mouseButton == 0) {
                scrubbing = true
                scrubStartX = e.relativeX
                setFromLocal(e.relativeX)
                // Captured after the click so Shift's fine mode continues from the clicked value.
                scrubStartValue = value
            }
        }
        // Elementa delivers drag events to every component in the tree, not just the one under the
        // cursor - without the `scrubbing` gate, dragging anywhere on screen would move this slider.
        onMouseDrag { mx, _, btn ->
            if (active && btn == 0 && scrubbing) {
                if (UKeyboard.isShiftKeyDown()) {
                    // Shift turns the drag into a fine adjustment relative to where the press began -
                    // one tenth of the pointer travel, so a long slider can still be aimed precisely.
                    val avail = (bw - 2).coerceAtLeast(1)
                    value = range.snap(scrubStartValue + (mx - scrubStartX) / avail * (max - min) * 0.1f)
                } else {
                    setFromLocal(mx)
                }
            }
        }
        onMouseRelease { scrubbing = false }
        holder.onChange(onChange)
    }

    override fun onKeyPressed(keyCode: Int): Boolean {
        return when (keyCode) {
            org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> { value = range.nudge(value, -1); true }
            org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> { value = range.nudge(value, 1); true }
            org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> { value = min; true }
            org.lwjgl.glfw.GLFW.GLFW_KEY_END -> { value = max; true }
            else -> false
        }
    }

    private fun fraction(): Float = range.fraction(value)

    /**
     * Handle width - a slim grip. Derived from the control's **height**, not its width (a fraction of a
     * long track grows absurdly wide), and capped so it never eats the track.
     */
    private fun handleWidth(w: Int, h: Int): Int =
        (h * 0.42f).roundToInt().coerceIn(5, (w / 4).coerceAtLeast(5))

    private fun setFromLocal(localX: Float) {
        // the fill spans the card interior (inset 1 px each side), so the value maps across that span
        val avail = (bw - 2).coerceAtLeast(1)
        value = range.valueAt((localX - 1f) / avail)
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val hot = hoveredState || scrubbing
        if (hot && active) BrassCursor.request(BrassCursor.Kind.HAND)

        val target = fraction()
        // The fill eases toward its value so a programmatic jump animates rather than snapping - but
        // NOT while the user is dragging it. Easing under the cursor is exactly the "handle lags a few
        // frames behind the mouse" complaint: during a scrub the handle must be *at* the cursor, and
        // the weighting is only wanted for changes the user did not make by hand.
        if (!knobPrimed) { knobPrimed = true; knob.snapTo(target) }
        knob.target = target
        if (scrubbing) knob.snapTo(target) else knob.advance()
        glowValue.target = if (hot) 1f else 0f
        val glow = glowValue.advance()
        val frac = knob.value.coerceIn(0f, 1f)

        val x1 = x.toFloat(); val y1 = y.toFloat()
        val x2 = (x + w).toFloat(); val y2 = (y + h).toFloat()

        // the shared BrassLoading-style groove, filled up to the value
        val body = if (hot) Colors.BRASS_500 else Colors.BRASS_600
        val litEdge = if (hot) Colors.BRASS_300 else Colors.BRASS_400
        BrassCard.filledTrack(m, x1, y1, x2, y2, frac, body, litEdge)

        // Slim grip handle at the fill edge, standing 1 px proud of the card at the top and bottom so
        // it reads as a separate part riding the track rather than a lighter patch inside it.
        val ix1 = x1 + 1f; val ix2 = x2 - 1f
        val gw = handleWidth(w, h).toFloat()
        val cx = ix1 + (ix2 - ix1) * frac
        val gx1 = (cx - gw / 2f).coerceIn(ix1, (ix2 - gw).coerceAtLeast(ix1))
        BrassCard.grip(m, gx1, y1 - 1f, gx1 + gw, y2 + 1f, glow)

        // Centred and unbacked, exactly as BrassNumberInput draws its number: the whole family reads
        // its value from the same place, in the same way. This used to be right-aligned in a dark
        // chip on the theory that a centred value would be unreadable under the handle for half the
        // range - but it is drawn *after* the grip and carries a shadow, so it stays legible over it,
        // and a chip on the slider that the number input did not have was a bigger difference than
        // the one it was avoiding.
        val label = BrassFont.fit(this, format(value), (w - PAD * 2f))
        if (label.isNotEmpty()) {
            BrassFont.draw(
                m, this, label,
                x + (w - BrassFont.width(this, label)) / 2f,
                y + (h - BrassFont.LINE) / 2f,
                if (scrubbing) Colors.UI_ACCENT_BRIGHT else textColor,
                true,
            )
        }
        // The range is only noise on a track nobody is looking at; while the slider is hot (hovered
        // or being scrubbed) the min and max appear at the track ends so the player can see the
        // bounds the value is moving between.
        if (hot && active && w > 140f) {
            val lo = BrassFont.fit(this, format(min), w / 2f - 8f)
            val hi = BrassFont.fit(this, format(max), w / 2f - 8f)
            BrassFont.draw(m, this, lo, x + 3f, y + (h - BrassFont.LINE) / 2f, Colors.UI_TEXT_DARK, true)
            BrassFont.draw(m, this, hi, x + w - 3f - BrassFont.width(this, hi), y + (h - BrassFont.LINE) / 2f, Colors.UI_TEXT_DARK, true)
        }
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("slider", "Slider", 190f, 16f) {
            BrassSlider(0f, 100f, 24f, format = { "${it.toInt()}%" })
        }

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val KNOB_SPEED = 18f
        private const val GLOW_SPEED = 12f
        private const val PAD = 3f
    }
}
