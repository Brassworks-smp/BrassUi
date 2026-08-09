package net.swzo.brass.ui.kit.media

import gg.essential.elementa.components.UIImage
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.constrain
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.base.BrassStats
import net.swzo.brass.ui.kit.net.BrassworksProfile
import net.swzo.brass.ui.kit.platform.BrassPlatform
import net.swzo.brass.ui.kit.surface.BrassTooltip
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import kotlin.math.min
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A player's avatar - the face from their skin, as every player list in the game shows it.
 * ```kotlin
 * BrassPlayerHead("Notch")                                    // 18 px card, face from the game skin cache
 * BrassPlayerHead("Notch", size = 48f)                        // the same card, bigger
 * BrassPlayerHead(uuid, source = BrassPlayerHead.Source.BRASSWORKS)   // face from the BrassWorks API
 * ```
 * [player] is a name or a UUID; which one is the resolver's problem, not the widget's. Skins load
 * asynchronously, so a head that has not arrived yet shows the placeholder and starts drawing the
 * moment it does - no frame ever blocks on the network.
 * ### Where the face comes from
 * [source] chooses the resolver:
 * - [Source.GAME] (default) asks the running game's skin cache through the platform seam. Immediate for
 *   anyone the client has seen, but it only knows players in *this* session — an offline teammate, or
 *   any lookup on the desktop, comes up blank.
 * - [Source.BRASSWORKS] asks the [BrassworksProfile] API by id, which resolves **any** player online or
 *   not and hands back a canonical username (used for the tooltip) plus a face avatar. That is what a
 *   team roster wants: heads for everyone on the team, whether or not they happen to be logged in.
 * ### Sizing
 * The card is **square by construction**: [size] is one number and both constraints follow it, so a
 * caller scales an avatar by saying how big it is rather than by writing two constraints and hoping
 * they agree. That is not just convenience - a head in a non-square box has to be centred in it, and
 * the leftover on the long axis showed up as a band of card above and below the face that looked like
 * padding nobody asked for. One number means there is never any leftover.
 * A caller's own `constrain {}` still wins, since it is applied after construction; the face stays
 * square inside whatever box it ends up with, because a stretched face is unmistakably wrong in a way
 * a stretched icon is not.
 */
class BrassPlayerHead(
    player: String,
    var size: Float = DEFAULT_SIZE,
    val source: Source = Source.GAME,
    tooltip: Boolean = true,
    private val onClick: (() -> Unit)? = null,
) : BrassPlatformVisual(BrassAccent.DEFAULT) {

    var player: String = player
        set(value) {
            if (field == value) return
            field = value
            // Drop whatever the old key resolved to, so nothing stale draws for the new player.
            profileFuture = null
            avatarFuture = null
            texture = null
            resolvedName = null
        }

    private var resolvedName: String? = null

    // BRASSWORKS resolution, in two hops: key -> profile (name + avatar url) -> decoded avatar image.
    private var profileFuture: CompletableFuture<BrassworksProfile.Profile?>? = null
    private var avatarFuture: CompletableFuture<BufferedImage?>? = null

    /**
     * The uploaded avatar texture, built on the first frame after the download lands.
     * Deliberately created during draw rather than in the loader's callback: [UIImage] uploads to the
     * GPU, which must happen on the render thread, and the loaders complete on a worker.
     */
    private var texture: UIImage? = null

    init {
        placeholder = "?"
        constrain {
            width = basicWidthConstraint { this@BrassPlayerHead.size }
            height = basicHeightConstraint { this@BrassPlayerHead.size }
        }
        clickable = onClick != null
        if (onClick != null) onMouseClick { e -> if (active && e.mouseButton == 0) onClick.invoke() }
        // Attached once with a supplier, not from onMouseEnter: adding listeners while Elementa is
        // iterating them throws from updateCurrentlyHoveredState. See BrassItem. The supplier prefers the
        // API-resolved username so a UUID-keyed head does not show a raw UUID in its tooltip.
        if (tooltip) BrassTooltip.attachLazy(this, { resolvedName ?: player })
    }

    override fun proxyActivate() { if (active) onClick?.invoke() }

    override fun contentBox(x: Int, y: Int, w: Int, h: Int): FloatArray {
        val size = (min(w, h) - BORDER * 2).coerceAtLeast(1).toFloat()
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
    ): Boolean = when (source) {
        Source.GAME -> platform.drawPlayerFace(m, player, x, y, w, fade)
        Source.BRASSWORKS -> paintBrassworks(m, x, y, w, h, fade)
    }

    private fun paintBrassworks(m: UMatrixStack, x: Float, y: Float, w: Float, h: Float, fade: Float): Boolean {
        val image = avatarImage() ?: return false
        val tex = texture ?: UIImage(CompletableFuture.completedFuture(image)).also {
            // Nearest-neighbour: a face is pixel art, and linear filtering turns its edges to mush at
            // GUI scale.
            it.textureMinFilter = UIImage.TextureScalingMode.NEAREST
            it.textureMagFilter = UIImage.TextureScalingMode.NEAREST
            texture = it
        }
        BrassStats.quad()
        tex.drawImage(m, x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble(), tint(fade))
        return true
    }

    private fun avatarImage(): BufferedImage? {
        val pf = profileFuture ?: BrassworksProfile.load(player).also { profileFuture = it }
        if (!pf.isDone) return null
        val profile = pf.getNow(null) ?: return null
        resolvedName = profile.username
        val af = avatarFuture ?: BrassImageLoader.load(profile.avatarUrl).also { avatarFuture = it }
        if (!af.isDone) return null
        return af.getNow(null)
    }

    private fun tint(fade: Float): Color {
        val base = BrassAmbientFade.apply(Color.WHITE)
        val alpha = (base.alpha * fade).toInt().coerceIn(0, 255)
        return Color(base.red, base.green, base.blue, alpha)
    }

    enum class Source {
        GAME,

        BRASSWORKS,
    }

    companion object : BrassDemoSource {


        override fun demo() = BrassDemo("player-head", "Player head", 36f, 36f, worldRequired = true) {
            BrassPlayerHead(DEMO_PLAYER, size = 32f)
        }

        private const val DEMO_PLAYER = "Dev"

        const val DEFAULT_SIZE = 18f

        /** The card's own 1-px border, which the face must not paint over. */
        private const val BORDER = 1
    }
}
