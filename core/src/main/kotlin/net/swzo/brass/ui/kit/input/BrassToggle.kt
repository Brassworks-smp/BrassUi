package net.swzo.brass.ui.kit.input

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A toggle built from the same **filled-card** shape as the slider and progress bar (see
 * [BrassCard.filledTrack]): a recessed card that fills with brass as it turns on, with a grey grip knob
 * ([BrassCard.grip]) sliding from the left to the right of the track. Off is an empty card with the knob
 * at the left; on is a brass-filled card with the knob at the right. No Bedrock/OreUI bevelling.
 *
 * Rendered directly rather than through the keycap base: the switch should sit *into* the surface, not
 * raised off it. It still extends [BrassWidget] for the hover state and animation plumbing, but sets
 * [transparent] and [flat] so the base paints no keycap of its own.
 */
class BrassToggle(
    // named `initial`, not `toggled`: a constructor parameter shadows the same-named property
    // throughout the class body, so reads would silently see the start value (see BrassTextInput).
    initial: Boolean = false,
    private val onChange: (Boolean) -> Unit = {},
) : BrassWidget(BrassAccent.DEFAULT), BrassValue<Boolean>, BrassFocusable {

    private val holder = BrassValueHolder(initial)

    override var value: Boolean
        get() = holder.value
        set(v) { holder.value = v }

    override fun setSilently(value: Boolean) = holder.setSilently(value)
    override fun onChange(listener: (Boolean) -> Unit) = holder.onChange(listener)
    override fun bind(state: BrassState<Boolean>) = holder.bind(this, state)

    /** The switch's state. Alias of [value], kept because every call site already reads it. */
    val toggled: Boolean get() = holder.value

    /** 0 = fully off, 1 = fully on; eased, and drives both the knob slide and the fill wipe. */
    private val slideValue = BrassEased(if (initial) 1f else 0f, speed = SPEED)
    private val glowValue = BrassEased(0f, speed = GLOW_SPEED)

    init {
        clickable = true
        // The switch paints its own card through BrassCard; the keycap base must draw nothing behind
        // it. `transparent` alone still outlined the whole widget box, which put a stray rectangle
        // around the (narrower, centred) track.
        chrome = BrassChrome.NONE
        onMouseClick { e -> if (active && e.mouseButton == 0) toggle() }
        // The constructor callback is simply listener zero, so a caller can add more later.
        holder.onChange(onChange)
    }

    fun toggle() { value = !value }

    override fun proxyActivate() { if (active) toggle() }

    /** Set the switch, firing listeners. Alias of assigning [value]. */
    fun set(value: Boolean) { this.value = value }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        slideValue.target = if (toggled) 1f else 0f
        val slide = slideValue.advance()

        val hot = hoveredState && active
        glowValue.target = if (hot) 1f else 0f
        val glow = glowValue.advance()

        // Geometry is derived from the height, not from percentages of an arbitrary width: the track is
        // a fixed-proportion card and the knob is *square*, which stops the control looking stretched
        // when its box is wide. The whole thing is centred in the box it is given.
        val trackH = h.coerceAtLeast(8)
        val knob = (trackH - 2).coerceAtLeast(4)          // square knob, inside the card border
        val trackW = (knob * 2 + 6).coerceAtMost(w)       // a touch over two knobs wide
        val tx = x + (w - trackW) / 2f                    // centred rather than stretched
        val ty = y + (h - trackH) / 2f

        // the shared track card, the brass wiping across as it turns on. Hover jumps two ramp steps
        // brighter so a hovered switch reads as clearly brighter, not the same selected green.
        val body = if (hot) Colors.BRASS_400 else Colors.BRASS_600
        val litEdge = if (hot) Colors.BRASS_300 else Colors.BRASS_400
        BrassCard.filledTrack(m, tx, ty, tx + trackW, ty + trackH, slide, body, litEdge)

        // Grey grip knob sliding across the card, standing 1 px proud at the top and bottom like the
        // slider's. At the "on" end it travels one pixel further than the track interior allows, so it
        // sits slightly over the right edge - the switch reads as pushed fully home rather than as
        // having stopped just short of it. Scaled by `slide`, so the extra pixel eases in with the rest.
        // The knob overhangs the track at BOTH ends: one pixel past the right when on, two past the
        // left when off. Sitting flush inside the track at rest read as "not quite off"; the overhang
        // makes each end state look deliberately seated rather than merely stopped there.
        val ix1 = tx + 1f; val ix2 = tx + trackW - 1f
        val travel = (ix2 - ix1 - knob).coerceAtLeast(0f)
        val kx = ix1 + travel * slide + slide * KNOB_OVERSHOOT - (1f - slide) * KNOB_UNDERSHOOT + KNOB_NUDGE
        BrassCard.grip(m, kx, ty - 1f, kx + knob, ty + trackH + 1f, glow)
    }

    companion object : BrassDemoSource {

        /**
         * The handle sliding across, both ways.
         *
         * The travel is the widget. A still can only ever catch it parked at one end, where it is
         * indistinguishable from a two-state box, so the demo switches it on and back off with enough
         * of a beat between to read each resting state.
         */
        override fun demo() = BrassDemo("toggle", "Toggle", 30f, 16f) {
            BrassToggle(initial = false)
        }

        // ---- widget internals ------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val SPEED = 15f
        /** How fast the knob brightens on hover. */
        private const val GLOW_SPEED = 12f
        /** How far past the track interior the knob sits when fully on. */
        private const val KNOB_OVERSHOOT = 1f
        /** How far past the track's left edge the knob sits when fully off. */
        private const val KNOB_UNDERSHOOT = 2f
        /** A constant rightward nudge applied at both ends, to centre the knob in the track. */
        private const val KNOB_NUDGE = 1f
    }
}
