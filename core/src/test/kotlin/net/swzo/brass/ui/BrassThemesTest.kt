package net.swzo.brass.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.awt.Color

class BrassThemesTest {

    @Test
    fun `hex round trips`() {
        for (c in listOf(Color(0x1F, 0xBF, 0x63), Color.BLACK, Color.WHITE, Color(1, 2, 3))) {
            assertEquals(c.rgb, BrassThemes.parseHex(BrassThemes.toHex(c))!!.rgb)
        }
    }

    @Test
    fun `hex accepts both spellings`() {
        assertEquals(BrassThemes.parseHex("#1FBF63")!!.rgb, BrassThemes.parseHex("1FBF63")!!.rgb)
        assertNotNull(BrassThemes.parseHex("  #1fbf63  "))
    }

    @Test
    fun `bad hex is ignored rather than throwing`() {
        // a hand-edited or out-of-date config must downgrade, not fail
        assertNull(BrassThemes.parseHex(""))
        assertNull(BrassThemes.parseHex("#12345"))
        assertNull(BrassThemes.parseHex("#GGGGGG"))
        assertNull(BrassThemes.parseHex("not a colour"))
    }

    @Test
    fun `an unknown theme id falls back to the default`() {
        BrassThemes.apply("no such theme", null)
        assertEquals(BrassThemes.DEFAULT.name, BrassThemes.currentId)
    }

    @Test
    fun `applying an accent tints the brass ramp but not the ink`() {
        val base = BrassThemes.byId("ocean")!!
        BrassThemes.apply("ocean", "#FF0000")
        val tinted = Colors.theme

        assertEquals(base.ink900.rgb, tinted.ink900.rgb, "surfaces come from the theme underneath")
        assertEquals(Color(0xFF, 0, 0).rgb, tinted.brass500.rgb, "the accent is the tint")
        BrassThemes.apply(BrassThemes.DEFAULT.name, null)
    }

    @Test
    fun `an accent-tinted theme still forwards every non-accent role`() {
        // The regression BrassForwardingTheme exists for: AccentTinted forwarded 62 of 100 roles by
        // hand and the rest silently fell back to the DARK defaults. Compare a tinted ocean against
        // plain ocean on roles that have nothing to do with the accent.
        val base = BrassThemes.byId("ocean")!!
        BrassThemes.apply("ocean", "#FF0000")
        val tinted = Colors.theme

        assertEquals(base.scrollTrack.rgb, tinted.scrollTrack.rgb)
        assertEquals(base.codeBg.rgb, tinted.codeBg.rgb)
        assertEquals(base.outerBorder.rgb, tinted.outerBorder.rgb)
        assertEquals(base.textMuted.rgb, tinted.textMuted.rgb)
        assertEquals(base.danger.rgb, tinted.danger.rgb)
        assertEquals(base.shadow.rgb, tinted.shadow.rgb)
        assertEquals(base.radius, tinted.radius)
        BrassThemes.apply(BrassThemes.DEFAULT.name, null)
    }

    @Test
    fun `selecting a theme adopts its own accent`() {
        val ocean = BrassThemes.byId("ocean")!!
        BrassThemes.apply("ocean", ocean.defaultAccent?.let(BrassThemes::toHex))
        if (ocean.defaultAccent != null) {
            assertEquals(ocean.defaultAccent!!.rgb, BrassThemes.accent!!.rgb)
        }
        BrassThemes.apply(BrassThemes.DEFAULT.name, null)
    }

    @Test
    fun `every role derived from the brass ramp follows the accent tint`() {
        // The regression: converting AccentTinted to BrassForwardingTheme made these forward from the
        // *untinted* base, because the old hand-written version omitted them and the omissions looked
        // like oversights. They are not — BrassTheme derives each of them from the ramp, so a tinted
        // theme has to let that derivation run rather than taking the base's answer.
        //
        // Visible as: the brass seam in a window header, the rule down a tooltip's left edge, a
        // dropdown's seam and every heading staying on the old colour after an accent change.
        val base = BrassThemes.byId("ocean")!!
        BrassThemes.apply("ocean", "#FF0000")
        val tinted = Colors.theme
        val red = Color(0xFF, 0, 0)

        assertEquals(red.rgb, tinted.brass500.rgb, "sanity: the ramp is tinted")
        assertEquals(tinted.brass500.rgb, tinted.accent.rgb, "accent = brass500")
        assertEquals(tinted.brass300.rgb, tinted.accentBright.rgb, "accentBright = brass300")
        assertEquals(tinted.brass500.rgb, tinted.accentBorder.rgb, "accentBorder = brass500")
        assertEquals(tinted.brass300.rgb, tinted.outlineAccent.rgb, "outlineAccent = brass300")
        assertEquals(tinted.brass600.rgb, tinted.primaryFill.rgb, "primaryFill = brass600")
        assertEquals(tinted.brass400.rgb, tinted.syntaxKeyword.rgb, "syntaxKeyword = brass400")
        assertEquals(tinted.accent.rgb and 0xFFFFFF, tinted.selection.rgb and 0xFFFFFF, "selection washes the accent")

        for (role in listOf(tinted.accent, tinted.accentBright, tinted.accentBorder, tinted.outlineAccent)) {
            assertNotEquals(base.accent.rgb, role.rgb, "a tinted role must not equal the untinted theme's")
        }
        BrassThemes.apply(BrassThemes.DEFAULT.name, null)
    }

    @Test
    fun `every registered theme is retrievable by id`() {
        for (theme in BrassThemes.all()) {
            assertEquals(theme.name, BrassThemes.byId(theme.name)!!.name)
        }
    }
}
