package net.swzo.brass.ui.kit.platform

/**
 * A callback that paints with the game's own renderer, for [BrassPlatform.drawNative].
 * ### Why handle is untyped
 * The thing being handed over is Minecraft's `GuiGraphics`, and the whole design of [BrassPlatform] is
 * that the toolkit never names a Minecraft type - that is what lets `brassui` compile and run without
 * the game, and what lets a second loader be added by writing one class instead of touching every
 * widget. So the seam passes it as `Any` and the platform module casts it back.
 * Callers do **not** write that cast by hand. The platform module ships a typed builder that does it
 * once, so the modder-facing call reads:
 * ```kotlin
 * brassCanvas { g, w, h -> g.blit(TEXTURE, 0, 0, 0f, 0f, w.toInt(), h.toInt(), 64, 64) }
 * ```
 * The cast is safe by construction: only the platform that produces the handle can consume it, and
 * a bad one is caught by the `runCatching` around every native draw rather than taking the screen
 * down.
 */
fun interface BrassNativeDraw {

    fun draw(handle: Any, width: Float, height: Float)
}
