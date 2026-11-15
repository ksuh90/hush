package app.hush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRingTest {
    @Test
    fun levelUsesADecibelScale() {
        assertEquals(0f, VoiceRing.levelOf(0f), 0f)
        assertEquals(0f, VoiceRing.levelOf(0.002f), 0f)
        assertEquals(1f, VoiceRing.levelOf(0.1f), 0.001f)
        assertEquals(1f, VoiceRing.levelOf(0.9f), 0f)
        assertEquals(0.5f, VoiceRing.levelOf(0.0178f), 0.01f)
    }

    @Test
    fun levelGrowsWithVoice() {
        val levels = listOf(0.005f, 0.01f, 0.03f, 0.06f).map(VoiceRing::levelOf)
        assertTrue(levels.zipWithNext().all { (a, b) -> b > a })
    }
}
