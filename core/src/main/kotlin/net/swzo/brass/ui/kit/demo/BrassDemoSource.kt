package net.swzo.brass.ui.kit.demo

/**
 * Implemented by a widget's **companion object** to declare what the widget's showcase looks like.
 *
 * ```kotlin
 * class BrassToggle(...) : BrassWidget(...) {
 *
 *     // …the widget…
 *
 *     companion object : BrassDemoSource {
 *         override fun demo() = BrassDemo("toggle", "Toggle", 30f, 16f) {
 *             BrassToggle(initial = false)
 *         }
 *     }
 * }
 * ```
 *
 * ### Why a companion rather than an instance method
 *
 * A demo has to *construct* the widget, including whatever sub-widgets it needs to be worth looking at
 * — a tree needs nodes, a split pane needs two panels, an inventory grid needs a linked hotbar to drag
 * a stack into. An instance method would mean building a widget in order to ask it how to build a
 * widget, and would tempt a demo into showing the instance it was called on, which is exactly the
 * shared-state bug that makes the second scene of a clip start halfway through the first. A companion
 * has no instance to be confused by.
 *
 * Kotlin has no static interface members, so "every widget implements a demo method" is spelled as a
 * companion implementing this. That also makes it **optional**, which is the intent: a widget with no
 * demo is a widget whose companion does not implement this, and nothing breaks.
 *
 * ### What makes a good demo
 *
 * Build the widget with enough around it to be worth looking at, and stop. A demo does not script what
 * the widget then does — that is the recorder's job, by hand — so the question to answer here is only
 * "what does this need in order to be demonstrable at all". For a button, itself. For a tree, a
 * two-level hierarchy with siblings, or the indent guides have nothing to connect. For an inventory
 * grid, a *second linked grid*, because a stack cannot be carried across a boundary that does not
 * exist. Sample content is deliberately generic: a demo documents the widget, not whatever app it
 * happens to ship in.
 */
interface BrassDemoSource {

    /** How this widget shows itself off. Called fresh whenever a demo is needed. */
    fun demo(): BrassDemo
}
