package app.hush

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

/**
 * The navy ghost on the bubble disc. While Hush records or transcribes, it moves as [GhostMotion] says.
 * When idle, or when the phone has Remove animations on, it stays still and draws no extra frames.
 */
class GhostView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val motion = GhostMotion()
    private val density = resources.displayMetrics.density
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.navy) }
    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.cream) }
    private val cheek = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.cheek) }
    private val path = Path()
    private val clip = Path()
    private val matrix = Matrix()
    private val values = FloatArray(9)
    private val oval = RectF()
    private var lastFrame = 0L

    var mode: GhostMotion.Mode
        get() = motion.mode
        set(value) {
            if (motion.mode == value) return
            motion.mode = value
            lastFrame = SystemClock.uptimeMillis()
            invalidate()
        }

    /** Sets the voice level from the RMS of the last audio chunk. */
    fun setRms(rms: Float) = motion.setRms(rms)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        clip.reset()
        clip.addCircle(w / 2f, h / 2f, CLIP_DP * density, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val moving = mode != GhostMotion.Mode.IDLE && ValueAnimator.areAnimatorsEnabled()
        val pose = if (moving) motion.step(now - lastFrame) else GhostMotion.Pose.STILL
        lastFrame = now
        canvas.save()
        canvas.clipPath(clip)
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(density, density)
        val t = GhostMotion.transform(pose)
        values[Matrix.MSCALE_X] = t.a
        values[Matrix.MSKEW_X] = t.b
        values[Matrix.MTRANS_X] = t.c
        values[Matrix.MSKEW_Y] = t.d
        values[Matrix.MSCALE_Y] = t.e
        values[Matrix.MTRANS_Y] = t.f
        values[Matrix.MPERSP_0] = 0f
        values[Matrix.MPERSP_1] = 0f
        values[Matrix.MPERSP_2] = 1f
        matrix.setValues(values)
        canvas.concat(matrix)
        drawGhost(canvas, pose)
        canvas.restore()
        if (moving) postInvalidateOnAnimation()
    }

    private fun drawGhost(canvas: Canvas, pose: GhostMotion.Pose) {
        path.reset()
        path.moveTo(36f, 54f)
        oval.set(36f, 36f, 72f, 72f)
        path.arcTo(oval, 180f, 180f)
        for (segment in GhostMotion.outline(pose).segments) {
            when (segment) {
                is GhostMotion.Segment.Line -> path.lineTo(segment.to.first, segment.to.second)
                is GhostMotion.Segment.Quad ->
                    path.quadTo(segment.control.first, segment.control.second, segment.to.first, segment.to.second)
            }
        }
        path.close()
        canvas.drawPath(path, body)
        canvas.drawCircle(47f, 54f, 3.4f, face)
        canvas.drawCircle(61f, 54f, 3.4f, face)
        oval.set(51.2f, 60.6f, 56.8f, 67.4f)
        canvas.drawOval(oval, face)
        canvas.drawCircle(42.5f, 61f, 3f, cheek)
        canvas.drawCircle(65.5f, 61f, 3f, cheek)
    }

    private companion object {
        // Half a dp inside the 52 dp disc, as in the mockup.
        const val CLIP_DP = 25.5f
    }
}
