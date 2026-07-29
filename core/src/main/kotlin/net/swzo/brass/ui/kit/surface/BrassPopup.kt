package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassDismissable
import net.swzo.brass.ui.kit.base.BrassMetrics
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassSquareButton
import net.swzo.brass.ui.kit.layout.BrassForm
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTag

/**
 * A floating, **draggable** sub-window with a title bar and a scrolling form body. Shown above the
 * screen via [show]; [dismiss] closes it and fires [onClose].
 *
 * ### What lives where
 *
 * The frame itself - drag, collapse, maximize, resize, the open/close animation - is
 * [BrassFrameBase]. The form is [BrassForm], and the `add*` builders here simply forward to it, so a
 * form can equally be built outside a popup. What is left here is what is genuinely a *popup*: the
 * modal scrim, the z-order rules, and the show/dismiss lifecycle.
 *
 * ### Modal mode
 *
 * Pass `modal = true` (or use [showModal]) for a dialog: a dark scrim covers the whole screen, the
 * popup is centred on it, and clicks cannot reach anything behind. **A modal's chrome defaults off** -
 * no header, no close button, no resize, no collapse - because a dialog is answered by its own
 * buttons, not dismissed by furniture around the edge. Every one of those is an independent flag, so
 * a modal that does want a title bar and a close button just asks for them.
 *
 * `dismissOnEscape` stays on by default in both modes; turn it off for a dialog that must be answered.
 */
class BrassPopup(
    private val title: String,
    private val onClose: () -> Unit = {},
    /** Show over a dark scrim, centred, with chrome off by default. */
    val modal: Boolean = false,
    /** Show the title bar. Off by default for a modal. */
    private val showHeader: Boolean = !modal,
    /** Show the close button in the title bar. Off by default for a modal. */
    private val showCloseButton: Boolean = !modal,
    /** Allow dragging the edges to resize. Off by default for a modal. */
    resizable: Boolean = !modal,
    /** Allow double-clicking / minimising the title bar to roll the popup up. Off for a modal. */
    collapsible: Boolean = !modal,
    /** Whether Escape closes this popup. On by default in both modes. */
    val dismissOnEscape: Boolean = true,
    /**
     * Build the scrolling [BrassForm] the `add*` builders fill.
     *
     * Turn it off for a dialog that lays out its own content - a browser, a wizard step, anything that
     * is not a stack of labelled rows. [content] is then an empty container filling the frame below
     * the header, and the builder methods throw.
     */
    private val scrollingBody: Boolean = true,
) : BrassFrameBase(
    titleBarH = if (showHeader) 20 else 0,
    minW = MIN_W,
    minH = MIN_H,
    resizable = resizable,
    collapsible = collapsible,
    contentPad = PAD,
), BrassDismissable {

    private var root: UIComponent? = null

    /** The form filling [content], or null when the popup has a custom body. */
    private val form: BrassForm?

    override val dismissing: Boolean get() = anim.isClosing
    override val escapeDismissable: Boolean get() = dismissOnEscape

    /** The dark scrim behind a modal, if any - created and torn down with the popup. */
    private var scrim: Scrim? = null

    /** A popup is dragged within its screen root, which is also its parent. */
    override val dragBounds: UIComponent? get() = root ?: super.dragBounds

    init {
        // A headerless popup builds no title bar at all - no drag handle, no title, no controls. That
        // is deliberate: a modal is placed by the layout, not by the user, and a strip of invisible
        // drag target across its top would be a trap.
        if (showHeader) buildTitleBar()

        form = if (scrollingBody) {
            BrassForm().constrain { width = 100.percent(); height = 100.percent() } childOf content
        } else null

        // clicking anywhere on the popup raises it above its siblings
        onMouseClick { raisePending = true; raiseStamp = ++interactionSeq }
    }

    private fun buildTitleBar() {
        val titleBar = UIContainer().constrain {
            x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = titleBarH.pixels()
        } childOf this
        installDrag(titleBar)

        // No decorative chip in the corner, matching BrassWindow - see the note there.
        BrassLabel(title, Colors.UI_TEXT_HOVER).also { it.entranceEnabled = false }
            .constrain { x = 8.pixels(); y = CenterConstraint() } childOf titleBar

        // Glyphless and the same landscape shape as BrassWindow's control keys.
        if (showCloseButton) {
            controlsBar = BrassSquareButton(BrassIcons.NONE, BrassAccent.DANGER) { dismiss() }.also {
                it.entranceEnabled = false
                BrassTooltip.attach(it, "Close")
            }.constrain {
                x = 5.pixels(true); y = CenterConstraint()
                width = 18.pixels(); height = 10.pixels()
            } childOf titleBar
        }
    }

    /** A popup shades to its title bar on a double-click rather than maximizing. */
    override fun onTitleDoubleClick() = toggleCollapse()

    override fun requestClose() = dismiss()

    // ---- z-order ---------------------------------------------------------------------------------

    /**
     * Set when a click lands on this popup, and acted on at the start of the next draw. Re-parenting
     * during event dispatch would mutate the child list Elementa is iterating over.
     */
    private var raisePending = false

    /**
     * Interaction ordering, so a queued raise can tell whether it has been overtaken.
     *
     * A click anywhere on a popup queues a raise, and the click that opens a *new* popup necessarily
     * lands on the popup holding the button - so the opener would raise itself over the popup it just
     * opened, one frame later. That is why a colour picker opened from a settings popup appeared
     * behind it. Stamping both the click and the show from one counter lets the deferred raise notice
     * that something newer exists and stand down.
     */
    private var raiseStamp = 0L
    private var shownStamp = 0L

    /**
     * Raise this popup among its siblings on a click, **only as high as its rank allows**.
     *
     * Entirely through [BrassLayers.raise] - this method used to keep its own copy of the ordering
     * rule (stop below a scrim, then put the context menu back on top), and a private copy of a rule
     * is a rule that drifts: it knew about modals and menus but not about the toast column or the
     * command palette, so a click on any popup appended it above both. Now the popup only knows its
     * own rank; who it may and may not cover is [BrassLayers]' single decision.
     *
     * A modal raises its scrim first and itself second. Both are ranked [BrassLayers.Rank.MODAL], so
     * the two land together at the top of the modal band - scrim, then popup directly on top of it -
     * while transient chrome and toasts stay above.
     */
    private fun raiseWithin(r: UIComponent) {
        scrim?.let { BrassLayers.raise(r, it) }
        BrassLayers.raise(r, this)
    }

    override fun beforeAnim() {
        // deferred raise - safe here, outside event dispatch
        if (!raisePending) return
        raisePending = false
        root?.let { r ->
            // Stand down if a popup was opened after the click that queued this: that popup is what
            // the user is looking at, and raising over it would bury it.
            val overtaken = r.children.any { it is BrassPopup && it !== this && it.shownStamp > raiseStamp }
            if (!overtaken) raiseWithin(r)
        }
    }

    /** The scrim is a sibling, not a child, so it is told the frame's alpha rather than inheriting it. */
    override fun onAlpha(alpha: Float) {
        scrim?.alpha = alpha
    }

    // ---- form ------------------------------------------------------------------------------------

    private fun form(): BrassForm = requireNotNull(form) {
        "This popup was built with scrollingBody = false; add to `content` instead of using the form builders."
    }

    fun addField(label: String, control: UIComponent): BrassPopup = also { form().addField(label, control) }

    fun addRow(label: String, controlHeight: Int, vararg controls: UIComponent): BrassPopup =
        also { form().addRow(label, controlHeight, *controls) }

    fun addTags(label: String, vararg tags: BrassTag): BrassPopup = also { form().addTags(label, *tags) }

    fun addTextField(label: String, initial: String, placeholder: String = "", onChange: (String) -> Unit = {}): BrassPopup =
        also { form().addTextField(label, initial, placeholder, onChange) }

    fun addDropdown(label: String, options: List<Pair<String, String>>, initial: String, onSelect: (String) -> Unit = {}): BrassPopup =
        also { form().addDropdown(label, options, initial, onSelect) }

    fun addToggleRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit = {}): BrassPopup =
        also { form().addToggleRow(label, initial, onChange) }

    fun addSlider(
        label: String, min: Float, max: Float, initial: Float, step: Float = 0f,
        format: (Float) -> String = { String.format("%.2f", it) }, onChange: (Float) -> Unit = {},
    ): BrassPopup = also { form().addSlider(label, min, max, initial, step, format, onChange) }

    fun addButtons(vararg buttons: BrassButton): BrassPopup = also { form().addButtons(*buttons) }

    fun text(label: String): String? = form?.text(label)
    fun flag(label: String): Boolean? = form?.flag(label)
    fun number(label: String): Float? = form?.number(label)
    fun choice(label: String): String? = form?.choice(label)

    /** Every registered control's current value, keyed by caption. */
    fun values(): Map<String, Any> = form?.values() ?: emptyMap()

    /** Populate the form from saved values - see [BrassForm.setValues]. */
    fun setValues(values: Map<String, Any?>): BrassPopup = also { form?.setValues(values) }

    // ---- lifecycle -------------------------------------------------------------------------------

    /**
     * Float the popup above [screenRoot] at ([x],[y]) with size [w]x[h], **clamped to fit the
     * screen**: the size is capped to the available area first, then the position is pulled back
     * inside, so a popup can never open partly (or wholly) off-screen at small GUI scales.
     */
    fun show(screenRoot: UIComponent, x: Float, y: Float, w: Float, h: Float): BrassPopup {
        root = screenRoot
        val sw = screenRoot.getWidth()
        val sh = screenRoot.getHeight()
        val cw = w.coerceAtMost((sw - EDGE * 2).coerceAtLeast(MIN_W))
        val ch = h.coerceAtMost((sh - EDGE * 2).coerceAtLeast(MIN_H))
        val cx = x.coerceIn(EDGE, (sw - cw - EDGE).coerceAtLeast(EDGE))
        val cy = y.coerceIn(EDGE, (sh - ch - EDGE).coerceAtLeast(EDGE))
        constrain { this.x = cx.pixels(); this.y = cy.pixels(); width = cw.pixels(); height = ch.pixels() }
        this childOf screenRoot
        isFloating = true
        // childOf appended us above *everything*, including layers that outrank a popup - a toast
        // mid-slide, an open palette. Slot into the right band immediately rather than covering them
        // until the next reorder.
        raiseWithin(screenRoot)
        shownStamp = ++interactionSeq
        return this
    }

    /**
     * Show as a **modal**: a dark scrim over the whole screen with the popup centred on it.
     *
     * The scrim is added to the root *first* so it sits under the popup, and it swallows every click,
     * which is what makes the dialog modal rather than merely centred. Both fade together with the
     * frame animation, and both are removed when it lands.
     */
    fun showModal(screenRoot: UIComponent, w: Float, h: Float): BrassPopup {
        val sc = Scrim().constrain {
            x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent()
        } childOf screenRoot
        sc.isFloating = true
        scrim = sc

        val sw = screenRoot.getWidth()
        val sh = screenRoot.getHeight()
        val cw = w.coerceAtMost((sw - EDGE * 2).coerceAtLeast(MIN_W))
        val ch = h.coerceAtMost((sh - EDGE * 2).coerceAtLeast(MIN_H))
        return show(screenRoot, (sw - cw) / 2f, (sh - ch) / 2f, cw, ch)
    }

    /**
     * Dismiss the popup. Named `dismiss` (not `hide`) - `UIComponent.hide()` is final.
     *
     * This only *starts* the close: the popup animates out and removes itself from the tree once the
     * animation lands. Removing it here would mean a popup could never be seen to close.
     */
    override fun dismiss() {
        anim.beginClose()
    }

    private var closeFired = false

    /**
     * The actual teardown, run once the close animation has finished.
     *
     * [onClose] fires **unconditionally**, not only when there is still a root to detach from.
     * Bailing out early on a null root - which is what this used to do - meant a popup whose root had
     * been cleared (a double dismiss, or an external reparent) became an invisible zombie that never
     * told its caller it had closed, so whatever `onClose` was meant to release never was.
     */
    override fun onClosed() {
        if (closeFired) return
        closeFired = true
        root?.let { r ->
            scrim?.let { if (r.children.contains(it)) r.removeChild(it) }
            r.removeChild(this)
        }
        scrim = null
        root = null
        onClose()
    }

    /**
     * The dark wash behind a modal.
     *
     * It swallows clicks rather than passing them through - that is the whole point of a modal, and it
     * is also why it is a real component rather than a rectangle painted by the popup: only something
     * in the tree can take the click.
     */
    private class Scrim : UIComponent(), BrassLayers.Layer {
        /** Ranked with the modal it belongs to, so [BrassLayers.raise] treats the pair as one band. */
        override val rank: BrassLayers.Rank get() = BrassLayers.Rank.MODAL

        /** Set each frame by the popup so the scrim fades in and out with it. */
        var alpha: Float = 1f

        init {
            // consume every click; nothing behind a modal is reachable
            onMouseClick { it.stopPropagation() }
        }

        override fun draw(matrixStack: UMatrixStack) {
            beforeDraw(matrixStack)
            val a = alpha.coerceIn(0f, 1f)
            if (a > 0.01f) {
                BrassPaint.rect(
                    matrixStack, getLeft(), getTop(), getRight(), getBottom(),
                    Colors.withAlpha(Colors.SCRIM, (SCRIM_ALPHA * a).toInt()),
                )
            }
            super.draw(matrixStack)
        }

        private companion object {
            /** How dark the scrim gets. Enough to push the UI back without hiding it. */
            const val SCRIM_ALPHA = 150
        }
    }

    private companion object {
        /**
         * Monotonic counter shared by every popup, stamping clicks and shows so their relative order
         * is comparable across instances - see [raiseStamp].
         */
        var interactionSeq = 0L

        /** Inset of the body from the frame's edges. */
        const val PAD = 12f

        /** Margin kept between a popup and the screen edge. */
        const val EDGE = BrassMetrics.FLOATING_EDGE

        /** Smallest a popup may be squeezed to before it stops shrinking. */
        const val MIN_W = 160f
        const val MIN_H = 120f
    }
}
