package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.HeightConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.layout.BrassBounds
import net.swzo.brass.ui.kit.layout.BrassResize
import net.swzo.brass.ui.kit.paint.BrassCard
import kotlin.math.roundToInt

/**
 * Everything a window-like frame does, in one place: chrome, drag, collapse, maximize, resize and the
 * open/close animation.
 *
 * ### Why this exists
 *
 * [BrassWindow] and [BrassPopup] were two independent implementations of the same object, and they
 * had drifted in ways the user could see:
 *
 * - the window animated a maximize by easing its bounds; the popup teleported;
 * - the window captured and restored its height **constraint** across a collapse, so a
 *   percentage-sized frame still tracked the screen afterwards, while the popup restored pixels and
 *   was stuck at that size from then on;
 * - the window cancelled an in-flight maximize when you started dragging; the popup let the
 *   animation keep reapplying its own bounds underneath the drag;
 * - `EDGE` was 2 in one and 4 in the other, for the same clearance.
 *
 * Each of those was fixed once, in whichever file someone happened to be in. Three of the surviving
 * comments in the two files describe the same bug being found twice.
 *
 * Subclasses supply their chrome (a title bar, control keys, a scrim) and their body; everything
 * above is inherited and therefore identical.
 */
abstract class BrassFrameBase(
    /** Title bar height in pixels, or 0 for a frame with no header. */
    protected val titleBarH: Int,
    /** Smallest the frame may be dragged or squeezed down to. */
    protected val minW: Float,
    protected val minH: Float,
    /** Lay resize grips over the border. */
    private val resizable: Boolean = true,
    /** Allow the frame to be rolled up to its title bar. */
    protected val collapsible: Boolean = true,
    /** Inset of [content] from the frame's edges. */
    private val contentPad: Float = 0f,
) : UIComponent(), BrassFrame {

    /** The frame's interior, below the header. */
    val content: UIContainer

    protected val anim = BrassFrameAnim()

    // ---- drag ------------------------------------------------------------------------------------

    private var grabDX = 0f
    private var grabDY = 0f

    /** True only between a press on the title bar and its release - see [installDrag]. */
    private var dragging = false
    private var lastTitleClick = 0L

    /**
     * The chrome that must not start a drag - the control keys. Set by a subclass once it has built
     * them.
     */
    protected var controlsBar: UIComponent? = null

    /** The component drags are clamped inside. The parent by default; a popup uses its screen root. */
    protected open val dragBounds: UIComponent? get() = if (hasParent) parent else null

    init {
        content = UIContainer().constrain {
            x = contentPad.pixels()
            y = (titleBarH + contentPad).pixels()
            width = 100.percent() - (contentPad * 2).pixels()
            height = openContentHeight()
        } childOf this

        // Clip the body to its own bounds. Collapsing sets this height to 0, and Elementa does not clip
        // by default, so every child kept drawing at its own coordinates - a rolled-up frame still
        // showed its rail captions, footer text and scrollbar floating under the title bar.
        content.enableEffect(ScissorEffect())

        // Grips go on last so they sit above the content in hit-testing order. They only cover the
        // border band, which no widget occupies.
        if (resizable) {
            BrassResize.attach(this, minW, minH) {
                clearMaximize()
                if (collapsed) uncollapse()
            }
        }
    }

    /** The body's height when the frame is open - restored after every collapse and maximize. */
    private fun openContentHeight(): HeightConstraint =
        100.percent() - (titleBarH + contentPad * 2).pixels()

    /**
     * Make [bar] the frame's drag handle: press and move to reposition, double-click to
     * maximize/restore.
     *
     * Elementa broadcasts drag events to the whole tree, so the move is gated on a press that
     * actually landed on this bar - the same gate `BrassSlider` and `BrassTable` need.
     */
    protected fun installDrag(bar: UIComponent) {
        bar.onMouseClick { e ->
            if (e.mouseButton != 0) return@onMouseClick
            // Elementa BUBBLES clicks: a press on a control key fires its own handler and then this
            // one too. Without this guard, two presses of the maximize key inside the double-click
            // window read here as a double-click on the bar and fired toggleMaximize a SECOND time in
            // the same gesture - which re-captured the restore bounds from the frame that had just
            // been maximized, so from then on "restore" restored the maximized size and the frame
            // appeared not to respond at all. It also armed a drag from a button press.
            controlsBar?.let { if (BrassTree.isDescendantOf(e.target, it)) return@onMouseClick }
            grabDX = e.relativeX
            grabDY = e.relativeY
            dragging = true
            val now = System.currentTimeMillis()
            if (now - lastTitleClick < BrassMetrics.DOUBLE_CLICK_MS) onTitleDoubleClick()
            lastTitleClick = now
        }
        bar.onMouseRelease { dragging = false }
        bar.onMouseDrag { mx, my, btn ->
            if (btn != 0 || !dragging) return@onMouseDrag
            // NOTE: the receiver inside onMouseDrag is the *bar*, so every call here must be qualified
            // - otherwise the bar detaches and chases the cursor while the frame stays put.
            val frame = this@BrassFrameBase
            val bounds = frame.dragBounds ?: return@onMouseDrag
            frame.moveTo(
                frame.getLeft() + (mx - grabDX),
                frame.getTop() + (my - grabDY),
                bounds,
            )
        }
    }

    /** What a double-click on the title bar does. Maximize by default. */
    protected open fun onTitleDoubleClick() = toggleMaximize()

    /**
     * Reposition the frame, keeping a grabbable strip on screen in every direction - a frame dragged
     * fully past an edge could never be dragged back.
     */
    private fun moveTo(x: Float, y: Float, bounds: UIComponent) {
        val e = BrassMetrics.FRAME_EDGE
        val keep = BrassMetrics.KEEP_VISIBLE
        val nx = x.coerceIn(e - getWidth() + keep, (bounds.getWidth() - keep).coerceAtLeast(e))
        val ny = y.coerceIn(e, (bounds.getHeight() - titleBarH.coerceAtLeast(1)).coerceAtLeast(e))
        // Dragging a maximized frame un-maximizes it, as every desktop does. This also has to cancel
        // any maximize animation still in flight: leaving it running meant the slide kept reapplying
        // its own bounds every frame while the drag set x/y, and the control key's lit state desynced
        // from what the frame was actually doing.
        clearMaximize()
        constrain { this.x = nx.pixels(); this.y = ny.pixels() }
    }

    /**
     * Widen the hit area by the resize grips' [BrassResize.OUTREACH].
     *
     * Elementa dispatches a click by walking down from the root, and only descends into a component
     * whose own `isPointInside` passes. The grips deliberately extend a few pixels past the frame so
     * an edge can be grabbed from just outside it - but the frame itself rejected those points, so
     * the grips were never reached and the drag did nothing, even though the cursor had already
     * changed to the resize arrow. The cursor was right and the hit test was wrong.
     */
    override fun isPointInside(x: Float, y: Float): Boolean {
        val o = BrassResize.OUTREACH
        return x > getLeft() - o && x < getRight() + o && y > getTop() - o && y < getBottom() + o
    }

    // ---- collapse --------------------------------------------------------------------------------

    protected var collapsed = false
        private set

    private var collapsedFrom = 0f

    /** 0 = open, 1 = fully rolled up. */
    private val collapseRoll = BrassEased(0f, speed = COLLAPSE_SPEED)

    /**
     * The height **constraint** the frame had before it was rolled up.
     *
     * Captured as a constraint, not a number, and handed back once the roll-down finishes. A frame
     * sized by a percentage of the screen used to come back as fixed pixels after its first collapse
     * and stopped responding to resizes from then on. `BrassWindow` fixed that; `BrassPopup` never
     * did, which is precisely the kind of divergence this base class exists to prevent.
     */
    private var openHeight: HeightConstraint? = null

    override fun toggleCollapse() {
        if (!collapsible) return
        if (!collapsed) {
            collapsed = true
            // Both of these are captured only when starting from a genuinely open frame. Toggling
            // again mid-animation would otherwise save the *animated pixel* height as the "open" one
            // and the frame could never be restored to its real size.
            if (openHeight == null) {
                collapsedFrom = getHeight()
                openHeight = constraints.height
            }
            content.constrain { height = 0.pixels() }
            collapseRoll.target = 1f
        } else {
            collapsed = false
            content.constrain { height = openContentHeight() }
            collapseRoll.target = 0f
        }
    }

    /** Finish a roll open immediately - for a resize or maximize taking over the frame's bounds. */
    protected fun uncollapse() {
        collapsed = false
        collapseRoll.snapTo(0f)
        openHeight = null
        content.constrain { height = openContentHeight() }
    }

    private fun advanceCollapse() {
        collapseRoll.advance()
        if (!collapsed && collapseRoll.value == 0f) {
            // fully open: hand the original constraint back so the frame tracks the screen again
            openHeight?.let { h -> constraints.height = h; openHeight = null }
            return
        }
        val shut = (titleBarH + 2).toFloat()
        val open = collapsedFrom.coerceAtLeast(shut)
        constrain { height = (open + (shut - open) * collapseRoll.value).pixels() }
    }

    // ---- maximize --------------------------------------------------------------------------------

    protected var maximized = false
        private set

    private var savedBounds: FloatArray? = null
    private var fromBounds: FloatArray? = null
    private var toBounds: FloatArray? = null
    private val boundsSlide = BrassEased(1f, speed = MAXIMIZE_SPEED)

    /** Bounds as fractions of the parent, so a maximized frame stays maximized across a resize. */
    private fun snapshot(): FloatArray {
        val p = parent
        val pw = p.getWidth().coerceAtLeast(1f)
        val ph = p.getHeight().coerceAtLeast(1f)
        return floatArrayOf(
            (getLeft() - p.getLeft()) / pw, (getTop() - p.getTop()) / ph,
            getWidth() / pw, getHeight() / ph,
        )
    }

    private fun slideTo(to: FloatArray) {
        fromBounds = snapshot()
        toBounds = to
        boundsSlide.snapTo(0f)
        boundsSlide.target = 1f
    }

    private fun advanceBounds() {
        boundsSlide.advance()
        val from = fromBounds ?: return
        val to = toBounds ?: return
        val t = boundsSlide.value
        BrassBounds.apply(
            this,
            from[0] + (to[0] - from[0]) * t,
            from[1] + (to[1] - from[1]) * t,
            from[2] + (to[2] - from[2]) * t,
            from[3] + (to[3] - from[3]) * t,
            minW, minH,
        )
        if (boundsSlide.settled) { fromBounds = null; toBounds = null }
    }

    /**
     * Fill the parent, or restore the pre-maximize frame - **animated**, by easing the bounds as
     * fractions rather than swapping them in one frame. Easing fractions rather than pixels means the
     * animation survives a screen resize halfway through and lands exactly where the un-animated
     * version would have.
     */
    override fun toggleMaximize() {
        // Any roll-up is finished off first, and without restoring the old constraint: maximize is
        // about to drive its own bounds, and a pending restore would overwrite them a few frames later.
        uncollapse()

        if (!maximized) {
            // Only capture a restore target when the frame is genuinely at its own size. Capturing
            // while a previous transition is still running would snapshot a frame partway to (or fully
            // at) the maximized bounds and make restore a no-op.
            if (toBounds == null) savedBounds = snapshot()
            maximized = true
            slideTo(FULL)
        } else {
            maximized = false
            savedBounds?.let { slideTo(it) }
        }
        onMaximizeChanged(maximized)
    }

    /** Told whenever the maximized flag flips, so a subclass can light its control key. */
    protected open fun onMaximizeChanged(maximized: Boolean) {}

    /**
     * Cancel any in-flight maximize/restore and drop the flag - for a drag or a manual resize, which
     * take over the frame's bounds and must not be fought by an animation running somewhere else.
     */
    protected fun clearMaximize() {
        fromBounds = null
        toBounds = null
        boundsSlide.snapTo(1f)
        if (maximized) {
            maximized = false
            onMaximizeChanged(false)
        }
    }

    // ---- draw ------------------------------------------------------------------------------------

    /**
     * Advance the frame's animations and paint its chrome, then the subtree.
     *
     * `final` on purpose: the push/pop pairing around `super.draw` restores two globals as well as
     * the matrix, and a subclass that overrode this and forgot the `finally` would strand the ambient
     * fade below 1 for the rest of the session. Subclasses paint through [drawChrome] instead.
     */
    final override fun draw(matrixStack: UMatrixStack) {
        beforeDraw(matrixStack)

        advanceCollapse()
        advanceBounds()
        beforeAnim()

        val a = anim.advance()
        if (anim.finished) { onClosed(); return }

        val x = getLeft().roundToInt().toFloat()
        val y = getTop().roundToInt().toFloat()
        val x2 = getRight().roundToInt().toFloat()
        val y2 = getBottom().roundToInt().toFloat()

        onAlpha(a)

        val pushed = anim.push(matrixStack, a, x, y, x2, y2)
        try {
            drawChrome(matrixStack, x, y, x2, y2, a)
            super.draw(matrixStack)
        } finally {
            anim.pop(matrixStack, pushed)
        }
    }

    /**
     * The frame's chrome: a card, and a header stacked on it sharing its top edge. Override to add
     * more; call through for the standard look.
     */
    protected open fun drawChrome(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        alpha: Float,
    ) {
        BrassCard.draw(m, x1, y1, x2, y2, shadow = true, alpha = alpha)
        if (titleBarH > 0) BrassCard.header(m, x1, y1, x2, titleBarH.toFloat(), alpha = alpha)
    }

    /** Run at the top of every frame, before the open/close animation advances. */
    protected open fun beforeAnim() {}

    /** The frame's current chrome alpha, each frame - for a sibling that must fade in step (a scrim). */
    protected open fun onAlpha(alpha: Float) {}

    /** The close animation has landed and the frame may be torn down. Fires exactly once. */
    protected abstract fun onClosed()

    override fun requestClose() {
        anim.beginClose()
    }

    protected companion object {
        /** How fast a frame rolls up and back down. */
        const val COLLAPSE_SPEED = 14f

        /** How fast a frame travels between its restored and maximized bounds. */
        const val MAXIMIZE_SPEED = 16f

        /** Parent-filling fractions - the maximize target. */
        private val FULL = floatArrayOf(0f, 0f, 1f, 1f)
    }
}
