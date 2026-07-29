package net.swzo.brass.ui.kit.text

import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.constrain
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import java.awt.Color
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * The wrapping counterpart to [BrassLabel] - body text that **word-wraps** to the width it is given
 * and grows taller as it wraps, drawn entirely through [BrassFont] so it is a real [BrassWidget] (it
 * shows up in the dev-mode inspector, animates in with its neighbours, and can grow richer later -
 * e.g. Minecraft `Component` support - without callers changing).
 *
 * Replaces Elementa's `UIWrappedText`, which is not a widget: it appeared instantly while the controls
 * around it faded in, and it drew through a path the toolkit's instrumentation cannot see.
 *
 * Give it a width (usually `100.percent()`); it self-sizes its height from the wrap, so a container
 * with a `ChildBasedSizeConstraint` height (or a sibling anchored to its `getBottom()`) reflows
 * correctly when the width changes.
 */
class BrassWrappedLabel(
    text: String,
    /** Colour at rest; the entrance fade multiplies it. Named `tint` - see [BrassLabel]. */
    var tint: Color = Colors.UI_TEXT,
    var shadow: Boolean = true,
) : BrassWidget(BrassAccent.DEFAULT) {

    var text: String = text
        set(value) { if (field != value) { field = value; cachedWidth = -1f } }

    // Wrapping is recomputed only when the width or the text actually changes, not every frame - the
    // result is cached and reused, so an idle screen wraps nothing.
    private var cachedWidth = -1f
    private var cachedLines: List<String> = emptyList()

    init {
        chrome = BrassChrome.NONE
        constrain {
            height = basicHeightConstraint {
                (linesFor(getWidth()).size.coerceAtLeast(1) * BrassFont.LINE).toFloat()
            }
        }
    }

    private fun linesFor(maxWidth: Float): List<String> {
        val w = maxWidth.coerceAtLeast(1f)
        if (w == cachedWidth) return cachedLines
        cachedWidth = w
        cachedLines = wrap(text, w)
        return cachedLines
    }

    /** Greedy word-wrap: honour explicit line breaks, then pack words up to [maxWidth]. */
    private fun wrap(s: String, maxWidth: Float): List<String> {
        val out = ArrayList<String>()
        for (paragraph in s.split('\n')) {
            if (paragraph.isEmpty()) { out.add(""); continue }
            var line = StringBuilder()
            for (word in paragraph.split(' ')) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (BrassFont.width(this, candidate) <= maxWidth || line.isEmpty()) {
                    // a single word longer than the line still goes on its own line rather than vanishing
                    line = StringBuilder(candidate)
                } else {
                    out.add(line.toString())
                    line = StringBuilder(word)
                }
            }
            out.add(line.toString())
        }
        return out
    }

    /** The wrapped height at the current width - for callers that size a container around it. */
    fun contentHeight(): Float = (linesFor(getWidth()).size.coerceAtLeast(1) * BrassFont.LINE).toFloat()

    /**
     * The width of the **last** wrapped line - where a trailing annotation goes.
     *
     * A chat's "(edited)" marker, a footnote dagger, an inline status dot: all of them belong at the end
     * of the text, which after wrapping is a point no caller can compute for itself. Without this the
     * only options are to right-align the marker (leaving a gap after short messages) or to put it on a
     * line of its own (which reads as another message).
     */
    fun lastLineWidth(): Float =
        BrassFont.width(this, linesFor(getWidth()).lastOrNull().orEmpty())

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val lines = linesFor(w.toFloat())
        val color = withAlpha(tint, entranceFade)
        var ly = y.toFloat()
        for (line in lines) {
            if (line.isNotEmpty()) BrassFont.draw(m, this, line, x.toFloat(), ly, color, shadow)
            ly += BrassFont.LINE
        }
    }

    companion object : BrassDemoSource {

        /** A paragraph that wraps to its box. */
        override fun demo() = BrassDemo("wrapped-label", "Wrapped label", 220f, 60f) {
            BrassWrappedLabel(
                "A longer run of text that wraps to the width it is given, rather than " +
                    "running off the edge of whatever container it was put in.",
            )
        }
    }
}
