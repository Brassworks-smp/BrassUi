package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.dsl.*
import net.swzo.brass.ui.kit.base.BrassMetrics
import net.swzo.brass.ui.kit.layout.BrassBounds.applyFractional

/**
 * Frame geometry that survives the game window being resized.
 *
 * A window dragged to a size and then written back as pixel constraints keeps those pixels forever.
 * Shrink the Minecraft window afterwards and the frame is suddenly wider than the screen, with its
 * title bar - and therefore its controls - off the edge and unreachable.
 *
 * [applyFractional] instead records the frame's position and size as **fractions of its parent** and
 * installs constraints that re-derive pixels every time they are evaluated. Resizing the game window
 * then rescales the frame proportionally, and because the clamps live *inside* the constraints they
 * are re-applied continuously rather than only at the moment of the drag:
 *
 * - never smaller than `minW`/`minH`, so a frame cannot be squeezed into an unusable sliver;
 * - never wider or taller than the parent, less a margin;
 * - never positioned so far right or down that it leaves the parent.
 */
object BrassBounds {

    /** Margin kept between a frame and the parent's edge. */
    const val EDGE = BrassMetrics.FRAME_EDGE

    /**
     * Constrain [target] to the rectangle `[left,top]..[right,bottom]` (absolute coordinates),
     * expressed as fractions of its parent so the frame tracks screen resizes.
     */
    fun applyFractional(
        target: UIComponent,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        minW: Float,
        minH: Float,
    ) {
        val parent = target.parent
        val pw = parent.getWidth().coerceAtLeast(1f)
        val ph = parent.getHeight().coerceAtLeast(1f)

        val fx = ((left - parent.getLeft()) / pw).coerceIn(0f, 1f)
        val fy = ((top - parent.getTop()) / ph).coerceIn(0f, 1f)
        val fw = ((right - left) / pw).coerceIn(0f, 1f)
        val fh = ((bottom - top) / ph).coerceIn(0f, 1f)

        apply(target, fx, fy, fw, fh, minW, minH)
    }

    /** As [applyFractional], but taking the fractions directly. */
    fun apply(
        target: UIComponent,
        fx: Float,
        fy: Float,
        fw: Float,
        fh: Float,
        minW: Float,
        minH: Float,
    ) {
        target.constrain {
            width = basicWidthConstraint { c ->
                val avail = (c.parent.getWidth() - EDGE * 2).coerceAtLeast(1f)
                (c.parent.getWidth() * fw).coerceIn(minW.coerceAtMost(avail), avail)
            }
            height = basicHeightConstraint { c ->
                val avail = (c.parent.getHeight() - EDGE * 2).coerceAtLeast(1f)
                (c.parent.getHeight() * fh).coerceIn(minH.coerceAtMost(avail), avail)
            }
            // Position is clamped against the *current* width, so a frame pushed against the right
            // edge by a shrinking screen slides back into view instead of hanging off it.
            x = basicXConstraint { c ->
                val p = c.parent
                val maxX = (p.getWidth() - c.getWidth() - EDGE).coerceAtLeast(EDGE)
                p.getLeft() + (p.getWidth() * fx).coerceIn(EDGE, maxX)
            }
            y = basicYConstraint { c ->
                val p = c.parent
                val maxY = (p.getHeight() - c.getHeight() - EDGE).coerceAtLeast(EDGE)
                p.getTop() + (p.getHeight() * fy).coerceIn(EDGE, maxY)
            }
        }
    }
}
