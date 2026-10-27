package net.swzo.brass.ui.kit.quest

import net.swzo.brass.ui.kit.media.BrassIcons
import kotlin.math.roundToInt

/**
 * A quest or mission as plain data: objectives with progress, rewards, a status, an optional deadline.
 * [BrassQuestCard] draws it live every frame, so mutating a quest (progress, status, tracked) is all a
 * host does - there is nothing to refresh. Game-free: rewards name items by string id.
 *
 * ```
 * val q = BrassQuest("iron", "Iron Age", "Equip the forge.", BrassIcons.PICKAXE)
 *     .objective("Mine iron ore", target = 32)
 *     .objective("Smelt ingots", target = 32)
 *     .objective("Find a diamond", optional = true)
 *     .reward(BrassQuest.Reward.item("minecraft:iron_pickaxe"))
 *     .reward(BrassQuest.Reward.xp(250))
 * q.progress(0, 24)
 * ```
 *
 * ### Reacting to progress
 * Hook game logic to the quest itself - [onObjectiveDone], [onComplete] (every required objective
 * done), [onClaim], [onStatus] - and give rewards a [Reward.onGrant] that runs when the quest is
 * claimed. Hooks fire for changes made through [progress], [advance] and [status]; writing an
 * objective's `progress` field directly bypasses them.
 * ```
 * q.onComplete { toast("${it.title} ready to hand in") }
 *  .reward(BrassQuest.Reward.xp(250).onGrant { player.giveXp(250) })
 * ```
 */
class BrassQuest @JvmOverloads constructor(
    val id: String,
    var title: String,
    var summary: String? = null,
    var icon: BrassIcons.Icon? = null,
    status: Status = Status.ACTIVE,
) {

    /** The host's status. Setting it fires [onStatus] (and [onClaim] + reward grants on CLAIMED). */
    var status: Status = status
        set(value) {
            val old = field
            if (old == value) return
            field = value
            statusListeners.toList().forEach { it(this, old, value) }
            if (value == Status.CLAIMED) {
                rewards.forEach { r -> r.grant?.invoke(this) }
                claimListeners.toList().forEach { it(this) }
            }
        }

    /** A landmark quest: drawn richer everywhere (brass frame, ornaments, shimmer, a bigger tree node). */
    var milestone: Boolean = false

    enum class Status {
        /** Visible but not startable yet; [lockedReason] says why. */
        LOCKED,
        AVAILABLE,
        ACTIVE,
        /** Every required objective is done; the reward is waiting to be claimed. */
        READY,
        CLAIMED,
        FAILED,
    }

    /**
     * An item to show instead of [icon] (`"minecraft:iron_pickaxe"`): drawn by the game where it can be,
     * [icon] everywhere else (off-game, unknown id, no platform).
     */
    var iconItem: String? = null

    var giver: String? = null

    /** Who handed out a mission - "NPC", "Player", "Server"... (a journal's mission filters use it). */
    var source: String? = null

    /** A seasonal quest's season or event ("Winterfest"): a badge on the card, the Seasonal filter. */
    var season: String? = null

    /**
     * The advancement this quest grants or gatekeeps (an id for the host - grant it from [onClaim], refuse
     * it server-side until the quest is claimed), and the name the card shows for it.
     */
    var advancement: String? = null
    var advancementTitle: String? = null
    var category: String? = null

    /** 0 hides the rating; otherwise filled stars out of [difficultyMax]. */
    var difficulty: Int = 0
    var difficultyMax: Int = 3

    /** Epoch millis. An active or available quest past it reads as failed. */
    var expiresAt: Long? = null
    var tracked: Boolean = false
    var lockedReason: String? = null

    /** ACTIVE quests whose required objectives are all done read as READY without the host saying so. */
    var autoReady: Boolean = true

    val objectives = ArrayList<Objective>()
    val rewards = ArrayList<Reward>()

    class Objective @JvmOverloads constructor(
        var label: String,
        var target: Int = 1,
        var progress: Int = 0,
        var optional: Boolean = false,
    ) {
        /** Shown as "???" until revealed. */
        var hidden: Boolean = false

        /** Custom counter text, e.g. `{ p, t -> "${p}m / ${t}m" }`; default "24/32" (compacted past 1000). */
        var format: ((Int, Int) -> String)? = null

        val done: Boolean get() = progress >= target

        val fraction: Float get() = if (target <= 0) 1f else (progress.toFloat() / target).coerceIn(0f, 1f)

        fun counter(): String = format?.invoke(progress, target) ?: BrassQuestFormat.counter(progress, target)
    }

    /**
     * One reward, drawn as a chip: the game's item for an [itemId] (an icon off-game) or [icon], then
     * [label] ("8x Bread" for an item stack of [amount] 8). [detail] is the hover text.
     */
    class Reward @JvmOverloads constructor(
        var label: String,
        var icon: BrassIcons.Icon? = null,
        var itemId: String? = null,
        var amount: Int = 1,
        var detail: String? = null,
    ) {
        /** Runs when the quest is claimed - give the item, add the XP, unlock the recipe. */
        var grant: ((BrassQuest) -> Unit)? = null

        fun onGrant(action: (BrassQuest) -> Unit): Reward = apply { grant = action }

        companion object {
            @JvmStatic @JvmOverloads
            fun item(itemId: String, count: Int = 1, label: String? = null): Reward =
                Reward(label ?: itemId.substringAfter(':').replace('_', ' ').replaceFirstChar { it.uppercase() }, itemId = itemId, amount = count)

            @JvmStatic
            fun xp(amount: Int): Reward = Reward("$amount XP", BrassIcons.XP, amount = amount)

            @JvmStatic @JvmOverloads
            fun coins(amount: Int, label: String = "coins"): Reward = Reward("$amount $label", BrassIcons.COIN, amount = amount)

            /** A reward that is a thing rather than a quantity: a title, an unlock, a recipe. */
            @JvmStatic @JvmOverloads
            fun unlock(label: String, icon: BrassIcons.Icon = BrassIcons.KEY, detail: String? = null): Reward =
                Reward(label, icon, amount = 0, detail = detail)
        }
    }

    /** Add an objective; [format] (optional, last so it can trail) writes its counter, e.g. `{ p, t -> "${'$'}p/${'$'}t m" }`. */
    @JvmOverloads
    fun objective(
        label: String,
        target: Int = 1,
        progress: Int = 0,
        optional: Boolean = false,
        format: ((Int, Int) -> String)? = null,
    ): BrassQuest = apply { objectives += Objective(label, target, progress, optional).also { it.format = format } }

    fun reward(reward: Reward): BrassQuest = apply { rewards += reward }

    private val objectiveListeners = ArrayList<(BrassQuest, Objective) -> Unit>()
    private val completeListeners = ArrayList<(BrassQuest) -> Unit>()
    private val claimListeners = ArrayList<(BrassQuest) -> Unit>()
    private val statusListeners = ArrayList<(BrassQuest, Status, Status) -> Unit>()

    /** An objective just reached its target. */
    fun onObjectiveDone(listener: (BrassQuest, Objective) -> Unit): BrassQuest = apply { objectiveListeners += listener }

    /** Every required objective is now done (the quest reads READY). */
    fun onComplete(listener: (BrassQuest) -> Unit): BrassQuest = apply { completeListeners += listener }

    /** The quest was claimed (after its rewards' [Reward.onGrant] ran). */
    fun onClaim(listener: (BrassQuest) -> Unit): BrassQuest = apply { claimListeners += listener }

    /** [status] changed from the first to the second. */
    fun onStatus(listener: (BrassQuest, Status, Status) -> Unit): BrassQuest = apply { statusListeners += listener }

    /** Set objective [index]'s progress (clamped to 0..target); fires the hooks it crosses. */
    fun progress(index: Int, value: Int): BrassQuest = apply {
        val o = objectives.getOrNull(index) ?: return@apply
        val wasDone = o.done
        val wasComplete = isComplete
        o.progress = value.coerceIn(0, o.target)
        if (!wasDone && o.done) objectiveListeners.toList().forEach { it(this, o) }
        if (!wasComplete && isComplete) completeListeners.toList().forEach { it(this) }
    }

    /** Add [by] to objective [index]'s progress. */
    @JvmOverloads
    fun advance(index: Int, by: Int = 1): BrassQuest = apply {
        objectives.getOrNull(index)?.let { progress(index, it.progress + by) }
    }

    val required: List<Objective> get() = objectives.filter { !it.optional }

    val requiredDone: Int get() = required.count { it.done }

    /** Average fraction over the required objectives (all objectives when every one is optional). */
    val fraction: Float
        get() {
            val pool = required.ifEmpty { objectives }
            if (pool.isEmpty()) return if (status == Status.READY || status == Status.CLAIMED) 1f else 0f
            return pool.sumOf { it.fraction.toDouble() }.toFloat() / pool.size
        }

    val isComplete: Boolean get() = required.isNotEmpty() && required.all { it.done }

    fun remainingMs(now: Long): Long? = expiresAt?.let { it - now }

    /** The status to show: [status], with deadlines and [autoReady] applied. */
    fun effectiveStatus(now: Long): Status {
        val s = status
        val expired = (remainingMs(now) ?: 1L) <= 0L
        return when {
            s == Status.ACTIVE && autoReady && isComplete -> Status.READY
            (s == Status.ACTIVE || s == Status.AVAILABLE) && expired -> Status.FAILED
            else -> s
        }
    }

    /** Back to the start: every objective at 0. */
    fun reset(): BrassQuest = apply { objectives.forEach { it.progress = 0 } }
}

/** Text helpers for quests; pure, so tested off-game. */
object BrassQuestFormat {

    /** "45s", "12m", "2h 14m", "3d 4h"; "expired" at or below zero. */
    @JvmStatic
    fun remaining(ms: Long): String {
        if (ms <= 0L) return "expired"
        val s = ms / 1000
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m"
            s < 86_400 -> "${s / 3600}h" + (s % 3600 / 60).let { if (it > 0) " ${it}m" else "" }
            else -> "${s / 86_400}d" + (s % 86_400 / 3600).let { if (it > 0) " ${it}h" else "" }
        }
    }

    /** 950 -> "950", 1200 -> "1.2k", 12000 -> "12k", 3400000 -> "3.4M". */
    @JvmStatic
    fun compact(n: Int): String {
        val a = kotlin.math.abs(n)
        fun one(v: Double, unit: String): String {
            val r = (v * 10).roundToInt() / 10.0
            return (if (r >= 10 || r == r.toLong().toDouble()) r.toLong().toString() else r.toString()) + unit
        }
        val body = when {
            a < 1000 -> a.toString()
            a < 1_000_000 -> one(a / 1000.0, "k")
            else -> one(a / 1_000_000.0, "M")
        }
        return if (n < 0) "-$body" else body
    }

    /** "24/32"; a single-step objective (target 1) has no counter. */
    @JvmStatic
    fun counter(progress: Int, target: Int): String =
        if (target <= 1) "" else "${compact(progress.coerceAtMost(target))}/${compact(target)}"
}
