package net.swzo.brass.ui.neoforge

import net.minecraft.client.gui.GuiGraphics
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.media.BrassCanvas
import net.swzo.brass.ui.kit.platform.BrassNativeDraw

/**
 * The typed way to build a [BrassCanvas] — `GuiGraphics` instead of the seam's `Any`.
 *
 * [BrassCanvas] lives in the toolkit core, which has no Minecraft on its classpath, so its callback
 * receives an untyped handle. That is the right trade for the toolkit and the wrong one for a modder,
 * who would otherwise open every canvas with the same cast. This module *does* have Minecraft, so it
 * performs the cast once, here.
 *
 * ```kotlin
 * brassCanvas(chrome = BrassChrome.NONE) { g, w, h ->
 *     g.blit(MY_TEXTURE, 0, 0, 0f, 0f, w.toInt(), h.toInt(), 64, 64)
 * }.constrain { width = 100.percent(); height = 80.pixels() }
 * ```
 *
 * `(0, 0)` is the top-left of the drawing area and ([w], [h]) its size — the pose is already
 * translated, so the same block works wherever the widget lands. See [BrassCanvas] for what the
 * arguments do and for the clipping and depth rules.
 */
fun brassCanvas(
    chrome: BrassChrome = BrassChrome.KEYCAP,
    inset: Float = 2f,
    clip: Boolean = true,
    depth: Boolean = true,
    accent: BrassAccent = BrassAccent.DEFAULT,
    draw: (g: GuiGraphics, w: Float, h: Float) -> Unit,
): BrassCanvas = BrassCanvas(
    chrome = chrome,
    inset = inset,
    clip = clip,
    depth = depth,
    accent = accent,
    content = BrassNativeDraw { handle, w, h -> draw(handle as GuiGraphics, w, h) },
)
