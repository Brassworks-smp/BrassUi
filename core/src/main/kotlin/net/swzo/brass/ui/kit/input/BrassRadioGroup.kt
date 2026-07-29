package net.swzo.brass.ui.kit.input

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.base.BrassState
import net.swzo.brass.ui.kit.base.BrassValue
import net.swzo.brass.ui.kit.base.BrassValueHolder
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.layout.BrassHBox
import net.swzo.brass.ui.kit.layout.BrassVBox
import net.swzo.brass.ui.kit.text.BrassLabel

/**
 * Exactly one of a set of [BrassCheckbox]es ticked at a time.
 *
 * ### Why this exists
 *
 * Mutual exclusion was open-coded wherever it was needed -
 * [net.swzo.brass.ui.kit.settings.BrassThemeCard] resolves it in a `syncTo` that every swatch runs
 * against the live accent - and there was no reusable way to say "one of these". A radio group is
 * a *value*, not a container: the boxes can be laid out however the screen likes.
 *
 * ```kotlin
 * val mode = BrassRadioGroup<String>()
 * mode.register("fast", fastBox)
 * mode.register("safe", safeBox)
 * mode.value = "safe"
 * ```
 */
class BrassRadioGroup<T>(
    initial: T? = null,
    private val onChange: (T?) -> Unit = {},
) : BrassValue<T?> {

    private val options = LinkedHashMap<T, BrassCheckbox>()

    private val holder = BrassValueHolder<T?>(initial) { selected -> sync(selected) }

    override var value: T?
        get() = holder.value
        set(v) { holder.value = v }

    override fun setSilently(value: T?) = holder.setSilently(value)
    override fun onChange(listener: (T?) -> Unit) = holder.onChange(listener)

    /**
     * Two-way binding is not available on a group: it owns no component, so there is nothing whose
     * teardown could unbind it. Bind the state to the group by hand and dispose it with whichever
     * component owns the layout.
     */
    override fun bind(state: BrassState<T?>): () -> Unit {
        val fromState = state.onChange { setSilently(it) }
        val toState = onChange { state.value = it }
        return { fromState(); toState() }
    }

    init {
        holder.onChange(onChange)
    }

    /**
     * Add [box] as the control for [option]. Clicking it selects that option and unticks the rest.
     *
     * The box keeps its own appearance, so a group can be a row of swatches, a column of labelled
     * rows, or anything else - the group only governs which one is on.
     */
    fun register(option: T, box: BrassCheckbox): BrassCheckbox {
        options[option] = box
        box.onChange { on -> if (on) value = option else if (value == option) value = null }
        box.setSilently(value == option)
        return box
    }

    /** The control registered for [option], if any. */
    fun boxFor(option: T): UIComponent? = options[option]

    private fun sync(selected: T?) {
        for ((option, box) in options) box.setSilently(option == selected)
    }

    companion object : BrassDemoSource {

        /**
         * Three options as a labelled column, one ticked.
         *
         * A radio group owns no layout of its own, so the demo *is* the layout: a checkbox and a label
         * per row, every box registered to one group so ticking any one unticks the others. That is the
         * whole thing a still cannot show, so it is what the demo lets you click.
         */
        override fun demo() = BrassDemo("radio-group", "Radio group", 96f, 70f) {
            val group = BrassRadioGroup<String>(initial = "normal")
            val rows = BrassVBox(gap = 4f)
            listOf("easy" to "Easy", "normal" to "Normal").forEach { (id, label) ->
                val box = BrassCheckbox(initial = id == "normal").apply {
                    constrain { width = 12.pixels(); height = 12.pixels() }
                }
                group.register(id, box)
                rows.add(
                    BrassHBox(gap = 6f).add(
                        box,
                        BrassLabel(label).apply { constrain { y = CenterConstraint() } },
                    ),
                )
            }
            rows
        }
    }
}
