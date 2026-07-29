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
 *   "version": 5,
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
 * [compatibility] lets a host warn before that tolerant future-version read, while versions
 * [OLDEST_SUPPORTED_VERSION] through [CURRENT_VERSION] are the explicit compatibility contract.
 *
 * Uses Gson, already on the classpath (see [net.swzo.brass.ui.kit.media.BrassIcons]); no new dependency.
 */
object NodeIO {

    const val CURRENT_VERSION = 5
    const val OLDEST_SUPPORTED_VERSION = 1
    private val gson = GsonBuilder().setPrettyPrinting().create()

    enum class Compatibility { CURRENT, LEGACY, FUTURE, INVALID }

    fun compatibility(json: String): Compatibility {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
            ?: return Compatibility.INVALID
        if (!root.has("nodes")) return Compatibility.INVALID
        val version = runCatching {
            root.get("version")?.asInt ?: OLDEST_SUPPORTED_VERSION
        }.getOrElse { return Compatibility.INVALID }
        return when {
            version > CURRENT_VERSION -> Compatibility.FUTURE
            version < CURRENT_VERSION -> Compatibility.LEGACY
            else -> Compatibility.CURRENT
        }
    }

    fun toJson(graph: NodeGraph): String {
        val root = JsonObject()
        root.addProperty("version", CURRENT_VERSION)

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
            if (l.closing) continue
            val o = JsonObject()
            o.addProperty("from", l.from.id)
            o.addProperty("fromPort", l.fromPort)
            o.addProperty("to", l.to.id)
            o.addProperty("toPort", l.toPort)
            if (l.reroutes.isNotEmpty()) {
                val reroutes = JsonArray()
                for (point in l.reroutes) {
                    val p = JsonObject()
                    p.addProperty("x", point.x)
                    p.addProperty("y", point.y)
                    reroutes.add(p)
                }
                o.add("reroutes", reroutes)
            }
            links.add(o)
        }
        root.add("links", links)

        val frames = JsonArray()
        for (frame in graph.frames) {
            val o = JsonObject()
            o.addProperty("id", frame.id)
            o.addProperty("title", frame.title)
            o.addProperty("tone", frame.tone.name)
            frame.customColor?.let { o.addProperty("customColor", it) }
            o.addProperty("autoResize", frame.autoResize)
            frame.parentFrameId?.let { o.addProperty("parent", it) }
            o.addProperty("x", frame.x); o.addProperty("y", frame.y)
            o.addProperty("width", frame.width); o.addProperty("height", frame.height)
            val ids = JsonArray()
            frame.nodeIds.forEach(ids::add)
            o.add("nodes", ids)
            frames.add(o)
        }
        root.add("frames", frames)

        val comments = JsonArray()
        for (comment in graph.comments) {
            val o = JsonObject()
            o.addProperty("id", comment.id)
            o.addProperty("text", comment.text)
            o.addProperty("x", comment.x); o.addProperty("y", comment.y)
            o.addProperty("width", comment.width)
            o.addProperty("height", comment.height)
            o.addProperty("tone", comment.tone.name)
            comment.customColor?.let { o.addProperty("customColor", it) }
            comments.add(o)
        }
        root.add("comments", comments)

        val bookmarks = JsonArray()
        for (bookmark in graph.bookmarks) {
            val o = JsonObject()
            o.addProperty("name", bookmark.name)
            o.addProperty("panX", bookmark.panX); o.addProperty("panY", bookmark.panY)
            o.addProperty("zoom", bookmark.zoom)
            bookmarks.add(o)
        }
        root.add("bookmarks", bookmarks)

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
            graph.link(from, o.get("fromPort")?.asInt ?: 0, to, o.get("toPort")?.asInt ?: 0)?.let { link ->
                o.getAsJsonArray("reroutes")?.forEach { point ->
                    val p = point.asJsonObject
                    graph.reroute(link, p.get("x")?.asFloat ?: 0f, p.get("y")?.asFloat ?: 0f)
                }
            }
        }

        root.getAsJsonArray("frames")?.forEach { el ->
            val o = el.asJsonObject
            val ids = o.getAsJsonArray("nodes")?.mapNotNull { it.asInt.takeIf { id -> graph.byId(id) != null } }
                ?.toMutableSet() ?: mutableSetOf()
            graph.adoptFrame(GraphFrame(
                id = o.get("id")?.asInt ?: return@forEach,
                title = o.get("title")?.asString ?: "Group",
                nodeIds = ids,
                tone = runCatching { FrameTone.valueOf(o.get("tone")?.asString ?: "") }
                    .getOrDefault(FrameTone.BRASS),
                autoResize = o.get("autoResize")?.asBoolean ?: true,
                parentFrameId = o.get("parent")?.asInt,
                x = o.get("x")?.asFloat ?: 0f,
                y = o.get("y")?.asFloat ?: 0f,
                width = o.get("width")?.asFloat ?: 160f,
                height = o.get("height")?.asFloat ?: 100f,
                customColor = o.get("customColor")?.asInt,
            ))
        }

        root.getAsJsonArray("comments")?.forEach { el ->
            val o = el.asJsonObject
            graph.adoptComment(GraphComment(
                id = o.get("id")?.asInt ?: return@forEach,
                text = o.get("text")?.asString ?: "",
                x = o.get("x")?.asFloat ?: 0f,
                y = o.get("y")?.asFloat ?: 0f,
                width = o.get("width")?.asFloat ?: 132f,
                height = o.get("height")?.asFloat ?: 48f,
                tone = runCatching { FrameTone.valueOf(o.get("tone")?.asString ?: "") }
                    .getOrDefault(FrameTone.PATINA),
                customColor = o.get("customColor")?.asInt,
            ))
        }

        root.getAsJsonArray("bookmarks")?.forEach { el ->
            val o = el.asJsonObject
            graph.bookmark(
                o.get("name")?.asString ?: return@forEach,
                o.get("panX")?.asFloat ?: 0f,
                o.get("panY")?.asFloat ?: 0f,
                o.get("zoom")?.asFloat ?: 1f,
            )
        }
    }

    private fun primitive(p: JsonPrimitive): Any = when {
        p.isBoolean -> p.asBoolean
        p.isNumber -> p.asDouble
        else -> p.asString
    }
}
