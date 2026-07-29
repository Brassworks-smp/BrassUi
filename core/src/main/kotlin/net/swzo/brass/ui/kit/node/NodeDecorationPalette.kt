package net.swzo.brass.ui.kit.node

import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.*
import net.swzo.brass.ui.BrassThemes
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.input.BrassCheckbox
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.text.BrassLabel

/**
 * The compact decoration-colour row used by notes and groups. It deliberately mirrors the appearance
 * card: preset colour checkboxes followed by a `+` chip for an arbitrary colour. The enclosing
 * [net.swzo.brass.ui.kit.surface.BrassContextMenu] supplies the panel and click-away behaviour.
 */
internal class NodeDecorationPalette(
    selectedTone: FrameTone,
    customColor: Int?,
    onTone: (FrameTone) -> Unit,
    onCustom: (Float, Float, Float) -> Unit,
) : UIContainer() {

    init {
        val hasCustomColor = customColor != null
        BrassLabel("COLOR", Colors.UI_TEXT_DARK).constrain {
            x = PAD.pixels()
            y = 4f.pixels()
        } childOf this

        FrameTone.entries.forEachIndexed { index, tone ->
            val swatch = BrassCheckbox(
                initial = !hasCustomColor && tone == selectedTone,
                fixedAccent = BrassThemes.accentFor(tone.color()),
            )
            swatch.entranceEnabled = false
            swatch.constrain {
                x = (PAD + index * (SWATCH + GAP)).pixels()
                y = 17f.pixels()
                width = SWATCH.pixels()
                height = SWATCH.pixels()
            } childOf this
            swatch.onMouseClick { event ->
                if (event.mouseButton == 0) onTone(tone)
            }
        }

        val custom = object : BrassCheckbox(
            initial = hasCustomColor,
            fixedAccent = BrassThemes.accentFor(customColor?.let { java.awt.Color(it, true) } ?: Colors.UI_ACCENT),
            alwaysShowIcon = true,
        ) {
            override val icon: BrassIcons.Icon
                get() = if (checked) BrassIcons.CHECK else BrassIcons.PLUS
        }
        custom.entranceEnabled = false
        custom.constrain {
            x = (PAD + FrameTone.entries.size * (SWATCH + GAP)).pixels()
            y = 17f.pixels()
            width = SWATCH.pixels()
            height = SWATCH.pixels()
        } childOf this
        custom.onMouseClick { event ->
            if (event.mouseButton == 0) onCustom(custom.getLeft(), custom.getBottom() + 3f, custom.getTop() - 3f)
        }
    }

    private companion object {
        const val PAD = 6f
        const val SWATCH = 14f
        const val GAP = 4f
    }
}
