package net.swzo.brass.ui.dsl

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.input.*
import net.swzo.brass.ui.kit.layout.BrassGrid
import net.swzo.brass.ui.kit.layout.BrassHBox
import net.swzo.brass.ui.kit.layout.BrassVBox
import net.swzo.brass.ui.kit.text.*
import java.awt.Color

// Re-exports, so a screen needs one import for the Elementa constraint DSL rather than fifteen.
// The import block of the desktop showcase was fifty lines, a third of them constraint helpers.
typealias Pixels = gg.essential.elementa.constraints.PixelConstraint

/**
 * A small builder over the layout boxes, so a column of controls reads as a column.
 *
 * ### Why this exists
 *
 * Placing a caption and a rail took six lines and two constraint helpers:
 *
 * ```kotlin
 * val nav = UIContainer().constrain {
 *     x = 12.pixels(); y = 12.pixels()
 *     width = basicWidthConstraint { c -> (c.parent.getWidth() * 0.26f).coerceIn(110f, 170f) }
 *     height = 100.percent() - 24.pixels()
 * } childOf frame.content
 * BrassText.label("SECTIONS").constrain { x = 4.pixels(); y = 2.pixels() } childOf nav
 * ```
 *
 * [BrassVBox] and [BrassHBox] already removed the anchor chains; this removes the ceremony around
 * them, and is deliberately thin - it composes the existing types rather than introducing a parallel
 * layout system.
 *
 * ```kotlin
 * column(gap = 8f) {
 *     label("SECTIONS")
 *     button("Save", BrassAccent.BRASS) { save() }
 *     row { button("Cancel") { close() }; spring(); button("Apply") { apply() } }
 * } childOf frame.content
 * ```
 */
class BrassColumnScope(private val box: BrassVBox) {
    fun add(component: UIComponent) = box.add(component)
    fun label(text: String, color: Color = Colors.UI_TEXT_DARK) = BrassLabel(text, color).also { box.add(it) }
    fun body(text: String, color: Color = Colors.UI_TEXT) = BrassLabel(text, color).also { box.add(it) }
    fun paragraph(text: String, color: Color = Colors.UI_TEXT) = BrassWrappedLabel(text, color).also { box.add(it) }
    fun heading(text: String) = BrassLabel(text, Colors.UI_ACCENT_BRIGHT, scale = 1.15f).also { box.add(it) }
    fun tag(text: String, style: BrassTagStyle = BrassTagStyle.NEUTRAL) = BrassTag(text, style).also { box.add(it) }
    fun button(text: String, accent: BrassAccent = BrassAccent.DEFAULT, onClick: () -> Unit = {}) =
        BrassButton(text, accent, onClick).also { box.add(it) }
    fun toggle(initial: Boolean = false, onChange: (Boolean) -> Unit = {}) =
        BrassToggle(initial, onChange).also { box.add(it) }
    fun checkbox(initial: Boolean = false, onChange: (Boolean) -> Unit = {}) =
        BrassCheckbox(initial, onChange = onChange).also { box.add(it) }
    fun field(initial: String = "", placeholder: String = "", onChange: (String) -> Unit = {}) =
        BrassTextInput(initial, placeholder, onChange).also { box.add(it) }
    fun slider(min: Float, max: Float, initial: Float, onChange: (Float) -> Unit = {}) =
        BrassSlider(min, max, initial, onChange = onChange).also { box.add(it) }
    fun dropdown(options: List<Pair<String, String>>, initial: String, onSelect: (String) -> Unit = {}) =
        BrassDropdown(options, initial, onSelect).also { box.add(it) }
    fun spacer(size: Float) = box.addSpacer(size)

    /** A nested row inside this column. */
    fun row(gap: Float = 6f, build: BrassRowScope.() -> Unit): BrassHBox =
        net.swzo.brass.ui.dsl.row(gap, bleed = true, build = build).also { box.add(it) }
}

/** The horizontal counterpart to [BrassColumnScope]. */
class BrassRowScope(private val box: BrassHBox) {
    fun add(component: UIComponent) = box.add(component)
    fun label(text: String, color: Color = Colors.UI_TEXT_DARK) = BrassLabel(text, color).also { box.add(it) }
    fun body(text: String, color: Color = Colors.UI_TEXT) = BrassLabel(text, color).also { box.add(it) }
    fun tag(text: String, style: BrassTagStyle = BrassTagStyle.NEUTRAL) = BrassTag(text, style).also { box.add(it) }
    fun button(text: String, accent: BrassAccent = BrassAccent.DEFAULT, onClick: () -> Unit = {}) =
        BrassButton(text, accent, onClick).also { box.add(it) }
    fun toggle(initial: Boolean = false, onChange: (Boolean) -> Unit = {}) =
        BrassToggle(initial, onChange).also { box.add(it) }
    fun spacer(size: Float) = box.addSpacer(size)

    /** Push everything after this to the right-hand end - see [BrassHBox.spring]. */
    fun spring() = box.spring()
}

/** Build a vertical stack. */
fun column(gap: Float = 6f, bleed: Boolean = true, build: BrassColumnScope.() -> Unit): BrassVBox {
    val box = BrassVBox(gap, bleed)
    BrassColumnScope(box).build()
    return box
}

/** Build a horizontal row. */
fun row(gap: Float = 6f, bleed: Boolean = true, build: BrassRowScope.() -> Unit): BrassHBox {
    val box = BrassHBox(gap, bleed)
    BrassRowScope(box).build()
    return box
}

/** Build a weighted grid - see [BrassGrid]. */
fun grid(
    weights: List<Float>,
    rowHeight: Float = 20f,
    build: BrassGrid.() -> Unit,
): BrassGrid = BrassGrid(weights, rowHeight).apply(build)

/** An empty container, for the cases the boxes do not cover. */
fun box(build: UIContainer.() -> Unit = {}): UIContainer = UIContainer().apply(build)
