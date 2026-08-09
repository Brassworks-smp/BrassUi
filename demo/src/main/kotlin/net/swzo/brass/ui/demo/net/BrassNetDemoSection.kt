package net.swzo.brass.ui.demo.net

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.component.BrassText
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.layout.BrassHBox
import net.swzo.brass.ui.kit.net.BrassNet
import net.swzo.brass.ui.kit.net.actionButton
import net.swzo.brass.ui.kit.surface.BrassPopup
import net.swzo.brass.ui.kit.text.BrassCodeView
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTextInput

/**
 * The gallery's Networking section: the same screen on the desktop and in game, powered by the same
 * action set. Everything here is a real round trip - rename, toggle, reset, spam, whisper and the live
 * ticker all execute server-side (or in-process on the desktop) and push state back through the same
 * path a host mod would use.
 */
object BrassNetDemoSection {

    fun open(root: UIComponent, bounds: FloatArray) {
        val unsubs = ArrayList<() -> Unit>()

        val stateName = BrassNet.state("brassui.demo.team.name", String::class.java)
        val stateActor = BrassNet.state("brassui.demo.team.actor", String::class.java)
        val stateOnline = BrassNet.state("brassui.demo.team.online", Boolean::class.java)
        val stateRuns = BrassNet.state("brassui.demo.slow.runs", Int::class.java)
        val stateResets = BrassNet.state("brassui.demo.reset.count", Int::class.java)
        val stateSpamDisabled = BrassNet.state("brassui.demo.spam.disabled", Boolean::class.java)
        val stateLive = BrassNet.state("brassui.demo.live", Int::class.java)
        val stateTicker = BrassNet.state("brassui.demo.live.running", Boolean::class.java)
        val stateWhisper = BrassNet.state("brassui.demo.whisper", String::class.java)

        val nameLabel = BrassLabel("—", Colors.BRASS_400)
        val actorLabel = BrassLabel("—", Colors.UI_TEXT_DARK)
        val onlineLabel = BrassLabel("offline", Colors.UI_TEXT_DARK)
        val runsLabel = BrassLabel("slow tasks: 0 · resets: 0", Colors.UI_TEXT_DARK)
        val spamLabel = BrassLabel("spam: enabled", Colors.UI_TEXT_DARK)
        val liveLabel = BrassLabel("live: 0 (coalesced to 4/s)", Colors.UI_TEXT_DARK)
        val whisperLabel = BrassLabel("private ping: —", Colors.UI_TEXT_DARK)
        val auditLabel = BrassLabel("no actions yet", Colors.UI_TEXT_DARK)

        unsubs += stateName.onChange { nameLabel.text = it ?: "—" }
        unsubs += stateActor.onChange { actorLabel.text = "last changed by ${it ?: "nobody"}" }
        unsubs += stateOnline.onChange {
            onlineLabel.text = if (it == true) "online" else "offline"
            onlineLabel.tint = if (it == true) Colors.PATINA_400 else Colors.UI_TEXT_DARK
        }
        unsubs += stateRuns.onChange { runs ->
            val resets = stateResets.current ?: 0
            runsLabel.text = "slow tasks: ${runs ?: 0} · resets: $resets"
        }
        unsubs += stateResets.onChange { resets ->
            val runs = stateRuns.current ?: 0
            runsLabel.text = "slow tasks: $runs · resets: ${resets ?: 0}"
        }
        unsubs += stateSpamDisabled.onChange {
            spamLabel.text = if (it == true) "spam: disabled (clicks fail with action.disabled)" else "spam: enabled"
            spamLabel.tint = if (it == true) Colors.WARN else Colors.UI_TEXT_DARK
        }
        unsubs += stateLive.onChange { liveLabel.text = "live: ${it ?: 0} (coalesced to 4/s)" }
        unsubs += stateTicker.onChange {
            liveLabel.text = if (it == true) "live: ${stateLive.current ?: 0} · running (coalesced to 4/s)" else "live: ${stateLive.current ?: 0} (stopped)"
        }
        unsubs += stateWhisper.onChange { whisperLabel.text = "private ping: ${it ?: "—"}" }

        val status = UIContainer().constrain { height = 84.pixels() }
        nameLabel.constrain { x = 0.pixels(); y = 0.pixels() } childOf status
        actorLabel.constrain { x = 0.pixels(); y = 14.pixels() } childOf status
        onlineLabel.constrain { x = 0.pixels(); y = 28.pixels() } childOf status
        runsLabel.constrain { x = 0.pixels(); y = 42.pixels() } childOf status
        spamLabel.constrain { x = 0.pixels(); y = 56.pixels() } childOf status
        liveLabel.constrain { x = 0.pixels(); y = 70.pixels() } childOf status

        val field = BrassTextInput(initial = "Brassworks", placeholder = "Team name")
        field.constrain { width = 150.pixels(); height = 20.pixels() }
        val rename = actionButton("Rename", BrassNetDemoActions.renameTeam) {
            BrassNetDemoActions.RenameTeam("main", field.text)
        }
        val nameRow = BrassHBox(gap = 8f).add(field, rename)

        // Optimistic: the label flips immediately; the server's state push reconciles it, and a failed
        // action reverts to the last authoritative value.
        val toggle = actionButton(
            "Toggle online",
            BrassNetDemoActions.toggleOnline,
            optimistic = { stateOnline.current?.let { stateOnline.optimistic(!it) } },
            onResult = { result -> if (!result.ok) stateOnline.revert() },
        ) { }
        val slow = actionButton("Slow task (async, 1.2s)", BrassNetDemoActions.slowTask) { }
        val spam = actionButton("Spam (3/5s)", BrassNetDemoActions.spamPing) { }
        val spamToggle = actionButton("Spam on/off", BrassNetDemoActions.toggleSpamEnabled) { }
        val startTicker = actionButton("Start live ticker", BrassNetDemoActions.startTicker) { }
        val stopTicker = actionButton("Stop ticker", BrassNetDemoActions.stopTicker) { }
        val reset = actionButton("Reset (op 2+)", BrassNetDemoActions.adminReset, accent = BrassAccent.DANGER) { }

        val whisperField = BrassTextInput(initial = "", placeholder = "Private message…")
        whisperField.constrain { width = 150.pixels(); height = 20.pixels() }
        val whisper = actionButton("Whisper to self", BrassNetDemoActions.whisper) {
            BrassNetDemoActions.Whisper(whisperField.text)
        }
        val whisperRow = BrassHBox(gap = 8f).add(whisperField, whisper)

        BrassNet.onActionExecuted = { actionId, _, result, durationMs ->
            BrassNet.onUiThread {
                auditLabel.text = "last action: $actionId → ${if (result.ok) "ok" else result.message} (${durationMs}ms)"
            }
        }

        val code = BrassCodeView(SNIPPET, language = "kotlin")
        code.constrain { height = 100.pixels() }

        BrassPopup("Networking", onClose = {
            BrassNet.onActionExecuted = null
            unsubs.forEach { it() }
        })
            .addField("Server state (pushed, snapshotted & coalesced)", status)
            .addField("Rename the team", nameRow)
            .addButtons(toggle, slow, spam)
            .addButtons(spamToggle, startTicker, stopTicker)
            .addButtons(reset)
            .addField("Targeted push (only you receive it)", whisperRow)
            .addField("Audit hook (fires for every action)", auditLabel)
            .addField("Declared inline, runs on the server", code)
            .addField(
                "Current transport",
                BrassText.label("${BrassNet.transportName ?: "unbound"} · ${BrassNet.identity ?: "—"}"),
            )
            .show(root, bounds[0], bounds[1], bounds[2], bounds[3])
    }

    private val SNIPPET = """
        @BrassActionSet
        object TeamActions {
            val rename = brassAction<RenameTeam>(
                id = "brassui.team.rename",
                permission = "brassui.team.rename",
                minOpLevel = 3,
            ) { ctx, input ->
                Teams.get(input.teamId)?.name = input.name
                ctx.publish("brassui.team.name", input.name)
                ok()
            }
        }

        // in the screen:
        actionButton("Rename", TeamActions.rename) {
            RenameTeam(teamId, field.text)
        }
    """.trimIndent()
}
