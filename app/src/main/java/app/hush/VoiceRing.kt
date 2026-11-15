package app.hush

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

/**
 * The gold ring around the bubble disc. While Hush records, the ring is still: it only shows "recording"
 * (the ghost shows the voice level). While Hush transcribes, a short arc turns. When the phone has
 * Remove animations on, the arc stays still. A navy edge keeps the ring visible on light and dark apps.
 */
class VoiceRing(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    enum class Mode { OFF, LISTENING, BUSY }

    var mode = Mode.OFF
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val gold = context.getColor(R.color.gold)
    private val navy = context.getColor(R.color.navy)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = RectF()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        when (mode) {
            Mode.OFF -> Unit
            Mode.LISTENING -> ring(canvas, cx, cy, dp(LISTENING_GOLD), 0f, 360f)
            Mode.BUSY -> {
                val turning = ValueAnimator.areAnimatorsEnabled()
                val start = if (turning) (SystemClock.uptimeMillis() % TURN_MS) * 360f / TURN_MS - 90f else -90f
                ring(canvas, cx, cy, dp(BUSY_GOLD), start, BUSY_SWEEP)
                if (turning) postInvalidateOnAnimation()
            }
        }
    }

    private fun ring(canvas: Canvas, cx: Float, cy: Float, goldWidth: Float, start: Float, sweep: Float) {
        val radius = dp(RADIUS)
        val edge = dp(EDGE)
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
        stroke.color = navy
        stroke.strokeWidth = goldWidth + 2 * edge
        canvas.drawArc(arc, start, sweep, false, stroke)
        stroke.color = gold
        stroke.strokeWidth = goldWidth
        canvas.drawArc(arc, start, sweep, false, stroke)
    }

    private fun dp(value: Float) = value * density

    private companion object {
        // Sizes in dp from the motion mockup: the ring is 4 dp outside the 52 dp disc.
        const val RADIUS = 30f
        const val LISTENING_GOLD = 2.5f
        const val BUSY_GOLD = 3f
        const val EDGE = 1.25f
        const val BUSY_SWEEP = 108f
        const val TURN_MS = 1000L
    }
}
