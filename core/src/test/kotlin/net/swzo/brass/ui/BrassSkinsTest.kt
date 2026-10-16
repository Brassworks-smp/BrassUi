package net.swzo.brass.ui

import net.swzo.brass.ui.kit.net.BrassSkins
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.util.Base64

class BrassSkinsTest {

    @Test
    fun `playerdb found and missing`() {
        val found = """{"code":"player.found","data":{"player":{"username":"Notch","id":"069a79f4-44e9-4726-a5be-fca90e38aaf5",
            "raw_id":"069a79f444e94726a5befca90e38aaf5","skin_texture":"https://textures.minecraft.net/texture/abc"}},"success":true}"""
        val skin = BrassSkins.parsePlayerDb(found)!!
        assertEquals("Notch", skin.name)
        assertEquals("069a79f444e94726a5befca90e38aaf5", skin.uuid)
        assertEquals("https://textures.minecraft.net/texture/abc", skin.skinUrl)
        assertEquals(BrassSkins.Provider.PLAYERDB, skin.provider)

        assertNull(BrassSkins.parsePlayerDb("""{"message":"No Minecraft user could be found.","code":"minecraft.invalid_username","data":{}}"""))
        assertNull(BrassSkins.parsePlayerDb("not json"))
    }

    @Test
    fun `ely textures and profile`() {
        assertEquals(
            "http://ely.by/storage/skins/69c6.png",
            BrassSkins.parseElyTextures("""{"SKIN":{"url":"http://ely.by/storage/skins/69c6.png"}}"""),
        )
        assertNull(BrassSkins.parseElyTextures("""{"CAPE":{"url":"x"}}"""))
        assertEquals("ffc8" to "ErickSkrauch", BrassSkins.parseIdName("""{"id":"ffc8","name":"ErickSkrauch"}"""))
    }

    @Test
    fun `mojang session textures are base64 json`() {
        val textures = """{"profileName":"Notch","textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/292"}}}"""
        val value = Base64.getEncoder().encodeToString(textures.toByteArray())
        val json = """{"id":"069a","name":"Notch","properties":[{"name":"textures","value":"$value"}]}"""
        val skin = BrassSkins.parseMojangSession(json)!!
        assertEquals("http://textures.minecraft.net/texture/292", skin.skinUrl)
        assertEquals("Notch", skin.name)
        assertNull(BrassSkins.parseMojangSession("""{"id":"069a","name":"Notch","properties":[]}"""))
    }

    @Test
    fun `uuid detection and https upgrade`() {
        assertTrue(BrassSkins.isUuid("069a79f444e94726a5befca90e38aaf5"))
        assertTrue(BrassSkins.isUuid("069a79f4-44e9-4726-a5be-fca90e38aaf5"))
        assertFalse(BrassSkins.isUuid("Notch"))
        assertEquals("https://ely.by/a.png", BrassSkins.https("http://ely.by/a.png"))
        assertEquals("https://x/a.png", BrassSkins.https("https://x/a.png"))
    }

    private fun skin(w: Int, h: Int, face: Int, hat: Int?): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val s = w / 64
        for (y in 0 until 8 * s) for (x in 0 until 8 * s) {
            img.setRGB(8 * s + x, 8 * s + y, face)
            if (hat != null) img.setRGB(40 * s + x, 8 * s + y, hat)
        }
        return img
    }

    @Test
    fun `face takes the hat layer over the face`() {
        val face = BrassSkins.faceOf(skin(64, 64, 0xFF112233.toInt(), null))!!
        assertEquals(8, face.width)
        assertEquals(0xFF112233.toInt(), face.getRGB(3, 3))

        val hatted = BrassSkins.faceOf(skin(64, 64, 0xFF112233.toInt(), 0xFFAABBCC.toInt()))!!
        assertEquals(0xFFAABBCC.toInt(), hatted.getRGB(0, 0))
    }

    @Test
    fun `fully opaque hat on a legacy skin is ignored like vanilla`() {
        val face = BrassSkins.faceOf(skin(64, 32, 0xFF112233.toInt(), 0xFF000000.toInt()))!!
        assertEquals(0xFF112233.toInt(), face.getRGB(4, 4))
    }

    @Test
    fun `hd skins are sampled at their own scale and tiny images rejected`() {
        val face = BrassSkins.faceOf(skin(128, 128, 0xFF445566.toInt(), null))!!
        assertEquals(0xFF445566.toInt(), face.getRGB(7, 7))
        assertNull(BrassSkins.faceOf(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)))
    }
}
