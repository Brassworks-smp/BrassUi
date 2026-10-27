package net.swzo.brass.ui.demo.reference

import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.quest.BrassQuest
import net.swzo.brass.ui.kit.quest.BrassQuest.Status
import net.swzo.brass.ui.kit.quest.BrassQuestCard
import net.swzo.brass.ui.kit.quest.BrassQuestJournal
import net.swzo.brass.ui.kit.surface.BrassToast

/**
 * The quest book as the Brassworks design asks for it, built on [BrassQuestJournal]:
 * - **Missions** - tasks from NPCs, players and the server (plus seasonal ones), filtered by status/source.
 * - Quest lines as trees: **Getting Started** (the one-time tutorial thread), **Production**, **Farming**,
 *   **Seasonal**; each ends in a milestone tied to an advancement.
 * - **World Story** (lore) starts hidden and is revealed when the first artifact is found - here by the
 *   "Find artifact" key; in the mod, by the server (and `onReveal` plays the sound).
 */
class QuestJournalScreen(fit: Fit) : BrassReferenceScreen("Quest Journal", "Ironforge", fit, maxWidth = 520f) {

    override val id = "quests"

    private val missions = QuestSamples.all()
    private val book = BrassQuestJournal()
    private val lines = QuestBook()
    private val firstNight = lines.firstNight()

    init {
        book.missions("missions", "Missions", BrassIcons.SCROLL) { missions }
            .questLine("start", "Getting Started", BrassIcons.HOME) { lines.gettingStarted(it, firstNight) }
            .questLine("production", "Production", BrassIcons.HAMMER) { lines.production(it) }
            .questLine("farming", "Farming", BrassIcons.LEAF) { lines.farming(it) }
            .questLine("seasonal", "Seasonal", BrassIcons.SNOWFLAKE) { lines.seasonal(it) }
            .questLine("story", "World Story", BrassIcons.BOOK, hidden = true) { lines.worldStory(it) }
        // Stands in for the server event (the first artifact found) that reveals the lore line.
        val find = BrassButton("Find artifact", BrassAccent.BRASS) { book.reveal("story") }
        find.icon = BrassIcons.GEM
        book.sidebarFooter(find, FIND_H)
        book.onPage = { lastPage = it }
        book.onReveal = { id -> if (id == "story") { find.disable("Already found"); BrassToast.show(background, "A new chapter: World Story", BrassToast.Type.SUCCESS) } }
        book.card.onAction = { kind, q ->
            if (kind != BrassQuestCard.Kind.CLAIM) BrassToast.show(background, "${q.title}: ${kind.name.lowercase()}", BrassToast.Type.INFO)
        }
        // The quest hooks are where game logic goes: grant the advancement a milestone carries, and so on.
        (missions + lines.all).forEach { q ->
            q.onClaim { BrassToast.show(background, if (it.milestone) "Milestone: ${it.title}" else "${it.title} claimed", BrassToast.Type.SUCCESS) }
        }
        book.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf body
    }

    override fun scenario() = listOf(
        ReferenceStep("overview-hover") { d -> d.hover(book) },
        // Untracking (no status change, so no hook) must still drop the quest from the Overview's Tracking.
        ReferenceStep("untrack", frames = 30) { missions.first { it.id == "iron-age" }.tracked = false },
        ReferenceStep("untrack-check") {
            val q = missions.first { it.id == "iron-age" }
            val listed = net.swzo.brass.ui.kit.base.BrassTree.descendantsOfType(window, BrassQuestCard::class.java)
                .any { it.style == BrassQuestCard.Style.TRACKER && it.quest === q && net.swzo.brass.ui.kit.base.BrassTree.isAttached(it) }
            println("CLICK-TEST overview untrack: ${if (listed) "FAILED (still under Tracking)" else "OK"}")
            q.tracked = true
        },
        ReferenceStep("missions", frames = 40) { d -> d.unhover(); book.open("missions") },
        // The source menu opens down over the list; it was drawn under the rows and took no clicks.
        ReferenceStep("filter-open", frames = 30) {
            net.swzo.brass.ui.kit.base.BrassTree.descendantsOfType(window, net.swzo.brass.ui.kit.input.BrassDropdown::class.java)
                .firstOrNull { net.swzo.brass.ui.kit.base.BrassTree.isAttached(it) && it.getWidth() > 0f }?.let { dd ->
                    window.mouseClick((dd.getLeft() + dd.getRight()) / 2.0, dd.getTop() + 9.0, 0); window.mouseRelease()
                }
        },
        ReferenceStep("filter-pick", frames = 30) {
            val row = net.swzo.brass.ui.kit.base.BrassTree.descendantsOfType(window, BrassButton::class.java)
                .firstOrNull { it.label == "Server" && it.getHeight() > 0f }
            if (row == null) println("CLICK-TEST filter: menu row not found") else click(row)
        },
        ReferenceStep("filter-check") {
            val sources = net.swzo.brass.ui.kit.base.BrassTree.descendantsOfType(window, BrassQuestCard::class.java)
                .filter { it.style == BrassQuestCard.Style.COMPACT && net.swzo.brass.ui.kit.base.BrassTree.isAttached(it) }
                // rows on pages not showing (the Overview's) are attached too - only the live page counts
                .filter { c -> net.swzo.brass.ui.kit.base.BrassTree.ancestors(c).none { it is net.swzo.brass.ui.kit.base.BrassInert && it.inert } }
                .map { it.quest.source }.distinct()
            println("CLICK-TEST filter -> Server: ${if (sources == listOf("Server")) "OK" else "FAILED (sources $sources)"}")
            net.swzo.brass.ui.kit.base.BrassTree.descendantsOfType(window, net.swzo.brass.ui.kit.input.BrassDropdown::class.java)
                .forEach { it.value = "*" }
        },
        ReferenceStep("missions-pick", frames = 40) { book.showQuest(missions.first { it.id == "wolves" }) },
        ReferenceStep("getting-started", frames = 90) { book.open("start") },
        ReferenceStep("milestone-reveal", frames = 14) { book.showQuest(firstNight) },
        ReferenceStep("milestone-settled", frames = 60) {},
        ReferenceStep("production", frames = 90) { book.open("production") },
        ReferenceStep("artifact-found", frames = 24) { book.reveal("story") },
        ReferenceStep("world-story", frames = 150) {},
        ReferenceStep("overview-again", frames = 150) { book.open("overview") },
        ReferenceStep("narrow") { squeeze(320f, 260f) },
        ReferenceStep("narrow-tree", frames = 90) { book.open("farming") },
        ReferenceStep("narrow-push", frames = 14) { book.tree("farming")?.quest("bakery")?.let { book.showQuest(it) } },
        ReferenceStep("narrow-detail", frames = 50) {},
        ReferenceStep("narrow-back", frames = 50) { book.closeDetail() },
        ReferenceStep("narrow-missions", frames = 60) { book.open("missions") },
        ReferenceStep("narrow-mission-pick", frames = 50) { missions.firstOrNull { it.id == "wolves" }?.let { book.showQuest(it) } },
        // Real clicks, not API calls: the sidebar once stopped taking clicks after a pushed card was
        // closed (an invisible full-size host sat over it). These steps print CLICK-TEST lines.
        ReferenceStep("click-back", frames = 50) { clickBack() },
        ReferenceStep("click-sidebar", frames = 40) { clickSidebar("farming", 4) },
        ReferenceStep("click-sidebar-again", frames = 40) { clickSidebar("missions", 1) },
    )

    private var lastPage = ""

    private fun click(c: gg.essential.elementa.UIComponent) {
        val x = (c.getLeft() + c.getRight()) / 2.0
        val y = (c.getTop() + c.getBottom()) / 2.0
        window.mouseClick(x, y, 0)
        window.mouseRelease()
    }

    private fun clickBack() {
        val back = net.swzo.brass.ui.kit.base.BrassTree.descendantsOfType(window, BrassButton::class.java)
            .firstOrNull { it.label == "Missions" && it.getHeight() > 0f }
        if (back == null) { println("CLICK-TEST back: no back key"); return }
        click(back)
        println("CLICK-TEST back pressed: detail closed = ${!book.detailShown}")
    }

    private fun clickSidebar(id: String, row: Int) {
        val rows = net.swzo.brass.ui.kit.base.BrassTree.descendantsOfType(window, net.swzo.brass.ui.kit.base.BrassWidget::class.java)
            .filter { it.javaClass.name.endsWith("BrassSidebar\$Row") }
        rows.getOrNull(row)?.let { click(it) }
        println("CLICK-TEST sidebar -> $id: ${if (lastPage == id) "OK" else "FAILED (on $lastPage)"}")
    }

    private companion object {
        const val FIND_H = 20f
    }
}

/** The quest lines of the demo book: one-time tutorial thread, production, farming, seasonal, lore. */
class QuestBook {

    private val made = ArrayList<BrassQuest>()

    /** Every quest-line quest made so far (the screen hooks them). */
    val all: List<BrassQuest> get() = made

    private fun q(
        id: String, title: String, icon: BrassIcons.Icon, item: String, status: Status,
        goal: String, target: Int = 1, done: Int = 0, summary: String? = null,
    ) = BrassQuest(id, title, summary, icon, status).objective(goal, target = target, progress = done)
        .apply { iconItem = item; made += this }

    fun firstNight() = q("first-night", "The First Night", BrassIcons.MOON, "minecraft:red_bed", Status.ACTIVE,
        "Survive until morning", summary = "Every newcomer's rite: make it to sunrise.").apply {
        milestone = true; category = "Tutorial"; giver = "Guide Ansel"
        advancement = "brassworks:tutorial/first_night"; advancementTitle = "Through the Dark"
        objective("Sleep in a bed or stay awake")
        reward(BrassQuest.Reward.xp(150))
        reward(BrassQuest.Reward.item("minecraft:torch", 16))
    }

    fun gettingStarted(t: net.swzo.brass.ui.kit.quest.BrassQuestTree, firstNight: BrassQuest) {
        t.node(q("punch", "Punch a Tree", BrassIcons.AXE, "minecraft:oak_log", Status.CLAIMED, "Collect logs", 8, 8))
        t.node(q("table", "Workbench", BrassIcons.HAMMER, "minecraft:crafting_table", Status.CLAIMED, "Craft a crafting table", 1, 1), listOf("punch"))
        t.node(q("tools", "Wooden Tools", BrassIcons.PICKAXE, "minecraft:wooden_pickaxe", Status.READY, "Craft a pickaxe and an axe", 2, 2), listOf("table"))
        t.node(q("shelter", "Shelter", BrassIcons.HOME, "minecraft:oak_door", Status.ACTIVE, "Build walls and a door", 2, 1), listOf("table"))
        t.node(firstNight, listOf("tools", "shelter"))
        t.select("first-night")
    }

    fun production(t: net.swzo.brass.ui.kit.quest.BrassQuestTree) {
        t.node(q("smelt", "Smelting", BrassIcons.FIRE, "minecraft:furnace", Status.CLAIMED, "Smelt any ore", 1, 1))
        t.node(BrassQuestCard.sample().also { made += it }, listOf("smelt"))
        t.node(q("blast", "Blast Furnace", BrassIcons.HAMMER, "minecraft:blast_furnace", Status.AVAILABLE, "Build a blast furnace"), listOf("iron-age"))
        t.node(q("press", "The Press", BrassIcons.WRENCH, "minecraft:piston", Status.AVAILABLE, "Build a mechanical press"), listOf("iron-age"))
        t.node(q("line", "Assembly Line", BrassIcons.LAYERS, "minecraft:hopper", Status.AVAILABLE, "Automate a recipe", 3), listOf("blast", "press"))
        t.node(q("industry", "Industrialist", BrassIcons.CHIP, "minecraft:observer", Status.AVAILABLE, "Run a factory for a day").apply {
            milestone = true; advancement = "brassworks:production/industrialist"; advancementTitle = "Industrialist"
            reward(BrassQuest.Reward.unlock("Factory blueprint", BrassIcons.FILE_TEXT))
        }, listOf("line"))
        t.select("iron-age")
    }

    fun farming(t: net.swzo.brass.ui.kit.quest.BrassQuestTree) {
        t.node(q("seeds", "Seeds", BrassIcons.LEAF, "minecraft:wheat_seeds", Status.CLAIMED, "Gather seeds", 16, 16))
        t.node(q("field", "Wheat Field", BrassIcons.SUN, "minecraft:wheat", Status.ACTIVE, "Harvest wheat", 64, 41), listOf("seeds"))
        t.node(q("pens", "Animal Pens", BrassIcons.HOME, "minecraft:oak_fence", Status.AVAILABLE, "Fence in animals", 4), listOf("seeds"))
        t.node(q("bakery", "Bakery", BrassIcons.BREAD, "minecraft:bread", Status.AVAILABLE, "Bake bread", 32), listOf("field"))
        t.node(q("breeding", "Breeding", BrassIcons.HEART, "minecraft:egg", Status.AVAILABLE, "Breed animals", 6), listOf("pens"))
        t.node(q("farmer", "Master Farmer", BrassIcons.FLOWER, "minecraft:golden_hoe", Status.AVAILABLE, "Feed the whole town").apply {
            milestone = true; advancement = "brassworks:farming/master"; advancementTitle = "Master Farmer"
        }, listOf("bakery", "breeding"))
        t.select("field")
    }

    fun seasonal(t: net.swzo.brass.ui.kit.quest.BrassQuestTree) {
        val now = System.currentTimeMillis()
        fun winter(b: BrassQuest) = b.apply { season = "Winterfest"; expiresAt = now + 6L * 24 * 3_600_000 }
        t.node(winter(q("snow", "First Snow", BrassIcons.SNOWFLAKE, "minecraft:snowball", Status.CLAIMED, "Collect snowballs", 32, 32)))
        t.node(winter(q("snowman", "Snow Friend", BrassIcons.SMILE, "minecraft:carved_pumpkin", Status.ACTIVE, "Build a snow golem")), listOf("snow"))
        t.node(winter(q("gifts", "Gift Exchange", BrassIcons.GIFT, "minecraft:chest", Status.AVAILABLE, "Give gifts to players", 5)), listOf("snow"))
        t.node(winter(q("festival", "Frost Festival", BrassIcons.SPARKLE, "minecraft:blue_ice", Status.AVAILABLE, "Light the festival tree").apply {
            milestone = true; advancement = "brassworks:seasonal/winterfest"; advancementTitle = "Winterfest Hero"
            reward(BrassQuest.Reward.unlock("Frostbound cape", BrassIcons.SHIELD, "Seasonal cosmetic"))
        }), listOf("snowman", "gifts"))
        t.select("snowman")
    }

    fun worldStory(t: net.swzo.brass.ui.kit.quest.BrassQuestTree) {
        t.node(q("artifact", "The First Artifact", BrassIcons.GEM, "minecraft:amethyst_shard", Status.CLAIMED, "Find a strange relic", 1, 1,
            "Something old hums beneath the hills."))
        t.node(q("echoes", "Echoes", BrassIcons.BOOK, "minecraft:written_book", Status.ACTIVE, "Read the inscriptions", 3, 1,
            "The relic speaks of a city that sank."), listOf("artifact"))
        t.node(q("city", "The Buried City", BrassIcons.PORTAL, "minecraft:sculk", Status.AVAILABLE, "Find the city's gate").apply {
            milestone = true; advancement = "brassworks:story/buried_city"; advancementTitle = "Into the Deep"
        }, listOf("echoes"))
        t.select("echoes")
    }
}

/** The sample quests the journal reference and the gallery's Quests section share. */
object QuestSamples {

    private const val MIN = 60_000L

    fun all(now: Long = System.currentTimeMillis()): List<BrassQuest> = listOf(
        BrassQuestCard.sample().apply { tracked = true; expiresAt = now + 134 * MIN },
        BrassQuest("long-night", "The Long Night", "Hold the walls until the blood moon sets.", BrassIcons.MOON).apply {
            milestone = true
            giver = "The Council"; category = "Milestone"; difficulty = 3
            iconItem = "minecraft:totem_of_undying"
            objective("Repair the north gate", progress = 1)
            objective("Arm the watch", target = 6, progress = 6)
            objective("Light the beacon fires", target = 4, progress = 2)
            objective("Defeat the siege captain")
            objective("Keep every villager alive", target = 12, progress = 12)
            objective("Survive until dawn")
            objective("Find the hidden armoury", optional = true)
            reward(BrassQuest.Reward.unlock("Warden of the Wall", BrassIcons.CROWN, "A title shown above your name"))
            reward(BrassQuest.Reward.item("minecraft:netherite_ingot", 2))
            reward(BrassQuest.Reward.xp(2000))
        },
        BrassQuest("wolves", "Pack Hunt", "Thin the wolf packs harassing the shepherds.", BrassIcons.SWORD).apply {
            giver = "Shepherd Ollie"; source = "NPC"; category = "Side quest"; difficulty = 1; iconItem = "minecraft:iron_sword"
            objective("Defeat wolves", target = 10, progress = 10)
            objective("Return the pelts")
            progress(1, 1)
            reward(BrassQuest.Reward.item("minecraft:leather", 6))
            reward(BrassQuest.Reward.coins(25))
        },
        BrassQuest("relay", "Beacon Relay", "Light the three relay beacons before the storm.", BrassIcons.BEACON).apply {
            giver = "Warden Kael"; source = "Server"; category = "Event"; difficulty = 3; iconItem = "minecraft:beacon"
            expiresAt = now - MIN
            objective("Light the north beacon", progress = 1)
            objective("Light the east beacon")
            objective("Light the south beacon")
            reward(BrassQuest.Reward.unlock("Stormcaller title", BrassIcons.CROWN, "A title shown above your name"))
        },
        BrassQuest("stone-quota", "Stone Quota", "The masons need stone by nightfall.", BrassIcons.PICKAXE).apply {
            giver = "Mason Dunn"; source = "NPC"; category = "Daily"; iconItem = "minecraft:cobblestone"
            expiresAt = now + 5 * 60 * MIN
            objective("Mine stone", target = 64, progress = 40)
            reward(BrassQuest.Reward.coins(15))
        },
        BrassQuest("depths", "Into the Depths", "Map the abandoned mineshaft under the old quarry.", BrassIcons.COMPASS, BrassQuest.Status.AVAILABLE).apply {
            giver = "Cartographer Wren"; source = "Player"; category = "Exploration"; difficulty = 2; iconItem = "minecraft:compass"
            objective("Descend the shaft", target = 40, progress = 12) { p, t -> "${p}m / ${t}m" }
            objective("Find the lost survey map")
            reward(BrassQuest.Reward.item("minecraft:filled_map"))
            reward(BrassQuest.Reward.xp(400))
        },
        BrassQuest("nether", "Gate of Embers", "Build and light a portal to the Nether.", BrassIcons.PORTAL, BrassQuest.Status.LOCKED).apply {
            milestone = true
            category = "Main story"; difficulty = 3; iconItem = "minecraft:obsidian"
            lockedReason = "Finish The Iron Age first"
            objective("Collect obsidian", target = 10)
            objective("Light the portal")
            reward(BrassQuest.Reward.item("minecraft:flint_and_steel"))
        },
        BrassQuest("lanterns", "Frost Lanterns", "Hang lanterns along the market road for the festival.", BrassIcons.LANTERN).apply {
            giver = "Festival board"; source = "Server"; season = "Winterfest"; iconItem = "minecraft:lantern"
            expiresAt = now + 3 * 24 * 60 * MIN
            objective("Hang lanterns", target = 20, progress = 7)
            reward(BrassQuest.Reward.coins(30, "festival tokens"))
        },
        BrassQuest("bread", "Daily Bread", "Bake bread for the tavern.", BrassIcons.BREAD, BrassQuest.Status.CLAIMED).apply {
            giver = "Innkeeper Bram"; source = "NPC"; category = "Daily"; iconItem = "minecraft:bread"
            objective("Bake bread", target = 12, progress = 12)
            reward(BrassQuest.Reward.coins(10))
        },
    )
}
