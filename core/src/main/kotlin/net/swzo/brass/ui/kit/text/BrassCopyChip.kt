package net.swzo.brass.ui.kit.text

import gg.essential.universal.UDesktop
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassCopyChip.FLASH_MS

/**
 * The small copy-to-clipboard chip that sits in the corner of a code surface - the code view, and
 * each fenced block in a markdown document.
 * One drawing in one place, because the two hosts paint their own chrome (neither is a component
 * tree the chip could be added to as a child) and a copy affordance that looks different in the two
 * places it appears reads as two features. The glyph is the traditional pair of offset pages, drawn
 * as two 1-px outlines with the front page's fill occluding the back - no icon sheet entry needed.
 * After a copy it shows a check for [FLASH_MS], which is the entire feedback: a toast for a copy
 * would outweigh the action.
 */
object BrassCopyChip {

    const val SIZE = 11f

    const val FLASH_MS = 1200L

    fun flashing(copiedAt: Long): Boolean =
        copiedAt != 0L && System.currentTimeMillis() - copiedAt < FLASH_MS

    fun copy(text: String): Boolean = runCatching { UDesktop.setClipboardString(text) }.isSuccess

    fun draw(m: UMatrixStack, x1: Float, y1: Float, hovered: Boolean, copied: Boolean) {
        val x2 = x1 + SIZE
        val y2 = y1 + SIZE
        val fill = if (hovered) Colors.UI_ELEMENT_BG_HOVER else Colors.UI_ELEMENT_BG
        BrassCard.flat(m, x1, y1, x2, y2, fill = fill)

        if (copied) {
            BrassIcons.draw(m, BrassIcons.CHECK, x1 + 2f, y1 + 2f, SIZE - 4f, Colors.GOOD)
            return
        }

        val ink = if (hovered) Colors.UI_TEXT_HOVER else Colors.UI_TEXT_DARK
        // Back page first; the front page's fill covers their overlap so the two read as stacked
        // sheets rather than a lattice.
        BrassPaint.border(m, x1 + 3f, y1 + 3f, x1 + 7f, y1 + 7f, ink)
        BrassPaint.rect(m, x1 + 5f, y1 + 5f, x1 + 9f, y1 + 9f, fill)
        BrassPaint.border(m, x1 + 5f, y1 + 5f, x1 + 9f, y1 + 9f, ink)
    }
}
