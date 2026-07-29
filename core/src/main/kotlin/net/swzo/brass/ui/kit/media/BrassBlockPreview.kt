package net.swzo.brass.ui.kit.media

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.platform.BrassPlatform
import net.swzo.brass.ui.kit.surface.BrassTooltip
import kotlin.math.min
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A block rendered as an actual 3D model, at the isometric three-quarter angle [BrassEntity] uses.
 *
 * ```kotlin
 * BrassBlockPreview("minecraft:blast_furnace", spin = 30f)
 *     .constrain { width = 48.pixels(); height = 48.pixels() }
 * ```
 *
 * ### Why not just `BrassItem`
 *
 * `BrassItem("minecraft:oak_door")` draws the *item* sprite - a flat picture of a door, not a door.
 * Plenty of blocks have an item form that looks nothing like the block (doors, beds, cauldrons,
 * banners) and some have none at all. This renders the block state itself, which is what a block
 * picker or a world-editor palette actually needs to show.
 *
 * The angle is fixed by default for the same reason [BrassEntity]'s is: a grid of previews that all
 * swing toward the cursor is distracting. [spin] gives a slow idle rotation instead, and
 * [followCursor] the interactive behaviour when there is only one of them.
 */
class BrassBlockPreview(
    var blockId: String,
    /** Turn to face the cursor. Off by default - a grid of these should sit still. */
    var followCursor: Boolean = false,
    /** Degrees per second of idle rotation, when not following the cursor. */
    var spin: Float = 0f,
    tooltip: Boolean = true,
) : BrassPlatformVisual(BrassAccent.DEFAULT) {

    /** Facing, in degrees. Driven by the cursor or [spin] when either is enabled. */
    var yaw: Float = BrassEntity.ISO_YAW
    var pitch: Float = BrassEntity.ISO_PITCH

    init {
        placeholder = "no block"
        if (tooltip) {
            BrassTooltip.attachLazy(this, { BrassPlatform.current?.blockName(blockId) ?: blockId })
        }
    }

    /** Square, centred, inset from the card's inner border. */
    override fun contentBox(x: Int, y: Int, w: Int, h: Int): FloatArray {
        val size = (min(w, h) - INSET * 2).coerceAtLeast(1).toFloat()
        return floatArrayOf(x + (w - size) / 2f, y + (h - size) / 2f, size, size)
    }

    override fun paintNative(
        m: UMatrixStack,
        platform: BrassPlatform,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        fade: Float,
    ): Boolean {
        if (followCursor) {
            val (mx, my) = getMousePosition()
            // Damped rather than mapped one-to-one, for the same reason BrassEntity damps it: a
            // preview that swings 180 degrees as the pointer passes reads as twitchy.
            yaw = BrassEntity.ISO_YAW + ((mx - (x + w / 2f)) / w * 60f).coerceIn(-45f, 45f)
            pitch = BrassEntity.ISO_PITCH + ((my - (y + h / 2f)) / h * 30f).coerceIn(-20f, 20f)
        } else if (spin != 0f) {
            yaw = (yaw + spin * BrassClock.dt) % 360f
        }
        return platform.drawBlockModel(m, blockId, x, y, w, yaw, pitch, fade)
    }

    companion object : BrassDemoSource {

        /** A block turning on its axis. World-required for the same reason the entity demo is. */
        override fun demo() = BrassDemo("block-preview", "Block preview", 52f, 52f, worldRequired = true) {
            BrassBlockPreview("minecraft:blast_furnace", spin = 40f)
        }

        // ---- widget internals ------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that
        // it carries the demo. Same visibility as before for everything below.

        private const val INSET = 2
    }
}
