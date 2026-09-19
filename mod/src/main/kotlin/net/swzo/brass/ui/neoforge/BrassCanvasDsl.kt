package net.swzo.brass.ui.neoforge

// The native draw handle is MC's GUI graphics object. In MC 26.x GuiGraphics became the retained-mode
// GuiGraphicsExtractor, so the DSL's callback type follows via this alias (the handle a 26.x
// BrassNativeDraw receives is a GuiGraphicsExtractor once NeoForgePlatform.drawNative is implemented).
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor as GuiGraphics*/
//?} else {
import net.minecraft.client.gui.GuiGraphics
//?}
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.media.BrassCanvas
import net.swzo.brass.ui.kit.platform.BrassNativeDraw

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
    content = { handle, w, h -> draw(handle as GuiGraphics, w, h) },
)
