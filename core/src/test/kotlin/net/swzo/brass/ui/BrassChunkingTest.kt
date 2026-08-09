package net.swzo.brass.ui

import net.swzo.brass.ui.kit.net.BrassChunking
import net.swzo.brass.ui.kit.net.BrassJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Wire chunking and compression are pure, so they are tested off-game. Bodies are BSON bytes now. */
class BrassChunkingTest {

    @Test
    fun `short values travel as a single chunk`() {
        val pieces = BrassChunking.split(byteArrayOf(1, 2, 3))
        assertEquals(1, pieces.size)
        assertTrue(pieces[0].contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `long values split into whole chunks and rejoin exactly`() {
        val big = ByteArray(2_000_000) { (it * 31 + 7).toByte() }
        val pieces = BrassChunking.split(big)
        assertTrue(pieces.size > 1, "expected multiple chunks, got ${pieces.size}")
        assertTrue(pieces.all { it.size <= BrassChunking.CHUNK_BYTES })
        assertTrue(big.contentEquals(pieces.reduce { a, b -> a + b }))
    }

    @Test
    fun `chunk sizes respect the cap`() {
        val big = ByteArray(BrassChunking.CHUNK_BYTES * 2 + 123)
        val pieces = BrassChunking.split(big)
        assertEquals(3, pieces.size)
        assertTrue(big.contentEquals(pieces.reduce { a, b -> a + b }))
    }

    @Test
    fun `compression round-trips below and above the threshold`() {
        for (data in listOf(byteArrayOf(1), ByteArray(10_000) { 0x59.toByte() })) {
            assertTrue(data.contentEquals(BrassJson.decompress(BrassJson.compress(data))))
        }
    }
}
