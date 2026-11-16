package app.hush

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ChunksTest {
    private fun seg(value: Float, size: Int) = FloatArray(size) { value }

    @Test
    fun joinsShortSegmentsWithAGap() {
        val chunks = Chunks.pack(listOf(seg(1f, 3), seg(2f, 2)), maxSamples = 10, gapSamples = 1)
        assertEquals(1, chunks.size)
        assertArrayEquals(floatArrayOf(1f, 1f, 1f, 0f, 2f, 2f), chunks[0], 0f)
    }

    @Test
    fun startsANewChunkAtTheLimit() {
        val chunks = Chunks.pack(listOf(seg(1f, 4), seg(2f, 4), seg(3f, 4)), maxSamples = 9, gapSamples = 1)
        assertEquals(listOf(9, 4), chunks.map { it.size })
        assertEquals(3f, chunks[1][0])
    }

    @Test
    fun keepsALongSegmentAlone() {
        val chunks = Chunks.pack(listOf(seg(1f, 2), seg(2f, 20), seg(3f, 2)), maxSamples = 10, gapSamples = 1)
        assertEquals(listOf(2, 20, 2), chunks.map { it.size })
    }

    @Test
    fun noSpeechGivesNoChunks() {
        assertEquals(0, Chunks.pack(emptyList(), 10, 1).size)
        assertEquals(0, Chunks.pack(listOf(FloatArray(0)), 10, 1).size)
    }
}
