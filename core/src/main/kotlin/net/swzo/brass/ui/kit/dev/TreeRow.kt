package net.swzo.brass.ui.kit.dev

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.layout.BrassTreeGuides
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassTag
import java.awt.Color

/**
 * One tree row: an expand/collapse glyph (when the node has children), the element's name, and a
 * coloured type tag. Hovering highlights the element on screen; clicking the row selects it (feeding
 * [DevDetails]); clicking the glyph expands or collapses it.
 */
internal class TreeRow(
    val target: UIComponent,
    val depth: Int,
    val hasChildren: Boolean,
) : BrassWidget(BrassAccent.DEFAULT), BrassDevOverlay {

    private val name = target.javaClass.simpleName.ifEmpty { "anon" }

    /** Set while this row's branch is folding away; fades the row out before the tree rebuilds. */
    var leaving = false
    private var leave = 1f

    /** Whether [target] sits under [node] in the component tree - used to pick the rows to fade. */
    fun isUnder(node: UIComponent): Boolean = BrassTree.isDescendantOf(target, node)

    init {
        chrome = BrassChrome.NONE
        // Off by default; DevTree turns it on for the rebuild that follows an expand. The base class's
        // entrance then gives each row a fade and a short rise, staggered down the list, so a branch
        // unfolds instead of a block of text appearing between two frames.
        entranceEnabled = false
        clickable = true
        onMouseEnter { BrassDevMode.setTreeHighlight(target) }
        onMouseLeave { BrassDevMode.clearTreeHighlight(target) }
        onMouseClick { e ->
            if (e.mouseButton != 0) return@onMouseClick
            val glyphStart = 3f + depth * INDENT
            if (hasChildren && e.relativeX >= glyphStart && e.relativeX <= glyphStart + 11f) {
                BrassDevMode.toggleCollapse(target)
            } else {
                BrassDevMode.select(target)
            }
        }
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        if (leaving) leave = (leave - BrassClock.dt * LEAVE_SPEED).coerceAtLeast(0f)
        val a = leave * entranceFade
        if (a <= 0.01f) return

        fun fill(x1: Float, y1: Float, x2: Float, y2: Float, c: Color) {
            BrassStats.quad()
            UIBlock.drawBlock(m, withAlpha(c, a), x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble())
        }

        val selected = BrassDevMode.selectedComponent === target
        if (selected) fill(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), Colors.UI_SELECTION)
        else if (hoveredState) fill(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), Colors.UI_SELECTION_FAINT)

        // Indent guides and the expand glyph come from BrassTreeGuides, so the inspector and a
        // BrassTreeView draw the same tree rather than two trees that nearly agree.
        BrassTreeGuides.drawGuides(m, x.toFloat(), y.toFloat(), h.toFloat(), depth, a)

        val cx = BrassTreeGuides.labelX(x.toFloat(), depth, hasChildren)
        val ty = y + (h - BrassFont.LINE) / 2f

        if (hasChildren) {
            BrassTreeGuides.drawGlyph(
                m, this, x.toFloat(), y.toFloat(), h.toFloat(), depth,
                expanded = !BrassDevMode.isCollapsed(target),
                color = Colors.UI_TEXT_DARK,
                alpha = a,
            )
        }
        val color = if (selected || hoveredState) Colors.UI_TEXT_HOVER else Colors.UI_TEXT
        BrassFont.draw(m, this, name, cx, ty, withAlpha(color, a), false)

        val (tagLabel, tagColor) = tagFor(target)
        val tagX = cx + BrassFont.width(this, name) + 6f
        val tagY = y + (h - BrassTag.HEIGHT) / 2f
        BrassTag.drawPill(m, this, tagX, tagY, tagLabel, tagColor, a)
    }

    companion object {
        /** Kept as an alias so the row's hit-testing reads the same as its painting. */
        const val INDENT = BrassTreeGuides.INDENT
        /** How fast a folding branch fades out. */
        const val LEAVE_SPEED = 7f
        /** How long the tree waits for that fade before rebuilding, in nanoseconds. */
        const val LEAVE_NANOS = 150_000_000L
    }
}
