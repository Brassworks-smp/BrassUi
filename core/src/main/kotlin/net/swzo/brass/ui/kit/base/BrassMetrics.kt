package net.swzo.brass.ui.kit.base

import net.swzo.brass.ui.kit.base.BrassMetrics.FRAME_EDGE


/**
 * The handful of numbers that describe how the toolkit's chrome sits in space.
 *
 * ### Why this exists
 *
 * `EDGE` was declared six times - in `BrassBounds`, `BrassResize`, `BrassWindow`, `BrassPopup`,
 * `BrassContextMenu` and `BrassTooltip` - for the same concept, at **two different values** (2 and
 * 4). `KEEP_VISIBLE` and `DOUBLE_CLICK_MS` were each declared three times. Nothing enforced that a
 * window and the popup floating over it agreed about how close to the screen edge either may sit, so
 * they didn't.
 *
 * These are `const`, so referencing them costs exactly what the literal did.
 */
object BrassMetrics {

    /**
     * Margin kept between a **frame** and its parent's edge - windows, and anything else that fills
     * the screen. Tight, because a window is meant to look seated against the edge.
     */
    const val FRAME_EDGE = 2f

    /**
     * Margin kept between a **floating layer** and the screen edge - popups, context menus,
     * tooltips. Wider than [FRAME_EDGE] on purpose: these cast a drop shadow, and a shadow clipped
     * by the screen edge reads as the layer being cut off rather than as lifted.
     */
    const val FLOATING_EDGE = 4f

    /**
     * How much of a dragged frame must stay on screen.
     *
     * A frame dragged fully past an edge can never be dragged back, so every drag clamp keeps at
     * least this much of the title bar reachable.
     */
    const val KEEP_VISIBLE = 80f

    /** Window within which two presses count as a double-click. */
    const val DOUBLE_CLICK_MS = 300L

    /** Clearance kept between two floating cards that would otherwise land on each other. */
    const val CARD_GAP = 4f

    /** Gap between the cursor and a card placed relative to it. */
    const val CURSOR_GAP = 10f
}
