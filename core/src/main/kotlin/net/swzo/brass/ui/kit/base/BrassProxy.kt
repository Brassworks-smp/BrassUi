package net.swzo.brass.ui.kit.base

import gg.essential.elementa.UIComponent

/**
 * Lets one component stand in for a widget's click target - the label-activates-the-checkbox
 * behaviour every desktop UI has, where the caption beside a toggle is part of the toggle.
 *
 * ```kotlin
 * val toggle = BrassToggle(false)
 * val label = BrassText.body("Show FPS")
 * toggle.proxiedBy(label)      // clicking or hovering the label now drives the toggle
 * ```
 *
 * Binding forwards two things:
 *
 * - **Clicks** call the target's [BrassWidget.proxyActivate], which is what the widget does on a
 *   click of its own (toggling, ticking, pressing).
 * - **Hover**, via [BrassWidget.proxyHovered], so the widget lights up while the cursor is over the
 *   proxy. Without this the label would be clickable but dead-looking, which reads as a bug rather
 *   than a feature - the affordance is the point.
 *
 * A widget can have any number of proxies, and a proxy can be any component: a text label, an icon,
 * a whole row container. Binding a row is the usual case, since it makes the entire line clickable.
 */
object BrassProxy {

    /**
     * Make [proxy] drive [target]. Returns [proxy] so it can be used inline in a `childOf` chain.
     *
     * Only the left mouse button activates; other buttons are left alone so a proxy sitting inside a
     * panel with its own right-click menu keeps working.
     */
    fun bind(proxy: UIComponent, target: BrassWidget): UIComponent {
        proxy.onMouseClick { e ->
            if (e.mouseButton != 0) return@onMouseClick
            // A proxy is often an *ancestor* of the widget - a settings row that contains the toggle.
            // Elementa bubbles a click up from the widget to that row, so a click that landed on the
            // toggle itself would fire twice: once as the toggle's own activation, once here - and two
            // toggles cancel out, which is exactly the "clicking the switch does nothing" bug. Only
            // stand in for clicks that did *not* land on the widget (or something inside it).
            if (BrassTree.isDescendantOf(e.target, target)) return@onMouseClick
            target.proxyActivate()
        }
        proxy.onMouseEnter { target.proxyHovered = true }
        proxy.onMouseLeave { target.proxyHovered = false }
        return proxy
    }
}

/** Bind [proxy] to this widget - see [BrassProxy.bind]. Returns the widget for chaining. */
fun <T : BrassWidget> T.proxiedBy(proxy: UIComponent): T {
    BrassProxy.bind(proxy, this)
    return this
}
