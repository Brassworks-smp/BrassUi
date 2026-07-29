package net.swzo.brass.ui.kit.node

import net.swzo.brass.ui.Colors
import java.awt.Color

enum class FrameTone {
    BRASS, PATINA, NEUTRAL, DANGER;

    fun color(): Color = when (this) {
        BRASS -> Colors.BRASS_400
        PATINA -> Colors.PATINA_400
        NEUTRAL -> Colors.UI_TEXT_DARK
        DANGER -> Colors.DANGER
    }
}

data class GraphFrame(
    val id: Int,
    var title: String,
    val nodeIds: MutableSet<Int>,
    var tone: FrameTone = FrameTone.BRASS,
    var autoResize: Boolean = true,
    var parentFrameId: Int? = null,
    var x: Float = 0f,
    var y: Float = 0f,
    var width: Float = 160f,
    var height: Float = 100f,
    var customColor: Int? = null,
) {
    fun color(): Color = customColor?.let { Color(it, true) } ?: tone.color()

    fun resizeToContents(graph: NodeGraph, padding: Float = 18f) {
        if (!autoResize) return
        val nodes = nodeIds.mapNotNull(graph::byId)
        if (nodes.isEmpty()) return
        x = nodes.minOf { it.x } - padding
        y = nodes.minOf { it.y } - padding - 12f
        width = nodes.maxOf { it.x + it.width } - x + padding
        height = nodes.maxOf { it.y + NodeLayout.height(it) } - y + padding
    }
}

data class GraphComment(
    val id: Int,
    var text: String,
    var x: Float,
    var y: Float,
    var width: Float = 132f,
    var height: Float = 48f,
    var tone: FrameTone = FrameTone.PATINA,
    var customColor: Int? = null,
) {
    fun color(): Color = customColor?.let { Color(it, true) } ?: tone.color()
}

data class GraphBookmark(
    var name: String,
    var panX: Float,
    var panY: Float,
    var zoom: Float,
)
