package net.swzo.brass.ui.kit.layout

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.effects.ScissorEffect
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.base.BrassClock
import net.swzo.brass.ui.kit.base.BrassEntrance
import net.swzo.brass.ui.kit.base.BrassFocus
import net.swzo.brass.ui.kit.base.BrassInert
import net.swzo.brass.ui.kit.base.BrassTree
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.text.BrassTextField

/**
 * Drill-down navigation: a stack of levels where going deeper slides the new level in from the right
 * while the one under it drifts left and dims, and going back reverses it. The levels under the top
 * are **kept, not rebuilt** - their tab, scroll position, typed text and focus are exactly as the
 * player left them when they come back.
 *
 * The new level's widgets play their entrance cascade while it slides in (the stack publishes
 * [BrassEntrance.Phase.OPENING] around it, as a window does while it pops open), so its rows arrive
 * one after another rather than as one slab. A level coming back on [pop] does not cascade: it was
 * already on screen and nothing about it is new.
 *
 * ```kotlin
 * val nav = BrassNavStack().constrain { … } childOf host
 * nav.push(listLevel, animate = false)  // the root
 * nav.push(detailLevel)                 // slides in
 * nav.pop()                             // slides back to listLevel, untouched
 * ```
 * Each level is any component; the stack sizes it to fill itself. Motion follows [BrassFrameAnim]'s
 * approach: it is drawn through the matrix (no relayout per frame), so hit-testing uses the resting
 * bounds during the ~400 ms a transition runs (eased in and out; the old level fades out in its
 * first third).
 */
class BrassNavStack : UIContainer() {

    /** One level: drawn offset and faded while a transition runs; inert unless it is the top. */
    private inner class Level(val content: UIComponent) : UIContainer(), BrassInert {
        var shown = true
        var dx = 0f
        var fade = 1f
        var phase = BrassEntrance.Phase.IDLE

        override val inert: Boolean get() = this !== top

        override fun draw(matrixStack: UMatrixStack) {
            if (!shown) return
            val moved = dx != 0f || fade < 0.999f
            val savedFade = BrassAmbientFade.current
            val savedPhase = BrassEntrance.phase
            // IDLE inherits: the root level opens with its window's own cascade.
            if (phase != BrassEntrance.Phase.IDLE) BrassEntrance.phase = phase
            if (moved) {
                matrixStack.push()
                matrixStack.translate(dx, 0f, 0f)
                BrassAmbientFade.current = savedFade * fade
            }
            super.draw(matrixStack)
            if (moved) {
                BrassAmbientFade.current = savedFade
                matrixStack.pop()
            }
            BrassEntrance.phase = savedPhase
        }

        override fun isPointInside(x: Float, y: Float): Boolean = shown && this === top && super.isPointInside(x, y)

        override fun mouseMove(window: Window) {
            if (shown && this === top) super.mouseMove(window)
        }

        override fun animationFrame() {
            if (shown) super.animationFrame()
        }
    }

    private val levels = ArrayList<Level>()
    private val top: Level? get() = levels.lastOrNull()

    /** The level going away (popped, or replaced by a push without a way back). */
    private var leaving: Level? = null
    private var direction = 0 // +1 push, -1 pop, 0 at rest
    private var progress = 1f
    private var settledAt = 0L
    private var clip: ScissorEffect? = null

    /** How many levels deep (1 = the root alone). */
    val depth: Int get() = levels.size

    /** The level on top, as given to [push]. */
    val current: UIComponent? get() = top?.content

    /** The levels, root first, as given to [push]. */
    val stack: List<UIComponent> get() = levels.map { it.content }

    /** Whether a transition is running (dev checks wait for it). */
    val animating: Boolean get() = direction != 0

    /** Puts [content] on top; slides it in from the right unless [animate] is false (the root). */
    @JvmOverloads
    fun push(content: UIComponent, animate: Boolean = true) {
        finish()
        val level = Level(content)
        content.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf level
        level.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf this
        release(top)
        levels += level
        if (animate && levels.size > 1) start(+1) else settle()
    }

    /** Back one level: the top slides out to the right and is dropped; the one under it returns as it was. */
    @JvmOverloads
    fun pop(animate: Boolean = true): Boolean {
        if (levels.size < 2) return false
        finish()
        val gone = levels.removeAt(levels.size - 1)
        release(gone)
        if (animate) {
            leaving = gone
            start(-1)
        } else {
            removeChild(gone)
            settle()
        }
        return true
    }

    /** Back to the level holding [content] (dropping everything above it); false if it isn't here. */
    @JvmOverloads
    fun popTo(content: UIComponent, animate: Boolean = true): Boolean {
        val i = levels.indexOfFirst { it.content === content }
        if (i < 0) return false
        if (i == levels.size - 1) return true
        finish()
        // Everything between goes at once; only the top is seen leaving.
        while (levels.size > i + 2) removeChild(levels.removeAt(levels.size - 2))
        return pop(animate)
    }

    /**
     * Back to a level that isn't on the stack (a parent the player never came through, say a list
     * after opening straight onto one of its items): [content] goes under the top, which slides away.
     */
    @JvmOverloads
    fun popInto(content: UIComponent, animate: Boolean = true) {
        val keep = top ?: return push(content, animate = false)
        finish()
        for (l in levels.toList()) if (l !== keep) { release(l); removeChild(l) }
        levels.clear()
        val level = Level(content)
        content.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() } childOf level
        level.constrain { x = 0.pixels(); y = 0.pixels(); width = 100.percent(); height = 100.percent() }
        insertChildAt(level, 0)
        levels += level
        levels += keep
        pop(animate)
    }

    /** Swaps the top level for [content] in place (no motion): the same place, rebuilt. */
    fun replaceTop(content: UIComponent) {
        val old = top ?: return push(content, animate = false)
        finish()
        release(old)
        levels.removeAt(levels.size - 1)
        removeChild(old)
        push(content, animate = false)
    }

    /** Drops every level. */
    fun clear() {
        finish()
        levels.forEach { release(it); removeChild(it) }
        levels.clear()
    }

    private fun start(dir: Int) {
        direction = dir
        progress = 0f
        if (clip == null) clip = ScissorEffect().also { enableEffect(it) }
        apply()
    }

    /** Jumps a running transition to its end (a second navigation mid-slide). */
    private fun finish() {
        if (direction == 0) return
        progress = 1f
        land()
    }

    private fun land() {
        val pushed = direction > 0
        direction = 0
        leaving?.let { removeChild(it) }
        leaving = null
        clip?.let { removeEffect(it) }
        clip = null
        settle()
        // Widgets that first paint just after the slide lands still get to cascade in.
        if (pushed) {
            top?.phase = BrassEntrance.Phase.SETTLING
            settledAt = System.nanoTime()
        }
    }

    private fun settle() {
        levels.forEachIndexed { i, l ->
            l.shown = i == levels.size - 1
            l.dx = 0f
            l.fade = 1f
            l.phase = BrassEntrance.Phase.IDLE
        }
    }

    override fun animationFrame() {
        if (direction != 0) {
            // Linear in time; the curve is applied in [apply] (an exponential approach front-loads all
            // the motion into the first frames and reads as a snap).
            progress = (progress + BrassClock.dt / DURATION).coerceAtMost(1f)
            if (progress >= 1f) land() else apply()
        } else if (top?.phase == BrassEntrance.Phase.SETTLING && System.nanoTime() - settledAt > SETTLE_NANOS) {
            top?.phase = BrassEntrance.Phase.IDLE
        }
        super.animationFrame()
    }

    /** Lays the moving levels out for the current [progress]. */
    private fun apply() {
        val w = getWidth()
        val t = smooth(progress)
        // The level being covered (or leaving) is gone by OUT of the way; the arriving one fades in
        // over the first IN of it, so the two never sit half-visible on top of each other.
        val out = 1f - (progress / OUT).coerceIn(0f, 1f)
        val inn = smooth((progress / IN).coerceIn(0f, 1f))
        val under = levels.getOrNull(levels.size - 2)
        val onTop = top
        when (direction) {
            +1 -> {
                // The new top comes in from the right edge; the one it covers drifts left and dims.
                onTop?.apply { shown = true; dx = (1f - t) * w; fade = inn; phase = BrassEntrance.Phase.OPENING }
                under?.apply { shown = out > 0f; dx = -t * w * DRIFT; fade = out * out }
            }
            -1 -> {
                // The leaving level goes back out to the right; the one under it returns from the left.
                leaving?.apply { shown = out > 0f; dx = t * w; fade = out * out; phase = BrassEntrance.Phase.IDLE }
                onTop?.apply { shown = true; dx = -(1f - t) * w * DRIFT; fade = inn; phase = BrassEntrance.Phase.IDLE }
                under?.shown = false
            }
            else -> {}
        }
        // The leaving level is drawn over the returning one (it is the one sliding across).
        leaving?.let { if (children.lastOrNull() !== it) { removeChild(it); addChild(it) } }
    }

    /** A level losing the top gives up keyboard focus (a field under the stack must not keep typing). */
    private fun release(level: Level?) {
        level ?: return
        BrassFocus.focused?.let { if (BrassTree.isDescendantOf(it, level)) BrassFocus.clear() }
        for (field in BrassTree.descendantsOfType(level, BrassTextField::class.java)) {
            if (field.focused) (field as? UIComponent)?.loseFocus()
        }
    }

    private companion object {
        /** How long a slide takes, in seconds. */
        const val DURATION = 0.42f
        /** How far (of the width) the covered level drifts left. */
        const val DRIFT = 0.25f
        /** By what share of the slide the old level has faded out, and the new one in. */
        const val OUT = 0.35f
        const val IN = 0.6f

        /** Ease in-out (quintic smootherstep): starts gently, glides, settles without a jolt. */
        fun smooth(x: Float): Float = x * x * x * (x * (x * 6f - 15f) + 10f)

        val SETTLE_NANOS: Long = ((BrassWidget.ENTRANCE_DELAY_MAX + 1f / BrassWidget.ENTRANCE_SPEED) * 1e9f).toLong()
    }
}
