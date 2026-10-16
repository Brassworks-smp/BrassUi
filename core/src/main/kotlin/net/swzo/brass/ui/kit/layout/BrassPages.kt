package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassFocus
import net.swzo.brass.ui.kit.base.BrassInert
import net.swzo.brass.ui.kit.base.BrassState
import net.swzo.brass.ui.kit.base.BrassTree
import net.swzo.brass.ui.kit.input.BrassNavRail
import net.swzo.brass.ui.kit.input.BrassTabSwitch
import net.swzo.brass.ui.kit.text.BrassTextField

class BrassPages @JvmOverloads constructor(
    private val lazy: Boolean = true,
    private val pad: Float = 0f,
) : UIContainer() {

    class Page internal constructor(
        val title: String,
        private val build: (Page) -> Unit,
    ) : UIContainer(), BrassInert {

        var active: Boolean = false
            internal set

        override val inert: Boolean get() = !active

        internal var built = false
            private set

        internal fun ensureBuilt() {
            if (built) return
            built = true
            build(this)
        }

        override fun draw(matrixStack: UMatrixStack) {
            if (active) super.draw(matrixStack)
        }

        override fun isPointInside(x: Float, y: Float): Boolean = active && super.isPointInside(x, y)

        override fun mouseMove(window: Window) {
            if (active) super.mouseMove(window)
        }

        override fun animationFrame() {
            if (active) super.animationFrame()
        }
    }

    val pages: List<Page> get() = all

    private val all = ArrayList<Page>()

    val selection: BrassState<Int> = BrassState(-1)

    val current: Page? get() = all.getOrNull(selection.value)

    init {
        selection.onChange { apply(it) }
    }

    fun page(title: String, build: (Page) -> Unit): Page {
        val page = Page(title, build)
        page.constrain {
            x = pad.pixels(); y = pad.pixels()
            width = 100.percent() - (pad * 2).pixels()
            height = 100.percent() - (pad * 2).pixels()
        } childOf this
        all.add(page)
        if (!lazy) page.ensureBuilt()
        if (selection.value < 0) selection.value = 0
        return page
    }

    fun select(index: Int) {
        if (index in all.indices) selection.value = index
    }

    fun select(title: String) = select(all.indexOfFirst { it.title == title })

    fun tabs(equalWidths: Boolean = false): BrassTabSwitch {
        val tabs = BrassTabSwitch(all.map { it.title }, selection.value.coerceAtLeast(0), equalWidths = equalWidths)
        tabs.bind(selection)
        tabs.shrinkToFit = true
        return tabs
    }

    /** A nav rail over the pages; the last [pinnedBottom] sit at its bottom (Settings, say). */
    @JvmOverloads
    fun navRail(pinnedBottom: Int = 0): BrassNavRail {
        val rail = BrassNavRail(all.map { it.title }, pinnedBottom = pinnedBottom)
        rail.bind(selection)
        return rail
    }

    private fun apply(index: Int) {
        all.forEachIndexed { i, page ->
            val live = i == index
            if (page.active && !live) release(page)
            page.active = live
            if (live) page.ensureBuilt()
        }
    }

    private fun release(page: Page) {
        BrassFocus.focused?.let { if (BrassTree.isDescendantOf(it, page)) BrassFocus.clear() }
        for (field in BrassTree.descendantsOfType(page, BrassTextField::class.java)) {
            if (field.focused) (field as? UIComponent)?.loseFocus()
        }
    }
}
