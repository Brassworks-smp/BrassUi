package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.base.BrassFocus
import net.swzo.brass.ui.kit.base.BrassInert
import net.swzo.brass.ui.kit.base.BrassMotion
import net.swzo.brass.ui.kit.base.BrassState
import net.swzo.brass.ui.kit.base.BrassTree
import net.swzo.brass.ui.kit.input.BrassNavRail
import net.swzo.brass.ui.kit.input.BrassTabSwitch
import net.swzo.brass.ui.kit.text.BrassTextField

/**
 * Pages of which one is shown, switched by [tabs] or a [navRail]. With [slide], a switch the player makes
 * there moves the new page in from the side it sits on (the next tab from the right, a tab before from the
 * left; or from below and above for a rail) while the old one drifts the other way and fades. A switch made
 * in code ([select], [selection], a screen rebuilt with fresh data) lands at once: motion is for what the
 * player did, never for state changing under them. Drawn through the matrix (whole pixels, so pixel art
 * stays crisp), so hit-testing uses the resting bounds while it runs.
 */
class BrassPages @JvmOverloads constructor(
    private val lazy: Boolean = true,
    private val pad: Float = 0f,
    private val slide: Slide = Slide.NONE,
) : UIContainer() {

    /** How switching pages moves: not at all, sideways (tabs), or up and down (a nav rail). */
    enum class Slide { NONE, HORIZONTAL, VERTICAL }

    class Page internal constructor(
        val title: String,
        private val build: (Page) -> Unit,
        /** Shown before the title on a [navRail]. */
        val icon: net.swzo.brass.ui.kit.media.BrassIcons.Icon? = null,
    ) : UIContainer(), BrassInert {

        var active: Boolean = false
            internal set

        /** Drawn while it slides away (inert all the same). */
        internal var leaving = false
        internal var dx = 0f
        internal var dy = 0f
        internal var fade = 1f

        override val inert: Boolean get() = !active

        internal var built = false
            private set

        internal fun ensureBuilt() {
            if (built) return
            built = true
            build(this)
        }

        override fun draw(matrixStack: UMatrixStack) {
            if (!active && !leaving) return
            val moved = dx != 0f || dy != 0f || fade < 0.999f
            val savedFade = BrassAmbientFade.current
            if (moved) {
                matrixStack.push()
                matrixStack.translate(dx, dy, 0f)
                BrassAmbientFade.current = savedFade * fade
            }
            super.draw(matrixStack)
            if (moved) {
                BrassAmbientFade.current = savedFade
                matrixStack.pop()
            }
        }

        override fun isPointInside(x: Float, y: Float): Boolean = active && super.isPointInside(x, y)

        override fun mouseMove(window: Window) {
            if (active) super.mouseMove(window)
        }

        override fun animationFrame() {
            if (active || leaving) super.animationFrame()
        }
    }

    val pages: List<Page> get() = all

    private val all = ArrayList<Page>()

    val selection: BrassState<Int> = BrassState(-1)

    val current: Page? get() = all.getOrNull(selection.value)

    init {
        selection.onChange { apply(it) }
    }

    fun page(title: String, build: (Page) -> Unit): Page = page(title, null, build)

    /** A page with an [icon] beside its title on a [navRail]. */
    fun page(title: String, icon: net.swzo.brass.ui.kit.media.BrassIcons.Icon?, build: (Page) -> Unit): Page {
        val page = Page(title, build, icon)
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
        tabs.onChange { picked = it } // before the binding: the player's pick is known when the selection moves
        tabs.bind(selection)
        tabs.shrinkToFit = true
        return tabs
    }

    /**
     * A nav rail over the pages; the last [pinnedBottom] sit at its bottom (Settings, say), the first
     * [pinnedTop] are headline keycaps set apart by a separator (Overview, say).
     */
    @JvmOverloads
    fun navRail(pinnedBottom: Int = 0, pinnedTop: Int = 0): BrassNavRail {
        val rail = BrassNavRail(all.map { it.title }, pinnedBottom = pinnedBottom, pinnedTop = pinnedTop, icons = all.map { it.icon })
        rail.onChange { picked = it }
        rail.bind(selection)
        return rail
    }

    /** The page the player just picked on the tabs or the rail (the next selection change is theirs). */
    private var picked = -1

    private fun apply(index: Int) {
        finish()
        val byPlayer = picked == index
        picked = -1
        val from = all.indexOfFirst { it.active }
        all.forEachIndexed { i, page ->
            val live = i == index
            if (page.active && !live) release(page)
            page.active = live
            if (live) page.ensureBuilt()
        }
        if (byPlayer && slide != Slide.NONE && from >= 0 && from != index && index in all.indices && getWidth() > 0f && getHeight() > 0f) {
            leaving = all[from].also { it.leaving = true }
            direction = if (index > from) 1 else -1
            progress = 0f
            if (clip == null) clip = ScissorEffect().also { enableEffect(it) }
            layoutSlide()
        }
    }

    private var leaving: Page? = null
    private var direction = 0
    private var progress = 1f
    private var clip: ScissorEffect? = null

    /** Whether a switch is still sliding (dev checks wait for it). */
    val animating: Boolean get() = direction != 0

    override fun animationFrame() {
        if (direction != 0) {
            progress = (progress + BrassClock.dt / BrassMotion.DURATION).coerceAtMost(1f)
            if (progress >= 1f) finish() else layoutSlide()
        }
        super.animationFrame()
    }

    /** The arriving page glides in from its side, the leaving one drifts away and fades (see [BrassMotion]). */
    private fun layoutSlide() {
        val size = if (slide == Slide.VERTICAL) getHeight() else getWidth()
        val arrive = BrassMotion.arrive(progress, direction, size)
        val drift = BrassMotion.drift(progress, direction, size)
        current?.apply {
            if (slide == Slide.VERTICAL) { dy = arrive; dx = 0f } else { dx = arrive; dy = 0f }
            fade = BrassMotion.fadeIn(progress)
        }
        leaving?.apply {
            if (slide == Slide.VERTICAL) { dy = drift; dx = 0f } else { dx = drift; dy = 0f }
            fade = BrassMotion.fadeOut(progress)
            leaving = fade > 0f
        }
    }

    /** Lands a running slide (also when another switch comes mid-slide). */
    private fun finish() {
        if (direction == 0) return
        direction = 0
        progress = 1f
        leaving?.let { it.leaving = false; it.dx = 0f; it.dy = 0f; it.fade = 1f }
        leaving = null
        current?.let { it.dx = 0f; it.dy = 0f; it.fade = 1f }
        clip?.let { removeEffect(it) }
        clip = null
    }

    private fun release(page: Page) {
        BrassFocus.focused?.let { if (BrassTree.isDescendantOf(it, page)) BrassFocus.clear() }
        for (field in BrassTree.descendantsOfType(page, BrassTextField::class.java)) {
            if (field.focused) (field as? UIComponent)?.loseFocus()
        }
    }
}
