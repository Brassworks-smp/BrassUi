package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.ChildBasedSizeConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassValue
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.base.proxiedBy
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassDropdown
import net.swzo.brass.ui.kit.input.BrassSlider
import net.swzo.brass.ui.kit.input.BrassToggle
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTag
import net.swzo.brass.ui.kit.text.BrassTextInput
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A scrolling stack of labelled rows, built with a small chaining API and readable back by caption.
 * ### Why this is not part of [net.swzo.brass.ui.kit.surface.BrassPopup]
 * It used to be. Along with the frame chrome, the drag, the collapse, the maximize, the resize, the
 * modal scrim and the z-order, which is how that one class reached 667 lines and fourteen
 * responsibilities - and why it imported the entire `input` and `text` packages from `surface`,
 * inverting the layering.
 * The practical cost was that a form could only exist *inside a popup*. A settings panel in a window,
 * a form in a tab, a wizard step - none of them could use any of this, which is exactly why the
 * showcase screens hand-build their panels row by row instead.
 * ```kotlin
 * val form = BrassForm()
 *     .addTextField("Address", "localhost")
 *     .addSlider("Volume", 0f, 1f, 0.8f)
 *     .addToggleRow("Autostart", false)
 * // ...later
 * val settings = form.values()
 * form.setValues(saved)
 * ```
 */
class BrassForm(
    private val rowGap: Float = BrassWidget.BLEED_BOTTOM + 6f,
) : UIContainer() {

    private val scroll: ScrollComponent
    private val column: UIContainer

    val body: ScrollComponent get() = scroll

    init {
        // Room is reserved for the scrollbar rather than letting it overlay the content: the body
        // scissors to its own bounds, so a bar drawn on top would sit over the right-hand edge of
        // every row (and over a full-width control's border).
        scroll = ScrollComponent().constrain {
            x = 0.pixels(); y = 0.pixels()
            width = 100.percent() - (BrassScrollbar.WIDTH + SCROLLBAR_GAP).pixels()
            height = 100.percent()
        } childOf this

        // Every form scrolls, so every form gets a bar. Leaving it to callers is how they all ended
        // up without one. It hides itself when the content already fits, so a short form is unchanged.
        BrassScrollbar.attach(this, scroll)

        // The scroll view scissors to its own bounds and keycaps bleed outside their box (outer ring,
        // bottom lip), so the column is inset by that bleed - otherwise borders are shaved off at the
        // left, right and top edges.
        column = UIContainer().constrain {
            x = BrassWidget.BLEED_X.pixels()
            y = BrassWidget.BLEED_TOP.pixels()
            width = 100.percent() - (BrassWidget.BLEED_X * 2 + 6f).pixels()
            // Measures the lowest row's bottom plus the keycap bleed, so the last row's lip is inside
            // the scrollable area. ChildBasedSizeConstraint sums heights and ignores offsets, which
            // under-measured any row that positions its own children.
            height = BrassLayout.contentHeight()
        } childOf scroll
    }


    private fun stack(c: UIComponent): BrassForm {
        c.constrain {
            y = if (column.children.isEmpty()) 0.pixels() else SiblingConstraint(rowGap)
            width = 100.percent()
        } childOf column
        return this
    }

    private fun caption(text: String): BrassLabel = BrassLabel(text, Colors.UI_TEXT_DARK)

    fun add(component: UIComponent): BrassForm = stack(component)

    fun addField(label: String, control: UIComponent): BrassForm {
        val f = UIContainer().constrain { height = ChildBasedSizeConstraint(4f) }
        caption(label).constrain { x = 0.pixels(); y = 0.pixels() } childOf f
        control.constrain { x = 0.pixels(); y = SiblingConstraint(4f); width = 100.percent() } childOf f
        return stack(f)
    }

    fun addRow(label: String, controlHeight: Int, vararg controls: UIComponent): BrassForm {
        val row = UIContainer().constrain { height = ChildBasedSizeConstraint(3f) }
        if (label.isNotEmpty()) {
            caption(label).constrain { x = 0.pixels(); y = 0.pixels() } childOf row
        }
        val flow = BrassFlow(itemHeight = controlHeight.toFloat(), stretch = true).constrain {
            x = 0.pixels()
            y = if (label.isEmpty()) 0.pixels() else SiblingConstraint(4f)
            width = 100.percent()
        } childOf row
        controls.forEach { flow.add(it, MIN_CONTROL) }
        flow.constrain { height = basicHeightConstraint { flow.contentHeight() } }
        return stack(row)
    }

    fun addTags(label: String, vararg tags: BrassTag): BrassForm {
        val row = UIContainer().constrain { height = ChildBasedSizeConstraint(3f) }
        if (label.isNotEmpty()) {
            caption(label).constrain { x = 0.pixels(); y = 0.pixels() } childOf row
        }
        val flow = BrassFlow(gapX = 4f, gapY = 4f, itemHeight = BrassTag.HEIGHT).constrain {
            x = 0.pixels()
            y = if (label.isEmpty()) 0.pixels() else SiblingConstraint(4f)
            width = 100.percent()
        } childOf row
        tags.forEach { tag -> flow.add(tag, BrassTag.measure(this, tag.text)) }
        flow.constrain { height = basicHeightConstraint { flow.contentHeight() } }
        return stack(row)
    }

    fun addTextField(
        label: String,
        initial: String,
        placeholder: String = "",
        onChange: (String) -> Unit = {},
    ): BrassForm = addField(
        label,
        register(label, BrassTextInput(initial, placeholder, onChange).also { it.constrain { height = 20.pixels() } }),
    )

    fun addDropdown(
        label: String,
        options: List<Pair<String, String>>,
        initial: String,
        onSelect: (String) -> Unit = {},
    ): BrassForm = addField(label, register(label, BrassDropdown(options, initial, onSelect)))

    fun addToggleRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit = {}): BrassForm {
        val t = register(label, BrassToggle(initial, onChange))
        val row = UIContainer().constrain { height = 18.pixels() }
        caption(label).constrain { x = 0.pixels(); y = CenterConstraint() } childOf row
        t.constrain { x = 0.pixels(true); y = CenterConstraint(); width = 40.pixels(); height = 18.pixels() } childOf row
        // The whole row drives the toggle, as a settings row should - the caption is inside it, so a
        // click on the words bubbles up to here. Binding only the row (not the caption too) is what
        // keeps a click from firing twice and cancelling itself out. See BrassProxy.
        t.proxiedBy(row)
        return stack(row)
    }

    fun addSlider(
        label: String,
        min: Float,
        max: Float,
        initial: Float,
        step: Float = 0f,
        format: (Float) -> String = { String.format("%.2f", it) },
        onChange: (Float) -> Unit = {},
    ): BrassForm = addField(
        label,
        register(label, BrassSlider(min, max, initial, step, format, onChange).also { it.constrain { height = 20.pixels() } }),
    )

    fun addButtons(vararg buttons: BrassButton): BrassForm = addRow("", 20, *buttons)


    /**
     * Controls added through the builder, keyed by their caption.
     * The builder methods return the form so they can chain, which meant a form could be *built* in
     * one expression but never *read* - every value had to be captured through an `onChange` closure
     * into state the caller maintained by hand.
     */
    private val controls = LinkedHashMap<String, UIComponent>()

    private fun <T : UIComponent> register(label: String, control: T): T {
        controls[label] = control
        return control
    }

    inline fun <reified T : UIComponent> control(label: String): T? = controlsView[label] as? T

    @PublishedApi
    internal val controlsView: Map<String, UIComponent> get() = controls

    fun text(label: String): String? = (controls[label] as? BrassTextInput)?.text
    fun flag(label: String): Boolean? = (controls[label] as? BrassToggle)?.toggled
    fun number(label: String): Float? = (controls[label] as? BrassSlider)?.value
    fun choice(label: String): String? = (controls[label] as? BrassDropdown)?.selected

    fun values(): Map<String, Any> = controls.mapNotNull { (label, c) ->
        when (c) {
            is BrassTextInput -> label to c.text
            is BrassToggle -> label to c.toggled
            is BrassSlider -> label to c.value
            is BrassDropdown -> label to c.selected
            else -> null
        }
    }.toMap()

    @Suppress("UNCHECKED_CAST")
    fun setValues(values: Map<String, Any?>) {
        for ((label, raw) in values) {
            val control = controls[label] ?: continue
            val value = raw ?: continue
            when {
                control is BrassTextInput && value is String -> control.setSilently(value)
                control is BrassToggle && value is Boolean -> control.setSilently(value)
                control is BrassSlider && value is Number -> control.setSilently(value.toFloat())
                control is BrassDropdown && value is String -> control.setSilently(value)
                control is BrassValue<*> -> runCatching {
                    (control as BrassValue<Any>).setSilently(value)
                }
            }
        }
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("form", "Form", 250f, 130f) {
            val form = BrassForm()
            form.addTextField("Server", "survival.example.net")
            form.addDropdown(
                "Difficulty",
                listOf("easy" to "Easy", "normal" to "Normal", "hard" to "Hard"),
                "normal",
            )
            form.addToggleRow("Hardcore", false)
            form.addSlider("View distance", 2f, 32f, 12f)
            form
        }

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val SCROLLBAR_GAP = 3f

        private const val MIN_CONTROL = 76f
    }
}
