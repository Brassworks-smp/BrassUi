package net.swzo.brass.ui.kit.net

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The one Gson instance the networking module serialises through, and the wire compression helpers.
 * The action bus now rides **BSON bytes** (see [BrassBson]) rather than JSON strings, so this object
 * is left with the two JSON helpers brassnet still genuinely needs: the Gson instance that serialises
 * `ok(payload)` values and the NeoForge `/brassui action` command bridge, and the raw byte
 * compressor/decompressor the payload codecs use (BSON chunks past [COMPRESS_THRESHOLD] travel gzip'd
 * so a repetitive large graph stays small on the wire even though it is no longer text).
 */
object BrassJson {

    /** Payloads below this many bytes travel raw; larger ones are gzip-compressed by [compress]. */
    const val COMPRESS_THRESHOLD = 256

    val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun toJson(value: Any?): String = gson.toJson(value)

    fun <T : Any> fromJson(json: String?, type: Class<T>): T? {
        if (json == null) return null
        return runCatching { gson.fromJson(json, type) }.getOrNull()
    }

    /**
     * Encode [data] for the wire: a leading flag byte (0 = raw, 1 = gzip) followed by the body.
     * Compressing is the transport's choice - call this only when the payload is large enough to
     * matter; small payloads pass through untouched so the common case adds one byte. The input is
     * raw BSON bytes, not text, so this is a plain binary gzip rather than a string round-trip.
     */
    fun compress(data: ByteArray): ByteArray {
        if (data.size < COMPRESS_THRESHOLD) {
            return ByteArray(data.size + 1).also {
                it[0] = 0
                data.copyInto(it, 1)
            }
        }
        val buffer = ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { it.write(data) }
        val gzip = buffer.toByteArray()
        return ByteArray(gzip.size + 1).also {
            it[0] = 1
            gzip.copyInto(it, 1)
        }
    }

    fun decompress(bytes: ByteArray): ByteArray {
        val raw = when (bytes.getOrNull(0)) {
            0.toByte() -> bytes.copyOfRange(1, bytes.size)
            1.toByte() -> GZIPInputStream(ByteArrayInputStream(bytes, 1, bytes.size - 1)).use { it.readBytes() }
            else -> bytes
        }
        return raw
    }
}
