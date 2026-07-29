package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassDismissable
import net.swzo.brass.ui.kit.base.BrassMetrics
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.input.BrassButton

/**
 * A small floating panel of action rows shown at a point (typically on
 * right-click). Built as a keycap panel with keycap rows that highlight on hover; picking a row runs its
 * action and closes the menu. Call [show] with the screen root so it floats above everything, [hide] to
 * dismiss.
 */
class BrassContextMenu private constructor(
    private val items: List<Item>,
    /** Custom body, for a menu that is a *panel* rather than a list of rows. Null for a normal menu. */
    private val content: UIComponent?,
    private val rowWidth: Int,
    /** Height of [content], including this menu's padding. Ignored for a row menu, which measures itself. */
    private val contentHeight: Int,
) : UIContainer(), BrassDismissable {

    /** The usual menu: a list of labelled actions. */
    constructor(items: List<Item>, rowWidth: Int = 130) : this(items, null, rowWidth, 0)

    data class Item(val label: String, val action: () -> Unit)

    private val panel = Panel()
    private var root: UIComponent? = null

    /** Optional completion hook for live custom panels such as colour pickers. Called exactly once. */
    var onDismiss: (() -> Unit)? = null

    private val rowH = 17
    private val pad = 3

    /** When this menu was shown - used to ignore the very click that opened it. */
    private var openedAt = 0L

    companion object {
        /** Margin kept between the menu and the screen edge before it flips. */
        private const val EDGE = BrassMetrics.FLOATING_EDGE

        /**
         * The one menu currently on screen, if any.
         *
         * A context menu is modal-ish by convention: opening a second one closes the first, so two can
         * never be left stacked on top of each other.
         *
         * Held **weakly**. This is a static field cleared only by [dismiss], and closing a screen with
         * a menu still up never calls it - so the reference survived, and through `menu.root` it
         * retained the entire screen tree for the life of the process.
         */
        private var openRef: java.lang.ref.WeakReference<BrassContextMenu>? = null

        private var open: BrassContextMenu?
            get() = openRef?.get()
            set(value) { openRef = value?.let { java.lang.ref.WeakReference(it) } }

        /**
         * Roots that already have the click-away handler installed. Weak, because a screen root dies
         * with its screen and this must not be what keeps it alive.
         */
        private val wired = java.util.WeakHashMap<UIComponent, Boolean>()

        /** Close whatever menu is open, if any. */
        fun closeOpen() {
            open?.dismiss()
        }

        /**
         * Install the dismiss-on-click-elsewhere handler for [root], once per root.
         *
         * This is a listener on the root rather than a full-screen scrim beneath the menu. A scrim
         * would swallow the click that dismisses, so closing a menu and pressing a button underneath
         * would take two clicks, and right-clicking elsewhere could not open a new menu in one go.
         * Listening at the root lets the click reach whatever it landed on as usual.
         */
        private fun wire(root: UIComponent) {
            if (wired.put(root, true) != null) return
            root.onMouseClick { e ->
                val menu = open ?: return@onMouseClick
                // The click that opened a menu also bubbles up to here. Ignore clicks that arrive in
                // the same moment the menu appeared, or a menu would close on the press that made it.
                if (System.nanoTime() - menu.openedAt < OPEN_GRACE_NANOS) return@onMouseClick
                val x = e.absoluteX
                val y = e.absoluteY
                val inside = x >= menu.getLeft() && x <= menu.getRight() &&
                    y >= menu.getTop() && y <= menu.getBottom()
                if (!inside) menu.dismiss()
            }
        }

        /** How long after opening a menu ignores stray clicks - one frame's worth, comfortably. */
        private const val OPEN_GRACE_NANOS = 60_000_000L

        /**
         * A menu that holds a component instead of rows - a colour picker, a small form.
         *
         * It is the same object, so it inherits everything that makes a menu behave: only one open at
         * a time, click-away dismissal, Escape, and flipping back inside the screen near an edge. Build
         * a panel by hand and you get none of that, which is how you end up with two pickers open at
         * once and neither closing.
         */
        fun custom(content: UIComponent, width: Int, height: Int): BrassContextMenu =
            BrassContextMenu(emptyList(), content, width, height)

        /**
         * Lift the open menu back to the top of its band under [root].
         *
         * Through [BrassLayers.raise] - the menu is transient, so this clears every window, but it
         * must **not** clear the toast column, which is exactly what the old hand-rolled append to
         * the end of the child list did. Kept public for anything that reorders layers outside
         * [BrassLayers]' knowledge, though popups no longer need it: their raise stops below
         * transient chrome by rank.
         */
        fun keepOnTop(root: UIComponent) {
            val menu = open ?: return
            if (menu.root !== root) return
            BrassLayers.raise(root, menu)
        }
    }

    /** The menu's height: measured from the rows, or the caller's for a custom body. */
    private fun menuHeight(): Int =
        if (content != null) contentHeight else items.size * (rowH + 1) - 1 + pad * 2

    init {
        constrain { width = rowWidth.pixels(); height = menuHeight().pixels() }
        panel.constrain { width = 100.percent(); height = 100.percent() } childOf this

        content?.constrain {
            x = pad.pixels(); y = pad.pixels()
            width = 100.percent() - (pad * 2).pixels()
            height = 100.percent() - (pad * 2).pixels()
        }?.childOf(this)

        items.forEachIndexed { i, item ->
            val row = BrassButton(item.label, BrassAccent.DEFAULT) { item.action(); dismiss() }.apply {
                centered = false
                flat = true
                entranceEnabled = false
            }
            row.constrain {
                x = pad.pixels()
                y = if (i == 0) pad.pixels() else SiblingConstraint(1f)
                width = 100.percent() - (pad * 2).pixels()
                height = rowH.pixels()
            } childOf this
        }
    }

    /**
     * Float the menu at ([x],[y]), **flipping** it back inside the screen when it would overhang: a menu
     * opened near the right or bottom edge unfolds left / upward from the cursor instead of spilling off.
     *
     * Pass [anchorTop] when the menu hangs off a control rather than the cursor - see the note in the
     * body for why a flipped menu otherwise covers its own button.
     */
    fun show(screenRoot: UIComponent, x: Float, y: Float, anchorTop: Float? = null) {
        // opening a menu replaces any menu already up
        closeOpen()
        wire(screenRoot)
        root = screenRoot
        openedAt = System.nanoTime()
        val h = menuHeight()
        val sw = screenRoot.getWidth()
        val sh = screenRoot.getHeight()
        val px = if (x + rowWidth + EDGE > sw) (x - rowWidth).coerceAtLeast(EDGE) else x
        // Flipping upward puts the menu's *bottom* at the flip line. For a cursor menu that line is the
        // cursor itself, but a menu anchored under a control must clear the whole control - otherwise
        // it flips up and lands on top of the very button that opened it. [anchorTop] is that control's
        // top edge; without one the anchor is a point and y serves for both.
        val flipLine = anchorTop ?: y
        val py = if (y + h + EDGE > sh) (flipLine - h).coerceAtLeast(EDGE) else y
        constrain { this.x = px.pixels(); this.y = py.pixels() }
        this childOf screenRoot
        isFloating = true
        // Into the transient band - childOf appended us above even the toast column.
        BrassLayers.raise(screenRoot, this)
        open = this
    }

    /**
     * Whether this menu is the one currently on screen.
     *
     * Lets a control that opens a menu treat its button as a **toggle**: without this, clicking the
     * button again dismisses the menu (the click-away handler) and then immediately reopens it, so it
     * looks like the button does nothing.
     */
    val isOpen: Boolean get() = open === this

    /** Dismiss the menu. Named `dismiss` (not `hide`) - `UIComponent.hide()` is final. */
    override fun dismiss() {
        if (open === this) open = null
        val r = root ?: return
        r.removeChild(this)
        root = null
        onDismiss?.invoke()
    }

    /** The menu's keycap backing panel. */
    private class Panel : BrassWidget(BrassAccent.DEFAULT) {
        init {
            entranceEnabled = false
            roundness = 4f
            // The panel is the menu's backing, not a control. Without this the whole menu - rows and
            // all - rose two pixels the moment the cursor entered it, which reads as the menu flinching
            // away from the pointer.
        }
        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {}
    }
}
