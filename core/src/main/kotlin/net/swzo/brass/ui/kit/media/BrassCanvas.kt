package net.swzo.brass.ui.kit.media

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.platform.BrassNativeDraw
import net.swzo.brass.ui.kit.platform.BrassPlatform

/**
 * An escape hatch onto the game's own renderer: a widget-shaped rectangle you can draw *anything*
 * into with `GuiGraphics`.
 * [BrassItem] and [BrassEntity] each wrap one specific vanilla draw call. This wraps the *plumbing*
 * they share and hands the brush to the caller, which is what an addon needs when it wants a recipe
 * grid, a block model, a map, a skin, a particle preview - anything the toolkit does not and should
 * not know about.
 * ### What the plumbing is
 * Drawing vanilla content inside an Elementa UI is not "call GuiGraphics and go". Four things have to
 * happen or the result lands in the wrong place, escapes its container, or eats the rest of the frame:
 * - Elementa's model matrix must be multiplied into the vanilla `PoseStack`, or the draw ignores the
 *   widget's position, the scroll offset and any window translation, and paints at the screen origin.
 * - The scissor has to be *intersected* with whatever clip is already in force - `GuiGraphics`' own
 *   scissor stack assumes it is the only thing touching `GL_SCISSOR_TEST` and will happily destroy
 *   the clip Elementa set for the surrounding scroll view.
 * - Fading has to darken rather than dissolve, because vanilla's solid/cutout render types configure
 *   blending themselves and throw our alpha away (see the platform's `applyFade`).
 * - Depth has to be cleared afterwards for 3D content, or later flat UI fails the depth test where it
 *   overlaps and simply vanishes.
 * All four are the platform's problem, and it already solves them for items and entities. This widget
 * just routes an arbitrary callback through the same path.
 * ### Card, or no card
 * The rectangle is a normal [BrassWidget], so it obeys the usual constraints, hover, entrance and
 * accent machinery, and [chrome] decides whether it sits on one of the toolkit's raised keycap cards
 * or is invisible framing around free-floating content:
 * ```kotlin
 * // on a card, clipped to the card's face
 * BrassCanvas { g, w, h -> renderMyThing(g, w, h) }
 *     .constrain { width = 64.pixels(); height = 64.pixels() }
 * // bare rectangle, no chrome at all
 * BrassCanvas(chrome = BrassChrome.NONE) { g, w, h -> renderMyThing(g, w, h) }
 *     .constrain { width = 100.percent(); height = 120.pixels() }
 * ```
 * The callback's coordinate space is **local**: `(0, 0)` is the top-left of the drawing area and
 * (width, height) - the values handed to the callback - are its size, already reduced by [inset]
 * and by the card's own border when there is a card. Draw in that space and the same code works
 * whether it is framed or not, which is the point.
 * Content is clipped to that area by default ([clip]). Turning it off is for content that
 * deliberately overhangs, and means the caller is responsible for not painting over its neighbours.
 * With no platform bound - the standalone desktop build - the widget draws its placeholder instead of
 * silently leaving a hole, exactly like an item slot with an unknown id.
 */
class BrassCanvas(
    chrome: BrassChrome = BrassChrome.KEYCAP,
    var inset: Float = 2f,
    var clip: Boolean = true,
    var depth: Boolean = true,
    accent: BrassAccent = BrassAccent.DEFAULT,
    var content: BrassNativeDraw? = null,
) : BrassPlatformVisual(accent) {

    init {
        this.chrome = chrome
        placeholder = "no renderer"
    }

    fun draws(content: BrassNativeDraw) = apply { this.content = content }

    override fun contentBox(x: Int, y: Int, w: Int, h: Int): FloatArray = floatArrayOf(
        x + inset,
        y + inset,
        (w - inset * 2f).coerceAtLeast(0f),
        (h - inset * 2f).coerceAtLeast(0f),
    )

    override fun paintNative(
        m: UMatrixStack,
        platform: BrassPlatform,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        fade: Float,
    ): Boolean {
        val body = content ?: return false
        return platform.drawNative(m, x, y, w, h, fade, clip, depth, body)
    }
}
