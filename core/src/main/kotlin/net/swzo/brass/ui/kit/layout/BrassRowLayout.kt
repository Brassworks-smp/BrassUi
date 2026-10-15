package net.swzo.brass.ui.kit.layout

object BrassRowLayout {

    data class Solved(
        val titleX: Float,
        val titleW: Float,
        val metaX: Float?,
        val tagX: Float?,
        val actionsX: Float,
    )

    fun solve(
        width: Float,
        leadingW: Float,
        metaW: Float,
        tagW: Float,
        actionsW: Float,
        pad: Float = BrassSpacing.PAD,
        gap: Float = 6f,
        minTitle: Float = 24f,
    ): Solved {
        val right = (width - pad).coerceAtLeast(pad)
        val actionsX = (right - actionsW).coerceAtLeast(pad)
        val titleX = pad + if (leadingW > 0f) leadingW + gap else 0f

        var showMeta = metaW > 0f
        var showTag = tagW > 0f
        fun trailingStart(): Float {
            var x = if (actionsW > 0f) actionsX - gap else right
            if (showTag) x -= tagW + gap
            if (showMeta) x -= metaW + gap
            return x
        }
        if (showMeta && trailingStart() - titleX < minTitle) showMeta = false
        if (showTag && trailingStart() - titleX < minTitle) showTag = false

        val start = trailingStart()
        val titleW = (start - titleX).coerceAtLeast(0f)
        var cursor = start + gap
        val metaX = if (showMeta) cursor.also { cursor += metaW + gap } else null
        val tagX = if (showTag) cursor else null
        return Solved(titleX, titleW, metaX, tagX, actionsX)
    }
}
