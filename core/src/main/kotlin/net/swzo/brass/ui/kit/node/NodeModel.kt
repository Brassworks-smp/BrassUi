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
enum class WireStyle { SOLID, DASHED, FLOW }

enum class PortShape { ROUND, SQUARE, DIAMOND, DOT, CROSS }

/**
 * Visual and behavioural metadata shared by every port of a connection type. Compatibility is
 * deliberately a predicate rather than an equality check: plugins can accept a family of related
 * types without teaching the editor about them.
 */
class PortType(
    val id: String,
    val wireStyle: WireStyle = WireStyle.SOLID,
    val arrow: Boolean = false,
    val symbol: String? = null,
    accepts: (PortType) -> Boolean = { it.id == id },
    private val colorOf: () -> Color,
) {
    private val compatibility = accepts
    fun color(): Color = colorOf()
    fun accepts(other: PortType): Boolean = compatibility(other)
    override fun equals(other: Any?): Boolean = other is PortType && other.id == id
    override fun hashCode(): Int = id.hashCode()

    companion object {
        val FLOW = PortType("flow", wireStyle = WireStyle.FLOW, arrow = true, symbol = "›") { Colors.UI_TEXT }
        val NUMBER = PortType("number") { Colors.BRASS_400 }
        val COLOR = PortType("color") { Colors.PATINA_400 }
        val VECTOR = PortType("vector") { Colors.WARN }
        val BUILTIN = listOf(FLOW, NUMBER, COLOR, VECTOR)
    }
}

/**
 * One socket on a node's edge.
 *
 * [maxConnections] is enforced independently on both ends. A single input keeps Blueprint's useful
 * replace-on-drop behaviour; a full output or multi-input rejects another connection. Hidden ports keep
 * their stable list index for save compatibility but are omitted from layout and hit-testing. Dynamic
 * ports are metadata for hosts that rebuild a [NodeType] from plugin state.
 */
class Port(
    val name: String,
    val type: PortType,
    val shape: PortShape = PortShape.ROUND,
    val size: Float = 1f,
    val maxConnections: Int = 0,
    val optional: Boolean = false,
    val hidden: Boolean = false,
    val dynamic: Boolean = false,
    val endLabel: String? = null,
    val showLabel: Boolean = true,
) {
    init {
        require(size in 0.5f..2f) { "port size must be between 0.5 and 2" }
        require(maxConnections >= 0) { "maxConnections must be non-negative (0 means direction default)" }
    }

    val multiConnect: Boolean get() = maxConnections > 1
}

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
    /** Optional runtime implementation. A graph remains editable and serializable without one. */
    val executor: NodeExecutor? = null,
    /** Optional complete visual replacement for this node type. */
    val renderer: NodeRenderer? = null,
)

/**
 * The set of node types an editor can build. Register the ones your app offers (including custom types
 * with custom [NodeField]s); the editor reads it for the add menu and [NodeGraph] reads it to
 * deserialize. Ordering is preserved for the menu.
 */
class NodeRegistry {
    private val map = LinkedHashMap<String, NodeType>()
    private val portMap = LinkedHashMap<String, PortType>()
    internal val connectionRules = ArrayList<ConnectionRule>()
    internal val nodeActions = ArrayList<NodeEditorAction>()
    internal val canvasActions = ArrayList<NodeEditorAction>()
    private val pluginIds = LinkedHashSet<String>()

    init {
        PortType.BUILTIN.forEach { portMap[it.id] = it }
    }

    fun register(type: NodeType): NodeRegistry {
        map[type.id] = type
        return this
    }

    operator fun get(id: String): NodeType? = map[id]
    fun all(): List<NodeType> = map.values.toList()

    fun registerPortType(type: PortType): NodeRegistry {
        portMap[type.id] = type
        return this
    }

    fun portType(id: String): PortType? = portMap[id]
    fun allPortTypes(): List<PortType> = portMap.values.toList()

    fun install(plugin: NodeEditorPlugin): NodeRegistry {
        if (pluginIds.add(plugin.id)) plugin.install(NodePluginApi(this))
        return this
    }
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

    init {
        fields.forEach { it.reveal.snapTo(if (it.visibleWhen()) 1f else 0f) }
    }

    /** 0 = just spawned / closing, 1 = fully open - the miniature-modal pop. */
    val pop = BrassEased(0f, speed = 13f)
    val hover = BrassEased(0f, speed = 14f)
    val lift = BrassEased(0f, speed = 18f)
    val sel = BrassEased(0f, speed = 14f)

    /** 0 = expanded, 1 = rolled up to header + ports. */
    val roll = BrassEased(0f, speed = 14f)

    val glowIn = FloatArray(type.inputs.size)
    val glowOut = FloatArray(type.outputs.size)

    /** Per-port rejection glow, raised while a dragged wire hovers this port but cannot connect to it. */
    val rejectIn = FloatArray(type.inputs.size)
    val rejectOut = FloatArray(type.outputs.size)

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

    /** True once the wire is animating out; the editor removes it when [fade] reaches 0. */
    var closing: Boolean = false
    /** 1 = present, eased to 0 as the wire disconnects, so a cut retracts rather than blinking out. */
    val fade = BrassEased(1f, speed = 11f)
    val reroutes = ArrayList<ReroutePoint>()

    fun portType(): PortType = from.type.outputs[fromPort].type
}

data class ReroutePoint(var x: Float, var y: Float)

enum class LinkRejection {
    MISSING_PORT,
    SAME_NODE,
    INCOMPATIBLE_TYPE,
    DUPLICATE,
    OUTPUT_FULL,
    INPUT_FULL,
    CUSTOM,
}

data class LinkValidation(
    val allowed: Boolean,
    val rejection: LinkRejection? = null,
    val detail: String? = null,
)

/**
 * The **pure-data** graph: nodes and links, plus the mutation ops an editor drives. Holding the graph
 * as plain data (rather than as a tree of live components) is what makes save/load, copy/paste and undo
 * trivial - each is just serializing or cloning this.
 */
class NodeGraph(val registry: NodeRegistry) {

    val nodes = ArrayList<GraphNode>()
    val links = ArrayList<Link>()
    val frames = ArrayList<GraphFrame>()
    val comments = ArrayList<GraphComment>()
    val bookmarks = ArrayList<GraphBookmark>()

    private var nextId = 1
    private var nextDecorationId = 1

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

    /**
     * Check the complete connection contract without mutating the graph. This is the single source of
     * truth used by the model, hover feedback, plugins and tests.
     */
    fun validateLink(from: GraphNode, fromPort: Int, to: GraphNode, toPort: Int): LinkValidation {
        val out = from.type.outputs.getOrNull(fromPort)
            ?: return LinkValidation(false, LinkRejection.MISSING_PORT)
        val inp = to.type.inputs.getOrNull(toPort)
            ?: return LinkValidation(false, LinkRejection.MISSING_PORT)
        if (from === to) return LinkValidation(false, LinkRejection.SAME_NODE)
        if (!inp.type.accepts(out.type)) return LinkValidation(false, LinkRejection.INCOMPATIBLE_TYPE)
        val live = links.filterNot { it.closing }
        if (live.any { it.from === from && it.fromPort == fromPort && it.to === to && it.toPort == toPort })
            return LinkValidation(false, LinkRejection.DUPLICATE)
        val outMax = if (out.maxConnections == 0) Int.MAX_VALUE else out.maxConnections
        val inMax = if (inp.maxConnections == 0) 1 else inp.maxConnections
        if (live.count { it.from === from && it.fromPort == fromPort } >= outMax)
            return LinkValidation(false, LinkRejection.OUTPUT_FULL)
        val inputCount = live.count { it.to === to && it.toPort == toPort }
        if (inputCount >= inMax && inMax > 1)
            return LinkValidation(false, LinkRejection.INPUT_FULL)
        for (rule in registry.connectionRules) {
            val detail = rule.validate(this, from, fromPort, to, toPort)
            if (detail != null) return LinkValidation(false, LinkRejection.CUSTOM, detail)
        }
        return LinkValidation(true)
    }

    /** Wire an output to an input when [validateLink] allows it. Single inputs replace their old wire. */
    fun link(from: GraphNode, fromPort: Int, to: GraphNode, toPort: Int): Link? {
        val validation = validateLink(from, fromPort, to, toPort)
        if (!validation.allowed) {
            val replacingSingleInput = validation.rejection == LinkRejection.INPUT_FULL &&
                to.type.inputs.getOrNull(toPort)?.maxConnections == 1
            if (!replacingSingleInput) return null
        }
        val inp = to.type.inputs[toPort]
        if (inp.maxConnections <= 1) links.removeAll { it.to === to && it.toPort == toPort }
        return Link(from, fromPort, to, toPort).also { links.add(it) }
    }

    /** Add a bend pin to [link]. It remains part of the wire and is serialized with it. */
    fun reroute(link: Link, x: Float, y: Float): ReroutePoint =
        ReroutePoint(x, y).also { link.reroutes.add(it) }

    fun frame(
        title: String,
        nodeIds: Collection<Int>,
        tone: FrameTone = FrameTone.BRASS,
        autoResize: Boolean = true,
    ): GraphFrame = GraphFrame(nextDecorationId++, title, nodeIds.toMutableSet(), tone, autoResize).also {
        it.resizeToContents(this)
        frames += it
    }

    fun comment(text: String, x: Float, y: Float): GraphComment =
        GraphComment(nextDecorationId++, text, x, y).also(comments::add)

    fun bookmark(name: String, x: Float, y: Float, zoom: Float): GraphBookmark =
        GraphBookmark(name, x, y, zoom).also {
            bookmarks.removeAll { old -> old.name == name }
            bookmarks += it
        }

    internal fun adoptFrame(frame: GraphFrame) {
        nextDecorationId = maxOf(nextDecorationId, frame.id + 1)
        frames += frame
    }

    internal fun adoptComment(comment: GraphComment) {
        nextDecorationId = maxOf(nextDecorationId, comment.id + 1)
        comments += comment
    }

    fun byId(id: Int): GraphNode? = nodes.firstOrNull { it.id == id }

    // Persistence

    /** Serialize to the native JSON format. */
    fun toJson(): String = NodeIO.toJson(this)

    /**
     * Replace this graph's contents from [json]. Unknown node types are skipped. Invalid documents
     * return false without destroying the graph that is already open.
     */
    fun load(json: String): Boolean {
        if (NodeIO.compatibility(json) == NodeIO.Compatibility.INVALID) return false
        val backup = toJson()
        nodes.clear(); links.clear(); frames.clear(); comments.clear(); bookmarks.clear()
        return runCatching {
            NodeIO.into(this, json)
            true
        }.getOrElse {
            nodes.clear(); links.clear(); frames.clear(); comments.clear(); bookmarks.clear()
            NodeIO.into(this, backup)
            false
        }
    }

    companion object {
        fun fromJson(registry: NodeRegistry, json: String): NodeGraph =
            NodeGraph(registry).also { it.load(json) }
    }
}
