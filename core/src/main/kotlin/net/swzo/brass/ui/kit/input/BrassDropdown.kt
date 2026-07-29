package net.swzo.brass.ui.kit.input

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.text.BrassFont
import kotlin.math.roundToInt
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * An in-place accordion dropdown: a keycap [field] showing the current label and a
 * chevron that flips when open, and a menu of keycap option rows that expands below (pushing following
 * content down) so hitboxes track scrolling. The selected row goes brass; picking one collapses the menu.
 */
class BrassDropdown(
    private val options: List<Pair<String, String>>, // id to label
    initial: String = options.firstOrNull()?.first ?: "",
    private val onSelect: (String) -> Unit = {},
) : UIContainer(), BrassValue<String> {

    private val holder = BrassValueHolder(initial) { id -> applySelection(id) }
    private var open = false

    /**
     * The selected option id. Now writable: a dropdown used to be readable and nothing else, so a
     * form could be read back but never populated.
     */
    override var value: String
        get() = holder.value
        set(v) { if (options.any { it.first == v }) holder.value = v }

    override fun setSilently(value: String) {
        if (options.any { it.first == value }) holder.setSilently(value)
    }

    override fun onChange(listener: (String) -> Unit) = holder.onChange(listener)
    override fun bind(state: BrassState<String>) = holder.bind(this, state)

    /** The currently selected option id. Alias of [value]. */
    val selected: String get() = holder.value

    private val selectedId: String get() = holder.value

    private val field = Field()
    private val menu = UIContainer()
    private val card = Card()
    private val rows = LinkedHashMap<String, BrassButton>()

    companion object : BrassDemoSource {

        /**
         * Opened, an option picked, closed.
         *
         * The panel is the point — it animates open, hangs off the control with a brass seam, and
         * closes when something is chosen. All three are invisible in a still of a closed dropdown,
         * which is otherwise just a button with a chevron.
         *
         * The demo is taller than the control so the open panel has somewhere to be; a demo sized to
         * the closed state would capture the options clipped off at the bottom edge.
         *
         * ### Why the card fits rather than fills
         *
         * `fitCard` is what makes this read correctly. The declared height has to cover the *open*
         * menu, and a card painted to that box would sit there full-size the whole time with a small
         * closed control at the top of it — which is not what a dropdown looks like in a real
         * UI, where the surface around it grows as it unrolls. Fitting lets the card follow the
         * widget's own height (which already tracks the open fraction, see `ownHeight`) while the
         * captured frame stays a constant size.
         */
        override fun demo() = BrassDemo("dropdown", "Dropdown", 170f, 90f, fitCard = true) {
            BrassDropdown(
                listOf("low" to "Low detail", "med" to "Medium detail", "high" to "High detail"),
            )
        }

        // ---- widget internals ------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val ROW_H = 18f
        private const val ROW_GAP = 2f
        /** How fast the menu unrolls and rolls back up. */
        private const val SPEED = 18f
        /** Default height of the closed field. */
        private const val FIELD_H = 20f
        /** Floor for a caller-supplied field height, so a stray 0 cannot make the field unclickable. */
        private const val MIN_FIELD_H = 12f
        /** Gap between the field and the menu below it. */
        private const val MENU_GAP = 4f

        /** Default cap on the unrolled menu. About eight rows — a glanceable list. */
        private const val MAX_MENU_H = 160f
        /** Inner padding of the options card - clears the rows' keycap bleed on every side. */
        private val CARD_PAD = maxOf(BrassWidget.BLEED_X, BrassWidget.BLEED_TOP, BrassWidget.BLEED_BOTTOM) + 1f
    }

    /**
     * The component's height: the field, plus however much of the menu is currently unrolled.
     *
     * This is what makes the accordion part of the *layout* - a sibling positioned with
     * `SiblingConstraint` slides down as the menu opens, instead of the menu painting over it.
     */
    private val ownHeight = basicHeightConstraint {
        fieldHeight + if (openAmount > 0f) MENU_GAP + menuHeight() * openAmount else 0f
    }

    /**
     * How tall the open menu is allowed to grow before it scrolls instead.
     *
     * A dropdown with forty options used to unroll to the height of forty options, which on a short
     * form runs off the bottom of whatever is hosting it - the rows are simply not reachable, and the
     * accordion pushes everything below it off the screen on the way. Capping the roll and scrolling
     * inside it keeps the control the size of a control.
     */
    var maxMenuHeight: Float = MAX_MENU_H

    /** Scroll offset inside the menu, 0 at the top. */
    private var menuScroll = 0f

    /** The menu's height: its content, or [maxMenuHeight] when there is more than that. */
    private fun menuHeight(): Float = minOf(card.contentHeight(), maxMenuHeight)

    /** How far the menu can scroll — zero when everything already fits. */
    private fun maxScroll(): Float = (card.contentHeight() - menuHeight()).coerceAtLeast(0f)

    /** Height of the always-visible field. Set indirectly by constraining the component (see [draw]). */
    private var fieldHeight = FIELD_H

    init {
        constrain { height = ownHeight }
        field.constrain {
            x = 0.pixels(); y = 0.pixels(); width = 100.percent()
            height = basicHeightConstraint { fieldHeight }
        } childOf this

        // The options live inside a card: a panel with its own fill, border and outer ring, so the open
        // menu reads as a surface sitting above the form rather than loose buttons floating on it. The
        // card is padded by the keycap bleed so the rows' rings and lips are never clipped by its edge.
        // The menu's height is the *animated* fraction of the card's content height, and it scissors to
        // itself, so the rows are revealed by the card growing past them rather than by appearing.
        menu.constrain {
            x = 0.pixels(); y = SiblingConstraint(MENU_GAP)
            width = 100.percent()
            height = basicHeightConstraint { menuHeight() * openAmount }
        }
        menu.enableEffect(ScissorEffect())
        // The wheel scrolls the card behind the menu's scissor. Propagation stops here so a dropdown
        // inside a scroll view does not scroll the view out from under itself while its list is open.
        menu.onMouseScroll { event ->
            if (maxScroll() <= 0f) return@onMouseScroll
            menuScroll = (menuScroll - event.delta.toFloat() * ROW_H).coerceIn(0f, maxScroll())
            event.stopPropagation()
        }
        // The card keeps its full height regardless, so the rows inside do not squash as it unrolls -
        // they are clipped by the menu above them, which is what an accordion actually does.
        card.constrain {
            x = 0.pixels()
            // Offset upward by the scroll, and clipped by the menu's scissor — the card keeps its full
            // height so the rows never squash, and the menu shows a window onto it.
            y = basicYConstraint { c -> c.parent.getTop() - menuScroll }
            width = 100.percent()
            height = basicHeightConstraint { card.contentHeight() }
        } childOf menu

        options.forEach { (id, label) ->
            val row = BrassButton(label, if (id == selectedId) BrassAccent.BRASS else BrassAccent.DEFAULT) { choose(id) }
            row.centered = false
            row.selectable = true
            row.selected = id == selectedId
            row.entranceEnabled = false
            row.constrain {
                x = CARD_PAD.pixels()
                y = if (rows.isEmpty()) CARD_PAD.pixels() else SiblingConstraint(ROW_GAP)
                width = 100.percent() - (CARD_PAD * 2).pixels()
                height = ROW_H.pixels()
            } childOf card
            rows[id] = row
        }
        holder.onChange(onSelect)
    }

    private fun labelFor(id: String) = options.firstOrNull { it.first == id }?.second ?: id

    // ---- open / close animation ------------------------------------------------------------------

    /**
     * Eased 0..1 opening. The menu's **height constraint reads this**, so the card unrolls and the
     * content below it slides down together - the accordion is in the layout, not painted over it.
     *
     * The menu stays parented for the whole close animation and is only detached at the end; detaching
     * on click, which is what used to happen, is precisely why it vanished between two frames.
     */
    private val roll = BrassEased(0f, speed = SPEED)
    private val openAmount: Float get() = roll.value

    /** The chevron's rotation proxy: 0 = down, 1 = up. Follows [openAmount]. */
    private val chevronFlipped: Boolean get() = openAmount > 0.5f

    private fun toggle() { if (open) collapse() else expand() }

    /**
     * Whether the menu is open. Settable, for a caller that needs to drive the dropdown itself - an
     * "edit" button that opens the list, or a form that closes every dropdown when it is submitted.
     */
    var expanded: Boolean
        get() = open
        set(value) { if (value) expand() else collapse() }

    private fun expand() {
        if (open) return
        open = true
        // Opened at the top every time. A menu that reopened where it was last left would hide the
        // selected row as often as it showed it.
        menuScroll = 0f
        roll.target = 1f
        if (!children.contains(menu)) menu childOf this
    }

    private fun collapse() {
        if (!open) return
        open = false
        roll.target = 0f
    }

    /**
     * Advance the roll, and detach the menu once it has fully closed.
     *
     * The easing lives in [BrassEased] rather than a loop here. The hand-rolled version returned early
     * when the value was already settled *without reading its clock*, so the second open covered the
     * whole animation in one frame - the dropdown animated once and snapped from then on.
     */
    private fun advance() {
        roll.advance()
        if (roll.settled && roll.target == 0f && children.contains(menu)) removeChild(menu)
    }

    override fun draw(matrixStack: UMatrixStack) {
        // no beforeDraw() - UIContainer.draw already calls it (see BrassFlow for the full note)

        // A caller who writes `height = 18.pixels()` means "an 18-px field", not "never open" - but a
        // fixed height leaves the menu nowhere to unroll into, so it renders outside the component's
        // box and its rows stop taking clicks (hit testing is bounded by the box). Rather than let
        // that fail silently, take their value as the field height and take the total back.
        if (constraints.height !== ownHeight) {
            fieldHeight = getHeight().coerceAtLeast(MIN_FIELD_H)
            constraints.height = ownHeight
        }

        advance()
        super.draw(matrixStack)
    }

    private fun choose(id: String) {
        value = id
        collapse()
    }

    /** Repaint the rows for [id]. Runs for every change, whoever made it. */
    private fun applySelection(id: String) {
        rows.forEach { (rid, row) ->
            row.selected = rid == id
            row.accent = if (rid == id) BrassAccent.BRASS else BrassAccent.DEFAULT
        }
    }

    /**
     * The card the options sit in: a flat panel (fill + inner border + near-black outer ring) matching
     * the popup/window chrome, with a brass seam down its left edge tying it to the field above.
     */
    private inner class Card : UIContainer() {

        /** Height needed for every row plus the card's padding. */
        fun contentHeight(): Float =
            rows.size * ROW_H + (rows.size - 1).coerceAtLeast(0) * ROW_GAP + CARD_PAD * 2

        override fun draw(matrixStack: UMatrixStack) {
            // no beforeDraw() - UIContainer.draw already calls it (see BrassFlow for the full note)
            // The same flat card the colour picker's regions wear, with the brass seam that ties the
            // menu to the field above it. This was a hand-rolled stack of six fills that also forgot
            // to count its quads (see BrassPaint).
            BrassCard.flat(
                matrixStack,
                getLeft().roundToInt().toFloat(), getTop().roundToInt().toFloat(),
                getRight().roundToInt().toFloat(), getBottom().roundToInt().toFloat(),
                fill = Colors.UI_INNER_BG,
                seam = Colors.UI_ACCENT,
            )
            super.draw(matrixStack)
        }
    }

    /** The always-visible field: current label on the left, a flip chevron on the right. */
    private inner class Field : BrassWidget(BrassAccent.DEFAULT) {
        init { onMouseClick { e -> if (active && e.mouseButton == 0) toggle() } }
        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
            val ty = y + (h - BrassFont.LINE) / 2 + 1
            // leave room for the chevron on the right; ellipsise anything longer
            val show = BrassFont.fit(this, labelFor(selectedId), (w - 20).toFloat())
            BrassFont.draw(m, this, show, (x + 6).toFloat(), ty.toFloat(), textColor, true)
            val g = if (chevronFlipped) BrassIcons.CHEVRON_UP else BrassIcons.CHEVRON_DOWN
            BrassIcons.draw(m, g, (x + w - 12).toFloat(), (y + (h - 4) / 2f), 7f, textColor)
        }
    }
}
