package net.swzo.brass.ui.kit.surface

import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.layout.BrassTreeGuides
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.layout.BrassVirtualList
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassTag
import net.swzo.brass.ui.kit.text.BrassTagStyle
import org.lwjgl.glfw.GLFW
import java.awt.Color

/**
 * A tree of [T] - a file browser, a config hierarchy, a nested data structure.
 * ```kotlin
 * val tree = BrassTreeView<File>(
 *     roots = listOf(project),
 *     childrenOf = { it.listFiles()?.toList() ?: emptyList() },
 *     label = { it.name },
 * )
 * ```
 * ### Flattened, not nested
 * A tree drawn as nested components costs one component per node whether or not it is on screen, and
 * a large tree makes that immediately obvious. This flattens the expanded nodes into a list of
 * `(item, depth)` rows and hands them to [BrassVirtualList], which paints only the visible ones - so
 * expanding a folder of ten thousand files is a `flatten` call, not ten thousand allocations.
 * The flatten is recomputed when the expansion state changes or [setRoots] is called, not per frame:
 * [childrenOf] may be expensive (a directory listing, a network call) and must not run sixty times a
 * second. Call [refresh] when the underlying data changes.
 * Indent guides and the expand glyph come from [BrassTreeGuides], shared with the dev inspector's own
 * tree so the two look identical rather than nearly so.
 * ### Unfolding
 * Expanding and collapsing animate, the way the dev inspector's tree does: new rows fade and rise into
 * place staggered down the branch, and a collapsing branch fades out *before* the rows are removed.
 * The two need opposite handling for the same reason they do in the inspector. An expand can rebuild
 * immediately - the new rows animate themselves in from nothing. A collapse cannot, because rebuilding
 * is precisely what destroys the rows that need to be seen leaving; so [toggle] defers the rebuild
 * until the fade has landed.
 * Where the inspector gets both for free - its rows are components, so [net.swzo.brass.ui.kit.base.BrassWidget]'s
 * own entrance cascade drives them - these rows are *painted*, so the same two effects are tracked here
 * per row against the clock and applied as an alpha and a vertical offset in [paintRow].
 */
class BrassTreeView<T>(
    private var roots: List<T> = emptyList(),
    /** A node's children. Called only during a flatten, never per frame. */
    private val childrenOf: (T) -> List<T>,
    private val label: (T) -> String,
    private val tag: ((T) -> Pair<String, BrassTagStyle>?)? = null,
    private val icon: ((T) -> BrassIcons.Icon?)? = null,
    rowHeight: Float = BrassFont.LINE + 6f,
    onSelect: ((T, Int) -> Unit)? = null,
) : BrassVirtualList<BrassTreeView.Node<T>>(rowHeight, wrap(onSelect)) {

    data class Node<T>(val item: T, val depth: Int, val hasChildren: Boolean)

    private val expanded = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<T, Boolean>())

    var onToggle: ((T, Boolean) -> Unit)? = null


    /**
     * Rows that appeared in the last [refresh], mapped to their ordinal within that batch - the
     * ordinal is what staggers the cascade so a branch unfolds top-down instead of all at once.
     * Identity-keyed for the same reason [expanded] is: two equal-but-distinct siblings must animate
     * as the separate rows they are.
     */
    private val entering = java.util.IdentityHashMap<T, Int>()
    private var enterStart = 0L

    private val leaving = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<T, Boolean>())
    private var leaveStart = 0L

    private var pendingCollapse: T? = null

    init {
        refresh()
        // The first flatten is the tree's initial state, not an expansion - without this every row
        // would cascade in on the frame the tree is first drawn, which reads as a loading screen.
        entering.clear()
    }

    fun setRoots(next: List<T>) {
        roots = next
        refresh()
    }

    fun refresh() {
        val before = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<T, Boolean>())
        items.forEach { before.add(it.item) }

        val out = ArrayList<Node<T>>()
        fun walk(item: T, depth: Int) {
            val kids = childrenOf(item)
            out.add(Node(item, depth, kids.isNotEmpty()))
            if (kids.isNotEmpty() && item in expanded) {
                for (child in kids) walk(child, depth + 1)
            }
        }
        roots.forEach { walk(it, 0) }

        entering.clear()
        var ordinal = 0
        for (node in out) {
            if (node.item !in before) entering[node.item] = ordinal++
        }
        enterStart = System.nanoTime()

        setItems(out)
    }

    fun isExpanded(item: T): Boolean = item in expanded

    fun toggle(item: T) {
        if (item in expanded) beginCollapse(item) else {
            expanded.add(item)
            onToggle?.invoke(item, true)
            refresh()
        }
    }

    private fun beginCollapse(item: T) {
        val index = items.indexOfFirst { it.item === item }
        if (index < 0) {
            // Not on screen - nothing to animate, so just collapse it.
            expanded.remove(item)
            onToggle?.invoke(item, false)
            refresh()
            return
        }
        val depth = items[index].depth
        leaving.clear()
        for (i in index + 1 until items.size) {
            if (items[i].depth <= depth) break
            leaving.add(items[i].item)
        }
        if (leaving.isEmpty()) {
            expanded.remove(item)
            onToggle?.invoke(item, false)
            refresh()
            return
        }
        pendingCollapse = item
        leaveStart = System.nanoTime()
    }

    private fun commitCollapse() {
        val item = pendingCollapse ?: return
        pendingCollapse = null
        leaving.clear()
        expanded.remove(item)
        onToggle?.invoke(item, false)
        refresh()
        // A collapse reveals nothing new - anything the diff flagged is a row that merely moved up.
        entering.clear()
    }

    fun expandTo(depth: Int) {
        pendingCollapse = null
        leaving.clear()
        expanded.clear()
        fun walk(item: T, at: Int) {
            if (at >= depth) return
            expanded.add(item)
            childrenOf(item).forEach { walk(it, at + 1) }
        }
        roots.forEach { walk(it, 0) }
        refresh()
    }

    override fun onRowClick(index: Int, localX: Float, button: Int): Boolean {
        val node = items[index]
        if (!node.hasChildren) return false
        // A click on the glyph column toggles; anywhere else selects, as the inspector's tree does.
        val glyphStart = BrassTreeGuides.contentX(0f, node.depth)
        if (localX >= glyphStart && localX <= glyphStart + BrassTreeGuides.GLYPH_W) {
            toggle(node.item)
            return true
        }
        return false
    }

    override fun onKeyPressed(keyCode: Int): Boolean {
        val node = items.getOrNull(selectedIndex) ?: return false
        return when (keyCode) {
            GLFW.GLFW_KEY_UP -> { moveSelection(-1); true }
            GLFW.GLFW_KEY_DOWN -> { moveSelection(1); true }
            GLFW.GLFW_KEY_LEFT -> {
                if (node.hasChildren && isExpanded(node.item)) toggle(node.item) else moveToParent(node)
                true
            }
            GLFW.GLFW_KEY_RIGHT -> {
                if (node.hasChildren && !isExpanded(node.item)) toggle(node.item) else moveSelection(1)
                true
            }
            else -> false
        }
    }

    private fun moveToParent(node: Node<T>) {
        for (i in selectedIndex - 1 downTo 0) {
            if (items[i].depth < node.depth) { select(i); scrollTo(i); return }
        }
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun drawContent(matrixStack: UMatrixStack, bx: Int, by: Int, bw: Int, bh: Int) {
        if (pendingCollapse != null && System.nanoTime() - leaveStart >= LEAVE_NANOS) commitCollapse()
        super.drawContent(matrixStack, bx, by, bw, bh)
    }

    private fun rowAnim(item: T): FloatArray {
        if (item in leaving) {
            val t = ((System.nanoTime() - leaveStart).toFloat() / LEAVE_NANOS).coerceIn(0f, 1f)
            return floatArrayOf(1f - t, 0f)
        }
        val ordinal = entering[item] ?: return SETTLED
        val elapsed = (System.nanoTime() - enterStart).toFloat() / 1_000_000_000f
        val t = ((elapsed - delayFor(ordinal)) / ENTER_SECONDS).coerceIn(0f, 1f)
        if (t >= 1f) {
            // Done: drop it so a settled tree does no per-row work at all.
            entering.remove(item)
            return SETTLED
        }
        // Alpha runs at twice the rate so a row is legible well before it stops moving - the same
        // shape BrassWidget's entrance uses, and why the two read as one animation.
        val eased = 1f - (1f - t) * (1f - t) * (1f - t)
        return floatArrayOf((t * 2f).coerceAtMost(1f), (1f - eased) * ENTER_RISE)
    }

    override fun paintRow(m: UMatrixStack, item: Node<T>, index: Int, x: Float, y0: Float, w: Float) {
        val anim = rowAnim(item.item)
        val alpha = anim[0]
        if (alpha <= 0.01f) return
        // Positive rise sits the row *below* its resting place and decays to zero, so it travels
        // upward into position - the direction BrassWidget's entrance moves.
        val y = y0 + anim[1]

        BrassTreeGuides.drawGuides(m, x, y, rowHeight, item.depth, alpha)

        if (item.hasChildren) {
            BrassTreeGuides.drawGlyph(
                m, this, x, y, rowHeight, item.depth,
                expanded = isExpanded(item.item),
                color = Colors.UI_TEXT_DARK,
                alpha = alpha,
            )
        }

        var cx = BrassTreeGuides.labelX(x, item.depth, item.hasChildren)
        val ty = y + (rowHeight - BrassFont.LINE) / 2f

        icon?.invoke(item.item)?.let { glyph ->
            BrassIcons.draw(m, glyph, cx, y + (rowHeight - ICON) / 2f, ICON, fade(Colors.UI_TEXT_DARK, alpha))
            cx += ICON + 3f
        }

        val text = label(item.item)
        val tint: Color = if (index == selectedIndex) Colors.UI_ACCENT_BRIGHT else Colors.UI_TEXT
        BrassFont.draw(m, this, text, cx, ty, fade(tint, alpha), false)

        tag?.invoke(item.item)?.let { (tagLabel, style) ->
            BrassTag.drawPill(
                m, this,
                cx + BrassFont.width(this, text) + 6f,
                y + (rowHeight - BrassTag.HEIGHT) / 2f,
                tagLabel, style.color, alpha,
            )
        }
    }

    /**
     * A row mid-animation must not wear the selection or hover wash at full strength while its own ink
     * is still fading in - the band would arrive a frame before the text it belongs to.
     */
    override fun rowBackground(index: Int): Color? {
        val base = super.rowBackground(index) ?: return null
        val item = items.getOrNull(index)?.item ?: return base
        val alpha = rowAnimAlpha(item)
        return if (alpha >= 1f) base else fade(base, alpha)
    }

    private fun rowAnimAlpha(item: T): Float {
        if (item in leaving) {
            return 1f - ((System.nanoTime() - leaveStart).toFloat() / LEAVE_NANOS).coerceIn(0f, 1f)
        }
        val ordinal = entering[item] ?: return 1f
        val elapsed = (System.nanoTime() - enterStart).toFloat() / 1_000_000_000f
        val t = ((elapsed - delayFor(ordinal)) / ENTER_SECONDS).coerceIn(0f, 1f)
        return (t * 2f).coerceAtMost(1f)
    }

    private fun delayFor(ordinal: Int): Float = (ordinal * STAGGER).coerceAtMost(MAX_DELAY)

    private fun fade(c: Color, alpha: Float): Color =
        if (alpha >= 1f) c else Color(c.red, c.green, c.blue, (c.alpha * alpha.coerceIn(0f, 1f)).toInt())

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("tree-view", "Tree view", 230f, 150f) {
            val tree = BrassTreeView(
                roots = listOf(DEMO_TREE),
                childrenOf = { it.children },
                label = { it.name },
            )
            // Roots start closed so the expansion has somewhere to go; a demo that opens already
            // expanded has nothing left to demonstrate.
            tree
        }

        private class DemoNode(val name: String, val children: List<DemoNode> = emptyList())

        // Held as named properties, not built inline, because the script has to toggle *these exact
        // objects*: the tree tracks expansion by identity, so a second equal node would not match.
        private val ASSETS = DemoNode(
            "assets",
            listOf(DemoNode("icons.png"), DemoNode("palette.json"), DemoNode("font.ttf")),
        )

        private val DEMO_TREE = DemoNode(
            "project",
            listOf(
                ASSETS,
                DemoNode("src", listOf(DemoNode("main.kt"), DemoNode("theme.kt"))),
                DemoNode("README.md"),
            ),
        )

        // Private individually rather than on the companion, which has to be public now that it
        // carries the demo. Same visibility as before for everything below.

        private const val ICON = 8f

        private const val ENTER_SECONDS = 0.13f
        private const val ENTER_RISE = 4f
        private const val STAGGER = 0.018f
        /** Longest any one row waits its turn - a deep branch must not crawl. */
        private const val MAX_DELAY = 0.12f
        private const val LEAVE_NANOS = 150_000_000L

        private val SETTLED = floatArrayOf(1f, 0f)

        private fun <T> wrap(onSelect: ((T, Int) -> Unit)?): ((Node<T>, Int) -> Unit)? =
            onSelect?.let { inner -> { node, index -> inner(node.item, index) } }
    }
}
