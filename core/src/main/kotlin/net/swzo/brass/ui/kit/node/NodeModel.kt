@file:Suppress("unused")
package net.swzo.brass.ui.kit.node

import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassEased
import java.awt.Color

/**
 * A wire's colour-coded socket kind. A wire is only allowed between an output and an input of the
 * **same** type; the colour is read from the theme on every draw so it follows a retheme.
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
    val label: String? = null,
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
    val description: String? = null,
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

    /**
     * Called after a node's fields may have changed (an edit, or a graph load) so the node can
     * rebuild its SCRIPT-DERIVED fields - a Lua node re-creates its `ui.*` control fields from the
     * current script. Fired by [NodeGraph.rebuildDynamicFields].
     */
    val onFieldsChanged: ((GraphNode) -> Unit)? = null,
    val width: Float = 156f,
    val executor: NodeExecutor? = null,
    val renderer: NodeRenderer? = null,
    val description: String? = null,
    /**
     * Per-node extra input ports, appended after [inputs] - a node whose port count depends on one of
     * its own fields (a toggle that adds "configure from nodes" inputs). Evaluated live against the
     * node's current field values, so flipping the field changes the port list. Extra inputs keep
     * their indices stable (they are always appended), so existing links to the base inputs are
     * unaffected when a graph with extra wires is loaded without them - the extra links simply fail
     * validation and are dropped, like any now-incompatible wire.
     */
    val extraInputs: ((GraphNode) -> List<Port>)? = null,
    /**
     * The symmetric output-side hook: per-node extra output ports, appended after [outputs] - a node
     * whose output count depends on one of its own fields (a scripting node whose pins derive from
     * the typed code). Evaluated live against the node's current field values; extra outputs keep
     * their indices stable (always appended), so saved wires to the base outputs survive a field
     * change and a graph without them loads with the extra links dropped, like any now-incompatible
     * wire. Every draw/layout/hit-test/validate/execution path reads through [GraphNode.effectiveOutputs].
     */
    val extraOutputs: ((GraphNode) -> List<Port>)? = null,
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

    /** The type's base fields, built fresh per node. */
    val staticFields: List<NodeField> = type.makeFields()

    /**
     * Script-derived fields added AFTER construction (a Lua node's `ui.*` controls): mutable so the
     * node type can rebuild them when the script changes. [fields] concatenates them under [staticFields],
     * so every consumer that iterates `node.fields` (drawing, layout, hit-testing, encode) sees them.
     */
    val dynamicFields = ArrayList<NodeField>()

    val fields: List<NodeField> get() = staticFields + dynamicFields

    /** Replace the dynamic fields, preserving any existing instance by key (value + reveal state). */
    fun syncDynamicFields(next: List<NodeField>) {
        val existing = dynamicFields.associateBy { it.key }
        dynamicFields.clear()
        for (f in next) {
            val old = existing[f.key]
            if (old != null) {
                dynamicFields.add(old)
            } else {
                f.reveal.snapTo(1f)
                dynamicFields.add(f)
            }
        }
    }

    init {
        fields.forEach { it.reveal.snapTo(if (it.visibleWhen()) 1f else 0f) }
    }

    val pop = BrassEased(0f, speed = 13f)
    val hover = BrassEased(0f, speed = 14f)
    val lift = BrassEased(0f, speed = 18f)
    val sel = BrassEased(0f, speed = 14f)

    val roll = BrassEased(0f, speed = 14f)

    // Growable because a dynamic node's port count can change after construction (extra
    // inputs/outputs re-derived from field values); see [ensureGlow].
    var glowIn = FloatArray(effectiveInputs().size)
        private set
    var glowOut = FloatArray(effectiveOutputs().size)
        private set
    var rejectIn = FloatArray(effectiveInputs().size)
        private set
    var rejectOut = FloatArray(effectiveOutputs().size)
        private set

    /**
     * Grow the glow/reject arrays to cover [ins] inputs and [outs] outputs. Called by the editor's
     * animation pass (and anything else that writes glow state) so a node whose dynamic ports were
     * revealed after construction - a scripting node whose pins come from typed code - still animates
     * its newest ports instead of silently iterating a stale array.
     */
    fun ensureGlow(ins: Int, outs: Int) {
        if (glowIn.size < ins) glowIn = glowIn.copyOf(ins)
        if (rejectIn.size < ins) rejectIn = rejectIn.copyOf(ins)
        if (glowOut.size < outs) glowOut = glowOut.copyOf(outs)
        if (rejectOut.size < outs) rejectOut = rejectOut.copyOf(outs)
    }

    var closing: Boolean = false

    val width: Float get() = type.width

    fun visibleFields(): List<NodeField> = fields.filter { it.visibleWhen() }
    fun field(key: String): NodeField? = fields.firstOrNull { it.key == key }

    /**
     * The full input list for this node: the type's base inputs plus any per-node extra inputs
     * (evaluated against the current field values). Everything that draws, hit-tests or validates a
     * node's ports must read through this so a field-driven extra input (a toggle that reveals
     * "configure from nodes" ports) shows and behaves correctly.
     */
    fun effectiveInputs(): List<Port> =
        type.extraInputs?.invoke(this)?.let { type.inputs + it } ?: type.inputs

    /**
     * The full output list for this node: the type's base outputs plus any per-node extra outputs
     * (evaluated against the current field values). Everything that draws, hit-tests or validates a
     * node's ports must read through this - the symmetric sibling of [effectiveInputs].
     */
    fun effectiveOutputs(): List<Port> =
        type.extraOutputs?.invoke(this)?.let { type.outputs + it } ?: type.outputs

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

    var closing: Boolean = false
    val fade = BrassEased(1f, speed = 11f)
    val reroutes = ArrayList<ReroutePoint>()

    /**
     * The wire's output port type, or null when the port no longer exists (a stale link whose node's
     * dynamic ports changed - e.g. a Lua node's script lost an output). Drawing and the evaluator
     * must treat null as "skip this link", never index an out-of-bounds port.
     */
    fun portType(): PortType? = from.effectiveOutputs().getOrNull(fromPort)?.type
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

    fun spawn(typeId: String, x: Float, y: Float): GraphNode? {
        val type = registry[typeId] ?: return null
        val node = GraphNode(nextId++, type, x, y).also { it.pop.target = 1f }
        nodes.add(node)
        return node
    }

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

    fun clear() {
        nodes.clear()
        links.clear()
        frames.clear()
        comments.clear()
        bookmarks.clear()
    }

    fun validateLink(from: GraphNode, fromPort: Int, to: GraphNode, toPort: Int): LinkValidation {
        val out = from.effectiveOutputs().getOrNull(fromPort)
            ?: return LinkValidation(false, LinkRejection.MISSING_PORT)
        val inp = to.effectiveInputs().getOrNull(toPort)
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

    fun link(from: GraphNode, fromPort: Int, to: GraphNode, toPort: Int): Link? {
        val validation = validateLink(from, fromPort, to, toPort)
        if (!validation.allowed) {
            val replacingSingleInput = validation.rejection == LinkRejection.INPUT_FULL &&
                to.effectiveInputs().getOrNull(toPort)?.maxConnections == 1
            if (!replacingSingleInput) return null
        }
        val inp = to.effectiveInputs()[toPort]
        if (inp.maxConnections <= 1) links.removeAll { it.to === to && it.toPort == toPort }
        return Link(from, fromPort, to, toPort).also { links.add(it) }
    }

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

    /** Serialize to the native **BSON** format - the fast save path (wire, editor snapshots). */
    fun toBson(): ByteArray = NodeIO.toBson(this)

    /**
     * Let every node rebuild its script-derived fields (a Lua node re-derives its `ui.*` controls
     * from the current script). Called after an edit and after a graph load, so a control that
     * appears/disappears with the script stays in sync.
     */
    fun rebuildDynamicFields() {
        for (node in nodes) node.type.onFieldsChanged?.invoke(node)
    }

    /**
     * Replace this graph's contents from [bytes] (BSON). Unknown node types are skipped. Invalid
     * documents return false without destroying the graph that is already open.
     */
    fun loadBson(bytes: ByteArray): Boolean {
        if (NodeIO.compatibility(bytes) == NodeIO.Compatibility.INVALID) return false
        val backup = toBson()
        nodes.clear(); links.clear(); frames.clear(); comments.clear(); bookmarks.clear()
        return runCatching {
            NodeIO.intoBson(this, bytes)
            rebuildDynamicFields()
            true
        }.getOrElse {
            nodes.clear(); links.clear(); frames.clear(); comments.clear(); bookmarks.clear()
            NodeIO.intoBson(this, backup)
            false
        }
    }

    fun toJson(): String = NodeIO.toJson(this)

    fun load(json: String): Boolean {
        if (NodeIO.compatibility(json) == NodeIO.Compatibility.INVALID) return false
        val backup = toJson()
        nodes.clear(); links.clear(); frames.clear(); comments.clear(); bookmarks.clear()
        return runCatching {
            NodeIO.into(this, json)
            rebuildDynamicFields()
            true
        }.getOrElse {
            nodes.clear(); links.clear(); frames.clear(); comments.clear(); bookmarks.clear()
            NodeIO.into(this, backup)
            false
        }
    }

    companion object {
        fun fromBson(registry: NodeRegistry, bytes: ByteArray): NodeGraph =
            NodeGraph(registry).also { it.loadBson(bytes) }

        fun fromJson(registry: NodeRegistry, json: String): NodeGraph =
            NodeGraph(registry).also { it.load(json) }
    }
}
