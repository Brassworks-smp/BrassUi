package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.layout.BrassFlow
import net.swzo.brass.ui.kit.layout.BrassScrollArea
import net.swzo.brass.ui.kit.layout.BrassSpacing

/**
 * A **dialog**: a titled modal with a bleed-safe body and, optionally, a footer of buttons - the shape
 * every "ask the user one thing" popup is, without the boilerplate each one otherwise repeats.
 * ### Why this exists
 * Every modal in an app is the same five-line incantation - `BrassPopup(title, modal = true, showHeader
 * = true, showCloseButton = true, scrollingBody = false)` … `popup.showModal(root, w, h)` - followed by
 * hand-placed content, and every one of them makes the same two mistakes:
 * 1. **Buttons and controls clip.** A dialog's content area carries a `ScissorEffect` clipped to its own
 *    bounds. A keycap paints a 1-px outer ring and a 3–4-px bottom lip *outside* its box (see
 *    [BrassWidget]), so a button pinned flush to the content edge - `y = 0.pixels(alignOpposite = true)`,
 *    the obvious way to put a Save button at the bottom - has its black border and lip fall outside the
 *    scissor and shaved off. Widgets near the right edge lose their ring the same way.
 * 2. **The layout is bespoke every time**, so the buttons sit at slightly different insets in every
 *    dialog and none of them line up with the next.
 * [BrassModal] fixes both. [body] is inset by exactly the keycap bleed, so anything placed in it keeps
 * its full border; [footer] lays buttons out in a wrapping row that reserves the same bleed, so they
 * never clip and never collide when the dialog is narrow.
 * ```kotlin
 * BrassModal("Nickname", width = 220f, height = 120f)
 *     .body { host ->
 *         BrassLabel("What everyone sees you as.").constrain { … } childOf host
 *         field.constrain { … } childOf host
 *     }
 *     .footer(
 *         BrassButton("Clear", BrassAccent.DANGER) { … },
 *         BrassButton("Set", BrassAccent.NICE) { … },
 *     )
 *     .show(root)
 * ```
 * For a dialog whose body scrolls (a long list, a tall card), call [scrollBody] instead of [body] and
 * fill the [BrassScrollArea] it returns. For anything this does not cover, [popup] is the underlying
 * [BrassPopup].
 */
class BrassModal @JvmOverloads constructor(
    title: String,
    private val width: Float,
    private val height: Float,
    showClose: Boolean = true,
    /** Whether Escape dismisses the dialog. Turn off for one that must be answered by its buttons. */
    dismissOnEscape: Boolean = true,
    onClose: () -> Unit = {},
) {

    val popup: BrassPopup = BrassPopup(
        title,
        onClose,
        modal = true,
        showHeader = true,
        showCloseButton = showClose,
        dismissOnEscape = dismissOnEscape,
        // This lays out its own body (a bleed-safe region and a footer), not a stack of form rows.
        scrollingBody = false,
    )

    private var footerRow: BrassFlow? = null

    val body: UIContainer = UIContainer().constrain {
        x = BrassWidget.BLEED_X.pixels()
        y = BrassWidget.BLEED_TOP.pixels()
        width = 100.percent() - (BrassWidget.BLEED_X * 2).pixels()
        height = basicHeightConstraint { c ->
            val f = footerRow
            if (f == null) {
                // No footer: fill to the bottom, less the bleed so a control pinned to the body's own
                // bottom keeps its lip inside the frame's scissor.
                (c.parent.getHeight() - BrassWidget.BLEED_TOP - BrassWidget.BLEED_BOTTOM).coerceAtLeast(0f)
            } else {
                // With a footer: fill down to its top, so the two never overlap however the footer wraps.
                (f.getTop() - c.getTop() - BrassSpacing.TIGHT).coerceAtLeast(0f)
            }
        }
    } childOf popup.content

    fun body(build: (UIContainer) -> Unit): BrassModal {
        build(body)
        return this
    }

    fun scrollBody(): BrassScrollArea =
        BrassScrollArea().constrain {
            x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent()
        } childOf body

    /**
     * A footer of [buttons] pinned to the bottom of the dialog, laid out in a wrapping row that
     * **reserves the keycap bleed** (so the buttons never clip against the frame) and **wraps onto
     * another line when the dialog is too narrow** (so they never collide). The buttons share the width
     * evenly - a Cancel / Save pair sits as two halves, three tools as three thirds.
     */
    fun footer(vararg buttons: BrassButton): BrassModal = footer(FOOTER_H, *buttons)

    fun footer(height: Float, vararg buttons: BrassButton): BrassModal {
        val flow = BrassFlow(gapX = BrassSpacing.GAP, gapY = BrassSpacing.TIGHT, itemHeight = height, stretch = true)
        buttons.forEach { flow.add(it, FOOTER_MIN_W) }
        flow.constrain {
            x = BrassWidget.BLEED_X.pixels()
            // Pinned to the bottom. BrassFlow reserves the bleed inside itself, so the buttons' lip sits
            // in that reserved band rather than over the frame's edge.
            y = 0.pixels(alignOpposite = true)
            width = 100.percent() - (BrassWidget.BLEED_X * 2).pixels()
            this.height = basicHeightConstraint { flow.contentHeight() }
        } childOf popup.content
        footerRow = flow
        return this
    }

    fun show(root: UIComponent): BrassModal {
        popup.showModal(root, width, height)
        return this
    }

    fun dismiss() = popup.dismiss()

    private companion object {
        const val FOOTER_H = 18f

        const val FOOTER_MIN_W = 72f
    }
}
