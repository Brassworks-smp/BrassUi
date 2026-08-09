@file:Suppress("unused")
package net.swzo.brass.ui.kit.base

import gg.essential.elementa.UIComponent
import net.swzo.brass.ui.kit.base.BrassTree.isDescendantOf

/**
 * Walking the component tree, once.
 * ### Why this exists
 * Eight places in the toolkit had each written the same ancestor loop - "climb `parent` until you
 * find X, guarding against the root, which is its own parent" - and they had settled on **three
 * different termination conditions**: `!hasParent`, `p === node`, and `!p.children.contains(node)`.
 * Only the last is actually correct for "is this still in the tree": Elementa leaves a removed
 * component's `parent` pointing at its old parent, so a subtree detached by a closing popup still
 * climbs all the way to the root and reports itself attached. Two call sites got that right and six
 * did not, which is the kind of divergence that only shows up as a stale tooltip hanging over a
 * window that closed.
 */
object BrassTree {

    fun isDescendantOf(c: UIComponent, ancestor: UIComponent): Boolean {
        var node: UIComponent = c
        while (true) {
            if (node === ancestor) return true
            if (!node.hasParent) return false
            val p = node.parent
            if (p === node) return false
            node = p
        }
    }

    /**
     * Whether [c] is still **genuinely connected** to [root] - every ancestor must actually still
     * list the one below it.
     * The stricter counterpart to [isDescendantOf], and the right question for *lifetime*: a
     * component removed from the tree keeps its stale `parent` pointer, so the cheap walk says yes
     * long after it has stopped being drawn.
     */
    fun isAttachedTo(c: UIComponent, root: UIComponent): Boolean {
        var node: UIComponent = c
        while (true) {
            if (node === root) return true
            if (!node.hasParent) return false
            val p = node.parent
            if (p === node || !p.children.contains(node)) return false
            node = p
        }
    }

    fun isAttached(c: UIComponent): Boolean = isAttachedTo(c, rootOf(c))

    fun rootOf(c: UIComponent): UIComponent {
        var node: UIComponent = c
        while (true) {
            if (!node.hasParent) return node
            val p = node.parent
            if (p === node) return node
            node = p
        }
    }

    fun ancestors(c: UIComponent): Sequence<UIComponent> = sequence {
        var node: UIComponent = c
        while (node.hasParent) {
            val p = node.parent
            if (p === node) break
            yield(p)
            node = p
        }
    }

    fun <T : Any> descendantsOfType(root: UIComponent, type: Class<T>): List<T> {
        val out = ArrayList<T>()
        fun walk(c: UIComponent) {
            for (child in c.children) {
                if (type.isInstance(child)) out.add(type.cast(child))
                walk(child)
            }
        }
        walk(root)
        return out
    }
}
