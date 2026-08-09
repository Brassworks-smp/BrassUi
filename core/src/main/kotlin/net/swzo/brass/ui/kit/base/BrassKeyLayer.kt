package net.swzo.brass.ui.kit.base

import gg.essential.universal.UKeyboard

/**
 * A floating layer that gets **first refusal** on key presses - ahead of keyboard navigation, ahead
 * of Escape, and ahead of the focused text field.
 * ### Why the focused field is not enough
 * [net.swzo.brass.ui.BrassScreen] skips its whole keyboard-navigation block while a text field has
 * focus, and rightly so: Tab and Enter belong to the field, and Space is a character. But a layer
 * like [net.swzo.brass.ui.kit.surface.BrassCommandPalette] is *built* around a focused field whose
 * up, down and Enter belong to the **list beside it** rather than to the caret. Without this hook the
 * palette is keyboard-first in design and mouse-only in practice - the arrows do nothing and Enter
 * inserts a newline.
 * Escape reaches [BrassDismissable] eventually, but only after it has first blurred the field, so a
 * palette took two presses to close. A layer implementing this can take it on the first.
 * Only the **topmost** implementor is asked, and only until one returns true. A layer that does not
 * recognise a key must return false so the key falls through to the field it was typed into.
 */
interface BrassKeyLayer {

    fun onLayerKey(keyCode: Int, modifiers: UKeyboard.Modifiers?): Boolean
}
