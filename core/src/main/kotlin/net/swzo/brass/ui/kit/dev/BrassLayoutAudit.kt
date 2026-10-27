package net.swzo.brass.ui.kit.dev

import net.swzo.brass.ui.kit.base.BrassTree

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

    fun snapshot(c: UIComponent): Node? = snapshot(c, c, Rect(c.getLeft(), c.getTop(), c.getRight(), c.getBottom()))

    fun report(findings: List<Finding>): String =
        if (findings.isEmpty()) "layout audit: clean"
        else buildString {
            val errors = findings.count { it.severity == Severity.ERROR }
            append("layout audit: $errors error(s), ${findings.size - errors} warning(s)\n")
            findings.forEach { append("  ").append(it).append('\n') }
        }

    private fun snapshot(c: UIComponent, root: UIComponent, clip: Rect): Node? {
        if (c is BrassInert && c.inert) return null
        val w = c as? BrassWidget
        val box = Rect(c.getLeft(), c.getTop(), c.getRight(), c.getBottom())
        val inner = if (c is ScrollComponent || c.effects.any { it is ScissorEffect }) clip.intersect(box) else clip
        val kids = c.children.mapNotNull { snapshot(it, root, inner) }
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
            layer = isLayer(c),
            allowed = excuses[c] ?: emptyMap(),
            blockedBy = if (w != null && w.clickable && w.active) blocker(w, root, box.intersect(clip)) else null,
            children = kids,
        )
    }

    private fun isLayer(c: UIComponent) = c is BrassFrame || c is BrassLayers.Layer || c is BrassContextMenu

    /** The nearest layer holding [c] - itself included (a click can land right on a modal's scrim). */
    private fun layerOf(c: UIComponent): UIComponent? = if (isLayer(c)) c else BrassTree.ancestors(c).firstOrNull { isLayer(it) }

    /**
     * Click [c] where it is visible, the way Elementa routes a real click (topmost child whose
     * `isPointInside` holds, descending only into parents that contain the point). Returns what the click
     * would land on instead, when that is not [c] or inside it - and only when it is no higher a layer: a
     * modal's scrim covering the screen behind it is meant to.
     */
    private fun blocker(c: UIComponent, root: UIComponent, visible: Rect): String? {
        if (visible.empty) return null
        val x = (visible.x1 + visible.x2) / 2f
        val y = (visible.y1 + visible.y2) / 2f
        val hit = root.hitTest(x, y)
        if (hit === c || BrassTree.isDescendantOf(hit, c)) return null
        val hitLayer = layerOf(hit)
        if (hitLayer != null && hitLayer !== layerOf(c) && !BrassTree.isDescendantOf(c, hitLayer)) return null
        val name = hit.javaClass.simpleName.ifEmpty { hit.javaClass.superclass?.simpleName ?: "?" }
        val parent = hit.parent.takeIf { hit.hasParent }?.javaClass?.simpleName?.ifEmpty { null }
        return (if (parent != null) "$parent>$name" else name) + " " + Rect(hit.getLeft(), hit.getTop(), hit.getRight(), hit.getBottom())
    }
}
