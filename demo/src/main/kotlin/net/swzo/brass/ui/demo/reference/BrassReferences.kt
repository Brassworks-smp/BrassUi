package net.swzo.brass.ui.demo.reference

import gg.essential.elementa.UIComponent
import net.swzo.brass.ui.BrassAppScreen

object BrassReferences {

    class Reference(val id: String, val title: String, val create: (BrassAppScreen.Fit) -> BrassReferenceScreen)

    val ALL: List<Reference> = listOf(
        Reference("dashboard", "Dashboard") { ShardDashboardScreen(it) },
        Reference("settings", "Settings") { ClientSettingsScreen(it) },
        Reference("registry", "Large list") { ItemRegistryScreen(it) },
        Reference("guild", "Multi-tab manager") { GuildManagerScreen(it) },
        Reference("quests", "Quest journal") { QuestJournalScreen(it) },
    )

    val EXAMPLES: List<Reference> = listOf(
        Reference("hub", "Server hub") { net.swzo.brass.ui.demo.examples.ServerHubScreen(it) },
        Reference("mod-settings", "Mod settings") { net.swzo.brass.ui.demo.examples.ModSettingsScreen(it) },
        Reference("market", "Market") { net.swzo.brass.ui.demo.examples.MarketScreen(it) },
    )

    fun find(id: String): Reference? = (ALL + EXAMPLES).firstOrNull { it.id.equals(id, ignoreCase = true) }
}

interface ReferenceDriver {
    fun hover(component: UIComponent)

    fun unhover()

    fun click(component: UIComponent, fx: Float = 0.5f, fy: Float = 0.5f) =
        clickAt(component.getLeft() + component.getWidth() * fx, component.getTop() + component.getHeight() * fy)

    fun clickAt(x: Float, y: Float)
}

class ReferenceStep(val name: String, val frames: Int = 24, val act: (ReferenceDriver) -> Unit)

abstract class BrassReferenceScreen(
    title: String,
    subtitle: String?,
    fit: Fit,
    maxWidth: Float = 520f,
    maxHeight: Float = 340f,
) : BrassAppScreen(title, subtitle, fit, maxWidth, maxHeight) {

    abstract val id: String

    open fun scenario(): List<ReferenceStep> = emptyList()

    protected fun squeeze(w: Float, h: Float) {
        resizeWindow(w, h)
    }
}
