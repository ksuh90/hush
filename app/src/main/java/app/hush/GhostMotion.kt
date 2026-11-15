package app.hush

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * How the bubble ghost moves (mockup ghost-motion-v3, version 3). The hem flutters like cloth. While the user
 * speaks, the body also sways from the top of the head and the hem squashes. Both grow with the voice level.
 * Coordinates are in the 46 x 56 viewport of the ghost path (x 31 to 77, y 31 to 87). This class has no
 * Android code, so JVM tests can check it.
 */
class GhostMotion {
    enum class Mode { IDLE, LISTENING, BUSY }

    /** One frame. [flutter] and [phase] move the hem, [tilt] (degrees) and [stretch] move the whole ghost. */
    data class Pose(val flutter: Float, val phase: Float, val tilt: Float, val stretch: Float) {
        companion object {
            val STILL = Pose(0f, 0f, 0f, 0f)
        }
    }

    var mode = Mode.IDLE
        set(value) {
            if (field == value) return
            field = value
            target = 0f
            level = 0f
        }

    /** The smoothed voice level, 0 to 1. */
    var level = 0f
        private set
    private var target = 0f
    private var phase = 0.0
    private var seconds = 0.0

    /** Sets the voice level from the RMS of the last audio chunk. */
    fun setRms(rms: Float) {
        if (mode == Mode.LISTENING) target = levelOf(rms)
    }

    /** Moves time forward by [ms] and returns the pose for the new frame. */
    fun step(ms: Long): Pose {
        val dt = ms.coerceIn(0, MAX_STEP_MS)
        if (mode == Mode.IDLE) return Pose.STILL
        if (mode == Mode.LISTENING) {
            val tau = if (target > level) ATTACK_MS else RELEASE_MS
            level += (target - level) * (1 - exp(-dt / tau))
        }
        val busy = mode == Mode.BUSY
        val flutter = if (busy) BUSY_FLUTTER else QUIET_FLUTTER + LOUD_FLUTTER * level
        val speed = if (busy) BUSY_SPEED else QUIET_SPEED + LOUD_SPEED * level
        seconds += dt / 1000.0
        phase = (phase + 2 * PI * speed * dt / 1000.0) % (2 * PI)
        if (busy) return Pose(flutter, phase.toFloat(), 0f, 0f)
        val s = strength(level)
        val tilt = TILT * s * sin(2 * PI * SWAY_HZ * seconds)
        val stretch = STRETCH * s * sin(2 * PI * SQUASH_HZ * seconds)
        return Pose(flutter, phase.toFloat(), tilt.toFloat(), stretch.toFloat())
    }

    companion object {
        // The voice level, as the gold ring of 0.3.0 used it.
        private const val SILENT_DB = -50f
        private const val LOUD_DB = -20f
        private const val ATTACK_MS = 60f
        private const val RELEASE_MS = 180f
        private const val MAX_STEP_MS = 100L

        // Hem flutter (mockup idea 4): height in viewport units and speed in Hz.
        const val QUIET_FLUTTER = 0.6f
        const val LOUD_FLUTTER = 3.2f
        const val QUIET_SPEED = 1.0f
        const val LOUD_SPEED = 2.2f
        const val BUSY_FLUTTER = 1.2f
        const val BUSY_SPEED = 1.2f
        private const val SIDE_SWAY = 0.45f

        // The wobble: no wobble below level 0.15, full wobble at level 0.95, power 1.4 keeps most of it for loud speech.
        private const val KNEE = 0.15f
        private const val SPAN = 0.80f
        private const val POWER = 1.4
        const val TILT = 12.0       // degrees, pendulum from the top of the head
        const val SWAY_HZ = 0.8
        const val STRETCH = 0.14    // height change of the squash on the hem; the width changes by 0.10
        const val SQUASH_HZ = 2.0
        private const val NARROW = 0.10f / 0.14f

        // Pivots and the ghost_plain.xml group transform.
        private const val PIVOT_X = 54f
        private const val HEAD_Y = 36f
        private const val HEM_Y = 82f
        private const val GROUP_X = 52f
        private const val GROUP_Y = 58f
        private const val GROUP_SCALE = 0.92f
        private const val GROUP_ROTATION = -8.0
        private const val CENTER_Y = 59f

        /** The ghost is 28 dp wide in the 52 dp disc. */
        const val GHOST_DP = 28f
        const val DISC_DP = 52f

        /** Maps the RMS of an audio chunk (0 to 1) to a level (0 to 1) on a decibel scale. */
        fun levelOf(rms: Float): Float {
            if (rms <= 0f) return 0f
            val db = 20 * log10(rms)
            return ((db - SILENT_DB) / (LOUD_DB - SILENT_DB)).coerceIn(0f, 1f)
        }

        /** Wobble strength (0 to 1) for a voice level. */
        fun strength(level: Float): Float =
            ((level - KNEE) / SPAN).coerceIn(0f, 1f).toDouble().pow(POWER).toFloat()

        /**
         * The body outline for a pose, as path commands: [Outline.arc] is the head (from x 36 to 72 over the top),
         * then [Outline.points] are line and quad points down the right side, along the hem and up the left side.
         */
        fun outline(pose: Pose): Outline {
            fun hem(x: Float) = pose.flutter * sin(pose.phase - (x - 36f) / 36f * 2f * PI.toFloat())
            fun side(y: Float) = (y - 56f) / 24f * pose.flutter * SIDE_SWAY * sin(pose.phase)
            fun move(x: Float, y: Float): Pair<Float, Float> {
                val ny = if (y >= 74f) y + hem(x) else y
                val nx = if (y > 56f) x + side(y) else x
                return nx to ny
            }
            val segments = ArrayList<Segment>()
            for (k in 1..6) segments += Segment.Line(move(72f, 54f + k * 26f / 6f))
            for ((control, end) in HEM) segments += Segment.Quad(move(control.first, control.second), move(end.first, end.second))
            for (k in 1..5) segments += Segment.Line(move(36f, 80f - k * 26f / 6f))
            return Outline(segments)
        }

        private val HEM = listOf(
            (69f to 75.5f) to (66f to 80f), (63f to 84.5f) to (60f to 80f), (57f to 75.5f) to (54f to 80f),
            (51f to 84.5f) to (48f to 80f), (45f to 75.5f) to (42f to 80f), (39f to 84.5f) to (36f to 80f),
        )

        /** The transform from viewport units to dp around the disc center, for a pose. */
        fun transform(pose: Pose): Affine {
            val dp = GHOST_DP / 46f
            val toDisc = Affine.scale(dp, dp).then(Affine.translate(-PIVOT_X, -CENTER_Y))
            val group = Affine.translate(GROUP_X, GROUP_Y)
                .then(Affine.rotate(GROUP_ROTATION))
                .then(Affine.scale(GROUP_SCALE, GROUP_SCALE))
                .then(Affine.translate(-PIVOT_X, -GROUP_Y))
            val sway = Affine.translate(PIVOT_X, HEAD_Y).then(Affine.rotate(pose.tilt.toDouble())).then(Affine.translate(-PIVOT_X, -HEAD_Y))
            val squash = Affine.translate(PIVOT_X, HEM_Y)
                .then(Affine.scale(1f - NARROW * pose.stretch, 1f + pose.stretch))
                .then(Affine.translate(-PIVOT_X, -HEM_Y))
            return toDisc.then(group).then(sway).then(squash)
        }
    }

    sealed class Segment {
        data class Line(val to: Pair<Float, Float>) : Segment()
        data class Quad(val control: Pair<Float, Float>, val to: Pair<Float, Float>) : Segment()
    }

    /** The body: a half circle head (center 54, 54, radius 18) from (36, 54) to (72, 54), then [segments]. */
    data class Outline(val segments: List<Segment>)

    /** x' = a x + b y + c, y' = d x + e y + f. [then] applies the other transform first. */
    data class Affine(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float) {
        fun then(o: Affine) = Affine(
            a * o.a + b * o.d, a * o.b + b * o.e, a * o.c + b * o.f + c,
            d * o.a + e * o.d, d * o.b + e * o.e, d * o.c + e * o.f + f,
        )

        fun map(x: Float, y: Float) = (a * x + b * y + c) to (d * x + e * y + f)

        companion object {
            fun translate(x: Float, y: Float) = Affine(1f, 0f, x, 0f, 1f, y)
            fun scale(x: Float, y: Float) = Affine(x, 0f, 0f, 0f, y, 0f)
            fun rotate(degrees: Double): Affine {
                val r = Math.toRadians(degrees)
                val c = cos(r).toFloat()
                val s = sin(r).toFloat()
                return Affine(c, -s, 0f, s, c, 0f)
            }
        }
    }
}
