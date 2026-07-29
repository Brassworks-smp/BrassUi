package net.swzo.brass.ui.kit.base



/**
 * When a widget appearing for the first time is allowed to play its entrance.
 *
 * Without this, "first time drawn" was the trigger - and a widget scrolled into view is being drawn for
 * the first time, so a list popped its rows in one by one as you scrolled past them, which reads as the
 * UI stuttering rather than as anything arriving. The entrance belongs to a frame *opening*, not to a
 * component's first paint.
 *
 * A frame publishes its phase around its own subtree's draw (see [BrassFrameAnim.push]), so a widget
 * reads it at exactly the moment it first paints and never has to know which frame it is in.
 */
object BrassEntrance {

    enum class Phase {
        /** Nothing is opening. A widget first drawn now is already in place - no animation. */
        IDLE,

        /** A frame is playing its open animation. Contents hold, invisible, until it lands. */
        OPENING,

        /** The frame has just finished opening. This is the window in which contents cascade in. */
        SETTLING,
    }

    var phase: Phase = Phase.IDLE
}
