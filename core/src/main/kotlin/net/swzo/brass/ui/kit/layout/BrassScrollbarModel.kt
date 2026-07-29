package net.swzo.brass.ui.kit.layout

/**
 * The arithmetic behind a scrollbar: where the grip sits, how far it travels, and what an offset a
 * drag or a track click implies.
 *
 * ### Why this exists
 *
 * [BrassScrollbar] is a component driven by Elementa, which does the geometry. [net.swzo.brass.ui.kit.surface.BrassTable]
 * scrolls **itself** - that is what lets it skip off-screen rows - so it cannot use the component,
 * and it had grown its own `gripRect`, `scrollToGripTop`, drag tracking and page-on-track-click.
 *
 * The table's comment acknowledged the duplication and concluded it "cannot reuse the component, but
 * it can and must reuse the drawing", and then reused only the grip's *paint*. But the paint was
 * never the fiddly part: the geometry is, and two copies of it is two sets of off-by-one bugs waiting
 * to diverge.
 *
 * Deliberately pure - no components, no drawing, no state beyond what it is handed. That makes it the
 * one part of the scrolling machinery that is directly testable.
 */
class BrassScrollbarModel(
    /** Height of the visible area. */
    var viewport: Float = 0f,
    /** Total height of the content being scrolled. */
    var content: Float = 0f,
    /** Shortest the grip may become, so it stays grabbable in a very long list. */
    private val minGrip: Float = 12f,
) {

    /** How much content does not fit. Zero when everything is visible. */
    val overflow: Float get() = (content - viewport).coerceAtLeast(0f)

    /** Whether there is anything to scroll. */
    val scrollable: Boolean get() = overflow > 0f && viewport > 0f

    /** Clamp [offset] to the scrollable range. */
    fun clamp(offset: Float): Float = offset.coerceIn(0f, overflow)

    /** Height of the grip for the current ratio, never below [minGrip]. */
    fun gripHeight(): Float =
        if (!scrollable) 0f else (viewport / content * viewport).coerceAtLeast(minGrip)

    /** How far the grip can travel from top to bottom. */
    fun gripTravel(): Float = (viewport - gripHeight()).coerceAtLeast(0f)

    /** Top edge of the grip, relative to the top of the track, for a given scroll [offset]. */
    fun gripTop(offset: Float): Float {
        if (!scrollable) return 0f
        return (clamp(offset) / overflow) * gripTravel()
    }

    /**
     * The scroll offset that puts the grip's top at [gripTop] (relative to the track's top) - the
     * inverse of [gripTop], for a drag.
     */
    fun offsetForGripTop(gripTop: Float): Float {
        val travel = gripTravel()
        if (travel <= 0f) return 0f
        return clamp((gripTop / travel) * overflow)
    }

    /**
     * The offset after clicking the track at [trackY] (relative to the track's top) with the grip
     * currently at [offset] - a page toward the click, as every scrollbar does.
     */
    fun pageToward(offset: Float, trackY: Float): Float {
        val top = gripTop(offset)
        return clamp(if (trackY < top) offset - viewport else offset + viewport)
    }

    /** Whether [trackY] (relative to the track's top) is on the grip rather than the track. */
    fun gripContains(offset: Float, trackY: Float): Boolean {
        val top = gripTop(offset)
        return trackY >= top && trackY <= top + gripHeight()
    }

    /** The minimum scroll that brings the row spanning [top]..[top]+[height] fully into view. */
    fun reveal(offset: Float, top: Float, height: Float): Float = clamp(
        when {
            top < offset -> top
            top + height > offset + viewport -> top + height - viewport
            else -> offset
        },
    )
}
