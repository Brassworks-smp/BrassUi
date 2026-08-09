package net.swzo.brass.ui.kit.settings

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.*
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.BrassThemes
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassTree
import net.swzo.brass.ui.kit.base.disposeWith
import net.swzo.brass.ui.kit.input.BrassCheckbox
import net.swzo.brass.ui.kit.input.BrassColorPicker
import net.swzo.brass.ui.kit.input.BrassDropdown
import net.swzo.brass.ui.kit.layout.BrassFlow
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.surface.BrassContextMenu
import net.swzo.brass.ui.kit.text.BrassLabel
import java.awt.Color

/**
 * The **appearance card**: a theme picker and an accent picker, wired straight to [BrassThemes].
 * A port of the BrassWorks launcher's Appearance settings card - a labelled dropdown listing the
 * registered themes, then a row of accent swatches with a check on the active one, ending in a custom
 * chip that opens a colour picker in a floating panel rather than inline. The picker is a popover for
 * the same reason it is in the launcher: a full HSV square is far taller than the row it belongs to,
 * and inlining it pushes every setting below it down the card.
 * ### Wiring
 * The card holds no state of its own. It reads [BrassThemes] to draw itself and writes to it on every
 * interaction, so it stays correct if something else changes the theme - a config load at startup, or
 * a second copy of this card on another screen. Persistence is not its business either: hook
 * [BrassThemes.onChange] from the mod's config code to write the change to disk.
 * ```
 * BrassThemeCard() childOf settingsColumn
 * ```
 */
class BrassThemeCard(
    private val popoverRoot: (() -> UIComponent?)? = null,
) : UIContainer() {

    private val swatchRow = BrassFlow(gapX = 4f, gapY = 4f, itemHeight = SWATCH)
    private val swatches = ArrayList<Swatch>()
    private var customChip: Swatch? = null


    init {
        constrain { height = basicHeightConstraint { contentHeight() } }

        BrassLabel("APPEARANCE", Colors.UI_TEXT_DARK).constrain {
            x = PAD.pixels(); y = (PAD + 2f).pixels()
        } childOf this

        BrassLabel("Theme").constrain { x = PAD.pixels(); y = ROW_THEME.pixels() } childOf this

        val dropdown = BrassDropdown(
            options = BrassThemes.all().map { it.name to it.name.replaceFirstChar(Char::titlecase) },
            initial = BrassThemes.currentId,
        ) { id ->
            // Adopt the new theme's own accent, as the launcher does - a theme is a whole look, and
            // ocean without its cyan is not ocean. Writing it *explicitly* (rather than leaving the
            // accent unset and letting it resolve) is what ticks that colour's swatch, so the row shows
            // which accent is actually in force.
            BrassThemes.apply(id, BrassThemes.byId(id)?.defaultAccent?.let(BrassThemes::toHex))
        }
        // No height here: the dropdown owns it, growing as the menu unrolls (see BrassDropdown).
        // Pinning it is what left the menu with nowhere to open into.
        dropdown.constrain {
            x = PAD.pixels(); y = (ROW_THEME + 12f).pixels()
            width = 100.percent() - (PAD * 2f).pixels()
        } childOf this

        val accentLabel = BrassLabel("Accent").constrain {
            x = PAD.pixels()
            y = basicYConstraint { dropdown.getBottom() + 10f }
        } childOf this

        swatchRow.constrain {
            x = PAD.pixels()
            y = basicYConstraint { accentLabel.getBottom() + 4f }
            width = 100.percent() - (PAD * 2f).pixels()
            height = basicHeightConstraint { swatchRow.contentHeight() }
        } childOf this

        // The leading swatch is "the theme's own accent" - the launcher's null accent. It shows
        // whatever the current theme's brass is, so it retints as the theme changes.
        addSwatch(Swatch(null))
        BrassThemes.ACCENT_SWATCHES.forEach { addSwatch(Swatch(it)) }

        customChip = Swatch(null, custom = true).also { addSwatch(it) }

        // Follow the registry rather than only this card's own clicks: a config load at startup, a
        // second card on another screen, or code setting the theme directly all have to be reflected
        // here. Every interaction below writes to BrassThemes and lets the change come back through
        // this listener, so there is one path that updates the swatches instead of two.
        // Torn down when the card leaves the tree. It used to unsubscribe itself the *next time it
        // fired* after being orphaned - which meant a card on a closed screen stayed registered, and
        // kept itself alive, until some unrelated retheme happened to come along.
        disposeWith(BrassThemes.onChange { refresh() })

        refresh()
    }

    private fun addSwatch(s: Swatch) {
        swatches.add(s)
        swatchRow.add(s, SWATCH)
    }

    private fun refresh() {
        val current = BrassThemes.accent
        for (s in swatches) s.syncTo(current)
    }

    private fun contentHeight(): Float =
        (children.maxOfOrNull { it.getBottom() } ?: getTop()) - getTop() + PAD

    override fun draw(matrixStack: UMatrixStack) {
        BrassCard.draw(
            matrixStack,
            getLeft(), getTop(), getRight(), getBottom(),
            shadow = true,
        )
        super.draw(matrixStack)
    }

    private inner class Swatch(
        private val color: Color?,
        private val custom: Boolean = false,
    ) : BrassCheckbox(
        initial = false,
        fixedAccent = swatchAccent(color, custom),
        alwaysShowIcon = custom,
    ) {

        /**
         * The "+" is the *empty* state of the custom chip - an invitation to pick something. Once it
         * holds the colour in use it is a swatch like any other and wears a tick, so the row never
         * shows two different things meaning "selected".
         */
        override val icon: BrassIcons.Icon
            get() = if (custom && customAccent() == null) BrassIcons.PLUS else BrassIcons.CHECK

        init {
            constrain { width = SWATCH.pixels(); height = SWATCH.pixels() }
            onMouseClick { e ->
                if (e.mouseButton != 0) return@onMouseClick
                if (custom) togglePicker()
                else BrassThemes.apply(BrassThemes.currentId, color?.let(BrassThemes::toHex))
            }
        }

        fun syncTo(accent: Color?) {
            val on = when {
                custom -> accent != null && BrassThemes.ACCENT_SWATCHES.none { it.rgb == accent.rgb }
                color == null -> accent == null
                else -> accent?.rgb == color.rgb
            }
            if (on != checked) set(on)
        }

        private var openMenu: BrassContextMenu? = null

        private fun togglePicker() {
            val current = openMenu
            if (current != null && current.isOpen) {
                current.dismiss()
                openMenu = null
                return
            }
            openPicker()
        }

        private fun openPicker() {
            val root = popoverRoot?.invoke() ?: findRoot()
            val start = BrassThemes.accent ?: Colors.UI_ACCENT

            val picker = BrassColorPicker(start) { picked ->
                BrassThemes.apply(BrassThemes.currentId, BrassThemes.toHex(picked))
            }

            // A context menu, not a popup: the picker is transient chrome, so it wants exactly the
            // behaviour a menu already has - one at a time, dismissed by clicking away, closed by
            // Escape, and flipped back inside the screen near an edge. A popup gave it none of those,
            // which is why a second one could be opened and why clicking the window left it stranded
            // behind the window instead of closing it.
            val menu = BrassContextMenu.custom(picker, width = PICKER_W, height = PICKER_H)

            // Anchored just below the chip and aligned to its left edge, so the menu never sits over
            // the swatch that opened it. show() flips it above the chip if it would run off the bottom.
            openMenu = menu
            menu.show(root, getLeft(), getBottom() + 3f, anchorTop = getTop() - 3f)
        }

        /** Walk up to the screen root, which is what a floating layer must be parented to. */
        private fun findRoot(): UIComponent = BrassTree.rootOf(this)
    }

    private companion object {
        fun swatchAccent(color: Color?, custom: Boolean): BrassAccent = when {
            // The custom chip shows the colour it is holding, so the row reads as "this is your
            // colour" rather than "press + for something unknown". With nothing custom chosen there is
            // no colour to show, and it falls back to a plain keycap.
            custom -> BrassAccent.derived(
                "custom",
                accent = { customAccent() ?: Colors.UI_ELEMENT_BORDER },
                accentHover = { customAccent()?.let { Colors.mix(it, Color.WHITE, 0.25f) } ?: Colors.UI_ELEMENT_BORDER_HOVER },
                dark = { customAccent()?.let { Colors.mix(Colors.UI_ELEMENT_BG, it, 0.55f) } ?: Colors.UI_ELEMENT_BG },
                darkHover = { customAccent()?.let { Colors.mix(Colors.UI_ELEMENT_BG, it, 0.70f) } ?: Colors.UI_ELEMENT_BG_HOVER },
                bottom = { customAccent()?.let { Colors.mix(it, Color.BLACK, 0.45f) } ?: Colors.KEYCAP_BOTTOM },
                bottomHover = { customAccent()?.let { Colors.mix(it, Color.BLACK, 0.30f) } ?: Colors.KEYCAP_BOTTOM },
            )
            color != null -> BrassThemes.accentFor(color)
            else -> BrassAccent.derived(
                "theme-default",
                accent = { themeOwnAccent() },
                accentHover = { Colors.mix(themeOwnAccent(), Color.WHITE, 0.25f) },
                dark = { Colors.mix(Colors.UI_ELEMENT_BG, themeOwnAccent(), 0.55f) },
                darkHover = { Colors.mix(Colors.UI_ELEMENT_BG, themeOwnAccent(), 0.70f) },
                bottom = { Colors.mix(themeOwnAccent(), Color.BLACK, 0.45f) },
                bottomHover = { Colors.mix(themeOwnAccent(), Color.BLACK, 0.30f) },
            )
        }

        fun customAccent(): Color? = BrassThemes.accent
            ?.takeIf { a -> BrassThemes.ACCENT_SWATCHES.none { it.rgb == a.rgb } }

        fun themeOwnAccent(): Color = BrassThemes.DEFAULT.brass500

        const val PAD = 10f
        const val SWATCH = 14f
        const val PICKER_W = 150
        const val PICKER_H = 150
        const val ROW_THEME = 24f
    }
}
