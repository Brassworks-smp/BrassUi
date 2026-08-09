package net.swzo.brass.ui.desktop

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.platform.BrassCursor
import net.swzo.brass.ui.kit.platform.BrassPlatform
import org.lwjgl.glfw.GLFW

/**
 * The desktop end of the [BrassPlatform] seam.
 *
 * The toolkit asks the platform for the handful of things it cannot do itself. In game those are
 * backed by Minecraft; here only one of them has an answer — the mouse cursor, which GLFW owns
 * directly, since we have the window handle. Items and entities are the game's renderer and stay
 * unavailable, so those widgets keep drawing their placeholders exactly as they did before.
 *
 * Without this bound, cursors silently stayed as the arrow: every widget requests a shape while
 * hovered and [BrassCursor] resolves the winner, but the final hand-off had nowhere to go.
 */
class DesktopPlatform(private val windowHandle: Long) : BrassPlatform {

    /**
     * GLFW cursor objects, created on first use and kept for the process's life.
     *
     * Creating one per request would leak a native object every time the pointer crossed a widget
     * boundary, which on a UI like this is several times a second.
     */
    private val cursors = HashMap<BrassCursor.Kind, Long>()

    override fun setCursor(kind: BrassCursor.Kind) {
        // 0 (NULL) means "no cursor object", which GLFW reads as the plain arrow — so an unsupported
        // shape falls back to the arrow rather than being ignored or crashing.
        GLFW.glfwSetCursor(windowHandle, cursorFor(kind))
    }

    private fun cursorFor(kind: BrassCursor.Kind): Long = cursors.getOrPut(kind) {
        if (kind == BrassCursor.Kind.ARROW) return@getOrPut 0L
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
        val handle = GLFW.glfwCreateStandardCursor(shape)
        // The diagonal resize shapes are the ones most likely to be missing (they are newer, and on
        // macOS GLFW fakes them from private system cursors). Fall back to the nearest axis shape so
        // a window corner still reads as resizable instead of reverting to a plain arrow.
        if (handle != 0L) handle else when (kind) {
            BrassCursor.Kind.RESIZE_NWSE, BrassCursor.Kind.RESIZE_NESW ->
                GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_EW_CURSOR)
            else -> 0L
        }
    }

    // ---- game-only features, unavailable off-game ------------------------------------------------

    override fun drawItem(
        matrixStack: UMatrixStack,
        itemId: String,
        x: Float,
        y: Float,
        size: Float,
        count: Int,
        alpha: Float,
    ): Boolean = false

    override fun itemName(itemId: String): String? = null

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
    ): Boolean = false

    override fun entityName(entityId: String): String? = null
}
