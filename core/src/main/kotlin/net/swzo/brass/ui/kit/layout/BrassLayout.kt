@file:Suppress("unused")
package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.HeightConstraint
import gg.essential.elementa.constraints.WidthConstraint
import gg.essential.elementa.constraints.YConstraint
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicYConstraint
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.layout.BrassLayout.contentHeight
import net.swzo.brass.ui.kit.layout.BrassLayout.contentWidth

/**
 * Layout constraints for building reactive, web-like UIs on Elementa.
 * These exist because Elementa's `ChildBasedSizeConstraint` **sums** its children's sizes, which is
 * only correct for a stack of siblings flowing one after another. The moment children are placed at
 * explicit offsets (`basicYConstraint`, a wrapping [BrassFlow], anything absolutely positioned) the sum
 * no longer describes the area they occupy, and a container measured that way comes out the wrong
 * size - most visibly as a scroll view that stops a few pixels short of its own content.
 * [contentHeight] / [contentWidth] measure the real extent instead: the far edge of the furthest
 * child, relative to the container's own origin. They also add the keycap **bleed**, because a
 * [BrassWidget] paints outside its box (outer ring, bottom lip, hover lift) and that ink is part of the
 * content as far as clipping and scrolling are concerned.
 * ### Qualify `getWidth()` and `getHeight()` inside a `constrain` block
 * This one is silent, and it is a stack overflow rather than a wrong number:
 * ```kotlin
 * child.constrain {
 *     width = basicWidthConstraint { getWidth() - PAD }   // WRONG - recurses forever
 *     width = basicWidthConstraint { this@MyWidget.getWidth() - PAD }   // right
 * }
 * ```
 * `constrain`'s receiver is `UIConstraints`, which has its own no-arg `getWidth()` and `getHeight()`.
 * Those are *nearer in scope* than the enclosing widget's, so an unqualified call resolves to **the
 * child's own constraint** - which is the very constraint being defined. Elementa evaluates it,
 * re-enters it, and the stack dies with `Cyclic constraint structure detected!`.
 * `getLeft()` and `getTop()` happen to be safe (`UIConstraints` has no such methods), which makes the
 * bug worse: a block of four constraints written the same way has two that work and two that do not.
 * Qualify all four.
 * **Do not size a child as a percentage of the same axis you are measuring.** `contentHeight` asks
 * its children how tall they are; a child whose height is `100.percent()` asks its parent the same
 * question straight back, and Elementa will recurse until the stack overflows. Give such children a
 * pixel or content-derived size, or measure the other axis. The same applies to [contentWidth] and
 * percentage widths - which is why the popup and showcase measure height this way but set width
 * explicitly.
 */
object BrassLayout {

    fun spanningChildrenHeight(extra: Float = BrassWidget.BLEED_BOTTOM): HeightConstraint =
        basicHeightConstraint { c ->
            val kids = c.children
            if (kids.isEmpty()) extra
            else (kids.maxOf { it.getBottom() } - c.getTop() + extra).coerceAtLeast(0f)
        }

    fun spanningChildrenWidth(extra: Float = BrassWidget.BLEED_X): WidthConstraint =
        basicWidthConstraint { c ->
            val kids = c.children
            if (kids.isEmpty()) extra
            else (kids.maxOf { it.getRight() } - c.getLeft() + extra).coerceAtLeast(0f)
        }

    // These constraints look like an obvious candidate for a per-frame memo: they ask every child for
    // a resolved edge, Elementa evaluates a size constraint several times a frame, and the answer
    // "cannot change until the next layout".
    // That last part is false, and caching on it broke real layouts. Children here are positioned by
    // SiblingConstraint, whose y depends on the previous sibling's height, which may itself be
    // child-derived. Elementa resolves that by **re-evaluating until it converges within the frame**.
    // A memo returns the first answer - taken before the children have positions - and pins it for
    // the whole frame, so a box measures ~0 tall and its contents vanish. It self-corrects on the next
    // frame only if something asks again, which for a settled popup it does not.
    // The saving was never measured, and it cost the toolkit a class of bug that is very hard to see:
    // content that is simply not there. If this is ever worth revisiting, the cache has to be keyed on
    // something that actually changes when the layout does, the way BrassFlow keys on its width.

    fun contentHeight(extra: Float = BrassWidget.BLEED_BOTTOM): HeightConstraint =
        spanningChildrenHeight(extra)

    fun contentWidth(extra: Float = BrassWidget.BLEED_X): WidthConstraint =
        spanningChildrenWidth(extra)

    fun below(sibling: UIComponent, gap: Float = 6f): YConstraint =
        basicYConstraint { sibling.getBottom() + gap }

    fun tallestChildHeight(extra: Float = BrassWidget.BLEED_BOTTOM): HeightConstraint =
        spanningChildrenHeight(extra)

    /**
     * A width that fills the parent minus [reserve] on the right - the gutter a scrollbar sits in, or
     * room kept for a pinned control. Never negative.
     * This is the `100.percent() - (BrassScrollbar.WIDTH + 3f).pixels()` every scrolling list wrote by
     * hand; [BrassScrollArea] uses it so a list never has to name the number.
     */
    fun fillWidthMinus(reserve: Float): WidthConstraint = basicWidthConstraint { c ->
        (c.getParentWidthSafe() - reserve).coerceAtLeast(0f)
    }

    private fun UIComponent.getParentWidthSafe(): Float = if (hasParent) parent.getWidth() else 0f

    /**
     * A height that fills from the component's own top down to [floor]'s top, less [gap] - so a
     * region grows and shrinks with the space actually available above a pinned footer instead of
     * running underneath it. Never returns a negative height.
     * This is the reactive counterpart to bottom-pinning a footer: pin the footer, then give the
     * region above it this height, and the two can never overlap at any window size.
     */
    fun fillAbove(floor: UIComponent, gap: Float = 8f): HeightConstraint = basicHeightConstraint { c ->
        (floor.getTop() - c.getTop() - gap).coerceAtLeast(0f)
    }
}
