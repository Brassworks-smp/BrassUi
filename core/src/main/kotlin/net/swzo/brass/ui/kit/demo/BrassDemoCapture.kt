package net.swzo.brass.ui.kit.demo

import java.awt.image.BufferedImage

/**
 * The seam between the demo browser and whatever can actually render to a file.
 * ### Why this is an interface and not just code
 * Reading a rectangle of pixels off the frame, and encoding a PNG or GIF, both need a GL context and
 * game classes the core toolkit deliberately has no access to — the same reason
 * [net.swzo.brass.ui.kit.platform.BrassPlatform] exists for item and entity drawing. So the browser
 * above ([BrassDemoBrowser]) is pure toolkit code that knows *what* to capture and when, and the host
 * binds something that knows *how*.
 * Unbound, the browser still runs: demos play, scenes step, everything previews. Only the shutter and
 * the record button go dark, with a line saying why. That matters because the desktop gallery has no
 * Minecraft under it at all and should not be a broken screen.
 * ### Why frames come back as images rather than being written directly
 * Because a recording is a *decision made after the fact*. The browser accumulates frames while the
 * button is held down and only knows it has a GIF when the user stops; and a run of frames that turn
 * out identical should collapse to a PNG rather than becoming an animation of nothing happening. Both
 * of those are the browser's call, so the host's job stops at handing back pixels.
 */
interface BrassDemoCapture {

    /**
     * Read the rectangle at ([x], [y]) sized [width] x [height] — in GUI units, on screen — back out
     * of the frame that has just been drawn.
     * ### Why it reads the real screen rather than drawing offscreen
     * Because the widget is *already on screen*, drawn by the normal pass, with every one of its clip
     * rectangles resolved against the real framebuffer — which is the one thing an offscreen target
     * could not reproduce. Anything that scissors its contents (the tree, the table, the code view,
     * the chart, the accordion, the dropdown) came out blank when redrawn into a small surface, because
     * `ScissorEffect` computes its clip in screen pixels and that surface was not the screen. Reading
     * the region the widget occupies sidesteps the whole problem: whatever rendered correctly is what
     * gets captured.
     * Call this **after** the frame has drawn, so the pixels are the widget as just shown. The mouse
     * cursor is not in the result — it is the window system's own cursor, composited over the frame
     * and never part of it. Returns null if the region could not be read.
     */
    fun grab(x: Float, y: Float, width: Float, height: Float): BufferedImage?

    fun writePng(name: String, image: BufferedImage): String?

    fun writeGif(name: String, frames: List<BufferedImage>, fps: Int): String?

    fun release() {}

    companion object {

        var current: BrassDemoCapture? = null
            private set

        fun bind(capture: BrassDemoCapture) {
            current = capture
        }

        val available: Boolean get() = current != null
    }
}
