package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.basicYConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassFont
import kotlin.math.floor

class BrassSectionHeader @JvmOverloads constructor(
    var title: String,
    var count: String? = null,
) : BrassWidget(BrassAccent.DEFAULT) {

    private var trailing: UIComponent? = null
    private var trailingWidth = 0f

    init {
        chrome = BrassChrome.NONE
        constrain { height = basicHeightConstraint { if (trailing != null) TRAILING_H else HEIGHT } }
    }

    fun trailing(component: UIComponent, width: Float): BrassSectionHeader = apply {
        trailing?.let { removeChild(it) }
        trailing = component
        trailingWidth = width
        component.constrain {
            x = basicXConstraint { this@BrassSectionHeader.getRight() - width - BLEED_X }
            y = basicYConstraint { this@BrassSectionHeader.getTop() + BLEED_TOP }
            this.width = gg.essential.elementa.dsl.basicWidthConstraint { width }
            height = basicHeightConstraint { TRAILING_H - BLEED_TOP - BLEED_BOTTOM }
        } childOf this
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val ty = y + floor((h - BrassFont.LINE) / 2f) + 1f
        val caption = title.uppercase()
        val end = x + w - if (trailing != null) trailingWidth + GAP + BLEED_X else 0f
        val shown = BrassFont.fit(this, caption, end - x)
        BrassFont.draw(m, this, shown, x.toFloat(), ty, withAlpha(Colors.UI_TEXT_DARK, entranceFade))
        var cursor = x + BrassFont.width(this, shown)
        count?.let { c ->
            val cx = cursor + COUNT_GAP
            if (cx + BrassFont.width(this, c) < end) {
                BrassFont.draw(m, this, c, cx, ty, withAlpha(Colors.UI_ACCENT, entranceFade))
                cursor = cx + BrassFont.width(this, c)
            }
        }
        val lineX = cursor + GAP
        if (lineX < end) {
            val ly = y + floor(h / 2f)
            BrassPaint.rect(m, lineX, ly, end, ly + 1f, withAlpha(Colors.UI_INNER_BORDER, entranceFade))
        }
    }

    companion object : BrassDemoSource {
        const val HEIGHT = 11f
        const val TRAILING_H = 18f
        private const val GAP = 6f
        private const val COUNT_GAP = 4f

        override fun demo() = BrassDemo("section-header", "Section header", 220f, 14f) {
            BrassSectionHeader("Recent activity", count = "12")
        }
    }
}
