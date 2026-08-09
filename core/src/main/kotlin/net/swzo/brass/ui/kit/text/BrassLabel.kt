@file:Suppress("unused")
package net.swzo.brass.ui.kit.text

import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.constrain
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import java.awt.Color
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A line of text that animates in like every other widget.
 * Elementa's `UIText` is a fine text component but it is not a [BrassWidget], so it appeared instantly
 * while the controls around it faded and rose into place - the entrance cascade stopped dead wherever
 * a caption sat. This is text built on the widget base instead, so it inherits the fade, the rise and
 * the staggered delay for free, and it is one less thing that animates differently from its neighbours.
 * It paints no keycap ([transparent] + [flat]); it is only text. Size is **intrinsic** by default -
 * the component measures itself from the string, exactly as `UIText` does - so it drops into existing
 * layouts that give it a position and let it size itself. Override `width`/`height` as usual when a
 * layout needs something else.
 */
class BrassLabel(
    text: String,
    tint: Color = Colors.UI_TEXT,
    var shadow: Boolean = true,
    var scale: Float = 1f,
) : BrassWidget(BrassAccent.DEFAULT) {

    var text: String = text
        set(value) { if (field != value) { field = value; cachedWidth = -1f } }

    var tint: Color = tint
        set(value) {
            field = value
            tintRole = inferRole(value)
        }

    /**
     * Follow a colour **role** rather than a fixed colour, so the label retints when the theme changes.
     * A [Color] passed to the constructor is a value, captured once - which is right for a label that
     * is deliberately, say, pure red, but wrong for one that meant "the accent colour". `Colors.UI_ACCENT`
     * reads the live theme at the moment it is evaluated, so passing it in leaves the label holding
     * whatever the accent happened to be at construction. Pass the role itself instead:
     * ```
     * BrassLabel("Connected").tintedBy { Colors.UI_ACCENT }
     * ```
     */
    fun tintedBy(role: () -> Color): BrassLabel {
        tintRole = role
        return this
    }

    private var tintRole: (() -> Color)? = inferRole(tint)

    private fun inferRole(c: Color): (() -> Color)? = when {
        c === Colors.UI_TEXT -> ({ Colors.UI_TEXT })
        c === Colors.UI_TEXT_DARK -> ({ Colors.UI_TEXT_DARK })
        c === Colors.UI_TEXT_HOVER -> ({ Colors.UI_TEXT_HOVER })
        c === Colors.UI_ACCENT -> ({ Colors.UI_ACCENT })
        c === Colors.UI_ACCENT_BRIGHT -> ({ Colors.UI_ACCENT_BRIGHT })
        c === Colors.DANGER -> ({ Colors.DANGER })
        c === Colors.WARN -> ({ Colors.WARN })
        c === Colors.GOOD -> ({ Colors.GOOD })
        else -> null
    }

    private val effectiveTint: Color get() = tintRole?.invoke() ?: tint

    fun copyable(): BrassLabel {
        selectable = true
        clickable = true
        onMouseClick { e -> if (e.mouseButton == 0) { selected = true; net.swzo.brass.ui.kit.base.BrassFocus.focus(this) } }
        return this
    }

    fun copy(): Boolean {
        if (!selected) return false
        return runCatching { gg.essential.universal.UDesktop.setClipboardString(text) }.isSuccess
    }

    fun bind(state: BrassState<String>): BrassLabel {
        // See BrassProgressBar.bind: the handle must be kept, or a long-lived state retains the whole
        // screen through this label.
        disposeWith(state.onChange { text = it })
        return this
    }

    // The width constraint is resolved on every getWidth() - i.e. every frame - so measuring the string
    // through the font each time is a real cost with a screenful of captions. Memoise it, invalidated
    // only when the text or scale actually changes.
    private var cachedWidth = -1f
    private var cachedScale = -1f

    private fun measuredWidth(): Float {
        if (cachedWidth < 0f || scale != cachedScale) {
            cachedScale = scale
            cachedWidth = BrassFont.width(this, text, scale)
        }
        return cachedWidth
    }

    init {
        // no keycap of any kind: transparent alone would still outline every label with a 1-px box
        chrome = BrassChrome.NONE
        constrain {
            width = basicWidthConstraint { measuredWidth() }
            height = basicHeightConstraint { BrassFont.LINE * scale }
        }
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        // withAlpha applies the base class's entrance fade to our own colour - the animated text
        // colour the keycaps use is not wanted here, since a label's colour is chosen by the caller.
        BrassFont.draw(m, this, text, x.toFloat(), y.toFloat(), withAlpha(effectiveTint, entranceFade), shadow, scale)
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("label", "Label", 140f, 14f) {
            BrassLabel("Label text")
        }
    }
}
