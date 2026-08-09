@file:Suppress("unused")
package net.swzo.brass.ui.kit.base

import gg.essential.elementa.UIComponent
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.BrassBlock
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.dev.BrassDevMode
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.platform.BrassCursor
import java.awt.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The base every Brass* widget extends, and the source of the toolkit's look and feel:
 * - **Raised "keycap" render**: a flat fill, a 1-px inner border on all four sides, a 1-px outer ring
 *   just outside it, and a 2–3-px **bottom edge** (a soft shadow + a coloured or dark lip) so the control
 *   reads as a physical pixel button sitting above the surface. `flat` drops the ring/edge; `rounded`
 *   swaps to a rounded rect.
 * - **Per-widget animated colours**: fill / border / text / outer / bottom each *lerp* toward their
 *   target every frame (hover / selected / accent).
 * - **Entrance cascade**: on first appearance the widget fades in and drops into place, staggered by its
 *   distance from the top-left so a screenful sweeps in diagonally.
 * - **Accent**: [accent] tints the control (see [BrassAccent]); the default accent uses neutral element
 *   colours.
 * Subclasses implement [drawContent] to paint their interior; the base hands them the current pixel
 * bounds and exposes the live animated [bgColor]/[borderColor]/[textColor].
 */
abstract class BrassWidget(
    var accent: BrassAccent = BrassAccent.DEFAULT,
) : UIComponent() {

    var active: Boolean = true

    var disabledReason: String? = null
        private set

    fun disable(reason: String? = null) {
        active = false
        disabledReason = reason
        if (reason != null) {
            net.swzo.brass.ui.kit.surface.BrassTooltip.attachLazy(
                this,
                title = { disabledReason ?: "" },
                // The tooltip is attached once and answers from the live state, so it disappears of
                // its own accord when the widget is re-enabled rather than needing a detach.
                body = { null },
            )
        }
    }

    fun enable() {
        active = true
        disabledReason = null
    }
    var selected: Boolean = false
    var selectable: Boolean = false

    var chrome: BrassChrome = BrassChrome.KEYCAP

    @Deprecated("Use chrome = BrassChrome.FLAT", ReplaceWith("chrome"))
    var flat: Boolean
        get() = chrome == BrassChrome.FLAT || chrome == BrassChrome.NONE
        set(value) { if (value && chrome == BrassChrome.KEYCAP) chrome = BrassChrome.FLAT }
    var transparent: Boolean = false

    @Deprecated("Use chrome = BrassChrome.NONE", ReplaceWith("chrome"))
    var chromeless: Boolean
        get() = chrome == BrassChrome.NONE
        set(value) { if (value) chrome = BrassChrome.NONE }

    @Deprecated("Use chrome = BrassChrome.ROUNDED", ReplaceWith("chrome"))
    var rounded: Boolean
        get() = chrome == BrassChrome.ROUNDED
        set(value) { if (value) chrome = BrassChrome.ROUNDED }
    var roundness: Float = 6f
    var entranceEnabled: Boolean = true

    var opacity: Float = 1f

    protected var hoveredState: Boolean = false

    var proxyHovered: Boolean = false

    var clickable: Boolean = false

    private var pressedState = false

    private var press = 0f

    // live animated colours (exposed to subclasses), faded by both the widget's own entrance and any
    // ancestor frame currently animating (see BrassAmbientFade)
    protected val bgColor: Color get() = animBg.toColor(alpha)
    protected val borderColor: Color get() = animBorder.toColor(alpha)
    protected val textColor: Color get() = animText.toColor(alpha)
    protected val bottomColor: Color get() = animBottom.toColor(alpha)
    protected val outerColor: Color get() = animOuter.toColor(alpha)

    private val alpha: Float get() = entranceAlpha * opacity * BrassAmbientFade.current

    private val animBg = AnimColor(accent.dark.takeIf { !accent.isDefault } ?: Colors.UI_ELEMENT_BG)
    private val animBorder = AnimColor(if (accent.isDefault) Colors.UI_ELEMENT_BORDER else accent.accent)
    private val animText = AnimColor(if (accent.isDefault) Colors.UI_TEXT else accent.accent)
    private val animBottom = AnimColor(accent.bottom)
    private val animOuter = AnimColor(accent.outer)

    // entrance
    private var entranceState = EntranceState.WAITING
    private var entranceStarted = false
    private var entranceDelay = -1f
    private var entranceProgress = 0f
    private var entranceAlpha = 1f

    protected val entranceFade: Float get() = alpha
    private var entranceRise = 0f

    init {
        onMouseEnter { hoveredState = true }
        onMouseLeave { hoveredState = false }
        onMouseClick { e ->
            if (active && clickable && e.mouseButton == 0) {
                pressedState = true
                // Clicking a focusable control moves focus to it, without the ring - see BrassFocus.
                if (this is BrassFocusable) BrassFocus.focus(this, fromKeyboard = false)
            }
        }
        // Elementa broadcasts releases to the whole tree, which is exactly what is wanted here: a
        // press that ends with the cursor dragged off the widget must still pop back up.
        onMouseRelease { pressedState = false }
    }

    protected val bx: Int get() = getLeft().roundToInt()
    protected val by: Int get() = getTop().roundToInt()
    protected val bw: Int get() = getRight().roundToInt() - getLeft().roundToInt()
    protected val bh: Int get() = getBottom().roundToInt() - getTop().roundToInt()

    private fun advanceColors(dt: Float, hovered: Boolean, sel: Boolean) {
        val ct = (COLOR_SPEED * dt).coerceAtMost(1f)
        // targets (mirror ThemeManager.getElement*Color)
        val tBg: Color; val tBorder: Color; val tText: Color
        if (accent.isDefault) {
            tBg = if (sel) (if (hovered) accent.darkHover else accent.dark)
                  else if (hovered) Colors.UI_ELEMENT_BG_HOVER else Colors.UI_ELEMENT_BG
            tBorder = if (sel) (if (hovered) accent.accentHover else accent.accent)
                      else if (hovered) Colors.UI_ELEMENT_BORDER_HOVER else Colors.UI_ELEMENT_BORDER
            tText = if (sel) (if (hovered) accent.accentHover else accent.accent)
                    else if (hovered) Colors.UI_TEXT_HOVER else Colors.UI_TEXT
        } else {
            tBg = if (hovered) accent.darkHover else accent.dark
            tBorder = if (hovered) accent.accentHover else accent.accent
            tText = if (hovered) accent.accentHover else accent.accent
        }
        val tBg2 = if (active) tBg else Colors.UI_ELEMENT_BG
        val tBorder2 = if (active) tBorder else Colors.UI_ELEMENT_BORDER
        val tText2 = if (active) tText else Colors.UI_TEXT_DARK
        animBg.advance(tBg2, ct)
        animBorder.advance(tBorder2, ct * 0.8f)
        animText.advance(tText2, ct * 0.5f)
        animBottom.advance(if (hovered) accent.bottomHover else accent.bottom, ct)
        animOuter.advance(accent.outer, ct * 0.8f)
    }

    private fun settleEntrance() {
        entranceState = EntranceState.DONE
        entranceAlpha = 1f
        entranceRise = 0f
        entranceProgress = 1f
    }

    private fun advanceEntrance(dt: Float) {
        if (!entranceEnabled) { settleEntrance(); return }

        when (entranceState) {
            EntranceState.WAITING -> when (BrassEntrance.phase) {
                // The frame is opening (or has just settled): begin the cascade *now*, so the contents
                // fade and rise in together with the frame growing.
                // This used to hold every widget fully invisible for the whole open animation and only
                // start the cascade once the frame had landed - a second animation stacked after the
                // first. The result was a window that popped open empty, sat there for a beat, and only
                // then filled in, which read as "the widgets take forever to appear". Starting on the
                // first OPENING frame also makes the cascade robust: a widget is guaranteed to observe
                // OPENING (it lasts several frames), whereas the brief SETTLING window could be skipped
                // entirely by a hitch - e.g. a screenful of remote images decoding and uploading right
                // as the frame landed - leaving those widgets to snap in with no entrance at all.
                BrassEntrance.Phase.OPENING, BrassEntrance.Phase.SETTLING -> {
                    entranceState = EntranceState.RUNNING
                    // stagger by distance from the top-left corner (a diagonal sweep), capped short so
                    // the last widget on screen is not left visibly waiting its turn
                    entranceDelay = ((bx + by) * ENTRANCE_DELAY_FACTOR).coerceIn(0f, ENTRANCE_DELAY_MAX)
                }
                // Appeared with nothing opening - scrolled into view, or added to a settled screen.
                BrassEntrance.Phase.IDLE -> { settleEntrance(); return }
            }

            // Once running, it runs to completion whatever the frame goes on to do; a cascade cut off
            // half way because the settle window closed would look worse than no cascade at all.
            EntranceState.RUNNING -> Unit

            EntranceState.DONE -> { entranceAlpha = 1f; entranceRise = 0f; return }
        }

        if (!entranceStarted) {
            entranceDelay -= dt
            if (entranceDelay <= 0f) entranceStarted = true
            entranceAlpha = 0f; entranceRise = ENTRANCE_RISE
            return
        }
        if (entranceProgress < 1f) {
            entranceProgress = (entranceProgress + ENTRANCE_SPEED * dt).coerceAtMost(1f)
        }
        entranceAlpha = (entranceProgress * 2f).coerceAtMost(1f)
        val eased = 1f - (1f - entranceProgress) * (1f - entranceProgress) * (1f - entranceProgress)
        entranceRise = (1f - eased) * ENTRANCE_RISE
        if (entranceProgress >= 1f) entranceState = EntranceState.DONE
    }

    private enum class EntranceState { WAITING, RUNNING, DONE }

    override fun draw(matrixStack: UMatrixStack) {
        beforeDraw(matrixStack)
        // Shared frame step. A private clock here meant a widget that had been culled came back with
        // a large dt of its own while its neighbours had a small one, so a row of controls that should
        // animate together visibly did not. See BrassClock.
        val dt = BrassClock.dt
        val hovered = (hoveredState || proxyHovered) && active
        val sel = selectable && selected
        advanceColors(dt, hovered, sel)
        advanceEntrance(dt)

        // Key travel: a pressed keycap sinks toward the surface. The body moves down and the lip
        // under it loses the same pixel, so the key looks compressed rather than merely translated -
        // moving both together would just slide the whole control down.
        val pressTarget = if (pressedState && active && clickable) 1f else 0f
        press += (pressTarget - press) * (PRESS_SPEED * dt).coerceAtMost(1f)
        if (abs(press - pressTarget) < 0.01f) press = pressTarget

        // Hovering a clickable widget asks for the pointer cursor; the screen resolves competing
        // requests once per frame (see BrassCursor).
        if (hovered && clickable) BrassCursor.request(BrassCursor.Kind.HAND)

        if (alpha <= 0.001f) { super.draw(matrixStack); return }

        // No cull check here on purpose: Elementa's UIComponent.draw already tests every child with
        // Window.isAreaVisible (screen bounds *and* the active scissor) and skips its draw entirely.
        // Reaching this line means we are visible. See BrassCull for the full explanation.
        BrassStats.painted()

        matrixStack.push()
        matrixStack.translate(0f, entranceRise + press * PRESS_TRAVEL, 0f)

        val x = bx; val y = by; val w = bw; val h = bh
        if (w > 0 && h > 0) {
            // chrome first, then content on top of it - a chromeless widget simply has no chrome
            if (chrome.paintsBackground) {
                if (chrome == BrassChrome.ROUNDED) {
                    BrassBlock.drawBox(matrixStack, x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(),
                        roundness, bgColor, borderColor, outerColor)
                } else {
                    drawKeycap(matrixStack, x, y, w, h)
                }
            }
            drawContent(matrixStack, x, y, w, h)
        }

        matrixStack.pop()

        // Focus ring: a brass outline just outside the keycap, drawn only for keyboard focus.
        if (BrassFocus.showRing && BrassFocus.isFocused(this) && w > 0 && h > 0) {
            BrassPaint.border(
                matrixStack,
                x - FOCUS_RING, y - FOCUS_RING,
                x + w + FOCUS_RING, y + h + FOCUS_RING,
                withAlpha(Colors.UI_ACCENT_BRIGHT, alpha),
            )
        }

        // layout inspector (Ctrl+Shift+D) - additive only, and a no-op while disabled
        if (BrassDevMode.enabled) {
            val (mx, my) = getMousePosition()
            BrassDevMode.inspect(
                matrixStack, this, mx, my,
                bleedX = BLEED_X,
                bleedTop = if (chrome == BrassChrome.KEYCAP) BLEED_TOP else 0f,
                bleedBottom = if (chrome == BrassChrome.KEYCAP) BLEED_BOTTOM else 0f,
            )
        }

        super.draw(matrixStack)
    }

    private fun drawKeycap(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        // the lip loses a pixel as the key goes down
        val lip = (press * PRESS_TRAVEL).roundToInt().toFloat()
        net.swzo.brass.ui.kit.paint.BrassKeycap.draw(
            m, x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(),
            bg = bgColor, border = borderColor, outer = outerColor, bottom = bottomColor,
            transparent = transparent,
            flat = chrome == BrassChrome.FLAT,
            defaultAccent = accent.isDefault,
            lip = lip,
        )
    }

    /**
     * What a click on this widget *means* - toggling, pressing, ticking. Subclasses that do something
     * on click override this, which lets any other component stand in for them via [BrassProxy].
     * Deliberately separate from the widget's own click listener: the listener is about *where* the
     * click landed, this is about what it does. Named `proxyActivate` rather than `activate` because
     * Elementa's UIComponent is full of final members and a collision is a link-time crash, not a
     * compile error.
     */
    open fun proxyActivate() {}

    protected abstract fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int)

    protected fun withAlpha(c: Color, alpha: Float): Color =
        if (alpha >= 1f) c
        else Color(c.red, c.green, c.blue, (c.alpha * alpha.coerceIn(0f, 1f)).roundToInt())

    companion object {
        const val COLOR_SPEED = 14f
        const val MOVE_SPEED = 16f
        const val PRESS_SPEED = 28f
        const val PRESS_TRAVEL = 1f
        // Entrance is deliberately subtle: a short 4-px rise that resolves quickly, with only a slight
        // diagonal stagger. The earlier values (14 px over a long cascade) read as a loading screen
        // rather than a UI settling into place. The cascade now runs *concurrently* with the frame
        // opening (see advanceEntrance), so the stagger is kept short - a longer one only re-introduces
        // the "why is this taking so long" wait it was meant to avoid.
        const val ENTRANCE_RISE = 4f
        const val ENTRANCE_SPEED = 8f
        const val ENTRANCE_DELAY_FACTOR = 0.0004f
        const val ENTRANCE_DELAY_MAX = 0.10f
        val SOFT_SHADOW: Color get() = Colors.SOFT_SHADOW

        // A keycap deliberately paints *outside* its own box: a 1-px outer ring on three sides, a 3–4-px
        // bottom lip, and it lifts 2 px while hovered. Any container that clips (a ScrollComponent
        // scissors to its bounds) must leave at least this much room, or the borders get shaved off.
        // Layout code should use these rather than re-deriving the numbers.
        const val FOCUS_RING = 2f

        const val BLEED_X = 1f
        const val BLEED_TOP = 1f
        const val BLEED_BOTTOM = 4f

        /**
         * Margin added when culling. The largest bleed on any side, plus a pixel of slack - a widget
         * must not blink out while part of its ring or lip is still on screen.
         */
        const val CULL_BLEED = BLEED_BOTTOM + 1f
    }

    protected class AnimColor(initial: Color) {
        private var r = initial.red.toFloat()
        private var g = initial.green.toFloat()
        private var b = initial.blue.toFloat()
        private var a = initial.alpha.toFloat()

        private var cached: Color = initial
        private var cachedArgb: Int = initial.rgb

        fun advance(target: Color, t: Float) {
            r += (target.red - r) * t
            g += (target.green - g) * t
            b += (target.blue - b) * t
            a += (target.alpha - a) * t
        }

        fun toColor(alpha: Float = 1f): Color {
            val ri = r.roundToInt().coerceIn(0, 255)
            val gi = g.roundToInt().coerceIn(0, 255)
            val bi = b.roundToInt().coerceIn(0, 255)
            val ai = (a * alpha.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
            val argb = (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
            if (argb != cachedArgb) {
                cachedArgb = argb
                cached = Color(ri, gi, bi, ai)
            }
            return cached
        }
    }
}
