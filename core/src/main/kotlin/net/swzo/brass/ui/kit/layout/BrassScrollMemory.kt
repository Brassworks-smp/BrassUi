package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.base.BrassTree

/**
 * Where the scroll views under a component are scrolled to, so a subtree that has to be rebuilt (new
 * data with a different shape) comes back where the player was rather than at the top.
 *
 * ```kotlin
 * val at = BrassScrollMemory.capture(page)
 * page.clearChildren(); build(page)
 * BrassScrollMemory.restore(page, at)
 * ```
 * Scroll views are matched by their order in the tree (the same build makes the same order). A
 * freshly built view has no content height until it is laid out, so [restore] keeps trying for a few
 * frames until the offset sticks.
 */
object BrassScrollMemory {

    /** The vertical offset of each scroll view under [root], in tree order. */
    @JvmStatic
    fun capture(root: UIComponent): List<Float> =
        BrassTree.descendantsOfType(root, ScrollComponent::class.java).map { it.verticalOffset }

    /** Scrolls the views under [root] back to [offsets] (from [capture]) once they can be. */
    @JvmStatic
    fun restore(root: UIComponent, offsets: List<Float>) {
        if (offsets.none { it != 0f }) return
        Restorer(offsets).constrain { width = 0.pixels(); height = 0.pixels() } childOf root
    }

    /** Invisible: retries each frame until every view is at its offset, then removes itself. */
    private class Restorer(private val offsets: List<Float>) : UIContainer() {
        private var frames = 0
        private var gone = false

        override fun animationFrame() {
            super.animationFrame()
            if (gone) return
            val root = parent
            val views = BrassTree.descendantsOfType(root, ScrollComponent::class.java)
            var done = true
            views.forEachIndexed { i, v ->
                val want = offsets.getOrNull(i) ?: return@forEachIndexed
                if (kotlin.math.abs(v.verticalOffset - want) > 0.5f) {
                    v.scrollTo(verticalOffset = want, smoothScroll = false)
                    if (kotlin.math.abs(v.verticalOffset - want) > 0.5f) done = false
                }
            }
            if (done || ++frames > MAX_FRAMES) {
                gone = true
                // Not mid-iteration: the tree is walking its children right now.
                gg.essential.elementa.components.Window.enqueueRenderOperation { root.removeChild(this) }
            }
        }
    }

    private const val MAX_FRAMES = 20
}
