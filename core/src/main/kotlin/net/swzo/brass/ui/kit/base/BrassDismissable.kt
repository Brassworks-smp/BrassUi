package net.swzo.brass.ui.kit.base

/**
 * A transient layer floating above a screen - a popup sub-window, a context menu - that Escape should
 * close *before* the screen itself.
 *
 * [net.swzo.brass.ui.BrassScreen] walks its floating children for these and dismisses the topmost one
 * per Escape press, so the key peels layers off one at a time and only closes the UI once none are
 * left, instead of tearing the whole screen down from under an open dialog.
 */
interface BrassDismissable {
    /**
     * Close this layer. Called by the screen on Escape; also wired to close buttons.
     *
     * Named `dismiss` and not `hide` because `UIComponent.hide()` is final in Elementa. The toolkit
     * settled on three verbs for one idea - `dismiss` here, `close` on a toast, `requestClose` on a
     * frame - and this is the one they all now mean: **start** closing, animate out, tear down when
     * the animation lands. `close` and `requestClose` survive as aliases on the types that had them.
     */
    fun dismiss()

    /**
     * Whether this layer is already playing its close animation.
     *
     * Escape must skip these. A layer that animates out stays in the tree for the duration, so without
     * this a second Escape press lands on the same half-closed layer instead of peeling off the one
     * beneath it - the key would appear to stop working for a few frames.
     */
    val dismissing: Boolean get() = false

    /**
     * Whether Escape may close this layer.
     *
     * A dialog that must be answered - a confirmation, a destructive action - turns this off, and
     * Escape then falls through to whatever is beneath it rather than being swallowed by a layer that
     * refuses to close. Note this governs *Escape only*: a close button or code calling [dismiss]
     * still works.
     */
    val escapeDismissable: Boolean get() = true
}
