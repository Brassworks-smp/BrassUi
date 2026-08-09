package net.swzo.brass.ui.demo.net

import net.swzo.brass.ui.kit.net.BrassActions
import net.swzo.brass.ui.kit.net.BrassActionSet
import net.swzo.brass.ui.kit.net.BrassMessages
import net.swzo.brass.ui.kit.net.BrassNet
import net.swzo.brass.ui.kit.net.BrassRateLimit
import net.swzo.brass.ui.kit.net.brassAction
import net.swzo.brass.ui.kit.net.brassAsyncAction
import net.swzo.brass.ui.kit.net.brassValue
import net.swzo.brass.ui.kit.net.ok
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@BrassActionSet
object BrassNetDemoActions : BrassActions {

    data class RenameTeam(val teamId: String, val name: String)
    data class Whisper(val text: String)

    init {
        // The demo's own failure codes get their display text here, the same way a host mod would
        // register translations for its action errors.
        BrassMessages.register("demo.name.blank", "The team name can't be empty")
        BrassMessages.register("demo.whisper.blank", "Say something first")
    }

    // A shared daemon scheduler so async handlers (the slow task, the live ticker) never block the
    // server main thread or the desktop render thread.
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "brassui-demo").apply { isDaemon = true }
    }


    val teamName = brassValue("brassui.demo.team.name", "Brassworks")
    val lastActor = brassValue("brassui.demo.team.actor", "nobody")
    val online = brassValue("brassui.demo.team.online", false)
    val slowRuns = brassValue("brassui.demo.slow.runs", 0)
    val resets = brassValue("brassui.demo.reset.count", 0)
    val spamDisabled = brassValue("brassui.demo.spam.disabled", false)

    // Coalesced: the ticker updates 20x/s, but only the latest value is broadcast once per 250ms.
    val liveTicks = brassValue("brassui.demo.live", 0, coalesceMillis = 250)
    val tickerRunning = brassValue("brassui.demo.live.running", false)

    private var tickTask: ScheduledFuture<*>? = null


    val renameTeam = brassAction<RenameTeam>(
        id = "brassui.demo.team.rename",
        permission = "brassui.demo.team.rename",
        minOpLevel = 0,
        validate = { if (it.name.isBlank()) "demo.name.blank" else null },
    ) { ctx, input ->
        teamName.value = input.name.trim()
        lastActor.value = ctx.playerId ?: "local"
        ok()
    }

    val toggleOnline = brassAction<Unit>(
        id = "brassui.demo.team.toggle",
        permission = "brassui.demo.team.toggle",
        minOpLevel = 0,
    ) { _, _ ->
        online.value = !online.value
        ok(online.value)
    }

    val adminReset = brassAction<Unit>(
        id = "brassui.demo.team.reset",
        permission = "brassui.demo.team.reset",
        minOpLevel = 2,
    ) { ctx, _ ->
        teamName.value = "Brassworks"
        lastActor.value = ctx.playerId ?: "local"
        resets.value = resets.value + 1
        ok(resets.value)
    }

    /**
     * An **async** handler: the work runs on the demo scheduler after 1.2s, so the server main thread
     * (or the desktop render thread) is never blocked, and the reply is sent when the future
     * completes. This is how real mods should do slow work.
     */
    val slowTask = brassAsyncAction<Unit>(
        id = "brassui.demo.slow",
        permission = "brassui.demo.slow",
        minOpLevel = 0,
    ) { _, _ ->
        CompletableFuture.supplyAsync(
            {
                slowRuns.value = slowRuns.value + 1
                ok("slow task finished")
            },
            CompletableFuture.delayedExecutor(1200, TimeUnit.MILLISECONDS, scheduler),
        )
    }

    val spamPing = brassAction<Unit>(
        id = "brassui.demo.spam",
        permission = "brassui.demo.spam",
        minOpLevel = 0,
        rateLimit = BrassRateLimit(max = 3, perSeconds = 5),
    ) { _, _ ->
        ok()
    }

    val toggleSpamEnabled = brassAction<Unit>(
        id = "brassui.demo.spam.toggle",
        permission = "brassui.demo.spam.toggle",
        minOpLevel = 0,
    ) { _, _ ->
        val nowDisabled = if (BrassNet.isDisabled("brassui.demo.spam")) {
            BrassNet.enable("brassui.demo.spam")
            false
        } else {
            BrassNet.disable("brassui.demo.spam")
            true
        }
        spamDisabled.value = nowDisabled
        ok(nowDisabled)
    }

    val startTicker = brassAction<Unit>(
        id = "brassui.demo.live.start",
        permission = "brassui.demo.live.start",
        minOpLevel = 0,
    ) { _, _ ->
        if (tickTask != null) return@brassAction ok(false)
        tickerRunning.value = true
        tickTask = scheduler.scheduleAtFixedRate(
            { liveTicks.value = liveTicks.value + 1 },
            0,
            50,
            TimeUnit.MILLISECONDS,
        )
        ok(true)
    }

    val stopTicker = brassAction<Unit>(
        id = "brassui.demo.live.stop",
        permission = "brassui.demo.live.stop",
        minOpLevel = 0,
    ) { _, _ ->
        tickTask?.cancel(false)
        tickTask = null
        tickerRunning.value = false
        ok()
    }

    val whisper = brassAction<Whisper>(
        id = "brassui.demo.whisper",
        permission = "brassui.demo.whisper",
        minOpLevel = 0,
        validate = { if (it.text.isBlank()) "demo.whisper.blank" else null },
    ) { ctx, input ->
        ctx.publishTo(ctx.playerId, "brassui.demo.whisper", input.text.trim())
        ok()
    }
}
