package net.swzo.brass.ui.kit.html.internal

import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.html.BrassHtmlView
import net.swzo.brass.ui.kit.html.HtmlSurfaceRenderer
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12

/**
 * The default [HtmlSurfaceRenderer], and the reason one implementation serves both hosts: it draws
 * through UniversalCraft's [UGraphics] (the same path Elementa's [gg.essential.elementa.components.UIImage]
 * uses), which exists in-game and in the standalone desktop app, and uploads pixels through raw
 * LWJGL, which both provide too.
 *
 * Each view owns one GL texture; only the dirty region re-uploads per frame, so an idle page costs
 * nothing.
 */
internal class UltralightSurfaceRenderer : HtmlSurfaceRenderer {

    private class Texture(val id: Int) {
        var width = 0
        var height = 0
    }

    private val textures = HashMap<BrassHtmlView, Texture>()

    override fun draw(view: BrassHtmlView, matrixStack: UMatrixStack, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
        if (w <= 0f || h <= 0f || alpha <= 0.004f) return
        val tex = textureFor(view) ?: return
        val frame = view.lockSurface()
        if (frame != null) {
            upload(tex, frame)
            view.unlockSurface()
        }

        if (alpha >= 0.02f) drawQuad(tex, view, matrixStack, x, y, w, h, alpha)
    }

    private fun textureFor(view: BrassHtmlView): Texture? {
        if (view.surfaceWidth <= 0 || view.surfaceHeight <= 0) return null
        return textures.getOrPut(view) {
            val id = GL11.glGenTextures()
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
            Texture(id)
        }
    }

    private fun upload(tex: Texture, frame: net.swzo.brass.ui.kit.html.HtmlSurfaceFrame) {
        val width = frame.width
        val height = frame.height
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex.id)
        // Ultralight's bitmap is BGRA with the row stride possibly wider than width*4.
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, frame.rowBytes / 4)

        val full = frame.dirtyWidth >= width && frame.dirtyHeight >= height
        if (full || tex.width != width || tex.height != height) {
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, frame.buffer,
            )
            tex.width = width
            tex.height = height
        } else {
            val startOffset = frame.y * frame.rowBytes + frame.x * 4
            GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D, 0,
                frame.x, frame.y, frame.dirtyWidth, frame.dirtyHeight,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                frame.buffer.position(startOffset),
            )
        }
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
    }

    private fun drawQuad(
        tex: Texture,
        view: BrassHtmlView,
        m: UMatrixStack,
        x: Float, y: Float, w: Float, h: Float,
        alpha: Float,
    ) {
        // The texture holds the view at its device-pixel size; the quad is widget-sized, so the UVs
        // are (viewSize / quadSize) — identical to how the original mapped a full-screen view.
        val u = view.surfaceWidth.toDouble() / w
        val v = view.surfaceHeight.toDouble() / h
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()

        UGraphics.enableBlend()
        UGraphics.enableAlpha()
        UGraphics.bindTexture(tex.id, 0)

        val g = UGraphics.getFromTessellator()
        g.beginWithDefaultShader(UGraphics.DrawMode.QUADS, UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR)
        g.pos(m, x.toDouble(), y.toDouble(), 0.0).tex(0.0, 0.0).color(255, 255, 255, a).endVertex()
        g.pos(m, (x + w).toDouble(), y.toDouble(), 0.0).tex(u, 0.0).color(255, 255, 255, a).endVertex()
        g.pos(m, (x + w).toDouble(), (y + h).toDouble(), 0.0).tex(u, v).color(255, 255, 255, a).endVertex()
        g.pos(m, x.toDouble(), (y + h).toDouble(), 0.0).tex(0.0, v).color(255, 255, 255, a).endVertex()
        g.drawDirect()

        UGraphics.disableBlend()
    }
}
