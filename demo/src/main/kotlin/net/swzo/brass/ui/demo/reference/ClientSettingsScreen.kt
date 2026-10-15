package net.swzo.brass.ui.demo.reference

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.dsl.basicWidthConstraint
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
import net.swzo.brass.ui.kit.layout.BrassPages
import net.swzo.brass.ui.kit.layout.BrassSpacing
import net.swzo.brass.ui.kit.surface.BrassToast
import org.lwjgl.glfw.GLFW

class ClientSettingsScreen(fit: Fit) : BrassReferenceScreen("Settings", "client", fit, maxWidth = 460f) {

    override val id = "settings"

    private val pages = BrassPages()
    private lateinit var videoForm: BrassForm

    init {
        val footer = UIContainer().constrain {
            x = 0.pixels(); y = 0.pixels(alignOpposite = true)
            width = 100.percent(); height = FOOTER_H.pixels()
        } childOf body

        fun sized(b: BrassButton) = b.also { it.constrain { width = 60.pixels(); height = BUTTON_H.pixels() } }
        BrassHBox(gap = BrassSpacing.GAP).add(
            sized(BrassButton("Reset") { BrassToast.show(background, "Defaults restored", BrassToast.Type.INFO) }),
        ).spring().add(
            sized(BrassButton("Cancel") { onCloseRequested() }),
            sized(BrassButton("Save", BrassAccent.BRASS) { BrassToast.show(background, "Saved", BrassToast.Type.SUCCESS) }),
        ).constrain { x = 0.pixels(); y = 0.pixels() } childOf footer

        pages.page("General") { root ->
            form(root)
                .addDropdown("Language", listOf("en" to "English", "de" to "Deutsch", "fr" to "Français"), "en")
                .addToggleRow("Show toasts", true)
                .addToggleRow("Snapshot builds", false)
                .addToggleRow("Send crash reports", true)
                .addTextField("Display name", "Steve", "shown to other players")
        }
        pages.page("Video") { root ->
            videoForm = form(root)
                .addSlider("Render distance", 2f, 32f, 12f, step = 1f, format = { "${it.toInt()} chunks" })
                .addSlider("Field of view", 30f, 110f, 70f, step = 1f, format = { "${it.toInt()}°" })
                .addRow("GUI scale", 18, BrassTabSwitch(listOf("Auto", "1", "2", "3"), equalWidths = true))
                .addSlider("Brightness", 0f, 1f, 0.5f, format = { "${(it * 100).toInt()}%" })
                .addToggleRow("VSync", true)
                .addToggleRow("Entity shadows", true)
                .addToggleRow("View bobbing", false)
                .addSlider("Max framerate", 30f, 260f, 120f, step = 10f, format = { if (it >= 260f) "Unlimited" else "${it.toInt()} fps" })
        }
        pages.page("Audio") { root ->
            form(root)
                .addSlider("Master", 0f, 1f, 0.8f, format = { "${(it * 100).toInt()}%" })
                .addSlider("Music", 0f, 1f, 0.35f, format = { "${(it * 100).toInt()}%" })
                .addSlider("Blocks", 0f, 1f, 1f, format = { "${(it * 100).toInt()}%" })
                .addToggleRow("Subtitles", false)
        }
        pages.page("Controls") { root ->
            val f = form(root)
            KEYS.forEach { (label, key) ->
                f.addField(label, BrassKeybind(BrassKeyChord(key)).also { it.constrain { height = 18.pixels() } })
            }
        }

        val rail = pages.navRail().constrain {
            x = 0.pixels(); y = 0.pixels()
            width = BrassBreakpoint.proportional(fraction = 0.24f, min = 84f, max = 120f)
            height = BrassLayout.fillAbove(footer, gap = BrassSpacing.GAP)
        } childOf body

        pages.constrain {
            x = basicXConstraint { rail.getRight() + RAIL_GAP }
            y = 0.pixels()
            width = basicWidthConstraint { c -> (c.parent.getRight() - rail.getRight() - RAIL_GAP).coerceAtLeast(0f) }
            height = BrassLayout.fillAbove(footer, gap = BrassSpacing.GAP)
        } childOf body
        BrassDivider.between(body, rail, pages, span = rail, inset = 0f)
    }

    private fun form(root: UIContainer): BrassForm =
        BrassForm().constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf root

    override fun scenario() = listOf(
        ReferenceStep("video") { pages.select("Video") },
        ReferenceStep("video-end") { videoForm.body.scrollToBottom(false) },
        ReferenceStep("controls") { pages.select("Controls") },
        ReferenceStep("video-again") { pages.select("Video") },
        ReferenceStep("narrow") { pages.select("General"); squeeze(300f, 240f) },
    )

    private companion object {
        const val BUTTON_H = 18f
        val FOOTER_H = BUTTON_H + BrassWidget.BLEED_BOTTOM
        const val RAIL_GAP = 12f

        val KEYS = listOf(
            "Forward" to GLFW.GLFW_KEY_W, "Back" to GLFW.GLFW_KEY_S, "Left" to GLFW.GLFW_KEY_A,
            "Right" to GLFW.GLFW_KEY_D, "Jump" to GLFW.GLFW_KEY_SPACE, "Sneak" to GLFW.GLFW_KEY_LEFT_SHIFT,
            "Inventory" to GLFW.GLFW_KEY_E, "Chat" to GLFW.GLFW_KEY_T,
        )
    }
}
