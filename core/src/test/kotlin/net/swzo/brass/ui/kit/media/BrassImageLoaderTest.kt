package net.swzo.brass.ui.kit.media

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class BrassImageLoaderTest {

    private val tmp = Files.createTempDirectory("brass-image-test")

    @AfterEach
    fun cleanUp() {
        BrassImageLoader.clear()
        tmp.toFile().deleteRecursively()
    }

    @Test
    fun `loads a local file by absolute path`() {
        val file = writePng(tmp.resolve("local.png"))
        assertNotNull(BrassImageLoader.load(file.toString()).get(5, TimeUnit.SECONDS))
    }

    @Test
    fun `loads a local file by file URI`() {
        val file = writePng(tmp.resolve("uri.png"))
        assertNotNull(BrassImageLoader.load(file.toUri().toString()).get(5, TimeUnit.SECONDS))
    }

    @Test
    fun `loads a classpath resource`() {
        val source = "net/swzo/brass/ui/kit/media/brass-test.png"
        assertNotNull(BrassImageLoader.load(":$source").get(5, TimeUnit.SECONDS))
        // A bare path that is not a file falls back to the classpath too.
        assertNotNull(BrassImageLoader.load(source).get(5, TimeUnit.SECONDS))
    }

    @Test
    fun `plain http is rejected without a request`() {
        assertNull(BrassImageLoader.load("http://example.com/image.png").get(5, TimeUnit.SECONDS))
    }

    @Test
    fun `missing local sources complete with null`() {
        assertNull(BrassImageLoader.load(tmp.resolve("nope.png").toString()).get(5, TimeUnit.SECONDS))
        assertNull(BrassImageLoader.load(":does/not/exist.png").get(5, TimeUnit.SECONDS))
    }

    private fun writePng(path: Path): Path {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xFFCC0000.toInt())
        Files.newOutputStream(path).use { ImageIO.write(image, "png", it) }
        return path
    }
}
