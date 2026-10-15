package net.swzo.brass.ui.demo.reference

import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.basicYConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.input.BrassSearchField
import net.swzo.brass.ui.kit.input.BrassTabSwitch
import net.swzo.brass.ui.kit.layout.BrassGrid
import net.swzo.brass.ui.kit.layout.BrassPages
import net.swzo.brass.ui.kit.layout.BrassSpacing
import net.swzo.brass.ui.kit.layout.BrassVBox
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.surface.BrassEmptyState
import net.swzo.brass.ui.kit.surface.BrassPanel
import net.swzo.brass.ui.kit.surface.BrassTable
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTag
import net.swzo.brass.ui.kit.text.BrassTagStyle

class ItemRegistryScreen(fit: Fit) : BrassReferenceScreen("Item Registry", "5 000 entries", fit) {

    override val id = "registry"

    data class Entry(val id: String, val name: String, val category: String, val stack: Int, val rarity: String)

    private val all: List<Entry> = generate()
    private var category = "All"

    private val search: BrassSearchField
    private val results = BrassPages()
    private val detailState = BrassPages()
    private lateinit var table: BrassTable<Entry>

    private val nameLabel = BrassLabel("", Colors.UI_TEXT_HOVER)
    private val idLabel = BrassLabel("", Colors.UI_TEXT_DARK)
    private val categoryTag = BrassTag("", BrassTagStyle.INFO)
    private val stackValue = BrassLabel("")
    private val rarityValue = BrassLabel("")

    init {
        val tabs = BrassTabSwitch(CATEGORIES) { i -> category = CATEGORIES[i]; refresh() }
        tabs.constrain {
            x = 0.pixels(alignOpposite = true); y = 0.pixels()
            width = basicWidthConstraint { tabs.contentWidth() }; height = TOOL_H.pixels()
        } childOf body

        search = BrassSearchField("Search items…") { refresh() }
        search.constrain {
            x = 0.pixels(); y = 0.pixels()
            width = basicWidthConstraint { c -> (c.parent.getWidth() - tabs.getWidth() - BrassSpacing.GAP).coerceAtLeast(0f) }
            height = TOOL_H.pixels()
        } childOf body

        results.page("results") { root ->
            table = BrassTable(
                listOf(
                    BrassTable.Column("Name", 2.2f, sortBy = { it.name }) { it.name },
                    BrassTable.Column("Category", 1.2f, sortBy = { it.category }) { it.category },
                    BrassTable.Column("Stack", 0.8f, sortBy = { it.stack }) { it.stack.toString() },
                ),
                all,
            ) { entry, _ -> show(entry) }
            table.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf root
        }
        results.page("empty") { root ->
            BrassEmptyState(BrassIcons.SEARCH, "No items match", "Try fewer letters or another category")
                .withAction("Clear search") { search.setQuery("") }
                .constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf root
        }

        val detail = BrassPanel("DETAILS", layout = BrassPanel.Layout.FREE)
        detailState.page("none") { root ->
            BrassEmptyState(title = "Nothing selected", detail = "Pick a row")
                .also { it.chrome = BrassChrome.NONE }
                .constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf root
        }
        detailState.page("item") { root ->
            val facts = BrassGrid(weights = listOf(1f, 1f), rowHeight = 10f, gapY = 3f, bleed = false)
                .add(BrassLabel("Stack", Colors.UI_TEXT_DARK), stackValue)
                .add(BrassLabel("Rarity", Colors.UI_TEXT_DARK), rarityValue)
            BrassVBox(gap = 4f, bleed = false).add(nameLabel, idLabel, categoryTag, facts)
                .constrain { width = 100.percent() } childOf root
            facts.constrain { width = 100.percent() }
            nameLabel.fitWidth()
            idLabel.fitWidth()
        }
        detailState.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf detail.content

        val top = TOOL_H + BrassSpacing.GAP + 1f
        results.constrain {
            x = 0.pixels()
            y = top.pixels()
            width = basicWidthConstraint { c -> if (wide()) c.parent.getWidth() - DETAIL_W - BrassSpacing.GAP else c.parent.getWidth() }
            height = basicHeightConstraint { c -> c.parent.getHeight() - top - if (wide()) 0f else DETAIL_H + BrassSpacing.GAP }
        } childOf body
        detail.constrain {
            x = basicXConstraint { c -> if (wide()) c.parent.getRight() - DETAIL_W else c.parent.getLeft() }
            y = basicYConstraint { c -> if (wide()) c.parent.getTop() + top else c.parent.getBottom() - DETAIL_H }
            width = basicWidthConstraint { c -> if (wide()) DETAIL_W else c.parent.getWidth() }
            height = basicHeightConstraint { c -> if (wide()) c.parent.getHeight() - top else DETAIL_H }
        } childOf body
    }

    private fun wide() = body.getWidth() >= WIDE_AT

    private fun refresh() {
        val base = if (category == "All") all else all.filter { it.category == category }
        val rows = if (search.text.isBlank()) base else search.filter(base) { it.name }
        table.setRows(rows)
        results.select(if (rows.isEmpty()) "empty" else "results")
        if (table.selected() == null) detailState.select("none")
    }

    private fun show(e: Entry) {
        nameLabel.text = e.name
        idLabel.text = e.id
        categoryTag.text = e.category.uppercase()
        stackValue.text = e.stack.toString()
        rarityValue.text = e.rarity
        detailState.select("item")
    }

    override fun scenario() = listOf(
        ReferenceStep("selected") { table.select(3) },
        ReferenceStep("sorted-desc") { table.sortBy(0); table.sortBy(0); table.select(0) },
        ReferenceStep("scrolled-end") { table.scrollToEnd() },
        ReferenceStep("no-results") { search.setQuery("zzzqx") },
        ReferenceStep("cleared") { search.setQuery("") },
        ReferenceStep("narrow") { table.select(1); squeeze(300f, 300f) },
    )

    private companion object {
        const val TOOL_H = 18f
        const val DETAIL_W = 128f
        const val DETAIL_H = 100f
        const val WIDE_AT = 380f
        val CATEGORIES = listOf("All", "Blocks", "Items", "Tools")

        fun generate(): List<Entry> {
            val adjectives = listOf("Polished", "Mossy", "Cracked", "Waxed", "Oxidized", "Chiseled", "Smooth", "Cut", "Gilded", "Weathered")
            val nouns = listOf("Andesite", "Copper", "Deepslate", "Basalt", "Quartz", "Tuff", "Blackstone", "Prismarine", "Calcite", "Granite")
            val forms = listOf("Block", "Stairs", "Slab", "Wall", "Ingot", "Nugget", "Pickaxe", "Shovel", "Lantern", "Button")
            val rarities = listOf("Common", "Common", "Uncommon", "Rare", "Epic")
            val out = ArrayList<Entry>(5000)
            var i = 0
            while (out.size < 5000) {
                val a = adjectives[i % adjectives.size]
                val n = nouns[(i / adjectives.size) % nouns.size]
                val f = forms[(i / (adjectives.size * nouns.size)) % forms.size]
                val variant = i / 1000
                val cat = when (f) { "Ingot", "Nugget", "Button" -> "Items"; "Pickaxe", "Shovel" -> "Tools"; else -> "Blocks" }
                val name = "$a $n $f" + if (variant > 0) " Mk$variant" else ""
                out += Entry(
                    "brass:" + name.lowercase().replace(' ', '_'),
                    name, cat, if (cat == "Tools") 1 else if (f == "Nugget") 64 else 16 * (1 + i % 4),
                    rarities[(i * 7) % rarities.size],
                )
                i++
            }
            return out
        }
    }
}
