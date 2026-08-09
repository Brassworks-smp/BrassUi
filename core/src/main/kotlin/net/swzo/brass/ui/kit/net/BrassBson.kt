package net.swzo.brass.ui.kit.net

import org.bson.BsonArray
import org.bson.BsonBinary
import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonDouble
import org.bson.BsonInt32
import org.bson.BsonInt64
import org.bson.BsonNull
import org.bson.BsonNumber
import org.bson.BsonString
import org.bson.BsonValue
import org.bson.BsonBinaryWriter
import org.bson.BsonDocumentReader
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.io.BasicOutputBuffer
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import sun.misc.Unsafe

/**
 * The binary codec brassnet serialises through: every action input, server state value and action
 * result travels as one BSON document instead of a JSON string, and the node editor's native graph
 * format is BSON too (see [net.swzo.brass.ui.kit.node.NodeIO]).
 * ### Why BSON
 * A large node graph is the worst possible JSON payload: the editor serialises the whole graph on
 * every edit, Gson then *escapes* that text a second time inside the action envelope, and the
 * receiving side parses it all back. BSON is a typed binary format - numbers stay numbers, strings
 * stay strings, and a [ByteArray] travels as raw binary - so the graph serialises once, in compact
 * bytes, and parses once on the other side. No escaping, no string building, no character parsing.
 * ### How values are shaped
 * Encoding mirrors Gson's default reflective behaviour for exactly the value universe brassnet
 * actually moves (declared fields, nulls skipped, enums by name, map keys stringified, byte arrays
 * as binary), and decoding mirrors Gson's reflective *types* - including generic field types like
 * `Map<Int, Any?>` or `List<String>` - so a data class round-trips through BSON with the same shapes
 * it used to round-trip through JSON. Object allocation uses `sun.misc.Unsafe`, the same trick Gson
 * uses on the NeoForge 21.1 classpath, so Kotlin data classes need no no-arg constructor.
 * The public surface takes and returns [ByteArray]; org.bson types never leak into brassui's API, so
 * consumers only need the (jar-in-jar'd, mirrored) BSON library on their runtime classpath.
 */
object BrassBson {

    /** Top-level values ride in `{ "v": … }` so nulls and scalars are valid BSON documents. */
    private const val ROOT_KEY = "v"

    private val codec = BsonDocumentCodec()

    private val fieldsCache = ConcurrentHashMap<Class<*>, List<Field>>()

    private val unsafe: Unsafe? = runCatching {
        val f = Unsafe::class.java.getDeclaredField("theUnsafe")
        f.isAccessible = true
        f.get(null) as Unsafe
    }.getOrNull()


    /** Serialize [value] (any plain data) to a BSON document's bytes. */
    fun toBytes(value: Any?): ByteArray {
        val root = BsonDocument().apply {
            put(ROOT_KEY, if (value == null) BsonNull.VALUE else encode(value))
        }
        return writeDocument(root)
    }

    fun <T : Any> fromBytes(bytes: ByteArray, type: Class<T>): T? =
        fromBytes(bytes, type as Type)

    fun <T : Any> fromBytes(bytes: ByteArray, type: Type): T? {
        if (bytes.isEmpty()) return null
        val root = parseDocument(bytes) ?: return null
        val value = root[ROOT_KEY] ?: return null
        @Suppress("UNCHECKED_CAST")
        return decode(value, type) as T?
    }

    fun toWire(result: BrassActionResult): ByteArray = when (result) {
        is BrassActionResult.Success -> toBytes(Wire(true, null, null, result.payload))
        is BrassActionResult.Failure -> toBytes(Wire(false, result.code, result.args, null))
    }

    fun fromWire(bytes: ByteArray): BrassActionResult {
        val wire = fromBytes(bytes, Wire::class.java) ?: return err("wire.bad")
        return if (wire.ok) BrassActionResult.Success(wire.payload)
        else BrassActionResult.Failure(wire.code ?: "unknown", wire.args ?: emptyList())
    }

    /** Serialize a whole [BsonDocument] to bytes (used by the node graph's native format). */
    internal fun writeDocument(doc: BsonDocument): ByteArray {
        val buffer = BasicOutputBuffer()
        val writer = BsonBinaryWriter(buffer)
        codec.encode(writer, doc, EncoderContext.builder().build())
        return buffer.toByteArray()
    }

    /**
     * Parse a whole [BsonDocument] from bytes, or null when invalid. Decodes into a fresh *mutable*
     * document (the raw byte-backed form validates lazily and is immutable, which would turn invalid
     * input into a late exception and graph manipulation into `UnsupportedOperationException`).
     */
    internal fun parseDocument(bytes: ByteArray): BsonDocument? = runCatching {
        BsonDocumentCodec().decode(
            BsonDocumentReader(org.bson.RawBsonDocument(bytes)),
            DecoderContext.builder().build(),
        )
    }.getOrNull()


    private fun encode(value: Any?): BsonValue = when (value) {
        null -> BsonNull.VALUE
        is ByteArray -> BsonBinary(value)
        is Boolean -> BsonBoolean(value)
        is Byte, is Short, is Int -> BsonInt32((value as Number).toInt())
        is Long -> BsonInt64(value)
        is Float, is Double -> BsonDouble((value as Number).toDouble())
        is BigInteger -> if (value.bitLength() < 63) BsonInt64(value.toLong()) else BsonDouble(value.toDouble())
        is BigDecimal -> BsonDouble(value.toDouble())
        is Char -> BsonString(value.toString())
        is String -> BsonString(value)
        is Enum<*> -> BsonString(value.name)
        is Map<*, *> -> BsonDocument().also { doc ->
            value.forEach { (key, v) -> doc.put(key?.toString() ?: "null", encode(v)) }
        }
        is Collection<*> -> BsonArray(value.map(::encode))
        is Array<*> -> BsonArray(value.map(::encode))
        else -> encodeObject(value)
    }

    private fun encodeObject(value: Any): BsonDocument {
        val doc = BsonDocument()
        for (field in fieldsOf(value.javaClass)) {
            val v = runCatching { field.get(value) }.getOrNull() ?: continue
            doc.put(field.name, encode(v))
        }
        return doc
    }


    private fun decode(value: BsonValue?, type: Type): Any? {
        if (value == null || value.isNull) return null
        return when (type) {
            Any::class.java, Number::class.java -> natural(value)
            String::class.java -> (value as? BsonString)?.value
            Char::class.java, Char::class.javaPrimitiveType, java.lang.Character::class.java ->
                (value as? BsonString)?.value?.firstOrNull()
            Boolean::class.java, Boolean::class.javaPrimitiveType, java.lang.Boolean::class.java ->
                (value as? BsonBoolean)?.value
            Byte::class.java, Byte::class.javaPrimitiveType, java.lang.Byte::class.java ->
                (value as? BsonNumber)?.intValue()?.toByte()
            Short::class.java, Short::class.javaPrimitiveType, java.lang.Short::class.java ->
                (value as? BsonNumber)?.intValue()?.toShort()
            Int::class.java, Int::class.javaPrimitiveType, java.lang.Integer::class.java ->
                (value as? BsonNumber)?.intValue()
            Long::class.java, Long::class.javaPrimitiveType, java.lang.Long::class.java ->
                (value as? BsonNumber)?.longValue()
            Float::class.java, Float::class.javaPrimitiveType, java.lang.Float::class.java ->
                (value as? BsonNumber)?.doubleValue()?.toFloat()
            Double::class.java, Double::class.javaPrimitiveType, java.lang.Double::class.java ->
                (value as? BsonNumber)?.doubleValue()
            ByteArray::class.java -> (value as? BsonBinary)?.data
            else -> when {
                type is Class<*> && type.isEnum -> enumValue(type, value)
                type is ParameterizedType -> decodeParameterized(value, type)
                type is Class<*> && type.isArray -> decodeArray(value, type.componentType)
                type is Class<*> && Map::class.java.isAssignableFrom(type) ->
                    decodeMap(value, String::class.java, Any::class.java)
                type is Class<*> && Collection::class.java.isAssignableFrom(type) ->
                    (value as? BsonArray)?.map { natural(it) }?.let {
                        if (Set::class.java.isAssignableFrom(type)) it.toSet() else it
                    }
                type is Class<*> -> decodeObject(value, type)
                else -> natural(value)
            }
        }
    }

    private fun decodeParameterized(value: BsonValue, type: ParameterizedType): Any? {
        val raw = type.rawType as? Class<*> ?: return natural(value)
        val args = type.actualTypeArguments
        return when {
            Map::class.java.isAssignableFrom(raw) -> decodeMap(
                value,
                args.getOrNull(0) ?: String::class.java,
                args.getOrNull(1) ?: Any::class.java,
            )
            Collection::class.java.isAssignableFrom(raw) -> {
                val element = args.firstOrNull() ?: Any::class.java
                val list = (value as? BsonArray)?.mapNotNull { decode(it, element) } ?: emptyList<Any?>()
                if (Set::class.java.isAssignableFrom(raw)) list.toSet() else list
            }
            else -> decodeObject(value, raw)
        }
    }

    private fun decodeMap(value: BsonValue, keyType: Type, valueType: Type): Any? {
        val doc = value as? BsonDocument ?: return null
        val result = LinkedHashMap<Any?, Any?>()
        for ((key, v) in doc) {
            result[convertKey(key, keyType)] = decode(v, valueType)
        }
        return result
    }

    private fun decodeArray(value: BsonValue, component: Class<*>): Any? {
        val array = value as? BsonArray ?: return null
        val result = java.lang.reflect.Array.newInstance(component, array.size)
        array.forEachIndexed { i, v -> java.lang.reflect.Array.set(result, i, decode(v, component)) }
        return result
    }

    private fun decodeObject(value: BsonValue, type: Class<*>): Any? {
        val doc = value as? BsonDocument ?: return null
        val instance = unsafe?.let { runCatching { it.allocateInstance(type) }.getOrNull() } ?: return null
        for (field in fieldsOf(type)) {
            val v = doc[field.name] ?: continue
            if (v.isNull) {
                if (!field.type.isPrimitive) field.set(instance, null)
                continue
            }
            val decoded = runCatching { decode(v, field.genericType) }.getOrNull()
            if (decoded != null || !field.type.isPrimitive) {
                runCatching { field.set(instance, decoded) }
            }
        }
        return instance
    }

    private fun enumValue(type: Class<*>, value: BsonValue): Any? {
        val name = (value as? BsonString)?.value ?: return null
        @Suppress("UNCHECKED_CAST")
        return runCatching { java.lang.Enum.valueOf(type as Class<out Enum<*>>, name) }.getOrNull()
    }

    private fun convertKey(key: String, keyType: Type): Any? = when (keyType) {
        String::class.java -> key
        Int::class.java, Int::class.javaPrimitiveType, java.lang.Integer::class.java -> key.toIntOrNull()
        Long::class.java, Long::class.javaPrimitiveType, java.lang.Long::class.java -> key.toLongOrNull()
        Short::class.java, Short::class.javaPrimitiveType, java.lang.Short::class.java -> key.toShortOrNull()
        Byte::class.java, Byte::class.javaPrimitiveType, java.lang.Byte::class.java -> key.toByteOrNull()
        Double::class.java, Double::class.javaPrimitiveType, java.lang.Double::class.java -> key.toDoubleOrNull()
        Float::class.java, Float::class.javaPrimitiveType, java.lang.Float::class.java -> key.toFloatOrNull()
        Boolean::class.java, Boolean::class.javaPrimitiveType, java.lang.Boolean::class.java -> key.toBooleanStrictOrNull()
        Any::class.java, Object::class.java -> key
        else -> key
    }

    /**
     * The "natural" form of a BSON value when no concrete type is known - the analogue of Gson
     * parsing into `Object`: whole numbers as Int (Long past 32 bits), decimals as Double, binary as
     * ByteArray, documents as `Map<String, Any?>`.
     */
    private fun natural(value: BsonValue): Any? = when (value) {
        is BsonNull -> null
        is BsonBoolean -> value.value
        is BsonInt32 -> value.value
        is BsonInt64 -> value.value
        is BsonDouble -> value.value
        is BsonString -> value.value
        is BsonBinary -> value.data
        is BsonArray -> value.map { natural(it) }
        is BsonDocument -> value.entries.associate { it.key to natural(it.value) }
        else -> value.toString()
    }


    private fun fieldsOf(type: Class<*>): List<Field> = fieldsCache.getOrPut(type) {
        val fields = ArrayList<Field>()
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            for (f in current.declaredFields) {
                val mods = f.modifiers
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || f.isSynthetic) continue
                runCatching { f.isAccessible = true }
                fields.add(f)
            }
            current = current.superclass
        }
        fields
    }

    private class Wire(val ok: Boolean, val code: String?, val args: List<String>?, val payload: String?)
}
