package net.swzo.brass.ui

import net.swzo.brass.ui.kit.quest.BrassQuest
import net.swzo.brass.ui.kit.quest.BrassQuest.Status
import net.swzo.brass.ui.kit.quest.BrassQuestFormat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class BrassQuestTest {

    private fun quest() = BrassQuest("q", "Quest")
        .objective("Mine", target = 32, progress = 16)
        .objective("Smelt", target = 16)
        .objective("Bonus", optional = true)

    @Test
    fun fractionAveragesRequiredObjectivesOnly() {
        val q = quest()
        assertEquals(0.25f, q.fraction, 1e-4f)
        q.progress(2, 1)
        assertEquals(0.25f, q.fraction, 1e-4f, "optional objectives never move the bar")
    }

    @Test
    fun allOptionalFallsBackToEveryObjective() {
        val q = BrassQuest("q", "Q").objective("a", optional = true, progress = 1).objective("b", optional = true)
        assertEquals(0.5f, q.fraction, 1e-4f)
        assertFalse(q.isComplete, "nothing required means nothing to complete")
    }

    @Test
    fun progressClampsAndAdvanceAdds() {
        val q = quest()
        q.progress(0, 99)
        assertEquals(32, q.objectives[0].progress)
        q.progress(1, -5)
        assertEquals(0, q.objectives[1].progress)
        q.advance(1, 4).advance(1)
        assertEquals(5, q.objectives[1].progress)
        q.progress(9, 1)
    }

    @Test
    fun activeQuestReadsReadyOnceRequiredAreDone() {
        val q = quest()
        assertEquals(Status.ACTIVE, q.effectiveStatus(0))
        q.progress(0, 32).progress(1, 16)
        assertTrue(q.isComplete)
        assertEquals(Status.READY, q.effectiveStatus(0))
        assertEquals(Status.ACTIVE, q.status, "the stored status is the host's")
        q.autoReady = false
        assertEquals(Status.ACTIVE, q.effectiveStatus(0))
    }

    @Test
    fun deadlinesFailActiveAndAvailableOnly() {
        val q = quest().apply { expiresAt = 1000L }
        assertEquals(Status.ACTIVE, q.effectiveStatus(999L))
        assertEquals(Status.FAILED, q.effectiveStatus(1000L))
        q.status = Status.AVAILABLE
        assertEquals(Status.FAILED, q.effectiveStatus(2000L))
        q.status = Status.CLAIMED
        assertEquals(Status.CLAIMED, q.effectiveStatus(2000L))
    }

    @Test
    fun resetZeroesProgress() {
        val q = quest().reset()
        assertTrue(q.objectives.all { it.progress == 0 })
    }

    @Test
    fun remainingFormats() {
        assertEquals("expired", BrassQuestFormat.remaining(0))
        assertEquals("45s", BrassQuestFormat.remaining(45_000))
        assertEquals("12m", BrassQuestFormat.remaining(12 * 60_000L + 30_000))
        assertEquals("2h 14m", BrassQuestFormat.remaining((2 * 60 + 14) * 60_000L))
        assertEquals("3h", BrassQuestFormat.remaining(3 * 3_600_000L))
        assertEquals("3d 4h", BrassQuestFormat.remaining((3 * 24 + 4) * 3_600_000L))
    }

    @Test
    fun compactAndCounter() {
        assertEquals("950", BrassQuestFormat.compact(950))
        assertEquals("1k", BrassQuestFormat.compact(1000))
        assertEquals("1.2k", BrassQuestFormat.compact(1200))
        assertEquals("12k", BrassQuestFormat.compact(12_345))
        assertEquals("3.4M", BrassQuestFormat.compact(3_400_000))
        assertEquals("-1.5k", BrassQuestFormat.compact(-1500))
        assertEquals("", BrassQuestFormat.counter(0, 1))
        assertEquals("24/32", BrassQuestFormat.counter(24, 32))
        assertEquals("5k/5k", BrassQuestFormat.counter(9000, 5000))
    }

    @Test
    fun customCounterFormat() {
        val q = BrassQuest("q", "Q").objective("Descend", target = 40, progress = 12) { p, t -> "${p}m / ${t}m" }
        assertEquals("12m / 40m", q.objectives[0].counter())
    }

    @Test
    fun itemRewardNamesItselfFromTheId() {
        // xp/coins/unlock touch BrassIcons, which needs Elementa - not on the test classpath.
        val r = BrassQuest.Reward.item("minecraft:iron_pickaxe", 2)
        assertEquals("Iron pickaxe", r.label)
        assertEquals(2, r.amount)
    }

    @Test
    fun hooksFireWhenProgressCrossesThem() {
        val q = quest()
        val log = ArrayList<String>()
        q.onObjectiveDone { _, o -> log += "done:${o.label}" }
            .onComplete { log += "complete" }
        q.progress(0, 31)
        q.advance(0)
        assertEquals(listOf("done:Mine"), log)
        q.advance(0)
        assertEquals(listOf("done:Mine"), log, "an objective already done does not fire again")
        q.progress(2, 1)
        assertEquals(listOf("done:Mine", "done:Bonus"), log, "optional objectives fire their own hook but never complete the quest")
        q.progress(1, 16)
        assertEquals(listOf("done:Mine", "done:Bonus", "done:Smelt", "complete"), log)
        q.progress(1, 15); q.progress(1, 16)
        assertEquals(2, log.count { it == "complete" }, "completing again after a regression fires again")
    }

    @Test
    fun claimRunsGrantsThenClaimHooks() {
        val q = quest()
        val log = ArrayList<String>()
        q.reward(BrassQuest.Reward.item("minecraft:bread", 3).onGrant { log += "grant" })
            .onClaim { log += "claim" }
            .onStatus { _, old, new -> log += "$old>$new" }
        q.status = Status.ACTIVE
        assertTrue(log.isEmpty(), "setting the same status fires nothing")
        q.status = Status.CLAIMED
        assertEquals(listOf("ACTIVE>CLAIMED", "grant", "claim"), log)
    }
}
