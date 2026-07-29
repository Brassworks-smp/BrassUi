package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.effects.ScissorEffect
import net.swzo.brass.ui.kit.layout.BrassCull.clipOf
import net.swzo.brass.ui.kit.layout.BrassCull.rectVisible
import net.swzo.brass.ui.kit.layout.BrassCull.visible
import net.swzo.brass.ui.kit.surface.BrassTable
import net.swzo.brass.ui.kit.text.BrassMarkdown

/**
 * Visibility culling for content a component paints **itself**.
 *
 * ### What Elementa already does - and why the widget-level cull was removed
 *
 * `UIComponent.draw` tests every child with `Window.isAreaVisible(...)` and skips its `draw`
 * entirely unless it passes. That test covers both the screen bounds *and* the active
 * `ScissorEffect`, which is exactly what a `ScrollComponent` installs while drawing its contents.
 * Off-screen and scrolled-away **components** are therefore already culled by the framework, one
 * level higher than any check inside a widget could sit.
 *
 * An earlier version of this file added a per-widget check in `BrassWidget.draw`. It was redundant:
 * a culled widget's `draw` is never called, so the check never ran for anything it was meant to
 * catch - which is precisely why the dev overlay reported a 0% cull rate. That reading was correct
 * and the code was wrong.
 *
 * ### What is still worth culling
 *
 * Elementa can only cull whole components. A component that paints *many pieces itself* - the lines
 * of a [BrassMarkdown] document, the rows of a [BrassTable] - is a single child as far as the
 * framework is concerned, so it gets drawn in full whenever any part of it is on screen. Those
 * loops are where this class earns its keep: fetch [clipOf] once per frame, then test each piece
 * with [rectVisible].
 */
object BrassCull {

    /** Master switch, for A/B testing the internal culling in game. */
    var enabled: Boolean = true

    /**
     * The effective clip rect for [c] as `[left, top, right, bottom]`: the root's bounds intersected
     * with every scrolling ancestor's.
     *
     * Fetch this **once** per frame in a component that draws many pieces, rather than per piece -
     * it walks the ancestor chain, and doing that per row of a long table would undo the saving.
     */
    fun clipOf(c: UIComponent): BrassRect = clipInto(c, BrassRect.infinite())

    /**
     * As [clipOf], but writing into [out] - for a caller holding a scratch rectangle across frames,
     * which is every caller that walks a long list.
     */
    fun clipInto(c: UIComponent, out: BrassRect): BrassRect {
        out.mutate(
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
        )

        var node: UIComponent = c
        while (true) {
            val parent = node.parent
            if (parent === node) {
                out.intersect(node.getLeft(), node.getTop(), node.getRight(), node.getBottom())
                break
            }
            // A scroll view clips, and so does anything carrying a ScissorEffect - a collapsed
            // BrassWindow's body, a table. Testing for the effect as well as the type is what makes
            // this agree with what is actually on screen: checking only for ScrollComponent reported
            // widgets inside a rolled-up window as visible, which is how their tooltips kept popping
            // up over a window that showed nothing.
            if (parent is ScrollComponent || parent.effects.any { it is ScissorEffect }) {
                out.intersect(parent.getLeft(), parent.getTop(), parent.getRight(), parent.getBottom())
            }
            node = parent
        }

        return out
    }

    /** Whether a rectangle overlaps a clip fetched from [clipOf]. */
    fun rectVisible(clip: BrassRect, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        if (!enabled) return true
        return clip.overlaps(left, top, right, bottom)
    }

    /** Whether [c]'s own box, expanded by [bleed], overlaps its clip. */
    fun visible(c: UIComponent, bleed: Float = 0f): Boolean {
        if (!enabled) return true
        val clip = clipInto(c, scratch)
        return rectVisible(clip, c.getLeft() - bleed, c.getTop() - bleed, c.getRight() + bleed, c.getBottom() + bleed)
    }

    /**
     * Reused by [visible], which is called once a frame per hovered component and has no reason to
     * allocate. Safe because the UI draws on one thread and the result is consumed immediately.
     */
    private val scratch = BrassRect.infinite()
}
