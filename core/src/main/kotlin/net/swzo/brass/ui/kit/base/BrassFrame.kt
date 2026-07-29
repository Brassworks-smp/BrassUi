package net.swzo.brass.ui.kit.base

import net.swzo.brass.ui.kit.surface.BrassPopup
import net.swzo.brass.ui.kit.surface.BrassWindow

/**
 * A window-like frame that responds to the standard window shortcuts. Implemented by [BrassWindow] and
 * [BrassPopup]; `BrassScreen` finds the topmost one in the tree and routes keys to it, so a screen gets
 * the shortcuts for free without knowing which kind of frame it is holding.
 *
 * | Key | Action |
 * |---|---|
 * | `F11` | [toggleMaximize] |
 * | `Ctrl/Cmd+M` | [toggleCollapse] |
 * | `Ctrl/Cmd+W` | [requestClose] |
 */
interface BrassFrame {
    /** Fill the parent, or restore the pre-maximise frame. */
    fun toggleMaximize()

    /** Roll up to just the title bar, or back down to the previous height. */
    fun toggleCollapse()

    /** Close the frame, as if its close button was pressed. */
    fun requestClose()
}
