package net.swzo.brass.ui.neoforge

import com.mojang.blaze3d.systems.RenderSystem
import gg.essential.universal.UMatrixStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Holder
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.level.block.Block
import java.util.UUID
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.swzo.brass.ui.kit.platform.BrassCursor
import net.swzo.brass.ui.kit.platform.BrassNativeDraw
import net.swzo.brass.ui.kit.platform.BrassPlatform
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The NeoForge implementation of [BrassPlatform] — everything `brassui` needs from Minecraft, kept on
 * this side of the seam so the toolkit itself stays free of game imports and can be reused under
 * another loader by writing another one of these.
 * Bound once during client setup by [BrassUiClientCommands].
 */
object NeoForgePlatform : BrassPlatform {


    private val cursors = HashMap<BrassCursor.Kind, Long>()

    private fun handleFor(kind: BrassCursor.Kind): Long = cursors.getOrPut(kind) {
        val shape = when (kind) {
            BrassCursor.Kind.ARROW -> GLFW.GLFW_ARROW_CURSOR
            BrassCursor.Kind.TEXT -> GLFW.GLFW_IBEAM_CURSOR
            BrassCursor.Kind.HAND -> GLFW.GLFW_POINTING_HAND_CURSOR
            // GLFW has no dedicated move cursor; the four-arrow resize-all is the standard move icon.
            BrassCursor.Kind.MOVE -> GLFW.GLFW_RESIZE_ALL_CURSOR
            BrassCursor.Kind.CROSSHAIR -> GLFW.GLFW_CROSSHAIR_CURSOR
            BrassCursor.Kind.RESIZE_H -> GLFW.GLFW_RESIZE_EW_CURSOR
            BrassCursor.Kind.RESIZE_V -> GLFW.GLFW_RESIZE_NS_CURSOR
            BrassCursor.Kind.RESIZE_NWSE -> GLFW.GLFW_RESIZE_NWSE_CURSOR
            BrassCursor.Kind.RESIZE_NESW -> GLFW.GLFW_RESIZE_NESW_CURSOR
        }
        // Not every platform provides the diagonal shapes (they are unsupported on Wayland and some
        // X11 themes); GLFW returns NULL there, which glfwSetCursor treats as "use the default".
        GLFW.glfwCreateStandardCursor(shape)
    }

    override fun setCursor(kind: BrassCursor.Kind) {
        val window = Minecraft.getInstance().window ?: return
        GLFW.glfwSetCursor(window.window, handleFor(kind))
    }


    private val stacks = HashMap<String, ItemStack>()

    private fun stackFor(itemId: String): ItemStack = stacks.getOrPut(itemId) {
        val location = ResourceLocation.tryParse(itemId) ?: return@getOrPut ItemStack.EMPTY
        val item = BuiltInRegistries.ITEM.getOptional(location).orElse(null) ?: return@getOrPut ItemStack.EMPTY
        ItemStack(item)
    }

    override fun itemName(itemId: String): String? {
        val stack = stackFor(itemId)
        return if (stack.isEmpty) null else stack.hoverName.string
    }

    override fun itemTooltip(itemId: String): List<Pair<String, Color>>? {
        val stack = stackFor(itemId)
        if (stack.isEmpty) return null
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return listOf(stack.hoverName.string to Color(0xAAAAAA))
        val context = Item.TooltipContext.of(level)
        val flag = TooltipFlag.Default(mc.options.advancedItemTooltips, mc.player?.isCreative == true)
        return stack.getTooltipLines(context, mc.player, flag).map { line ->
            val value = line.style.color?.value
            line.getString() to (if (value != null) Color(value, true) else Color(0xAAAAAA))
        }
    }

    override fun maxStackSize(itemId: String): Int {
        val stack = stackFor(itemId)
        return if (stack.isEmpty) 64 else stack.maxStackSize
    }


    /**
     * Entities are expensive to construct and must not be rebuilt per frame, so each id is created
     * once against the client level and reused. A null value marks an id we already failed on.
     */
    private val entities = HashMap<String, LivingEntity?>()

    private fun entityFor(entityId: String): LivingEntity? = entities.getOrPut(entityId) {
        val level = Minecraft.getInstance().level ?: return@getOrPut null
        val location = ResourceLocation.tryParse(entityId) ?: return@getOrPut null
        val type = BuiltInRegistries.ENTITY_TYPE.getOptional(location).orElse(null) ?: return@getOrPut null
        runCatching { type.create(level) as? LivingEntity }.getOrNull()
    }

    override fun entityName(entityId: String): String? {
        val location = ResourceLocation.tryParse(entityId) ?: return null
        return BuiltInRegistries.ENTITY_TYPE.getOptional(location).orElse(null)?.description?.string
    }

    override fun flushText() {
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch()
    }

    override fun guiScale(): Float = Minecraft.getInstance().window.guiScale.toFloat()

    override fun drawEntity(
        matrixStack: UMatrixStack,
        entityId: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        yaw: Float,
        pitch: Float,
        alpha: Float,
    ): Boolean {
        if (alpha <= 0.02f) return true
        val entity = entityFor(entityId) ?: return false
        val mc = Minecraft.getInstance()
        val graphics = GuiGraphics(mc, mc.renderBuffers().bufferSource())

        val pose = graphics.pose()
        pose.pushPose()
        pose.mulPose(matrixStack.peek().model)

        // `renderEntityInInventory` draws the entity at its *own* body/head rotation and only uses the
        // pose quaternion for the upright flip and the downward tilt — the yaw is not a parameter. The
        // previous code passed yaw as the camera orientation, which only steers the light, so every mob
        // faced dead-on rather than three-quarter: no isometric turn at all. The fix is to set the
        // entity's rotation fields, exactly as vanilla's inventory preview does, and restore them after
        // (the LivingEntity instance is cached and shared between previews).
        val savedBodyRot = entity.yBodyRot
        val savedYRot = entity.yRot
        val savedXRot = entity.xRot
        val savedHeadRot = entity.yHeadRot
        val savedHeadRotO = entity.yHeadRotO

        return try {
            // Fit the *whole* model, centred, whatever way the isometric turn happens to spin it. The
            // safe bound on a box rotated to any angle is its bounding *sphere*: with depth ≈ width, the
            // diameter is sqrt(w² + h² + w²). Scaling that diameter to the shorter side of the slot (with
            // a margin) guarantees a tall allay and a wide cow both fit without clipping — then the model
            // is centred vertically rather than stood on the floor of the slot.
            val bbHeight = entity.bbHeight.coerceAtLeast(0.1f)
            val bbWidth = entity.bbWidth.coerceAtLeast(0.1f)
            val diameter = sqrt(bbWidth * bbWidth + bbHeight * bbHeight + bbWidth * bbWidth)
            val scale = (minOf(width, height) / diameter) * 0.9f
            val cx = x + width / 2f
            val cy = y + height / 2f + bbHeight * scale / 2f

            entity.yBodyRot = 180f + yaw
            entity.setYRot(180f + yaw)
            entity.setXRot(0f)
            entity.yHeadRot = 180f + yaw
            entity.yHeadRotO = 180f + yaw

            val pitchRad = pitch * Math.PI.toFloat() / 180f
            // upright flip, then tilt the camera down onto the model so its top face shows (isometric)
            val bodyPose = Quaternionf().rotateZ(Math.PI.toFloat()).rotateX(-pitchRad)
            val tilt = Quaternionf().rotateX(-pitchRad)

            // Clip the model to the slot it was given. The bounding-sphere fit above keeps a *typical*
            // mob inside the box, but it is a fit, not a guarantee: a model with parts outside its
            // collision box (a horse's head, an allay's wings) still spills over the card's edge and
            // paints across whatever is beside it. A scissor is the only hard bound, since the model is
            // drawn by the game's renderer and knows nothing about our layout.
            // Done by hand against raw GL rather than through GuiGraphics.enableScissor/disableScissor.
            // That pair is stack-based, and this GuiGraphics is created fresh per call, so its stack is
            // empty — `disableScissor` therefore did not restore the caller's clip, it turned
            // GL_SCISSOR_TEST off outright and destroyed the scissor Elementa had set for the
            // surrounding ScrollComponent. Everything drawn after an entity escaped its scroll view.
            pushScissor(x, y, width, height)
            applyFade(alpha)
            try {
                InventoryScreen.renderEntityInInventory(
                    graphics,
                    cx, cy,
                    scale,
                    Vector3f(0f, 0f, 0f),
                    bodyPose,
                    tilt,
                    entity,
                )
                graphics.flush()
            } finally {
                clearFade(alpha)
                popScissor()
            }
            // The entity is drawn as a 3D model at GUI depth ~50 with depth-testing on; without clearing
            // the depth buffer afterwards, any later flat UI (a tooltip, a label above the slot) fails the
            // depth test where it overlaps the model and vanishes. Clearing depth lets the rest of the 2D
            // UI paint over the model normally.
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX)
            true
        } catch (_: Throwable) {
            false
        } finally {
            entity.yBodyRot = savedBodyRot
            entity.setYRot(savedYRot)
            entity.setXRot(savedXRot)
            entity.yHeadRot = savedHeadRot
            entity.yHeadRotO = savedHeadRotO
            pose.popPose()
        }
    }

    override fun drawItem(
        matrixStack: UMatrixStack,
        itemId: String,
        x: Float,
        y: Float,
        size: Float,
        count: Int,
        alpha: Float,
    ): Boolean {
        if (alpha <= 0.02f) return true
        val stack = stackFor(itemId)
        if (stack.isEmpty) return false

        val mc = Minecraft.getInstance()
        val graphics = GuiGraphics(mc, mc.renderBuffers().bufferSource())

        val pose = graphics.pose()
        pose.pushPose()
        // Elementa draws through its own matrix stack, so the vanilla PoseStack knows nothing about
        // the widget's position, any scroll offset, or a window's translation. Multiplying Elementa's
        // current model matrix in first adopts all of that; without it the item renders at the screen
        // origin regardless of where its slot is.
        pose.mulPose(matrixStack.peek().model)
        pose.translate(x.toDouble(), y.toDouble(), 0.0)
        val scale = size / 16f
        pose.scale(scale, scale, 1f)

        return try {
            RenderSystem.enableDepthTest()
            applyFade(alpha)
            graphics.renderItem(stack, 0, 0)
            // Decorations (stack count, durability bar) go through vanilla so they land at the depth
            // offset *above* the model. Drawing the count with our own font afterwards puts it behind
            // the item, which is what it did before.
            if (count > 1) {
                graphics.renderItemDecorations(mc.font, stack.copyWithCount(count), 0, 0)
            }
            graphics.flush()
            // Clear depth, exactly as drawEntity and drawNative do. An item is drawn as a *model* with
            // depth testing on at GUI depth ~50, and anything flat drawn afterwards that overlaps it
            // fails the depth test and is silently dropped — which is why a tooltip passing over a row
            // of item slots lost the text where it crossed them. drawItem was the one path of the
            // three that never cleared.
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX)
            true
        } catch (_: Throwable) {
            // A broken item model must not take the screen down with it.
            false
        } finally {
            clearFade(alpha)
            pose.popPose()
        }
    }


    override fun drawPlayerFace(
        matrixStack: UMatrixStack,
        player: String,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float,
    ): Boolean {
        val skin = skinFor(player) ?: return false
        return drawNative(matrixStack, x, y, size, size, alpha, clip = true, depth = false) { handle, w, _ ->
            val g = handle as GuiGraphics
            val px = w.toInt().coerceAtLeast(1)
            g.blit(skin, 0, 0, px, px, 8f, 8f, 8, 8, 64, 64)
            g.blit(skin, 0, 0, px, px, 40f, 8f, 8, 8, 64, 64)
        }
    }

    private fun skinFor(player: String): ResourceLocation? {
        val connection = Minecraft.getInstance().connection ?: return null
        val info = runCatching { UUID.fromString(player) }.getOrNull()
            ?.let { connection.getPlayerInfo(it) }
            ?: connection.getPlayerInfo(player)
        return info?.skin?.texture
    }


    private val blocks = HashMap<String, Block?>()

    private fun blockFor(blockId: String): Block? = blocks.getOrPut(blockId) {
        val location = ResourceLocation.tryParse(blockId) ?: return@getOrPut null
        BuiltInRegistries.BLOCK.getOptional(location).orElse(null)
    }

    override fun blockName(blockId: String): String? = blockFor(blockId)?.name?.string

    override fun drawBlockModel(
        matrixStack: UMatrixStack,
        blockId: String,
        x: Float,
        y: Float,
        size: Float,
        yaw: Float,
        pitch: Float,
        alpha: Float,
    ): Boolean {
        val block = blockFor(blockId) ?: return false
        val mc = Minecraft.getInstance()
        return drawNative(matrixStack, x, y, size, size, alpha, clip = true, depth = true) { handle, w, h ->
            val g = handle as GuiGraphics
            val pose = g.pose()
            pose.pushPose()
            pose.translate(w / 2.0, h / 2.0, 100.0)
            // 0.62 rather than 1.0: a cube turned to the isometric angle measures sqrt(3) across its
            // long diagonal, so a unit-scaled cube would overrun the box by ~73% at the corners.
            val scale = minOf(w, h) * 0.62f
            pose.scale(scale, -scale, scale)
            // +pitch, not -pitch. The scale above flips Y so that +Y is up on screen, and that flip
            // also reverses the sense of a rotation about X — so the negative angle that reads as
            // "tilt the camera down onto the top face" in world space tilted it *up* here, and every
            // block was shown from underneath.
            pose.mulPose(Quaternionf().rotationX(pitch * Math.PI.toFloat() / 180f))
            // +180: a block's default state faces **north**, which is away from the camera in this
            // projection — so a furnace was presented back-first and the face was never visible. Half
            // a turn puts the front toward the viewer, and the isometric yaw then reads off its
            // front-right corner as intended.
            pose.mulPose(Quaternionf().rotationY((yaw + 180f) * Math.PI.toFloat() / 180f))
            pose.translate(-0.5, -0.5, -0.5)

            val buffers = mc.renderBuffers().bufferSource()
            @Suppress("DEPRECATION")
            mc.blockRenderer.renderSingleBlock(
                block.defaultBlockState(),
                pose,
                buffers,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
            )
            buffers.endBatch()
            pose.popPose()
        }
    }


    private val effects = HashMap<String, Holder<MobEffect>?>()

    private fun effectFor(effectId: String): Holder<MobEffect>? = effects.getOrPut(effectId) {
        val location = ResourceLocation.tryParse(effectId) ?: return@getOrPut null
        BuiltInRegistries.MOB_EFFECT.getHolder(location).orElse(null)
    }

    override fun effectName(effectId: String): String? =
        effectFor(effectId)?.value()?.displayName?.string

    override fun drawEffectIcon(
        matrixStack: UMatrixStack,
        effectId: String,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float,
    ): Boolean {
        val effect = effectFor(effectId) ?: return false
        val mc = Minecraft.getInstance()
        val sprite = mc.getMobEffectTextures().get(effect) ?: return false
        return drawNative(matrixStack, x, y, size, size, alpha, clip = true, depth = false) { handle, w, h ->
            val g = handle as GuiGraphics
            g.blit(0, 0, 0, w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), sprite)
        }
    }


    /**
     * Run an arbitrary `GuiGraphics` draw inside a UI-space box, with the same plumbing [drawItem] and
     * [drawEntity] each hand-roll: adopt Elementa's matrix, translate to the box, clip, fade, and
     * clean up after 3D content.
     * The pose is translated to the box's top-left before the callback runs, so callers draw at
     * `(0, 0)` and never have to know where their widget ended up on screen — which is the difference
     * between a snippet that works in a scroll view inside a dragged window and one that only works at
     * the origin.
     */
    override fun drawNative(
        matrixStack: UMatrixStack,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        alpha: Float,
        clip: Boolean,
        depth: Boolean,
        draw: BrassNativeDraw,
    ): Boolean {
        if (alpha <= 0.02f) return true
        if (width <= 0f || height <= 0f) return true

        val mc = Minecraft.getInstance()
        val graphics = GuiGraphics(mc, mc.renderBuffers().bufferSource())

        val pose = graphics.pose()
        pose.pushPose()
        pose.mulPose(matrixStack.peek().model)
        pose.translate(x.toDouble(), y.toDouble(), 0.0)

        if (clip) pushScissor(x, y, width, height)
        applyFade(alpha)
        if (depth) RenderSystem.enableDepthTest()

        return try {
            draw.draw(graphics, width, height)
            graphics.flush()
            true
        } catch (_: Throwable) {
            // A caller's broken draw must not take the screen down with it — and must not leave the
            // scissor, the shader colour or the pose behind either, hence the finally below.
            false
        } finally {
            if (depth) {
                // See drawEntity: 3D content is drawn with depth testing on, and without this any later
                // flat UI that overlaps it fails the depth test and disappears.
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX)
            }
            clearFade(alpha)
            if (clip) popScissor()
            pose.popPose()
        }
    }


    private fun applyFade(alpha: Float) {
        if (alpha >= 0.999f) return
        val a = alpha.coerceIn(0f, 1f)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(a, a, a, a)
    }

    /** Undo [applyFade]. Must run even on the failure path, or the whole UI stays tinted. */
    private fun clearFade(alpha: Float) {
        if (alpha >= 0.999f) return
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    }

    // GuiGraphics owns a scissor *stack* and assumes it is the only thing touching GL_SCISSOR_TEST.
    // Elementa sets the scissor directly for its ScissorEffect, so the two cannot be mixed: popping
    // GuiGraphics' empty stack disables scissoring outright and silently drops Elementa's clip. These
    // two functions read the live GL state, intersect our box with whatever was already there, and put
    // the original back exactly as it was.

    private val scissorStack = ArrayDeque<IntArray?>()

    /**
     * Clip drawing to the UI-space rectangle at ([x],[y]) sized [w] x [h], **intersected** with any
     * clip already in force, so this can never widen a caller's scissor.
     */
    private fun pushScissor(x: Float, y: Float, w: Float, h: Float) {
        val savedEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        val savedBox = if (savedEnabled) IntArray(4).also { GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, it) } else null
        scissorStack.addLast(savedBox)

        val window = Minecraft.getInstance().window
        val scale = window.guiScale
        val fbHeight = window.height

        // GL scissor is in framebuffer pixels with the origin at the BOTTOM-left; the UI is in scaled
        // pixels from the top-left, hence the flip.
        var sx = floor(x * scale).toInt()
        var sy = fbHeight - ceil((y + h) * scale).toInt()
        var sx2 = ceil((x + w) * scale).toInt()
        var sy2 = fbHeight - floor(y * scale).toInt()

        if (savedBox != null) {
            sx = maxOf(sx, savedBox[0])
            sy = maxOf(sy, savedBox[1])
            sx2 = minOf(sx2, savedBox[0] + savedBox[2])
            sy2 = minOf(sy2, savedBox[1] + savedBox[3])
        }

        RenderSystem.enableScissor(sx, sy, (sx2 - sx).coerceAtLeast(0), (sy2 - sy).coerceAtLeast(0))
    }

    private fun popScissor() {
        // `removeLastOrNull` cannot be used here: a null entry is a *meaningful* value ("the test was
        // off"), indistinguishable from its empty-stack signal.
        if (scissorStack.isEmpty()) return
        val savedBox = scissorStack.removeLast()
        if (savedBox != null) {
            RenderSystem.enableScissor(savedBox[0], savedBox[1], savedBox[2], savedBox[3])
        } else {
            RenderSystem.disableScissor()
        }
    }
}
