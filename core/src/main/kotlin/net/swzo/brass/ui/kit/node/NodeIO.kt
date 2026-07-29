package net.swzo.brass.ui.kit.node

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * The native save/load format for a [NodeGraph] - a small, versioned JSON document that any app or user
 * can read, diff and hand-edit:
 *
 * ```json
 * {
 *   "version": 1,
 *   "nodes": [ { "id": 1, "type": "time", "x": 0.0, "y": 40.0, "collapsed": false,
 *               "fields": { "wave": "Sine", "speed": 0.4 } } ],
 *   "links": [ { "from": 1, "fromPort": 0, "to": 2, "toPort": 0 } ]
 * }
 * ```
 *
 * ### Why this is trivial
 *
 * Because the graph is pure data (see [NodeGraph]). A node writes its type id, position and each field's
 * primitive value; loading rebuilds the node from its registered [NodeType] (so predicates and control
 * layout come back correct) and hands each field its saved value through [NodeField.decode]. Custom node
 * types and custom fields ride the same path with no special case - a field the reader does not
 * recognise is simply skipped, so an older build opens a newer file without crashing.
 *
 * Uses Gson, already on the classpath (see [net.swzo.brass.ui.kit.media.BrassIcons]); no new dependency.
 */
object NodeIO {

    private const val VERSION = 1
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun toJson(graph: NodeGraph): String {
        val root = JsonObject()
        root.addProperty("version", VERSION)

        val nodes = JsonArray()
        for (n in graph.nodes) {
            if (n.closing) continue
            val o = JsonObject()
            o.addProperty("id", n.id)
            o.addProperty("type", n.type.id)
            o.addProperty("x", n.x)
            o.addProperty("y", n.y)
            o.addProperty("collapsed", n.collapsed)
            val fields = JsonObject()
            for (f in n.fields) {
                when (val v = f.encode()) {
                    is Boolean -> fields.addProperty(f.key, v)
                    is Number -> fields.addProperty(f.key, v)
                    is String -> fields.addProperty(f.key, v)
                    else -> fields.addProperty(f.key, v.toString())
                }
            }
            o.add("fields", fields)
            nodes.add(o)
        }
        root.add("nodes", nodes)

        val links = JsonArray()
        for (l in graph.links) {
            val o = JsonObject()
            o.addProperty("from", l.from.id)
            o.addProperty("fromPort", l.fromPort)
            o.addProperty("to", l.to.id)
            o.addProperty("toPort", l.toPort)
            links.add(o)
        }
        root.add("links", links)

        return gson.toJson(root)
    }

    /** Read [json] into [graph] (which should be empty), skipping anything unknown. */
    fun into(graph: NodeGraph, json: String) {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return

        root.getAsJsonArray("nodes")?.forEach { el ->
            val o = el.asJsonObject
            val id = o.get("id")?.asInt ?: return@forEach
            val type = o.get("type")?.asString ?: return@forEach
            val x = o.get("x")?.asFloat ?: 0f
            val y = o.get("y")?.asFloat ?: 0f
            val node = graph.adopt(id, type, x, y) ?: return@forEach
            node.collapsed = o.get("collapsed")?.asBoolean ?: false
            node.roll.snapTo(if (node.collapsed) 1f else 0f)
            o.getAsJsonObject("fields")?.let { fields ->
                for (f in node.fields) {
                    val el2 = fields.get(f.key) ?: continue
                    if (el2 is JsonPrimitive) f.decode(primitive(el2))
                }
            }
        }

        root.getAsJsonArray("links")?.forEach { el ->
            val o = el.asJsonObject
            val from = graph.byId(o.get("from")?.asInt ?: return@forEach) ?: return@forEach
            val to = graph.byId(o.get("to")?.asInt ?: return@forEach) ?: return@forEach
            graph.link(from, o.get("fromPort")?.asInt ?: 0, to, o.get("toPort")?.asInt ?: 0)
        }
    }

    private fun primitive(p: JsonPrimitive): Any = when {
        p.isBoolean -> p.asBoolean
        p.isNumber -> p.asDouble
        else -> p.asString
    }
}
