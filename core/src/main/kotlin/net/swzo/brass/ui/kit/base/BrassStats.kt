package net.swzo.brass.ui.kit.base

import gg.essential.elementa.UIComponent
import net.swzo.brass.ui.kit.base.BrassStats.SAMPLES
import net.swzo.brass.ui.kit.base.BrassStats.beginFrame
import net.swzo.brass.ui.kit.base.BrassStats.componentCount
import net.swzo.brass.ui.kit.base.BrassStats.frameTimes
import net.swzo.brass.ui.kit.base.BrassStats.quads
import net.swzo.brass.ui.kit.base.BrassStats.widgetCount
import net.swzo.brass.ui.kit.dev.BrassDevLayer
import net.swzo.brass.ui.kit.dev.BrassDevMode
import net.swzo.brass.ui.kit.dev.BrassDevOverlay

/**
 * Frame instrumentation for the dev overlay: frame rate, frame time, how many widgets were painted
 * versus culled, and how much drawing each frame actually asked for.
 *
 * Counters are reset at the top of every frame by [beginFrame] and read by [BrassDevMode] after the
 * frame is drawn. Collection is a handful of integer increments and stays on permanently - the cost
 * is far below the noise floor, and numbers that only exist while you are looking at them are no use
 * for finding a regression.
 *
 * ### On "draw calls"
 *
 * [quads] counts quads submitted through the toolkit's own painting helpers. It is a **lower bound**
 * on real GPU work, not a GPU counter: Elementa's own components (`UIText`, `UIImage`,
 * `ScrollComponent` chrome) draw through paths this cannot see, and the renderer batches. Treat it
 * as a relative measure - useful for "this screen got twice as expensive", not for absolute cost.
 */
object BrassStats {

    // ---- rolling frame rate ----------------------------------------------------------------------

    private const val SAMPLES = 60
    private val frameTimes = FloatArray(SAMPLES)
    private var sampleIndex = 0
    private var samplesFilled = 0
    private var lastFrameNanos = 0L

    /** Sum of the live entries in [frameTimes], maintained incrementally - see [beginFrame]. */
    private var frameSum = 0f

    /** Smoothed frames per second over the last [SAMPLES] frames. */
    var fps: Float = 0f
        private set

    /** Mean frame time in milliseconds over the same window. */
    var frameMs: Float = 0f
        private set

    // ---- per-frame counters ----------------------------------------------------------------------

    var paintedWidgets = 0; private set
    var culledWidgets = 0; private set
    var quads = 0; private set
    var glyphRuns = 0; private set

    // snapshot of the previous frame, so the overlay reports a complete frame rather than the
    // partial counts accumulated up to the moment it happens to draw
    var lastPainted = 0; private set
    var lastCulled = 0; private set
    var lastQuads = 0; private set
    var lastGlyphRuns = 0; private set

    /** Total components in the tree, recomputed at most once a second (the walk is not free). */
    var componentCount = 0
        private set

    /**
     * Widgets present in the tree, recomputed alongside [componentCount].
     *
     * The cull rate is derived from this against the number actually painted, rather than from a
     * counter incremented at a cull site. Culling happens inside Elementa - a skipped widget's draw
     * never runs, so there is nowhere in our code to count it. Comparing "in the tree" against
     * "painted this frame" measures the real thing from the outside.
     */
    var widgetCount = 0
        private set

    private var lastCountAt = 0L

    /**
     * While true, every counter is a no-op. The dev overlay itself is built from real widgets, and
     * without this its own cards, labels and tree rows would show up in the very numbers it reports -
     * a screen would appear to gain dozens of widgets and hundreds of quads the moment you opened the
     * inspector. [BrassDevLayer] flips this on around its subtree's draw so the overlay measures the
     * screen, not itself.
     */
    var paused = false

    fun painted() { if (!paused) paintedWidgets++ }
    fun culled() { if (!paused) culledWidgets++ }
    fun quad(n: Int = 1) { if (!paused) quads += n }
    fun glyphRun() { if (!paused) glyphRuns++ }

    /** Call once at the start of each frame, before anything draws. */
    fun beginFrame(root: UIComponent?) {
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) {
            val dt = (now - lastFrameNanos) / 1_000_000f
            // Running sum: subtract the sample being overwritten, add the new one. Re-adding all 60
            // entries every frame was cheap but pointlessly so, and this is instrumentation code -
            // it has no business being the most arithmetic in the frame.
            frameSum += dt - frameTimes[sampleIndex]
            frameTimes[sampleIndex] = dt
            sampleIndex = (sampleIndex + 1) % SAMPLES
            if (samplesFilled < SAMPLES) samplesFilled++

            frameMs = if (samplesFilled == 0) 0f else frameSum / samplesFilled
            fps = if (frameMs > 0f) 1000f / frameMs else 0f
        }
        lastFrameNanos = now

        lastPainted = paintedWidgets
        lastCulled = culledWidgets
        lastQuads = quads
        lastGlyphRuns = glyphRuns
        paintedWidgets = 0
        culledWidgets = 0
        quads = 0
        glyphRuns = 0

        if (root != null && now - lastCountAt > 1_000_000_000L) {
            lastCountAt = now
            componentCount = 0
            widgetCount = 0
            count(root)
        }
    }

    private fun count(c: UIComponent) {
        // the dev overlay is not part of the UI being measured - skip its whole subtree
        if (c is BrassDevOverlay) return
        componentCount++
        if (c is BrassWidget) widgetCount++
        for (child in c.children) count(child)
    }

    /**
     * Percentage of the tree's widgets that were *not* painted last frame - i.e. that Elementa
     * culled. Derived rather than counted; see [widgetCount].
     */
    fun cullRate(): Float {
        if (widgetCount == 0) return 0f
        val skipped = (widgetCount - lastPainted).coerceAtLeast(0)
        return skipped * 100f / widgetCount
    }
}
