package net.swzo.brass.ui.kit.input

import gg.essential.elementa.dsl.*
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.input.BrassColorPicker.Companion.PAD
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassTextInput
import java.awt.Color
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A colour picker, laid out as a single **card**: a saturation/value square with a live preview swatch
 * beside it, a hue strip below, and an editable **hex field** along the bottom. Every gap - card edge to
 * square, square to swatch, square to hue strip, hue strip to hex field - is the same [PAD], so it reads
 * as one tidy panel.
 * The hex field is a real [BrassTextInput]: type `#1FBF63` and the picker jumps to it; scrub the square
 * or the hue strip and the field rewrites itself. The two stay in sync without looping because the
 * write-back uses [BrassTextInput.setTextSilently], which does not re-fire the field's change callback.
 * ### Smooth, in 8 quads
 * Gradients are drawn as **vertex-coloured quads**, not a grid of flat cells: the SV square is a
 * hue-to-white horizontal quad with a transparent-to-black quad over it, and the hue strip is six quads,
 * one per 60-degree sector. Eight quads a frame, no texture, no banding at any size.
 */
class BrassColorPicker(
    initial: Color = Colors.BRASS_500,
    private val onPick: (Color) -> Unit = {},
) : BrassWidget(BrassAccent.DEFAULT), BrassValue<Color> {

    private var hue: Float
    private var sat: Float
    private var brightness: Float

    private val holder = BrassValueHolder(initial) { c -> applyColor(c) }

    override var value: Color
        get() = holder.value
        set(v) { holder.value = v }

    override fun setSilently(value: Color) = holder.setSilently(value)
    override fun onChange(listener: (Color) -> Unit) = holder.onChange(listener)
    override fun bind(state: BrassState<Color>) = holder.bind(this, state)

    private fun applyColor(c: Color) {
        val hsv = Color.RGBtoHSB(c.red, c.green, c.blue, null)
        hue = hsv[0]; sat = hsv[1]; brightness = hsv[2]
        hexInput.setTextSilently(hexOf(c))
    }

    private val hexInput: BrassTextInput

    init {
        val hsv = Color.RGBtoHSB(initial.red, initial.green, initial.blue, null)
        hue = hsv[0]
        sat = hsv[1]
        brightness = hsv[2]

        chrome = BrassChrome.NONE
        // Not `clickable`: it handles its own press below, and the keycap's press-sink/hover-lift are
        // both wrong for a panel - clicking the square should not make the whole picker dip down.

        // Elementa broadcasts drags tree-wide, so scrubbing is gated on a press that landed on the square
        // or the hue strip - clicks on the hex field (a child) fall through to Region.NONE.
        onMouseClick { e ->
            if (!active || e.mouseButton != 0) return@onMouseClick
            scrubbing = regionAt(e.relativeX, e.relativeY)
            if (scrubbing != Region.NONE) applyAt(e.relativeX, e.relativeY)
        }
        onMouseRelease { scrubbing = Region.NONE }
        onMouseDrag { mx, my, btn ->
            if (btn != 0 || scrubbing == Region.NONE) return@onMouseDrag
            applyAt(mx, my)
        }

        hexInput = BrassTextInput(hexOf(picked), "#RRGGBB") { onHexTyped(it) }
        hexInput.constrain {
            x = PAD.pixels(); y = PAD.pixels(true)
            width = 100.percent() - (PAD * 2).pixels()
            height = HEX_H.pixels()
        } childOf this

        // The constructor callback is listener zero; more can be added later through onChange.
        holder.onChange(onPick)
    }

    private enum class Region { NONE, SQUARE, HUE }

    private var scrubbing = Region.NONE

    val picked: Color get() = Color(Color.HSBtoRGB(hue, sat, brightness))

    fun set(c: Color) = setSilently(c)


    private fun contentR() = getWidth() - PAD
    private fun contentB() = getHeight() - PAD
    private fun hueBottomL() = (contentB() - HEX_H - PAD)
    private fun hueTopL() = (hueBottomL() - HUE_H)
    private fun squareBottomL() = (hueTopL() - PAD)
    private fun squareRightL() = (contentR() - SWATCH_W - PAD)

    private fun regionAt(localX: Float, localY: Float): Region = when {
        localY in hueTopL()..hueBottomL() && localX in PAD..contentR() -> Region.HUE
        localY in PAD..squareBottomL() && localX in PAD..squareRightL() -> Region.SQUARE
        else -> Region.NONE
    }

    private fun applyAt(localX: Float, localY: Float) {
        when (scrubbing) {
            Region.SQUARE -> {
                val sw = (squareRightL() - PAD).coerceAtLeast(1f)
                val sh = (squareBottomL() - PAD).coerceAtLeast(1f)
                sat = ((localX - PAD) / sw).coerceIn(0f, 1f)
                brightness = 1f - ((localY - PAD) / sh).coerceIn(0f, 1f)
            }
            Region.HUE -> {
                val hw = (contentR() - PAD).coerceAtLeast(1f)
                hue = ((localX - PAD) / hw).coerceIn(0f, 1f)
            }
            Region.NONE -> return
        }
        hexInput.setTextSilently(hexOf(picked))
        notifyPick()
    }


    private fun hexOf(c: Color): String = "#%02X%02X%02X".format(c.red, c.green, c.blue)

    private fun parseHex(s: String): Color? {
        val t = s.trim().removePrefix("#")
        if (t.length != 6) return null
        return runCatching { Color(t.toInt(16)) }.getOrNull()
    }

    private fun onHexTyped(s: String) {
        val c = parseHex(s) ?: return
        val hsv = Color.RGBtoHSB(c.red, c.green, c.blue, null)
        hue = hsv[0]; sat = hsv[1]; brightness = hsv[2]
        notifyPick()
    }

    private fun notifyPick() = holder.notifyNow(picked)


    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val cardL = x.toFloat(); val cardT = y.toFloat()
        val cardR = (x + w).toFloat(); val cardB = (y + h).toFloat()

        // the whole picker sits on one card
        BrassCard.draw(m, cardL, cardT, cardR, cardB, shadow = true)

        val cl = cardL + PAD; val cr = cardR - PAD; val ct = cardT + PAD; val cb = cardB - PAD
        val hueBottom = cb - HEX_H - PAD
        val hueTop = hueBottom - HUE_H
        val squareBottom = hueTop - PAD
        val squareRight = cr - SWATCH_W - PAD
        val pure = Color(Color.HSBtoRGB(hue, 1f, 1f))
        gradient(m, cl, ct, squareRight, squareBottom, Color.WHITE, pure, pure, Color.WHITE)
        gradient(m, cl, ct, squareRight, squareBottom, CLEAR, CLEAR, Color.BLACK, Color.BLACK)
        cardFrame(m, cl, ct, squareRight, squareBottom)

        val sx = cr - SWATCH_W
        fill(m, sx, ct, cr, squareBottom, picked)
        cardFrame(m, sx, ct, cr, squareBottom)

        // selection marker: the picked colour in the centre, ringed in black
        val selX = cl + sat * (squareRight - cl)
        val selY = ct + (1f - brightness) * (squareBottom - ct)
        gripMark(m, selX, selY)

        val stripW = cr - cl
        for (i in 0 until 6) {
            val x1 = cl + stripW * i / 6f
            val x2 = cl + stripW * (i + 1) / 6f
            gradient(m, x1, hueTop, x2, hueBottom, HUE_STOPS[i], HUE_STOPS[i + 1], HUE_STOPS[i + 1], HUE_STOPS[i])
        }
        cardFrame(m, cl, hueTop, cr, hueBottom)
        val hx = (cl + hue * stripW).coerceIn(cl + 2f, cr - 2f)
        gripBar(m, hx, hueTop - 1f, hueBottom + 1f)

        // the hex field draws itself, as a child component
    }

    // `enableBlend` / `beginWithDefaultShader` are deprecated in UniversalCraft's UGraphics, but the
    // non-deprecated path (`beginWithActiveShader`) needs a shader bound by hand and would drag Minecraft
    // render-pipeline imports across the seam that keeps `brassui` game-agnostic. The deprecated calls
    // still do exactly what a vertex-coloured quad needs, so the warning is suppressed rather than chased.
    @Suppress("DEPRECATION")
    private fun gradient(
        m: UMatrixStack,
        x1: Float, y1: Float, x2: Float, y2: Float,
        topLeft: Color, topRight: Color, bottomRight: Color, bottomLeft: Color,
    ) {
        if (x2 <= x1 || y2 <= y1) return
        BrassStats.quad()
        // The corner colours go straight into the vertex buffer, so they bypass every fill helper the
        // ambient fade hooks into - which is why the picker's two gradients and its hue strip stayed
        // fully painted while the rest of a closing window faded. Blending is already on for these
        // quads, so scaling the vertex alpha is a real fade rather than a darken.
        val tl = BrassAmbientFade.apply(topLeft)
        val tr = BrassAmbientFade.apply(topRight)
        val br = BrassAmbientFade.apply(bottomRight)
        val bl = BrassAmbientFade.apply(bottomLeft)
        UGraphics.enableBlend()
        val g = UGraphics.getFromTessellator()
        g.beginWithDefaultShader(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_COLOR)
        g.pos(m, x1.toDouble(), y2.toDouble(), 0.0).color(bl).endVertex()
        g.pos(m, x2.toDouble(), y2.toDouble(), 0.0).color(br).endVertex()
        g.pos(m, x2.toDouble(), y1.toDouble(), 0.0).color(tr).endVertex()
        g.pos(m, x1.toDouble(), y1.toDouble(), 0.0).color(tl).endVertex()
        g.drawDirect()
        UGraphics.disableBlend()
    }

    private fun cardFrame(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float) =
        BrassCard.flat(m, x1, y1, x2, y2)

    private fun gripMark(m: UMatrixStack, cx: Float, cy: Float) {
        fill(m, cx - MARKER_R - 1f, cy - MARKER_R - 1f, cx + MARKER_R + 1f, cy + MARKER_R + 1f, MARKER_OUTLINE)
        fill(m, cx - MARKER_R, cy - MARKER_R, cx + MARKER_R, cy + MARKER_R, picked)
    }

    private fun gripBar(m: UMatrixStack, cx: Float, y1: Float, y2: Float) {
        fill(m, cx - 3f, y1 - 1f, cx + 3f, y2 + 1f, Colors.UI_OUTER_BORDER)
        fill(m, cx - 2f, y1, cx + 2f, y2, GRIP)
        fill(m, cx - 2f, y1, cx + 2f, y1 + 1f, GRIP_EDGE)
    }

    private fun fill(m: UMatrixStack, x1: Float, y1: Float, x2: Float, y2: Float, c: Color) =
        BrassPaint.rect(m, x1, y1, x2, y2, c)

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("color-picker", "Colour picker", 160f, 130f) {
            BrassColorPicker(Colors.BRASS_500)
        }

        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val PAD = 6f
        private const val HUE_H = 10f
        private const val HEX_H = 16f
        private const val SWATCH_W = 20f
        private const val MARKER_R = 4f
        private val MARKER_OUTLINE: Color = Color.BLACK
        private val CLEAR: Color get() = Colors.NONE
        private val HUE_STOPS: Array<Color> = Array(7) { Color(Color.HSBtoRGB(it / 6f, 1f, 1f)) }
        private val GRIP: Color get() = Colors.GRIP
        private val GRIP_EDGE: Color get() = Colors.GRIP_EDGE
    }
}
