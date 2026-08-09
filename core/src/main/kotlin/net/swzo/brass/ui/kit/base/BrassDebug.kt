@file:Suppress("unused")

package net.swzo.brass.ui.kit.base

import gg.essential.elementa.UIComponent
import net.swzo.brass.ui.kit.base.BrassDebug.strict

/**
 * Turns silent misuse into a message that says what went wrong.
 * ### Why this exists
 * Several of the toolkit's rules were documented and unenforced, so breaking one produced a crash
 * with no obvious cause, or nothing at all:
 * - Sizing a child `100.percent()` inside a container measured by
 *   [net.swzo.brass.ui.kit.layout.BrassLayout.spanningChildrenHeight] recurses until the **stack
 *   overflows** - documented in that file's KDoc, with nothing to catch it.
 * - Forgetting to reserve scrollbar width silently paints the bar over the content.
 * - Measuring a component before parenting it answers from the wrong font provider and caches the
 *   result forever.
 * `BrassPopup`'s "you built this with `scrollingBody = false`" message is the model: it names the
 * mistake and the fix. These do the same for the rules that had no check at all.
 * [strict] is on by default in a development environment and costs a boolean test per call when off.
 */
object BrassDebug {

    /**
     * Whether misuse throws rather than being ignored.
     * Defaults to on when assertions are enabled (`-ea`, which every IDE run and Gradle test sets),
     * so a developer gets the message and a player never sees a crash the previous behaviour would
     * merely have rendered oddly.
     */
    @JvmStatic
    var strict: Boolean = BrassDebug::class.java.desiredAssertionStatus()

    fun violation(message: () -> String) {
        val text = message()
        if (strict) throw IllegalStateException("brassui: $text")
        if (reported.add(text)) System.err.println("[brassui] $text")
    }

    fun require(condition: Boolean, message: () -> String) {
        if (!condition) violation(message)
    }

    fun checkNotCircular(parent: UIComponent, child: UIComponent, axis: String) {
        if (!strict) return
        val constraint = if (axis == "height") child.constraints.height else child.constraints.width
        val name = constraint.javaClass.simpleName
        if (name.contains("RelativeConstraint") || name.contains("FillConstraint")) {
            violation {
                "${child.javaClass.simpleName} is sized as a percentage of its parent's $axis, but " +
                    "${parent.javaClass.simpleName} measures its own $axis from its children - the two " +
                    "will ask each other until the stack overflows. Give the child a pixel or " +
                    "content-derived $axis, or measure the other axis on the parent."
            }
        }
    }

    private val reported = HashSet<String>()
}
