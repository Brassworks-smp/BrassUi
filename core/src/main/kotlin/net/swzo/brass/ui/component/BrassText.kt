package net.swzo.brass.ui.component

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.ChildBasedMaxSizeConstraint
import gg.essential.elementa.constraints.ChildBasedSizeConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.component.BrassText.body
import net.swzo.brass.ui.component.BrassText.flat
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassWrappedLabel
import java.awt.Color

/**
 * Text primitives for the toolkit. **Every one returns a [BrassLabel] / [BrassWrappedLabel]** - real
 * [net.swzo.brass.ui.kit.BrassWidget]s drawn through [net.swzo.brass.ui.kit.BrassFont], not Elementa's
 * `UIText`. That is deliberate: the brass labels show up in the dev-mode inspector, animate in with the
 * controls around them, and are the single seam where richer text (e.g. Minecraft `Component` support)
 * can be added later without callers changing. Use [flat] for dark-on-brass runs that want no shadow.
 */
object BrassText {

    /** Shadowed text in an explicit colour - a plain, static label (no entrance animation). */
    fun shadowed(text: String, color: Color): BrassLabel = plain(text, color, shadow = true)

    /**
     * The primitive: a static line of text with no entrance animation, for chrome (title bars, tabs,
     * headers) where a fade-in would look wrong. It is still a [BrassLabel], so it inspects and measures
     * itself exactly like the animated labels.
     */
    fun plain(text: String, color: Color = Colors.UI_TEXT, shadow: Boolean = true): BrassLabel =
        BrassLabel(text, color, shadow).also { it.entranceEnabled = false }

    /**
     * A muted secondary label - used for captions and un-selected states.
     *
     * Returns a [BrassLabel] so captions fade and rise with the widgets around them instead of snapping
     * in. It measures itself from the string, so layouts that only set a position keep working.
     */
    fun label(text: String, color: Color = Colors.UI_TEXT_DARK): BrassLabel = BrassLabel(text, color)

    /** Primary body text - white-ish on dark surfaces. */
    fun body(text: String, color: Color = Colors.UI_TEXT): BrassLabel = BrassLabel(text, color)

    /** Brass section heading, set slightly larger. */
    fun heading(text: String, color: Color = Colors.UI_ACCENT_BRIGHT): BrassLabel =
        BrassLabel(text, color, scale = 1.15f)

    /** Flat text (no shadow). Use on brass fills where any shadow would clash. */
    fun flat(text: String, color: Color): BrassLabel =
        BrassLabel(text, color, shadow = false).also { it.entranceEnabled = false }

    /**
     * Body text that **wraps** to the width of its parent instead of running off the edge. Give the
     * returned component a width (usually `100.percent()`); it self-sizes its height from the wrap, so
     * it reflows automatically when that width changes.
     *
     * Prefer this over [body] for any prose whose length isn't fixed - hand-placed `\n` breaks only
     * happen to look right at one particular window size.
     */
    fun wrapped(text: String, color: Color = Colors.UI_TEXT): BrassWrappedLabel =
        BrassWrappedLabel(text, color)
}

/**
 * A labelled value line: a muted `label` on the left and a `value` on the right. The container is split
 * 50/50 with a strict grid (no `alignOpposite` - that failed when the parent's width wasn't authoritative
 * yet). Give it a width (usually `100.percent()`).
 */
class Row(label: String, value: String, valueColor: Color = Colors.TEXT) : UIContainer() {
    init {
        constrain { height = ChildBasedMaxSizeConstraint() }

        // Left half - the caption.
        val leftCell = UIContainer().constrain {
            x = 0.pixels(); y = 0.pixels()
            width = 50.percent(); height = ChildBasedSizeConstraint()
        } childOf this
        BrassText.label(label).constrain { x = 0.pixels(); y = CenterConstraint() } childOf leftCell

        // Right half - the value, right-aligned inside the cell.
        val rightCell = UIContainer().constrain {
            x = 50.percent(); y = 0.pixels()
            width = 50.percent(); height = ChildBasedSizeConstraint()
        } childOf this
        val valueText = BrassText.body(value, valueColor)
        // The cell is a strict right half, so pinning the text's right edge to the cell's right edge
        // (via a 0 opposite-aligned pixel offset) can't spill outside the parent.
        valueText.constrain { x = 0.pixels(true); y = CenterConstraint() } childOf rightCell
    }
}

/**
 * A form field: a small muted caption stacked above its `control` - the launcher's `Field`. The control
 * is any component; it's re-parented under the field and stretched full width.
 */
class Field(caption: String, control: UIComponent) : UIContainer() {
    init {
        constrain { height = ChildBasedSizeConstraint(4f) }
        BrassText.label(caption).constrain { x = 0.pixels(); y = 0.pixels() } childOf this
        control.constrain {
            x = 0.pixels()
            y = SiblingConstraint(4f)
            width = 100.percent()
        } childOf this
    }
}
