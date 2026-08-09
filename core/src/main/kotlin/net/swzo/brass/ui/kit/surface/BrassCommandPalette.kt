package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import gg.essential.elementa.dsl.*
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.*
import net.swzo.brass.ui.kit.layout.BrassVirtualList
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassFuzzy
import net.swzo.brass.ui.kit.text.BrassTextInput
import org.lwjgl.glfw.GLFW

/**
 * A fuzzy-search overlay: type a few letters, get the command you meant, press Enter.
 * ```kotlin
 * BrassCommandPalette(commands).show(screenRoot)
 * ```
 * ### Why the toolkit wants one
 * Every control in a dev tool is behind a menu, a panel or a keybind, and a palette is the one piece
 * of UI that makes all of them reachable by name. The toolkit already had the parts -
 * [BrassTextInput] for the query, [BrassVirtualList] for the results, [BrassFuzzy] for the ranking,
 * [BrassFocus] for the keyboard - so this is assembly rather than invention, which is what makes it
 * worth having.
 * ### Keyboard first
 * The mouse works, but nothing here is designed for it: focus goes to the query the moment it opens,
 * up and down move the selection *without leaving the field*, Enter runs the highlighted command and
 * Escape dismisses. A palette you have to click is a menu with extra steps.
 */
class BrassCommandPalette(
    commands: List<Command> = emptyList(),
    private val prompt: String = "Type a command…",
) : BrassWidget(BrassAccent.DEFAULT), BrassDismissable, BrassLayers.Layer, BrassKeyLayer {

    data class Command(
        val name: String,
        val category: String? = null,
        val shortcut: String? = null,
        val run: () -> Unit,
    )

    private var all: List<Command> = commands

    private val query = BrassTextInput(placeholder = prompt)
    private val results = Results()

    var onClose: (() -> Unit)? = null

    init {
        // A BrassWidget, not a UIContainer. The base class is what runs the entrance animation, the
        // hover/press colour easing, the cursor request, the focus ring and BrassDevMode.inspect - a
        // raw Elementa container painting itself gets none of that and is invisible to the inspector.
        // chrome = NONE because this widget paints all of its own background.
        chrome = BrassChrome.NONE
        query.constrain {
            x = basicXConstraint { this@BrassCommandPalette.getLeft() + PAD }
            y = basicYConstraint { this@BrassCommandPalette.getTop() + PAD }
            width = basicWidthConstraint { this@BrassCommandPalette.getWidth() - PAD * 2 }
            height = FIELD_H.pixels()
        } childOf this

        results.constrain {
            x = basicXConstraint { this@BrassCommandPalette.getLeft() + PAD }
            y = basicYConstraint { this@BrassCommandPalette.getTop() + PAD * 2 + FIELD_H }
            width = basicWidthConstraint { this@BrassCommandPalette.getWidth() - PAD * 2 }
            height = basicHeightConstraint { (this@BrassCommandPalette.getHeight() - PAD * 3 - FIELD_H).coerceAtLeast(0f) }
        } childOf this

        query.onChange { refilter() }
        refilter()
    }

    fun setCommands(next: List<Command>) {
        all = next
        refilter()
    }

    private fun refilter() {
        results.setItems(BrassFuzzy.rank(query.text, all) { it.name })
        // Always leave something highlighted, so Enter is never a no-op with results on screen.
        results.select(if (results.rowCount() > 0) 0 else -1)
        results.scrollOffset = 0f
    }

    override val rank: BrassLayers.Rank get() = BrassLayers.Rank.TRANSIENT

    private var openedAt = 0L

    fun show(root: UIComponent) {
        constrain {
            width = WIDTH.pixels()
            height = HEIGHT.pixels()
            x = basicXConstraint { root.getLeft() + (root.getWidth() - WIDTH) / 2f }
            // Placed at a third rather than centred: a palette that grows downward as it fills should
            // not appear to jump upward, and the eye expects a launcher near the top.
            y = basicYConstraint { root.getTop() + root.getHeight() / 3f - HEIGHT / 2f }
        }
        childOf(root)
        // Floating, like every other layer that has to clear the windows: Elementa draws floating
        // components after the ordinary tree, so a palette left as a plain child is painted *under*
        // any window - and every frame in this toolkit is floating.
        isFloating = true
        // Then raised among the floating layers, so it also clears popups and menus already up.
        BrassLayers.raise(root, this)

        openedAt = System.nanoTime()
        open = this
        wire(root)

        // BrassFocus routes through BrassFocusable, which is what actually marks the field focused.
        BrassFocus.focus(query, fromKeyboard = true)
    }

    fun runSelected() {
        val command = results.selected() ?: return
        close()
        command.run()
    }

    override fun dismiss() = close()

    private fun close() {
        if (open === this) open = null
        BrassFocus.clear()
        parent.removeChild(this)
        onClose?.invoke()
    }

    override fun onLayerKey(keyCode: Int, modifiers: gg.essential.universal.UKeyboard.Modifiers?): Boolean =
        onKey(keyCode)

    fun onKey(keyCode: Int): Boolean = when (keyCode) {
        GLFW.GLFW_KEY_UP -> { results.moveSelection(-1); true }
        GLFW.GLFW_KEY_DOWN -> { results.moveSelection(1); true }
        GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { runSelected(); true }
        GLFW.GLFW_KEY_ESCAPE -> { close(); true }
        else -> false
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun drawContent(matrixStack: UMatrixStack, bx: Int, by: Int, bw: Int, bh: Int) {
        BrassCard.draw(matrixStack, getLeft(), getTop(), getRight(), getBottom(), shadow = true)
        BrassIcons.draw(
            matrixStack, BrassIcons.SEARCH,
            getLeft() + PAD + 4f, getTop() + PAD + (FIELD_H - ICON) / 2f, ICON, Colors.UI_TEXT_DARK,
        )
    }

    private inner class Results : BrassVirtualList<Command>(ROW_H, { _, _ -> }) {

        init {
            // A click runs the command outright: in a palette, selecting and confirming are the same
            // gesture, and requiring a double-click would be a trap.
            enableEffect(gg.essential.elementa.effects.ScissorEffect())
        }

        override fun onRowClick(index: Int, localX: Float, button: Int): Boolean {
            select(index)
            runSelected()
            return true
        }

        override fun paintRow(m: UMatrixStack, item: Command, index: Int, x: Float, y: Float, w: Float) {
            val ty = y + (rowHeight - BrassFont.LINE) / 2f
            val tint = if (index == selectedIndex) Colors.UI_ACCENT_BRIGHT else Colors.UI_TEXT
            BrassFont.draw(m, this@BrassCommandPalette, item.name, x + PAD, ty, tint)

            var right = x + w - PAD

            item.shortcut?.let {
                val sw = BrassFont.width(this@BrassCommandPalette, it)
                BrassFont.draw(m, this@BrassCommandPalette, it, right - sw, ty, Colors.UI_TEXT_DARK)
                right -= sw + PAD
            }

            item.category?.let {
                val fitted = BrassFont.fit(this@BrassCommandPalette, it, w / 3f)
                val cw = BrassFont.width(this@BrassCommandPalette, fitted)
                BrassFont.draw(m, this@BrassCommandPalette, fitted, right - cw, ty, Colors.UI_TEXT_DARK)
            }
        }
    }

    private companion object {

        /**
         * The one palette currently on screen, if any.
         * Held **weakly**, for the reason [BrassContextMenu] documents: this is a static field cleared
         * only on close, and closing a screen with a palette up never calls it - so the reference
         * would survive and retain the whole screen tree for the life of the process.
         */
        private var openRef: java.lang.ref.WeakReference<BrassCommandPalette>? = null

        private var open: BrassCommandPalette?
            get() = openRef?.get()
            set(value) { openRef = value?.let { java.lang.ref.WeakReference(it) } }

        private val wired = java.util.WeakHashMap<UIComponent, Boolean>()

        private fun wire(root: UIComponent) {
            if (wired.put(root, true) != null) return
            root.onMouseClick { e ->
                val palette = open ?: return@onMouseClick
                // The click that opened the palette also bubbles up here. Ignore anything arriving in
                // the same moment it appeared, or it would close on the press that created it.
                if (System.nanoTime() - palette.openedAt < OPEN_GRACE_NANOS) return@onMouseClick
                val inside = e.absoluteX >= palette.getLeft() && e.absoluteX <= palette.getRight() &&
                    e.absoluteY >= palette.getTop() && e.absoluteY <= palette.getBottom()
                if (!inside) palette.dismiss()
            }
        }

        private const val OPEN_GRACE_NANOS = 60_000_000L

        const val WIDTH = 320f
        const val HEIGHT = 220f
        const val PAD = 6f
        const val FIELD_H = 18f
        const val ROW_H = 15f
        const val ICON = 8f
    }
}
