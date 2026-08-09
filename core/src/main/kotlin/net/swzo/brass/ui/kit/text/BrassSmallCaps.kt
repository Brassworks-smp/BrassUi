@file:Suppress("unused")
package net.swzo.brass.ui.kit.text

import com.google.gson.Gson
import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIImage
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.base.BrassStats
import net.swzo.brass.ui.kit.text.BrassSmallCaps.drawString
import net.swzo.brass.ui.kit.text.BrassSmallCaps.width
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.InputStreamReader
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

/**
 * The **small glyph sheets** [BrassTag] draws its text from - one cell per character, with a JSON
 * atlas beside each sheet giving the cell size and every glyph's advance width:
 * | sheet | cells | covers |
 * |---|---|---|
 * | `small-caps.png` | 26 x 6x9 | `A`–`Z`, case-insensitive |
 * | `small-numbers.png` | 10 x 4x9 | `0`–`9` |
 * Both rasterise to **5 px of ink** on a 9-px line box, which is the whole reason they exist together:
 * a tag's pill is sized to that ink height (see the geometry table on [BrassTag]), so a digit drawn
 * from the ordinary font - taller, and sitting on a different baseline - visibly broke the line of a
 * label like `1.21` or `v2`. Anything in neither sheet still falls through to [BrassFont].
 * ### Why bitmaps and not the font
 * The small capitals live in Minecraft's Unicode font pages - so they exist in game and nowhere else.
 * The standalone UniversalCraft font the desktop build uses covers little beyond ASCII and rendered
 * every one of them as a missing glyph, so tags came out as rows of blank boxes off-game.
 * The caps sheet is those exact glyphs, rasterised once out of the running game and committed; in game
 * the result is pixel-identical to what the font produced, because the pixels came from the font. The
 * dev command that produced it has been removed - it existed to generate that file, the file is
 * generated, and a one-shot tool that ships forever is a liability rather than a convenience. The
 * digits are drawn by hand to match. The trade is that tags no longer follow a resource pack's font -
 * acceptable, and arguably the point, since their geometry is fixed anyway.
 */
object BrassSmallCaps {

    val cellHeight: Int get() = caps.cellHeight


    private val caps = Sheet("small-caps", LETTERS)
    private val numbers = Sheet("small-numbers", DIGITS)

    private class Sheet(name: String, private val count: Int) {

        private class GlyphMeta {
            var index: Int = 0
            var advance: Int = 0
        }

        private class AtlasFile {
            var cellWidth: Int = 0
            var cellHeight: Int = 0
            var glyphs: List<GlyphMeta>? = null
        }

        private val sheetPath = "/assets/brassui/textures/gui/$name.png"
        private val atlasPath = "/assets/brassui/textures/gui/$name.json"

        // Read through THIS class's own class loader, for the same reason BrassIcons does: under
        // NeoForge's module layers Elementa cannot see `assets/brassui/...` and would silently hand
        // back a placeholder.
        private val image: BufferedImage? by lazy {
            runCatching {
                BrassSmallCaps::class.java.getResourceAsStream(sheetPath)?.use(ImageIO::read)
            }.getOrNull()
        }

        private val meta: AtlasFile? by lazy {
            runCatching {
                val stream = BrassSmallCaps::class.java.getResourceAsStream(atlasPath)
                    ?: return@runCatching null
                stream.use { Gson().fromJson(InputStreamReader(it), AtlasFile::class.java) }
            }.getOrNull()
        }

        val cellWidth: Int get() = meta?.cellWidth ?: 0
        val cellHeight: Int get() = meta?.cellHeight ?: BrassFont.LINE

        private val advances: List<Int> by lazy {
            val glyphs = meta?.glyphs ?: return@lazy emptyList()
            // Ordered by index rather than trusting file order, so the sheet and the code cannot
            // disagree about which cell is which character.
            val byIndex = glyphs.associateBy { it.index }
            (0 until count).map { byIndex[it]?.advance ?: 0 }
        }

        private val glyphs: Array<UIImage?> by lazy {
            val img = image
            val cw = cellWidth
            val ch = meta?.cellHeight ?: 0
            Array(count) { i ->
                if (img == null || cw <= 0 || ch <= 0 || (i + 1) * cw > img.width || ch > img.height) null
                else {
                    // NEAREST and whole-pixel placement, or the 5-px ink turns to mush at any gui scale.
                    UIImage(CompletableFuture.completedFuture(img.getSubimage(i * cw, 0, cw, ch))).also {
                        it.textureMinFilter = UIImage.TextureScalingMode.NEAREST
                        it.textureMagFilter = UIImage.TextureScalingMode.NEAREST
                    }
                }
            }
        }

        fun available(): Boolean = image != null && advances.isNotEmpty()

        fun advance(i: Int): Float = advances.getOrElse(i) { 0 }.toFloat()

        fun glyph(i: Int): UIImage? = if (i in 0 until count) glyphs[i] else null
    }

    private fun locate(ch: Char): Pair<Sheet, Int>? {
        val upper = ch.uppercaseChar()
        return when {
            upper in 'A'..'Z' -> caps to (upper - 'A')
            ch in '0'..'9' -> numbers to (ch - '0')
            else -> null
        }
    }

    fun available(): Boolean = caps.available()


    /**
     * Advance width of [s] as [drawString] will lay it out: sheet advances for the characters a sheet
     * carries, the font's own for everything else. Measured through the same rules as drawing, so a
     * caller sizing a box around the text can never be off by a pixel from what lands in it.
     */
    fun width(c: UIComponent, s: String, scale: Float = 1f): Float = unscaledWidth(c, s) * scale

    /**
     * [width] at scale 1, memoised.
     * The loop allocates a single-character String per non-sheet character and calls through to the
     * font for it. [net.swzo.brass.ui.kit.text.BrassTag] measures its label on **every frame it
     * draws** (the pill sizes itself to its text), so a row of tags was re-running this per character
     * per frame for text that never changes.
     */
    private fun unscaledWidth(c: UIComponent, s: String): Float = widthCache.getOrPut(s) {
        var w = 0f
        for (ch in s) {
            val at = locate(ch)
            w += if (at != null && at.first.available()) at.first.advance(at.second)
                 else BrassFont.width(c, ch.toString())
        }
        w
    }

    private val widthCache = object : LinkedHashMap<String, Float>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>) = size > 512
    }

    fun drawString(
        m: UMatrixStack,
        c: UIComponent,
        s: String,
        x: Float,
        y: Float,
        color: Color,
        scale: Float = 1f,
    ): Float {
        var penX = x
        for (ch in s) {
            val at = locate(ch)
            if (at == null || !at.first.available()) {
                // In neither sheet: draw it as text, exactly as before the sheets existed.
                val str = ch.toString()
                BrassFont.draw(m, c, str, penX, y, color, shadow = false, scale = scale)
                penX += BrassFont.width(c, str) * scale
                continue
            }

            val (sheet, index) = at
            val img = sheet.glyph(index)
            if (img != null) {
                BrassStats.quad()
                img.drawImage(
                    m,
                    Math.round(penX).toDouble(), Math.round(y).toDouble(),
                    (sheet.cellWidth * scale).toDouble(), (sheet.cellHeight * scale).toDouble(),
                    BrassAmbientFade.apply(color),
                )
            }
            penX += sheet.advance(index) * scale
        }
        return penX - x
    }

    private const val LETTERS = 26

    private const val DIGITS = 10
}
