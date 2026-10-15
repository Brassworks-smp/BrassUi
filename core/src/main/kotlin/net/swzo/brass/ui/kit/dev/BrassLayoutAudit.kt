package net.swzo.brass.ui.kit.dev

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.effects.ScissorEffect
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassFrame
import net.swzo.brass.ui.kit.base.BrassInert
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.layout.BrassVirtualList
import net.swzo.brass.ui.kit.surface.BrassChat
import net.swzo.brass.ui.kit.surface.BrassContextMenu
import net.swzo.brass.ui.kit.surface.BrassFrameBase
import net.swzo.brass.ui.kit.surface.BrassLayers
import net.swzo.brass.ui.kit.surface.BrassPanel
import net.swzo.brass.ui.kit.text.BrassTextArea
import net.swzo.brass.ui.kit.dev.BrassLayoutRules.Clip
import net.swzo.brass.ui.kit.dev.BrassLayoutRules.Finding
import net.swzo.brass.ui.kit.dev.BrassLayoutRules.Node
import net.swzo.brass.ui.kit.dev.BrassLayoutRules.Rect
import net.swzo.brass.ui.kit.dev.BrassLayoutRules.Severity
import java.util.WeakHashMap

object BrassLayoutAudit {

    private val excuses = WeakHashMap<UIComponent, MutableMap<String, String>>()

    @JvmStatic
    fun allow(component: UIComponent, rule: String, reason: String) {
        excuses.getOrPut(component) { HashMap() }[rule] = reason
    }

    @JvmStatic
    fun run(root: UIComponent): List<Finding> = BrassLayoutRules.check(snapshot(root) ?: return emptyList())

    fun report(findings: List<Finding>): String =
        if (findings.isEmpty()) "layout audit: clean"
        else buildString {
            val errors = findings.count { it.severity == Severity.ERROR }
            append("layout audit: $errors error(s), ${findings.size - errors} warning(s)\n")
            findings.forEach { append("  ").append(it).append('\n') }
        }

    fun snapshot(c: UIComponent): Node? {
        if (c is BrassInert && c.inert) return null
        val w = c as? BrassWidget
        val kids = c.children.mapNotNull { snapshot(it) }
        return Node(
            name = c.javaClass.simpleName.ifEmpty { c.javaClass.superclass?.simpleName ?: "?" },
            box = Rect(c.getLeft(), c.getTop(), c.getRight(), c.getBottom()),
            widget = w != null,
            bleeds = w != null && w.chrome == BrassChrome.KEYCAP,
            clip = when {
                c is ScrollComponent -> Clip.SCROLL
                c.effects.any { it is ScissorEffect } -> Clip.SCISSOR
                else -> Clip.NONE
            },
            selfScroll = c is BrassVirtualList<*>,
            editor = c is BrassTextArea || c is BrassChat,
            card = c is BrassPanel,
            insetRef = (c.parent as? BrassFrameBase)?.takeIf { c.hasParent && it.content === c }
                ?.let { f -> Rect(f.getLeft(), c.getTop(), f.getRight(), f.getBottom()) },
            layer = c is BrassFrame || c is BrassLayers.Layer || c is BrassContextMenu,
            allowed = excuses[c] ?: emptyMap(),
            children = kids,
        )
    }

}
