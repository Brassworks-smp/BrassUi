package net.swzo.brass.ui

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.InputStreamReader
import javax.imageio.ImageIO

class BrassIconsAtlasTest {

    private val sheet = ImageIO.read(javaClass.getResourceAsStream("/assets/brassui/textures/gui/icons.png"))
    private val sprites = JsonParser.parseReader(
        InputStreamReader(javaClass.getResourceAsStream("/assets/brassui/textures/gui/icons.json")!!),
    ).asJsonObject.getAsJsonObject("sprites").entrySet().associate { (name, r) ->
        val o = r.asJsonObject
        name to intArrayOf(o["x"].asInt, o["y"].asInt, o["w"].asInt, o["h"].asInt)
    }

    @Test
    fun `every sprite is inside the sheet and paints something`() {
        for ((name, r) in sprites) {
            val (x, y, w, h) = r.toList()
            assertTrue(x >= 0 && y >= 0 && x + w <= sheet.width && y + h <= sheet.height, "$name out of bounds")
            var lit = 0
            for (yy in y until y + h) for (xx in x until x + w) if ((sheet.getRGB(xx, yy) ushr 24) > 0) lit++
            assertTrue(lit > 0, "$name is empty")
        }
    }

    @Test
    fun `no two sprites share pixels`() {
        val cells = sprites.entries.toList()
        for (i in cells.indices) for (j in i + 1 until cells.size) {
            val a = cells[i].value; val b = cells[j].value
            val overlap = a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3]
            assertTrue(!overlap, "${cells[i].key} overlaps ${cells[j].key}")
        }
    }

    @Test
    fun `every BrassIcons constant names a sprite`() {
        val source = File("src/main/kotlin/net/swzo/brass/ui/kit/media/BrassIcons.kt").readText()
        val names = Regex("""Icon\("([a-z_0-9]+)"\)""").findAll(source).map { it.groupValues[1] }.toList()
        assertTrue(names.size > 100, "expected the full set, found ${names.size}")
        assertEquals(emptyList<String>(), names.filter { it !in sprites })
    }
}
