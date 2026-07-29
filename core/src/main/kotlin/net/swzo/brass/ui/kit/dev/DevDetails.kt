package net.swzo.brass.ui.kit.dev

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTag
import net.swzo.brass.ui.kit.text.BrassWrappedLabel
import java.awt.Color
import kotlin.math.roundToInt

/**
 * The bottom pane: everything known about the selected element - its type + tag, bounds, child count,
 * and (for a widget) its flags / text. Modelled on the reference debugger's selected-element panel.
 */
internal class DevDetails : UIContainer(), BrassDevOverlay {

    override fun draw(matrixStack: UMatrixStack) {
        val x = getLeft().roundToInt().toFloat(); val y = getTop().roundToInt().toFloat()
        val x2 = getRight().roundToInt().toFloat()
        UIBlock.drawBlock(matrixStack, Colors.UI_INNER_BORDER, x.toDouble(), y.toDouble(), x2.toDouble(), (y + 1).toDouble())

        val avail = (x2 - x - 16f).coerceAtLeast(10f)
        var ly = y + 6f
        fun line(text: String, color: Color) {
            BrassFont.draw(matrixStack, this, BrassFont.fit(this, text, avail), x + 8f, ly, color, false)
            ly += BrassFont.LINE + 3f
        }

        val c = BrassDevMode.selectedComponent
        if (c == null) {
            line("SELECTED", Colors.UI_TEXT_DARK)
            line("click an element in the tree", Colors.UI_TEXT_DARK)
            super.draw(matrixStack)
            return
        }
        try {
            val (tagLabel, tagColor) = tagFor(c)
            BrassFont.draw(matrixStack, this, c.javaClass.simpleName.ifEmpty { "anon" }, x + 8f, ly, Colors.UI_TEXT_HOVER, false)
            val nameW = BrassFont.width(this, c.javaClass.simpleName.ifEmpty { "anon" })
            // a tag is exactly a label's height, so it aligns with the name on the same top edge
            BrassTag.drawPill(matrixStack, this, x + 8f + nameW + 6f, ly, tagLabel, tagColor)
            ly += BrassFont.LINE + 6f

            line("Position", Colors.UI_TEXT_DARK)
            line("x ${c.getLeft().roundToInt()}   y ${c.getTop().roundToInt()}", Colors.UI_TEXT)
            line("Size", Colors.UI_TEXT_DARK)
            line("${(c.getRight() - c.getLeft()).roundToInt()} x ${(c.getBottom() - c.getTop()).roundToInt()}", Colors.UI_TEXT)
            line("Children", Colors.UI_TEXT_DARK)
            line("${c.children.count { it !is BrassDevOverlay }}", Colors.UI_TEXT)

            when (c) {
                is BrassLabel -> { line("Text", Colors.UI_TEXT_DARK); line("\"${c.text}\"", Colors.UI_TEXT) }
                is BrassWrappedLabel -> { line("Text", Colors.UI_TEXT_DARK); line("\"${c.text}\"", Colors.UI_TEXT) }
                is BrassWidget -> {
                    line("Flags", Colors.UI_TEXT_DARK)
                    val flags = buildString {
                        if (c.clickable) append("clickable ")
                        if (c.chrome != BrassChrome.KEYCAP) append(c.chrome.name.lowercase()).append(' ')
                        if (!c.active) append("disabled ")
                    }.ifBlank { "-" }
                    line(flags, Colors.UI_TEXT)
                }
            }
        } catch (_: Throwable) {
            // a selected element can be detached (its popup closed) between frames - never crash the panel
        }
        super.draw(matrixStack)
    }

    companion object {
        const val HEIGHT = 150f
    }
}
