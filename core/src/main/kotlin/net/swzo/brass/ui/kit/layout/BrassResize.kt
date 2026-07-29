package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.*
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassMetrics
import net.swzo.brass.ui.kit.input.BrassSlider
import net.swzo.brass.ui.kit.platform.BrassCursor
import net.swzo.brass.ui.kit.surface.BrassPopup
import net.swzo.brass.ui.kit.surface.BrassWindow

/**
 * Makes a frame resizable by dragging its edges and corners, as eight invisible grips laid over the
 * border band. Used by both [BrassWindow] and [BrassPopup] - the drag maths is fiddly enough that having two
 * copies of it would guarantee two different sets of bugs.
 *
 * ```kotlin
 * BrassResize.attach(this, minW = 220f, minH = 140f)
 * ```
 *
 * ### Why absolute coordinates
 *
 * Elementa reports drag coordinates *relative to the component being dragged* - but a resize grip
 * moves as the frame resizes, so a naive `relativeX` delta feeds its own movement back in and the
 * frame accelerates away from the cursor. Every grip therefore converts straight back to absolute
 * (`grip.getLeft() + mx`) and works from the bounds captured at mouse-down, which is stable no matter
 * how far the grip has travelled since.
 *
 * Drags are also gated on a press that landed on the grip itself, because Elementa broadcasts drag
 * events to the entire tree (the same gate [BrassSlider] and [BrassPopup] need).
 */
class BrassResize private constructor(
    private val target: UIComponent,
    private val minW: Float,
    private val minH: Float,
    private val onResize: (() -> Unit)?,
) {

    /** One edge or corner. The four booleans say which sides this grip moves. */
    private inner class Grip(
        val left: Boolean,
        val right: Boolean,
        val top: Boolean,
        val bottom: Boolean,
    ) : UIContainer() {

        private var active = false
        private var hovering = false
        private var startL = 0f
        private var startT = 0f
        private var startW = 0f
        private var startH = 0f
        private var startMX = 0f
        private var startMY = 0f

        init {
            // While hovered, ask for the matching resize cursor. Diagonal corners get the proper
            // NWSE/NESW arrows, so the handle announces itself before you press.
            onMouseEnter { hovering = true }
            onMouseLeave { hovering = false }

            onMouseClick { e ->
                if (e.mouseButton != 0) return@onMouseClick
                startL = target.getLeft(); startT = target.getTop()
                startW = target.getWidth(); startH = target.getHeight()
                startMX = getLeft() + e.relativeX
                startMY = getTop() + e.relativeY
                active = true
            }
            onMouseRelease { active = false }
            onMouseDrag { mx, my, btn ->
                if (!active || btn != 0) return@onMouseDrag
                resize(getLeft() + mx - startMX, getTop() + my - startMY)
            }
        }

        override fun draw(matrixStack: UMatrixStack) {
            // no beforeDraw() here - UIContainer.draw already calls it (see BrassFlow for the note)
            tickCursor()
            super.draw(matrixStack)
        }

        /** Ask for the cursor that matches what this grip does, while hovered or dragging. */
        private fun tickCursor() {
            if (!hovering && !active) return
            BrassCursor.request(
                when {
                    (left && top) || (right && bottom) -> BrassCursor.Kind.RESIZE_NWSE
                    (right && top) || (left && bottom) -> BrassCursor.Kind.RESIZE_NESW
                    left || right -> BrassCursor.Kind.RESIZE_H
                    else -> BrassCursor.Kind.RESIZE_V
                },
            )
        }

        private fun resize(dx: Float, dy: Float) {
            val parent = target.parent
            val pl = parent.getLeft()
            val pt = parent.getTop()
            val pr = parent.getRight()
            val pb = parent.getBottom()

            var l = startL
            var t = startT
            var r = startL + startW
            var b = startT + startH

            if (left) l = startL + dx
            if (right) r = startL + startW + dx
            if (top) t = startT + dy
            if (bottom) b = startT + startH + dy

            // magnetic edges: within SNAP px of the parent's inner margin, latch onto it, so a frame
            // dragged out to fill the screen lands flush instead of a pixel or two short
            if (left && kotlin.math.abs(l - (pl + EDGE)) < SNAP) l = pl + EDGE
            if (right && kotlin.math.abs(r - (pr - EDGE)) < SNAP) r = pr - EDGE
            if (top && kotlin.math.abs(t - (pt + EDGE)) < SNAP) t = pt + EDGE
            if (bottom && kotlin.math.abs(b - (pb - EDGE)) < SNAP) b = pb - EDGE

            // stay inside the parent
            l = l.coerceAtLeast(pl + EDGE)
            t = t.coerceAtLeast(pt + EDGE)
            r = r.coerceAtMost(pr - EDGE)
            b = b.coerceAtMost(pb - EDGE)

            // enforce the minimum by pushing back whichever edge is being dragged, so the opposite
            // edge stays anchored under the cursor's expectation
            if (r - l < minW) { if (left) l = r - minW else r = l + minW }
            if (b - t < minH) { if (top) t = b - minH else b = t + minH }

            // Store the result as *fractions of the parent* rather than fixed pixels. A frame sized
            // in pixels keeps those pixels when the game window is resized, so it ends up larger than
            // the screen or entirely off it. Fractions rescale with the parent, and the min-size and
            // on-screen clamps are re-applied every frame by the constraints themselves.
            BrassBounds.applyFractional(target, l, t, r, b, minW, minH)
            onResize?.invoke()
        }
    }

    private fun build() {
        fun grip(
            l: Boolean, r: Boolean, t: Boolean, b: Boolean,
            x: () -> Float, y: () -> Float, w: () -> Float, h: () -> Float,
        ) {
            Grip(l, r, t, b).constrain {
                this.x = basicXConstraint { x() }
                this.y = basicYConstraint { y() }
                width = basicWidthConstraint { w() }
                height = basicHeightConstraint { h() }
            } childOf target
        }

        val tl = { target.getLeft() }
        val tt = { target.getTop() }
        val tr = { target.getRight() }
        val tb = { target.getBottom() }
        // edges stop short of the corners so the corner grips win where they overlap
        val innerW = { (target.getWidth() - CORNER * 2).coerceAtLeast(0f) }
        val innerH = { (target.getHeight() - CORNER * 2).coerceAtLeast(0f) }

        // Every grip extends OUTREACH pixels beyond the frame, so the edge can be grabbed from just
        // outside it. Aiming at a 4-px band is fussy; the forgiving hit area is what makes dragging
        // a window edge feel normal rather than fiddly.
        val o = OUTREACH
        grip(true, false, false, false, { tl() - o }, { tt() + CORNER }, { BAND + o }, innerH)
        grip(false, true, false, false, { tr() - BAND }, { tt() + CORNER }, { BAND + o }, innerH)
        grip(false, false, true, false, { tl() + CORNER }, { tt() - o }, innerW, { BAND + o })
        grip(false, false, false, true, { tl() + CORNER }, { tb() - BAND }, innerW, { BAND + o })

        grip(true, false, true, false, { tl() - o }, { tt() - o }, { CORNER + o }, { CORNER + o })
        grip(false, true, true, false, { tr() - CORNER }, { tt() - o }, { CORNER + o }, { CORNER + o })
        grip(true, false, false, true, { tl() - o }, { tb() - CORNER }, { CORNER + o }, { CORNER + o })
        grip(false, true, false, true, { tr() - CORNER }, { tb() - CORNER }, { CORNER + o }, { CORNER + o })
    }

    companion object {
        /** Width of the draggable band along each edge. */
        const val BAND = 4f
        /** Size of the corner grips, which take priority over the edges. */
        const val CORNER = 10f
        /** Margin kept between a frame and its parent's edge. */
        const val EDGE = BrassMetrics.FRAME_EDGE
        /** Distance at which an edge latches onto the parent's margin. */
        const val SNAP = 6f
        /** How far outside the frame a grip still responds. */
        const val OUTREACH = 3f

        /**
         * Lay resize grips over [target]'s border. [onResize] fires after each change, for frames that
         * need to react (clearing a "maximized" flag, say).
         */
        fun attach(
            target: UIComponent,
            minW: Float = 160f,
            minH: Float = 120f,
            onResize: (() -> Unit)? = null,
        ): BrassResize = BrassResize(target, minW, minH, onResize).also { it.build() }

        // No corner mark is drawn. It went through a three-step diagonal hatch and then a single
        // square, and neither earned its place: the cursor changing to the resize arrow as you
        // approach the edge is what actually tells you the frame is resizable, and it does so on every
        // edge rather than only the one corner. A permanent dot in the corner of every window and
        // modal was decoration that had to be explained.
    }
}
