@file:Suppress("unused")
package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import net.swzo.brass.ui.kit.base.BrassTree
import net.swzo.brass.ui.kit.surface.BrassLayers.raise

/**
 * Who sits above whom among a screen's floating layers.
 * ### Why this exists
 * Z-order was decided in three files that each knew a piece of the rule: [BrassPopup] raised itself
 * on a click but had to stop below any modal scrim; [BrassContextMenu] had `keepOnTop`, called by
 * the popup after it raised, to stop a menu being buried by the very popup it was opened from;
 * [net.swzo.brass.ui.kit.surface.BrassToast] and the tooltip opted out of the tree entirely. The
 * ordering rule existed, but it was distributed across the objects it governed, so adding a fourth
 * kind of layer meant finding and amending all three.
 * Here the rule is stated once, as a rank.
 * ### Every reorder goes through [raise]
 * Elementa draws floating components in **tree child order**, so whoever reorders children decides
 * the stack - and any code that appends itself with a bare `childOf` jumps to the very top no matter
 * what its rank claims. That is exactly what the popup's own raise-on-click did, which is why a toast
 * or the command palette was on top only until the next click on any window: the rank system said
 * one thing and the popup's private reshuffle said another, and the reshuffle ran later. A layer
 * that wants to be on top asks [raise]; nothing appends itself past a layer that outranks it.
 */
object BrassLayers {

    /**
     * How high a layer sits. Higher ranks draw over lower ones, and a layer never rises above one
     * that outranks it however recently it was clicked.
     */
    enum class Rank {
        CONTENT,

        POPUP,

        MODAL,

        /** Transient chrome - a context menu, a dropdown popover, the command palette. Above every
         * window, including a modal: it is dismissed by the first click anywhere else, so there is no
         * state it can strand by covering something. */
        TRANSIENT,

        /**
         * Notifications - the toast column. Above absolutely everything: a toast is the one layer
         * that must stay visible *because* something else has the screen, and it holds no focus and
         * blocks no clicks outside its own chip, so nothing is ever trapped beneath it.
         */
        OVERLAY,
    }

    interface Layer {
        val rank: Rank
    }

    fun rankOf(c: UIComponent): Rank = when {
        c is Layer -> c.rank
        c is BrassContextMenu -> Rank.TRANSIENT
        c is BrassPopup && c.modal -> Rank.MODAL
        c is BrassPopup -> Rank.POPUP
        else -> Rank.CONTENT
    }

    fun raise(root: UIComponent, layer: UIComponent) {
        if (!root.children.contains(layer)) return
        val rank = rankOf(layer)
        val blocker = root.children.firstOrNull { it !== layer && rankOf(it) > rank }
        if (blocker == null && root.children.lastOrNull() === layer) return
        root.removeChild(layer)
        if (blocker != null && root.children.contains(blocker)) root.insertChildBefore(layer, blocker)
        else root.addChild(layer)
    }

    fun topmost(root: UIComponent, minRank: Rank = Rank.CONTENT): UIComponent? =
        BrassTree.descendantsOfType(root, UIComponent::class.java)
            .lastOrNull { rankOf(it) >= minRank }
}
