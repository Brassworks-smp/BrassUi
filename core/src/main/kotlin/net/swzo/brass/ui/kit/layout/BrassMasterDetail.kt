package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.basicYConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.base.BrassDismissable
import net.swzo.brass.ui.kit.base.BrassInert
import net.swzo.brass.ui.kit.base.BrassMotion
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.surface.BrassLayers
import net.swzo.brass.ui.kit.surface.BrassListRow
import net.swzo.brass.ui.kit.surface.BrassPanel
import net.swzo.brass.ui.kit.text.BrassWrappedLabel

/**
 * A list beside its details that adapts like a phone app: **side by side** while there is room, and when
 * the [master] would get narrower than [minMaster], **one column** - the master takes the whole width and
 * [open] pushes the [detail] in over it as a sub-panel (sliding from the right over the dimmed master, a
 * back key labelled [backLabel] on top). The back key, Escape and [close] slide it away again.
 *
 * Put the list (or tree, or board) in [master] and the details in [detail]; both are filled edge to edge,
 * and [detail] sits below the back bar when pushed. Call [open] when the *player* picks something - a
 * selection restored in code should just update the detail, not push it.
 *
 * ```
 * val md = BrassMasterDetail().constrain { ... } childOf body
 * list.constrain { width = 100.percent(); height = 100.percent() } childOf md.master
 * card.constrain { width = 100.percent(); height = 100.percent() } childOf md.detail
 * row.onClick { card.show(it); md.open() }
 * md.backLabel = { "Members" }
 * ```
 */
class BrassMasterDetail @JvmOverloads constructor(
    /** The detail column's width side by side (read live, so it can depend on the screen). */
    var detailWidth: () -> Float = { 210f },
    /** The narrowest the master may get beside the detail before the layout goes to one column. */
    var minMaster: Float = 150f,
) : UIContainer() {

    /** What the back key says in one column ("Back", or the master's name). */
    var backLabel: () -> String = { "Back" }

    /** The pushed detail was closed (back key, Escape or [close]). */
    var onClose: (() -> Unit)? = null

    private var pushed = false
    private var t = 0f

    /** One column: the master alone, the detail pushed over it on [open]. */
    val stacked: Boolean get() = getWidth() - detailW() - BrassSpacing.GAP < minMaster

    /** Whether the detail is pushed over the master right now (only ever in one column). */
    val detailShown: Boolean get() = stacked && pushed

    private fun detailW(): Float = detailWidth().coerceAtMost(getWidth())

    /** The player picked something: in one column, push the detail in. (Side by side it is already showing.) */
    fun open() { pushed = true }

    /** Slide the pushed detail back out to the master. */
    fun close() {
        if (!pushed) return
        pushed = false
        onClose?.invoke()
    }

    /** Back to the master at once, no slide (switching to another list, say). */
    fun reset() {
        pushed = false
        t = 0f
    }

    /** The list side. Skipped while a pushed detail covers it completely. */
    val master: UIContainer = object : UIContainer(), BrassInert {
        // once the spring has landed: the timer's last stretch moves less than a pixel, and frames can
        // come faster than the timer finishes (the master's keycap rings used to peek past the panel)
        private fun covered() = detailShown && BrassMotion.spring(t) >= 0.995f
        override val inert: Boolean get() = covered()
        override fun draw(matrixStack: UMatrixStack) { if (!covered()) super.draw(matrixStack) }
        override fun isPointInside(x: Float, y: Float): Boolean = !covered() && super.isPointInside(x, y)
        override fun mouseMove(window: Window) { if (!covered()) super.mouseMove(window) }
    }

    private val host = Host()

    private val back = BrassButton("Back", BrassAccent.DEFAULT) { close() }.also {
        it.icon = BrassIcons.ARROW_LEFT
        it.centered = false
        it.entranceEnabled = false
    }

    /** The details side; below the back bar when pushed. */
    val detail: UIContainer = UIContainer()

    init {
        master.constrain {
            x = 0.pixels(); y = 0.pixels()
            width = basicWidthConstraint { if (stacked) this@BrassMasterDetail.getWidth() else (this@BrassMasterDetail.getWidth() - detailW() - BrassSpacing.GAP).coerceAtLeast(0f) }
            height = 100.percent()
        } childOf this
        host.constrain {
            x = basicXConstraint { if (stacked) this@BrassMasterDetail.getLeft() else master.getRight() + BrassSpacing.GAP }
            y = 0.pixels()
            width = basicWidthConstraint { if (stacked) this@BrassMasterDetail.getWidth() else detailW() }
            height = 100.percent()
        } childOf this
        back.constrain {
            // inset by the keycap's bleed, which the host's clip would otherwise shave
            x = BrassWidget.BLEED_X.pixels(); y = BrassWidget.BLEED_TOP.pixels()
            width = basicWidthConstraint { c -> if (stacked) minOf(back.labelWidth() + 4f, c.parent.getWidth() - BrassWidget.BLEED_X * 2) else 0f }
            height = basicHeightConstraint { if (stacked) BACK_H else 0f }
        } childOf host
        detail.constrain {
            x = 0.pixels()
            y = basicYConstraint { c -> c.parent.getTop() + (if (stacked) BACK_BAR else 0f) }
            width = 100.percent()
            height = basicHeightConstraint { c -> c.parent.getHeight() - (if (stacked) BACK_BAR else 0f) }
        } childOf host
    }

    /**
     * Side by side, the detail's column. One column, the master's whole area: empty until [open], then the
     * detail slides in from the right over a dimmed master - solid, like a phone screen - and back out.
     */
    private inner class Host : UIContainer(), BrassInert, BrassDismissable, BrassLayers.Layer {
        init { enableEffect(ScissorEffect()) }

        // A layer of its own: in one column it is drawn over the master on purpose.
        override val rank: BrassLayers.Rank get() = BrassLayers.Rank.CONTENT

        private fun hidden() = stacked && t <= 0f && !pushed

        override val inert: Boolean get() = hidden()
        override fun dismiss() = close()
        override val escapeDismissable: Boolean get() = detailShown

        override fun isPointInside(x: Float, y: Float): Boolean = !hidden() && super.isPointInside(x, y)
        override fun mouseMove(window: Window) { if (!hidden()) super.mouseMove(window) }

        override fun draw(matrixStack: UMatrixStack) {
            val dt = if (BrassClock.driven) BrassClock.dt else 0.016f
            val target = if (detailShown) 1f else 0f
            t = if (t < target) (t + dt / PUSH_TIME).coerceAtMost(1f) else (t - dt / PUSH_TIME).coerceAtLeast(0f)
            back.label = backLabel()
            if (!stacked) { super.draw(matrixStack); return }
            if (t <= 0f) return
            // No beforeDraw() here: UIContainer.draw calls it, and twice throws in the live render loop.
            val k = BrassMotion.spring(t)
            BrassPaint.rect(matrixStack, getLeft(), getTop(), getRight(), getBottom(), Colors.withAlpha(Colors.UI_BACKGROUND, (200 * k).toInt()))
            val dx = Math.round((1f - k) * getWidth()).toFloat()
            BrassPaint.rect(matrixStack, getLeft() + dx, getTop(), getRight(), getBottom(), Colors.UI_INNER_BG)
            matrixStack.push()
            matrixStack.translate(dx, 0f, 0f)
            super.draw(matrixStack)
            matrixStack.pop()
        }
    }

    companion object : BrassDemoSource {
        const val BACK_H = 18f
        /** The back bar's full height: the key, its bleed above and lip below, a small gap. */
        const val BACK_BAR = BACK_H + BrassWidget.BLEED_TOP + BrassWidget.BLEED_BOTTOM + BrassSpacing.TIGHT
        private const val PUSH_TIME = 0.45f

        override fun demo() = BrassDemo("master-detail", "Master / detail", 230f, 140f) {
            val md = BrassMasterDetail(detailWidth = { 120f }, minMaster = 140f)
            md.backLabel = { "Servers" }
            val list = BrassScrollArea.list().constrain { width = 100.percent(); height = 100.percent() } childOf md.master
            val panel = BrassPanel("DETAILS").constrain { width = 100.percent(); height = 100.percent() } childOf md.detail
            val text = BrassWrappedLabel("Pick a server")
            panel.add(text)
            val rows = ArrayList<BrassListRow>()
            listOf("survival.example.net", "creative.example.net", "minigames.example.net").forEach { name ->
                val row = BrassListRow(name, "1.21.1", BrassIcons.SERVER) { r ->
                    BrassListRow.selectOnly(rows, r)
                    text.text = r.title
                    md.open()
                }
                rows += row
                list.add(row)
            }
            md
        }
    }
}
