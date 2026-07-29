package net.swzo.brass.ui

import net.swzo.brass.ui.kit.layout.BrassScrollbarModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Geometry that used to exist twice, in the scrollbar component and inside BrassTable. */
class BrassScrollbarModelTest {

    private fun model(viewport: Float, content: Float) =
        BrassScrollbarModel(viewport = viewport, content = content)

    @Test
    fun `nothing to scroll when the content fits`() {
        val m = model(viewport = 100f, content = 80f)
        assertFalse(m.scrollable)
        assertEquals(0f, m.overflow)
        assertEquals(0f, m.gripTop(50f))
    }

    @Test
    fun `grip spans the whole track at the extremes`() {
        val m = model(viewport = 100f, content = 200f)
        assertEquals(0f, m.gripTop(0f))
        assertEquals(m.gripTravel(), m.gripTop(m.overflow))
    }

    @Test
    fun `gripTop and offsetForGripTop are inverses`() {
        val m = model(viewport = 100f, content = 400f)
        for (offset in listOf(0f, 37f, 150f, 299f, 300f)) {
            val round = m.offsetForGripTop(m.gripTop(offset))
            assertEquals(offset, round, 0.01f, "round trip at offset $offset")
        }
    }

    @Test
    fun `grip never shrinks below the minimum`() {
        // a hundred thousand rows in a short viewport would otherwise give a sub-pixel grip
        val m = BrassScrollbarModel(viewport = 100f, content = 1_000_000f, minGrip = 12f)
        assertEquals(12f, m.gripHeight())
    }

    @Test
    fun `clamping keeps the offset in range`() {
        val m = model(viewport = 100f, content = 250f)
        assertEquals(0f, m.clamp(-40f))
        assertEquals(150f, m.clamp(9999f))
        assertEquals(75f, m.clamp(75f))
    }

    @Test
    fun `paging moves toward the click`() {
        val m = model(viewport = 100f, content = 400f)
        // clicking above the grip pages back, below pages forward
        assertEquals(0f, m.pageToward(50f, trackY = 0f))
        assertEquals(150f, m.pageToward(50f, trackY = 99f))
    }

    @Test
    fun `reveal nudges by the minimum needed`() {
        val m = model(viewport = 100f, content = 500f)
        assertEquals(20f, m.reveal(offset = 50f, top = 20f, height = 10f), "scrolls up to reach it")
        assertEquals(50f, m.reveal(offset = 50f, top = 60f, height = 10f), "already visible: no move")
        assertEquals(110f, m.reveal(offset = 50f, top = 200f, height = 10f), "scrolls down to reach it")
    }

    @Test
    fun `gripContains distinguishes grip from track`() {
        val m = model(viewport = 100f, content = 200f)
        assertTrue(m.gripContains(0f, trackY = 1f))
        assertFalse(m.gripContains(0f, trackY = 99f))
    }
}
