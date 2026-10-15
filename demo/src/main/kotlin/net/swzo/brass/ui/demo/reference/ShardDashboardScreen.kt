package net.swzo.brass.ui.demo.reference

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.layout.BrassFlow
import net.swzo.brass.ui.kit.layout.BrassScrollArea
import net.swzo.brass.ui.kit.layout.BrassSectionHeader
import net.swzo.brass.ui.kit.layout.BrassSpacing
import net.swzo.brass.ui.kit.layout.BrassVBox
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.surface.BrassChart
import net.swzo.brass.ui.kit.surface.BrassListRow
import net.swzo.brass.ui.kit.surface.BrassStat
import net.swzo.brass.ui.kit.surface.BrassToast
import net.swzo.brass.ui.kit.text.BrassTagStyle
import kotlin.math.sin

class ShardDashboardScreen(fit: Fit) : BrassReferenceScreen("Shard Monitor", "eu-west-2", fit) {

    override val id = "dashboard"

    private val scroll: BrassScrollArea

    init {
        scroll = BrassScrollArea().constrain {
            x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent()
        } childOf body

        val stats = BrassFlow(gapX = BrassSpacing.GAP, gapY = BrassSpacing.GAP, itemHeight = BrassStat.HEIGHT, stretch = true)
        stats.add(BrassStat("Players", "42", note = "of 100").tag("LIVE", BrassTagStyle.SUCCESS), STAT_W)
        stats.add(BrassStat("TPS", "19.8", note = "5 min avg").tag("STABLE", BrassTagStyle.SUCCESS), STAT_W)
        stats.add(BrassStat("Memory", "3.1G", note = "of 8G"), STAT_W)
        stats.add(BrassStat("Uptime", "4d 2h").tag("PATCH DUE", BrassTagStyle.WARNING), STAT_W)
        stats.constrain { height = basicHeightConstraint { stats.contentHeight() } }

        val chart = BrassChart(window = 90, fixedMax = 20f)
        val tps = chart.series("TPS", Colors.BRASS_400, filled = true)
        val mspt = chart.series("MSPT/2.5", Colors.PATINA_400)
        repeat(90) { i ->
            chart.push(tps, 20f - (sin(i / 7f) + 1f) * (if (i in 50..58) 3f else 0.4f))
            chart.push(mspt, 9f + sin(i / 11f) * 3f)
        }
        val tickColumn = column(BrassSectionHeader("Tick rate"), chart)
        val feed = BrassVBox(gap = 1f, bleed = false)
        EVENTS.forEach { (icon, text, meta) ->
            feed.add(BrassListRow(text, icon = icon).also { r ->
                r.meta = meta
                r.constrain { width = 100.percent() }
            })
        }
        val feedColumn = column(
            BrassSectionHeader("Recent events").trailing(
                BrassButton("View all") { BrassToast.show(background, "Opening the full log", BrassToast.Type.INFO) },
                52f,
            ),
            feed,
        )

        val columns = BrassFlow(gapX = BrassSpacing.GAP * 2, gapY = BrassSpacing.SECTION_GAP, itemHeight = COLUMN_H, stretch = true)
        columns.add(tickColumn, COLUMN_MIN_W)
        columns.add(feedColumn, COLUMN_MIN_W)
        columns.constrain { height = basicHeightConstraint { columns.contentHeight() } }

        scroll.add(BrassSectionHeader("Overview"), stats, columns)
    }

    private fun column(header: BrassSectionHeader, content: gg.essential.elementa.UIComponent): UIContainer {
        val box = UIContainer()
        header.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = BrassSectionHeader.TRAILING_H.pixels() } childOf box
        content.constrain {
            x = 0.pixels(); y = (BrassSectionHeader.TRAILING_H + BrassSpacing.TIGHT).pixels()
            width = 100.percent()
            height = 100.percent() - (BrassSectionHeader.TRAILING_H + BrassSpacing.TIGHT + BrassWidget.BLEED_BOTTOM).pixels()
        } childOf box
        return box
    }

    override fun scenario() = listOf(
        ReferenceStep("scrolled-end") { scroll.body.scrollToBottom(false) },
        ReferenceStep("narrow") { scroll.body.scrollToTop(false); squeeze(300f, 300f) },
        ReferenceStep("narrow-end") { scroll.body.scrollToBottom(false) },
    )

    private companion object {
        const val STAT_W = 104f
        const val COLUMN_H = 124f
        const val COLUMN_MIN_W = 200f

        val EVENTS = listOf(
            Triple(BrassIcons.SAVE, "Backup finished", "2m"),
            Triple(BrassIcons.BOLT, "Tick spike in the_nether", "9m"),
            Triple(BrassIcons.USER, "Steve joined", "14m"),
            Triple(BrassIcons.REFRESH, "Config reloaded by Alex", "1h"),
            Triple(BrassIcons.CLOCK, "Restart scheduled for 04:00", "3h"),
        )
    }
}
