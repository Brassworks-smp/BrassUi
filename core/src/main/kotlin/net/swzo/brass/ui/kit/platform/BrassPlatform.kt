package net.swzo.brass.ui.kit.platform

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.surface.BrassTooltip

/**
 * The seam between `brassui` and Minecraft.
 *
 * The toolkit is deliberately free of Minecraft imports: it draws through Elementa and UniversalCraft
 * and knows nothing about items, sounds or the game window. A few things genuinely need the game
 * though - setting the mouse cursor, drawing an item stack - so those are declared here as an
 * interface and **implemented by the platform module** (`platform-neoforge` today, a Fabric module
 * later) which binds itself at startup:
 *
 * ```kotlin
 * BrassPlatform.bind(NeoForgePlatform)
 * ```
 *
 * That keeps the toolkit loader- and version-independent: adding a new loader means writing one
 * implementation of this interface, not touching a widget. Every call site treats an unbound platform
 * as "feature unavailable" rather than an error, so the UI still works standalone - cursors stay as
 * the arrow and item slots draw empty.
 */
interface BrassPlatform {

    /** Set the hardware cursor. Called at most once per frame, and only when the shape changes. */
    fun setCursor(kind: BrassCursor.Kind)

    /**
     * Draw the item identified by [itemId] (e.g. `minecraft:diamond_pickaxe`) in a [size]x[size] box
     * at ([x],[y]) in **UI space** - the same coordinates every other widget draws in, already scaled
     * by the GUI scale. Implementations are responsible for bridging to whatever the game's item
     * renderer expects.
     *
     * Returns false if the id is unknown or the item could not be drawn, so the widget can fall back
     * to its placeholder instead of leaving a hole.
     */
    fun drawItem(
        matrixStack: UMatrixStack,
        itemId: String,
        x: Float,
        y: Float,
        size: Float,
        /**
         * Stack size. Drawn by the *platform* rather than the widget: vanilla renders the count with
         * a depth offset above the item model, and text drawn by the toolkit afterwards lands behind
         * the item instead of on top of it.
         */
        count: Int = 1,
        /**
         * Opacity 0..1. Items are drawn by the game's own renderer, which knows nothing about the
         * toolkit's colours, so a fading frame has to hand its alpha down explicitly - otherwise an
         * item slot stays solid while the window around it fades and then blinks out.
         */
        alpha: Float = 1f,
    ): Boolean

    /** A human-readable name for [itemId], for tooltips and labels; null if unknown. */
    fun itemName(itemId: String): String?

    /**
     * How many of [itemId] fit in one stack.
     *
     * Needed by [net.swzo.brass.ui.kit.input.BrassInventoryGrid] to split, merge and distribute
     * correctly - an ender pearl stacks to 16 and a sword to 1, and a grid that assumed 64 would
     * cheerfully build stacks the game cannot hold. Defaults to 64 with no platform bound, which is
     * right for the standalone build where nothing is real anyway.
     */
    fun maxStackSize(itemId: String): Int = 64

    /**
     * Draw the entity identified by [entityId] (e.g. `minecraft:allay`) inside a [width]x[height] box
     * at ([x],[y]), scaled to fit and lit the way the inventory lights the player model.
     *
     * [yaw] and [pitch] aim the model, so a caller can make it track the cursor. Returns false when
     * the id is unknown or the entity cannot be created.
     */
    fun drawEntity(
        matrixStack: UMatrixStack,
        entityId: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        yaw: Float,
        pitch: Float,
        /** Opacity 0..1 - see [drawItem]. */
        alpha: Float = 1f,
    ): Boolean

    /** A human-readable name for [entityId]; null if unknown. */
    fun entityName(entityId: String): String?

    /**
     * Draw the face of [player]'s skin - a name or a UUID - in a [size]x[size] box at ([x],[y]).
     *
     * The *face*, not the whole skin: the 8x8 head front plus the hat overlay on top of it, which is
     * the avatar every player list in the game uses. Skins arrive asynchronously, so an implementation
     * returns false until one is available and the widget shows its placeholder in the meantime rather
     * than blocking a frame on the network.
     */
    fun drawPlayerFace(
        matrixStack: UMatrixStack,
        player: String,
        x: Float,
        y: Float,
        size: Float,
        /** Opacity 0..1 - see [drawItem]. */
        alpha: Float = 1f,
    ): Boolean = false

    /**
     * Draw the block [blockId] as a 3D model inside a [size]x[size] box, aimed by [yaw] and [pitch].
     *
     * Distinct from `drawItem` on the block's item form: that renders the flat GUI sprite the
     * inventory shows, which for many blocks is not the block at all (a door, a bed, anything with no
     * item model). This renders the block state itself.
     */
    fun drawBlockModel(
        matrixStack: UMatrixStack,
        blockId: String,
        x: Float,
        y: Float,
        size: Float,
        yaw: Float,
        pitch: Float,
        /** Opacity 0..1 - see [drawItem]. */
        alpha: Float = 1f,
    ): Boolean = false

    /** A human-readable name for [blockId]; null if unknown. */
    fun blockName(blockId: String): String? = null

    /**
     * Draw the sprite for status effect [effectId] (e.g. `minecraft:speed`) in a [size]x[size] box.
     *
     * Only the icon - the duration ring around it is the toolkit's own drawing, since it is chrome
     * rather than game content.
     */
    fun drawEffectIcon(
        matrixStack: UMatrixStack,
        effectId: String,
        x: Float,
        y: Float,
        size: Float,
        /** Opacity 0..1 - see [drawItem]. */
        alpha: Float = 1f,
    ): Boolean = false

    /** A human-readable name for [effectId]; null if unknown. */
    fun effectName(effectId: String): String? = null

    /**
     * Run [draw] against the game's own renderer, inside the [width] x [height] box at ([x],[y]) in
     * UI space - the generic form of [drawItem] and [drawEntity], for content this toolkit knows
     * nothing about (see [net.swzo.brass.ui.kit.media.BrassCanvas]).
     *
     * The implementation owns everything that makes vanilla drawing survive an Elementa frame:
     * adopting [matrixStack] so the draw lands at the widget rather than the screen origin,
     * translating to the box's top-left so [draw] can work in local coordinates, intersecting the
     * scissor with any clip already in force when [clip] is set, applying [alpha] the way [drawItem]
     * describes, and - when [depth] is set - enabling the depth test and clearing the depth buffer
     * afterwards so later flat UI is not silently discarded where it overlaps.
     *
     * Returns false when there is nothing to draw with, so the widget can show its placeholder. An
     * exception thrown by [draw] is the caller's bug, not a reason to lose the screen: implementations
     * catch it, restore GL state, and return false.
     */
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
     *
     * Minecraft batches glyphs into a buffer source and renders them when the batch ends, not at the
     * call site - so a label "drawn" early in a frame can land on the screen *after* quads drawn much
     * later, and its drop shadow ends up over them. Anything that must sit above all prior text calls
     * this first (see [BrassTooltip]). A platform with no such batching can leave it a no-op.
     */
    fun flushText() {}

    companion object {
        /** The bound platform, or null when running without one. */
        var current: BrassPlatform? = null
            private set

        /** Bind [platform]. Call once, during client setup. */
        fun bind(platform: BrassPlatform) {
            current = platform
            // a new platform may map cursors differently; force the next apply to re-send
            BrassCursor.forget()
        }

        /** Whether Minecraft-backed features are available. */
        val available: Boolean get() = current != null
    }
}
