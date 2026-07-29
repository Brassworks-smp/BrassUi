package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color

/**
 * The anatomy of a tree row: how far it indents, where its expand glyph sits, and the hairlines that
 * connect it to its ancestors.
 *
 * ### Why this exists
 *
 * The dev inspector's `TreeRow` worked this out once - indent per level, a `+`/`-` glyph, and one
 * vertical guide per ancestor drawn the full height of the row so consecutive rows tile them into
 * continuous lines. [net.swzo.brass.ui.kit.surface.BrassTreeView] needs the identical picture, and a
 * tree drawn to *nearly* the same metrics as another tree in the same UI is worse than either.
 *
 * The guides are the part worth sharing, not the row: the inspector's rows are components (they carry
 * hover highlighting and a fade-out animation), while a `BrassTreeView` paints its rows directly. Only
 * the geometry and the ink are common.
 */
object BrassTreeGuides {

    /** Horizontal step per nesting level. */
    const val INDENT = 10f

    /** Horizontal centre of the glyph within its indent column - where a guide line sits. */
    const val GLYPH_MID = 4f

    /** Left margin before the first level. */
    const val LEFT_PAD = 3f

    /** Width the glyph column occupies when a node has children. */
    const val GLYPH_W = 12f

    private val GUIDE: Color get() = Colors.TREE_GUIDE

    /** Where a row's own content (its glyph, or its label when it has no children) begins. */
    fun contentX(x: Float, depth: Int): Float = x + LEFT_PAD + depth * INDENT

    /** Where a row's label begins, after the glyph column if there is one. */
    fun labelX(x: Float, depth: Int, hasChildren: Boolean): Float =
        contentX(x, depth) + if (hasChildren) GLYPH_W else 0f

    /**
     * Draw the ancestor hairlines for a row at [depth], spanning the row's full height so consecutive
     * rows join into continuous verticals.
     *
     * Without these, a name indented four levels deep is just a name a bit further right - there is
     * nothing to count.
     */
    fun drawGuides(m: UMatrixStack, x: Float, y: Float, height: Float, depth: Int, alpha: Float = 1f) {
        for (d in 0 until depth) {
            val gx = x + LEFT_PAD + d * INDENT + GLYPH_MID
            BrassPaint.rect(m, gx, y, gx + 1f, y + height, BrassPaint.fade(GUIDE, alpha))
        }
    }

    /**
     * Draw the expand/collapse glyph for a node, and - when it is expanded - the stub of guide that
     * drops from it toward its children.
     *
     * The stub matters: it makes the line you follow down start at the node it belongs to, rather than
     * appearing from nowhere one row later.
     */
    fun drawGlyph(
        m: UMatrixStack,
        component: UIComponent,
        x: Float,
        y: Float,
        height: Float,
        depth: Int,
        expanded: Boolean,
        color: Color,
        alpha: Float = 1f,
    ) {
        val cx = contentX(x, depth)
        val ty = y + (height - BrassFont.LINE) / 2f
        BrassFont.draw(m, component, if (expanded) "-" else "+", cx + 2f, ty, BrassPaint.fade(color, alpha), false)
        if (expanded) {
            val gx = cx + GLYPH_MID
            BrassPaint.rect(m, gx, ty + BrassFont.LINE, gx + 1f, y + height, BrassPaint.fade(GUIDE, alpha))
        }
    }
}
