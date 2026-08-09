package net.swzo.brass.ui.kit.dev

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.input.BrassSquareButton
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassLabel
import kotlin.math.roundToInt

/**
 * The docked inspector panel - a fixed strip down the right edge (the main UI is shrunk to make room,
 * Chrome-dev-tools style). Header with a close button, a couple of compact perf lines, the scrolling
 * [DevTree], and a [DevDetails] pane describing the selected element.
 */
class BrassDevPanel(root: UIComponent) : UIContainer(), BrassDevOverlay {

    private val tree = DevTree()

    init {
        // The title and its keys live in a bar the exact height of the painted header, so both centre
        // in it rather than being nudged into place with hand-picked offsets that have to be
        // re-picked whenever HEADER changes.
        val headerBar = UIContainer().constrain {
            x = 0.pixels(); y = 0.pixels()
            width = 100.percent(); height = HEADER.pixels()
        } childOf this

        BrassLabel("Debug Tools", Colors.UI_TEXT_HOVER).also { it.entranceEnabled = false }
            .constrain { x = 8.pixels(); y = CenterConstraint() } childOf headerBar

        // Header controls, right to left: close, outline toggle, element picker. Each stays `selected`
        // while its mode is on, so the header shows the inspector's state at a glance.
        // These do get tooltips: the panel's suppression is a predicate that exempts the panel's own
        // subtree (see BrassDevMode.drawOverlay), precisely so its controls can explain themselves.
        var slot = 0
        fun headerButton(
            icon: BrassIcons.Icon,
            accent: BrassAccent,
            tipTitle: String,
            tipBody: String,
            action: BrassSquareButton.() -> Unit,
        ) = BrassSquareButton(icon, accent).also { b ->
            b.entranceEnabled = false
            b.selectable = true
            b.onMouseClick { e -> if (e.mouseButton == 0) b.action() }
            BrassTooltip.attach(b, tipTitle, tipBody)
            b.constrain {
                x = (6 + slot * 17).pixels(true); y = CenterConstraint()
                width = 14.pixels(); height = 14.pixels()
            } childOf headerBar
            slot++
        }

        headerButton(
            BrassIcons.CLOSE, BrassAccent.DANGER,
            "Close inspector", "Ctrl+Shift+D",
        ) { BrassDevMode.toggle() }

        headerButton(
            BrassIcons.MAXIMIZE, BrassAccent.DEFAULT,
            "Widget outlines", "Outline every widget as it paints",
        ) { selected = BrassDevMode.toggleOutlines() }
            .also { it.selected = BrassDevMode.showOutlines }

        headerButton(
            BrassIcons.SEARCH, BrassAccent.BRASS,
            "Pick element", "Click anything in the UI to select it here",
        ) { selected = BrassDevMode.togglePicking() }
            .also { it.selected = BrassDevMode.picking }

        tree.constrain {
            x = 0.pixels(); y = (HEADER + STATS).pixels()
            width = 100.percent()
            height = 100.percent() - (HEADER + STATS + DevDetails.HEIGHT).pixels()
        } childOf this
        tree.bind(root)

        DevDetails().constrain {
            x = 0.pixels(); y = 0.pixels(true)
            width = 100.percent(); height = DevDetails.HEIGHT.pixels()
        } childOf this
    }

    fun bind(root: UIComponent) = tree.bind(root)
    fun markTreeDirty() = tree.markDirty()
    fun markTreeDirtyAnimated() = tree.markDirtyAnimated()
    fun beginTreeCollapse(node: UIComponent) = tree.beginCollapse(node)

    override fun draw(matrixStack: UMatrixStack) {
        val x = getLeft().roundToInt().toFloat(); val y = getTop().roundToInt().toFloat()
        val x2 = getRight().roundToInt().toFloat(); val y2 = getBottom().roundToInt().toFloat()

        // The inspector is a card like every other surface in the toolkit - shadow, ring, fill, border
        // - floating clear of the screen edge rather than a flat strip welded to it. Its header is the
        // same stacked header a window or a table gets, so the panel reads as part of the same UI it
        // is inspecting instead of a debug overlay bolted on.
        BrassCard.draw(matrixStack, x, y, x2, y2, shadow = true)
        BrassCard.header(matrixStack, x, y, x2, HEADER)

        // compact perf readout under the header
        var ly = y + HEADER + 5f
        for ((text, color) in BrassDevMode.statsLines()) {
            BrassFont.draw(matrixStack, this, text, x + 8f, ly, color, false)
            ly += BrassFont.LINE + 2f
        }

        super.draw(matrixStack)
    }

    companion object {
        const val WIDTH = 244f
        const val HEADER = 20f
        const val STATS = 42f
        const val MARGIN = 0f
    }
}
