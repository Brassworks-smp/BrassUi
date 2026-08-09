package net.swzo.brass.ui.kit.input

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.media.BrassIcons
import kotlin.math.min
import kotlin.math.roundToInt
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A checkbox - a small keycap that goes brass and shows a pixel check glyph when ticked.
 * The accent is swapped between [BrassAccent.DEFAULT] and [BrassAccent.BRASS] as it toggles, rather than
 * being fixed at BRASS: a non-default accent makes [BrassWidget] paint its accent colours unconditionally,
 * which left an *unchecked* box looking exactly as brass as a checked one.
 */
open class BrassCheckbox(
    // named `initial`, not `checked`: a constructor parameter shadows the same-named property inside
    // `init`, so reads there would silently see the start value forever (see BrassTextInput for the bug
    // that caused when the property was the one being mutated).
    initial: Boolean = false,
    private val fixedAccent: BrassAccent? = null,
    protected open val icon: BrassIcons.Icon = BrassIcons.CHECK,
    private val alwaysShowIcon: Boolean = false,
    private val onChange: (Boolean) -> Unit = {},
) : BrassWidget(fixedAccent ?: if (initial) BrassAccent.BRASS else BrassAccent.DEFAULT),
    BrassValue<Boolean>, BrassFocusable {

    private val holder = BrassValueHolder(initial) { on ->
        selected = on
        if (fixedAccent == null) accent = if (on) BrassAccent.BRASS else BrassAccent.DEFAULT
    }

    override var value: Boolean
        get() = holder.value
        set(v) { holder.value = v }

    override fun setSilently(value: Boolean) = holder.setSilently(value)
    override fun onChange(listener: (Boolean) -> Unit) = holder.onChange(listener)
    override fun bind(state: BrassState<Boolean>) = holder.bind(this, state)

    val checked: Boolean get() = holder.value

    init {
        clickable = true
        selectable = true
        selected = initial
        onMouseClick { e -> if (active && e.mouseButton == 0) toggle() }
        holder.onChange(onChange)
    }

    fun toggle() { value = !value }

    override fun proxyActivate() { if (active) toggle() }

    open fun set(value: Boolean) { this.value = value }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        if (!checked && !alwaysShowIcon) return
        val size = (min(w, h) * 0.62f).roundToInt().coerceAtLeast(5).toFloat()
        BrassIcons.draw(m, icon, x + (w - size) / 2f, y + (h - size) / 2f, size, textColor)
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("checkbox", "Checkbox", 14f, 14f) {
            BrassCheckbox(initial = false)
        }
    }
}
