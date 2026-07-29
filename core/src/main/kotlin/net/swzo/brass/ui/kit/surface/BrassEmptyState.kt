package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.dsl.*
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * What a list shows when it has nothing to show: an icon, a line of explanation, and optionally the
 * one action that would fix it.
 *
 * ```kotlin
 * BrassEmptyState(BrassIcons.SEARCH, "No results", "Try a shorter search term")
 *     .withAction("Clear filters") { filters.clear() }
 * ```
 *
 * ### Why this is a widget at all
 *
 * It is four draw calls, and that is exactly why it should not be written four times. An empty table,
 * an empty tree, an empty search and an empty inventory each want the same shape, and hand-rolled
 * versions diverge on the things that matter: whether the icon is above or beside the text, how grey
 * the text is, whether there is an action and where it sits. The result is a UI that looks
 * inconsistent precisely in the moments when the user is already unsure whether something is broken.
 *
 * Everything is centred in whatever box it is given, so it can be dropped over a list without needing
 * to know the list's size.
 */
class BrassEmptyState(
    var icon: BrassIcons.Icon = BrassIcons.NONE,
    var title: String = "Nothing here",
    /** A quieter second line, or null for just the title. */
    var detail: String? = null,
) : BrassWidget(BrassAccent.DEFAULT) {

    private var action: BrassButton? = null

    /**
     * Add the one action that would resolve the empty state.
     *
     * *One*. An empty state offering three buttons is a menu, and the user reading it has already
     * failed to find what they were looking for.
     */
    fun withAction(label: String, accent: BrassAccent = BrassAccent.BRASS, onClick: () -> Unit) = apply {
        action?.let { removeChild(it) }
        val button = BrassButton(label, accent, onClick)
        button.constrain {
            x = basicXConstraint { this@BrassEmptyState.getLeft() + (this@BrassEmptyState.getWidth() - button.getWidth()) / 2f }
            y = basicYConstraint { contentTop() + iconBlock() + textBlock() + GAP }
            width = ACTION_W.pixels()
            height = ACTION_H.pixels()
        } childOf this
        action = button
    }

    /** Height of the icon and the gap under it, or zero when there is no icon. */
    private fun iconBlock(): Float = if (!icon.present) 0f else ICON + GAP

    private fun textBlock(): Float = BrassFont.LINE + if (detail != null) BrassFont.LINE + 2f else 0f

    /** Total height of the block, for centring it vertically. */
    private fun blockHeight(): Float =
        iconBlock() + textBlock() + if (action != null) GAP + ACTION_H else 0f

    private fun contentTop(): Float = getTop() + (getHeight() - blockHeight()) / 2f

    override fun drawContent(matrixStack: UMatrixStack, bx: Int, by: Int, bw: Int, bh: Int) {

        val cx = getLeft() + getWidth() / 2f
        var y = contentTop()

        if (icon.present) {
            BrassIcons.draw(matrixStack, icon, cx - ICON / 2f, y, ICON, Colors.UI_TEXT_DARK)
            y += ICON + GAP
        }

        val fitted = BrassFont.fit(this, title, getWidth() - PAD * 2)
        BrassFont.draw(matrixStack, this, fitted, cx - BrassFont.width(this, fitted) / 2f, y, Colors.UI_TEXT)
        y += BrassFont.LINE + 2f

        detail?.let {
            val line = BrassFont.fit(this, it, getWidth() - PAD * 2)
            BrassFont.draw(matrixStack, this, line, cx - BrassFont.width(this, line) / 2f, y, Colors.UI_TEXT_DARK)
        }

    }

    companion object : BrassDemoSource {

        /** The placeholder a list shows when it has nothing. Static by design. */
        override fun demo() = BrassDemo("empty-state", "Empty state", 240f, 104f) {
            BrassEmptyState(BrassIcons.SEARCH, "No results", "Try a different search term")
        }

        // ---- widget internals ------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val ICON = 16f
        private const val GAP = 6f
        private const val PAD = 12f
        private const val ACTION_W = 96f
        private const val ACTION_H = 16f
    }
}
