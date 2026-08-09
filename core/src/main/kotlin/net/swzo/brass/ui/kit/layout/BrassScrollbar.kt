package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.base.BrassEased
import net.swzo.brass.ui.kit.layout.BrassScrollbar.Companion.WIDTH
import net.swzo.brass.ui.kit.layout.BrassScrollbar.Companion.attach
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import java.awt.Color
import kotlin.math.roundToInt

/**
 * A scrollbar in the toolkit's grammar: a thin recessed track with a flat grip that brightens toward
 * brass on hover or while dragged.
 * Elementa drives this - [ScrollComponent.setVerticalScrollBarComponent] positions and sizes the grip
 * to reflect the scroll offset, and hides it when everything already fits. All this type does is
 * paint; the geometry is Elementa's.
 * Build one with [attach], which wires the track, the grip and the scroll view together.
 */
class BrassScrollbar : UIContainer() {

    private val grip = Grip()

    private var scroll: ScrollComponent? = null

    var alwaysShow: Boolean = false

    init {
        grip.constrain { x = 0.pixels(); width = 100.percent() } childOf this
    }

    override fun draw(matrixStack: UMatrixStack) {
        // no beforeDraw() - UIContainer.draw already calls it (see BrassFlow for the full note)
        val x = getLeft().roundToInt(); val y = getTop().roundToInt()
        val x2 = getRight().roundToInt(); val y2 = getBottom().roundToInt()

        // One measurement decides both parts, every frame. Elementa's own `hideWhenUseless` decides
        // from the scroll view's cached content height, which is not recomputed when a *resize* makes
        // the content wrap taller - so widening a row into an overflow left the bar hidden. Measuring
        // the children ourselves is resize-proof, and driving the grip from the same answer as the
        // track means the two can never disagree about whether the bar exists.
        val visible = alwaysShow || overflows()
        grip.visible = visible
        if (visible && x2 > x && y2 > y) {
            // A barely-there track: the previous one was a hard black groove with a border, which
            // drew more attention than the content it was scrolling. Now a faint wash, no edge.
            BrassPaint.rect(matrixStack, x, y, x2, y2, TRACK)
        }
        super.draw(matrixStack)
    }

    private var overflowFrame = -1L
    private var overflowResult = false

    private fun overflows(): Boolean {
        if (overflowFrame == BrassClock.frame) return overflowResult
        overflowFrame = BrassClock.frame
        overflowResult = needsScrolling()
        return overflowResult
    }

    private fun needsScrolling(): Boolean {
        val s = scroll ?: return true
        val kids = s.allChildren
        if (kids.isEmpty()) return false
        var top = Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (c in kids) {
            if (c.getTop() < top) top = c.getTop()
            if (c.getBottom() > bottom) bottom = c.getBottom()
        }
        return (bottom - top) > s.getHeight() + 0.5f
    }

    private class Grip : UIComponent() {
        private var hovered = false
        private val glowValue = BrassEased(0f, speed = GLOW_SPEED)

        var visible = true

        init {
            onMouseEnter { hovered = true }
            onMouseLeave { hovered = false }
        }

        override fun draw(matrixStack: UMatrixStack) {
            beforeDraw(matrixStack)
            glowValue.target = if (hovered) 1f else 0f
            val glow = glowValue.advance()

            val x = getLeft(); val y = getTop()
            val x2 = getRight(); val y2 = getBottom()
            // The grip is the toolkit's shared handle - the same one the slider and toggle ride.
            if (visible && x2 > x && y2 > y) BrassCard.grip(matrixStack, x, y, x2, y2, glow)
            super.draw(matrixStack)
        }
    }

    companion object {
        private const val GLOW_SPEED = 12f
        private val TRACK: Color get() = Colors.SCROLL_TRACK

        const val WIDTH = 3f

        /**
         * Attach a scrollbar to [scroll], laid out inside [parent] along the scroll view's right edge,
         * and return it. The bar hides itself whenever the content already fits, unless [alwaysShow].
         * Note the scroll view must leave room for it - reserve [WIDTH] plus a small gap in the
         * scroll's own width, or the bar will sit on top of the content.
         */
        fun attach(
            parent: UIComponent,
            scroll: ScrollComponent,
            width: Float = WIDTH,
            alwaysShow: Boolean = false,
        ): BrassScrollbar {
            val bar = BrassScrollbar()
            bar.scroll = scroll
            bar.alwaysShow = alwaysShow
            bar.constrain {
                x = gg.essential.elementa.dsl.basicXConstraint { scroll.getRight() + 2f }
                y = gg.essential.elementa.dsl.basicYConstraint { scroll.getTop() }
                this.width = width.pixels()
                height = gg.essential.elementa.dsl.basicHeightConstraint { scroll.getHeight() }
            } childOf parent
            // hideWhenUseless is left OFF: the bar decides visibility itself, from a measurement that
            // survives a resize. Letting Elementa hide the grip too would reintroduce the stale-height
            // bug for the grip alone, so the track showed with no handle in it.
            scroll.setVerticalScrollBarComponent(bar.grip, hideWhenUseless = false)
            return bar
        }
    }
}
