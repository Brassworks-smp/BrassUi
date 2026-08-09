package net.swzo.brass.ui.kit.media

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.platform.BrassPlatform
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color
import kotlin.math.min
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * An inventory-style slot showing a Minecraft item - the first widget backed by [BrassPlatform].
 * ```kotlin
 * BrassItem("minecraft:diamond_pickaxe", count = 1)
 *     .constrain { width = 18.pixels(); height = 18.pixels() }
 * ```
 * It is a [net.swzo.brass.ui.kit.base.BrassWidget] like everything else, so it obeys the same layout constraints, hover, press,
 * entrance and accent machinery - an item slot is a widget that happens to render an item, not a
 * special case that bypasses the toolkit.
 * The item itself is drawn by the platform, because item rendering is Minecraft's and `brassui` has
 * no Minecraft imports. With **no platform bound**, or an unknown id, the slot draws its recessed
 * well and a small cross rather than nothing - a missing item should look deliberately empty, not
 * like a rendering bug.
 */
class BrassItem(
    var itemId: String,
    var count: Int = 1,
    tooltip: Boolean = true,
    private val onClick: (() -> Unit)? = null,
) : BrassPlatformVisual(BrassAccent.DEFAULT) {

    init {
        clickable = onClick != null
        if (onClick != null) onMouseClick { e -> if (active && e.mouseButton == 0) onClick.invoke() }
        if (tooltip) {
            // Attached ONCE, with a supplier. The name is not knowable at construction (the platform
            // may not be bound yet, and itemId can change), but attaching from inside onMouseEnter -
            // which is what this used to do - adds listeners while Elementa is iterating them and
            // throws ConcurrentModificationException from updateCurrentlyHoveredState.
            BrassTooltip.attachLazy(this, { BrassPlatform.current?.itemName(itemId) ?: itemId })
        }
    }

    override fun proxyActivate() { if (active) onClick?.invoke() }

    override fun contentBox(x: Int, y: Int, w: Int, h: Int): FloatArray {
        val size = min(w, h).toFloat()
        val inset = (size * 0.12f).coerceIn(1f, 3f)
        val draw = size - inset * 2f
        return floatArrayOf(x + (w - size) / 2f + inset, y + (h - size) / 2f + inset, draw, draw)
    }

    override fun paintNative(
        m: UMatrixStack,
        platform: BrassPlatform,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        fade: Float,
    ): Boolean = platform.drawItem(m, itemId, x, y, w, count, fade)

    override fun paintPlaceholder(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val cx = x + w / 2f
        val cy = y + h / 2f
        val r = contentBox(x, y, w, h)[2] * 0.22f
        fill(m, cx - r, cy - 1f, cx + r, cy + 1f, EMPTY)
        fill(m, cx - 1f, cy - r, cx + 1f, cy + r, EMPTY)
    }

    override fun decorate(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int, fade: Float, drawn: Boolean) {
        // The count is NOT drawn when the platform drew the item. Vanilla renders stack decorations at
        // a depth offset above the model; text drawn by the toolkit afterwards ends up *behind* it, so
        // drawItem draws the count itself. With no platform there is no model to hide behind.
        if (count > 1 && !drawn) {
            val label = count.toString()
            val tw = BrassFont.width(this, label)
            BrassFont.draw(m, this, label, x + w - tw - 1f, y + h - BrassFont.LINE - 1f, Colors.UI_TEXT_HOVER)
        }
    }

    private fun fill(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) =
        BrassPaint.rect(m, x1, y1, x2, y2, c)

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("item", "Item slot", 24f, 24f) {
            BrassItem("minecraft:diamond_pickaxe")
        }

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private val EMPTY: Color get() = Colors.ITEM_EMPTY
    }
}
