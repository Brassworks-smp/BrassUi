package net.swzo.brass.ui.demo

import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.basicYConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.BrassScreen
import net.swzo.brass.ui.BrassThemes
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.component.BrassText
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassState
import net.swzo.brass.ui.kit.base.BrassTree
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.base.proxiedBy
import net.swzo.brass.ui.kit.demo.BrassDemoBrowser
import net.swzo.brass.ui.kit.demo.BrassDemoStrip
import net.swzo.brass.ui.kit.demo.BrassDemos
import net.swzo.brass.ui.kit.dev.BrassDevMode
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassCheckbox
import net.swzo.brass.ui.kit.input.BrassChips
import net.swzo.brass.ui.kit.input.BrassColorPicker
import net.swzo.brass.ui.kit.input.BrassConfirmSlider
import net.swzo.brass.ui.kit.input.BrassIconButton
import net.swzo.brass.ui.kit.input.BrassInventoryGrid
import net.swzo.brass.ui.kit.input.BrassKeyChord
import net.swzo.brass.ui.kit.input.BrassKeybind
import net.swzo.brass.ui.kit.input.BrassNumberInput
import net.swzo.brass.ui.kit.input.BrassRange
import net.swzo.brass.ui.kit.input.BrassRangeSlider
import net.swzo.brass.ui.kit.input.BrassScrollSelector
import net.swzo.brass.ui.kit.input.BrassSearchField
import net.swzo.brass.ui.kit.input.BrassSlider
import net.swzo.brass.ui.kit.input.BrassSquareButton
import net.swzo.brass.ui.kit.input.BrassTabSwitch
import net.swzo.brass.ui.kit.layout.BrassDivider
import net.swzo.brass.ui.kit.layout.BrassFlow
import net.swzo.brass.ui.kit.layout.BrassHBox
import net.swzo.brass.ui.kit.layout.BrassLayout
import net.swzo.brass.ui.kit.layout.BrassPageWindow
import net.swzo.brass.ui.kit.layout.BrassPagination
import net.swzo.brass.ui.kit.layout.BrassScrollbar
import net.swzo.brass.ui.kit.layout.BrassSplitPane
import net.swzo.brass.ui.kit.layout.BrassVBox
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.media.BrassImage
import net.swzo.brass.ui.kit.media.BrassBlockPreview
import net.swzo.brass.ui.kit.media.BrassEffectIcon
import net.swzo.brass.ui.kit.media.BrassEntity
import net.swzo.brass.ui.kit.media.BrassItem
import net.swzo.brass.ui.kit.media.BrassPlayerHead
import net.swzo.brass.ui.kit.surface.BrassAccordion
import net.swzo.brass.ui.kit.surface.BrassBarChart
import net.swzo.brass.ui.kit.surface.BrassChart
import net.swzo.brass.ui.kit.surface.BrassChat
import net.swzo.brass.ui.kit.surface.BrassCommandPalette
import net.swzo.brass.ui.kit.surface.BrassContextMenu
import net.swzo.brass.ui.kit.surface.BrassEmptyState
import net.swzo.brass.ui.kit.surface.BrassLoading
import net.swzo.brass.ui.kit.surface.BrassPopup
import net.swzo.brass.ui.kit.surface.BrassProgressBar
import net.swzo.brass.ui.kit.surface.BrassTable
import net.swzo.brass.ui.kit.settings.BrassThemeCard
import net.swzo.brass.ui.kit.surface.BrassToast
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.surface.BrassTreeView
import net.swzo.brass.ui.kit.surface.BrassWindow
import net.swzo.brass.ui.kit.text.BrassCodeView
import net.swzo.brass.ui.kit.text.BrassFuzzy
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassMarkdown
import net.swzo.brass.ui.kit.text.BrassSyntax
import net.swzo.brass.ui.kit.text.BrassTag
import net.swzo.brass.ui.kit.text.BrassTagStyle
import net.swzo.brass.ui.kit.text.BrassTimeAgo
import org.lwjgl.glfw.GLFW
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.layout.BrassBreakpoint

/**
 * The widget gallery: one screen showing every widget in the toolkit, hosted both by the standalone
 * desktop app and by the in-game `/brassui` command.
 *
 * A [BrassWindow] with a nav rail whose sections open functional [BrassPopup] sub-windows, an overview
 * panel with live controls, toasts, a right-click context menu and a running progress bar — every
 * control here actually does something. Toggle the layout inspector with Ctrl+Shift+D.
 *
 * ### One gallery, two hosts
 *
 * This was two screens once — `BrassDesktopDemoScreen` and `BrassUiShowcaseScreen` — and they had
 * drifted exactly as far apart as you would expect: the same twenty-odd sections written twice, each
 * host missing widgets the other had. Neither was a trustworthy answer to "what does the toolkit look
 * like right now", which is the only question a gallery exists to answer.
 *
 * They merge cleanly because the toolkit is game-free by construction — the item and entity widgets
 * take **string ids**, so naming `"minecraft:allay"` costs nothing off-game. What genuinely differs
 * between the hosts goes through [BrassDemoHost], which is short enough to read in one sitting: how to
 * close, what to call itself, whether game content can be drawn at all, and the raw-canvas cards.
 *
 * @param host the platform showing this gallery.
 */
class BrassGalleryScreen(private val host: BrassDemoHost) : BrassScreen(backdropColor = host.backdrop) {

    /** The showcase capture (Ctrl+Shift+S, or the panel's context menu) lands under this name. */
    override val showcaseName: String get() = "gallery"

    private val navRows = ArrayList<BrassButton>()

    /** The nav sections, in order, so [openSectionOnStart] can reach one by name. */
    private val sections = ArrayList<Pair<String, () -> Unit>>()

    /**
     * A section to open as soon as the screen is up, from `-Dui.section=Chat`.
     *
     * For working on one widget: relaunching straight into its section beats clicking through the nav
     * every time, and it means a section that *crashes on open* fails the launch rather than waiting for
     * someone to happen to click it. Cleared after the first frame — it is a starting position, not a
     * mode.
     */
    private var pendingSection: String? = System.getProperty("ui.section")

    private var cascade = 0
    private var demoProgress = 0f
    private var downloadToast: BrassToast? = null

    /** Charts currently on screen, with their two series, fed from the demo tick. */
    private val liveCharts = ArrayList<Triple<BrassChart, BrassChart.Series, BrassChart.Series>>()
    private var chartTick = 0

    /** One row of the split pane's master list. */
    private data class Shard(val name: String, val players: Int, val tps: Float, val region: String)

    /** A small tree of made-up files, so the tree view has real nesting to show. */
    private class DemoNode(val name: String, val children: List<DemoNode> = emptyList()) {
        companion object {
            private fun file(name: String) = DemoNode(name)
            val ROOT = DemoNode(
                "world",
                listOf(
                    DemoNode("region", listOf(file("r.0.0.mca"), file("r.0.1.mca"), file("r.1.0.mca"))),
                    DemoNode(
                        "data",
                        listOf(
                            file("scoreboard.dat"),
                            DemoNode("advancements", listOf(file("steve.json"), file("alex.json"))),
                        ),
                    ),
                    DemoNode("playerdata", listOf(file("uuid-a.dat"), file("uuid-b.dat"))),
                    file("level.dat"),
                    file("session.lock"),
                ),
            )
        }
    }

    private val frame = BrassWindow(
        title = "brassui",
        subtitle = host.subtitle,
        onClose = { host.close() },
        // When the gallery *is* the app, the minimise / maximise / close keys have nothing to do that
        // the OS window chrome does not already do a level up. Floating in game, they are the only way
        // to move and dismiss it, so they stay.
        controls = !host.fillsSurface,
    )

    init {
        // Parse the markdown syntax rules up front (read from the toolkit's bundled resources).
        BrassSyntax.init()

        if (host.fillsSurface) {
            // Edge to edge. The OS window is already the frame, so an inset leaves a band of backdrop
            // around the UI that frames nothing.
            frame.constrain {
                x = 0.pixels(); y = 0.pixels()
                width = 100.percent()
                height = 100.percent()
            } childOf background
        } else {
            // Floating over the world, centred, with a margin so the backdrop reads as a dimmed game
            // behind a window rather than as the window's own padding.
            frame.constrain {
                x = CenterConstraint(); y = CenterConstraint()
                width = 100.percent() - 80.pixels()
                height = 100.percent() - 60.pixels()
            } childOf background
        }

        // ---- nav rail ------------------------------------------------------------------------
        val nav = UIContainer().constrain {
            x = 12.pixels(); y = 12.pixels()
            width = BrassBreakpoint.proportional(fraction = 0.26f, min = 110f, max = 170f)
            height = 100.percent() - 24.pixels()
        } childOf frame.content

        BrassText.label("SECTIONS").constrain { x = 4.pixels(); y = 2.pixels() } childOf nav

        val navFooter = BrassText.wrapped("right-click the panel for a context menu", Colors.UI_TEXT_DARK)
            .constrain { x = 4.pixels(); y = 0.pixels(true); width = 100.percent() - 8.pixels() } childOf nav

        val navList = ScrollComponent().constrain {
            x = 0.pixels(); y = 16.pixels()
            width = 100.percent()
            height = BrassLayout.fillAbove(navFooter, gap = 8f)
        } childOf nav

        val navInner = UIContainer().constrain {
            x = BrassWidget.BLEED_X.pixels(); y = BrassWidget.BLEED_TOP.pixels()
            width = 100.percent() - (BrassWidget.BLEED_X * 2 + 6f).pixels()
            height = BrassLayout.contentHeight()
        } childOf navList

        sections += buildList {
            add("Buttons" to { openButtons() })
            add("Inputs" to { openInputs() })
            add("More inputs" to { openMoreInputs() })
            add("Toggles" to { openToggles() })
            add("Feedback" to { openFeedback() })
            add("Chat" to { openChat() })
            add("Layout" to { openLayout() })
            add("Sections" to { openSections() })
            add("Data" to { openData() })
            add("Appearance" to { openAppearance() })
            add("Markdown" to { openMarkdown() })
            add("Table" to { openTable() })
            // The declared demos, played live. Deliberately first-class in the nav rather than
            // tucked away: this is the one section that is guaranteed to match the captured
            // documentation, because it is running the same scripts.
            add("Demos" to { openDemos() })
            // Only where the platform can actually draw game content. Off-game these widgets have
            // nothing to render, and a section of empty boxes demos nothing — it reads as a bug.
            if (host.gameWidgets) add("Items" to { openItems() })
        }
        sections.forEachIndexed { i, (label, open) ->
            val row = BrassButton(label, BrassAccent.DEFAULT) { selectNav(i); open() }.apply {
                centered = false
                chrome = BrassChrome.FLAT
            }
            row.constrain {
                x = 0.pixels()
                y = if (i == 0) 0.pixels() else SiblingConstraint(3f)
                width = 100.percent(); height = 20.pixels()
            } childOf navInner
            navRows.add(row)
        }

        // ---- overview panel ------------------------------------------------------------------
        val mainScroll = ScrollComponent().constrain {
            x = basicXConstraint { nav.getRight() + 12f }
            y = 12.pixels()
            width = basicWidthConstraint { c ->
                (c.parent.getRight() - nav.getRight() - 24f - (BrassScrollbar.WIDTH + 4f)).coerceAtLeast(0f)
            }
            height = 100.percent() - 24.pixels()
        } childOf frame.content
        BrassScrollbar.attach(frame.content, mainScroll)
        BrassDivider.between(frame.content, nav, mainScroll, span = frame.content, inset = 0f)

        val main = UIContainer().constrain {
            x = BrassWidget.BLEED_X.pixels(); y = BrassWidget.BLEED_TOP.pixels()
            width = 100.percent() - (BrassWidget.BLEED_X * 2 + 6f).pixels()
            height = BrassLayout.contentHeight()
        } childOf mainScroll

        val heading = BrassText.heading("Widget toolkit on the desktop")
            .constrain { x = 0.pixels(); y = 0.pixels() } childOf main
        BrassTag("standalone", BrassTag.SUCCESS).constrain {
            x = basicXConstraint { heading.getRight() + 6f }
            y = BrassTag.centeredOn(heading)
        } childOf main
        val blurb = BrassText.wrapped(
            "The same components the in-game UI uses, running outside Minecraft. Everything here works, " +
                "so click things.",
        ).constrain {
            x = 0.pixels(); y = basicYConstraint { heading.getBottom() + 6f }; width = 100.percent()
        } childOf main

        val quick = BrassFlow(itemHeight = 20f, stretch = true).constrain {
            x = 0.pixels(); y = basicYConstraint { blurb.getBottom() + 12f }; width = 100.percent()
        } childOf main
        quick.add(BrassButton("Buttons", BrassAccent.BRASS) { openButtons() }, 84f)
        quick.add(BrassButton("Inputs", BrassAccent.CALM) { openInputs() }, 84f)
        val gearBtn = BrassIconButton("Toggles", BrassIcons.GEAR, BrassAccent.DEFAULT) { openToggles() }
        quick.add(gearBtn, 96f)
        BrassTooltip.attach(gearBtn, "Toggles & checks", "Switches, checkboxes and their labels")
        quick.constrain { height = basicHeightConstraint { quick.contentHeight() } }

        val loadingLabel = BrassText.label("LOADING").constrain {
            x = 0.pixels(); y = basicYConstraint { quick.getBottom() + 12f }
        } childOf main
        val loader = BrassLoading().constrain {
            x = 0.pixels(); y = basicYConstraint { loadingLabel.getBottom() + 5f }
            width = basicWidthConstraint { c -> (c.parent.getWidth() * 0.62f).coerceAtMost(240f) }
            height = 14.pixels()
        } childOf main

        val bar = BrassProgressBar("Syncing shards").constrain {
            x = 0.pixels(); y = basicYConstraint { loader.getBottom() + 8f }
            width = basicWidthConstraint { c -> (c.parent.getWidth() * 0.62f).coerceAtMost(240f) }
            height = 14.pixels()
        } childOf main
        bar.addUpdateFunc { _, _ ->
            demoProgress = (demoProgress + 0.004f) % 1.25f
            bar.progress = demoProgress.coerceAtMost(1f)
            downloadToast?.let { t ->
                val next = (t.progress ?: 0f) + 0.006f
                t.progress = next.coerceAtMost(1f)
                if (next >= 1f) { t.close(); downloadToast = null }
            }

            // Feed any open chart. Charts registered by a closed popup are dropped rather than kept
            // alive by this list — the demo would otherwise leak one per visit to the Data section.
            chartTick++
            liveCharts.removeAll { (chart, tps, ping) ->
                if (!BrassTree.isAttached(chart)) return@removeAll true
                chart.push(tps, 20f - (kotlin.math.sin(chartTick / 9f) + 1f) * 0.7f)
                chart.push(ping, 8f + kotlin.math.sin(chartTick / 14f) * 3f)
                false
            }
        }

        val toastLabel = BrassText.label("TOASTS").constrain {
            x = 0.pixels(); y = basicYConstraint { bar.getBottom() + 12f }
        } childOf main
        val toasts = BrassFlow(itemHeight = 18f, stretch = true).constrain {
            x = 0.pixels(); y = basicYConstraint { toastLabel.getBottom() + 5f }; width = 100.percent()
        } childOf main
        toasts.add(BrassButton("Success", BrassAccent.NICE) { BrassToast.show(background, "Confirmed!", BrassToast.Type.SUCCESS) }, 74f)
        toasts.add(BrassButton("Info", BrassAccent.CALM) { BrassToast.show(background, "Heads up.", BrassToast.Type.INFO) }, 60f)
        toasts.add(BrassButton("Error", BrassAccent.DANGER) { BrassToast.show(background, "Something broke.", BrassToast.Type.ERROR) }, 66f)
        toasts.constrain { height = basicHeightConstraint { toasts.contentHeight() } }

        main.onMouseClick { e ->
            if (e.mouseButton == 1) {
                BrassContextMenu(
                    listOf(
                        BrassContextMenu.Item("New window") { BrassToast.show(background, "New!", BrassToast.Type.INFO) },
                        BrassContextMenu.Item("Duplicate") { BrassToast.show(background, "Duplicated.", BrassToast.Type.SUCCESS) },
                        BrassContextMenu.Item("Delete") { BrassToast.show(background, "Deleted.", BrassToast.Type.ERROR) },
                        // Isolates the whole gallery UI onto transparency, world dropped — the hero
                        // cut-out for a showcase page. Also on Ctrl+Shift+S. See BrassScreen.
                        BrassContextMenu.Item("Capture showcase") { captureShowcase() },
                    ),
                ).show(background, e.absoluteX, e.absoluteY)
            }
        }

        openSectionOnStart()
    }

    /**
     * Open the section named by `-Dui.section`, on the first frame rather than here.
     *
     * A section's popup expects a live screen — it measures against the window and scrolls itself — so
     * opening one from `init`, before this screen has been shown, is the same mistake as seeding a chat
     * before it is mounted. Waiting for the first update means the tree is up and laid out.
     */
    private fun openSectionOnStart() {
        if (pendingSection == null) return
        background.addUpdateFunc { _, _ ->
            val target = pendingSection ?: return@addUpdateFunc
            pendingSection = null
            val i = sections.indexOfFirst { it.first.equals(target, ignoreCase = true) }
            if (i >= 0) {
                selectNav(i)
                sections[i].second()
            }
        }
    }

    private fun selectNav(index: Int) {
        navRows.forEachIndexed { i, row ->
            row.selected = i == index
            row.accent = if (i == index) BrassAccent.BRASS else BrassAccent.DEFAULT
        }
    }

    // ---- popups ----------------------------------------------------------------------------------

    private fun popupBounds(w: Float, h: Float): FloatArray {
        val sw = frame.getWidth()
        val sh = frame.getHeight()
        val cw = w.coerceAtMost((sw * 0.82f).coerceAtLeast(160f))
        val ch = h.coerceAtMost((sh * 0.80f).coerceAtLeast(120f))
        val step = (cascade % 3) * 10f
        cascade++
        return floatArrayOf((sw - cw) / 2f + step, (sh - ch) / 2f + step, cw, ch)
    }

    private fun openButtons() {
        val b = popupBounds(320f, 300f)
        BrassPopup("Buttons & Actions")
            .addButtons(
                BrassButton("Play", BrassAccent.BRASS) { BrassToast.show(background, "Playing.", BrassToast.Type.SUCCESS) },
                BrassButton("Cancel", BrassAccent.DEFAULT) {},
                BrassButton("Delete", BrassAccent.DANGER) { BrassToast.show(background, "Deleted.", BrassToast.Type.ERROR) },
            )
            .addRow("Icons", 20,
                BrassIconButton("Open", BrassIcons.FOLDER, BrassAccent.DEFAULT) {},
                BrassIconButton("Find", BrassIcons.SEARCH, BrassAccent.CALM) {})
            .addRow("Square", 18,
                BrassSquareButton(BrassIcons.GEAR, BrassAccent.DEFAULT) {},
                BrassSquareButton(BrassIcons.HEART, BrassAccent.BRASS) {},
                BrassSquareButton(BrassIcons.PLUS, BrassAccent.DEFAULT) {},
                BrassSquareButton(BrassIcons.MINUS, BrassAccent.DEFAULT) {})
            .show(background, b[0], b[1], b[2], b[3])
    }

    private fun openInputs() {
        BrassPopup("Inputs", modal = true, showHeader = true, showCloseButton = true)
            .addTextField("Username", "swzo", "pick a name")
            .addDropdown("Difficulty", listOf(
                "easy" to "Easy", "normal" to "Normal", "hard" to "Hard", "nightmare" to "Nightmare",
            ), "normal")
            .addSlider("Volume", 0f, 1f, 0.75f, format = { "${(it * 100).toInt()}%" })
            .addRow("Game mode", 18, BrassTabSwitch(listOf("Survival", "Creative", "Spectator")))
            .addRow(
                "Sort by (equal widths)", 18,
                BrassTabSwitch(listOf("Relevance", "Downloads", "Updated"), equalWidths = true),
            )
            .addRow("Time", 20, BrassScrollSelector(listOf("Day", "Night", "Twilight")))
            .showModal(background, 340f, 340f)
    }

    private fun openToggles() {
        val checks = UIContainer().constrain { height = 14.pixels() }
        val fps = BrassCheckbox(true)
            .constrain { x = 0.pixels(); width = 13.pixels(); height = 13.pixels() } childOf checks
        val fpsLabel = BrassText.body("Show FPS")
            .constrain { x = 18.pixels(); y = CenterConstraint() } childOf checks
        fps.proxiedBy(fpsLabel)

        val motion = BrassCheckbox(false)
            .constrain { x = 120.pixels(); width = 13.pixels(); height = 13.pixels() } childOf checks
        val motionLabel = BrassText.body("Reduced motion")
            .constrain { x = 138.pixels(); y = CenterConstraint() } childOf checks
        motion.proxiedBy(motionLabel)

        lateinit var toggles: BrassPopup
        toggles = BrassPopup("Toggles & Checks", modal = true)
        toggles
            .addToggleRow("Snapshot builds", false)
            .addToggleRow("Auto-update", true)
            .addToggleRow("Discord presence", true)
            .addField("Checkboxes", checks)
            .addButtons(BrassButton("Done", BrassAccent.BRASS) { toggles.dismiss() })
            .showModal(background, 300f, 280f)
    }

    private fun openMarkdown() {
        val b = popupBounds(440f, 380f)
        val doc = BrassMarkdown(
            """
            # Markdown + syntax highlighting
            Fenced blocks are highlighted from `syntax_rules.json`, covering **214 languages**.

            ## Kotlin
            ```kotlin
            val play = BrassButton("Play", BrassAccent.BRASS) { start() }
            play.constrain { width = 84.pixels(); height = 20.pixels() }
            ```

            ## JSON
            ```json
            { "sprites": { "close": { "x": 0, "y": 0, "w": 12, "h": 12 } } }
            ```

            > Highlighting runs at parse time, not every frame.

            ---

            - Keywords take the accent green
            - Strings are warm brass, numbers patina teal

            Links go through the handler: [brassui](brassui).
            """.trimIndent(),
        ) { url -> BrassToast.show(background, url, BrassToast.Type.INFO) }

        BrassPopup("Markdown")
            .addField("Rendered document", doc.also {
                it.constrain { height = basicHeightConstraint { _ -> doc.contentHeight() } }
            })
            .show(background, b[0], b[1], b[2], b[3])
    }

    /**
     * The widgets that draw actual game content, shown only where [BrassDemoHost.gameWidgets] says the
     * platform can draw it.
     *
     * Note how little of this is game code: every widget here names its content with a **string id**
     * and the platform seam resolves it. That is the whole reason this section can live in a module
     * that does not link against Minecraft. The one exception is the raw-canvas row, which the host
     * supplies, because a demo of the drop-to-`GuiGraphics` escape hatch is game code by definition.
     */
    private fun openItems() {
        val b = popupBounds(360f, 380f)

        val ids = listOf(
            "minecraft:diamond_pickaxe", "minecraft:golden_apple", "minecraft:redstone",
            "minecraft:oak_log", "minecraft:ender_pearl", "minecraft:not_a_real_item",
        )
        val row = BrassFlow(itemHeight = 22f, stretch = false)
        ids.forEach { id ->
            val slot = BrassItem(id, count = if (id.endsWith("redstone")) 32 else 1) {
                BrassToast.show(background, id, BrassToast.Type.INFO)
            }
            row.add(slot, 22f)
        }
        row.constrain { height = basicHeightConstraint { row.contentHeight() } }

        // Living entities, lit through the platform's inventory-renderer path — all sit at the fixed
        // isometric three-quarter angle, scaled to fit their card like an item slot.
        val mobs = BrassFlow(itemHeight = 64f, stretch = false)
        mobs.add(BrassEntity("minecraft:allay"), 48f)
        mobs.add(BrassEntity("minecraft:zombie"), 48f)
        mobs.add(BrassEntity("minecraft:cow"), 48f)
        mobs.constrain { height = basicHeightConstraint { mobs.contentHeight() } }

        val who = host.playerName
        // A row, not a BrassFlow: the flow pins every child to its own itemHeight, which is exactly
        // what a head must not have — a square card in a 20-px-tall box grows a band of padding above
        // and below the face. BrassHBox constrains only x and y and lets the head keep its own size.
        val faces = BrassHBox(gap = 4f).add(
            *listOf(who, "Notch", "jeb_").map { BrassPlayerHead(it) }.toTypedArray(),
        )

        // The same widget at five sizes, which is one constructor argument each: the card is square by
        // construction, so the face fills it exactly at every size with only its 1-px border showing.
        val scaled = BrassHBox(gap = 4f).add(
            *listOf(12f, 18f, 24f, 32f, 48f).map { BrassPlayerHead(who, size = it) }.toTypedArray(),
        )

        val blocks = BrassFlow(itemHeight = 44f, stretch = false)
        blocks.add(BrassBlockPreview("minecraft:blast_furnace"), 40f)
        blocks.add(BrassBlockPreview("minecraft:oak_door"), 40f)
        // Spinning, to show the angle is live rather than a baked sprite.
        blocks.add(BrassBlockPreview("minecraft:lectern", spin = 40f), 40f)
        blocks.constrain { height = basicHeightConstraint { blocks.contentHeight() } }

        val effects = BrassFlow(itemHeight = 22f, stretch = false)
        listOf("minecraft:speed" to 0.75f, "minecraft:strength" to 0.4f, "minecraft:poison" to 0.08f)
            .forEachIndexed { i, (id, left) ->
                effects.add(
                    BrassEffectIcon(id, amplifier = i).also { it.setRemaining(left, 1f) },
                    20f,
                )
            }
        effects.constrain { height = basicHeightConstraint { effects.contentHeight() } }

        // Two grids sharing one cursor, so every gesture can be tried across the boundary: left and
        // right click, shift-click between them, and left/right drag spreading a held stack.
        val grid = BrassInventoryGrid(columns = 9, rows = 2)
        listOf(
            "minecraft:diamond" to 12, "minecraft:iron_ingot" to 64, "minecraft:bread" to 3,
            "minecraft:torch" to 48, "minecraft:oak_planks" to 32, "minecraft:ender_pearl" to 16,
        ).forEachIndexed { i, (id, n) -> grid.setSlot(i * 2, BrassInventoryGrid.Slot(id, n)) }
        grid.constrain {
            width = basicWidthConstraint { grid.contentWidth() }
            height = basicHeightConstraint { grid.contentHeight() }
        }

        val hotbar = BrassInventoryGrid(columns = 9, rows = 1)
        hotbar.linkTo(grid)
        hotbar.constrain {
            width = basicWidthConstraint { hotbar.contentWidth() }
            height = basicHeightConstraint { hotbar.contentHeight() }
        }

        val popup = BrassPopup("Items, mobs & inventory")
            .addField("Item slots", row)
            .addField("Entities", mobs)
            .addField("Player heads", faces)
            .addField("…at 12, 18, 24, 32 and 48 px — one `size` argument each", scaled)
            .addField("Block models", blocks)
            .addField("Status effects", effects)
            .addField("Inventory — click, right-click, shift-click, drag", grid)
            .addField("…linked hotbar (shift-click moves between them)", hotbar)

        // Whatever raw-drawing cards the host cared to supply, in a flow sized to them.
        val raw = host.rawCanvases()
        if (raw.isNotEmpty()) {
            val canvases = BrassFlow(itemHeight = 64f, stretch = false)
            raw.forEach { canvases.add(it.component, it.height) }
            canvases.constrain { height = basicHeightConstraint { canvases.contentHeight() } }
            popup.addField("Canvas (raw platform drawing)", canvases)
        }

        popup.show(background, b[0], b[1], b[2], b[3])
    }

    /**
     * Every widget's own demo, playing on a loop.
     *
     * ### Why this section is not hand-built like the others
     *
     * Because the others are the problem it solves. Each section above arranges its widgets by hand,
     * which is right for showing them *in context* — a form beside a table, a toast over a window —
     * but it means the gallery's account of a widget is written separately from the documentation's,
     * and the two drifted for exactly as long as both existed. This section holds no arrangement of
     * its own: it plays [BrassDemos.ALL], the same declarations the capture run photographs, so it
     * cannot disagree with the wiki.
     *
     * The scrolled height comes from the strip rather than being guessed, because the demos size
     * themselves and the total changes whenever a widget's demo does.
     */
    private fun openDemos() {
        // A screen of its own, not a popup: the browser previews demos at 1:1 and a 290-px table demo
        // does not fit in a panel floating inside this one. Closing it comes back here.
        // Closing the browser comes straight back to a fresh gallery, so the section reads as a
        // round trip rather than a dead end.
        host.open(BrassDemoBrowser(onExit = { host.open(BrassGalleryScreen(host)) }))
    }

    private fun openTable() {
        val b = popupBounds(380f, 320f)
        val players = (1..20_000).map {
            Player("player_%05d".format(it), 12 + (it * 37) % 240, if (it % 7 == 0) "afk" else "online")
        }
        val table = BrassTable(
            listOf(
                BrassTable.Column<Player>("Player", 2.2f) { it.name },
                BrassTable.Column<Player>("Ping", 1f) { "${it.ping} ms" },
                BrassTable.Column<Player>("State", 1f) { it.state },
            ),
            players,
        ) { player, _ -> BrassToast.show(background, player.name, BrassToast.Type.INFO) }

        BrassPopup("Players (${players.size})")
            .addField("Virtualized table", table.also { it.constrain { height = 160.pixels() } })
            .addButtons(
                BrassButton("Jump to end", BrassAccent.DEFAULT) { table.scrollToEnd() },
                BrassButton("Top", BrassAccent.DEFAULT) { table.scrollTo(0) },
            )
            .show(background, b[0], b[1], b[2], b[3])
    }

    private data class Player(val name: String, val ping: Int, val state: String)

    private fun openFeedback() {
        val b = popupBounds(340f, 340f)
        BrassPopup("Feedback")
            .addField("Loading animation", BrassLoading().also { it.constrain { height = 14.pixels() } })
            .addField("Remote images", remoteImages())
            .addTags("Semantic tags", *semanticTags())
            .addTags("Theme tags", *themeTags())
            .addTags("Numeric tags (small-numbers sheet)", *numericTags())
            .addField("Colour picker", BrassColorPicker(Colors.BRASS_500) { c ->
            }.also { it.constrain { height = 132.pixels() } })
            .addButtons(
                BrassButton("Sticky download", BrassAccent.DEFAULT) { startDownloadToast() },
                BrassButton("Modal dialog", BrassAccent.BRASS) { openModal() },
            )
            .show(background, b[0], b[1], b[2], b[3])
    }

    /**
     * The chat box, with a small roster of BrassWorks-API player heads above it.
     *
     * The chat echoes what you send and a teammate answers, so the log, the scroll-to-follow and the
     * send-on-Enter are all live. The roster resolves its faces from the BrassWorks player API by name —
     * which is why it works here on the desktop with no game skin cache behind it — and shows the
     * canonical username in each head's tooltip.
     *
     * The seeded conversation is chosen to put every part of the widget on screen at once rather than to
     * read naturally: a system broadcast with no head, a run of three messages from one author (so the
     * card, the shared header and the hairlines between grouped messages are all visible), a line long
     * enough to wrap onto several rows, a message with a hard line break in it, one already marked
     * `(edited)`, and a reply quoting a message further up. [BrassChat.canModify] marks the local
     * player's own lines as editable, so right-clicking one offers edit and delete while right-clicking a
     * teammate's offers only copy and reply — the same asymmetry a real chat has.
     */
    private fun openChat() {
        val b = popupBounds(360f, 430f)

        val me = "swzo"
        lateinit var chat: BrassChat
        chat = BrassChat(placeholder = "Message your team… (Shift+Enter for a new line)") { text ->
            // replyingTo is still set for the duration of this call, so an outgoing message carries
            // whatever the context bar was pointing at — see BrassChat.replyingTo.
            chat.add(me, text, Colors.BRASS_400, avatar = me, replyTo = chat.replyingTo?.id)
            // The teammate's answer simply lands after, which closes the group above it and starts a
            // card of their own — no timer involved.
            chat.add("Notch", cannedReply(text), Colors.PATINA_400, avatar = "Notch")
        }
        chat.constrain { height = 240.pixels() }

        // Only your own lines are yours to change — teammates' messages get the copy/reply half only.
        chat.canModify = { it.author == me }
        chat.onEdit = { _, text -> BrassToast.show(background, "Edited: $text", BrassToast.Type.INFO) }
        chat.onDelete = { BrassToast.show(background, "Deleted a message.", BrassToast.Type.ERROR) }

        chat.add("[server]", "Welcome to the war room — prep phase ends Sunday.", Colors.UI_ACCENT_BRIGHT)
        val plan = chat.add(
            me,
            "stacking netherite and building the create line this week",
            Colors.BRASS_400,
            avatar = me,
        )
        chat.add(me, "consecutive messages share one card and one head", Colors.BRASS_400, avatar = me)
        chat.add(
            me,
            "…but each stays its own row: hover them one at a time, and right-click for copy, reply, " +
                "edit and delete. Long messages wrap across as many lines as they need.",
            Colors.BRASS_400,
            avatar = me,
        )
        // A hard line break, and a message that arrived already amended — the two things the body text
        // has to render beyond plain wrapping.
        chat.add(
            BrassChat.Message(
                author = me,
                text = "shopping list:\n- 2 stacks of obsidian\n- a spare elytra",
                authorColor = Colors.BRASS_400,
                avatar = me,
                edited = true,
            ),
        )
        // A reply: it quotes its target above its own body, and never joins the card above it.
        chat.add(
            "Notch",
            "on it — I'll wall off our base before the fight",
            Colors.PATINA_400,
            avatar = "Notch",
            replyTo = plan.id,
        )

        val roster = BrassHBox(gap = 4f).add(
            *listOf(me, "Notch").map {
                BrassPlayerHead(it, size = 20f, source = BrassPlayerHead.Source.BRASSWORKS)
            }.toTypedArray(),
        )

        BrassPopup("Team chat")
            .addField("Roster — heads from the BrassWorks API", roster)
            .addField("Chat — right-click a message to reply or edit", chat)
            .show(background, b[0], b[1], b[2], b[3])
    }

    /** A throwaway teammate reply, so the demo chat answers back instead of talking to itself. */
    private fun cannedReply(to: String): String = when {
        "?" in to -> "good question — ask the admin"
        to.length < 4 -> "copy that"
        else -> listOf("sounds good", "on it", "meet at the nether portal", "nice").random()
    }

    /**
     * The layout containers and state bindings, which exist to remove the two things this file is
     * otherwise full of: hand-written sibling anchors, and a tick loop pushing values into widgets.
     *
     * Nothing here names another component's position, and nothing polls: the progress bar and the
     * caption below it are bound to one [BrassState] each, and the buttons only assign to those.
     */
    private fun openLayout() {
        val b = popupBounds(360f, 300f)

        val progress = BrassState(0.35f)
        val caption = BrassState("35%, set by the buttons below")

        val bar = BrassProgressBar("Download").bind(progress)
        bar.constrain { height = 16.pixels() }

        fun step(label: String, value: Float) = BrassButton(label, BrassAccent.DEFAULT) {
            progress.value = value
            caption.value = "${(value * 100).toInt()}%, set by the buttons below"
        }.also { it.constrain { width = 52.pixels(); height = 18.pixels() } }

        val column = BrassVBox(gap = 6f).add(
            bar,
            BrassLabel("", Colors.UI_TEXT_DARK).bind(caption),
            BrassHBox(gap = 4f).add(step("0%", 0f), step("35%", 0.35f), step("100%", 1f)),
        )

        BrassPopup("Layout & state")
            .addField("VBox and HBox, bound to state", column)
            .show(background, b[0], b[1], b[2], b[3])
    }

    /**
     * The numeric and filtering controls: steppers, a two-handled range, a debounced search wired to
     * removable chips, pagination, relative timestamps, hold-to-confirm and key capture.
     */
    private fun openMoreInputs() {
        val b = popupBounds(360f, 400f)

        val count = BrassNumberInput(BrassRange(0f, 64f, step = 1f), initial = 16f, suffix = "items")
        val fine = BrassNumberInput(BrassRange(0f, 1f, step = 0.01f), initial = 0.35f)
        val band = BrassRangeSlider(BrassRange(0f, 100f, step = 1f), low = 20f, high = 70f, suffix = "%")

        // Chips and the search field wired to each other, which is the pairing they exist for: type
        // to narrow the list, click a chip's x to drop it.
        val tags = mutableListOf("survival", "hardcore", "1.21", "modded", "whitelist")
        val chips = BrassChips()
        chips.onRemove = { label -> tags.remove(label); chips.setLabels(tags) }
        chips.setLabels(tags)

        val search = BrassSearchField("Filter tags…") { query ->
            chips.setLabels(if (query.isEmpty()) tags else BrassFuzzy.rank(query, tags) { it })
        }

        // Pagination over a pretend 40-page result set, with the page's slice shown beside it.
        val pageLabel = BrassLabel("showing 1–10 of 397", Colors.UI_TEXT_DARK)
        val pager = BrassPagination(total = BrassPageWindow.pageCount(397, 10)) { page ->
            val slice = BrassPageWindow.range(page, perPage = 10, items = 397)
            pageLabel.text = if (slice.isEmpty()) "no results" else
                "showing ${slice.first + 1}–${slice.last + 1} of 397"
        }

        // The same pager again, on a card of its own — the footer-under-a-table shape.
        val carded = BrassPagination(total = 12, current = 4)
        carded.card = true
        carded.constrain { height = 20.pixels() }

        // Relative timestamps, each formatted from a real offset so they tick over as you watch.
        val now = System.currentTimeMillis()
        val times = BrassHBox(gap = 10f).add(
            BrassTimeAgo(now - 5_000L),
            BrassTimeAgo(now - 7 * 60_000L),
            BrassTimeAgo(now - 5 * 3_600_000L),
            BrassTimeAgo(now - 3L * 86_400_000L),
        )

        val danger = BrassConfirmSlider("Hold to delete the world") {
            BrassToast.show(background, "Deleted. (not really)", BrassToast.Type.ERROR)
        }

        val bind = BrassKeybind(BrassKeyChord(GLFW.GLFW_KEY_G))
        val other = BrassKeybind(BrassKeyChord(GLFW.GLFW_KEY_G, ctrl = true))
        // Each bind treats the other as the conflict set, so binding both to the same chord marks
        // them in the danger colour rather than silently letting one win.
        bind.conflictsWith = { it == other.value }
        other.conflictsWith = { it == bind.value }

        BrassPopup("More inputs")
            .addField("Stepper + scrub", count)
            .addField("Fractional", fine)
            .addField("Range", band)
            .addField("Search", search)
            .addField("Chips", chips)
            .addField("Pagination", pager)
            .addField("", pageLabel)
            .addField("…on a card of its own", carded)
            .addField("Time ago (hover for the timestamp)", times)
            .addField("Confirm — press and hold", danger)
            .addField("Keybind (Escape or Delete unbinds)", bind)
            .addField("Keybind (try G)", other)
            .addButtons(
                BrassButton("Re-arm hold", BrassAccent.DEFAULT) { danger.reset() },
                BrassButton("Reset tags", BrassAccent.DEFAULT) {
                    tags.clear()
                    tags.addAll(listOf("survival", "hardcore", "1.21", "modded", "whitelist"))
                    chips.setLabels(tags)
                },
            )
            .show(background, b[0], b[1], b[2], b[3])
    }

    /**
     * A checkbox with a clickable caption — the shape every settings row has, and the shape
     * [BrassCheckbox] deliberately does *not* have on its own (it is only the box). The caption is
     * bound as a click proxy, exactly as `openToggles` does it.
     */
    private fun labelledCheck(text: String, initial: Boolean): UIContainer {
        val row = UIContainer().constrain { width = 100.percent(); height = 13.pixels() }
        val box = BrassCheckbox(initial)
            .constrain { x = 0.pixels(); width = 13.pixels(); height = 13.pixels() } childOf row
        val caption = BrassText.body(text)
            .constrain { x = 18.pixels(); y = CenterConstraint() } childOf row
        box.proxiedBy(caption)
        return row
    }

    /** Collapsible sections, a draggable divider, an empty state, and the command palette. */
    private fun openSections() {
        val b = popupBounds(380f, 380f)

        val accordion = BrassAccordion(exclusive = true)
        accordion.section(
            "Display",
            BrassVBox(gap = 4f).add(
                labelledCheck("Fullscreen", false),
                labelledCheck("VSync", true),
                BrassSlider(0f, 100f, 75f, step = 5f) {}.constrain { height = 13.pixels() },
            ),
            open = true,
        )
        accordion.section(
            "Advanced",
            BrassVBox(gap = 4f).add(
                labelledCheck("Debug overlay", false),
                BrassNumberInput(BrassRange(1f, 32f, step = 1f), initial = 8f, suffix = "chunks")
                    .constrain { height = 14.pixels() },
            ),
        )
        accordion.constrain { height = basicHeightConstraint { accordion.contentHeight() } }

        // A real master/detail: pick a shard on the left, its details replace the empty state on the
        // right. That is the whole point of a split pane, and a pane of placeholder text showed none
        // of it — you could not tell what the widget was for or why the divider mattered.
        val shards = listOf(
            Shard("survival-1", 42, 19.8f, "eu-west"),
            Shard("survival-2", 37, 19.9f, "eu-west"),
            Shard("creative", 8, 20.0f, "us-east"),
            Shard("minigames", 63, 18.4f, "us-east"),
            Shard("staging", 0, 20.0f, "local"),
        )

        val detail = UIContainer()
        BrassEmptyState(BrassIcons.SEARCH, "No shard selected", "Pick one on the left")
            .constrain { width = 100.percent(); height = 100.percent() } childOf detail

        val list = BrassTable(
            listOf(
                BrassTable.Column("Shard", 2f, sortBy = { it.name }) { it.name },
                BrassTable.Column("Players", 1f, sortBy = { it.players }) { it.players.toString() },
            ),
            shards,
        ) { shard, _ ->
            // Swap the right pane for this shard's details. Rebuilt per selection rather than kept
            // around, which is what a details pane in a real tool does.
            detail.clearChildren()
            BrassVBox(gap = 3f).add(
                BrassLabel(shard.name).also { it.constrain { height = 10.pixels() } },
                BrassLabel("${shard.players} players", Colors.UI_TEXT_DARK)
                    .also { it.constrain { height = 9.pixels() } },
                BrassLabel("%.1f TPS".format(shard.tps), if (shard.tps > 19f) Colors.BRASS_400 else Colors.WARN)
                    .also { it.constrain { height = 9.pixels() } },
                BrassLabel(shard.region, Colors.UI_TEXT_DARK).also { it.constrain { height = 9.pixels() } },
            ).constrain {
                x = 6.pixels(); y = 6.pixels(); width = 100.percent() - 12.pixels()
                height = 100.percent() - 12.pixels()
            } childOf detail
        }

        val split = BrassSplitPane(
            BrassSplitPane.Orientation.HORIZONTAL,
            list,
            detail,
            split = 0.5f,
            card = true,
        )
        split.constrain { height = 96.pixels() }

        BrassPopup("Sections & panes")
            .addField("Accordion", accordion)
            .addField("Split pane — drag the divider", split)
            .addButtons(
                BrassButton("Command palette", BrassAccent.BRASS) { openPalette() },
                BrassButton("Collapse all", BrassAccent.DEFAULT) { accordion.closeAll() },
            )
            .show(background, b[0], b[1], b[2], b[3])
    }

    /** The palette, opened over the whole screen rather than inside a popup — that is its shape. */
    private fun openPalette() {
        val commands = listOf(
            BrassCommandPalette.Command("Open Preview Window", "View", "Ctrl+P") { openLayout() },
            BrassCommandPalette.Command("Show Table", "View") { openTable() },
            BrassCommandPalette.Command("Show Data Views", "View") { openData() },
            BrassCommandPalette.Command("Toggle Dev Inspector", "Debug", "F3") { BrassDevMode.toggle() },
            BrassCommandPalette.Command("Change Theme", "Appearance") { openAppearance() },
            BrassCommandPalette.Command("Run Feedback Demo", "Demo") { openFeedback() },
            BrassCommandPalette.Command("Copy Diagnostics", "Debug") {
                BrassToast.show(background, "Copied.", BrassToast.Type.SUCCESS)
            },
        )
        BrassCommandPalette(commands).show(background)
    }

    /** The data views: a tree, a live chart, two bar charts, and syntax-highlighted code. */
    private fun openData() {
        val b = popupBounds(400f, 420f)

        // A tiny in-memory filesystem, so the tree has something with real nesting to show.
        val tree = BrassTreeView(
            roots = listOf(DemoNode.ROOT),
            childrenOf = { it.children },
            label = { it.name },
            tag = { node -> if (node.children.isEmpty()) null else "${node.children.size}" to BrassTagStyle.INFO },
            icon = { node -> if (node.children.isEmpty()) BrassIcons.NONE else BrassIcons.FOLDER },
        )
        tree.expandTo(1)
        tree.constrain { height = 120.pixels() }

        val chart = BrassChart(window = 120, fixedMax = 20f)
        val tps = chart.series("TPS", Colors.BRASS_400, filled = true)
        val ping = chart.series("Ping", Colors.PATINA_400)
        chart.constrain { height = 70.pixels() }
        // Seeded with plausible history so the chart is not empty for the first two seconds.
        repeat(120) { i ->
            chart.push(tps, 20f - (kotlin.math.sin(i / 9f) + 1f) * (if (i in 70..80) 4f else 0.6f))
            chart.push(ping, 8f + kotlin.math.sin(i / 14f) * 3f)
        }
        liveCharts += Triple(chart, tps, ping)

        // Categorical data beside the time series, so the two hover treatments can be compared. Every
        // bar carries its own colour, and the outline on each is derived from that colour rather than
        // from the theme — the netherite bar's edge is a brighter red, not a brighter brass.
        val bars = BrassBarChart()
        bars.setBars(listOf(
            BrassBarChart.Bar("diamond", 128f),
            BrassBarChart.Bar("iron", 96f, Colors.PATINA_400),
            BrassBarChart.Bar("gold", 54f, Colors.WARN),
            BrassBarChart.Bar("netherite", 12f, Colors.DANGER),
            BrassBarChart.Bar("copper", 71f),
        ))
        bars.values = BrassBarChart.Values.INSIDE
        bars.constrain { height = 66.pixels() }

        // The same data with no colours at all, so every bar takes the theme accent — and with the
        // values written above the bars instead of inside them. Note the short bar: INSIDE has no room
        // on it, so that one number moves above on its own.
        val accented = BrassBarChart()
        accented.setBars(listOf(
            BrassBarChart.Bar("mon", 34f),
            BrassBarChart.Bar("tue", 51f),
            BrassBarChart.Bar("wed", 12f),
            BrassBarChart.Bar("thu", 68f),
            BrassBarChart.Bar("fri", 44f),
        ))
        accented.values = BrassBarChart.Values.ABOVE
        accented.constrain { height = 66.pixels() }

        val code = BrassCodeView(SAMPLE_CODE, language = "kotlin")
        code.markers = setOf(4)
        code.constrain { height = 110.pixels() }

        BrassPopup("Trees, charts & code")
            .addField("Tree view", tree)
            .addField("Chart (hover a slice)", chart)
            .addField("Bar chart — own colours, values inside", bars)
            .addField("Bar chart — accent colour, values above", accented)
            .addField("Code view", code)
            .addButtons(
                BrassButton("Expand all", BrassAccent.DEFAULT) { tree.expandTo(9) },
                BrassButton("Collapse", BrassAccent.DEFAULT) { tree.expandTo(0) },
            )
            .show(background, b[0], b[1], b[2], b[3])
    }

    /**
     * The appearance card: theme dropdown + accent swatches, wired to the global [BrassThemes]. Nothing
     * here stores a preference — a real app hooks `BrassThemes.onChange` and writes its own config.
     */
    private fun openAppearance() {
        val b = popupBounds(300f, 200f)
        BrassPopup("Appearance")
            .addField("Theme & accent", BrassThemeCard { background })
            .show(background, b[0], b[1], b[2], b[3])
    }

    /** Remote images fetched over HTTPS at runtime — plain and encased in cards. */
    private fun remoteImages(): UIContainer {
        val row = UIContainer().constrain { height = 72.pixels() }
        val urls = listOf(
            "https://cdn.modrinth.com/data/P7dR8mSH/icon.png",
            "https://cdn.modrinth.com/data/9eGKb6K1/icon.png",
            "https://cdn.modrinth.com/data/000000/does-not-exist.png",
        )
        urls.forEachIndexed { i, u ->
            BrassImage(u).constrain {
                x = (i * 36).pixels(); y = 0.pixels(); width = 32.pixels(); height = 32.pixels()
            } childOf row
            BrassImage(u, card = true).constrain {
                x = (i * 36).pixels(); y = 38.pixels(); width = 32.pixels(); height = 32.pixels()
            } childOf row
        }
        return row
    }

    private fun semanticTags(): Array<BrassTag> = arrayOf(
        BrassTag("success", BrassTag.SUCCESS), BrassTag("warning", BrassTag.WARNING),
        BrassTag("error", BrassTag.ERROR), BrassTag("info", BrassTag.INFO), BrassTag("muted", BrassTag.MUTED),
    )

    private fun themeTags(): Array<BrassTag> = arrayOf(
        BrassTag("brass", BrassTag.BRASS), BrassTag("patina", BrassTag.PATINA), BrassTag("amber", BrassTag.AMBER),
        BrassTag("rust", BrassTag.RUST), BrassTag("steel", BrassTag.STEEL),
        BrassTag("new", BrassTag.NEW), BrassTag("beta", BrassTag.BETA),
    )

    /**
     * Tags whose text is mostly digits — the case the small-numbers sheet exists for. The numerals are
     * the same 5-px ink height as the small capitals beside them, so `v1.21` reads as one run of text
     * rather than as short letters next to tall numbers.
     *
     * Worth having on the desktop specifically: the caps sheet was rasterised out of Minecraft's font
     * precisely because that font does not exist here, so this is the build where a regression in
     * either sheet actually shows.
     */
    private fun numericTags(): Array<BrassTag> = arrayOf(
        BrassTag("1.21", BrassTag.BRASS),
        BrassTag("v2.0.4", BrassTag.PATINA),
        BrassTag("0123456789", BrassTag.STEEL),
        BrassTag("120 fps", BrassTag.SUCCESS),
        BrassTag("beta 3", BrassTag.BETA),
    )

    private fun openModal() {
        lateinit var dialog: BrassPopup
        dialog = BrassPopup("Confirm", modal = true)
        dialog
            .addField("", BrassText.wrapped("Delete the selected world? This cannot be undone."))
            .addButtons(
                BrassButton("Cancel", BrassAccent.DEFAULT) { dialog.dismiss() },
                BrassButton("Delete", BrassAccent.DANGER) {
                    dialog.dismiss()
                    BrassToast.show(background, "Deleted.", BrassToast.Type.ERROR)
                },
            )
            .showModal(background, 260f, 130f)
    }

    private fun startDownloadToast() {
        val toast = BrassToast.show(background, "Downloading world…", BrassToast.Type.INFO, sticky = true)
        toast.progress = 0f
        downloadToast = toast
    }

    private companion object {
        /** Something for the code view to highlight that is short enough to read at a glance. */
        val SAMPLE_CODE = """
            fun render(stack: ItemStack, x: Int, y: Int) {
                // a marker sits on the line below
                val model = models.get(stack) ?: return
                if (!model.isReady) {
                    error("model not loaded: ${'$'}{stack.item}")
                }
                graphics.renderItem(stack, x, y)
            }
        """.trimIndent()
    }
}
