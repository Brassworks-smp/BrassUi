package net.swzo.brass.ui.kit.quest

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.platform.BrassPlatform
import java.awt.Color
import kotlin.math.floor

/** A quest's picture in a square box: its [BrassQuest.iconItem] through the game, else its pixel icon. */
internal object QuestIcon {

    /**
     * Draw centred in the [box]-sized square at ([x], [y]). An item is drawn at 16 px when the box has
     * room (8 px otherwise); a pixel icon at [iconSize], tinted [tint]. [dim] keeps items faded where a
     * pixel icon would be greyed (locked, done).
     */
    fun draw(m: UMatrixStack, q: BrassQuest, x: Float, y: Float, box: Float, iconSize: Float, tint: Color, alpha: Float, dim: Boolean = false) {
        val item = q.iconItem
        if (item != null) {
            val size = if (box >= 16f) 16f else 8f
            val drawn = BrassPlatform.current?.drawItem(
                m, item, x + floor((box - size) / 2f), y + floor((box - size) / 2f), size, 1, if (dim) alpha * 0.45f else alpha,
            ) == true
            if (drawn) return
        }
        val ic = q.icon?.takeIf { it.present } ?: BrassIcons.SCROLL
        BrassIcons.draw(m, ic, x + floor((box - iconSize) / 2f), y + floor((box - iconSize) / 2f), iconSize, tint)
    }
}
