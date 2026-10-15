package net.swzo.brass.ui.kit.dev

import net.swzo.brass.ui.kit.base.BrassWidget

object BrassLayoutRules {

    const val MIN_INSET = 4f
    private const val EPS = 0.5f

    enum class Severity { ERROR, WARN }

    data class Finding(val rule: String, val severity: Severity, val where: String, val detail: String) {
        override fun toString() = "[$severity] $rule  $where  $detail"
    }

    data class Rect(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
        val empty: Boolean get() = x2 - x1 <= EPS || y2 - y1 <= EPS
        fun intersect(o: Rect) = Rect(maxOf(x1, o.x1), maxOf(y1, o.y1), minOf(x2, o.x2), minOf(y2, o.y2))
        fun inset(d: Float) = Rect(x1 + d, y1 + d, x2 - d, y2 - d)
        override fun toString() = "(${x1.fmt()},${y1.fmt()} ${(x2 - x1).fmt()}x${(y2 - y1).fmt()})"
        private fun Float.fmt() = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)
    }

    enum class Clip { NONE, SCROLL, SCISSOR }

    class Node(
        val name: String,
        val box: Rect,
        val widget: Boolean = false,
        val bleeds: Boolean = false,
        val clip: Clip = Clip.NONE,
        val selfScroll: Boolean = false,
        val editor: Boolean = false,
        val card: Boolean = false,
        val insetRef: Rect? = null,
        val layer: Boolean = false,
        val allowed: Map<String, String> = emptyMap(),
        val children: List<Node> = emptyList(),
    ) {
        val paint: Rect
            get() = if (bleeds) Rect(
                box.x1 - BrassWidget.BLEED_X, box.y1 - BrassWidget.BLEED_TOP,
                box.x2 + BrassWidget.BLEED_X, box.y2 + BrassWidget.BLEED_BOTTOM,
            ) else box
    }

    private class Ctx(
        val clip: Rect,
        val clipKind: Clip,
        val inScroll: Boolean,
        val card: Rect?,
        val frame: Rect?,
        val layer: Node,
        val path: List<String>,
    )

    private class Placed(val node: Node, val visible: Rect, val layer: Node, val ancestors: Set<Node>, val where: String)

    fun check(root: Node): List<Finding> {
        val out = ArrayList<Finding>()
        val placed = ArrayList<Placed>()
        val screen = root.box

        fun visit(n: Node, ctx: Ctx, ancestors: Set<Node>) {
            val path = ctx.path + n.name
            val where = path.takeLast(3).joinToString(">") + " " + n.box
            fun report(rule: String, sev: Severity, detail: String) {
                val excuse = n.allowed[rule]
                if (excuse == null) out += Finding(rule, sev, where, detail)
            }

            if (n.widget && !n.box.empty) {
                val p = n.paint
                val c = ctx.clip
                val outX = p.x1 < c.x1 - EPS || p.x2 > c.x2 + EPS
                val outY = p.y1 < c.y1 - EPS || p.y2 > c.y2 + EPS
                if (outX || (ctx.clipKind != Clip.SCROLL && outY)) {
                    report("escapes-clip", Severity.ERROR, "paints $p outside ${ctx.clipKind.name.lowercase()} $c")
                }
                ctx.card?.let { card ->
                    if (p.x1 < card.x1 - EPS || p.x2 > card.x2 + EPS || p.y1 < card.y1 - EPS || p.y2 > card.y2 + EPS) {
                        report("escapes-card", Severity.ERROR, "paints $p outside panel $card")
                    }
                }
                ctx.frame?.let { f ->
                    val v = n.box.intersect(ctx.clip)
                    if (!v.empty && (v.x1 - f.x1 < MIN_INSET - EPS || f.x2 - v.x2 < MIN_INSET - EPS || f.y2 - v.y2 < MIN_INSET - EPS)) {
                        report("no-inset", Severity.ERROR, "sits ${minOf(v.x1 - f.x1, f.x2 - v.x2, f.y2 - v.y2).toInt()} px from frame edge $f")
                    }
                }
                val visible = n.box.intersect(ctx.clip)
                if (!visible.empty) placed += Placed(n, visible, ctx.layer, ancestors, where)
            }

            if ((n.clip == Clip.SCROLL || n.selfScroll) && ctx.inScroll) {
                report("nested-scroll", Severity.ERROR, "vertical scroll inside another scroll view")
            } else if (n.editor && ctx.inScroll) {
                report("nested-editor", Severity.WARN, "multi-line editor inside a scroll view")
            }

            val clipRect = if (n.clip != Clip.NONE) ctx.clip.intersect(n.box) else ctx.clip
            if (n.clip != Clip.NONE && clipRect.empty) return
            val next = Ctx(
                clip = clipRect,
                clipKind = if (n.clip != Clip.NONE) n.clip else ctx.clipKind,
                inScroll = ctx.inScroll || n.clip == Clip.SCROLL,
                card = when {
                    n.card -> n.box.inset(1f)
                    n.clip != Clip.NONE -> null
                    else -> ctx.card
                },
                frame = when {
                    n.insetRef != null -> n.insetRef
                    n.card || n.widget -> null
                    else -> ctx.frame
                },
                layer = if (n.layer) n else ctx.layer,
                path = path,
            )
            val anc = ancestors + n
            for (child in n.children) visit(child, next, anc)
        }

        visit(root, Ctx(screen, Clip.SCISSOR, false, null, null, root, emptyList()), emptySet())

        for (i in placed.indices) {
            val a = placed[i]
            for (j in i + 1 until placed.size) {
                val b = placed[j]
                if (a.layer !== b.layer) continue
                if (a.node in b.ancestors || b.node in a.ancestors) continue
                val o = a.visible.intersect(b.visible)
                if (o.x2 - o.x1 > 1f && o.y2 - o.y1 > 1f) {
                    if ("overlap" in a.node.allowed || "overlap" in b.node.allowed) continue
                    out += Finding("overlap", Severity.ERROR, a.where, "overlaps ${b.where}")
                }
            }
        }
        return out
    }
}
