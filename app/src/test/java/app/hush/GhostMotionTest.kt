package app.hush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

class GhostMotionTest {
    /** The RMS that gives [level] on the -50 to -20 dBFS scale. */
    private fun rmsFor(level: Float) = 10f.pow((-50f + 30f * level) / 20f)

    /** Runs [seconds] of 60 fps frames at a steady voice level and returns the poses. */
    private fun listen(level: Float, seconds: Double = 4.0): Pair<GhostMotion, List<GhostMotion.Pose>> {
        val motion = GhostMotion()
        motion.mode = GhostMotion.Mode.LISTENING
        val poses = (0 until (seconds * 60).toInt()).map {
            if (it % 6 == 0) motion.setRms(rmsFor(level))
            motion.step(16)
        }
        return motion to poses
    }

    private fun steady(poses: List<GhostMotion.Pose>) = poses.drop(60)

    @Test
    fun levelUsesADecibelScale() {
        assertEquals(0f, GhostMotion.levelOf(0f), 0f)
        assertEquals(0f, GhostMotion.levelOf(0.002f), 0f)
        assertEquals(1f, GhostMotion.levelOf(0.1f), 0.001f)
        assertEquals(1f, GhostMotion.levelOf(0.9f), 0f)
        assertEquals(0.5f, GhostMotion.levelOf(0.0178f), 0.01f)
        val levels = listOf(0.005f, 0.01f, 0.03f, 0.06f).map(GhostMotion::levelOf)
        assertTrue(levels.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun strengthFollowsTheMockupCurve() {
        assertEquals(0f, GhostMotion.strength(0f), 0f)
        assertEquals(0f, GhostMotion.strength(0.15f), 0f)
        assertEquals(0.379f, GhostMotion.strength(0.55f), 0.002f)
        assertEquals(0.948f, GhostMotion.strength(0.92f), 0.002f)
        assertEquals(1f, GhostMotion.strength(0.95f), 1e-5f)
        assertEquals(1f, GhostMotion.strength(1f), 0f)
    }

    @Test
    fun quietSpeechIsCalm() {
        val (_, poses) = listen(0.10f)
        for (pose in steady(poses)) {
            assertEquals(0f, pose.tilt, 0f)
            assertEquals(0f, pose.stretch, 0f)
            assertTrue(pose.flutter < 1f)
        }
    }

    @Test
    fun normalSpeechWobblesClearly() {
        val (motion, poses) = listen(0.55f)
        assertEquals(0.55f, motion.level, 0.01f)
        val tilt = steady(poses).maxOf { abs(it.tilt) }
        val stretch = steady(poses).maxOf { abs(it.stretch) }
        assertEquals(4.5f, tilt, 0.15f)
        assertEquals(0.053f, stretch, 0.002f)
    }

    @Test
    fun loudSpeechWobblesMuchMore() {
        val (_, poses) = listen(0.92f)
        val tilt = steady(poses).maxOf { abs(it.tilt) }
        val stretch = steady(poses).maxOf { abs(it.stretch) }
        assertEquals(11.4f, tilt, 0.2f)
        assertEquals(0.133f, stretch, 0.003f)
        assertEquals(GhostMotion.QUIET_FLUTTER + GhostMotion.LOUD_FLUTTER * 0.92f, steady(poses).last().flutter, 0.05f)
    }

    @Test
    fun levelRisesFasterThanItFalls() {
        val motion = GhostMotion()
        motion.mode = GhostMotion.Mode.LISTENING
        motion.setRms(rmsFor(1f))
        motion.step(60)
        val up = motion.level
        repeat(20) { motion.step(16) }
        motion.setRms(rmsFor(0f))
        val top = motion.level
        motion.step(60)
        assertTrue(up > 0.6f)
        assertTrue(top - motion.level < up)
    }

    @Test
    fun idleIsStillAndIgnoresTheVoice() {
        val motion = GhostMotion()
        motion.setRms(rmsFor(1f))
        repeat(30) { assertEquals(GhostMotion.Pose.STILL, motion.step(16)) }
        assertEquals(0f, motion.level, 0f)
    }

    @Test
    fun transcribingFluttersWithoutWobble() {
        val motion = GhostMotion()
        motion.mode = GhostMotion.Mode.LISTENING
        repeat(30) { motion.setRms(rmsFor(1f)); motion.step(16) }
        motion.mode = GhostMotion.Mode.BUSY
        assertEquals(0f, motion.level, 0f)
        repeat(60) {
            val pose = motion.step(16)
            assertEquals(GhostMotion.BUSY_FLUTTER, pose.flutter, 0f)
            assertEquals(0f, pose.tilt, 0f)
            assertEquals(0f, pose.stretch, 0f)
        }
    }

    @Test
    fun ghostStaysInsideTheDiscAtFullLevel() {
        val (_, poses) = listen(1f, seconds = 6.0)
        val limit = GhostMotion.DISC_DP / 2 - 0.5f
        for (pose in poses) {
            val t = GhostMotion.transform(pose)
            val points = GhostMotion.outline(pose).segments.flatMap {
                when (it) {
                    is GhostMotion.Segment.Line -> listOf(it.to)
                    is GhostMotion.Segment.Quad -> listOf(it.control, it.to)
                }
            } + (0..18).map { 54f + 18f * kotlin.math.cos(Math.PI * (1 + it / 18.0)).toFloat() to 54f + 18f * kotlin.math.sin(Math.PI * (1 + it / 18.0)).toFloat() }
            for ((x, y) in points) {
                val (dx, dy) = t.map(x, y)
                assertTrue("($x, $y) -> ${hypot(dx, dy)} dp", hypot(dx, dy) <= limit)
            }
        }
    }

    @Test
    fun stillPoseMatchesTheLogoGhost() {
        val t = GhostMotion.transform(GhostMotion.Pose.STILL)
        // The ghost is 28 dp wide: the head (36 to 72 wide, 46 units) maps to about 28 * 36 / 46 * 0.92 dp.
        val (lx, ly) = t.map(36f, 54f)
        val (rx, ry) = t.map(72f, 54f)
        assertEquals(28f * 36f / 46f * 0.92f, hypot(rx - lx, ry - ly), 0.01f)
        val outline = GhostMotion.outline(GhostMotion.Pose.STILL).segments
        assertEquals(GhostMotion.Segment.Line(72f to 80f), outline[5])
        assertEquals(GhostMotion.Segment.Quad(69f to 75.5f, 66f to 80f), outline[6])
    }
}
