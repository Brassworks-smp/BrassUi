package net.swzo.brass.ui

import net.swzo.brass.ui.kit.dev.BrassLayoutRules
import net.swzo.brass.ui.kit.dev.BrassLayoutRules.Clip
import net.swzo.brass.ui.kit.dev.BrassLayoutRules.Node
import net.swzo.brass.ui.kit.dev.BrassLayoutRules.Rect
import net.swzo.brass.ui.kit.dev.BrassLayoutRules.Severity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrassLayoutRulesTest {

    private val screen = Rect(0f, 0f, 640f, 400f)

    private fun rules(root: Node) = BrassLayoutRules.check(root).map { it.rule }

    private fun window(vararg kids: Node) = Node(
        "Screen", screen,
        children = listOf(Node("BrassWindow", Rect(100f, 50f, 540f, 350f), layer = true, children = listOf(
            Node(
                "content", Rect(100f, 70f, 540f, 350f), clip = Clip.SCISSOR,
                insetRef = Rect(100f, 70f, 540f, 350f), children = kids.toList(),
            ),
        ))),
    )

    private fun button(x: Float, y: Float, w: Float = 60f, h: Float = 18f) =
        Node("BrassButton", Rect(x, y, x + w, y + h), widget = true, bleeds = true)

    @Test
    fun `a well inset screen is clean`() {
        assertEquals(emptyList<String>(), rules(window(button(110f, 80f), button(180f, 80f))))
    }

    @Test
    fun `a control running past the frame edge escapes the clip`() {
        assertTrue("escapes-clip" in rules(window(button(500f, 80f, w = 60f))))
    }

    @Test
    fun `keycap bleed counts as painted - a lip under the frame bottom escapes`() {
        assertTrue("escapes-clip" in rules(window(button(110f, 328f, h = 20f))))
    }

    @Test
    fun `rows may run past a scroll view vertically but not sideways`() {
        val scroll = { row: Node -> Node("ScrollComponent", Rect(110f, 80f, 530f, 200f), clip = Clip.SCROLL, children = listOf(row)) }
        val belowFold = Node("Row", Rect(112f, 400f, 520f, 418f), widget = true)
        assertEquals(emptyList<String>(), rules(window(scroll(belowFold))))
        val sideways = Node("Row", Rect(112f, 90f, 540f, 108f), widget = true)
        assertTrue("escapes-clip" in rules(window(scroll(sideways))))
    }

    @Test
    fun `overlapping controls in one layer are reported, a popup over them is not`() {
        assertTrue("overlap" in rules(window(button(110f, 80f), button(150f, 85f))))

        val popup = Node("BrassPopup", Rect(140f, 70f, 300f, 200f), layer = true, children = listOf(button(150f, 85f)))
        val tree = Node("Screen", screen, children = listOf(window(button(110f, 80f)).children.single(), popup))
        assertEquals(emptyList<String>(), rules(tree))
    }

    @Test
    fun `a control flush against the frame side has no inset`() {
        assertTrue("no-inset" in rules(window(button(101f, 80f))))
    }

    @Test
    fun `scroll inside scroll is an error unless excused`() {
        fun nested(allowed: Map<String, String>) = window(
            Node("ScrollComponent", Rect(110f, 80f, 530f, 300f), clip = Clip.SCROLL, children = listOf(
                Node("BrassTable", Rect(112f, 90f, 520f, 200f), widget = true, selfScroll = true, allowed = allowed),
            )),
        )
        val found = BrassLayoutRules.check(nested(emptyMap()))
        assertTrue(found.any { it.rule == "nested-scroll" && it.severity == Severity.ERROR })
        assertTrue(BrassLayoutRules.check(nested(mapOf("nested-scroll" to "fixed-height preview"))).none { it.rule == "nested-scroll" })
    }

    @Test
    fun `a text area in a scrolling form is only a warning`() {
        val tree = window(
            Node("ScrollComponent", Rect(110f, 80f, 530f, 300f), clip = Clip.SCROLL, children = listOf(
                Node("BrassTextArea", Rect(112f, 90f, 520f, 160f), widget = true, editor = true),
            )),
        )
        val found = BrassLayoutRules.check(tree)
        assertEquals(listOf(Severity.WARN), found.map { it.severity })
    }

    @Test
    fun `a widget leaving its panel escapes the card`() {
        val panel = Node("BrassPanel", Rect(110f, 80f, 300f, 160f), card = true, children = listOf(button(120f, 150f)))
        assertTrue("escapes-card" in rules(window(panel)))
    }

    @Test
    fun `a rolled-up frame's body is not audited`() {
        val collapsed = Node("Screen", screen, children = listOf(
            Node("BrassWindow", Rect(100f, 50f, 540f, 72f), layer = true, children = listOf(
                Node("content", Rect(100f, 70f, 540f, 70f), clip = Clip.SCISSOR, children = listOf(button(110f, 80f))),
            )),
        ))
        assertEquals(emptyList<String>(), rules(collapsed))
    }
}
