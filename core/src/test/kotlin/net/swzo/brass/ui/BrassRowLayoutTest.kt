package net.swzo.brass.ui

import net.swzo.brass.ui.kit.layout.BrassRowLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrassRowLayoutTest {

    private val pad = 8f
    private val gap = 6f

    private fun solve(width: Float, leading: Float = 0f, meta: Float = 0f, tag: Float = 0f, actions: Float = 0f) =
        BrassRowLayout.solve(width, leading, meta, tag, actions, pad = pad, gap = gap, minTitle = 24f)

    @Test
    fun `everything fits - spans are ordered and end inside the row`() {
        val s = solve(300f, leading = 16f, meta = 20f, tag = 30f, actions = 31f)
        val metaX = s.metaX!!
        val tagX = s.tagX!!
        assertEquals(pad + 16f + gap, s.titleX)
        assertTrue(s.titleX + s.titleW <= metaX - gap + 0.01f)
        assertTrue(metaX + 20f <= tagX - gap + 0.01f)
        assertTrue(tagX + 30f <= s.actionsX - gap + 0.01f)
        assertEquals(300f - pad, s.actionsX + 31f)
    }

    @Test
    fun `without actions the tag ends at the padded right edge`() {
        val s = solve(200f, tag = 30f)
        assertEquals(200f - pad, s.tagX!! + 30f)
    }

    @Test
    fun `narrow rows drop meta first, then the tag, never the actions`() {
        val dropMeta = solve(130f, meta = 30f, tag = 30f, actions = 14f)
        assertNull(dropMeta.metaX)
        assertNotNull(dropMeta.tagX)
        assertTrue(dropMeta.titleW >= 24f)

        val dropBoth = solve(70f, meta = 30f, tag = 30f, actions = 14f)
        assertNull(dropBoth.metaX)
        assertNull(dropBoth.tagX)
        assertEquals(70f - pad - 14f, dropBoth.actionsX)
    }

    @Test
    fun `title width never goes negative`() {
        val s = solve(20f, leading = 16f, actions = 40f)
        assertTrue(s.titleW >= 0f)
        assertTrue(s.actionsX >= pad)
    }
}
