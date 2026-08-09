@file:Suppress("unused")
package net.swzo.brass.ui.kit.node

fun interface NodeRenderer {
    fun draw(context: NodeDrawCtx, graph: NodeGraph, node: GraphNode)
}

fun interface ConnectionRule {
    fun validate(
        graph: NodeGraph,
        from: GraphNode,
        fromPort: Int,
        to: GraphNode,
        toPort: Int,
    ): String?
}

data class NodeEditorAction(
    val label: String,
    val perform: (BrassNodeEditor, GraphNode?) -> Unit,
)

interface NodeEditorPlugin {
    val id: String
    fun install(api: NodePluginApi)
}

/**
 * The stable plugin surface. It deliberately exposes registrations rather than editor internals, so
 * plugins can add types, connection rules, custom rendering and contextual UI without depending on
 * the controller's private mode state.
 */
class NodePluginApi internal constructor(private val registry: NodeRegistry) {
    fun register(type: NodeType) { registry.register(type) }
    fun register(type: PortType) { registry.registerPortType(type) }
    fun connectionRule(rule: ConnectionRule) { registry.connectionRules += rule }
    fun nodeAction(action: NodeEditorAction) { registry.nodeActions += action }
    fun canvasAction(action: NodeEditorAction) { registry.canvasActions += action }
}
