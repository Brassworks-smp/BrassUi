package net.swzo.brass.ui.kit.node

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import net.swzo.brass.ui.kit.net.BrassBson
import org.bson.BsonArray
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonDouble
import org.bson.BsonInt32
import org.bson.BsonInt64
import org.bson.BsonNumber
import org.bson.BsonString
import org.bson.BsonValue

/**
 * The native save/load formats for a [NodeGraph]: a versioned **BSON document** for the wire and the
 * editor's fast save path (numbers stay numbers, byte arrays stay binary, and there is no text to
 * escape or parse), plus the equivalent versioned JSON document for portable export, import and
 * hand-editing:
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
 * BSON comes from MongoDB's pure-JVM `org.mongodb:bson` (jar-in-jar'd by the mod and mirrored to the
 * Brassworks maven, so consumers add no repository). JSON uses Gson, already on the classpath (see
 * [net.swzo.brass.ui.kit.media.BrassIcons]).
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

    /** The same [Compatibility] check for a BSON document. */
    fun compatibility(bytes: ByteArray): Compatibility {
        val root = BrassBson.parseDocument(bytes) ?: return Compatibility.INVALID
        if (!root.containsKey("nodes")) return Compatibility.INVALID
        val version = (root["version"] as? BsonNumber)?.intValue() ?: OLDEST_SUPPORTED_VERSION
        return when {
            version > CURRENT_VERSION -> Compatibility.FUTURE
            version < CURRENT_VERSION -> Compatibility.LEGACY
            else -> Compatibility.CURRENT
        }
    }

    /**
     * Serialize [graph] to the native **BSON** format. Structurally identical to [toJson] - the same
     * version, nodes/links/frames/comments/bookmarks and per-field values - so a graph round-trips
     * through either format and a host can switch between them freely.
     */
    fun toBson(graph: NodeGraph): ByteArray {
        val root = BsonDocument()
        root.put("version", BsonInt32(CURRENT_VERSION))

        val nodes = BsonArray()
        for (n in graph.nodes) {
            if (n.closing) continue
            val o = BsonDocument()
            o.put("id", BsonInt32(n.id))
            o.put("type", BsonString(n.type.id))
            o.put("x", BsonDouble(n.x.toDouble()))
            o.put("y", BsonDouble(n.y.toDouble()))
            o.put("collapsed", BsonBoolean(n.collapsed))
            val fields = BsonDocument()
            for (f in n.fields) {
                when (val v = f.encode()) {
                    is Boolean -> fields.put(f.key, BsonBoolean(v))
                    is Byte, is Short, is Int -> fields.put(f.key, BsonInt32((v as Number).toInt()))
                    is Long -> fields.put(f.key, BsonInt64(v))
                    is Number -> fields.put(f.key, BsonDouble(v.toDouble()))
                    is String -> fields.put(f.key, BsonString(v))
                    else -> fields.put(f.key, BsonString(v.toString()))
                }
            }
            o.put("fields", fields)
            nodes.add(o)
        }
        root.put("nodes", nodes)

        val links = BsonArray()
        for (l in graph.links) {
            if (l.closing) continue
            val o = BsonDocument()
            o.put("from", BsonInt32(l.from.id))
            o.put("fromPort", BsonInt32(l.fromPort))
            o.put("to", BsonInt32(l.to.id))
            o.put("toPort", BsonInt32(l.toPort))
            if (l.reroutes.isNotEmpty()) {
                val reroutes = BsonArray()
                for (point in l.reroutes) {
                    val p = BsonDocument()
                    p.put("x", BsonDouble(point.x.toDouble()))
                    p.put("y", BsonDouble(point.y.toDouble()))
                    reroutes.add(p)
                }
                o.put("reroutes", reroutes)
            }
            links.add(o)
        }
        root.put("links", links)

        val frames = BsonArray()
        for (frame in graph.frames) {
            val o = BsonDocument()
            o.put("id", BsonInt32(frame.id))
            o.put("title", BsonString(frame.title))
            o.put("tone", BsonString(frame.tone.name))
            frame.customColor?.let { o.put("customColor", BsonInt32(it)) }
            o.put("autoResize", BsonBoolean(frame.autoResize))
            frame.parentFrameId?.let { o.put("parent", BsonInt32(it)) }
            o.put("x", BsonDouble(frame.x.toDouble())); o.put("y", BsonDouble(frame.y.toDouble()))
            o.put("width", BsonDouble(frame.width.toDouble())); o.put("height", BsonDouble(frame.height.toDouble()))
            val ids = BsonArray()
            frame.nodeIds.forEach { ids.add(BsonInt32(it)) }
            o.put("nodes", ids)
            frames.add(o)
        }
        root.put("frames", frames)

        val comments = BsonArray()
        for (comment in graph.comments) {
            val o = BsonDocument()
            o.put("id", BsonInt32(comment.id))
            o.put("text", BsonString(comment.text))
            o.put("x", BsonDouble(comment.x.toDouble())); o.put("y", BsonDouble(comment.y.toDouble()))
            o.put("width", BsonDouble(comment.width.toDouble())); o.put("height", BsonDouble(comment.height.toDouble()))
            o.put("tone", BsonString(comment.tone.name))
            comment.customColor?.let { o.put("customColor", BsonInt32(it)) }
            comments.add(o)
        }
        root.put("comments", comments)

        val bookmarks = BsonArray()
        for (bookmark in graph.bookmarks) {
            val o = BsonDocument()
            o.put("name", BsonString(bookmark.name))
            o.put("panX", BsonDouble(bookmark.panX.toDouble())); o.put("panY", BsonDouble(bookmark.panY.toDouble()))
            o.put("zoom", BsonDouble(bookmark.zoom.toDouble()))
            bookmarks.add(o)
        }
        root.put("bookmarks", bookmarks)

        return BrassBson.writeDocument(root)
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

    /** Read [bytes] into [graph] (which should be empty), skipping anything unknown - the BSON twin of [into]. */
    fun intoBson(graph: NodeGraph, bytes: ByteArray) {
        val root = BrassBson.parseDocument(bytes) ?: return

        (root["nodes"] as? BsonArray)?.forEach { el ->
            val o = el as? BsonDocument ?: return@forEach
            val id = (o["id"] as? BsonNumber)?.intValue() ?: return@forEach
            val type = (o["type"] as? BsonString)?.value ?: return@forEach
            val x = (o["x"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 0f
            val y = (o["y"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 0f
            val node = graph.adopt(id, type, x, y) ?: return@forEach
            node.collapsed = (o["collapsed"] as? BsonBoolean)?.value ?: false
            node.roll.snapTo(if (node.collapsed) 1f else 0f)
            (o["fields"] as? BsonDocument)?.let { fields ->
                for (f in node.fields) {
                    val el2 = fields[f.key] ?: continue
                    f.decode(primitive(el2))
                }
            }
        }

        (root["links"] as? BsonArray)?.forEach { el ->
            val o = el as? BsonDocument ?: return@forEach
            val from = graph.byId((o["from"] as? BsonNumber)?.intValue() ?: return@forEach) ?: return@forEach
            val to = graph.byId((o["to"] as? BsonNumber)?.intValue() ?: return@forEach) ?: return@forEach
            graph.link(
                from,
                (o["fromPort"] as? BsonNumber)?.intValue() ?: 0,
                to,
                (o["toPort"] as? BsonNumber)?.intValue() ?: 0,
            )?.let { link ->
                (o["reroutes"] as? BsonArray)?.forEach { point ->
                    val p = point as? BsonDocument ?: return@forEach
                    graph.reroute(
                        link,
                        (p["x"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 0f,
                        (p["y"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 0f,
                    )
                }
            }
        }

        (root["frames"] as? BsonArray)?.forEach { el ->
            val o = el as? BsonDocument ?: return@forEach
            val ids = (o["nodes"] as? BsonArray)?.mapNotNull {
                (it as? BsonNumber)?.intValue()?.takeIf { id -> graph.byId(id) != null }
            }?.toMutableSet() ?: mutableSetOf()
            graph.adoptFrame(GraphFrame(
                id = (o["id"] as? BsonNumber)?.intValue() ?: return@forEach,
                title = (o["title"] as? BsonString)?.value ?: "Group",
                nodeIds = ids,
                tone = runCatching { FrameTone.valueOf((o["tone"] as? BsonString)?.value ?: "") }
                    .getOrDefault(FrameTone.BRASS),
                autoResize = (o["autoResize"] as? BsonBoolean)?.value ?: true,
                parentFrameId = (o["parent"] as? BsonNumber)?.intValue(),
                x = (o["x"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 0f,
                y = (o["y"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 0f,
                width = (o["width"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 160f,
                height = (o["height"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 100f,
                customColor = (o["customColor"] as? BsonNumber)?.intValue(),
            ))
        }

        (root["comments"] as? BsonArray)?.forEach { el ->
            val o = el as? BsonDocument ?: return@forEach
            graph.adoptComment(GraphComment(
                id = (o["id"] as? BsonNumber)?.intValue() ?: return@forEach,
                text = (o["text"] as? BsonString)?.value ?: "",
                x = (o["x"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 0f,
                y = (o["y"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 0f,
                width = (o["width"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 132f,
                height = (o["height"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 48f,
                tone = runCatching { FrameTone.valueOf((o["tone"] as? BsonString)?.value ?: "") }
                    .getOrDefault(FrameTone.PATINA),
                customColor = (o["customColor"] as? BsonNumber)?.intValue(),
            ))
        }

        (root["bookmarks"] as? BsonArray)?.forEach { el ->
            val o = el as? BsonDocument ?: return@forEach
            graph.bookmark(
                (o["name"] as? BsonString)?.value ?: return@forEach,
                (o["panX"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 0f,
                (o["panY"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 0f,
                (o["zoom"] as? BsonNumber)?.doubleValue()?.toFloat() ?: 1f,
            )
        }
    }

    private fun primitive(p: JsonPrimitive): Any = when {
        p.isBoolean -> p.asBoolean
        p.isNumber -> p.asDouble
        else -> p.asString
    }

    private fun primitive(v: BsonValue): Any = when (v) {
        is BsonBoolean -> v.value
        is BsonNumber -> v.doubleValue()
        is BsonString -> v.value
        else -> v.toString()
    }
}
