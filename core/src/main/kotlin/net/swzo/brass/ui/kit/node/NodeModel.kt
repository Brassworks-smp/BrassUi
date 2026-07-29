package net.swzo.brass.ui.kit.node

import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassEased
import java.awt.Color

/**
 * A wire's colour-coded socket kind. A wire is only allowed between an output and an input of the
 * **same** type; the colour is read from the theme on every draw so it follows a retheme.
 *
 * The four built-ins cover the usual shapes (execution flow, a number, a colour, a vector), and a
 * library user can make their own - a `PortType` is just an id plus a colour, and two ports connect iff
 * their ids match.
 */
class PortType(val id: String, private val colorOf: () -> Color) {
    fun color(): Color = colorOf()
    override fun equals(other: Any?): Boolean = other is PortType && other.id == id
    override fun hashCode(): Int = id.hashCode()

    companion object {
        val FLOW = PortType("flow") { Colors.UI_TEXT }
        val NUMBER = PortType("number") { Colors.BRASS_400 }
        val COLOR = PortType("color") { Colors.PATINA_400 }
        val VECTOR = PortType("vector") { Colors.WARN }
        val BUILTIN = listOf(FLOW, NUMBER, COLOR, VECTOR)
    }
}

/** One socket on a node's edge. */
class Port(val name: String, val type: PortType)

/**
 * A node **type**: its title, accent, ports and a factory for a fresh set of [NodeField]s. Registered
 * in a [NodeRegistry], it drives both the add-node menu and save/load, so a graph can be rebuilt from a
 * file knowing only type ids.
 */
class NodeType(
    val id: String,
    val title: String,
    val accent: BrassAccent,
    val inputs: List<Port> = emptyList(),
    val outputs: List<Port> = emptyList(),
    /** Built fresh per node, so two nodes of a type never share field state. */
    val makeFields: () -> List<NodeField> = { emptyList() },
    val width: Float = 156f,
)

/**
 * The set of node types an editor can build. Register the ones your app offers (including custom types
 * with custom [NodeField]s); the editor reads it for the add menu and [NodeGraph] reads it to
 * deserialize. Ordering is preserved for the menu.
 */
class NodeRegistry {
    private val map = LinkedHashMap<String, NodeType>()

    fun register(type: NodeType): NodeRegistry {
        map[type.id] = type
        return this
    }

    operator fun get(id: String): NodeType? = map[id]
    fun all(): List<NodeType> = map.values.toList()
}

/**
 * A live node in a graph: its type, canvas position, collapse state, and the field instances holding
 * its values. Everything animatable is a [BrassEased] so every state change (open, hover, lift, select,
 * collapse) is a smooth transition rather than a jump - the same animation primitive the rest of the
 * toolkit uses.
 */
class GraphNode internal constructor(
    val id: Int,
    val type: NodeType,
    var x: Float,
    var y: Float,
) {
    var collapsed: Boolean = false
    var selected: Boolean = false
    val fields: List<NodeField> = type.makeFields()

    /** 0 = just spawned / closing, 1 = fully open - the miniature-modal pop. */
    val pop = BrassEased(0f, speed = 13f)
    val hover = BrassEased(0f, speed = 14f)
    val lift = BrassEased(0f, speed = 18f)
    val sel = BrassEased(0f, speed = 14f)

    /** 0 = expanded, 1 = rolled up to header + ports. */
    val roll = BrassEased(0f, speed = 14f)

    val glowIn = FloatArray(type.inputs.size)
    val glowOut = FloatArray(type.outputs.size)

    /** True once the node is animating out; the editor removes it when [pop] reaches 0. */
    var closing: Boolean = false

    val width: Float get() = type.width

    fun visibleFields(): List<NodeField> = fields.filter { it.visibleWhen() }
    fun field(key: String): NodeField? = fields.firstOrNull { it.key == key }

    /** Copy this node's field values onto [other] (same type), for duplicate/paste. */
    fun copyValuesTo(other: GraphNode) {
        for (f in fields) other.field(f.key)?.decode(f.encode())
        other.collapsed = collapsed
        other.roll.snapTo(if (collapsed) 1f else 0f)
    }
}

/** A wire between an output and an input, with a connect flash and an eased selection outline. */
class Link(val from: GraphNode, val fromPort: Int, val to: GraphNode, val toPort: Int) {
    var selected: Boolean = false
    val sel = BrassEased(0f, speed = 14f)
    var flash: Float = 0f

    fun portType(): PortType = from.type.outputs[fromPort].type
}

/**
 * The **pure-data** graph: nodes and links, plus the mutation ops an editor drives. Holding the graph
 * as plain data (rather than as a tree of live components) is what makes save/load, copy/paste and undo
 * trivial - each is just serializing or cloning this.
 */
class NodeGraph(val registry: NodeRegistry) {

    val nodes = ArrayList<GraphNode>()
    val links = ArrayList<Link>()

    private var nextId = 1

    /** Spawn a node of [typeId] at ([x],[y]), animating it in. Null if the type is not registered. */
    fun spawn(typeId: String, x: Float, y: Float): GraphNode? {
        val type = registry[typeId] ?: return null
        val node = GraphNode(nextId++, type, x, y).also { it.pop.target = 1f }
        nodes.add(node)
        return node
    }

    /** Add a node whose id/type came from a file, keeping ids stable. */
    internal fun adopt(id: Int, typeId: String, x: Float, y: Float): GraphNode? {
        val type = registry[typeId] ?: return null
        if (id >= nextId) nextId = id + 1
        val node = GraphNode(id, type, x, y).also { it.pop.snapTo(1f) }
        nodes.add(node)
        return node
    }

    fun remove(node: GraphNode) {
        links.removeAll { it.from === node || it.to === node }
        nodes.remove(node)
    }

    /** Wire an output to an input if the types match; replaces whatever fed that input. Returns it. */
    fun link(from: GraphNode, fromPort: Int, to: GraphNode, toPort: Int): Link? {
        val out = from.type.outputs.getOrNull(fromPort) ?: return null
        val inp = to.type.inputs.getOrNull(toPort) ?: return null
        if (out.type != inp.type || from === to) return null
        links.removeAll { it.to === to && it.toPort == toPort }
        return Link(from, fromPort, to, toPort).also { links.add(it) }
    }

    fun byId(id: Int): GraphNode? = nodes.firstOrNull { it.id == id }

    // Persistence

    /** Serialize to the native JSON format. */
    fun toJson(): String = NodeIO.toJson(this)

    /** Replace this graph's contents from [json]. Unknown node types are skipped. */
    fun load(json: String) {
        nodes.clear(); links.clear()
        NodeIO.into(this, json)
    }

    companion object {
        fun fromJson(registry: NodeRegistry, json: String): NodeGraph =
            NodeGraph(registry).also { it.load(json) }
    }
}
