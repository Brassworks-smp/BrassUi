package net.swzo.brass.ui.demo.examples

import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.demo.reference.BrassReferenceScreen
import net.swzo.brass.ui.demo.reference.ReferenceStep
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.disposeWith
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassNumberInput
import net.swzo.brass.ui.kit.input.BrassRange
import net.swzo.brass.ui.kit.layout.BrassFlow
import net.swzo.brass.ui.kit.layout.BrassScrollArea
import net.swzo.brass.ui.kit.layout.BrassSectionHeader
import net.swzo.brass.ui.kit.layout.BrassSpacing
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.net.BrassNet
import net.swzo.brass.ui.kit.net.actionButton
import net.swzo.brass.ui.kit.surface.BrassListRow
import net.swzo.brass.ui.kit.surface.BrassPanel
import net.swzo.brass.ui.kit.surface.BrassStat
import net.swzo.brass.ui.kit.surface.BrassToast
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTagStyle
import gg.essential.elementa.dsl.basicHeightConstraint

class MarketScreen(fit: Fit) : BrassReferenceScreen("Market", BrassNet.identity ?: "offline", fit, maxWidth = 480f, maxHeight = 300f) {

    override val id = "market"

    private val stock = BrassNet.state<MarketActions.Stock>("examples.market.stock")
    private val wallet = BrassNet.state<MarketActions.Wallet>(MarketActions.BALANCE)

    private val rows = ArrayList<BrassListRow>()
    private var picked = MarketActions.ITEMS.first()

    private val title = BrassLabel("", Colors.UI_TEXT_HOVER)
    private val detail = BrassLabel("", Colors.UI_TEXT_DARK)
    private val count = BrassNumberInput(BrassRange(1f, 64f, step = 1f), initial = 1f)
    private val coins = BrassStat("Coins", "…")
    private val owned = BrassStat("Owned", "…")
    private lateinit var buyKey: BrassButton
    private lateinit var restockKey: BrassButton

    init {
        val list = BrassScrollArea.list().constrain {
            x = 0.pixels(); y = 0.pixels()
            width = basicWidthConstraint { (body.getWidth() * 0.45f).coerceIn(140f, 200f) }
            height = 100.percent()
        } childOf body

        MarketActions.ITEMS.forEach { item ->
            val row = BrassListRow(item.name, "${item.price} coins", BrassIcons.CART) { r ->
                BrassListRow.selectOnly(rows, r)
                pick(item)
            }
            rows += row
            list.add(row)
        }
        rows.first().selected = true

        val side = BrassScrollArea(gap = BrassSpacing.GAP).constrain {
            x = basicXConstraint { list.getRight() + BrassSpacing.GAP }; y = 0.pixels()
            width = basicWidthConstraint { (body.getRight() - list.getRight() - BrassSpacing.GAP).coerceAtLeast(0f) }
            height = 100.percent()
        } childOf body

        val meter = StockMeter { (stock.current?.counts?.get(picked.id) ?: 0) / 64f }
        val panel = BrassPanel("ITEM", hug = true).add(title, detail, meter, count.constrain { height = 18.pixels() })
        panel.constrain { width = 100.percent() }
        buyKey = actionButton("Buy", MarketActions.buy, BrassAccent.BRASS) {
            MarketActions.Trade(picked.id, count.value.toInt())
        }
        val sell = actionButton("Sell", MarketActions.sell) { MarketActions.Trade(picked.id, count.value.toInt()) }
        panel.row(18f, sell, buyKey)
        side.add(panel)

        val stats = BrassFlow(gapX = BrassSpacing.GAP, gapY = BrassSpacing.GAP, itemHeight = BrassStat.HEIGHT, stretch = true)
        stats.add(coins, 80f)
        stats.add(owned, 80f)
        stats.constrain { width = 100.percent(); height = basicHeightConstraint { stats.contentHeight() } }
        side.add(stats)

        restockKey = actionButton("Restock all", MarketActions.restock, BrassAccent.DANGER,
            onResult = { if (it.ok) BrassToast.show(background, "Shelves refilled", BrassToast.Type.SUCCESS) }) { }
        side.add(BrassSectionHeader("Admin").trailing(restockKey, 80f).also { it.constrain { width = 100.percent() } })

        val offStock = stock.onChange { refresh() }
        val offWallet = wallet.onChange { refresh() }
        body.disposeWith { offStock(); offWallet() }

        BrassNet.send(MarketActions.open, Unit)
        pick(picked)
    }

    private fun pick(item: MarketActions.Item) {
        picked = item
        title.text = item.name
        refresh()
    }

    private fun refresh() {
        val left = stock.current?.counts?.get(picked.id)
        detail.text = "${picked.price} coins each · ${left ?: "?"} in stock"
        coins.value = wallet.current?.coins?.toString() ?: "…"
        owned.value = wallet.current?.owned?.get(picked.id)?.toString() ?: "0"
        rows.forEachIndexed { i, r ->
            val n = stock.current?.counts?.get(MarketActions.ITEMS[i].id) ?: 0
            r.tag(if (n == 0) "SOLD OUT" else null, BrassTagStyle.ERROR)
        }
    }

    override fun scenario() = listOf(
        ReferenceStep("opened") { },
        ReferenceStep("pick-iron", frames = 30) { d -> d.click(rows[2]) },
        ReferenceStep("more", frames = 30) { d -> d.click(count, 0.95f, 0.5f) },
        ReferenceStep("buy", frames = 40) { d -> d.click(buyKey) },
        ReferenceStep("pick-elytra", frames = 30) { d -> d.click(rows[4]) },
        ReferenceStep("too-poor", frames = 50) { d -> d.click(buyKey) },
        ReferenceStep("admin-locked", frames = 260) { d -> d.hover(restockKey) },
        ReferenceStep("narrow", frames = 40) { squeeze(320f, 260f) },
    )
}
