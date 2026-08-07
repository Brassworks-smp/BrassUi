package net.swzo.brass.ui.kit.net

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The one Gson instance the networking module serialises through, and the wire format helpers.
 *
 * The action bus deliberately carries **JSON strings** rather than NeoForge's binary `StreamCodec`s:
 * a UI action is a handful of plain data (ids, names, numbers), and JSON is the only encoding that
 * runs unchanged on the game client, the game server, and the standalone desktop app - where the
 * same handler executes in-process without a wire at all. Gson already ships with the game and with
 * Elementa/UniversalCraft, and it serialises Kotlin data classes without any per-type codec, which is
 * what keeps action declarations to a single lambda.
 */
object BrassJson {

    /** Payloads below this many bytes travel raw; larger ones are gzip-compressed by [compress]. */
    const val COMPRESS_THRESHOLD = 1024

    /** Shared instance. `disableHtmlEscaping` keeps `<` and `>` readable in payloads. */
    val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun toJson(value: Any?): String = gson.toJson(value)

    /** Parse [json] into [type], or null when the JSON is malformed or absent. */
    fun <T : Any> fromJson(json: String?, type: Class<T>): T? {
        if (json == null) return null
        return runCatching { gson.fromJson(json, type) }.getOrNull()
    }

    /**
     * The on-the-wire form of a [BrassActionResult] - a flat object, because Gson cannot round-trip a
     * sealed interface through its default type adapters.
     */
    fun toWire(result: BrassActionResult): String = when (result) {
        is BrassActionResult.Success -> gson.toJson(Wire(true, null, null, result.payload))
        is BrassActionResult.Failure -> gson.toJson(Wire(false, result.code, result.args, null))
    }

    fun fromWire(json: String): BrassActionResult {
        val wire = runCatching { gson.fromJson(json, Wire::class.java) }.getOrNull()
            ?: return err("wire.bad")
        return if (wire.ok) BrassActionResult.Success(wire.payload)
        else BrassActionResult.Failure(wire.code ?: "unknown", wire.args ?: emptyList())
    }

    /**
     * Encode [json] for the wire: a leading flag byte (0 = raw, 1 = gzip) followed by the body.
     * Compressing is the transport's choice - call this only when the payload is large enough to
     * matter; small payloads pass through untouched so the common case adds one byte.
     */
    fun compress(json: String): ByteArray {
        val raw = json.toByteArray(Charsets.UTF_8)
        if (raw.size < COMPRESS_THRESHOLD) {
            return ByteArray(raw.size + 1).also {
                it[0] = 0
                raw.copyInto(it, 1)
            }
        }
        val buffer = ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { it.write(raw) }
        val gzip = buffer.toByteArray()
        return ByteArray(gzip.size + 1).also {
            it[0] = 1
            gzip.copyInto(it, 1)
        }
    }

    /** The inverse of [compress]. Unknown flag bytes are returned as-is (defensive forward-compat). */
    fun decompress(bytes: ByteArray): String {
        val raw = when (bytes.getOrNull(0)) {
            0.toByte() -> bytes.copyOfRange(1, bytes.size)
            1.toByte() -> GZIPInputStream(ByteArrayInputStream(bytes, 1, bytes.size - 1)).use { it.readBytes() }
            else -> bytes
        }
        return String(raw, Charsets.UTF_8)
    }

    private class Wire(val ok: Boolean, val code: String?, val args: List<String>?, val payload: String?)
}
