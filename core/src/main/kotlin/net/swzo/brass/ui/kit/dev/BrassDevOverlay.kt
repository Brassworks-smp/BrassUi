package net.swzo.brass.ui.kit.dev

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import net.swzo.brass.ui.kit.base.BrassStats
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTag
import net.swzo.brass.ui.kit.text.BrassWrappedLabel
import java.awt.Color

/**
 * Marker for everything that belongs to the **dev overlay** rather than the UI being inspected. Two
 * things key off it: [BrassStats.count] skips these subtrees so the overlay never inflates the widget
 * count it reports, and the inspector never highlights or lists itself.
 */
interface BrassDevOverlay

internal fun tagFor(c: UIComponent): Pair<String, Color> = when (c) {
    is BrassLabel, is BrassWrappedLabel, is BrassTag -> "Text" to BrassTag.TEXT
    is ScrollComponent -> "Scroll" to BrassTag.CONTAINER
    is BrassWidget -> c.javaClass.simpleName.removePrefix("Brass").ifEmpty { "Widget" } to BrassTag.WIDGET
    is UIContainer -> "Container" to BrassTag.CONTAINER
    is UIBlock -> "Rectangle" to BrassTag.BORDER_TAG
    else -> c.javaClass.simpleName.ifEmpty { "anon" } to BrassTag.ROOT
}
