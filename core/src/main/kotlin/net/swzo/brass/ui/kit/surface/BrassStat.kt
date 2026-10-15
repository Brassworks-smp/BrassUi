package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.constrain
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassState
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.base.disposeWith
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.layout.BrassSpacing
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassTag
import net.swzo.brass.ui.kit.text.BrassTagStyle
import java.awt.Color

class BrassStat @JvmOverloads constructor(
    var caption: String,
    var value: String,
    var note: String? = null,
) : BrassWidget(BrassAccent.DEFAULT) {

    var valueColor: () -> Color = { Colors.UI_TEXT_HOVER }

    private var tagText: String? = null
    private var tagStyle = BrassTagStyle.NEUTRAL

    init {
        chrome = BrassChrome.NONE
        constrain { height = basicHeightConstraint { HEIGHT } }
    }

    fun tag(text: String?, style: BrassTagStyle = BrassTagStyle.NEUTRAL): BrassStat = apply {
        tagText = text
        tagStyle = style
    }

    fun bind(state: BrassState<String>): BrassStat = apply {
        disposeWith(state.onChange { value = it })
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val a = entranceFade
        val x1 = x.toFloat(); val y1 = y.toFloat()
        BrassCard.panel(m, x1, y1, x1 + w, y1 + h, fill = Colors.UI_INNER_BG, alpha = a)

        val pad = BrassSpacing.PAD
        val inner = w - pad * 2
        val tx = x1 + pad
        BrassFont.draw(m, this, BrassFont.fit(this, caption.uppercase(), inner), tx, y1 + pad, withAlpha(Colors.UI_TEXT_DARK, a))

        val big = BrassFont.fit(this, value, inner / VALUE_SCALE)
        BrassFont.draw(m, this, big, tx, y1 + pad + BrassFont.LINE + 3f, withAlpha(valueColor(), a), scale = VALUE_SCALE)

        val lowY = y1 + h - pad - BrassFont.LINE + 1f
        var cursor = tx
        tagText?.let { t ->
            val tw = BrassTag.measure(this, t)
            if (tw <= inner) {
                BrassTag.drawPill(m, this, cursor, lowY - 1f, t, tagStyle.color, a)
                cursor += tw + 4f
            }
        }
        note?.let { n ->
            val room = tx + inner - cursor
            if (room > 8f) BrassFont.draw(m, this, BrassFont.fit(this, n, room), cursor, lowY, withAlpha(Colors.UI_TEXT_DARK, a))
        }
    }

    companion object : BrassDemoSource {
        const val HEIGHT = 56f
        private const val VALUE_SCALE = 2f

        override fun demo() = BrassDemo("stat", "Stat", 110f, HEIGHT) {
            BrassStat("Players", "42", note = "of 100").tag("LIVE", BrassTagStyle.SUCCESS)
        }
    }
}
