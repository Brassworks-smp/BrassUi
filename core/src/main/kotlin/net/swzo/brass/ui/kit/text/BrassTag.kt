package net.swzo.brass.ui.kit.text

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.YConstraint
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicYConstraint
import gg.essential.elementa.dsl.constrain
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassTag.Companion.AMBER
import net.swzo.brass.ui.kit.text.BrassTag.Companion.BRASS
import net.swzo.brass.ui.kit.text.BrassTag.Companion.ERROR
import net.swzo.brass.ui.kit.text.BrassTag.Companion.INFO
import net.swzo.brass.ui.kit.text.BrassTag.Companion.PATINA
import net.swzo.brass.ui.kit.text.BrassTag.Companion.SUCCESS
import net.swzo.brass.ui.kit.text.BrassTag.Companion.TRAILING
import net.swzo.brass.ui.kit.text.BrassTag.Companion.WARNING
import net.swzo.brass.ui.kit.text.BrassTag.Companion.roleFor
import java.awt.Color
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A **tag / chip**: a short run of text in a flat outlined rectangle - the top edge of a [BrassCard]
 * and nothing else. No rounded corners, no drop shadow, no raised bottom lip; a tag is a *label with a
 * box round it*, not a control, so it must not read as something you can press.
 *
 * ### Geometry
 *
 * The pill is exactly as tall as a [BrassLabel] ([BrassFont.LINE]), which is what lets a tag sit inline
 * with running text without pushing the line apart. That budget is spent precisely:
 *
 * ```
 *  1 px  outline
 *  1 px  padding
 *  5 px  small-capital ink   <- [INK_H]
 *  1 px  padding
 *  1 px  outline
 * ------
 *  9 px  = BrassFont.LINE
 * ```
 *
 * The text is drawn in **small capitals** (see [BrassSmallCaps]) precisely because their ink is short
 * enough to leave that budget room. A font line box is 9 px tall but small-cap ink only occupies the
 * middle 5 of them, so the glyph run is drawn at the pill's own top and the blank rows above and below
 * the ink *become* the padding and the outline. Drawing the text lower to "make room" would push the
 * ink out of the box.
 *
 * Those glyphs come from a committed bitmap sheet rather than the font, because they exist only in
 * Minecraft's Unicode font pages and the desktop build has no such font - see [BrassSmallCaps].
 * **Digits have a sheet of their own** for the same reason in reverse: they are perfectly available in
 * every font, but at full height, so a label like `1.21` drew its numbers two pixels taller than its
 * letters and sitting on a different baseline - the one place a tag visibly stopped being a single run
 * of text. Anything else still comes from the font, as it always did.
 *
 * ### Colour
 *
 * One [tint] drives the whole chip - outline, a low-alpha wash of the same hue for the fill, and a
 * brightened shade for the text - so a set of tags reads as one palette. Pick tints from the
 * companion: the theme set ([BRASS], [PATINA], [AMBER], …) or the semantic aliases ([SUCCESS],
 * [WARNING], [ERROR], [INFO], …), which are the theme colours under names that say what they mean.
 */
class BrassTag(
    text: String,
    tint: Color = BRASS,
) : BrassWidget(BrassAccent.DEFAULT) {

    /**
     * A tag in one of the named [BrassTagStyle]s - the preferred constructor.
     *
     * `BrassTag("beta", BrassTagStyle.BETA)` says what the chip means; the [Color] overload says what
     * it looks like, and then needs [roleFor]'s identity trick to stay theme-aware.
     */
    constructor(text: String, style: BrassTagStyle) : this(text, style.color) {
        this.style = style
    }

    /**
     * The style this tag was built with, if any. Takes precedence over [tint] and needs no reverse
     * lookup to survive a retheme.
     */
    var style: BrassTagStyle? = null
        set(value) { field = value; if (value != null) tintRole = { value.color } }

    /**
     * The colour the whole pill is derived from. Named `tint` - `UIComponent.getColor` is final.
     *
     * Assigning one of the palette entries above (or any [Colors] role) binds the tag to that role, so
     * it retints with the theme instead of keeping the colour that role happened to have when the tag
     * was built. A literal [Color] is kept exactly as given.
     */
    var tint: Color = tint
        set(value) { field = value; tintRole = roleFor(value) }

    private var tintRole: (() -> Color)? = roleFor(tint)

    /** The colour actually drawn: the bound role's current value, or the fixed [tint]. */
    private val liveTint: Color get() = tintRole?.invoke() ?: tint

    var text: String = text
        set(value) { if (field != value) { field = value; cachedWidth = -1f } }

    private var cachedWidth = -1f

    init {
        // The pill is drawn by drawContent; the keycap base must paint nothing behind it.
        chrome = BrassChrome.NONE
        constrain {
            width = basicWidthConstraint {
                if (cachedWidth < 0f) {
                    cachedWidth = measure(this@BrassTag, this@BrassTag.text)
                }
                cachedWidth
            }
            height = basicHeightConstraint { HEIGHT }
        }
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        // entranceFade is the base class's pop-in; passing it through is what gives a tag the same
        // fade-and-rise as every other widget instead of appearing fully formed mid-cascade.
        drawPill(m, this, x.toFloat(), y.toFloat(), text, liveTint, entranceFade)
    }

    companion object : BrassDemoSource {


        /**
         * The whole set, so the palette is the demo.
         *
         * One chip documents the shape and nothing else. What a reader actually needs from a tag
         * widget is *which colours exist and what they mean* — whether there is a warning tone
         * distinct from an error one, whether a neutral chip is available — and that is only visible
         * side by side. Laid out in two rows: the semantic set first, then the quieter markers.
         */
        override fun demo() = BrassDemo("tag", "Tag", 210f, 40f) {
            val box = UIContainer()
            var row = 0
            var xOffset = 0f
            for ((label, style) in DEMO_TAGS) {
                val tag = BrassTag(label, style)
                val w = BrassFont.width(tag, label) + TAG_PAD
                // Wrap by measured width rather than a fixed count, so re-labelling a chip cannot
                // silently push the last one off the edge of the capture.
                if (xOffset + w > DEMO_ROW_W && xOffset > 0f) { row++; xOffset = 0f }
                tag.constrain {
                    x = xOffset.pixels()
                    y = (row * (BrassFont.LINE + 6f)).pixels()
                    width = w.pixels()
                    height = (BrassFont.LINE + 3f).pixels()
                } childOf box
                xOffset += w + TAG_GAP
            }
            box
        }

        /** The chips the demo shows, in the order the docs introduce them. */
        private val DEMO_TAGS = listOf(
            "ACTIVE" to BrassTagStyle.SUCCESS,
            "PENDING" to BrassTagStyle.WARNING,
            "FAILED" to BrassTagStyle.ERROR,
            "INFO" to BrassTagStyle.INFO,
            "NEW" to BrassTagStyle.NEW,
            "BETA" to BrassTagStyle.BETA,
            "LEGACY" to BrassTagStyle.DEPRECATED,
            "NEUTRAL" to BrassTagStyle.NEUTRAL,
            "SUBTLE" to BrassTagStyle.SUBTLE,
        )

        /** Width the demo wraps its rows at. */
        private const val DEMO_ROW_W = 200f
        /** Horizontal padding a chip adds around its label. */
        private const val TAG_PAD = 10f
        /** Gap between two chips. */
        private const val TAG_GAP = 4f


        // ---- geometry -------------------------------------------------------------------------

        /** Outline thickness. */
        const val BORDER = 1f

        /** Padding between the outline and the text ink, on every side. */
        const val PAD = 1f

        /** Outline + padding: the inset from the pill's edge to the text ink. */
        const val INSET = BORDER + PAD

        /**
         * Height of small-capital ink inside a [BrassFont.LINE]-tall glyph box. The remaining rows sit
         * above and below it and are what the padding and outline are drawn into.
         */
        const val INK_H = 5f

        /** Blank rows above small-capital ink in the glyph box - the text's own top offset. */
        const val INK_TOP = 2f

        /** Pill height. Equal to [BrassFont.LINE], so a tag is exactly as tall as a [BrassLabel]. */
        val HEIGHT: Float get() = INK_H + INSET * 2f

        /**
         * Minecraft's advance width for a glyph includes one column of spacing *after* its ink, so a
         * measured string is one pixel wider than what you can see. Subtracting it is what makes the
         * right-hand padding come out equal to the left instead of two pixels to its one.
         */
        const val TRAILING = 1f

        /** Pill width for a measured text width - see [TRAILING]. */
        fun pillWidth(textWidth: Float): Float = textWidth - TRAILING + INSET * 2f

        /**
         * Pill width for [label] as it will actually be drawn. Anything laying tags out itself must
         * measure through this rather than measuring a glyph run of its own - the two must agree, and
         * only this one knows whether the text came from the sheet or the font.
         */
        fun measure(comp: UIComponent, label: String): Float =
            pillWidth(BrassSmallCaps.width(comp, label))

        // ---- theme palette --------------------------------------------------------------------
        // The default set, all drawn from the toolkit's own ramp so a tag can never look foreign.

        /** The accent green. */
        val BRASS: Color get() = Colors.BRASS_400
        /** A deeper brass, for a second tier of accent tags. */
        val BRASS_DEEP: Color get() = Colors.BRASS_600
        /** The secondary teal. */
        val PATINA: Color get() = Colors.PATINA_400
        /** A deeper patina. */
        val PATINA_DEEP: Color get() = Colors.PATINA_500
        /** Amber. */
        val AMBER: Color get() = Colors.WARN
        /** The muted red. */
        val RUST: Color get() = Colors.DANGER
        /** Plain grey - the default for a tag that carries no status. */
        val NEUTRAL: Color get() = Colors.UI_TEXT_DARK
        /** A brighter grey, for a neutral tag that still needs to be noticed. */
        val STEEL: Color get() = Colors.UI_ELEMENT_BORDER_HOVER
        /** The quietest tag there is - barely more than an outline. */
        val INK: Color get() = Colors.UI_INNER_BORDER

        // ---- semantic aliases -----------------------------------------------------------------
        // The same theme colours under names that say what the tag *means*. Call sites should prefer
        // these: "SUCCESS" survives a retheme, "BRASS_400" does not.

        val SUCCESS: Color get() = BRASS
        val WARNING: Color get() = AMBER
        val ERROR: Color get() = RUST
        val INFO: Color get() = PATINA
        val MUTED: Color get() = NEUTRAL
        /** For a new / highlighted item. */
        val NEW: Color get() = Colors.BRASS_300
        /** For a pre-release or experimental marker. */
        val BETA: Color get() = PATINA_DEEP
        /** For something on its way out. */
        val DEPRECATED: Color get() = STEEL

        // Specialised category tints for the inspector's element tags.
        val TEXT: Color get() = PATINA
        val CONTAINER: Color get() = BRASS
        val WIDGET: Color get() = AMBER
        val BORDER_TAG: Color get() = RUST
        val ROOT: Color get() = NEUTRAL

        /**
         * Bind a colour back to the palette entry it came from, by identity - see
         * [net.swzo.brass.ui.kit.text.BrassLabel] for why identity and not equality.
         */
        internal fun roleFor(c: Color): (() -> Color)? = when {
            c === Colors.BRASS_400 -> ({ BRASS })
            c === Colors.BRASS_600 -> ({ BRASS_DEEP })
            c === Colors.BRASS_300 -> ({ NEW })
            c === Colors.PATINA_400 -> ({ PATINA })
            c === Colors.PATINA_500 -> ({ PATINA_DEEP })
            c === Colors.WARN -> ({ AMBER })
            c === Colors.DANGER -> ({ RUST })
            c === Colors.UI_TEXT_DARK -> ({ NEUTRAL })
            c === Colors.UI_ELEMENT_BORDER_HOVER -> ({ STEEL })
            c === Colors.UI_INNER_BORDER -> ({ INK })
            else -> null
        }

        // ---- alignment ------------------------------------------------------------------------

        /**
         * A y-constraint that centres a tag on [other]'s text line - the correct way to place a tag
         * inline beside a label or a heading, at any [BrassLabel.scale].
         *
         * A tag is the same height as an unscaled label, so against one this resolves to "same top";
         * against a scaled heading it centres in the taller line. Either way the caller does not have
         * to know which, which is the point - this used to be open-coded at the one call site that
         * needed it and was wrong everywhere else.
         */
        fun centeredOn(other: UIComponent): YConstraint =
            basicYConstraint { other.getTop() + (other.getHeight() - HEIGHT) / 2f }

        // ---- immediate-mode drawing -------------------------------------------------------------

        /**
         * Draw a pill for [label] at ([x],[y]), sizing itself to the text - the same rendering the
         * widget uses, exposed so immediate-mode surfaces (the dev tree and details pane) can drop tags
         * inline without a child component each. Returns the pill's width.
         */
        fun drawPill(
            m: UMatrixStack,
            comp: UIComponent,
            x: Float,
            y: Float,
            label: String,
            tint: Color,
            alpha: Float = 1f,
        ): Float {
            val w = measure(comp, label)
            val h = HEIGHT
            if (alpha <= 0.004f) return w

            // A flat rectangle with a 1-px outline - the top edge of a card, nothing more.
            fill(m, x, y, x + w, y + h, fade(wash(tint), alpha))
            outline(m, x, y, x + w, y + h, fade(tint, alpha))

            // Drawn at the pill's own top: the glyph box's blank upper rows are the padding. The sheet
            // cell is a whole line box with the ink already sitting [INK_TOP] down it, so it takes the
            // same origin the font run took.
            BrassSmallCaps.drawString(m, comp, label, x + INSET, y + INSET - INK_TOP, fade(bright(tint), alpha))
            return w
        }

        private fun fill(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) =
            BrassPaint.rect(m, x1, y1, x2, y2, c)

        private fun outline(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) =
            BrassPaint.border(m, x1, y1, x2, y2, c, BORDER)

        /** A low-alpha wash of [c] for the fill, so the tag tints the surface rather than covering it. */
        private fun wash(c: Color) = Color(c.red, c.green, c.blue, 40)

        /** The outline sits a little under full strength so it reads as chrome, not as a highlight. */
        private fun fade(c: Color, a: Float): Color = BrassPaint.fade(c, a)

        private fun bright(c: Color) =
            net.swzo.brass.ui.BrassBlock.lighten(Color(c.red, c.green, c.blue, 255), 0.5f)
    }
}
