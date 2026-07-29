package net.swzo.brass.ui.kit.input

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassState
import net.swzo.brass.ui.kit.base.BrassValue
import net.swzo.brass.ui.kit.base.BrassValueHolder
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A segmented row of keycap tabs, exactly one selected. The active tab animates to the brass accent,
 * the rest sit neutral; clicking a tab selects it and fires [onChange].
 *
 * ### Width
 *
 * Tabs size themselves to their **own label** by default, so "Shaders" does not get the same slab of
 * space as "Resource Packs" and short labels stop swimming in empty keycap. Two escapes from that:
 *
 * - [equalWidths] splits the component's width evenly, the old behaviour, for a control that has to
 *   line up with something else.
 * - [widths] pins individual tabs by index; a null entry keeps the measured width. Use it when one
 *   segment must be a fixed size regardless of its text.
 *
 * With the default sizing the row is as wide as its content, so give the component
 * `width = basicWidthConstraint { tabs.contentWidth() }` rather than a guess - see [contentWidth].
 */
class BrassTabSwitch(
    private val options: List<String>,
    initialIndex: Int = 0,
    /** Split the available width evenly instead of sizing each tab to its label. */
    private val equalWidths: Boolean = false,
    /** Explicit pixel width per tab, by index. Null entries fall back to the measured width. */
    private val widths: List<Float?> = emptyList(),
    /**
     * Accent the selected tab animates to; the rest stay [BrassAccent.DEFAULT].
     *
     * Takes the same parameter [BrassButton] does, so a tab row can be themed exactly like the
     * buttons beside it instead of being the one control locked to brass.
     */
    var accent: BrassAccent = BrassAccent.BRASS,
    private val onChange: (Int) -> Unit = {},
) : UIContainer(), BrassValue<Int> {

    private val tabs = ArrayList<BrassButton>()

    private val holder = BrassValueHolder(initialIndex) { i -> applySelection(i) }

    override var value: Int
        get() = holder.value
        set(v) { if (v in options.indices) holder.value = v }

    override fun setSilently(value: Int) { if (value in options.indices) holder.setSilently(value) }
    override fun onChange(listener: (Int) -> Unit) = holder.onChange(listener)
    override fun bind(state: BrassState<Int>) = holder.bind(this, state)

    /** Index of the active tab. Alias of [value]. */
    val selectedIndex: Int get() = holder.value

    /**
     * Measured label widths, filled on first use.
     *
     * Width constraints resolve every frame, and measuring a string through the font provider is not
     * free - but the labels never change, so one measurement each is enough for the component's life.
     */
    private val measured = FloatArray(options.size) { -1f }

    init {
        constrain { height = 18.pixels() }
        val n = options.size.coerceAtLeast(1)
        options.forEachIndexed { i, opt ->
            val tab = BrassButton(opt, if (i == initialIndex) accent else BrassAccent.DEFAULT) { select(i) }
            tab.selectable = true
            tab.selected = i == initialIndex
            tab.constrain {
                x = if (i == 0) 0.pixels() else SiblingConstraint(GAP)
                y = 0.pixels()
                width = if (equalWidths) (100f / n).percent() else basicWidthConstraint { tabWidth(i) }
                height = 100.percent()
            } childOf this
            tabs.add(tab)
        }
        holder.onChange(onChange)
    }

    /** Width of tab [index]: an explicit override, or its label plus padding. */
    private fun tabWidth(index: Int): Float {
        widths.getOrNull(index)?.let { return it }
        if (measured[index] < 0f) {
            val w = BrassFont.width(this, options[index])
            // Only cache once we are in a tree. getFontProvider() resolves by walking up the parent
            // chain, so measuring before `childOf` answers from Elementa's default provider - and
            // because this cache is never invalidated, that wrong width would be kept for the
            // component's whole life. contentWidth() is public and documented as the thing you feed
            // to the width constraint, so calling it before parenting is an easy, silent mistake.
            if (!hasParent) return (w + PAD * 2f).coerceAtLeast(MIN_W)
            measured[index] = w
        }
        return (measured[index] + PAD * 2f).coerceAtLeast(MIN_W)
    }

    /**
     * Total width the tabs occupy, including the gaps between them.
     *
     * Feed this to the component's own width constraint. Meaningless under [equalWidths], where the
     * tabs divide whatever width they are given instead of asking for one.
     */
    fun contentWidth(): Float {
        if (options.isEmpty()) return 0f
        var total = 0f
        for (i in options.indices) total += tabWidth(i)
        return total + GAP * (options.size - 1)
    }

    /** Left/right move between tabs, as a segmented control should. */
    fun onArrow(forward: Boolean) {
        val n = options.size
        if (n == 0) return
        value = ((selectedIndex + if (forward) 1 else -1) % n + n) % n
    }

    /** Select tab [index], firing listeners. Alias of assigning [value]. */
    fun select(index: Int) { value = index }

    private fun applySelection(index: Int) {
        tabs.forEachIndexed { i, tab ->
            tab.selected = i == index
            tab.accent = if (i == index) accent else BrassAccent.DEFAULT
        }
    }

    companion object : BrassDemoSource {

        /**
         * Selection moving between tabs.
         *
         * Pressed at fractions of the row rather than at its centre, because the centre of a two-tab
         * switch is the seam between them — the one place a click means nothing. Each press lands
         * squarely inside a tab, and the accent slides across between them, which is the behaviour a
         * still cannot show.
         */
        override fun demo() = BrassDemo("tab-switch", "Tab switch", 190f, 20f) {
            BrassTabSwitch(listOf("Overview", "Details", "History"))
        }

        // ---- widget internals ------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        /** Horizontal breathing room either side of a tab's label. */
        private const val PAD = 9f
        /** Narrowest a tab gets, so a one-character label is still a comfortable target. */
        private const val MIN_W = 28f
        /** Seam between neighbouring tabs. */
        private const val GAP = 1f
    }
}
