package net.swzo.brass.ui.kit.base



/**
 * The handful of numbers that describe how the toolkit's chrome sits in space.
 * ### Why this exists
 * `EDGE` was declared six times - in `BrassBounds`, `BrassResize`, `BrassWindow`, `BrassPopup`,
 * `BrassContextMenu` and `BrassTooltip` - for the same concept, at **two different values** (2 and
 * 4). `KEEP_VISIBLE` and `DOUBLE_CLICK_MS` were each declared three times. Nothing enforced that a
 * window and the popup floating over it agreed about how close to the screen edge either may sit, so
 * they didn't.
 * These are `const`, so referencing them costs exactly what the literal did.
 */
object BrassMetrics {

    const val FRAME_EDGE = 2f

    const val FLOATING_EDGE = 4f

    /**
     * How much of a dragged frame must stay on screen.
     * A frame dragged fully past an edge can never be dragged back, so every drag clamp keeps at
     * least this much of the title bar reachable.
     */
    const val KEEP_VISIBLE = 80f

    const val DOUBLE_CLICK_MS = 300L

    const val CARD_GAP = 4f

    const val CURSOR_GAP = 10f
}
