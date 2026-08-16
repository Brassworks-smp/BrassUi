package net.swzo.brass.ui.kit.node

/**
 * Where every piece of a node sits, in **world** units, shared by the drawing and the hit-testing so a
 * click always lands exactly on the pixel that was drawn. Ports keep their positions when a node rolls
 * up (only the fields below fold away), so wires stay put through a collapse.
 */
object NodeLayout {

    const val HEADER = 18f
    const val PORT_ROW = 15f
    const val FIELD_ROW = 22f
    const val FIELD_CONTROL_H = 16f
    const val PAD = 8f
    const val CTRL_W = 66f
    const val GRID = 24f
    const val PORT_R = 4f
    const val PORT_HIT = 7f

    fun portRows(node: GraphNode): Int = maxOf(
        node.effectiveInputs().count { !it.hidden },
        node.effectiveOutputs().count { !it.hidden },
    )

    /** Height of the header + ports band (never folds). */
    fun baseHeight(node: GraphNode): Float = HEADER + 4f + portRows(node) * PORT_ROW + 6f

    fun fieldsHeight(node: GraphNode): Float {
        val rows = node.fields.sumOf { it.reveal.value.toDouble() }.toFloat()
        val presence = node.fields.maxOfOrNull { it.reveal.value } ?: 0f
        return (1f + 5f + 4f) * presence + rows * FIELD_ROW
    }

    fun height(node: GraphNode): Float = baseHeight(node) + fieldsHeight(node) * (1f - node.roll.value)

    private fun portY(node: GraphNode, ports: List<Port>, i: Int): Float {
        val row = ports.take(i).count { !it.hidden }
        return node.y + HEADER + 4f + row * PORT_ROW + PORT_ROW / 2f
    }

    fun inputY(node: GraphNode, i: Int): Float = portY(node, node.effectiveInputs(), i)
    fun outputY(node: GraphNode, i: Int): Float = portY(node, node.effectiveOutputs(), i)
    fun inputX(node: GraphNode): Float = node.x
    fun outputX(node: GraphNode): Float = node.x + node.width

    fun fieldsTop(node: GraphNode): Float = node.y + baseHeight(node) + 1f + 5f

    fun fieldRow(node: GraphNode, field: NodeField): FloatArray {
        val index = node.fields.indexOf(field).coerceAtLeast(0)
        val offset = node.fields.take(index).sumOf { it.reveal.value.toDouble() }.toFloat() * FIELD_ROW
        val y1 = fieldsTop(node) + offset
        return floatArrayOf(node.x + PAD, y1, node.x + node.width - PAD, y1 + FIELD_CONTROL_H)
    }

    fun controlLeft(node: GraphNode): Float = node.x + node.width - PAD - CTRL_W

    fun chevronX(node: GraphNode): Float = node.x + node.width - 11f
    fun chevronY(node: GraphNode): Float = node.y + HEADER / 2f
}
