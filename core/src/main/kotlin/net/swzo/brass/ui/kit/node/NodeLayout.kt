package net.swzo.brass.ui.kit.node

/**
 * Where every piece of a node sits, in **world** units, shared by the drawing and the hit-testing so a
 * click always lands exactly on the pixel that was drawn. Ports keep their positions when a node rolls
 * up (only the fields below fold away), so wires stay put through a collapse.
 */
object NodeLayout {

    const val HEADER = 18f
    const val PORT_ROW = 15f
    const val FIELD_ROW = 16f
    const val PAD = 8f
    const val CTRL_W = 66f
    const val GRID = 24f
    const val PORT_R = 4f
    const val PORT_HIT = 7f

    fun portRows(node: GraphNode): Int = maxOf(node.type.inputs.size, node.type.outputs.size)

    /** Height of the header + ports band (never folds). */
    fun baseHeight(node: GraphNode): Float = HEADER + 4f + portRows(node) * PORT_ROW + 6f

    private fun visN(node: GraphNode): Int = node.visibleFields().size

    /** Height of the fields block when fully expanded, or 0 when there are none. */
    fun fieldsHeight(node: GraphNode): Float {
        val n = visN(node)
        return if (n > 0) 1f + 5f + n * FIELD_ROW + 4f else 0f
    }

    /** Live height, folding the fields block away as the node rolls up. */
    fun height(node: GraphNode): Float = baseHeight(node) + fieldsHeight(node) * (1f - node.roll.value)

    fun portY(node: GraphNode, i: Int): Float = node.y + HEADER + 4f + i * PORT_ROW + PORT_ROW / 2f
    fun inputX(node: GraphNode): Float = node.x
    fun outputX(node: GraphNode): Float = node.x + node.width

    fun fieldsTop(node: GraphNode): Float = node.y + baseHeight(node) + 1f + 5f

    /** The full row rect of visible field [row]: `[x1,y1,x2,y2]` in world units. */
    fun fieldRow(node: GraphNode, row: Int): FloatArray {
        val y1 = fieldsTop(node) + row * FIELD_ROW
        return floatArrayOf(node.x + PAD, y1, node.x + node.width - PAD, y1 + FIELD_ROW - 3f)
    }

    /** Left edge of a field row's control area (the right [CTRL_W] of the row). */
    fun controlLeft(node: GraphNode): Float = node.x + node.width - PAD - CTRL_W

    fun chevronX(node: GraphNode): Float = node.x + node.width - 11f
    fun chevronY(node: GraphNode): Float = node.y + HEADER / 2f
}
