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
 *
 * ```kotlin
 * val tree = BrassTreeView<File>(
 *     roots = listOf(project),
 *     childrenOf = { it.listFiles()?.toList() ?: emptyList() },
 *     label = { it.name },
 * )
 * ```
 *
 * ### Flattened, not nested
 *
 * A tree drawn as nested components costs one component per node whether or not it is on screen, and
 * a large tree makes that immediately obvious. This flattens the expanded nodes into a list of
 * `(item, depth)` rows and hands them to [BrassVirtualList], which paints only the visible ones - so
 * expanding a folder of ten thousand files is a `flatten` call, not ten thousand allocations.
 *
 * The flatten is recomputed when the expansion state changes or [setRoots] is called, not per frame:
 * [childrenOf] may be expensive (a directory listing, a network call) and must not run sixty times a
 * second. Call [refresh] when the underlying data changes.
 *
 * Indent guides and the expand glyph come from [BrassTreeGuides], shared with the dev inspector's own
 * tree so the two look identical rather than nearly so.
 *
 * ### Unfolding
 *
 * Expanding and collapsing animate, the way the dev inspector's tree does: new rows fade and rise into
 * place staggered down the branch, and a collapsing branch fades out *before* the rows are removed.
 *
 * The two need opposite handling for the same reason they do in the inspector. An expand can rebuild
 * immediately - the new rows animate themselves in from nothing. A collapse cannot, because rebuilding
 * is precisely what destroys the rows that need to be seen leaving; so [toggle] defers the rebuild
 * until the fade has landed.
 *
 * Where the inspector gets both for free - its rows are components, so [net.swzo.brass.ui.kit.base.BrassWidget]'s
 * own entrance cascade drives them - these rows are *painted*, so the same two effects are tracked here
 * per row against the clock and applied as an alpha and a vertical offset in [paintRow].
 */
class BrassTreeView<T>(
    roots: List<T> = emptyList(),
    /** A node's children. Called only during a flatten, never per frame. */
    private val childrenOf: (T) -> List<T>,
    /** The text for a node. */
    private val label: (T) -> String,
    /** An optional tag drawn after the label - a type, a count, a status. */
    private val tag: ((T) -> Pair<String, BrassTagStyle>?)? = null,
    /** An optional icon drawn before the label. */
    private val icon: ((T) -> BrassIcons.Icon?)? = null,
    rowHeight: Float = BrassFont.LINE + 6f,
    onSelect: ((T, Int) -> Unit)? = null,
) : BrassVirtualList<BrassTreeView.Node<T>>(rowHeight, wrap(onSelect)) {

    /** One flattened row: the item, how deep it sits, and whether it has children. */
    data class Node<T>(val item: T, val depth: Int, val hasChildren: Boolean)

    private var roots: List<T> = roots

    /**
     * Which nodes are expanded, by identity.
     *
     * By **identity**, not equality: two sibling files with the same name are equal to a data class
     * and would expand and collapse together. Identity is what the caller's object graph actually
     * distinguishes.
     */
    private val expanded = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<T, Boolean>())

    /** Called when a node is expanded or collapsed - for lazily loading children, say. */
    var onToggle: ((T, Boolean) -> Unit)? = null

    // ---- unfolding animation ---------------------------------------------------------------------

    /**
     * Rows that appeared in the last [refresh], mapped to their ordinal within that batch - the
     * ordinal is what staggers the cascade so a branch unfolds top-down instead of all at once.
     *
     * Identity-keyed for the same reason [expanded] is: two equal-but-distinct siblings must animate
     * as the separate rows they are.
     */
    private val entering = java.util.IdentityHashMap<T, Int>()
    private var enterStart = 0L

    /** Rows fading out ahead of a deferred collapse, and when that fade began. */
    private val leaving = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<T, Boolean>())
    private var leaveStart = 0L

    /** The node whose collapse is waiting on [leaving] to finish fading. */
    private var pendingCollapse: T? = null

    init {
        refresh()
        // The first flatten is the tree's initial state, not an expansion - without this every row
        // would cascade in on the frame the tree is first drawn, which reads as a loading screen.
        entering.clear()
    }

    /** Replace the roots and rebuild. */
    fun setRoots(next: List<T>) {
        roots = next
        refresh()
    }

    /**
     * Rebuild the flattened rows. Call when the data behind [childrenOf] has changed.
     *
     * Rows that were not on screen before are marked [entering], so they cascade in rather than
     * appearing between two frames. A rebuild that adds nothing - a [setRoots] to the same shape, a
     * data refresh - therefore animates nothing, which is the behaviour that keeps the periodic case
     * from shimmering.
     */
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

    /**
     * Expand or collapse [item].
     *
     * An expand rebuilds at once; a collapse only *starts* - the branch fades first and the rows are
     * removed when it lands. See the class docs.
     */
    fun toggle(item: T) {
        if (item in expanded) beginCollapse(item) else {
            expanded.add(item)
            onToggle?.invoke(item, true)
            refresh()
        }
    }

    /**
     * Mark [item]'s visible descendants as leaving and start their fade. The rows stay in the list,
     * and [commitCollapse] removes them once the fade is done.
     *
     * The descendants are read off the *current* flattened rows - everything below [item] until the
     * depth returns to its own - rather than re-walked through [childrenOf], which may be expensive
     * and, for a lazily-loaded tree, may not even answer the same way twice.
     */
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

    /** Finish a deferred collapse: drop the faded rows and rebuild. */
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

    /** Expand every node down to [depth] levels. Depth 0 collapses everything. */
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

    /**
     * Left and right collapse and expand the selected node, as every tree control does - right on an
     * already-expanded node steps into its first child instead, which is what makes a tree navigable
     * from the keyboard without reaching for the mouse.
     */
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

    /** Select the nearest row above that sits one level shallower. */
    private fun moveToParent(node: Node<T>) {
        for (i in selectedIndex - 1 downTo 0) {
            if (items[i].depth < node.depth) { select(i); scrollTo(i); return }
        }
    }

    /** Land a deferred collapse before the frame that would otherwise draw it half-faded forever. */
    override fun drawContent(matrixStack: UMatrixStack, bx: Int, by: Int, bw: Int, bh: Int) {
        if (pendingCollapse != null && System.nanoTime() - leaveStart >= LEAVE_NANOS) commitCollapse()
        super.drawContent(matrixStack, bx, by, bw, bh)
    }

    /**
     * How visible a row is and how far it still has to rise, as `[alpha, riseY]`.
     *
     * `[1, 0]` - fully in place - for the overwhelming majority of rows, which is the case worth
     * keeping cheap: this runs per visible row per frame.
     */
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

    /** [rowAnim]'s alpha alone, without disturbing its bookkeeping - see [rowBackground]. */
    private fun rowAnimAlpha(item: T): Float {
        if (item in leaving) {
            return 1f - ((System.nanoTime() - leaveStart).toFloat() / LEAVE_NANOS).coerceIn(0f, 1f)
        }
        val ordinal = entering[item] ?: return 1f
        val elapsed = (System.nanoTime() - enterStart).toFloat() / 1_000_000_000f
        val t = ((elapsed - delayFor(ordinal)) / ENTER_SECONDS).coerceIn(0f, 1f)
        return (t * 2f).coerceAtMost(1f)
    }

    /**
     * How long the row at [ordinal] down an unfolding branch waits before it starts, capped so a deep
     * branch cascades rather than crawling - the same trade `BrassWidget.ENTRANCE_DELAY_MAX` makes.
     */
    private fun delayFor(ordinal: Int): Float = (ordinal * STAGGER).coerceAtMost(MAX_DELAY)

    private fun fade(c: Color, alpha: Float): Color =
        if (alpha >= 1f) c else Color(c.red, c.green, c.blue, (c.alpha * alpha.coerceIn(0f, 1f)).toInt())

    companion object : BrassDemoSource {

        /**
         * A folder tree, expanded and collapsed a level at a time.
         *
         * ### Why the demo declares a whole node graph
         *
         * A tree with one level of children is a list with indentation. The behaviours worth showing —
         * the guide lines connecting a child to its parent, the staggered entrance as a subtree
         * appears, the chevron flipping — only become legible at two levels with siblings around them,
         * so the demo builds a small but *real* hierarchy: nested folders, loose files, and a leaf that
         * sits beside a folder at the same depth.
         *
         * ### Why expansion is the animation
         *
         * Rows do not merely appear when a folder opens; they cascade, each delayed slightly behind
         * the one above. That stagger is the tree's signature and it is entirely invisible in a still,
         * so the main scene opens a folder, opens one of its children, and then collapses the lot —
         * the reverse being worth seeing too, since a collapse animates rather than cutting.
         */
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

        /** A node of the demo's sample hierarchy. */
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

        // ---- widget internals ------------------------------------------------------------------
        //
        // Private individually rather than on the companion, which has to be public now that it
        // carries the demo. Same visibility as before for everything below.

        private const val ICON = 8f

        /** How long one row takes to fade and rise into place, in seconds. */
        private const val ENTER_SECONDS = 0.13f
        /** How far a row rises over that time. Matches `BrassWidget.ENTRANCE_RISE`. */
        private const val ENTER_RISE = 4f
        /** Delay added per row down an unfolding branch, so it cascades rather than popping. */
        private const val STAGGER = 0.018f
        /** Longest any one row waits its turn - a deep branch must not crawl. */
        private const val MAX_DELAY = 0.12f
        /** How long a collapsing branch fades before its rows are removed, in nanoseconds. */
        private const val LEAVE_NANOS = 150_000_000L

        /** The `[alpha, rise]` every settled row shares, so the common case allocates nothing. */
        private val SETTLED = floatArrayOf(1f, 0f)

        /** Adapt the caller's `(T, Int)` callback to the list's `(Node<T>, Int)` one. */
        private fun <T> wrap(onSelect: ((T, Int) -> Unit)?): ((Node<T>, Int) -> Unit)? =
            onSelect?.let { inner -> { node, index -> inner(node.item, index) } }
    }
}
