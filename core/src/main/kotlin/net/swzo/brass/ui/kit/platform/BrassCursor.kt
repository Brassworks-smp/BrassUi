package net.swzo.brass.ui.kit.platform

import net.swzo.brass.ui.kit.platform.BrassCursor.apply


/**
 * The mouse cursor the UI wants this frame.
 * ### Why requests rather than direct sets
 * Widgets draw in tree order, and several can be under the cursor at once - a button inside a panel
 * inside a resizable window. If each set the cursor directly, the last one to draw would win, which
 * is the *bottom* of the stack rather than the thing the user is actually pointing at.
 * So a widget **requests** a cursor while it is hovered, the highest-priority request wins, and the
 * screen applies it once per frame in [apply]. Resize handles outrank buttons, buttons outrank text,
 * and everything outranks the arrow - which matches what the user is aiming at when regions overlap.
 * The actual cursor change is a platform call (GLFW, via the game window handle), so it goes through
 * [BrassPlatform]. With no platform bound this degrades to doing nothing.
 */
object BrassCursor {

    enum class Kind {
        ARROW,
        TEXT,
        HAND,
        MOVE,
        CROSSHAIR,
        RESIZE_H,
        RESIZE_V,
        RESIZE_NWSE,
        RESIZE_NESW,
    }

    private var requested: Kind = Kind.ARROW
    private var applied: Kind? = null

    fun request(kind: Kind) {
        if (kind.ordinal > requested.ordinal) requested = kind
    }

    fun apply() {
        val want = requested
        requested = Kind.ARROW
        if (want == applied) return
        applied = want
        runCatching { BrassPlatform.current?.setCursor(want) }
    }

    fun forget() {
        applied = null
    }
}
