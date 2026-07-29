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
import net.swzo.brass.ui.kit.surface.BrassTooltip.attachLazy
import net.swzo.brass.ui.kit.surface.BrassTooltip.placeCard
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color
import kotlin.math.max

/**
 * Hover tooltips, drawn as a card above everything else.
 *
 * Attach one to any component:
 *
 * ```kotlin
 * BrassTooltip.attach(button, "Start the server")
 * BrassTooltip.attach(icon, "Danger", "This cannot be undone", accent = BrassAccent.DANGER)
 * ```
 *
 * ### Behaviour
 *
 * - **Delayed**, so sweeping the cursor across a toolbar does not flash a tooltip per button.
 * - **Fades in**, and once one tooltip is up, moving to another shows it immediately - the usual
 *   "tooltip mode" every desktop UI has, so a row of controls can be read by sweeping across it.
 * - **Clamped to the screen**: it prefers to sit below-right of the cursor and flips above or left
 *   when there is not room, so it can never be pushed off the edge.
 * - **Follows the cursor** horizontally by default; pin it to the component with `follow = false`.
 *
 * ### Drawing
 *
 * A tooltip is not a component in the tree - it is painted by the screen after everything else, from
 * a single shared instance. Putting it in the tree would mean fighting z-order with popups and the
 * dev overlay, and a floating component that reparents itself is exactly what caused the earlier
 * "menu chases the cursor" bug.
 */
object BrassTooltip {

    /**
     * What to show for a component.
     *
     * The text is held as **suppliers**, not strings, so a tooltip whose content is only knowable at
     * hover time can be attached once at construction. Resolving it by re-attaching from inside a
     * hover handler registers new listeners while Elementa is iterating them, which throws
     * ConcurrentModificationException mid-draw - see [attachLazy].
     */
    private class Entry(
        val title: () -> String,
        val body: () -> String?,
        val accent: BrassAccent,
        val follow: Boolean,
        /** Delay before this tooltip appears, overriding [delayMs]. */
        val delayMs: Long?,
    )

    private val entries = java.util.WeakHashMap<UIComponent, Entry>()

    /** Components whose hover listeners are already installed - see [attachLazy]. */
    private val wired = java.util.WeakHashMap<UIComponent, Boolean>()

    /** The component currently hovered, and when the hover started. */
    private var hovered: UIComponent? = null
    private var hoveredAt = 0L

    /** Eased 0..1 visibility. */
    private val fadeValue = BrassEased(0f, speed = 30f)
    private val fade: Float get() = fadeValue.value

    /** The entry actually being drawn. Kept while fading out, after the cursor has already left. */
    private var shown: Entry? = null

    /** True once a tooltip has been shown recently - the next one appears without waiting again. */
    private var warm = false
    private var wentColdAt = 0L

    private var mouseX = 0f
    private var mouseY = 0f

    /**
     * Default delay before a tooltip appears from cold, in milliseconds. Individual tooltips can
     * override it per [attach] - a decorative hint can afford to wait longer than this.
     *
     * Short on purpose: long enough that sweeping across a toolbar does not strobe a card per button,
     * short enough that deliberately resting on a control feels like it answers immediately.
     */
    var delayMs = 90L

    /** How long after hiding the "tooltip mode" stays warm, so a sweep across a row reads instantly. */
    var warmMs = 700L

    /** How fast the card fades in and out. Higher is snappier. */
    var fadeSpeed = 30f

    /**
     * Show [title] (and optional [body]) when [component] is hovered.
     *
     * Re-attaching replaces the previous text, so this is safe to call when a label changes. The
     * component is held weakly - a screen that goes away takes its tooltips with it.
     */
    fun attach(
        component: UIComponent,
        title: String,
        body: String? = null,
        accent: BrassAccent = BrassAccent.DEFAULT,
        follow: Boolean = true,
        /** Delay before this tooltip pops up; null uses the global [delayMs]. */
        delayMs: Long? = null,
    ): () -> Unit = attachLazy(component, { title }, { body }, accent, follow, delayMs)

    /**
     * As [attach], but the text is resolved each time the tooltip is shown.
     *
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
        // Listeners are registered ONCE per component; a re-attach only swaps the entry.
        //
        // Elementa has no way to remove a listener, so the previous version - which registered a
        // fresh enter/leave pair on every call - grew an unbounded chain of closures on any widget
        // whose tooltip text was refreshed, and ran all of them on every hover. The doc above
        // explicitly invites re-attaching, so this had to be idempotent rather than merely rare.
        entries[component] = Entry(title, body, accent, follow, delayMs)
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
        return { detach(component) }
    }

    /** Forget [component]'s tooltip. */
    fun detach(component: UIComponent) {
        entries.remove(component)
        if (hovered === component) hovered = null
    }

    /**
     * Consulted before a tooltip is shown; returning false suppresses it.
     *
     * The dev inspector installs one so a tooltip belonging to the UI *underneath* its panel cannot
     * show through - while still letting the panel's own controls have tooltips of their own. A flat
     * on/off switch could not tell those two cases apart.
     */
    var gate: ((UIComponent) -> Boolean)? = null

    /**
     * The rectangle this tooltip occupied on the last frame it drew, as `[left, top, right, bottom]`,
     * or null when nothing is up.
     *
     * Published so another floating card - the dev inspector's metadata readout - can place itself
     * clear of it via [placeCard] instead of landing on top of it.
     */
    var lastBounds: FloatArray? = null
        private set

    /** Track the cursor; called by the screen each frame. */
    fun cursor(x: Float, y: Float) {
        mouseX = x
        mouseY = y
    }

    /**
     * Draw the active tooltip, if any. Called by the screen after everything else has drawn, so the
     * card always sits on top.
     */
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

    /**
     * Place a [w] x [h] card near ([mouseX],[mouseY]), on screen and clear of [avoid].
     *
     * Tries below-right of the cursor first, then flips left and/or above when there is no room, and
     * finally slides off the obstacle. Shared with the dev inspector's metadata card: two floating
     * cards that both "prefer below-right of the cursor" land on each other every time, and the only
     * way for them not to is for the second one to know where the first one went.
     *
     * Returns `[x, y]`.
     */
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
        val candidates = listOf(
            right to below, left to below, right to above, left to above,
            // last resorts: stack directly under or over the obstacle, ignoring the cursor
            right to clampY((avoid?.get(3) ?: 0f) + GAP),
            right to clampY((avoid?.get(1) ?: 0f) - h - GAP),
        )

        for ((cx, cy) in candidates) {
            if (avoid == null || !overlaps(cx, cy, w, h, avoid)) return floatArrayOf(cx, cy)
        }
        // Nothing fits cleanly - take the first choice rather than drawing nothing.
        return floatArrayOf(right, below)
    }

    private fun overlaps(x: Float, y: Float, w: Float, h: Float, r: FloatArray): Boolean =
        x < r[2] + GAP && x + w > r[0] - GAP && y < r[3] + GAP && y + h > r[1] - GAP

    private fun paint(m: UMatrixStack, root: UIComponent, entry: Entry, screenW: Float, screenH: Float) {
        val pad = 5f
        val title = entry.title()
        val body = entry.body()
        // A supplier that has nothing to say draws nothing, rather than an empty card. Suppliers are
        // asked every frame and legitimately have no answer sometimes - a grid whose cursor is over an
        // empty slot, a widget disabled with no reason given.
        if (title.isBlank() && body.isNullOrBlank()) return
        val titleW = BrassFont.width(root, title)
        val bodyW = body?.let { BrassFont.width(root, it) } ?: 0f
        val w = max(titleW, bodyW) + pad * 2
        val lines = if (body != null) 2 else 1
        val h = pad * 2 + BrassFont.LINE * lines + (if (lines > 1) 2f else 0f)

        val anchor = hovered
        var x: Float
        var y: Float
        if (entry.follow || anchor == null) {
            val at = placeCard(w, h, mouseX, mouseY, screenW, screenH)
            x = at[0]; y = at[1]
        } else {
            // pinned to the component, still clamped on screen
            x = anchor.getLeft().coerceIn(EDGE, (screenW - w - EDGE).coerceAtLeast(EDGE))
            y = (anchor.getBottom() + 4f).coerceIn(EDGE, (screenH - h - EDGE).coerceAtLeast(EDGE))
        }

        // rise slightly as it fades in - the same motion the widgets use on entrance
        val rise = (1f - fade) * 3f
        y += rise

        lastBounds = floatArrayOf(x, y, x + w, y + h)

        // Land every glyph queued this frame BEFORE the card is drawn.
        //
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

        BrassFont.draw(m, root, title, x + pad, y + pad, alpha(Colors.UI_TEXT_HOVER, fade))
        if (body != null) {
            BrassFont.draw(
                m, root, body,
                x + pad, y + pad + BrassFont.LINE + 2f, alpha(Colors.UI_TEXT_DARK, fade),
            )
        }
    }



    /**
     * [c] at alpha [a].
     *
     * Memoised on the rounded ARGB, the same trick `BrassWidget.AnimColor` uses: the tooltip is drawn
     * every frame it is up, and for most of those frames the fade has settled and the answer is
     * identical - so a settled tooltip now allocates nothing, and a fading one allocates only while
     * the alpha is genuinely in motion.
     */
    private fun alpha(c: Color, a: Float): Color {
        val ai = (255 * a.coerceIn(0f, 1f)).toInt()
        val argb = (ai shl 24) or (c.rgb and 0xFFFFFF)
        return alphaCache.getOrPut(argb) { Color(c.red, c.green, c.blue, ai) }
    }

    /**
     * Keyed on the resulting ARGB rather than held in a single slot: a tooltip paints three different
     * colours per frame (the accent rule, the title, the body), so one slot would be evicted by the
     * next call every time and cache nothing at all. Bounded because the fade sweeps through a range
     * of alphas on the way in and out.
     */
    private val alphaCache = object : LinkedHashMap<Int, Color>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Color>) = size > 256
    }

    private const val CURSOR_GAP = BrassMetrics.CURSOR_GAP
    private const val EDGE = BrassMetrics.FLOATING_EDGE
    /** Clearance kept between two floating cards. */
    private const val GAP = BrassMetrics.CARD_GAP
}
