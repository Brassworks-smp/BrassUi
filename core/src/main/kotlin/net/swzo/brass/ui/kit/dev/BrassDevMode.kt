package net.swzo.brass.ui.kit.dev

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.dsl.*
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.dev.BrassDevMode.enabled
import net.swzo.brass.ui.kit.dev.BrassDevMode.inspect
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.surface.BrassContextMenu
import net.swzo.brass.ui.kit.surface.BrassTooltip
import net.swzo.brass.ui.kit.text.BrassFont
import java.awt.Color
import java.util.*
import kotlin.math.roundToInt

/**
 * The toolkit's UI inspector - toggle with **Ctrl+Shift+D** on any [net.swzo.brass.ui.BrassScreen].
 *
 * When on, it **docks a panel to the right edge** and shrinks the main content to fit beside it, exactly
 * like a browser's dev tools. The panel ([BrassDevPanel]) carries a compact perf readout, the full UI
 * tree (colour-tagged, expand/collapse, click to select) and a details pane for the selected element.
 * On top of the finished frame it draws a **Chrome-dev-tools box model** for the element under the
 * cursor or the hovered tree row - content in blue, padding in green, keycap bleed as margin in orange -
 * plus a metadata tooltip.
 *
 * Everything is a real widget under a [BrassDevLayer], which pauses [BrassStats] around its draw, and
 * the stats walk skips [BrassDevOverlay] subtrees - so opening the inspector never moves the numbers it
 * reports.
 */
object BrassDevMode {

    var enabled: Boolean = false
        private set

    /**
     * Whether every widget draws its box outline as it paints.
     *
     * On by default - seeing the whole layout at once is most of the point - but it makes text hard to
     * read and hides subtle spacing problems behind a grid of blue lines, so it can be turned off to
     * inspect one element (whose box model still draws) against the real UI.
     */
    var showOutlines: Boolean = true
        private set

    /**
     * Element-picker mode: the next click anywhere in the UI selects whatever is under the cursor
     * instead of (well, as well as) activating it - Chrome's inspect-element arrow.
     */
    var picking: Boolean = false
        private set

    fun toggleOutlines(): Boolean { showOutlines = !showOutlines; return showOutlines }
    fun togglePicking(): Boolean { picking = !picking; return picking }

    // ---- inspected / selected component ----------------------------------------------------------

    /** Widget under the cursor this frame (set by [inspect]); recomputed every frame. */
    private var hovered: UIComponent? = null
    /** Component under the hovered tree row; persists until the row is left. */
    private var treeHighlight: UIComponent? = null

    /** The clicked-and-pinned element the details pane describes. */
    var selectedComponent: UIComponent? = null
        private set

    /** Per-node collapse state for the tree (identity-keyed; absent = expanded). */
    private val collapsed: MutableSet<UIComponent> =
        Collections.newSetFromMap(IdentityHashMap())

    private fun target(): UIComponent? = treeHighlight ?: hovered

    fun setTreeHighlight(c: UIComponent) { treeHighlight = c }
    fun clearTreeHighlight(c: UIComponent) { if (treeHighlight === c) treeHighlight = null }
    fun select(c: UIComponent) { selectedComponent = c }

    /**
     * Select [c] **and reveal it**: every ancestor is expanded and the tree rebuilt, so a component
     * picked out of the live UI actually appears in the tree rather than being selected inside a
     * collapsed branch where nothing visibly happens.
     */
    fun reveal(c: UIComponent) {
        selectedComponent = c
        var node = c
        while (true) {
            val p = node.parent
            if (p === node) break
            collapsed.remove(p)
            node = p
        }
        panel?.markTreeDirty()
    }

    fun isCollapsed(c: UIComponent): Boolean = collapsed.contains(c)
    /**
     * Fold or unfold [c]'s branch.
     *
     * Unfolding rebuilds at once and the new rows animate themselves in. Folding hands off to the tree,
     * which fades the branch out *before* rebuilding - rebuilding first would delete the rows that need
     * to be seen leaving.
     */
    fun toggleCollapse(c: UIComponent) {
        val nowCollapsed = !collapsed.remove(c)
        if (nowCollapsed) {
            collapsed.add(c)
            panel?.beginTreeCollapse(c)
        } else {
            panel?.markTreeDirtyAnimated()
        }
    }

    // ---- docking ---------------------------------------------------------------------------------

    private var host: UIComponent? = null
    private var content: UIComponent? = null
    private var layer: BrassDevLayer? = null
    private var panel: BrassDevPanel? = null

    /** Drop every reference to the current screen - called by [net.swzo.brass.ui.BrassScreen] on close. */
    fun forgetScreen() = detach()

    fun toggle(): Boolean {
        enabled = !enabled
        hovered = null
        treeHighlight = null
        picking = false
        if (!enabled) selectedComponent = null
        return enabled
    }

    /** Roots that already carry the element-picker click handler. Weak: a root dies with its screen. */
    private val pickWired = java.util.WeakHashMap<UIComponent, Boolean>()

    /**
     * Install the picker's click handler on [root], once per root.
     *
     * Listening at the root means the click still reaches whatever it landed on, so picking a button
     * also presses it. Chrome swallows the click; doing the same here would need a full-screen scrim,
     * which brings its own problems (see [BrassContextMenu] for that story). Picking is a deliberate,
     * momentary mode, so the extra activation is the cheaper trade.
     */
    private fun wirePicker(root: UIComponent) {
        if (pickWired.put(root, true) != null) return
        root.onMouseClick {
            if (!enabled || !picking) return@onMouseClick
            hovered?.let { reveal(it) }
            picking = false
        }
    }

    /**
     * Attach or detach the docked panel to match [enabled], keeping it bound to the current screen.
     * [window] is the Elementa root (the panel docks to it); [main] is the content the panel shrinks to
     * make room. Called once per frame before the tree draws; cheap when nothing changed.
     */
    fun sync(window: UIComponent, main: UIComponent) {
        // The dock slides in from the right and the content squeezes over to meet it, both driven by
        // one eased value. Detaching happens only once the panel has finished sliding *out*, so
        // closing the inspector is as visible as opening it.
        dockValue.target = if (enabled) 1f else 0f
        dockValue.advance()

        if (enabled) {
            if (host !== window) { detach(); attach(window, main) }
            wirePicker(window)
            panel?.bind(window)
        } else if (host != null && dock <= 0.02f) {
            detach()
        }

        // A selection pinned from a screen that has since closed is a strong static reference to that
        // screen's whole tree. Nothing clears it on close - `toggle()` is the only other path, and the
        // user has no reason to press it on the way out - so it is dropped here the moment the
        // component stops being part of a live tree.
        selectedComponent?.let { if (!BrassTree.isAttached(it)) selectedComponent = null }
    }

    /** Eased 0..1 dock position: 0 = fully off the right edge, 1 = fully docked. */
    private val dockValue = BrassEased(0f, speed = DOCK_SPEED)
    private val dock: Float get() = dockValue.value

    /** How far the panel has slid in, for the layout constraints that follow it. */
    private fun dockOffset(): Float = (BrassDevPanel.WIDTH + BrassDevPanel.MARGIN * 2f) * (1f - dock)

    private fun attach(window: UIComponent, main: UIComponent) {
        // Shrink the main content so the panel isn't drawn over it - it sits *beside* it. The reserved
        // strip is the panel plus the margin on both sides of it, so the card floats clear of the
        // screen edge and clear of the UI rather than being welded to either.
        //
        // Both this and the panel's own x read the animated `dock`, so the content squeezes over at
        // exactly the rate the panel slides in and the two never overlap mid-animation.
        val gutter = BrassDevPanel.WIDTH + BrassDevPanel.MARGIN * 2f
        main.constrain { width = basicWidthConstraint { c -> c.parent.getWidth() - gutter + dockOffset() } }

        val l = BrassDevLayer().constrain {
            x = basicXConstraint { c ->
                c.parent.getRight() - BrassDevPanel.MARGIN - BrassDevPanel.WIDTH + dockOffset()
            }
            y = BrassDevPanel.MARGIN.pixels()
            width = BrassDevPanel.WIDTH.pixels()
            height = 100.percent() - (BrassDevPanel.MARGIN * 2f).pixels()
        } childOf window
        val p = BrassDevPanel(window).constrain { width = 100.percent(); height = 100.percent() } childOf l

        host = window; content = main; layer = l; panel = p
    }

    private fun detach() {
        // the panel is going away, so it can no longer be what is holding tooltips back
        BrassTooltip.gate = null
        content?.constrain { width = 100.percent() }
        layer?.let { l -> host?.let { if (it.children.contains(l)) it.removeChild(l) } }
        host = null; content = null; layer = null; panel = null
        // Every one of these was a static reference to a screen's component tree. detach() only ran
        // when the inspector was closed *while a screen was still up*, so closing a screen with it
        // open retained that screen until some later screen's first frame replaced the references.
        hovered = null
        treeHighlight = null
        selectedComponent = null
        collapsed.clear()
    }

    // ---- per-widget outline (called by BrassWidget while it paints) -------------------------------

    fun inspect(
        m: UMatrixStack,
        component: UIComponent,
        mouseX: Float,
        mouseY: Float,
        bleedX: Float = 0f,
        bleedTop: Float = 0f,
        bleedBottom: Float = 0f,
    ) {
        // BrassStats.paused is true exactly while the dev layer draws, so this also skips the panel's own
        // widgets without a parent-chain walk.
        if (!enabled || BrassStats.paused || component is BrassDevOverlay) return
        try {
            val x = component.getLeft()
            val y = component.getTop()
            val x2 = component.getRight()
            val y2 = component.getBottom()
            if (x2 <= x || y2 <= y) return

            if (showOutlines) {
                outline(m, x, y, x2, y2, BOX)
                if (bleedX > 0f || bleedTop > 0f || bleedBottom > 0f) {
                    outline(m, x - bleedX, y - bleedTop, x2 + bleedX, y2 + bleedBottom, BLEED)
                }
            }

            // Record the candidate under the cursor, but do NOT draw a hot outline here. Widgets under
            // a window that covers them are still hit by this test - they simply painted earlier - so
            // outlining every one of them lit up controls hidden behind the frame in front. Assigning
            // instead of drawing means the LAST widget to paint wins, which is the topmost one, and
            // only that one gets the box model in drawOverlay.
            if (mouseX >= x && mouseX <= x2 && mouseY >= y && mouseY <= y2) hovered = component
        } catch (_: Throwable) {
            // never let the inspector break the screen it is inspecting
        }
    }

    // ---- top overlay: box model + tooltip --------------------------------------------------------

    fun drawOverlay(m: UMatrixStack, root: UIComponent, screenW: Float, screenH: Float, mouseX: Float, mouseY: Float) {
        if (!enabled) return
        BrassDrawScope.paused {
            // The panel is a solid card sitting over the UI. While the cursor is inside it, neither the
            // box model nor the metadata card may draw - a tooltip for a widget buried under the panel
            // is describing something you cannot see, and it followed the cursor down over the
            // selected-element pane where it had no business being.
            //
            // The panel's *own* controls are exempt, which is why this is a predicate rather than a
            // flag: the header buttons need tooltips of their own, and they live inside the panel.
            try {
                val overPanel = cursorOverPanel(mouseX, mouseY)
                BrassTooltip.gate = { c -> !overPanel || insideDevLayer(c) }
                val t = if (overPanel) null else target()
                if (t != null && t !is BrassDevOverlay) {
                    drawBoxModel(m, t)
                    drawTooltip(m, root, t, screenW, screenH, mouseX, mouseY)
                }
            } catch (_: Throwable) {
                // ditto
            } finally {
                hovered = null // recomputed every frame from inspect(); tree highlight persists
            }
        }
    }

    /** Whether [c] belongs to the inspector's own subtree, rather than the UI being inspected. */
    private fun insideDevLayer(c: UIComponent): Boolean {
        val l = layer ?: return false
        return BrassTree.isDescendantOf(c, l)
    }

    /** Whether the cursor is inside the docked panel (including its margin). */
    private fun cursorOverPanel(mouseX: Float, mouseY: Float): Boolean {
        val l = layer ?: return false
        return mouseX >= l.getLeft() - BrassDevPanel.MARGIN && mouseX <= l.getRight() + BrassDevPanel.MARGIN &&
            mouseY >= l.getTop() - BrassDevPanel.MARGIN && mouseY <= l.getBottom() + BrassDevPanel.MARGIN
    }

    private fun drawBoxModel(m: UMatrixStack, c: UIComponent) {
        val x = c.getLeft(); val y = c.getTop(); val x2 = c.getRight(); val y2 = c.getBottom()
        if (x2 <= x || y2 <= y) return

        if (c is BrassWidget) {
            val bx = BrassWidget.BLEED_X
            val keycap = c.chrome == BrassChrome.KEYCAP
            val bt = if (keycap) BrassWidget.BLEED_TOP else 0f
            val bb = if (keycap) BrassWidget.BLEED_BOTTOM else 0f
            if (bx > 0f || bt > 0f || bb > 0f) ring(m, x - bx, y - bt, x2 + bx, y2 + bb, x, y, x2, y2, MARGIN)
        }

        var hasKids = false
        var kx1 = Float.MAX_VALUE; var ky1 = Float.MAX_VALUE
        var kx2 = -Float.MAX_VALUE; var ky2 = -Float.MAX_VALUE
        for (child in c.children) {
            if (child is BrassDevOverlay) continue
            hasKids = true
            kx1 = minOf(kx1, child.getLeft()); ky1 = minOf(ky1, child.getTop())
            kx2 = maxOf(kx2, child.getRight()); ky2 = maxOf(ky2, child.getBottom())
        }

        if (hasKids) {
            val cx1 = kx1.coerceIn(x, x2); val cy1 = ky1.coerceIn(y, y2)
            val cx2 = kx2.coerceIn(x, x2); val cy2 = ky2.coerceIn(y, y2)
            ring(m, x, y, x2, y2, cx1, cy1, cx2, cy2, PADDING)
            fillRect(m, cx1, cy1, cx2, cy2, CONTENT)
        } else {
            fillRect(m, x, y, x2, y2, CONTENT)
        }
        outline(m, x, y, x2, y2, BOX_HOT)
    }

    private fun drawTooltip(m: UMatrixStack, root: UIComponent, c: UIComponent, screenW: Float, screenH: Float, mouseX: Float, mouseY: Float) {
        val lines = arrayOf(
            c.javaClass.simpleName.ifEmpty { "anon" },
            "x %d  y %d".format(c.getLeft().roundToInt(), c.getTop().roundToInt()),
            "w %d  h %d".format((c.getRight() - c.getLeft()).roundToInt(), (c.getBottom() - c.getTop()).roundToInt()),
        )
        var tw = 0f
        for (s in lines) tw = maxOf(tw, BrassFont.width(root, s))
        val boxW = tw + 10f
        val boxH = lines.size * (BrassFont.LINE + 3f) + 5f

        // Placed through the tooltip's own placer, told to avoid wherever the widget tooltip landed.
        // Both cards want to sit below-right of the cursor, so hovering a label that has a tooltip
        // *and* a dev outline drew them straight on top of each other.
        val at = BrassTooltip.placeCard(
            boxW, boxH, mouseX, mouseY, screenW, screenH,
            avoid = BrassTooltip.lastBounds,
        )
        val bx = at[0]
        val by = at[1]

        BrassCard.draw(m, bx, by, bx + boxW, by + boxH, shadow = true)
        var ly = by + 4f
        for ((i, s) in lines.withIndex()) {
            BrassFont.draw(m, root, s, bx + 5f, ly, if (i == 0) Colors.UI_TEXT_HOVER else Colors.UI_TEXT_DARK, false)
            ly += BrassFont.LINE + 3f
        }
    }

    // ---- perf readout ----------------------------------------------------------------------------

    fun statsLines(): List<Pair<String, Color>> {
        val s = BrassStats
        return listOf(
            "%.0f fps  %.2f ms".format(s.fps, s.frameMs) to when {
                s.fps >= 55f -> GOOD
                s.fps >= 30f -> WARN
                else -> BAD
            },
            "widgets %d/%d  quads %d".format(s.lastPainted, s.widgetCount, s.lastQuads) to Colors.UI_TEXT_DARK,
            "components %d  text %d".format(s.componentCount, s.lastGlyphRuns) to Colors.UI_TEXT_DARK,
        )
    }

    // ---- primitives ------------------------------------------------------------------------------

    private fun fillRect(m: UMatrixStack, x: Float, y: Float, x2: Float, y2: Float, c: Color) {
        if (x2 <= x || y2 <= y) return
        UIBlock.drawBlock(m, c, x.toDouble(), y.toDouble(), x2.toDouble(), y2.toDouble())
    }

    /** Fill the four bands between an outer and an inner rectangle - a box-model band (padding/margin). */
    private fun ring(
        m: UMatrixStack,
        ox: Float, oy: Float, ox2: Float, oy2: Float,
        ix: Float, iy: Float, ix2: Float, iy2: Float,
        c: Color,
    ) {
        fillRect(m, ox, oy, ox2, iy, c)
        fillRect(m, ox, iy2, ox2, oy2, c)
        fillRect(m, ox, iy, ix, iy2, c)
        fillRect(m, ix2, iy, ox2, iy2, c)
    }

    /**
     * A 1-px outline drawn as four **non-overlapping** bands.
     *
     * The obvious version draws full-width top/bottom bars and full-height left/right bars, which
     * double up at the four corners. With an opaque colour that is invisible; these outlines are
     * translucent, so every corner pixel got blended twice and came out noticeably darker - four dots
     * framing each widget. The side bands are inset by a pixel at each end so nothing is painted twice.
     */
    private fun outline(m: UMatrixStack, x: Float, y: Float, x2: Float, y2: Float, c: Color) {
        fillRect(m, x, y, x2, y + 1f, c)              // top, full width
        fillRect(m, x, y2 - 1f, x2, y2, c)            // bottom, full width
        fillRect(m, x, y + 1f, x + 1f, y2 - 1f, c)    // left, between them
        fillRect(m, x2 - 1f, y + 1f, x2, y2 - 1f, c)  // right, between them
    }

    /** How fast the panel docks and undocks. */
    private const val DOCK_SPEED = 13f

    private val BOX = Color(0, 200, 255, 90)
    private val BOX_HOT = Color(255, 220, 0, 220)
    private val BLEED = Color(255, 0, 170, 70)
    private val CONTENT = Color(90, 160, 255, 70)
    private val PADDING = Color(90, 220, 120, 70)
    private val MARGIN = Color(240, 170, 70, 70)
    private val GOOD = Color(0x34, 0xD2, 0x7A, 240)
    private val WARN = Color(0xF0, 0xC0, 0x40, 240)
    private val BAD = Color(0xE0, 0x60, 0x50, 240)
}
