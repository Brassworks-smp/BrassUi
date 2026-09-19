@file:Suppress("unused")
package net.swzo.brass.ui.kit.media

import com.google.gson.Gson
import gg.essential.elementa.components.UIImage
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.base.BrassStats
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.InputStreamReader
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

/**
 * The toolkit's icon set: a **sprite sheet** at `assets/brassui/textures/gui/icons.png` with an
 * accompanying `icons.json` naming each sprite's rectangle within it.
 * ```json
 * { "width": 96, "height": 36, "cell": 12,
 *   "sprites": { "close": { "x": 0, "y": 0, "w": 12, "h": 12 }, ... } }
 * ```
 * Adding an icon means painting it into the sheet and adding one entry to the JSON - no code change,
 * and no per-icon file. Sprites are white masks on transparency, so every icon tints with its
 * widget's animated text colour.
 * ### Loading
 * The sheet is read through **this class's own** class loader, not Elementa's `UIImage.ofResource`:
 * Elementa resolves that path against `UIImage`'s class, and under NeoForge's module layers Elementa
 * is a GAMELIBRARY in a different module from the mod, so it cannot see `assets/brassui/...` and
 * silently falls back to a placeholder.
 * The sheet is decoded once, on first use. Each sprite is then cropped out lazily and cached, so an
 * icon that never appears costs nothing beyond its JSON entry. Sprites are sampled NEAREST and
 * snapped to whole pixels, keeping the pixel-art crisp at any GUI scale.
 */
object BrassIcons {

    private const val SHEET = "/assets/brassui/textures/gui/icons.png"
    private const val ATLAS = "/assets/brassui/textures/gui/icons.json"

    class Icon(private val name: String) {
        val image: UIImage? by lazy { sprite(name) }

        val present: Boolean get() = name.isNotEmpty()
    }


    private class Rect {
        var x: Int = 0
        var y: Int = 0
        var w: Int = 0
        var h: Int = 0
    }

    private val sheet: BufferedImage? by lazy {
        runCatching {
            BrassIcons::class.java.getResourceAsStream(SHEET)?.use(ImageIO::read)
        }.getOrNull()
    }

    private class AtlasFile {
        var sprites: Map<String, Rect>? = null
    }

    private val atlas: Map<String, Rect> by lazy {
        runCatching {
            val stream = BrassIcons::class.java.getResourceAsStream(ATLAS) ?: return@runCatching emptyMap()
            val file = stream.use { Gson().fromJson(InputStreamReader(it), AtlasFile::class.java) }
            file?.sprites.orEmpty()
        }.getOrDefault(emptyMap())
    }

    private val cache = HashMap<String, UIImage?>()

    @Synchronized
    private fun sprite(name: String): UIImage? {
        cache[name]?.let { return it }
        if (cache.containsKey(name)) return null   // already tried and failed

        val img = sheet
        val r = atlas[name]
        val out = if (img == null || r == null || r.x + r.w > img.width || r.y + r.h > img.height) {
            null
        } else {
            UIImage(CompletableFuture.completedFuture(img.getSubimage(r.x, r.y, r.w, r.h))).also {
                it.textureMinFilter = UIImage.TextureScalingMode.NEAREST
                it.textureMagFilter = UIImage.TextureScalingMode.NEAREST
            }
        }
        cache[name] = out
        return out
    }

    fun spriteNames(): Set<String> = atlas.keys

    /**
     * Draw [icon] in a [size]x[size] box at ([x],[y]), tinted [color]. Coordinates snap to whole
     * pixels so the sprite lands on the pixel grid instead of rendering half-blurred. A missing
     * sprite draws nothing rather than a placeholder - a silent gap is easier to spot in the dev
     * inspector than Elementa's fallback checkerboard, and never obscures neighbouring widgets.
     */
    fun draw(m: UMatrixStack, icon: Icon, x: Float, y: Float, size: Float, color: Color) {
        val img = icon.image ?: return
        BrassStats.quad()
        img.drawImage(
            m,
            Math.round(x).toDouble(), Math.round(y).toDouble(),
            Math.round(size).toDouble(), Math.round(size).toDouble(),
            BrassAmbientFade.apply(color),
        )
    }


    val NONE = Icon("")

    val CLOSE = Icon("close")
    val MINIMIZE = Icon("minimize")
    val MAXIMIZE = Icon("maximize")
    val RESTORE = Icon("restore")

    val CHECK = Icon("check")
    val PLUS = Icon("plus")
    val MINUS = Icon("minus")
    val SEARCH = Icon("search")
    val GEAR = Icon("gear")
    val FOLDER = Icon("folder")
    val HEART = Icon("heart")
    val PLAY = Icon("play")
    val INFO = Icon("info")
    val WARN = Icon("warn")
    val DOTS = Icon("dots")

    val CHEVRON_LEFT = Icon("chevron_left")
    val CHEVRON_RIGHT = Icon("chevron_right")
    val CHEVRON_UP = Icon("chevron_up")
    val CHEVRON_DOWN = Icon("chevron_down")
}
