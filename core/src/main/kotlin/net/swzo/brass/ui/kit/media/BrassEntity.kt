package net.swzo.brass.ui.kit.media

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.platform.BrassPlatform
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A framed entity preview - the inventory's player model, for any living entity.
 * ```kotlin
 * BrassEntity("minecraft:allay", followCursor = true)
 *     .constrain { width = 60.pixels(); height = 80.pixels() }
 * ```
 * Lighting comes from the platform, which delegates to the same vanilla path the inventory uses;
 * hand-lit entities come out flat and grey, so this is one case where reusing the game's own
 * renderer matters more than owning the code.
 * By default the model sits at a fixed **isometric three-quarter** angle - 45 degrees of yaw and
 * `atan(1/sqrt(2))` (~35.26 degrees) of pitch, the true isometric viewing angle. That reads as a
 * deliberate presentation, the way a model viewer or a shop listing shows an item.
 * Cursor tracking is opt-in ([followCursor]), because a preview that swings around whenever the
 * pointer passes nearby is distracting in a list of them. [spin] gives a slow idle rotation instead.
 */
class BrassEntity(
    var entityId: String,
    var followCursor: Boolean = false,
    var spin: Float = 0f,
    tooltip: Boolean = true,
) : BrassPlatformVisual(BrassAccent.DEFAULT) {

    var yaw: Float = ISO_YAW
    var pitch: Float = ISO_PITCH


    init {
        placeholder = "no entity"
        // The entity sits on the same raised keycap card as an item slot, so a row of previews reads as
        // the same material as a row of items. The base paints the card; drawContent draws the model on
        // top of it, inset so the model stays inside the face.
        if (tooltip) {
            BrassTooltip.attachLazy(this, { BrassPlatform.current?.entityName(entityId) ?: entityId })
        }
    }

    override fun contentBox(x: Int, y: Int, w: Int, h: Int): FloatArray = floatArrayOf(
        (x + INSET).toFloat(),
        (y + INSET).toFloat(),
        (w - INSET * 2).coerceAtLeast(1).toFloat(),
        (h - INSET * 2).coerceAtLeast(1).toFloat(),
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
        aim(x, y, w, h)
        return platform.drawEntity(m, entityId, x, y, w, h, yaw, pitch, fade)
    }

    private fun aim(x: Float, y: Float, w: Float, h: Float) {
        if (followCursor) {
            val (mx, my) = getMousePosition()
            val cx = x + w / 2f
            val cy = y + h / 2f
            // shallow angles: a preview that swings a full 180 degrees with the cursor reads as
            // twitchy, so the pointer offset is damped rather than mapped one-to-one
            yaw = ((mx - cx) / w.coerceAtLeast(1f) * 60f).coerceIn(-45f, 45f)
            pitch = ((my - cy) / h.coerceAtLeast(1f) * 40f).coerceIn(-25f, 25f)
        } else if (spin != 0f) {
            yaw = (yaw + spin * BrassClock.dt) % 360f
        }
    }

    companion object : BrassDemoSource {


        override fun demo() = BrassDemo("entity", "Entity", 56f, 56f, worldRequired = true) {
            BrassEntity("minecraft:allay", spin = 45f)
        }

        const val INSET = 2

        const val ISO_YAW = 45f

        const val ISO_PITCH = 35.264f
    }
}
