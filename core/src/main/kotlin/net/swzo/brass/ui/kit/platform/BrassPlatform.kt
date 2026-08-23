package net.swzo.brass.ui.kit.platform

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.surface.BrassTooltip
import java.awt.Color

/**
 * The seam between `brassui` and Minecraft.
 * The toolkit is deliberately free of Minecraft imports: it draws through Elementa and UniversalCraft
 * and knows nothing about items, sounds or the game window. A few things genuinely need the game
 * though - setting the mouse cursor, drawing an item stack - so those are declared here as an
 * interface and **implemented by the platform module** (`platform-neoforge` today, a Fabric module
 * later) which binds itself at startup:
 * ```kotlin
 * BrassPlatform.bind(NeoForgePlatform)
 * ```
 * That keeps the toolkit loader- and version-independent: adding a new loader means writing one
 * implementation of this interface, not touching a widget. Every call site treats an unbound platform
 * as "feature unavailable" rather than an error, so the UI still works standalone - cursors stay as
 * the arrow and item slots draw empty.
 */
interface BrassPlatform {

    fun setCursor(kind: BrassCursor.Kind)

    fun drawItem(
        matrixStack: UMatrixStack,
        itemId: String,
        x: Float,
        y: Float,
        size: Float,
        count: Int = 1,
        alpha: Float = 1f,
    ): Boolean

    fun itemName(itemId: String): String?

    fun itemTooltip(itemId: String): List<Pair<String, Color>>? = null

    fun maxStackSize(itemId: String): Int = 64

    fun drawEntity(
        matrixStack: UMatrixStack,
        entityId: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        yaw: Float,
        pitch: Float,
        alpha: Float = 1f,
    ): Boolean

    fun entityName(entityId: String): String?

    fun drawPlayerFace(
        matrixStack: UMatrixStack,
        player: String,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float = 1f,
    ): Boolean = false

    fun drawBlockModel(
        matrixStack: UMatrixStack,
        blockId: String,
        x: Float,
        y: Float,
        size: Float,
        yaw: Float,
        pitch: Float,
        alpha: Float = 1f,
    ): Boolean = false

    fun blockName(blockId: String): String? = null

    fun drawEffectIcon(
        matrixStack: UMatrixStack,
        effectId: String,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float = 1f,
    ): Boolean = false

    fun effectName(effectId: String): String? = null

    fun drawNative(
        matrixStack: UMatrixStack,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        alpha: Float = 1f,
        clip: Boolean = true,
        depth: Boolean = true,
        draw: BrassNativeDraw,
    ): Boolean = false

    /**
     * Flush any text the game has queued but not yet drawn.
     * Minecraft batches glyphs into a buffer source and renders them when the batch ends, not at the
     * call site - so a label "drawn" early in a frame can land on the screen *after* quads drawn much
     * later, and its drop shadow ends up over them. Anything that must sit above all prior text calls
     * this first (see [BrassTooltip]). A platform with no such batching can leave it a no-op.
     */
    fun flushText() {}

    /**
     * The GUI scale factor (device pixels per GUI pixel), so content that must render at device
     * resolution - an embedded web view - can size itself. 1.0 off-game and on unscaled windows.
     */
    fun guiScale(): Float = 1f

    companion object {
        var current: BrassPlatform? = null
            private set

        fun bind(platform: BrassPlatform) {
            current = platform
            // a new platform may map cursors differently; force the next apply to re-send
            BrassCursor.forget()
        }

        val available: Boolean get() = current != null
    }
}
