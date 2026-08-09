package net.swzo.brass.ui.kit.base

/**
 * A transient layer floating above a screen - a popup sub-window, a context menu - that Escape should
 * close *before* the screen itself.
 * [net.swzo.brass.ui.BrassScreen] walks its floating children for these and dismisses the topmost one
 * per Escape press, so the key peels layers off one at a time and only closes the UI once none are
 * left, instead of tearing the whole screen down from under an open dialog.
 */
interface BrassDismissable {
    fun dismiss()

    /**
     * Whether this layer is already playing its close animation.
     * Escape must skip these. A layer that animates out stays in the tree for the duration, so without
     * this a second Escape press lands on the same half-closed layer instead of peeling off the one
     * beneath it - the key would appear to stop working for a few frames.
     */
    val dismissing: Boolean get() = false

    /**
     * Whether Escape may close this layer.
     * A dialog that must be answered - a confirmation, a destructive action - turns this off, and
     * Escape then falls through to whatever is beneath it rather than being swallowed by a layer that
     * refuses to close. Note this governs *Escape only*: a close button or code calling [dismiss]
     * still works.
     */
    val escapeDismissable: Boolean get() = true
}
