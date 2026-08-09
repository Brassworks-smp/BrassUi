package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassEased
import net.swzo.brass.ui.kit.base.BrassMetrics
import net.swzo.brass.ui.kit.base.BrassTree
import net.swzo.brass.ui.kit.layout.BrassCull
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.platform.BrassPlatform
import net.swzo.brass.ui.kit.surface.BrassTooltip.attach
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color
import kotlin.math.min

/**
 * Hover tooltips, drawn as a card above everything else.
 * Attach one to any component:
 * ```kotlin
 * BrassTooltip.attach(button, "Start the server")
 * BrassTooltip.attach(icon, "Danger", "This cannot be undone", accent = BrassAccent.DANGER)
 * ```
 * ### Behaviour
 * - **Delayed**, so sweeping the cursor across a toolbar does not flash a tooltip per button.
 * - **Fades in**, and once one tooltip is up, moving to another shows it immediately - the usual
 *   "tooltip mode" every desktop UI has, so a row of controls can be read by sweeping across it.
 * - **Clamped to the screen**: it prefers to sit below-right of the cursor and flips above or left
 *   when there is not room, so it can never be pushed off the edge.
 * - **Follows the cursor** horizontally by default; pin it to the component with `follow = false`.
 * ### Drawing
 * A tooltip is not a component in the tree - it is painted by the screen after everything else, from
 * a single shared instance. Putting it in the tree would mean fighting z-order with popups and the
 * dev overlay, and a floating component that reparents itself is exactly what caused the earlier
 * "menu chases the cursor" bug.
 */
object BrassTooltip {

    private class Entry(
        val title: () -> String,
        val body: () -> String?,
        val accent: BrassAccent,
        val follow: Boolean,
        val delayMs: Long?,
        val lines: () -> List<Pair<String, Color>> = { emptyList() },
        val custom: Custom? = null,
    )

    class Custom(
        val size: () -> FloatArray,
        val draw: (UMatrixStack, Float, Float, Float) -> Unit,
    )

    private val entries = java.util.WeakHashMap<UIComponent, Entry>()

    private val wired = java.util.WeakHashMap<UIComponent, Boolean>()

    private var hovered: UIComponent? = null
    private var hoveredAt = 0L

    private val fadeValue = BrassEased(0f, speed = 30f)
    private val fade: Float get() = fadeValue.value

    private var shown: Entry? = null

    private var warm = false
    private var wentColdAt = 0L

    private var mouseX = 0f
    private var mouseY = 0f

    var delayMs = 90L

    var warmMs = 700L

    var fadeSpeed = 30f

    /**
     * Maximum card width in pixels (including padding) before a tooltip line wraps at the nearest
     * word. Applied to the title/body and rich-line cards; also clamped to the screen so a tooltip
     * never spills off the edge on a small window. Custom-painted tooltips size themselves and are
     * unaffected.
     */
    var maxTextWidth = 260f

    fun attach(
        component: UIComponent,
        title: String,
        body: String? = null,
        accent: BrassAccent = BrassAccent.DEFAULT,
        follow: Boolean = true,
        delayMs: Long? = null,
    ): () -> Unit = attachLazy(component, { title }, { body }, accent, follow, delayMs)

    /**
     * As [attach], but the text is resolved each time the tooltip is shown.
     * Use this whenever the label depends on state that is not known when the widget is built - an
     * item's display name once the platform is bound, a live value, a changing selection. The
     * alternative, re-attaching on hover, corrupts Elementa's listener list while it is iterating it.
     */
    fun attachLazy(
        component: UIComponent,
        title: () -> String,
        body: () -> String? = { null },
        accent: BrassAccent = BrassAccent.DEFAULT,
        follow: Boolean = true,
        delayMs: Long? = null,
    ): () -> Unit {
        entries[component] = Entry(title, body, accent, follow, delayMs)
        wire(component)
        return { detach(component) }
    }

    fun attachRich(
        component: UIComponent,
        lines: () -> List<Pair<String, Color>>,
        accent: BrassAccent = BrassAccent.DEFAULT,
        follow: Boolean = true,
        delayMs: Long? = null,
    ): () -> Unit {
        entries[component] = Entry({ "" }, { null }, accent, follow, delayMs, lines)
        wire(component)
        return { detach(component) }
    }

    fun attachCustom(
        component: UIComponent,
        custom: Custom,
        accent: BrassAccent = BrassAccent.DEFAULT,
        follow: Boolean = true,
        delayMs: Long? = null,
    ): () -> Unit {
        entries[component] = Entry({ "" }, { null }, accent, follow, delayMs, custom = custom)
        wire(component)
        return { detach(component) }
    }

    private fun wire(component: UIComponent) {
        // Listeners are registered ONCE per component; a re-attach only swaps the entry.
        // Elementa has no way to remove a listener, so the previous version - which registered a
        // fresh enter/leave pair on every call - grew an unbounded chain of closures on any widget
        // whose tooltip text was refreshed, and ran all of them on every hover. The doc above
        // explicitly invites re-attaching, so this had to be idempotent rather than merely rare.
        // Tracked separately from `entries` so a detach-then-reattach cycle does not re-register
        // either: Elementa listeners cannot be removed, so "wired once" has to outlive the entry.
        if (wired.put(component, true) == null) {
            component.onMouseEnter {
                hovered = component
                hoveredAt = System.currentTimeMillis()
            }
            component.onMouseLeave {
                if (hovered === component) {
                    hovered = null
                    if (fade > 0.2f) { warm = true; wentColdAt = System.currentTimeMillis() }
                }
            }
        }
    }

    fun detach(component: UIComponent) {
        entries.remove(component)
        if (hovered === component) hovered = null
    }

    var gate: ((UIComponent) -> Boolean)? = null

    var lastBounds: FloatArray? = null
        private set

    fun cursor(x: Float, y: Float) {
        mouseX = x
        mouseY = y
    }

    fun draw(m: UMatrixStack, root: UIComponent, screenW: Float, screenH: Float) {
        if (warm && System.currentTimeMillis() - wentColdAt > warmMs) warm = false

        // A component removed from the tree (its popup or window closed) never fires onMouseLeave, so
        // the hover would stick and its tooltip would hang on screen. Drop it if it is no longer part of
        // the live tree under [root].
        hovered?.let { if (!BrassTree.isAttachedTo(it, root)) hovered = null }

        // A widget scrolled out of its view, hidden under a collapsed window, or pushed off the screen
        // keeps its hover state - Elementa dispatches enter/leave from geometry, not from what is
        // actually painted - so its tooltip would appear over empty space where the control *would*
        // have been. Nothing invisible gets to describe itself.
        hovered?.let { if (!BrassCull.visible(it)) hovered = null }

        // Whatever else is on top of the UI gets a veto - see [gate].
        hovered?.let { if (gate?.invoke(it) == false) hovered = null }

        val target = hovered?.let { c ->
            val entry = entries[c] ?: return@let null
            val waited = System.currentTimeMillis() - hoveredAt
            if (warm || waited >= (entry.delayMs ?: delayMs)) entry else null
        }

        // Content switches the instant a new tooltip qualifies, without fading the old one out
        // first. Cross-fading two different strings in the same box reads as a glitch, and a
        // half-faded card that then reverses direction mid-flight - the old behaviour - looked
        // like a stutter rather than a transition.
        if (target != null && target !== shown) {
            shown = target
            // Coming from a card that was already up, keep it up: only a genuinely cold tooltip
            // should be seen to fade in.
            if (fade > 0.05f) fadeValue.snapTo(fade.coerceAtLeast(0.85f))
        }

        fadeValue.speed = fadeSpeed
        fadeValue.target = if (target != null) 1f else 0f
        fadeValue.advance()
        if (fade <= 0.02f) {
            fadeValue.snapTo(0f)
            shown = null
            lastBounds = null
            return
        }

        val entry = shown
        if (entry == null) { lastBounds = null; return }
        paint(m, root, entry, screenW, screenH)
    }

    fun placeCard(
        w: Float, h: Float,
        mouseX: Float, mouseY: Float,
        screenW: Float, screenH: Float,
        avoid: FloatArray? = null,
    ): FloatArray {
        fun clampX(v: Float) = v.coerceIn(EDGE, (screenW - w - EDGE).coerceAtLeast(EDGE))
        fun clampY(v: Float) = v.coerceIn(EDGE, (screenH - h - EDGE).coerceAtLeast(EDGE))

        // The candidate positions, in order of preference.
        val below = clampY(mouseY + CURSOR_GAP)
        val above = clampY(mouseY - h - CURSOR_GAP)
        val right = clampX(mouseX + CURSOR_GAP)
        val left = clampX(mouseX - w - CURSOR_GAP)
        val candidates = buildList {
            add(right to below)
            add(left to below)
            add(right to above)
            add(left to above)
            // Clear of the obstacle (normally the hovered component): centred under it, above it,
            // then to its right and left. These are tried AFTER the cursor-relative spots so a
            // tooltip still follows the cursor when there is room, but never covers the control.
            if (avoid != null) {
                val centerX = clampX((avoid[0] + avoid[2]) / 2f - w / 2f)
                val centerY = clampY((avoid[1] + avoid[3]) / 2f - h / 2f)
                add(centerX to clampY(avoid[3] + GAP))
                add(centerX to clampY(avoid[1] - h - GAP))
                add(clampX(avoid[2] + GAP) to centerY)
                add(clampX(avoid[0] - w - GAP) to centerY)
            }
        }

        for ((cx, cy) in candidates) {
            if (avoid == null || !overlaps(cx, cy, w, h, avoid)) return floatArrayOf(cx, cy)
        }
        // Nothing fits cleanly - take the first choice rather than drawing nothing.
        return floatArrayOf(right, below)
    }

    private fun overlaps(x: Float, y: Float, w: Float, h: Float, r: FloatArray): Boolean =
        x < r[2] + GAP && x + w > r[0] - GAP && y < r[3] + GAP && y + h > r[1] - GAP

    private fun avoidBounds(screenW: Float, screenH: Float): FloatArray? {
        val anchor = hovered ?: return null
        if (anchor.getWidth() > screenW * MAX_AVOID_FRACTION) return null
        if (anchor.getHeight() > screenH * MAX_AVOID_FRACTION) return null
        return floatArrayOf(anchor.getLeft(), anchor.getTop(), anchor.getRight(), anchor.getBottom())
    }

    /**
     * Pinned placement (`follow = false`): below the component, flipping above when there is no room
     * below - never clamped up onto the component itself, which made a bottom-edge widget disappear
     * under its own tooltip.
     */
    private fun pinnedPosition(
        anchor: UIComponent,
        w: Float,
        h: Float,
        screenW: Float,
        screenH: Float,
    ): FloatArray {
        val x = anchor.getLeft().coerceIn(EDGE, (screenW - w - EDGE).coerceAtLeast(EDGE))
        val belowY = anchor.getBottom() + 4f
        val aboveY = anchor.getTop() - h - 4f
        val y = when {
            belowY + h <= screenH - EDGE -> belowY
            aboveY >= EDGE -> aboveY
            else -> belowY.coerceIn(EDGE, (screenH - h - EDGE).coerceAtLeast(EDGE))
        }
        return floatArrayOf(x, y)
    }

    /**
     * Components at most this fraction of the screen (per axis) count as "controls" for tooltip
     * avoidance; anything larger is a surface and is never avoided.
     */
    private const val MAX_AVOID_FRACTION = 0.4f
    private const val PAD = 5f

    private fun paint(m: UMatrixStack, root: UIComponent, entry: Entry, screenW: Float, screenH: Float) {
        entry.custom?.let { paintCustom(m, entry, screenW, screenH, it); return }
        val rich = entry.lines().takeIf { it.isNotEmpty() }
        if (rich != null) {
            paintRich(m, root, entry, screenW, screenH, rich)
            return
        }
        val title = entry.title()
        val body = entry.body()
        // A supplier that has nothing to say draws nothing, rather than an empty card. Suppliers are
        // asked every frame and legitimately have no answer sometimes - a grid whose cursor is over an
        // empty slot, a widget disabled with no reason given.
        if (title.isBlank() && body.isNullOrBlank()) return
        val textMaxW = (min(maxTextWidth, screenW - EDGE * 2) - PAD * 2).coerceAtLeast(20f)
        val rows = buildList {
            BrassFont.wrap(root, title, textMaxW).forEach { add(it to Colors.UI_TEXT_HOVER) }
            if (body != null) {
                BrassFont.wrap(root, body, textMaxW).forEach { add(it to Colors.UI_TEXT_DARK) }
            }
        }
        val w = (rows.maxOfOrNull { BrassFont.width(root, it.first) } ?: 0f) + PAD * 2
        val h = PAD * 2 + BrassFont.LINE * rows.size + (rows.size - 1) * 1f

        val anchor = hovered
        var x: Float
        var y: Float
        if (entry.follow || anchor == null) {
            // Keep the card off the hovered control itself - the cursor can sit on a small button
            // at the edge of the screen where every cursor-relative spot is taken, and the naive
            // answer (clamp up) landed the card on top of the control that raised it. Large
            // surfaces (the node editor canvas, full-screen panels) are deliberately NOT avoided -
            // their tooltips describe the cursor's target inside them, and avoiding the whole
            // surface would push the card outside the very UI it explains.
            val at = placeCard(w, h, mouseX, mouseY, screenW, screenH, avoidBounds(screenW, screenH))
            x = at[0]; y = at[1]
        } else {
            val at = pinnedPosition(anchor, w, h, screenW, screenH)
            x = at[0]; y = at[1]
        }

        // rise slightly as it fades in - the same motion the widgets use on entrance
        val rise = (1f - fade) * 3f
        y += rise

        lastBounds = floatArrayOf(x, y, x + w, y + h)

        // Land every glyph queued this frame BEFORE the card is drawn.
        // Minecraft batches text into a buffer source and flushes it later, so a label drawn earlier in
        // the frame - anywhere on screen - could still be in the queue when the tooltip's quads go
        // down, and its drop shadow then painted over the card. The tooltip is drawn last in the frame
        // and still lost to text that was "drawn" long before it. Flushing here forces the queue out
        // first; the tooltip's own text is queued after and lands on top, which is the order the draw
        // sequence already implied.
        BrassPlatform.current?.flushText()

        // the whole card fades as one piece - fading only the text left the panel popping in solid
        BrassCard.draw(m, x, y, x + w, y + h, shadow = true, alpha = fade)

        // a brass (or accent) rule down the left edge ties it to the rest of the chrome
        val accentColor = if (entry.accent.isDefault) Colors.UI_ACCENT else entry.accent.accent
        BrassPaint.rect(m, x, y, x + 1f, y + h, alpha(accentColor, fade))

        rows.forEachIndexed { i, (text, color) ->
            BrassFont.draw(
                m, root, text,
                x + PAD, y + PAD + i * (BrassFont.LINE + 1f), alpha(color, fade),
            )
        }
    }

    private fun paintCustom(
        m: UMatrixStack,
        entry: Entry,
        screenW: Float,
        screenH: Float,
        custom: Custom,
    ) {
        val size = custom.size()
        val w = size[0] + PAD * 2
        val h = size[1] + PAD * 2

        val anchor = hovered
        var x: Float
        var y: Float
        if (entry.follow || anchor == null) {
            val at = placeCard(w, h, mouseX, mouseY, screenW, screenH, avoidBounds(screenW, screenH))
            x = at[0]; y = at[1]
        } else {
            val at = pinnedPosition(anchor, w, h, screenW, screenH)
            x = at[0]; y = at[1]
        }

        val rise = (1f - fade) * 3f
        y += rise

        lastBounds = floatArrayOf(x, y, x + w, y + h)
        BrassPlatform.current?.flushText()

        BrassCard.draw(m, x, y, x + w, y + h, shadow = true, alpha = fade)
        val accentColor = if (entry.accent.isDefault) Colors.UI_ACCENT else entry.accent.accent
        BrassPaint.rect(m, x, y, x + 1f, y + h, alpha(accentColor, fade))

        custom.draw(m, x + PAD, y + PAD, fade)
    }

    private fun paintRich(
        m: UMatrixStack,
        root: UIComponent,
        entry: Entry,
        screenW: Float,
        screenH: Float,
        lines: List<Pair<String, Color>>,
    ) {
        val textMaxW = (min(maxTextWidth, screenW - EDGE * 2) - PAD * 2).coerceAtLeast(20f)
        val rows = lines.flatMap { (text, color) ->
            BrassFont.wrap(root, text, textMaxW).map { it to color }
        }
        val w = (rows.maxOfOrNull { BrassFont.width(root, it.first) } ?: 0f) + PAD * 2
        val h = PAD * 2 + BrassFont.LINE * rows.size + (rows.size - 1) * 1f

        val anchor = hovered
        var x: Float
        var y: Float
        if (entry.follow || anchor == null) {
            val at = placeCard(w, h, mouseX, mouseY, screenW, screenH, avoidBounds(screenW, screenH))
            x = at[0]; y = at[1]
        } else {
            val at = pinnedPosition(anchor, w, h, screenW, screenH)
            x = at[0]; y = at[1]
        }

        val rise = (1f - fade) * 3f
        y += rise

        lastBounds = floatArrayOf(x, y, x + w, y + h)
        BrassPlatform.current?.flushText()

        BrassCard.draw(m, x, y, x + w, y + h, shadow = true, alpha = fade)
        val accentColor = if (entry.accent.isDefault) Colors.UI_ACCENT else entry.accent.accent
        BrassPaint.rect(m, x, y, x + 1f, y + h, alpha(accentColor, fade))

        rows.forEachIndexed { i, (text, color) ->
            BrassFont.draw(
                m, root, text,
                x + PAD, y + PAD + i * (BrassFont.LINE + 1f), alpha(color, fade),
            )
        }
    }


    private fun alpha(c: Color, a: Float): Color {
        val ai = (255 * a.coerceIn(0f, 1f)).toInt()
        val argb = (ai shl 24) or (c.rgb and 0xFFFFFF)
        return alphaCache.getOrPut(argb) { Color(c.red, c.green, c.blue, ai) }
    }

    private val alphaCache = object : LinkedHashMap<Int, Color>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Color>) = size > 256
    }

    private const val CURSOR_GAP = BrassMetrics.CURSOR_GAP
    private const val EDGE = BrassMetrics.FLOATING_EDGE
    private const val GAP = BrassMetrics.CARD_GAP
}
