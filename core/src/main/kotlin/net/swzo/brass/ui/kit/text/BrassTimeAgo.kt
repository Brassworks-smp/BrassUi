package net.swzo.brass.ui.kit.text

import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.surface.BrassTooltip
import java.awt.Color
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource

/**
 * A timestamp that reads as "how long ago" and keeps itself current - `just now`, `4m ago`,
 * `3d ago`.
 *
 * ```kotlin
 * BrassTimeAgo(lastSeen)                     // "12m ago"
 * BrassTimeAgo(listedAt, suffix = false)     // "12m"
 * ```
 *
 * ### Why a widget and not a helper
 *
 * The formatting alone would be a function. The *updating* is the reason this is a component: a
 * friends list rendered once says "1m ago" for the rest of the session, and every screen that has
 * ever formatted its own relative times has ended up with some of them stale and some not, depending
 * on which happened to be rebuilt. Here the text is computed during the draw, so it can only ever be
 * right.
 *
 * It costs nothing to do that per frame - it is a subtraction and a comparison - but the *string* is
 * rebuilt only when [BrassRelativeTime.bucketOf] changes, so a list of two hundred of these is not
 * allocating two hundred strings sixty times a second.
 *
 * The formatting itself lives in [BrassRelativeTime], where it can be tested.
 *
 * The full timestamp is available on hover, because a relative time is the right default and the
 * wrong thing to have to guess at when it matters.
 */
class BrassTimeAgo(
    /** When the thing happened, as epoch millis. */
    var timestamp: Long = System.currentTimeMillis(),
    /** Append "ago". Off for a compact column where the heading already says so. */
    var suffix: Boolean = true,
    var tint: Color = Colors.UI_TEXT_DARK,
    /** Absolute form shown on hover. Null uses the platform default via [java.util.Date]. */
    var absolute: ((Long) -> String)? = null,
) : BrassWidget(BrassAccent.DEFAULT) {

    /** The last string produced, and the bucket it was produced for. */
    private var cached: String = ""
    private var cachedFor: Long = Long.MIN_VALUE

    init {
        chrome = BrassChrome.NONE
        // Intrinsic width as well as height, exactly as BrassLabel does it. Without it the component
        // resolved to Elementa's default zero width and drew nothing at all - the string was fitted to
        // a zero-width box every frame - so a row of these was an invisible gap in the layout. A
        // caller's own constrain{} still wins, since it is applied after construction.
        constrain {
            width = basicWidthConstraint { contentWidth() }
            height = BrassFont.LINE.pixels()
        }
        // Attached once with a supplier - never from onMouseEnter, which mutates the listener list
        // while Elementa is iterating it.
        BrassTooltip.attachLazy(this, { absolute?.invoke(timestamp) ?: java.util.Date(timestamp).toString() })
    }

    /** The text as it stands right now. */
    fun text(): String {
        val elapsed = System.currentTimeMillis() - timestamp
        val bucket = BrassRelativeTime.bucketOf(elapsed)
        if (bucket != cachedFor) {
            cachedFor = bucket
            cached = BrassRelativeTime.format(elapsed, suffix)
        }
        return cached
    }

    /** Width the text needs, for a caller sizing a column. */
    fun contentWidth(): Float = BrassFont.width(this, text())

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val label = BrassFont.fit(this, text(), w.toFloat())
        BrassFont.draw(m, this, label, x.toFloat(), y + (h - BrassFont.LINE) / 2f, tint)
    }

    companion object : BrassDemoSource {

        /**
         * A relative timestamp, with the absolute form revealed on hover.
         *
         * The offset is fixed rather than "now" so the text reads "5m ago" on every run — a demo whose
         * output changes with the wall clock produces a different image every capture and makes every
         * diff noise.
         */
        override fun demo() = BrassDemo("time-ago", "Time ago", 110f, 14f) {
            BrassTimeAgo(System.currentTimeMillis() - 5 * 60_000L)
        }
    }
}
