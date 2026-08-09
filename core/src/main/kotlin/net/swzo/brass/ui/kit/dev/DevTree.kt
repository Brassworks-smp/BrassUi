package net.swzo.brass.ui.kit.dev

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.layout.BrassScrollbar
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassTag

/**
 * The scrolling UI tree. Rebuilt about once a second (so a newly opened popup shows up) and immediately
 * whenever a node is expanded or collapsed. Rows honour the per-node collapse state held by
 * [BrassDevMode].
 */
class DevTree : UIContainer(), BrassDevOverlay {

    private val inner: UIContainer
    private var root: UIComponent? = null
    private var lastBuild = 0L
    private var dirty = true
    private var rowCount = 0
    private var contentW = 0f

    init {
        // Horizontal scroll as well as vertical: a deeply-nested node's name and tag run past the panel's
        // right edge, and shift+scroll now reaches them instead of the text simply being clipped.
        val scroll = ScrollComponent(
            horizontalScrollEnabled = true,
            verticalScrollEnabled = true,
        ).constrain {
            x = 6.pixels(); y = 4.pixels()
            width = 100.percent() - 12.pixels()
            height = 100.percent() - 8.pixels()
        } childOf this
        inner = UIContainer().constrain {
            x = 0.pixels(); y = 0.pixels()
            // At least as wide as the scroll (so short rows fill it and the hover band spans the panel),
            // and wider when a row overflows - that overflow is what the horizontal scroll reveals.
            width = basicWidthConstraint { c -> maxOf(contentW, c.parent.getWidth()) }
            height = basicHeightConstraint { rowCount * ROW }
        } childOf scroll
        BrassScrollbar.attach(this, scroll)
    }

    private var collapsing: UIComponent? = null
    private var collapseAt = 0L

    private var animateNext = false

    fun bind(newRoot: UIComponent) { if (root !== newRoot) { root = newRoot; dirty = true } }
    fun markDirty() { dirty = true }

    fun markDirtyAnimated() { dirty = true; animateNext = true }

    fun beginCollapse(node: UIComponent) {
        collapsing = node
        collapseAt = System.nanoTime()
        for (row in inner.children) {
            if (row is TreeRow && row.target !== node && row.isUnder(node)) row.leaving = true
        }
    }

    override fun draw(matrixStack: UMatrixStack) {
        val now = System.nanoTime()
        if (collapsing != null) {
            // hold the rebuild off until the fade lands, then fall through to it
            if (now - collapseAt >= TreeRow.LEAVE_NANOS) { collapsing = null; dirty = true }
        }
        // The periodic refresh is suspended mid-fold: rebuilding on the one-second tick would wipe the
        // fading rows out from under the animation.
        val periodic = collapsing == null && now - lastBuild > 1_000_000_000L
        if (dirty || periodic) { dirty = false; lastBuild = now; rebuild() }
        super.draw(matrixStack)
    }

    private fun rebuild() {
        val r = root ?: return
        val animate = animateNext
        animateNext = false
        inner.clearChildren()
        rowCount = 0
        contentW = 0f
        addRows(r, 0, animate)
    }

    private fun rowWidth(c: UIComponent, depth: Int, hasChildren: Boolean): Float {
        val name = c.javaClass.simpleName.ifEmpty { "anon" }
        var w = 3f + depth * TreeRow.INDENT
        if (hasChildren) w += 12f
        w += BrassFont.width(this, name) + 6f
        val (label, _) = tagFor(c)
        w += BrassTag.measure(this, label)
        return w + 4f
    }

    private fun addRows(c: UIComponent, depth: Int, animate: Boolean) {
        if (c is BrassDevOverlay) return
        val kids = c.children.filter { it !is BrassDevOverlay }
        contentW = maxOf(contentW, rowWidth(c, depth, kids.isNotEmpty()))
        TreeRow(c, depth, kids.isNotEmpty()).also { it.entranceEnabled = animate }.constrain {
            x = 0.pixels(); y = SiblingConstraint()
            width = 100.percent(); height = ROW.pixels()
        } childOf inner
        rowCount++
        if (rowCount >= MAX_ROWS) return
        if (!BrassDevMode.isCollapsed(c)) {
            for (child in kids) {
                addRows(child, depth + 1, animate)
                if (rowCount >= MAX_ROWS) return
            }
        }
    }

    companion object {
        val ROW = BrassFont.LINE + 6f
        const val MAX_ROWS = 800
    }
}
