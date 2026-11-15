package app.hush

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10

/**
 * The gold ring around the bubble ghost. While Hush records, the ring grows with the voice level.
 * While Hush transcribes, a short arc turns. A navy edge keeps the ring visible on light and dark apps.
 */
class VoiceRing(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    enum class Mode { OFF, LISTENING, BUSY }

    var mode = Mode.OFF
        set(value) {
            if (field == value) return
            field = value
            if (value != Mode.LISTENING) {
                target = 0f
                level = 0f
            }
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val gold = context.getColor(R.color.gold)
    private val navy = context.getColor(R.color.navy)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.glow) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = RectF()
    private var target = 0f
    private var level = 0f
    private var lastFrame = 0L

    /** Sets the voice level from the RMS of the last audio chunk. */
    fun setRms(rms: Float) {
        target = levelOf(rms)
        if (mode == Mode.LISTENING) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val cx = width / 2f
        val cy = height / 2f
        when (mode) {
            Mode.OFF -> Unit
            Mode.LISTENING -> {
                val dt = (now - lastFrame).coerceIn(0, 100)
                val tau = if (target > level) ATTACK_MS else RELEASE_MS
                level += (target - level) * (1 - exp(-dt / tau))
                val radius = dp(QUIET_RADIUS + (LOUD_RADIUS - QUIET_RADIUS) * level)
                val goldWidth = dp(QUIET_GOLD + (LOUD_GOLD - QUIET_GOLD) * level)
                val edge = dp(QUIET_EDGE + (LOUD_EDGE - QUIET_EDGE) * level)
                if (level > 0.01f) {
                    glow.alpha = (GLOW_ALPHA * level).toInt()
                    canvas.drawCircle(cx, cy, radius + dp(GLOW_GROW * level), glow)
                }
                ring(canvas, cx, cy, radius, goldWidth, edge, 0f, 360f)
                if (abs(target - level) > 0.005f) postInvalidateOnAnimation()
            }
            Mode.BUSY -> {
                val start = (now % TURN_MS) * 360f / TURN_MS - 90f
                ring(canvas, cx, cy, dp(QUIET_RADIUS), dp(BUSY_GOLD), dp(QUIET_EDGE), start, BUSY_SWEEP)
                postInvalidateOnAnimation()
            }
        }
        lastFrame = now
    }

    private fun ring(canvas: Canvas, cx: Float, cy: Float, radius: Float, goldWidth: Float, edge: Float, start: Float, sweep: Float) {
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
        stroke.color = navy
        stroke.strokeWidth = goldWidth + 2 * edge
        canvas.drawArc(arc, start, sweep, false, stroke)
        stroke.color = gold
        stroke.strokeWidth = goldWidth
        canvas.drawArc(arc, start, sweep, false, stroke)
    }

    private fun dp(value: Float) = value * density

    companion object {
        // Sizes in dp from the mockup: a still ring when quiet, a larger ring and a glow when loud.
        private const val QUIET_RADIUS = 28.5f
        private const val LOUD_RADIUS = 33f
        private const val QUIET_GOLD = 2.5f
        private const val LOUD_GOLD = 4f
        private const val QUIET_EDGE = 1.25f
        private const val LOUD_EDGE = 1.5f
        private const val GLOW_GROW = 6f
        private const val GLOW_ALPHA = 0x47
        private const val BUSY_GOLD = 3f
        private const val BUSY_SWEEP = 108f
        private const val TURN_MS = 1000L
        private const val ATTACK_MS = 60f
        private const val RELEASE_MS = 180f
        private const val SILENT_DB = -50f
        private const val LOUD_DB = -20f

        /** Maps the RMS of an audio chunk (0 to 1) to a ring level (0 to 1) on a decibel scale. */
        fun levelOf(rms: Float): Float {
            if (rms <= 0f) return 0f
            val db = 20 * log10(rms)
            return ((db - SILENT_DB) / (LOUD_DB - SILENT_DB)).coerceIn(0f, 1f)
        }
    }
}
