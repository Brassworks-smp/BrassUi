package net.swzo.brass.ui.kit.node

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UKeyboard
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.base.BrassFocus
import net.swzo.brass.ui.kit.base.BrassEased
import net.swzo.brass.ui.kit.input.BrassColorPicker
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.paint.BrassKeycap
import net.swzo.brass.ui.kit.paint.BrassSwatch
import net.swzo.brass.ui.kit.surface.BrassContextMenu
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassTextInput
import java.awt.Color
import kotlin.math.roundToInt

/** The context a node control is drawn into: the zoomed canvas matrix, the host, and the mouse. */
class NodeDrawCtx(
    val m: UMatrixStack,
    val host: UIComponent,
    val zoom: Float,
    val time: Float,
    val mouseWx: Float,
    val mouseWy: Float,
    val originX: Float = 0f,
    val originY: Float = 0f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    // Geometry outside the visible viewport is skipped entirely - the quad count runs away otherwise.
    val visMinX: Float = Float.NEGATIVE_INFINITY,
    val visMinY: Float = Float.NEGATIVE_INFINITY,
    val visMaxX: Float = Float.POSITIVE_INFINITY,
    val visMaxY: Float = Float.POSITIVE_INFINITY,
    val detail: Float = 1f,
) {

    var lodRects: BrassPaint.QuadBatch? = null

    var coveredBy: ((GraphNode, Float, Float, Float, Float) -> Boolean)? = null

    /** The world-space rects of nodes drawn ABOVE [GraphNode] that overlap it (may partially overlap). */
    var coverersOf: ((GraphNode) -> List<FloatArray>?)? = null

    fun screenX(wx: Float): Float = originX + panX + wx * zoom
    fun screenY(wy: Float): Float = originY + panY + wy * zoom

    fun visible(x1: Float, y1: Float, x2: Float, y2: Float): Boolean =
        x2 >= visMinX && x1 <= visMaxX && y2 >= visMinY && y1 <= visMaxY

    companion object {
        fun smoothstep(a: Float, b: Float, x: Float): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}

/**
 * A node's inline setting: draws its own control with the toolkit's shared painters, handles its own
 * press/scrub, and encodes to a primitive for [NodeGraph]'s save/load. Open for custom fields - the
 * editor never switches on the concrete type.
 */
abstract class NodeField(val key: String, val label: String) {

    var visibleWhen: () -> Boolean = { true }

    var description: String? = null

    val hover = BrassEased(0f, speed = 14f)
    val press = BrassEased(0f, speed = 26f)
    val reveal = BrassEased(1f, speed = 16f)

    fun onlyWhen(predicate: () -> Boolean): NodeField {
        visibleWhen = predicate
        return this
    }

    abstract fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float)

    open fun onPress(wx: Float, x1: Float, x2: Float): ((Float) -> Unit)? = null

    open fun onRelease() {}

    open val opensEditor: Boolean get() = false

    open fun showEditor(root: UIComponent, host: UIComponent, sx: Float, sy: Float) {}

    open fun onRightPress(root: UIComponent, host: UIComponent, sx: Float, sy: Float): Boolean = false

    /**
     * True for buttons whose presses the HOST must act on: executors run on a different copy of the
     * graph, so a client-side latch could never fire a node.
     */
    open val manualPress: Boolean get() = false

    /**
     * Called by the hosting editor after a quick-entry commit, to record the edit and broadcast it.
     * Every quick-entry commit must invoke this, or the machine never saves until some other edit.
     */
    var onQuickEdit: (() -> Unit)? = null

    open fun tip(): String = label

    open fun tooltipSize(host: UIComponent): FloatArray? = null

    open fun drawTooltip(m: UMatrixStack, host: UIComponent, x: Float, y: Float, alpha: Float) {}

    abstract fun encode(): Any

    abstract fun decode(v: Any?)

    protected fun centre(y1: Float, y2: Float, height: Float): Pair<Float, Float> {
        val cy1 = y1 + (y2 - y1 - height) / 2f
        return cy1 to cy1 + height
    }
}

/**
 * A floating focused text input that commits on Enter. Any dismissal that was not a successful Enter
 * commits too, so a typed value is never silently rolled back.
 */
fun openQuickTextEntry(root: UIComponent, sx: Float, sy: Float, initial: String, commit: (String) -> Boolean) {
    lateinit var menu: BrassContextMenu
    val input = BrassTextInput(initial)
    val content = UIContainer()
    var committed = false
    input.constrain {
        x = 0.pixels(); y = 0.pixels()
        width = 100.percent(); height = 100.percent()
    } childOf content
    input.onSubmit = { text ->
        if (commit(text)) { committed = true; menu.dismiss() }
    }
    menu = BrassContextMenu.custom(content, 170, 40)
    menu.onDismiss = {
        // A dismissal that did not come from a successful submit is still an answer: apply the text
        // (invalid text simply leaves the field unchanged, and a no-op commit broadcasts nothing).
        if (!committed) commit(input.text)
    }
    menu.show(root, sx, sy)
    BrassFocus.focus(input)
}

private object NodeControlChrome {
    data class Bounds(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    fun draw(
        ctx: NodeDrawCtx,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        hover: Float,
        press: Float,
    ): Bounds {
        val travel = press.coerceIn(0f, 1f)
        val top = y1 + travel
        val bottom = y2 + travel
        val bg = Colors.mix(Colors.UI_ELEMENT_BG, Colors.UI_ELEMENT_BG_HOVER, hover)
        val border = Colors.mix(Colors.UI_ELEMENT_BORDER, Colors.UI_ELEMENT_BORDER_HOVER, hover)
        BrassKeycap.draw(
            ctx.m, x1, top, x2 - x1, bottom - top,
            bg = bg,
            border = border,
            outer = Colors.UI_OUTER_BORDER,
            bottom = Colors.mix(bg, Color.BLACK, 0.45f),
            defaultAccent = true,
            lip = travel,
        )
        return Bounds(x1, top, x2, bottom)
    }
}

/** An on/off switch, drawn as a miniature toggle track + grip. */
class ToggleField(key: String, label: String, var on: Boolean = false) : NodeField(key, label) {
    private val slide = BrassEased(if (on) 1f else 0f, speed = 15f)

    override fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float) {
        slide.target = if (on) 1f else 0f
        val amount = slide.advance()
        val tw = 30f
        val t1 = x2 - tw
        val (cy1, cy2) = centre(y1, y2, NodeLayout.FIELD_CONTROL_H)
        val b = NodeControlChrome.draw(ctx, t1, cy1, x2, cy2, h, p)
        val ix1 = b.x1 + 3f; val ix2 = b.x2 - 3f
        val iy1 = b.y1 + 3f; val iy2 = b.y2 - 3f
        BrassCard.filledTrack(ctx.m, ix1, iy1, ix2, iy2, amount)
        val knob = (iy2 - iy1).coerceAtLeast(4f)
        val travel = (ix2 - ix1 - knob).coerceAtLeast(0f)
        val kx = ix1 + travel * amount
        BrassCard.grip(ctx.m, kx, iy1 - 1f, kx + knob, iy2 + 1f, glow = maxOf(h, amount * 0.65f))
    }
    override fun onPress(wx: Float, x1: Float, x2: Float): ((Float) -> Unit)? { on = !on; return null }
    override fun tip() = "$label: ${if (on) "on" else "off"}"
    override fun encode(): Any = on
    override fun decode(v: Any?) {
        on = when (v) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            else -> on
        }
    }
}

/** A 0..1 scrubber, drawn as a filled track + grip with a value readout. */
class SliderField(
    key: String, label: String,
    var value: Float = 0.5f,
    private val readout: (Float) -> String = { "" },
) : NodeField(key, label) {
    private val displayed = BrassEased(value.coerceIn(0f, 1f), speed = 18f)
    private var scrubbing = false
    private var scrubStartValue = 0f
    private var scrubStartWx = 0f

    override fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float) {
        displayed.target = value.coerceIn(0f, 1f)
        if (scrubbing) displayed.snapTo(displayed.target) else displayed.advance()
        val amount = displayed.value
        val (cy1, cy2) = centre(y1, y2, NodeLayout.FIELD_CONTROL_H)
        val b = NodeControlChrome.draw(ctx, x1, cy1, x2, cy2, h, p)
        val ix1 = b.x1 + 3f; val ix2 = b.x2 - 3f
        val iy1 = b.y1 + 3f; val iy2 = b.y2 - 3f
        BrassCard.filledTrack(ctx.m, ix1, iy1, ix2, iy2, amount)
        val kx = ix1 + (ix2 - ix1) * amount
        BrassCard.grip(ctx.m, kx - 2f, iy1 - 1f, kx + 2f, iy2 + 1f, glow = maxOf(h, p))
        val txt = readout(value)
        if (txt.isNotEmpty() && ctx.zoom > 0.6f) {
            BrassFont.draw(ctx.m, ctx.host, txt, x2 - BrassFont.width(ctx.host, txt) - 3f,
                (b.y1 + b.y2) / 2f - BrassFont.LINE / 2f, Colors.UI_TEXT_HOVER)
        }
    }
    override fun onPress(wx: Float, x1: Float, x2: Float): (Float) -> Unit {
        scrubbing = true
        scrubStartWx = wx
        // Jump to the click, as the plain slider does; the drag continues from there.
        value = ((wx - (x1 + 3f)) / (x2 - x1 - 6f)).coerceIn(0f, 1f)
        // Captured after the jump so Shift's fine mode is relative to where the value actually is.
        scrubStartValue = value
        return { w -> scrubTo(w, x1, x2) }
    }

    private fun scrubTo(w: Float, x1: Float, x2: Float) {
        val span = (x2 - x1 - 6f).coerceAtLeast(1f)
        value = if (UKeyboard.isShiftKeyDown()) {
            (scrubStartValue + (w - scrubStartWx) / span * 0.1f).coerceIn(0f, 1f)
        } else {
            ((w - (x1 + 3f)) / span).coerceIn(0f, 1f)
        }
    }
    override fun onRelease() { scrubbing = false }
    override fun onRightPress(root: UIComponent, host: UIComponent, sx: Float, sy: Float): Boolean {
        openQuickTextEntry(root, sx, sy, ((value * 100f).roundToInt()).toString()) { text ->
            val pct = text.trim().toDoubleOrNull() ?: return@openQuickTextEntry false
            value = (pct / 100.0).toFloat().coerceIn(0f, 1f)
            displayed.snapTo(value)
            onQuickEdit?.invoke()
            true
        }
        return true
    }
    override fun tip() = "$label: ${(value * 100).toInt()}% (0% - 100%)"
    override fun encode(): Any = value
    override fun decode(v: Any?) { value = (v as? Number)?.toFloat() ?: value }
}

/**
 * A cycler over [options], drawn as a mini keycap with prev/next arrows.
 * [options] are the **stored** values (saved to JSON, read by executors), so they stay stable. [displayOf]
 * maps a stored value to the text actually shown - the hook a host uses to localise the labels without
 * changing what is persisted or compared.
 */
class EnumField(
    key: String,
    label: String,
    val options: List<String>,
    var index: Int = 0,
    private val displayOf: (String) -> String = { it },
) : NodeField(key, label) {
    private val change = BrassEased(1f, speed = 22f)
    private var previous = current

    val current: String get() = options[index.coerceIn(0, options.size - 1)]
    fun next() = changeTo((index + 1) % options.size)
    fun prev() = changeTo((index - 1 + options.size) % options.size)

    private fun changeTo(next: Int) {
        if (next == index) return
        previous = current
        index = next
        change.snapTo(0f)
        change.target = 1f
    }

    override fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float) {
        val (cy1, cy2) = centre(y1, y2, NodeLayout.FIELD_CONTROL_H)
        val b = NodeControlChrome.draw(ctx, x1, cy1, x2, cy2, h, p)
        NodeGlyph.arrow(ctx.m, x1 + 4f, (b.y1 + b.y2) / 2f, left = true)
        NodeGlyph.arrow(ctx.m, x2 - 4f, (b.y1 + b.y2) / 2f, left = false)
        val amount = change.advance()
        animatedValueText(
            ctx, displayOf(previous), displayOf(current), amount,
            x1 + 8f, x2 - 8f, (b.y1 + b.y2) / 2f,
        )
    }
    override fun onPress(wx: Float, x1: Float, x2: Float): ((Float) -> Unit)? {
        if (wx < (x1 + x2) / 2f) prev() else next(); return null
    }
    override fun onRightPress(root: UIComponent, host: UIComponent, sx: Float, sy: Float): Boolean {
        // A dropdown of every option - jump straight to one instead of cycling.
        val items = options.map { opt ->
            BrassContextMenu.Item(displayOf(opt)) {
                changeTo(options.indexOf(opt))
                onQuickEdit?.invoke()
            }
        }
        BrassContextMenu(items).show(root, sx, sy)
        return true
    }
    override fun tip() = "$label: ${displayOf(current)}"
    override fun encode(): Any = current
    override fun decode(v: Any?) {
        val i = options.indexOf(v as? String)
        index = if (i >= 0) i else ((v as? Number)?.toInt() ?: index).coerceIn(0, options.size - 1)
        previous = current
        change.snapTo(1f)
    }
}

/** A stepped integer in [min]..[max], drawn as a mini keycap with -/+ ends. */
class StepperField(
    key: String, label: String,
    var value: Int = 0, val min: Int = 0, val max: Int = 9, val step: Int = 1,
) : NodeField(key, label) {
    private val change = BrassEased(1f, speed = 22f)
    private var previous = value.toString()
    private var scrubStartValue = 0
    private var scrubStartWx = 0f

    fun inc() = changeTo((value + step).coerceAtMost(max))
    fun dec() = changeTo((value - step).coerceAtLeast(min))

    private fun changeTo(next: Int) {
        if (next == value) return
        previous = value.toString()
        value = next
        change.snapTo(0f)
        change.target = 1f
    }

    override fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float) {
        val (cy1, cy2) = centre(y1, y2, NodeLayout.FIELD_CONTROL_H)
        val b = NodeControlChrome.draw(ctx, x1, cy1, x2, cy2, h, p)
        val cy = (b.y1 + b.y2) / 2f
        BrassFont.draw(ctx.m, ctx.host, "-", x1 + 4f, cy - BrassFont.LINE / 2f, Colors.UI_TEXT)
        BrassFont.draw(ctx.m, ctx.host, "+", x2 - 6f, cy - BrassFont.LINE / 2f, Colors.UI_TEXT)
        animatedValueText(ctx, previous, value.toString(), change.advance(), x1 + 10f, x2 - 10f, cy)
    }
    override fun onPress(wx: Float, x1: Float, x2: Float): (Float) -> Unit {
        scrubStartWx = wx
        // The +/- halves still step once on a plain click; a drag from anywhere scrubs relative to
        // the press point, so click-and-hold then move left/right tunes the value in either direction.
        if (wx < (x1 + x2) / 2f) dec() else inc()
        // Captured after the +/- click so the first small drag continues from the stepped value
        // instead of reverting it.
        scrubStartValue = value
        return { w -> scrubTo(w) }
    }

    private fun scrubTo(w: Float) {
        val fine = UKeyboard.isShiftKeyDown()
        // A full control-width drag is roughly 8 steps normally, under one with shift.
        val perPx = step * (if (fine) 0.008f else 0.08f)
        val target = scrubStartValue + ((w - scrubStartWx) * perPx).roundToInt()
        changeTo(target.coerceIn(min, max))
    }
    override fun onRightPress(root: UIComponent, host: UIComponent, sx: Float, sy: Float): Boolean {
        openQuickTextEntry(root, sx, sy, value.toString()) { text ->
            val next = text.trim().toIntOrNull() ?: return@openQuickTextEntry false
            changeTo(next.coerceIn(min, max))
            onQuickEdit?.invoke()
            true
        }
        return true
    }
    override fun tip() = "$label: $value  ($min … $max)"
    override fun encode(): Any = value
    override fun decode(v: Any?) {
        value = (v as? Number)?.toInt()?.coerceIn(min, max) ?: value
        previous = value.toString()
        change.snapTo(1f)
    }
}

private fun animatedValueText(
    ctx: NodeDrawCtx,
    previous: String,
    current: String,
    amount: Float,
    x1: Float,
    x2: Float,
    cy: Float,
) {
    fun draw(text: String, y: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val fitted = BrassFont.fit(ctx.host, text, x2 - x1)
        val saved = BrassAmbientFade.current
        BrassAmbientFade.current = saved * alpha
        BrassFont.draw(
            ctx.m, ctx.host, fitted,
            (x1 + x2) / 2f - BrassFont.width(ctx.host, fitted) / 2f,
            y - BrassFont.LINE / 2f, Colors.UI_TEXT,
        )
        BrassAmbientFade.current = saved
    }

    // One label occupies the slot at a time. The previous slide animation drew two pixel-font words
    // through each other, which reads as a smear at this scale; a brisk hand-off stays crisp.
    val split = 0.42f
    if (amount < split) draw(previous, cy, 1f - amount / split)
    else draw(current, cy, ((amount - split) / (1f - split)).coerceIn(0f, 1f))
}

/**
 * A **colour-display** field: the colour shown as the same raised keycap swatch the appearance card's
 * accent chips wear (see [BrassSwatch]), with its hex overlaid, and a press opens the real
 * [BrassColorPicker] in a floating menu. Translucent colours show a checkerboard behind them.
 */
class ColorField(key: String, label: String, var color: Color) : NodeField(key, label) {
    override fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float) {
        val (baseY1, baseY2) = centre(y1, y2, NodeLayout.FIELD_CONTROL_H)
        val cy1 = baseY1 + p
        val cy2 = baseY2 + p
        // A clean colour keycap, the way the theme swatches read; the hex lives in the tooltip so the
        // chip stays pixel-clean rather than carrying text that fights the pixel-art surface.
        BrassSwatch.draw(ctx.m, x1, cy1, x2, cy2, color, hot = maxOf(h, p), lip = p)
    }
    override val opensEditor: Boolean get() = true
    override fun showEditor(root: UIComponent, host: UIComponent, sx: Float, sy: Float) {
        val picker = BrassColorPicker(color) { color = it }
        BrassContextMenu.custom(picker, 168, 140).show(root, sx, sy)
    }
    override fun tip() = "$label  #%02X%02X%02X".format(color.red, color.green, color.blue)
    override fun encode(): Any = color.rgb
    override fun decode(v: Any?) { (v as? Number)?.let { color = Color(it.toInt(), true) } }
}

/**
 * A 2-component **vector** value (x, y), drawn as two mini keycaps you scrub left/right - the standard
 * inline control for a position or an offset. Each half scrubs its own component relative to the drag,
 * so a small movement is a fine adjustment rather than a jump to the cursor.
 */
class Vec2Field(
    key: String, label: String,
    var x: Float = 0f, var y: Float = 0f,
    private val speed: Float = 0.03f,
) : NodeField(key, label) {
    private var prevW = 0f
    private var comp = 0

    override fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float) {
        val (cy1, cy2) = centre(y1, y2, NodeLayout.FIELD_CONTROL_H)
        val mid = (x1 + x2) / 2f
        half(ctx, x1, cy1, mid - 1f, cy2, x, h, p)
        half(ctx, mid + 1f, cy1, x2, cy2, y, h, p)
    }

    private fun half(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, v: Float, h: Float, p: Float) {
        val b = NodeControlChrome.draw(ctx, x1, y1, x2, y2, h, p)
        val txt = BrassFont.fit(ctx.host, fmt(v), x2 - x1 - 4f)
        BrassFont.draw(ctx.m, ctx.host, txt, (x1 + x2) / 2f - BrassFont.width(ctx.host, txt) / 2f,
            (b.y1 + b.y2) / 2f - BrassFont.LINE / 2f, Colors.UI_TEXT)
    }

    override fun onPress(wx: Float, x1: Float, x2: Float): (Float) -> Unit {
        comp = if (wx < (x1 + x2) / 2f) 0 else 1
        prevW = wx
        return { w ->
            val d = (w - prevW) * speed
            if (comp == 0) x += d else y += d
            prevW = w
        }
    }

    private fun fmt(v: Float): String = ((v * 100f).roundToInt() / 100f).toString()
    override fun tip() = "$label: ${fmt(x)}, ${fmt(y)}"
    override fun encode(): Any = "${x},${y}"
    override fun decode(v: Any?) {
        (v as? String)?.split(',')?.let { p ->
            x = p.getOrNull(0)?.trim()?.toFloatOrNull() ?: x
            y = p.getOrNull(1)?.trim()?.toFloatOrNull() ?: y
        }
    }
}

/**
 * A momentary **action button** inside a node - runs [onClick] on press. Stateless, so it serializes to
 * nothing meaningful; it exists for a per-node command (randomise a seed, reset, bake) rather than a
 * stored value.
 */
class ButtonField(key: String, label: String, private val text: String, private val onClick: () -> Unit) :
    NodeField(key, label) {
    override fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float) {
        val (cy1, cy2) = centre(y1, y2, NodeLayout.FIELD_CONTROL_H)
        val b = NodeControlChrome.draw(ctx, x1, cy1, x2, cy2, h, p)
        val txt = BrassFont.fit(ctx.host, text, x2 - x1 - 6f)
        BrassFont.draw(ctx.m, ctx.host, txt, (x1 + x2) / 2f - BrassFont.width(ctx.host, txt) / 2f,
            (b.y1 + b.y2) / 2f - BrassFont.LINE / 2f,
            Colors.mix(Colors.UI_TEXT, Colors.UI_TEXT_HOVER, h))
    }
    override fun onPress(wx: Float, x1: Float, x2: Float): ((Float) -> Unit)? { onClick(); return null }
    override fun tip() = label
    override fun encode(): Any = 0
    override fun decode(v: Any?) {}
}
