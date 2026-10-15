package net.swzo.brass.ui.demo.reference

import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.layout.BrassFlow
import net.swzo.brass.ui.kit.layout.BrassForm
import net.swzo.brass.ui.kit.layout.BrassPages
import net.swzo.brass.ui.kit.layout.BrassScrollArea
import net.swzo.brass.ui.kit.layout.BrassSectionHeader
import net.swzo.brass.ui.kit.layout.BrassSpacing
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.media.BrassPlayerHead
import net.swzo.brass.ui.kit.surface.BrassBarChart
import net.swzo.brass.ui.kit.surface.BrassEmptyState
import net.swzo.brass.ui.kit.surface.BrassListRow
import net.swzo.brass.ui.kit.surface.BrassModal
import net.swzo.brass.ui.kit.surface.BrassPanel
import net.swzo.brass.ui.kit.surface.BrassStat
import net.swzo.brass.ui.kit.surface.BrassTable
import net.swzo.brass.ui.kit.surface.BrassToast
import net.swzo.brass.ui.kit.text.BrassTagStyle
import net.swzo.brass.ui.kit.text.BrassWrappedLabel

class GuildManagerScreen(fit: Fit) : BrassReferenceScreen("Guild", "Ironforge", fit, maxWidth = 480f) {

    override val id = "guild"

    private val pages = BrassPages()
    private lateinit var members: BrassScrollArea
    private val memberRows = ArrayList<BrassListRow>()
    private val roleRows = ArrayList<BrassListRow>()

    init {
        pages.page("Members") { root -> buildMembers(root) }
        pages.page("Roles") { root -> buildRoles(root) }
        pages.page("Treasury") { root -> buildTreasury(root) }
        pages.page("Log") { root -> buildLog(root) }
        pages.page("Requests") { root -> buildRequests(root) }

        val tabs = pages.tabs()
        tabs.constrain {
            x = 0.pixels(); y = 0.pixels()
            width = basicWidthConstraint { c -> minOf(tabs.contentWidth(), c.parent.getWidth() - INVITE_W - BrassSpacing.GAP) }
            height = HEADER_H.pixels()
        } childOf body

        val invite = BrassButton("Invite", BrassAccent.BRASS) { BrassToast.show(background, "Invite link copied", BrassToast.Type.SUCCESS) }
        invite.constrain {
            x = 0.pixels(alignOpposite = true); y = 0.pixels()
            width = INVITE_W.pixels(); height = HEADER_H.pixels()
        } childOf body

        pages.constrain {
            x = 0.pixels(); y = (HEADER_H + BrassSpacing.GAP + 1f).pixels()
            width = 100.percent(); height = 100.percent() - (HEADER_H + BrassSpacing.GAP + 1f).pixels()
        } childOf body
    }

    private fun buildMembers(root: BrassPages.Page) {
        members = BrassScrollArea.list().constrain {
            x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent()
        } childOf root
        MEMBERS.forEachIndexed { i, (name, role, online) ->
            val row = BrassListRow(name, "$role · ${if (online) "online" else "seen ${i % 9 + 1}d ago"}") { picked ->
                BrassListRow.selectOnly(memberRows, picked)
            }
                .leading(BrassPlayerHead(name, size = 16f), 16f)
                .tag(if (online) "ONLINE" else null, BrassTagStyle.SUCCESS)
                .action(BrassIcons.CROWN, "Change role") { BrassToast.show(background, "Role menu for $name", BrassToast.Type.INFO) }
                .action(BrassIcons.CLOSE, "Kick", BrassAccent.DANGER) { confirmKick(name) }
            row.revealActions = true
            memberRows += row
            members.add(row)
        }
    }

    private fun confirmKick(name: String) {
        lateinit var modal: BrassModal
        modal = BrassModal("Kick $name?", 220f, 110f)
            .body { b ->
                BrassWrappedLabel("They lose their role and items in the guild vault. They can be invited again.", Colors.UI_TEXT)
                    .constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent() } childOf b
            }
            .footer(
                BrassButton("Cancel") { modal.dismiss() },
                BrassButton("Kick", BrassAccent.DANGER) {
                    modal.dismiss()
                    BrassToast.show(background, "$name was kicked", BrassToast.Type.ERROR)
                },
            )
        openModal = modal
        modal.show(background)
    }

    private var openModal: BrassModal? = null

    private fun buildRoles(root: BrassPages.Page) {
        val list = BrassScrollArea.list().constrain {
            x = 0.pixels(); y = 0.pixels(); width = ROLE_LIST_W.pixels(); height = 100.percent()
        } childOf root
        ROLES.forEach { (role, count) ->
            val row = BrassListRow(role, icon = if (role == "Leader") BrassIcons.CROWN else BrassIcons.USER) { picked -> BrassListRow.selectOnly(roleRows, picked) }
            row.meta = count.toString()
            roleRows += row
            list.add(row)
        }
        roleRows.first().selected = true

        val perms = BrassPanel("PERMISSIONS", layout = BrassPanel.Layout.FREE).constrain {
            x = basicXConstraint { list.getRight() + BrassSpacing.GAP }; y = 0.pixels()
            width = basicWidthConstraint { c -> (c.parent.getRight() - list.getRight() - BrassSpacing.GAP).coerceAtLeast(0f) }
            height = 100.percent()
        } childOf root
        val form = BrassForm().constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf perms.content
        PERMISSIONS.forEachIndexed { i, p -> form.addToggleRow(p, i % 3 != 2) }
    }

    private fun buildTreasury(root: BrassPages.Page) {
        val scroll = BrassScrollArea().constrain {
            x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent()
        } childOf root
        val stats = BrassFlow(gapX = BrassSpacing.GAP, gapY = BrassSpacing.GAP, itemHeight = BrassStat.HEIGHT, stretch = true)
        stats.add(BrassStat("Balance", "12.4k", note = "emeralds"), 110f)
        stats.add(BrassStat("This week", "+840").tag("UP 7%", BrassTagStyle.SUCCESS), 110f)
        stats.add(BrassStat("Upkeep", "-310", note = "per week"), 110f)
        stats.constrain { height = basicHeightConstraint { stats.contentHeight() } }

        val bars = BrassBarChart()
        bars.setBars(listOf(
            BrassBarChart.Bar("mon", 120f), BrassBarChart.Bar("tue", 340f), BrassBarChart.Bar("wed", 90f),
            BrassBarChart.Bar("thu", 210f), BrassBarChart.Bar("fri", 80f),
        ))
        bars.values = BrassBarChart.Values.ABOVE
        bars.constrain { height = 74.pixels() }

        scroll.add(
            BrassSectionHeader("Overview"),
            stats,
            BrassSectionHeader("Deposits this week"),
            bars,
        )
    }

    private fun buildLog(root: BrassPages.Page) {
        data class Entry(val time: String, val who: String, val what: String)
        val actions = listOf("deposited 64 emeralds", "promoted Alex to Officer", "claimed chunk -12, 40",
            "withdrew 12 iron", "changed the MOTD", "kicked Herobrine")
        val entries = List(80) { i ->
            Entry("${(i * 7) % 24}:${(i * 13 % 60).toString().padStart(2, '0')}", MEMBERS[i % MEMBERS.size].first, actions[i % actions.size])
        }
        BrassTable(
            listOf(
                BrassTable.Column<Entry>("Time", 0.6f) { it.time },
                BrassTable.Column<Entry>("Member", 1.2f, sortBy = { it.who }) { it.who },
                BrassTable.Column<Entry>("Action", 2.4f) { it.what },
            ),
            entries,
        ).constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf root
    }

    private fun buildRequests(root: BrassPages.Page) {
        BrassEmptyState(BrassIcons.MAIL, "No pending requests", "Players who ask to join show up here")
            .withAction("Copy invite link") { BrassToast.show(background, "Invite link copied", BrassToast.Type.SUCCESS) }
            .constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf root
    }

    override fun scenario() = listOf(
        ReferenceStep("member-hover") { d -> memberRows[1].selected = true; d.hover(memberRows[2]) },
        ReferenceStep("members-end") { members.body.scrollToBottom(false) },
        ReferenceStep("roles") { pages.select("Roles") },
        ReferenceStep("treasury") { pages.select("Treasury") },
        ReferenceStep("log") { pages.select("Log") },
        ReferenceStep("requests") { pages.select("Requests") },
        ReferenceStep("members-again") { pages.select("Members") },
        ReferenceStep("kick-confirm", frames = 36) { confirmKick(MEMBERS[3].first) },
        ReferenceStep("narrow") { openModal?.dismiss(); squeeze(300f, 260f) },
        ReferenceStep("narrow-roles") { pages.select("Roles") },
    )

    private companion object {
        const val HEADER_H = 18f
        const val ROLE_LIST_W = 112f
        const val INVITE_W = 56f

        val MEMBERS = listOf(
            Triple("Steve", "Leader", true), Triple("Alex", "Officer", true),
            Triple("Notch", "Officer", false), Triple("jeb_", "Member", true),
            Triple("Dinnerbone", "Member", false), Triple("Grumm", "Member", false),
            Triple("AVeryLongPlayerNameThatGoesOnAndOn", "Recruit", true),
            Triple("Kingbdogz", "Member", false), Triple("Marc_IRL", "Member", true),
            Triple("LadyAgnes", "Recruit", false), Triple("slicedlime", "Member", false),
            Triple("Herobrine", "Recruit", false),
        )
        val ROLES = listOf("Leader" to 1, "Officer" to 2, "Member" to 6, "Recruit" to 3, "Guest" to 0)
        val PERMISSIONS = listOf("Invite players", "Kick members", "Withdraw from vault", "Claim land",
            "Edit the MOTD", "Start wars", "Manage roles", "Use guild chat")
    }
}
