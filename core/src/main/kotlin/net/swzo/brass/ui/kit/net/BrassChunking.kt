package net.swzo.brass.ui.kit.net

/**
 * Chunking for large BSON byte arrays that must cross the wire as one logical value.
 *
 * NeoForge custom payloads cap out around 1 MB, and a very large graph BSON can exceed that even
 * compressed. Values longer than [CHUNK_BYTES] are split *before* compression, travel as several
 * payloads that share a transfer identity, and are reassembled on the receiving side before the
 * handler ever sees them - so huge graphs move as a stream of small, safe packets.
 */
object BrassChunking {

    /** One wire chunk may carry up to this many raw BSON bytes (compressed it is only a few KB). */
    const val CHUNK_BYTES = 128 * 1024

    /** Reassembled values are capped, so a hostile peer cannot hold unbounded buffers. */
    const val MAX_TOTAL_BYTES = 64 * 1024 * 1024

    /** Split [data] into whole chunks; a short value travels as a single chunk. */
    fun split(data: ByteArray): List<ByteArray> {
        if (data.size <= CHUNK_BYTES) return listOf(data)
        val chunks = ArrayList<ByteArray>((data.size + CHUNK_BYTES - 1) / CHUNK_BYTES)
        var start = 0
        while (start < data.size) {
            chunks.add(data.copyOfRange(start, minOf(start + CHUNK_BYTES, data.size)))
            start += CHUNK_BYTES
        }
        return chunks
    }
}
