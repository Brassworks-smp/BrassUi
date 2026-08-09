package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassEased
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.input.BrassCheckbox
import net.swzo.brass.ui.kit.input.BrassSlider
import net.swzo.brass.ui.kit.input.BrassToggle
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.text.BrassFont

/**
 * Collapsible sections - a settings screen's groups, a details panel, anything that is too much at
 * once.
 * ```kotlin
 * val panel = BrassAccordion()
 * panel.section("Display", displayControls, open = true)
 * panel.section("Advanced", advancedControls)
 * ```
 * A section's body is any component. It is added as a real child rather than painted, because the
 * point of a section is to hold *controls*, which have to be clickable and focusable - so the
 * accordion is a layout, not a canvas.
 * ### Collapsing without measuring twice
 * A collapsed section's height animates to zero, and the body keeps its own natural height throughout:
 * the section's height is `bodyHeight * openness`, where openness is an eased 0..1. Nothing re-measures
 * the body while it moves, so a body whose height is content-derived does not thrash - which matters
 * because a `SiblingConstraint` stack re-evaluates until it converges within a frame, and a height
 * that changes as a function of that evaluation may never settle.
 * The body is clipped while moving, so its contents slide out from under the header rather than
 * scaling.
 */
class BrassAccordion(
    var exclusive: Boolean = false,
) : UIContainer() {

    private val sections = ArrayList<Section>()

    fun section(title: String, body: UIComponent, open: Boolean = false): Section {
        val section = Section(title, body, open)
        section.constrain {
            x = 0.pixels()
            // The gap has to be part of the *layout*, not padding inside a section: a KEYCAP header
            // paints its lip below its own box, and a flush SiblingConstraint puts the next header's
            // top border straight through it.
            y = SiblingConstraint(SECTION_GAP)
            width = 100.percent()
            height = basicHeightConstraint { section.measure() }
        } childOf this
        sections.add(section)
        return section
    }

    fun open(section: Section) {
        if (exclusive) sections.forEach { if (it !== section) it.open = false }
        section.open = true
    }

    fun closeAll() = sections.forEach { it.open = false }

    fun contentHeight(): Float {
        if (sections.isEmpty()) return 0f
        val stacked = sections.sumOf { it.measure().toDouble() }.toFloat()
        val gaps = SECTION_GAP * (sections.size - 1)
        val trailing = if (sections.any { it.openness > 0.01f }) TRAILING else BrassWidget.BLEED_BOTTOM
        return stacked + gaps + trailing
    }

    inner class Section(
        var title: String,
        private val body: UIComponent,
        open: Boolean,
    ) : UIContainer() {

        var open: Boolean = open
            set(value) {
                if (field == value) return
                field = value
                onToggle?.invoke(value)
            }

        var onToggle: ((Boolean) -> Unit)? = null

        private val eased = BrassEased(if (open) 1f else 0f, speed = SPEED)

        val openness: Float get() = eased.value

        private val header = Header()

        private val bodyHolder = BodyPanel()

        init {
            header.constrain {
                x = 0.pixels(); y = 0.pixels()
                width = 100.percent(); height = HEADER_H.pixels()
            } childOf this

            bodyHolder.constrain {
                x = 0.pixels()
                // Below the header *and its lip*, so the card starts where the keycap stops.
                y = (HEADER_H + BrassWidget.BLEED_BOTTOM).pixels()
                width = 100.percent()
                height = basicHeightConstraint { (panelHeight() * eased.value).coerceAtLeast(0f) }
            } childOf this
            // Clip, so a body that is taller than the current openness allows slides out from under
            // the header instead of being drawn over it.
            bodyHolder.enableEffect(gg.essential.elementa.effects.ScissorEffect())

            body.constrain {
                x = BODY_PAD.pixels()
                y = BODY_PAD.pixels()
                width = 100.percent() - (BODY_PAD * 2).pixels()
                height = basicHeightConstraint { bodyHeight() }
            } childOf bodyHolder
        }

        private fun bodyHeight(): Float {
            val kids = body.children
            if (kids.isEmpty()) return 0f
            return (kids.maxOf { it.getBottom() } - body.getTop() + PAD).coerceAtLeast(0f)
        }

        private fun panelHeight(): Float = bodyHeight() + BODY_PAD * 2

        fun measure(): Float =
            HEADER_H + BrassWidget.BLEED_BOTTOM + (panelHeight() * eased.value).coerceAtLeast(0f)

        override fun draw(matrixStack: UMatrixStack) {
            eased.target = if (open) 1f else 0f
            eased.advance()
            super.draw(matrixStack)
        }

        private inner class BodyPanel : BrassWidget(BrassAccent.DEFAULT) {
            init {
                // A BrassWidget rather than a UIContainer painting itself: the base class is what runs
                // the entrance animation and registers with BrassDevMode.inspect. chrome = NONE
                // because the card below is the whole background.
                chrome = BrassChrome.NONE
                // Clip, so a body taller than the current openness allows slides out from under the
                // header instead of being drawn over it.
                enableEffect(gg.essential.elementa.effects.ScissorEffect())
            }

            override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
                // panel, not draw: this sits under its own ScissorEffect (see init) clipped to exactly
                // these bounds, so draw's bled outer ring would be clipped away - see BrassCard.panel.
                if (h > 1) {
                    BrassCard.panel(m, x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
                }
            }
        }

        private inner class Header : BrassWidget(BrassAccent.DEFAULT) {
            init {
                // KEYCAP, not FLAT: the raised card with the bottom lip is what makes the header read
                // as a control you press to open something, rather than a caption.
                chrome = BrassChrome.KEYCAP
                clickable = true
                onMouseClick { e -> if (e.mouseButton == 0) toggle() }
            }

            override fun proxyActivate() = toggle()

            private fun toggle() {
                if (open) this@Section.open = false else this@BrassAccordion.open(this@Section)
            }

            override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
                val glyph = if (eased.value > 0.5f) BrassIcons.CHEVRON_DOWN else BrassIcons.CHEVRON_RIGHT
                BrassIcons.draw(m, glyph, x + PAD, y + (h - CHEVRON) / 2f, CHEVRON, Colors.UI_TEXT_DARK)
                BrassFont.draw(
                    m, this, BrassFont.fit(this, title, w - PAD * 3 - CHEVRON),
                    x + PAD * 2 + CHEVRON,
                    y + (h - BrassFont.LINE) / 2f,
                    textColor,
                )
            }
        }
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("accordion", "Accordion", 230f, 150f) {
            val panel = BrassAccordion(exclusive = true)

            panel.section("Display", body {
                BrassToggle(initial = true).constrain {
                    x = 0.pixels(); y = 0.pixels(); width = 28.pixels(); height = 14.pixels()
                } childOf it
                BrassSlider(0f, 100f, 62f).constrain {
                    x = 0.pixels(); y = 20.pixels(); width = 150.pixels(); height = 14.pixels()
                } childOf it
            })

            panel.section("Advanced", body {
                BrassCheckbox(initial = false).constrain {
                    x = 0.pixels(); y = 0.pixels(); width = 12.pixels(); height = 12.pixels()
                } childOf it
                BrassLabel("Verbose logging").constrain {
                    x = 18.pixels(); y = 1.pixels()
                } childOf it
            })

            panel
        }

        private fun body(fill: (UIContainer) -> Unit): UIContainer =
            UIContainer().also { fill(it) }

        // Private individually rather than on the companion, which has to be public now that it
        // carries the demo. Same visibility as before for everything below.
        private const val HEADER_H = 18f
        private const val PAD = 5f
        private const val CHEVRON = 8f
        private const val BODY_PAD = 5f
        private const val SECTION_GAP = 4f
        private const val TRAILING = 8f
        private const val SPEED = 10f
    }
}
