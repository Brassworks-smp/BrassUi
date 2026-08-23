package net.swzo.brass.ui.kit.html.internal

import com.labymedia.ultralight.plugin.clipboard.UltralightClipboard
import org.lwjgl.glfw.GLFW

/**
 * Clipboard bridged to the OS through GLFW, which is what both hosts already own. Ultralight calls
 * [readPlainText]/[writePlainText] from its UI thread; GLFW's clipboard is global so no window handle
 * is needed.
 */
internal class HtmlClipboard : UltralightClipboard {
    override fun readPlainText(): String = GLFW.glfwGetClipboardString(0) ?: ""
    override fun writePlainText(text: String) {
        GLFW.glfwSetClipboardString(0, text)
    }
    override fun clear() {
        GLFW.glfwSetClipboardString(0, "")
    }
}
