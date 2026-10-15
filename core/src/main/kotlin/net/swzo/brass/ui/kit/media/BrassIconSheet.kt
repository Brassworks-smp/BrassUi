package net.swzo.brass.ui.kit.media

import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.constrain
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.input.BrassSquareButton
import net.swzo.brass.ui.kit.layout.BrassFlow
import net.swzo.brass.ui.kit.surface.BrassTooltip

object BrassIconSheet : BrassDemoSource {

    override fun demo() = BrassDemo("icons", "Icons", 300f, 190f) {
        val flow = BrassFlow(gapX = 3f, gapY = 3f, itemHeight = 14f)
        for (name in BrassIcons.spriteNames().sorted()) {
            val key = BrassSquareButton(BrassIcons.Icon(name), BrassAccent.DEFAULT) {}
            BrassTooltip.attach(key, name)
            flow.add(key, 16f)
        }
        flow.constrain { height = basicHeightConstraint { flow.contentHeight() } }
        flow
    }
}
