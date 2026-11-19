package net.swzo.brass.ui.demo.examples

import net.swzo.brass.ui.kit.net.BrassActionSet
import net.swzo.brass.ui.kit.net.BrassActions
import net.swzo.brass.ui.kit.net.BrassMessages
import net.swzo.brass.ui.kit.net.BrassRateLimit
import net.swzo.brass.ui.kit.net.brassAction
import net.swzo.brass.ui.kit.net.brassAsyncAction
import net.swzo.brass.ui.kit.net.brassValue
import net.swzo.brass.ui.kit.net.err
import net.swzo.brass.ui.kit.net.ok
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@BrassActionSet
object MarketActions : BrassActions {

    data class Trade(val itemId: String, val count: Int)
    data class Stock(val counts: Map<String, Int>)
    data class Wallet(val coins: Int, val owned: Map<String, Int>)

    class Item(val id: String, val name: String, val price: Int)

    val ITEMS = listOf(
        Item("bread", "Bread", 2),
        Item("torch", "Torch", 1),
        Item("iron", "Iron ingot", 9),
        Item("map", "Explorer map", 40),
        Item("elytra", "Elytra", 900),
    )

    const val BALANCE = "examples.market.wallet"
    private const val START_COINS = 120

    val stock = brassValue("examples.market.stock", Stock(mapOf("bread" to 64, "torch" to 128, "iron" to 24, "map" to 3, "elytra" to 1)))

    private val wallets = ConcurrentHashMap<String, Wallet>()

    init {
        BrassMessages.register("market.unknown", "There is no {0} for sale")
        BrassMessages.register("market.count", "Pick between 1 and 64")
        BrassMessages.register("market.stock", "Only {0} left")
        BrassMessages.register("market.coins", "You need {0} coins, you have {1}")
        BrassMessages.register("market.owned", "You only have {0}")
    }

    private fun walletOf(player: String?) = wallets.getOrPut(player ?: "local") { Wallet(START_COINS, emptyMap()) }

    private fun item(id: String) = ITEMS.firstOrNull { it.id == id }

    private fun validTrade(t: Trade): String? = when {
        item(t.itemId) == null -> "market.unknown"
        t.count !in 1..64 -> "market.count"
        else -> null
    }

    val open = brassAction<Unit>(
        id = "examples.market.open",
        permission = "examples.market.use",
    ) { ctx, _ ->
        ctx.publishTo(ctx.playerId, BALANCE, walletOf(ctx.playerId))
        ok()
    }

    val buy = brassAction<Trade>(
        id = "examples.market.buy",
        permission = "examples.market.use",
        rateLimit = BrassRateLimit(max = 6, perSeconds = 5),
        validate = ::validTrade,
    ) { ctx, t ->
        val item = item(t.itemId)!!
        val left = stock.value.counts[t.itemId] ?: 0
        val wallet = walletOf(ctx.playerId)
        val cost = item.price * t.count
        when {
            left < t.count -> err("market.stock", left)
            wallet.coins < cost -> err("market.coins", cost, wallet.coins)
            else -> {
                stock.value = Stock(stock.value.counts + (t.itemId to left - t.count))
                val next = Wallet(wallet.coins - cost, wallet.owned + (t.itemId to (wallet.owned[t.itemId] ?: 0) + t.count))
                wallets[ctx.playerId ?: "local"] = next
                ctx.publishTo(ctx.playerId, BALANCE, next)
                ok(next.coins)
            }
        }
    }

    val sell = brassAction<Trade>(
        id = "examples.market.sell",
        permission = "examples.market.use",
        rateLimit = BrassRateLimit(max = 6, perSeconds = 5),
        validate = ::validTrade,
    ) { ctx, t ->
        val item = item(t.itemId)!!
        val wallet = walletOf(ctx.playerId)
        val have = wallet.owned[t.itemId] ?: 0
        if (have < t.count) return@brassAction err("market.owned", have)
        stock.value = Stock(stock.value.counts + (t.itemId to (stock.value.counts[t.itemId] ?: 0) + t.count))
        val next = Wallet(wallet.coins + item.price * t.count / 2, wallet.owned + (t.itemId to have - t.count))
        wallets[ctx.playerId ?: "local"] = next
        ctx.publishTo(ctx.playerId, BALANCE, next)
        ok(next.coins)
    }

    val restock = brassAsyncAction<Unit>(
        id = "examples.market.restock",
        permission = "examples.market.admin",
        minOpLevel = 2,
    ) { _, _ ->
        CompletableFuture.supplyAsync(
            {
                stock.value = Stock(ITEMS.associate { it.id to 64 })
                ok()
            },
            CompletableFuture.delayedExecutor(800, TimeUnit.MILLISECONDS),
        )
    }
}
