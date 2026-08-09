package net.swzo.brass.ui.kit.layout

/**
 * The arithmetic behind a scrollbar: where the grip sits, how far it travels, and what an offset a
 * drag or a track click implies.
 * ### Why this exists
 * [BrassScrollbar] is a component driven by Elementa, which does the geometry. [net.swzo.brass.ui.kit.surface.BrassTable]
 * scrolls **itself** - that is what lets it skip off-screen rows - so it cannot use the component,
 * and it had grown its own `gripRect`, `scrollToGripTop`, drag tracking and page-on-track-click.
 * The table's comment acknowledged the duplication and concluded it "cannot reuse the component, but
 * it can and must reuse the drawing", and then reused only the grip's *paint*. But the paint was
 * never the fiddly part: the geometry is, and two copies of it is two sets of off-by-one bugs waiting
 * to diverge.
 * Deliberately pure - no components, no drawing, no state beyond what it is handed. That makes it the
 * one part of the scrolling machinery that is directly testable.
 */
class BrassScrollbarModel(
    var viewport: Float = 0f,
    var content: Float = 0f,
    private val minGrip: Float = 12f,
) {

    val overflow: Float get() = (content - viewport).coerceAtLeast(0f)

    val scrollable: Boolean get() = overflow > 0f && viewport > 0f

    fun clamp(offset: Float): Float = offset.coerceIn(0f, overflow)

    /** Height of the grip for the current ratio, never below [minGrip]. */
    fun gripHeight(): Float =
        if (!scrollable) 0f else (viewport / content * viewport).coerceAtLeast(minGrip)

    fun gripTravel(): Float = (viewport - gripHeight()).coerceAtLeast(0f)

    fun gripTop(offset: Float): Float {
        if (!scrollable) return 0f
        return (clamp(offset) / overflow) * gripTravel()
    }

    fun offsetForGripTop(gripTop: Float): Float {
        val travel = gripTravel()
        if (travel <= 0f) return 0f
        return clamp((gripTop / travel) * overflow)
    }

    fun pageToward(offset: Float, trackY: Float): Float {
        val top = gripTop(offset)
        return clamp(if (trackY < top) offset - viewport else offset + viewport)
    }

    fun gripContains(offset: Float, trackY: Float): Boolean {
        val top = gripTop(offset)
        return trackY >= top && trackY <= top + gripHeight()
    }

    fun reveal(offset: Float, top: Float, height: Float): Float = clamp(
        when {
            top < offset -> top
            top + height > offset + viewport -> top + height - viewport
            else -> offset
        },
    )
}
