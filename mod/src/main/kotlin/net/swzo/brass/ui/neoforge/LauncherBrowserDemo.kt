@file:Suppress("unused")
package net.swzo.brass.ui.neoforge

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassStats
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.input.BrassSquareButton
import net.swzo.brass.ui.kit.input.BrassTabSwitch
import net.swzo.brass.ui.kit.layout.BrassScrollbar
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.media.BrassImage
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.surface.BrassPopup
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTag
import net.swzo.brass.ui.kit.text.BrassTextInput
import java.awt.Color
import net.swzo.brass.ui.kit.base.BrassChrome

/**
 * A **static mock** of the BrassWorks launcher's "Add content" browser, rebuilt in `brassui`.
 * Its only job is to put the toolkit under a real screen's worth of load: a modal with a custom body,
 * two segmented tab rows, a search field, a scrolling list of composed rows, item rendering, tags,
 * tooltips and a footer — the shapes an actual application screen is made of, rather than one widget
 * per row in a demo popup.
 * **Nothing here is wired up.** No search, no install. The tabs swap which list is shown and that is
 * the extent of it; the install keys do nothing.
 * The data *is* real, though: every entry below — title, author, description, download count, last
 * update — was read from Modrinth's public API, and the icons are the projects' actual artwork, pulled
 * over the network at runtime by [BrassImage] and cached per URL.
 * ### Why these particular mods
 * They are the well-known ones whose Modrinth icon happens to be a **PNG**. Modrinth serves most icons
 * as WebP, which the JDK's ImageIO has no reader for (see [BrassImage]), so a Sodium or a Create would sit on
 * the widget's failed placeholder and demo nothing. Both tabs are Modrinth data — CurseForge's API
 * needs a key, so the second tab is a second Modrinth list wearing that label.
 */
object LauncherBrowserDemo {

    private const val ROW_H = 40f
    private const val ROW_GAP = 4f
    private const val ICON_PAD = 4f

    private class Entry(
        val title: String,
        val author: String,
        val description: String,
        val downloads: Long,
        val updated: String,
        val categories: List<String>,
        val icon: String,
        val installed: Boolean = false,
    )

    private fun fmtDownloads(n: Long): String = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
        n >= 1_000 -> "%.1fK".format(n / 1_000.0)
        else -> n.toString()
    }

    private val MODRINTH = listOf(
        Entry(
            "Fabric API", "modmuss50",
            "Lightweight and modular API providing common hooks and intercompatibility measures",
            209854356L, "2026-07-17", listOf("library"),
            "https://cdn.modrinth.com/data/P7dR8mSH/icon.png",
            installed = true,
        ),
        Entry(
            "Mod Menu", "Prospector",
            "Adds a mod menu to view the list of mods you have installed",
            122375810L, "2026-07-09", listOf("utility"),
            "https://cdn.modrinth.com/data/mOgUt4GM/5a20ed1450a0e1e79a1fe04e61bb4e5878bf1d20.png",
        ),
        Entry(
            "AppleSkin", "squeek502",
            "Food/hunger-related HUD improvements",
            76470649L, "2026-06-18", listOf("food", "utility"),
            "https://cdn.modrinth.com/data/EsAfCjCV/icon.png",
        ),
        Entry(
            "Reese's Sodium Options", "FlashyReese",
            "Alternative options menu for Sodium",
            69317741L, "2026-07-11", listOf("utility"),
            "https://cdn.modrinth.com/data/Bh37bMuy/icon.png",
        ),
        Entry(
            "VeinMiner", "Miraculixx",
            "Mine a whole vein by breaking a single block",
            63544290L, "2026-07-11", listOf("equipment", "game-mechanics"),
            "https://cdn.modrinth.com/data/OhduvhIc/5ea1f538e66ee4d4e5e571ad952cba0e06e0bd5c.png",
        ),
        Entry(
            "Continuity", "Pepper_Bell",
            "Efficient connected textures",
            62641602L, "2026-06-16", listOf("decoration", "utility"),
            "https://cdn.modrinth.com/data/1IjD5062/icon.png",
            installed = true,
        ),
        Entry(
            "Simple Voice Chat", "henkelmax",
            "A working voice chat in Minecraft",
            57761691L, "2026-07-19", listOf("adventure", "social"),
            "https://cdn.modrinth.com/data/9eGKb6K1/icon.png",
        ),
        Entry(
            "No Chat Reports", "Aizistral",
            "Makes chat unreportable where possible",
            49812147L, "2026-07-04", listOf("social", "utility"),
            "https://cdn.modrinth.com/data/qQyHxfxd/icon.png",
        ),
        Entry(
            "Sound Physics Remastered", "henkelmax",
            "Realistic sound attenuation, reverb and absorption",
            45956226L, "2026-06-18", listOf("adventure", "utility"),
            "https://cdn.modrinth.com/data/qyVF9oeo/798fbfae58ec95ad51f3e1d522b43227306c326c.png",
        ),
        Entry(
            "Chat Heads", "dzwdz",
            "See who you are chatting with",
            41905619L, "2026-06-13", listOf("decoration", "social"),
            "https://cdn.modrinth.com/data/Wb5oqrBJ/icon.png",
        ),
    )

    private val CURSEFORGE = listOf(
        Entry(
            "Bookshelf", "Darkhax",
            "An open source library for other mods",
            40072247L, "2026-07-08", listOf("library", "utility"),
            "https://cdn.modrinth.com/data/uy4Cnpcm/002e73d916fb63c9c85e212ac47fde4ef9c4a65f.png",
        ),
        Entry(
            "Moonlight Lib", "MehVahdJukaar",
            "Dynamic data packs, villager activities and map markers",
            34585073L, "2026-07-13", listOf("library"),
            "https://cdn.modrinth.com/data/twkfQtEc/icon.png",
            installed = true,
        ),
        Entry(
            "Mod Menu", "Prospector",
            "Adds a mod menu to view the list of mods you have installed",
            122375810L, "2026-07-09", listOf("utility"),
            "https://cdn.modrinth.com/data/mOgUt4GM/5a20ed1450a0e1e79a1fe04e61bb4e5878bf1d20.png",
        ),
        Entry(
            "AppleSkin", "squeek502",
            "Food/hunger-related HUD improvements",
            76470649L, "2026-06-18", listOf("food", "utility"),
            "https://cdn.modrinth.com/data/EsAfCjCV/icon.png",
        ),
        Entry(
            "Simple Voice Chat", "henkelmax",
            "A working voice chat in Minecraft",
            57761691L, "2026-07-19", listOf("adventure", "social"),
            "https://cdn.modrinth.com/data/9eGKb6K1/icon.png",
        ),
        Entry(
            "Continuity", "Pepper_Bell",
            "Efficient connected textures",
            62641602L, "2026-06-16", listOf("decoration", "utility"),
            "https://cdn.modrinth.com/data/1IjD5062/icon.png",
        ),
    )

    fun open(screenRoot: UIComponent) {
        // A modal with a custom body: the launcher's browser is not a stack of labelled form rows, so
        // the popup's form scroller is turned off and the layout is built directly into `content`.
        val popup = BrassPopup(
            "Add content",
            modal = true,
            showHeader = true,
            showCloseButton = true,
            scrollingBody = false,
        )
        Body().constrain {
            x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent()
        } childOf popup.content

        popup.showModal(screenRoot, 560f, 340f)
    }

    private class Body : UIContainer() {

        private val list: UIContainer
        private var rowCount = 0

        init {
            // Sized to its own tabs rather than a guessed pixel width — see BrassTabSwitch.contentWidth.
            val sourceTabs = BrassTabSwitch(listOf("Modrinth", "CurseForge"), onChange = { showSource(it) })
            sourceTabs.constrain {
                x = 0.pixels(); y = 0.pixels()
                width = basicWidthConstraint { sourceTabs.contentWidth() }; height = 16.pixels()
            } childOf this

            BrassTextInput("", "Search Modrinth")
                .constrain {
                    x = basicXConstraint { sourceTabs.getRight() + 8f }
                    y = 0.pixels()
                    width = basicWidthConstraint { c ->
                        (c.parent.getRight() - sourceTabs.getRight() - 8f).coerceAtLeast(40f)
                    }
                    height = 16.pixels()
                } childOf this

            val typeTabs = BrassTabSwitch(listOf("Mods", "Resource Packs", "Shaders"))
            typeTabs.constrain {
                x = 0.pixels(); y = 22.pixels()
                width = basicWidthConstraint { typeTabs.contentWidth() }; height = 16.pixels()
            } childOf this

            val footer = BrassLabel("Showing results for 1.21.1  ·  NeoForge", Colors.UI_TEXT_DARK)
                .also { it.entranceEnabled = false }
                .constrain { x = 0.pixels(); y = 0.pixels(true) } childOf this

            val scroll = ScrollComponent().constrain {
                x = 0.pixels(); y = 44.pixels()
                width = 100.percent() - (BrassScrollbar.WIDTH + 4f).pixels()
                height = basicHeightConstraint { c ->
                    (footer.getTop() - 6f - (c.parent.getTop() + 44f)).coerceAtLeast(0f)
                }
            } childOf this
            BrassScrollbar.attach(this, scroll)

            list = UIContainer().constrain {
                x = BrassWidget.BLEED_X.pixels(); y = 0.pixels()
                width = 100.percent() - (BrassWidget.BLEED_X * 2).pixels()
                height = basicHeightConstraint { rowCount * (ROW_H + ROW_GAP) }
            } childOf scroll

            showSource(0)
        }

        private fun showSource(index: Int) {
            val curseforge = index == 1
            val shown = if (curseforge) CURSEFORGE else MODRINTH
            list.clearChildren()
            rowCount = shown.size
            shown.forEachIndexed { i, e ->
                Row(e, curseforge).constrain {
                    x = 0.pixels()
                    y = if (i == 0) 0.pixels() else SiblingConstraint(ROW_GAP)
                    width = 100.percent(); height = ROW_H.pixels()
                } childOf list
            }
        }

    }

    private class Row(private val entry: Entry, curseforge: Boolean) : BrassWidget(BrassAccent.DEFAULT) {

        init {
            chrome = BrassChrome.NONE
            clickable = true

            // Fetched over the network and cached per URL — the row shows a shimmering skeleton until
            // it lands, which is the point of putting this screen in the showcase at all. Sized to fill
            // the card's full height (less the padding) rather than a small fixed square, so the icon is
            // the anchor of the row.
            val icon = ROW_H - ICON_PAD * 2
            BrassImage(entry.icon).constrain {
                x = ICON_PAD.pixels(); y = ICON_PAD.pixels()
                width = icon.pixels(); height = icon.pixels()
            } childOf this

            if (!entry.installed) {
                BrassSquareButton(
                    BrassIcons.PLUS,
                    if (curseforge) BrassAccent.DANGER else BrassAccent.BRASS,
                ) {}.also {
                    // A content square button (unlike the window's own title-bar controls) fades in with
                    // the rest of the row when the browser opens.
                    BrassTooltip.attach(it, "Install", "${entry.title} by ${entry.author}")
                }.constrain {
                    x = 6.pixels(true); y = 6.pixels()
                    width = 16.pixels(); height = 16.pixels()
                } childOf this
            }

            BrassTooltip.attach(this, entry.title, entry.description)
        }

        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
            val x1 = x.toFloat(); val y1 = y.toFloat()
            val x2 = (x + w).toFloat(); val y2 = (y + h).toFloat()
            val hot = hoveredState && active

            // flat card: a wash fill and a 1-px outline that lights brass on hover
            fill(m, x1, y1, x2, y2, if (hot) ROW_BG_HOVER else ROW_BG)
            BrassCard.border(m, x1, y1, x2, y2, if (hot) Colors.UI_ACCENT_BORDER else Colors.UI_INNER_BORDER)

            // Text clears the icon: its inset, its size, and a small gap.
            val textInset = ICON_PAD + (ROW_H - ICON_PAD * 2) + 6f
            val textX = x + textInset
            val avail = w - textInset - 26f

            // title, then the author beside it, then an "installed" tag
            val title = BrassFont.fit(this, entry.title, avail * 0.6f)
            BrassFont.draw(m, this, title, textX, y + 5f, if (hot) Colors.UI_ACCENT_BRIGHT else Colors.UI_TEXT, false)
            var cx = textX + BrassFont.width(this, title) + 5f

            val by = "by ${entry.author}"
            BrassFont.draw(m, this, by, cx, y + 5f, Colors.UI_TEXT_DARK, false)
            cx += BrassFont.width(this, by) + 5f

            if (entry.installed) {
                BrassTag.drawPill(m, this, cx, y + 5f, "installed", BrassTag.INFO)
            }

            // description
            val desc = BrassFont.fit(this, entry.description, avail)
            BrassFont.draw(m, this, desc, textX, y + 16f, Colors.UI_TEXT_DARK, false)

            // downloads · date · category pills
            val meta = "${fmtDownloads(entry.downloads)} downloads  ·  ${entry.updated}"
            BrassFont.draw(m, this, meta, textX, y + 27f, Colors.UI_TEXT_DARK, false)
            var px = textX + BrassFont.width(this, meta) + 6f
            for (c in entry.categories) {
                val pw = BrassTag.measure(this, c)
                if (px + pw > x + w - 26f) break
                px += BrassTag.drawPill(m, this, px, y + 27f, c, BrassTag.MUTED) + 3f
            }
        }

        private fun fill(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) {
            BrassStats.quad()
            UIBlock.drawBlock(m, c, x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble())
        }

        private companion object {
            val ROW_BG: Color = Color(255, 255, 255, 8)
            val ROW_BG_HOVER: Color = Color(
                Colors.UI_ACCENT.red, Colors.UI_ACCENT.green, Colors.UI_ACCENT.blue, 14,
            )
        }
    }
}
