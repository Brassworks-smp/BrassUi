package net.swzo.brass.ui.kit.node

import gg.essential.elementa.UIComponent
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassEased
import net.swzo.brass.ui.kit.input.BrassColorPicker
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.surface.BrassContextMenu
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color

/**
 * The context a node control is drawn into: the matrix (already translated + scaled to the canvas'
 * zoom, so a control drawn in **world units** here scales uniformly with everything else), the host
 * component fonts are measured against, the zoom (for skipping detail when tiny), and the world-space
 * mouse for the control's own hover test.
 *
 * [screenX]/[screenY] map a world point to the on-screen GUI pixel it lands on, for the few things that
 * must work outside the canvas matrix - notably a [gg.essential.elementa.effects.ScissorEffect], whose
 * bounds are screen-space and ignore the active matrix.
 */
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
) {
    fun screenX(wx: Float): Float = originX + panX + wx * zoom
    fun screenY(wy: Float): Float = originY + panY + wy * zoom
}

/**
 * A node's inline setting - its **sub-config**. A field draws its own control with the toolkit's shared
 * painters (so it looks and animates exactly like the real widgets), handles its own press/scrub, and
 * knows how to encode itself to a primitive for [NodeGraph]'s save/load.
 *
 * [visibleWhen] lets a node's panel rearrange itself as its own values change (pick *Worley* and the
 * "Ridged" toggle folds away) - the "sub-config per config" idea.
 *
 * ### Extending
 *
 * This is deliberately `open`, not `sealed`: a library user adds a custom field by subclassing it,
 * painting whatever they like in [drawControl] (through the same [BrassCard]/[BrassPaint] helpers), and
 * implementing [encode]/[decode]. The editor never switches on the concrete type, so a custom field is a
 * first-class citizen of the graph, its panel, and its saved file.
 */
abstract class NodeField(val key: String, val label: String) {

    var visibleWhen: () -> Boolean = { true }

    /** Eased hover / press, driven by the editor and read by [drawControl] - the same feel as a keycap. */
    val hover = BrassEased(0f, speed = 14f)
    val press = BrassEased(0f, speed = 26f)

    /** Gate this field's visibility on a predicate (usually another field's value). Chains. */
    fun onlyWhen(predicate: () -> Boolean): NodeField {
        visibleWhen = predicate
        return this
    }

    /** Paint the control inside world rect `[x1,y1]..[x2,y2]`. [h]/[p] are the eased hover/press 0..1. */
    abstract fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float)

    /**
     * A press landed inside the control at world x [wx] (the control spans [x1]..[x2]). Immediate
     * controls (toggle, enum, stepper) apply the change here and return null; a slider applies and
     * returns a drag handler that keeps scrubbing as the cursor moves.
     */
    open fun onPress(wx: Float, x1: Float, x2: Float): ((Float) -> Unit)? = null

    /** Whether a press should open a floating editor instead - a colour swatch, a text field. */
    open val opensEditor: Boolean get() = false

    /** Open that editor, floating at screen ([sx],[sy]) under [root]. Reuses the real toolkit widgets. */
    open fun showEditor(root: UIComponent, host: UIComponent, sx: Float, sy: Float) {}

    /** A short line for the field's tooltip. */
    open fun tip(): String = label

    /** The field's value as a JSON primitive (Boolean / Number / String) for [NodeGraph.toJson]. */
    abstract fun encode(): Any

    /** Restore from a value produced by [encode] (Gson hands numbers back as Double). */
    abstract fun decode(v: Any?)

    /** Vertically centre a control of [height] inside the row `[y1,y2]`, returning its own top/bottom. */
    protected fun centre(y1: Float, y2: Float, height: Float): Pair<Float, Float> {
        val cy1 = y1 + (y2 - y1 - height) / 2f
        return cy1 to cy1 + height
    }
}

/** An on/off switch, drawn as a miniature toggle track + grip. */
class ToggleField(key: String, label: String, var on: Boolean = false) : NodeField(key, label) {
    override fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float) {
        val tw = 22f
        val t1 = x2 - tw
        val (cy1, cy2) = centre(y1, y2, 12f)
        BrassCard.trackShell(ctx.m, t1, cy1, x2, cy2, track = if (on) Colors.BRASS_700 else Colors.UI_ELEMENT_BG)
        val kw = (x2 - t1) / 2f
        val kx = if (on) x2 - kw else t1
        BrassCard.grip(ctx.m, kx, cy1 - 1f, kx + kw, cy2 + 1f, glow = if (on) 1f else h)
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
    override fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float) {
        val (cy1, cy2) = centre(y1, y2, 12f)
        BrassCard.filledTrack(ctx.m, x1, cy1, x2, cy2, value)
        val kx = x1 + 1f + (x2 - x1 - 2f) * value.coerceIn(0f, 1f)
        BrassCard.grip(ctx.m, kx - 2f, cy1 - 1f, kx + 2f, cy2 + 1f, glow = 0.5f + 0.5f * maxOf(h, p))
        val txt = readout(value)
        if (txt.isNotEmpty() && ctx.zoom > 0.6f) {
            BrassFont.draw(ctx.m, ctx.host, txt, x2 - BrassFont.width(ctx.host, txt) - 3f,
                (cy1 + cy2) / 2f - BrassFont.LINE / 2f, Colors.UI_TEXT_HOVER)
        }
    }
    override fun onPress(wx: Float, x1: Float, x2: Float): ((Float) -> Unit) {
        val set = { w: Float -> value = ((w - (x1 + 1f)) / (x2 - x1 - 2f)).coerceIn(0f, 1f) }
        set(wx)
        return set
    }
    override fun tip() = "$label: ${(value * 100).toInt()}%"
    override fun encode(): Any = value
    override fun decode(v: Any?) { value = (v as? Number)?.toFloat() ?: value }
}

/** A cycler over [options], drawn as a mini keycap with prev/next arrows. */
class EnumField(key: String, label: String, val options: List<String>, var index: Int = 0) : NodeField(key, label) {
    val current: String get() = options[index.coerceIn(0, options.size - 1)]
    fun next() { index = (index + 1) % options.size }
    fun prev() { index = (index - 1 + options.size) % options.size }
    override fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float) {
        val (cy1, cy2) = centre(y1, y2, 13f)
        BrassCard.miniKeycap(ctx.m, x1, cy1, x2, cy2, hot = h > 0.4f)
        NodeGlyph.arrow(ctx.m, x1 + 4f, (cy1 + cy2) / 2f, left = true)
        NodeGlyph.arrow(ctx.m, x2 - 4f, (cy1 + cy2) / 2f, left = false)
        val txt = BrassFont.fit(ctx.host, current, x2 - x1 - 16f)
        BrassFont.draw(ctx.m, ctx.host, txt, (x1 + x2) / 2f - BrassFont.width(ctx.host, txt) / 2f,
            (cy1 + cy2) / 2f - BrassFont.LINE / 2f, Colors.UI_TEXT)
    }
    override fun onPress(wx: Float, x1: Float, x2: Float): ((Float) -> Unit)? {
        if (wx < (x1 + x2) / 2f) prev() else next(); return null
    }
    override fun tip() = "$label: $current"
    override fun encode(): Any = current
    override fun decode(v: Any?) {
        val i = options.indexOf(v as? String)
        index = if (i >= 0) i else ((v as? Number)?.toInt() ?: index).coerceIn(0, options.size - 1)
    }
}

/** A stepped integer in [min]..[max], drawn as a mini keycap with -/+ ends. */
class StepperField(
    key: String, label: String,
    var value: Int = 0, val min: Int = 0, val max: Int = 9, val step: Int = 1,
) : NodeField(key, label) {
    fun inc() { value = (value + step).coerceAtMost(max) }
    fun dec() { value = (value - step).coerceAtLeast(min) }
    override fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float) {
        val (cy1, cy2) = centre(y1, y2, 13f)
        val cy = (cy1 + cy2) / 2f
        BrassCard.miniKeycap(ctx.m, x1, cy1, x2, cy2, hot = h > 0.4f)
        BrassFont.draw(ctx.m, ctx.host, "-", x1 + 4f, cy - BrassFont.LINE / 2f, Colors.UI_TEXT)
        BrassFont.draw(ctx.m, ctx.host, "+", x2 - 6f, cy - BrassFont.LINE / 2f, Colors.UI_TEXT)
        val txt = value.toString()
        BrassFont.draw(ctx.m, ctx.host, txt, (x1 + x2) / 2f - BrassFont.width(ctx.host, txt) / 2f,
            cy - BrassFont.LINE / 2f, Colors.UI_TEXT)
    }
    override fun onPress(wx: Float, x1: Float, x2: Float): ((Float) -> Unit)? {
        if (wx < (x1 + x2) / 2f) dec() else inc(); return null
    }
    override fun tip() = "$label: $value"
    override fun encode(): Any = value
    override fun decode(v: Any?) { value = (v as? Number)?.toInt()?.coerceIn(min, max) ?: value }
}

/** A colour chip; a press opens the real [BrassColorPicker] in a floating menu. */
class ColorField(key: String, label: String, var color: Color) : NodeField(key, label) {
    override fun drawControl(ctx: NodeDrawCtx, x1: Float, y1: Float, x2: Float, y2: Float, h: Float, p: Float) {
        val (cy1, cy2) = centre(y1, y2, 13f)
        val sw = 22f
        // a raised swatch: the same ring + border a mini keycap has, filled with the colour
        BrassPaint.rect(ctx.m, x2 - sw - 1f, cy1 - 1f, x2 + 1f, cy2 + 1f, Colors.UI_OUTER_BORDER)
        BrassPaint.rect(ctx.m, x2 - sw, cy1, x2, cy2, color)
        BrassPaint.border(ctx.m, x2 - sw, cy1, x2, cy2, if (h > 0.4f) Colors.UI_ELEMENT_BORDER_HOVER else Colors.UI_ELEMENT_BORDER)
    }
    override val opensEditor: Boolean get() = true
    override fun showEditor(root: UIComponent, host: UIComponent, sx: Float, sy: Float) {
        val picker = BrassColorPicker(color) { color = it }
        BrassContextMenu.custom(picker, 168, 140).show(root, sx, sy)
    }
    override fun tip() = label
    override fun encode(): Any = color.rgb
    override fun decode(v: Any?) { (v as? Number)?.let { color = Color(it.toInt(), true) } }
}
