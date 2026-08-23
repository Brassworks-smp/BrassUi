@file:Suppress("unused")
package net.swzo.brass.ui.kit.html

/**
 * Everything a host needs to create one HTML view. Extensible by construction: hosts can throw extra
 * [jsBindings] at the page (bound onto `window.<name>`), and the [deviceScale] drives crispness.
 */
class BrassHtmlConfig(
    /** Initial view size in device pixels; the widget resizes it to fit on the first frame. */
    var width: Int = 0,
    var height: Int = 0,
    /** Transparent background (the widget's card shows through the page). */
    var transparent: Boolean = true,
    /** Device pixels per CSS pixel; 1.0 on a 1:1 window, the GUI scale in a scaled Minecraft window. */
    var deviceScale: Double = 1.0,
    /** Custom user agent string, if the page sniffs for one. */
    var userAgent: String? = null,
    /** Extra `window.<name> = <value>` bindings, installed on every page load. */
    var jsBindings: MutableMap<String, Any> = mutableMapOf(),
)
