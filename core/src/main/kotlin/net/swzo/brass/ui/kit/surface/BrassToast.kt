package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassEased
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.input.BrassSquareButton
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.surface.BrassToast.Companion.LIFE
import net.swzo.brass.ui.kit.surface.BrassToast.Companion.show
import net.swzo.brass.ui.kit.text.BrassFont
import java.util.*

/**
 * A notification toast - a keycap chip that **slides in** from the right edge, holds, then slides back
 * out. The accent and glyph come from the [Type]. Fire one with [show].
 * ### The stack
 * Toasts are not positioned once and left. The live toasts for a screen are held in a list, and each
 * one eases toward the slot its **current index** implies, every frame. That single decision is what
 * fixes the two things a fire-and-forget toast gets wrong: a new toast can no longer be handed the slot
 * of one that is still visibly sliding away (it is only removed from the list when it is genuinely
 * gone), and when a toast at the bottom does leave, everything above it slides down to fill the gap
 * instead of leaving a hole in the column.
 * ### Sticky toasts
 * A toast normally expires after [LIFE]. Pass `sticky = true` for one that stays until something closes
 * it - the close button, or [close] from code. That is the case for progress: a download toast should
 * live exactly as long as the download, not for two and a half seconds.
 */
class BrassToast private constructor(
    private val message: String,
    private val type: Type,
    /** Never expire on its own; closes via the button or [close]. */
    private val sticky: Boolean,
) : BrassWidget(type.accent), BrassLayers.Layer {

    /**
     * The top of the stack, above every window, menu and palette.
     * Without a declared rank a toast ranked as ordinary content, so the first click on any popup -
     * whose raise puts it above everything content-ranked - buried the notification a second after
     * it slid in. A toast is precisely the thing that must survive other layers claiming the screen.
     */
    override val rank: BrassLayers.Rank get() = BrassLayers.Rank.OVERLAY

    enum class Type(val accent: BrassAccent, val icon: BrassIcons.Icon) {
        SUCCESS(BrassAccent.NICE, BrassIcons.CHECK),
        INFO(BrassAccent.CALM, BrassIcons.DOTS),
        WARN(BrassAccent.DEFAULT, BrassIcons.WARN),
        ERROR(BrassAccent.DANGER, BrassIcons.CLOSE),
    }

    private val born = System.currentTimeMillis()
    private var onscreenX = 0f
    private var offscreenX = 0f
    private var root: UIComponent? = null

    private var closingAt = 0L

    private val slot = BrassEased(0f, speed = SETTLE_SPEED)
    private var slotPrimed = false

    var progress: Float? = null

    init {
        entranceEnabled = false
    }

    fun close() {
        if (closingAt == 0L) closingAt = System.currentTimeMillis()
    }

    fun dismiss() = close()

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        BrassIcons.draw(m, type.icon, (x + 8).toFloat(), (y + (h - 8) / 2f), 8f, textColor)
        val ty = y + (h - BrassFont.LINE) / 2 + 1
        // ellipsise rather than let a long message run past the chip; leave room for the close button
        val show = BrassFont.fit(this, message, (w - 46).toFloat())
        BrassFont.draw(m, this, show, (x + 22).toFloat(), ty.toFloat(), textColor, true)

        // A progress toast reads its state from a hairline along the bottom edge rather than a full
        // BrassProgressBar - a card inside a card, at this size, is unreadable.
        progress?.let { p ->
            val end = x + w * p.coerceIn(0f, 1f)
            BrassPaint.rect(m, x.toFloat(), (y + h - 2).toFloat(), end, (y + h).toFloat(), Colors.UI_ACCENT)
        }
    }

    override fun draw(matrixStack: UMatrixStack) {
        val now = System.currentTimeMillis()
        val elapsed = now - born

        // A non-sticky toast starts closing itself once its time is up.
        if (!sticky && closingAt == 0L && elapsed > LIFE - SLIDE_MS) close()

        // Horizontal slide: ease-out on the way in, and back out again once closing.
        val slideIn = (elapsed / SLIDE_MS.toFloat()).coerceIn(0f, 1f)
        val out = if (closingAt == 0L) 0f else ((now - closingAt) / SLIDE_MS.toFloat()).coerceIn(0f, 1f)
        val p = (1f - (1f - slideIn) * (1f - slideIn)) * (1f - out)
        val curX = offscreenX + (onscreenX - offscreenX) * p

        // Vertical slot: eased toward wherever this toast currently sits in the stack, so the column
        // closes up behind a departing toast rather than leaving a gap.
        val target = targetY()
        if (!slotPrimed) { slotPrimed = true; slot.snapTo(target) }
        slot.target = target
        slot.advance()

        constrain { x = curX.pixels(); y = slot.value.pixels() }
        super.draw(matrixStack)

        // Removed only once it is fully off screen - until then it keeps its place in the stack, which
        // is what stops a new toast being dropped on top of it.
        if (out >= 1f) remove()
    }

    private fun targetY(): Float {
        val r = root ?: return slot.value
        val stack = live[r] ?: return 0f
        val index = stack.indexOf(this).coerceAtLeast(0)
        val rootH = r.getHeight()
        val slot = index.coerceAtMost(maxSlot(rootH))
        return (rootH - MARGIN - H - slot * (H + GAP)).coerceAtLeast(4f)
    }

    private fun remove() {
        val r = root ?: return
        live[r]?.remove(this)
        r.removeChild(this)
        root = null
    }

    companion object {
        const val LIFE = 2600L
        const val SLIDE_MS = 240L
        const val SETTLE_SPEED = 14f

        private const val W = 190
        private const val H = 22
        private const val GAP = 6
        private const val MARGIN = 16

        /**
         * Live toasts per screen root, bottom-most first. Weak, because a root dies with its screen and
         * this must not be what keeps it alive.
         */
        private val live = WeakHashMap<UIComponent, MutableList<BrassToast>>()

        private fun maxSlot(rootH: Float): Int =
            (((rootH - MARGIN - H) / (H + GAP)).toInt() - 1).coerceAtLeast(0)

        fun maxVisible(rootH: Float): Int = maxSlot(rootH) + 1

        fun show(
            screenRoot: UIComponent,
            message: String,
            type: Type = Type.INFO,
            sticky: Boolean = false,
        ): BrassToast {
            val stack = live.getOrPut(screenRoot) { ArrayList() }
            // Retire the oldest toasts once the column is full.
            // targetY() clamps a toast's *slot* to what fits on screen, so without a cap here every
            // toast past that point resolved to the SAME slot and they rendered exactly on top of
            // one another - a loop firing thirty notifications produced one unreadable smear rather
            // than thirty notifications. Closing the oldest is what a full column should do anyway.
            val room = maxVisible(screenRoot.getHeight())
            if (stack.size >= room) {
                stack.take(stack.size - room + 1).forEach { it.close() }
            }

            val t = BrassToast(message, type, sticky)
            val rootW = screenRoot.getWidth()
            // never wider than the screen allows
            val w = W.toFloat().coerceAtMost((rootW - 20f).coerceAtLeast(60f))

            t.onscreenX = (rootW - w - 14f).coerceAtLeast(4f)
            t.offscreenX = rootW + 6f
            t.root = screenRoot
            stack.add(t)

            t.constrain {
                x = t.offscreenX.pixels(); y = 0.pixels()
                width = w.pixels(); height = H.pixels()
            }
            t childOf screenRoot
            t.isFloating = true

            // Every toast is dismissible by hand, sticky or not - a notification you cannot get rid of
            // is an obstruction, and for a sticky one it is the only way out.
            BrassSquareButton(BrassIcons.CLOSE, BrassAccent.DEFAULT) { t.close() }.also {
                it.entranceEnabled = false
                it.iconScale = 0.45f
            }.constrain {
                x = 5.pixels(true); y = CenterConstraint()
                width = 12.pixels(); height = 12.pixels()
            } childOf t

            return t
        }

        fun clear(screenRoot: UIComponent) {
            live[screenRoot]?.toList()?.forEach { it.close() }
        }
    }
}
