package net.swzo.brass.ui.demo.examples

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
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
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassKeyChord
import net.swzo.brass.ui.kit.input.BrassKeybind
import net.swzo.brass.ui.kit.input.BrassNavRail
import net.swzo.brass.ui.kit.input.BrassTabSwitch
import net.swzo.brass.ui.kit.layout.BrassBreakpoint
import net.swzo.brass.ui.kit.layout.BrassDivider
import net.swzo.brass.ui.kit.layout.BrassForm
import net.swzo.brass.ui.kit.layout.BrassHBox
import net.swzo.brass.ui.kit.layout.BrassLayout
import net.swzo.brass.ui.kit.layout.BrassNavStack
import net.swzo.brass.ui.kit.layout.BrassPages
import net.swzo.brass.ui.kit.layout.BrassScrollArea
import net.swzo.brass.ui.kit.layout.BrassSpacing
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.settings.BrassThemeCard
import net.swzo.brass.ui.kit.surface.BrassListRow
import net.swzo.brass.ui.kit.surface.BrassToast
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassMarkdown
import org.lwjgl.glfw.GLFW

class ModSettingsScreen(fit: Fit) : BrassReferenceScreen("Create Linkworks", "settings", fit, maxWidth = 460f, maxHeight = 300f) {

    override val id = "mod-settings"

    private val pages = BrassPages(slide = BrassPages.Slide.VERTICAL)
    private val keys = BrassNavStack()
    private val categoryRows = ArrayList<BrassListRow>()
    private lateinit var rail: BrassNavRail

    init {
        val footer = UIContainer().constrain {
            x = 0.pixels(); y = 0.pixels(alignOpposite = true)
            width = 100.percent(); height = FOOTER_H.pixels()
        } childOf body
        fun sized(b: BrassButton) = b.also { it.constrain { width = 60.pixels(); height = 18.pixels() } }
        BrassHBox(gap = BrassSpacing.GAP).add(
            sized(BrassButton("Reset") { BrassToast.show(background, "Defaults restored", BrassToast.Type.INFO) }),
        ).spring().add(
            sized(BrassButton("Cancel") { onCloseRequested() }),
            sized(BrassButton("Save", BrassAccent.BRASS) { BrassToast.show(background, "Saved", BrassToast.Type.SUCCESS) }),
        ).constrain { x = 0.pixels(); y = 0.pixels() } childOf footer

        pages.page("Overview", BrassIcons.HOME) { root ->
            form(root)
                .addToggleRow("Show link previews", true)
                .addToggleRow("Wire particles", true)
                .addRow("Network view", 18, BrassTabSwitch(listOf("Flat", "Grouped", "Tree"), equalWidths = true))
        }
        pages.page("Performance", BrassIcons.BOLT) { root ->
            form(root)
                .addSlider("Update rate", 1f, 20f, 10f, step = 1f, format = { "${it.toInt()} / s" })
                .addSlider("Render distance", 16f, 256f, 96f, step = 16f, format = { "${it.toInt()} blocks" })
                .addToggleRow("Batch wire drawing", true)
                .addToggleRow("Cull hidden nodes", true)
        }
        pages.page("Keybinds", BrassIcons.KEYBOARD) { root -> buildKeys(root) }
        pages.page("Appearance", BrassIcons.PALETTE) { root ->
            val scroll = BrassScrollArea().constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf root
            scroll.add(BrassThemeCard { background }.also { it.constrain { width = 100.percent() } })
        }
        pages.page("About", BrassIcons.INFO) { root ->
            BrassMarkdown(ABOUT).constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf root
        }

        rail = pages.navRail(pinnedBottom = 1, pinnedTop = 1).constrain {
            x = 0.pixels(); y = 0.pixels()
            width = BrassBreakpoint.proportional(fraction = 0.26f, min = 92f, max = 124f)
            height = BrassLayout.fillAbove(footer, gap = BrassSpacing.GAP)
        } childOf body
        pages.constrain {
            x = basicXConstraint { rail.getRight() + RAIL_GAP }; y = 0.pixels()
            width = basicWidthConstraint { c -> (c.parent.getRight() - rail.getRight() - RAIL_GAP).coerceAtLeast(0f) }
            height = BrassLayout.fillAbove(footer, gap = BrassSpacing.GAP)
        } childOf body
        BrassDivider.between(body, rail, pages, span = rail, inset = 0f)
    }

    private fun form(root: UIContainer): BrassForm =
        BrassForm().constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf root

    private fun buildKeys(root: UIContainer) {
        keys.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf root
        val list = BrassScrollArea.list()
        KEYS.forEach { (category, binds) ->
            val row = BrassListRow(category, "${binds.size} keys", BrassIcons.KEYBOARD) { keys.push(level(category, binds)) }
            categoryRows += row
            list.add(row)
        }
        keys.push(list, animate = false)
    }

    private fun level(title: String, binds: List<Pair<String, Int>>): UIComponent {
        val c = UIContainer()
        BrassButton("Back") { keys.pop() }.constrain { x = 0.pixels(); y = 1.pixels(); width = 44.pixels(); height = 18.pixels() } childOf c
        BrassLabel(title, Colors.UI_TEXT_HOVER).constrain { x = 52.pixels(); y = 6.pixels() } childOf c
        val f = BrassForm().constrain { x = 0.pixels(); y = 28.pixels(); width = 100.percent(); height = 100.percent() - 28.pixels() } childOf c
        binds.forEach { (label, key) -> f.addField(label, BrassKeybind(BrassKeyChord(key)).also { it.constrain { height = 18.pixels() } }) }
        return c
    }

    private fun railRow(i: Int, count: Int): Pair<Float, Float> {
        val step = 20f
        val y = if (i == count - 1) rail.getBottom() - 12f else rail.getTop() + 10f + i * step + if (i > 0) 6f else 0f
        return rail.getLeft() + 30f to y
    }

    override fun scenario() = listOf(
        ReferenceStep("overview") { },
        ReferenceStep("performance", frames = 40) { d -> railRow(1, 5).let { (x, y) -> d.clickAt(x, y) } },
        ReferenceStep("keybinds", frames = 40) { d -> railRow(2, 5).let { (x, y) -> d.clickAt(x, y) } },
        ReferenceStep("drill-in", frames = 45) { d -> d.click(categoryRows[1]) },
        ReferenceStep("drill-back", frames = 45) { keys.pop() },
        ReferenceStep("appearance", frames = 40) { d -> railRow(3, 5).let { (x, y) -> d.clickAt(x, y) } },
        ReferenceStep("about", frames = 40) { d -> railRow(4, 5).let { (x, y) -> d.clickAt(x, y) } },
        ReferenceStep("keybinds-again", frames = 40) { d -> railRow(2, 5).let { (x, y) -> d.clickAt(x, y) } },
        ReferenceStep("narrow", frames = 40) { squeeze(300f, 240f) },
    )

    private companion object {
        val FOOTER_H = 18f + BrassWidget.BLEED_BOTTOM
        const val RAIL_GAP = 12f

        val KEYS = listOf(
            "Wiring" to listOf("Start wire" to GLFW.GLFW_KEY_W, "Cancel wire" to GLFW.GLFW_KEY_X, "Snap to grid" to GLFW.GLFW_KEY_G),
            "Network" to listOf("Open network" to GLFW.GLFW_KEY_N, "Ping node" to GLFW.GLFW_KEY_P, "Toggle overlay" to GLFW.GLFW_KEY_O, "Focus selection" to GLFW.GLFW_KEY_F),
            "Camera" to listOf("Frame all" to GLFW.GLFW_KEY_HOME, "Zoom in" to GLFW.GLFW_KEY_EQUAL, "Zoom out" to GLFW.GLFW_KEY_MINUS),
        )

        const val ABOUT = """# Create Linkworks

Logic networks for Create machines, edited in a node graph.

- Version 1.4.0
- Built on brassui

## Links

Source and issues live on GitHub."""
    }
}
