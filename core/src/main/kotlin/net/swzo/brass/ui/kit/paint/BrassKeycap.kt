package net.swzo.brass.ui.kit.paint

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import java.awt.Color

/**
 * The toolkit's raised **keycap** chrome, in one place: a flat fill, a 1-px inner border on all four
 * sides, a 1-px outer ring just outside it, and a 2–4-px bottom edge (a soft shadow plus a coloured or
 * dark lip) so the control reads as a physical pixel button sitting above the surface.
 *
 * ### Why this is extracted
 *
 * This is the exact fill/border/ring/lip stack that used to live privately inside
 * [net.swzo.brass.ui.kit.base.BrassWidget.drawContent] and *define* the toolkit's look. Pulling it out
 * lets a composite that paints its own keycap-shaped regions - the node editor's node headers, its port
 * nubs and its inline controls - draw the **identical** keycap rather than re-deriving a lookalike that
 * would drift. [net.swzo.brass.ui.kit.base.BrassWidget] now calls straight through here, so there is one
 * definition of a keycap and everything wears it.
 *
 * All bounds are floats and every offset is in the same units as the bounds, so a caller drawing under a
 * scaled matrix (the node canvas at some zoom) gets a keycap whose ring and lip scale at the same rate as
 * its body - which is the whole point of drawing it through the matrix rather than at fixed pixel sizes.
 */
object BrassKeycap {

    /**
     * Paint a keycap filling `[x,y] .. [x+w, y+h]`.
     *
     * @param bg fill colour (skipped when [transparent]).
     * @param border the 1-px inner border on all four sides.
     * @param outer the near-black outer ring and lip base.
     * @param bottom the coloured bottom lip, used only for a non-[defaultAccent] keycap.
     * @param flat drop the outer ring and the bottom lip - a control that sits *in* the surface.
     * @param defaultAccent neutral keycap (dark lip) vs a coloured accent keycap (coloured lip).
     * @param lip pixels the bottom lip has lost to a press, so a pressed key looks compressed.
     */
    fun draw(
        m: UMatrixStack,
        x: Float, y: Float, w: Float, h: Float,
        bg: Color, border: Color, outer: Color, bottom: Color,
        transparent: Boolean = false,
        flat: Boolean = false,
        defaultAccent: Boolean = true,
        lip: Float = 0f,
    ) {
        if (w <= 0f || h <= 0f) return
        fun fill(x1: Float, y1: Float, x2: Float, y2: Float, c: Color) = BrassPaint.rect(m, x1, y1, x2, y2, c)

        if (!transparent) fill(x, y, x + w, y + h, bg)
        fill(x, y, x + w, y + 1f, border)                 // top
        fill(x, y + h - 1f, x + w, y + h, border)         // bottom
        fill(x, y, x + 1f, y + h, border)                 // left
        fill(x + w - 1f, y, x + w, y + h, border)         // right
        if (flat) return

        fill(x - 1f, y - 1f, x + w + 1f, y, outer)        // outer top
        fill(x - 1f, y, x, y + h, outer)                  // outer left
        fill(x + w, y, x + w + 1f, y + h, outer)          // outer right

        val shadow = Colors.SOFT_SHADOW
        fill(x, y + h + 1f, x + w, y + h + 4f - lip, shadow)
        fill(x - 1f, y + h, x + w + 1f, y + h + 3f - lip, outer)
        if (defaultAccent) {
            fill(x, y + h, x + w, y + h + 2f - lip, bg)
            fill(x, y + h, x + w, y + h + 2f - lip, shadow)
        } else {
            fill(x, y + h, x + w, y + h + 2f - lip, bottom)
        }
    }
}
