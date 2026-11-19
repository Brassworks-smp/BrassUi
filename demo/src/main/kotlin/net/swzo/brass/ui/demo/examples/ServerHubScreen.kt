package net.swzo.brass.ui.demo.examples

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.demo.reference.BrassReferenceScreen
import net.swzo.brass.ui.demo.reference.ReferenceStep
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassTree
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.layout.BrassFlow
import net.swzo.brass.ui.kit.layout.BrassForm
import net.swzo.brass.ui.kit.layout.BrassGrid
import net.swzo.brass.ui.kit.layout.BrassMasterDetail
import net.swzo.brass.ui.kit.layout.BrassPages
import net.swzo.brass.ui.kit.layout.BrassScrollArea
import net.swzo.brass.ui.kit.layout.BrassSectionHeader
import net.swzo.brass.ui.kit.layout.BrassSidebar
import net.swzo.brass.ui.kit.layout.BrassSpacing
import net.swzo.brass.ui.kit.layout.BrassVBox
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.media.BrassPlayerHead
import net.swzo.brass.ui.kit.quest.BrassQuest
import net.swzo.brass.ui.kit.quest.BrassQuestCard
import net.swzo.brass.ui.kit.surface.BrassChart
import net.swzo.brass.ui.kit.surface.BrassEmptyState
import net.swzo.brass.ui.kit.surface.BrassListRow
import net.swzo.brass.ui.kit.surface.BrassModal
import net.swzo.brass.ui.kit.surface.BrassPanel
import net.swzo.brass.ui.kit.surface.BrassStat
import net.swzo.brass.ui.kit.surface.BrassToast
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTagStyle
import net.swzo.brass.ui.kit.text.BrassWrappedLabel

class ServerHubScreen(fit: Fit) : BrassReferenceScreen("Server hub", "Brassworks SMP", fit, maxWidth = 500f, maxHeight = 320f) {

    override val id = "hub"

    private val pages = BrassPages(slide = BrassPages.Slide.VERTICAL)
    private val side = BrassSidebar(pages)
    private val players = BrassMasterDetail(detailWidth = { 150f }, minMaster = 130f)
    private val playerRows = ArrayList<BrassListRow>()
    private val mailTabs = BrassPages(slide = BrassPages.Slide.HORIZONTAL)
    private var mailStrip: UIComponent? = null

    private val detailName = BrassLabel("", Colors.UI_TEXT_HOVER)
    private val detailRole = BrassLabel("", Colors.UI_TEXT_DARK)
    private val detailHead = UIContainer()
    private var picked = PLAYERS.first()
    private var unread = 3
    private var eventStarted = false

    init {
        val home = pages.page("Home") { buildHome(it) }
        val playersPage = pages.page("Players") { buildPlayers(it) }
        val mail = pages.page("Mail") { buildMail(it) }
        val claims = pages.page("Claims") { buildClaims(it) }
        val events = pages.page("Events") { buildEvents(it) }

        side.item("home", "Home", BrassIcons.HOME, home)
            .header("Community")
            .item("players", "Players", BrassIcons.USERS, playersPage, meta = { "${PLAYERS.count { it.online }}/20" }, accent = false)
            .item("mail", "Mail", BrassIcons.MAIL, mail, meta = { unread.takeIf { it > 0 }?.toString() })
            .header("World")
            .item("claims", "Claims", BrassIcons.FLAG, claims, meta = { "3/5" }, accent = false, progress = { 0.6f })
            .item("events", "Events", BrassIcons.STAR, events, meta = { "1" }, hidden = true)
        side.footer(BrassButton("Start event", BrassAccent.BRASS) { startEvent() }, 18f)

        side.constrain {
            x = 0.pixels(); y = 0.pixels()
            width = basicWidthConstraint { sideW() }
            height = 100.percent()
        } childOf body
        pages.constrain {
            x = basicXConstraint { side.getRight() + sideGap() }; y = 0.pixels()
            width = basicWidthConstraint { (body.getWidth() - sideW() - sideGap()).coerceAtLeast(0f) }
            height = 100.percent()
        } childOf body
        side.select("home")
        pages.selection.onChange { players.reset() }
    }

    private fun narrow() = body.getWidth() < 400f

    private fun sideW() = if (narrow()) 24f else (body.getWidth() * 0.24f).coerceIn(104f, 132f)

    private fun sideGap() = if (narrow()) BrassSpacing.TIGHT else BrassSpacing.GAP

    private fun <T : UIComponent> fill(c: T): T = c.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() }

    private fun buildHome(root: UIContainer) {
        val scroll = fill(BrassScrollArea()) childOf root
        val stats = BrassFlow(gapX = BrassSpacing.GAP, gapY = BrassSpacing.GAP, itemHeight = BrassStat.HEIGHT, stretch = true)
        stats.add(BrassStat("Online", "8", note = "of 20"), 96f)
        stats.add(BrassStat("TPS", "19.8").tag("OK", BrassTagStyle.SUCCESS), 96f)
        stats.add(BrassStat("Claims", "142", note = "+6 today"), 96f)
        stats.constrain { height = basicHeightConstraint { stats.contentHeight() } }

        val chart = BrassChart(window = 48, min = 0f)
        val online = chart.series("Players", Colors.UI_ACCENT, filled = true)
        repeat(48) { i -> chart.push(online, (6 + 5 * kotlin.math.sin(i / 6.0) + (i % 5) * 0.6).toFloat()) }
        chart.constrain { height = 70.pixels() }

        val feed = BrassVBox(gap = 1f, bleed = false)
        ACTIVITY.forEach { (who, what, ago) ->
            feed.add(BrassListRow(who, what).leading(BrassPlayerHead(who, size = 16f), 16f).also { it.meta = ago; it.constrain { width = 100.percent() } })
        }
        scroll.add(
            BrassSectionHeader("Today"), stats,
            BrassSectionHeader("Players online"), chart,
            BrassSectionHeader("Recent").trailing(BrassButton("View all") { side.select("players") }, 52f), feed,
        )
    }

    private fun buildPlayers(root: UIContainer) {
        fill(players) childOf root
        players.backLabel = { "Players" }
        val list = fill(BrassScrollArea.list()) childOf players.master
        PLAYERS.forEach { p ->
            val row = BrassListRow(p.name, p.role) { r ->
                BrassListRow.selectOnly(playerRows, r)
                show(p)
                players.open()
            }
                .leading(BrassPlayerHead(p.name, size = 16f), 16f)
                .tag(if (p.online) "ONLINE" else null, BrassTagStyle.SUCCESS)
            playerRows += row
            list.add(row)
        }
        playerRows.first().selected = true

        val who = UIContainer().constrain { width = 100.percent(); height = 26.pixels() }
        detailHead.constrain { x = 0.pixels(); y = 0.pixels(); width = 24.pixels(); height = 24.pixels() } childOf who
        detailName.constrain { x = 30.pixels(); y = 3.pixels() } childOf who
        detailRole.constrain { x = 30.pixels(); y = 14.pixels() } childOf who
        val facts = BrassGrid(listOf(1f, 1f), rowHeight = 11f).constrain { width = 100.percent() }
        facts.add(
            BrassLabel("Playtime", Colors.UI_TEXT_DARK), BrassLabel("41h"),
            BrassLabel("Claims", Colors.UI_TEXT_DARK), BrassLabel("3"),
            BrassLabel("Joined", Colors.UI_TEXT_DARK), BrassLabel("March"),
        )
        fill(BrassPanel()).also { it childOf players.detail }.add(
            who,
            facts,
            BrassButton("Message") { BrassToast.show(background, "Chat opened with ${picked.name}", BrassToast.Type.INFO) }.constrain { height = 18.pixels() },
            BrassButton("Kick", BrassAccent.DANGER) { confirmKick(picked.name) }.constrain { height = 18.pixels() },
        )
        show(PLAYERS.first())
    }

    private fun show(p: Player) {
        picked = p
        detailName.text = p.name
        detailRole.text = p.role
        detailHead.clearChildren()
        BrassPlayerHead(p.name, size = 24f).constrain { width = 24.pixels(); height = 24.pixels() } childOf detailHead
    }

    private var modal: BrassModal? = null

    private fun confirmKick(name: String) {
        lateinit var m: BrassModal
        m = BrassModal("Kick $name?", 210f, 96f)
            .body { b -> BrassWrappedLabel("They can join again straight away.").constrain { width = 100.percent() } childOf b }
            .footer(
                BrassButton("Cancel") { m.dismiss() },
                BrassButton("Kick", BrassAccent.DANGER) { m.dismiss(); BrassToast.show(background, "$name was kicked", BrassToast.Type.ERROR) },
            )
        modal = m
        m.show(background)
    }

    private fun buildMail(root: UIContainer) {
        mailTabs.page("Inbox") { page ->
            val list = fill(BrassScrollArea.list()) childOf page
            MAIL.forEach { (from, subject) ->
                val row = BrassListRow(subject, from, BrassIcons.MAIL) {}
                    .action(BrassIcons.ARCHIVE, "Archive") { unread = (unread - 1).coerceAtLeast(0) }
                    .action(BrassIcons.TRASH, "Delete", BrassAccent.DANGER) {}
                row.revealActions = true
                list.add(row)
            }
        }
        mailTabs.page("Sent") { page ->
            val list = fill(BrassScrollArea.list()) childOf page
            list.add(BrassListRow("Re: shop prices", "to Alex", BrassIcons.SEND), BrassListRow("Event next week", "to everyone", BrassIcons.SEND))
        }
        mailTabs.page("Archive") { page ->
            fill(BrassEmptyState(BrassIcons.ARCHIVE, "Nothing archived", "Archived mail lands here")) childOf page
        }
        val tabs = mailTabs.tabs(equalWidths = true)
        mailStrip = tabs
        tabs.constrain { x = 0.pixels(); y = 1.pixels(); width = 100.percent(); height = 18.pixels() } childOf root
        mailTabs.constrain { x = 0.pixels(); y = 26.pixels(); width = 100.percent(); height = 100.percent() - 26.pixels() } childOf root
    }

    private fun buildClaims(root: UIContainer) {
        fill(BrassForm()).also { it childOf root }
            .addSlider("Claim radius", 8f, 64f, 24f, step = 8f, format = { "${it.toInt()} blocks" })
            .addToggleRow("Allow visitors", true)
            .addToggleRow("Mob griefing", false)
            .addToggleRow("Fire spread", false)
            .addDropdown("Who can build", listOf("me" to "Only me", "team" to "My team", "all" to "Everyone"), "team")
    }

    private fun buildEvents(root: UIContainer) {
        val scroll = fill(BrassScrollArea(gap = 0f)) childOf root
        val quest = BrassQuest("harvest", "Harvest festival", "Bring crops to the spawn barn before Sunday.", BrassIcons.LEAF)
            .objective("Wheat", target = 64, progress = 40)
            .objective("Pumpkins", target = 16, progress = 9)
            .objective("Golden carrot", optional = true)
            .reward(BrassQuest.Reward.xp(500))
            .reward(BrassQuest.Reward.coins(120))
        scroll.add(BrassQuestCard(quest).also { it.constrain { width = 100.percent() } })
    }

    private fun startEvent() {
        if (eventStarted) return
        eventStarted = true
        side.reveal("events", open = false)
        BrassToast.show(background, "Harvest festival started", BrassToast.Type.SUCCESS)
    }

    private fun row(id: String): UIComponent {
        val rows = BrassTree.descendantsOfType(side, UIComponent::class.java).filter { it.javaClass.simpleName == "Row" }
        return rows[listOf("home", "players", "mail", "claims", "events").indexOf(id)]
    }

    private fun keyNamed(label: String) = BrassTree.descendantsOfType(players.detail, BrassButton::class.java).first { it.label == label }

    override fun scenario() = listOf(
        ReferenceStep("home") { },
        ReferenceStep("players", frames = 40) { d -> d.click(row("players"), 0.4f) },
        ReferenceStep("pick", frames = 30) { d -> d.click(playerRows[2]) },
        ReferenceStep("kick", frames = 40) { d -> d.click(keyNamed("Kick")) },
        ReferenceStep("mail", frames = 40) { d -> modal?.dismiss(); d.click(row("mail"), 0.4f) },
        ReferenceStep("sent", frames = 40) { d -> mailStrip?.let { d.click(it, 0.5f, 0.5f) } },
        ReferenceStep("archive", frames = 40) { d -> mailStrip?.let { d.click(it, 0.83f, 0.5f) } },
        ReferenceStep("event", frames = 70) { d -> d.clickAt(side.getLeft() + 40f, side.getBottom() - 10f) },
        ReferenceStep("events", frames = 50) { d -> d.click(row("events"), 0.4f) },
        ReferenceStep("narrow", frames = 40) { side.select("players"); squeeze(320f, 240f) },
        ReferenceStep("narrow-pick", frames = 40) { d -> d.click(playerRows[1]) },
        ReferenceStep("narrow-back", frames = 40) { players.close() },
    )

    private class Player(val name: String, val role: String, val online: Boolean)

    private companion object {
        val PLAYERS = listOf(
            Player("Steve", "Admin", true), Player("Alex", "Builder", true), Player("jeb_", "Builder", true),
            Player("Notch", "Member", false), Player("Dinnerbone", "Member", true), Player("Grumm", "Member", false),
            Player("Kingbdogz", "Moderator", true), Player("Marc_IRL", "Member", true), Player("LadyAgnes", "Member", false),
        )
        val ACTIVITY = listOf(
            Triple("Alex", "claimed a chunk near spawn", "4m"),
            Triple("jeb_", "sold 64 wool at the market", "12m"),
            Triple("Steve", "restarted the nether hub", "1h"),
        )
        val MAIL = listOf(
            "Alex" to "Shop prices", "Kingbdogz" to "Report: griefing at -120, 64",
            "jeb_" to "Sheep farm is done", "Server" to "Backup finished",
        )
    }
}
