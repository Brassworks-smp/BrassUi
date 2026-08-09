@file:Suppress("unused")
package net.swzo.brass.ui.kit.input

import gg.essential.elementa.dsl.*
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.text.BrassFuzzy
import net.swzo.brass.ui.kit.text.BrassTextInput
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A text field for filtering: a search glyph, a clear button that appears once there is something to
 * clear, and a debounce so the filter runs when typing pauses rather than on every keystroke.
 * ```kotlin
 * BrassSearchField("Filter players…") { query -> table.setRows(BrassFuzzy.rank(query, all) { it.name }) }
 * ```
 * ### Why the debounce is the point
 * [BrassTextInput] already reports every change, and for a form field that is right. For a search it
 * is not: the callback usually re-filters a list, re-sorts it and rebuilds a view, and running that
 * five times while someone types "diamond" is five times the work for four results nobody saw. The
 * delay is small enough to feel instant and long enough to collapse a burst of keystrokes into one
 * pass.
 * [onSearchNow] fires immediately regardless - for Enter, where the user has said they are done.
 */
class BrassSearchField(
    placeholder: String = "Search…",
    /** How long typing must pause before the search runs, in seconds. */
    var debounce: Float = 0.2f,
    private val onSearch: (String) -> Unit = {},
) : BrassWidget(BrassAccent.DEFAULT) {

    private val input = BrassTextInput(placeholder = placeholder)

    private var sinceEdit = -1f

    val text: String get() = input.text

    var onSearchNow: ((String) -> Unit)? = null

    init {
        // A default height, because BrassForm.addField sets x, y and width but deliberately not
        // height - a control with no intrinsic height resolves to zero and simply does not appear.
        // BrassLabel and BrassTag self-constrain for the same reason. A caller's own constrain{}
        // still wins, since it is applied after construction.
        constrain { height = DEFAULT_H.pixels() }
        // A BrassWidget, not a UIContainer. The base class is what runs the entrance animation, the
        // hover/press colour easing, the cursor request, the focus ring and BrassDevMode.inspect - a
        // raw Elementa container painting itself gets none of that and is invisible to the inspector.
        // chrome = NONE because this widget paints all of its own background.
        chrome = BrassChrome.NONE
        input.constrain {
            x = basicXConstraint { this@BrassSearchField.getLeft() + ICON_BOX }
            y = basicYConstraint { this@BrassSearchField.getTop() }
            width = basicWidthConstraint { (this@BrassSearchField.getWidth() - ICON_BOX - clearWidth()).coerceAtLeast(0f) }
            height = basicHeightConstraint { this@BrassSearchField.getHeight() }
        } childOf this

        input.onChange { sinceEdit = 0f }

        // The clear button is a hit area rather than a widget: it exists only when there is text, and
        // a component that comes and going would have to be added and removed from the tree mid-frame.
        onMouseClick { e ->
            if (e.mouseButton == 0 && input.text.isNotEmpty() && e.relativeX > getWidth() - CLEAR) clear()
        }
    }

    fun clear() {
        input.value = ""
        sinceEdit = -1f
        onSearch("")
    }

    fun searchNow() {
        sinceEdit = -1f
        onSearch(input.text)
        onSearchNow?.invoke(input.text)
    }

    private fun clearWidth(): Float = if (input.text.isEmpty()) 0f else CLEAR

    fun <T> filter(items: List<T>, text: (T) -> String): List<T> = BrassFuzzy.rank(this.text, items, text)

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun drawContent(matrixStack: UMatrixStack, bx: Int, by: Int, bw: Int, bh: Int) {
        if (sinceEdit >= 0f) {
            sinceEdit += BrassClock.dt
            if (sinceEdit >= debounce) {
                sinceEdit = -1f
                onSearch(input.text)
            }
        }


        val cy = getTop() + (getHeight() - ICON) / 2f
        BrassIcons.draw(
            matrixStack, BrassIcons.SEARCH,
            getLeft() + (ICON_BOX - ICON) / 2f, cy, ICON, Colors.UI_TEXT_DARK,
        )

        if (input.text.isNotEmpty()) {
            val (mx, my) = getMousePosition()
            val hot = mx > getRight() - CLEAR && mx < getRight() && my > getTop() && my < getBottom()
            BrassIcons.draw(
                matrixStack, BrassIcons.CLOSE,
                getRight() - CLEAR + (CLEAR - ICON) / 2f, cy, ICON,
                if (hot) Colors.UI_TEXT_HOVER else Colors.UI_TEXT_DARK,
            )
        }

    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("search-field", "Search field", 200f, 18f) {
            BrassSearchField("Search items…")
        }

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val DEFAULT_H = 16f
        private const val ICON = 7f
        private const val ICON_BOX = 15f
        private const val CLEAR = 14f
    }
}
