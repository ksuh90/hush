package app.hush

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import kotlin.math.abs
import kotlin.math.roundToInt

/** The floating ghost window. It uses TYPE_ACCESSIBILITY_OVERLAY and never takes input focus. */
class Bubble(
    private val context: Context,
    private val prefs: Prefs,
    private val onAction: (Action) -> Unit,
) {
    enum class Action { MIC, CANCEL, CONFIRM }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    // The bubble is the root of its own window, so it has no parent.
    @SuppressLint("InflateParams")
    private val root: View = LayoutInflater.from(context).inflate(R.layout.bubble, null)
    private val cancel: ImageButton = root.findViewById(R.id.cancel)
    private val confirm: ImageButton = root.findViewById(R.id.confirm)
    private val mic: ImageButton = root.findViewById(R.id.mic)
    private val ring: VoiceRing = root.findViewById(R.id.ring)
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private var attached = false
    private var expanded = false
    private var confirmShown = false
    private var keyboardTop: Int? = null
    private var dragAnchor: Pair<Int, Int>? = null

    // DragListener calls performClick for a tap.
    @SuppressLint("ClickableViewAccessibility")
    private fun listen() {
        cancel.setOnClickListener { onAction(Action.CANCEL) }
        confirm.setOnClickListener { onAction(Action.CONFIRM) }
        mic.setOnClickListener { onAction(Action.MIC) }
        mic.setOnTouchListener(DragListener())
    }

    init {
        listen()
    }

    fun render(visible: Boolean, state: State, keyboardTop: Int?) {
        this.keyboardTop = keyboardTop
        if (!visible) {
            remove()
            return
        }
        val recording = state is State.Recording
        val transcribing = state is State.Transcribing
        expanded = recording || transcribing
        confirmShown = recording
        cancel.visibility = if (expanded) View.VISIBLE else View.GONE
        confirm.visibility = if (recording) View.VISIBLE else View.GONE
        ring.mode = when {
            recording -> VoiceRing.Mode.LISTENING
            transcribing -> VoiceRing.Mode.BUSY
            else -> VoiceRing.Mode.OFF
        }
        mic.contentDescription = context.getString(if (recording) R.string.bubble_stop else R.string.bubble_start)
        place()
        if (attached) {
            windowManager.updateViewLayout(root, params)
        } else {
            windowManager.addView(root, params)
            attached = true
        }
    }

    fun setRms(rms: Float) = ring.setRms(rms)

    fun remove() {
        if (attached) {
            windowManager.removeView(root)
            attached = false
        }
    }

    private fun dp(value: Int) = (value * density).roundToInt()

    private fun anchor(): Pair<Int, Int> {
        dragAnchor?.let { return it }
        prefs.bubbleAnchor?.let { return clamp(it) }
        val bounds = windowManager.currentWindowMetrics.bounds
        // Leave space for the check button, so that the ghost does not move when recording starts.
        val x = bounds.width() - dp(EDGE + SPAN) - dp(FRAME) / 2
        val bottom = keyboardTop ?: (bounds.height() * 0.6f).roundToInt()
        return clamp(x to bottom - dp(KEYBOARD_GAP) - dp(GHOST) / 2)
    }

    private fun clamp(point: Pair<Int, Int>): Pair<Int, Int> {
        val bounds = windowManager.currentWindowMetrics.bounds
        val half = dp(FRAME) / 2 + dp(PAD)
        return point.first.coerceIn(half, bounds.width() - half) to point.second.coerceIn(half, bounds.height() - half)
    }

    /**
     * Puts the window so that the ghost center stays on the anchor when the buttons show.
     * Near a screen edge, the window moves to keep all buttons on the screen.
     */
    private fun place() {
        val (x, y) = anchor()
        val side = dp(SPAN)
        val left = dp(PAD) + (if (expanded) side else 0) + dp(FRAME) / 2
        val width = 2 * dp(PAD) + dp(FRAME) + (if (expanded) side else 0) + (if (confirmShown) side else 0)
        val screenWidth = windowManager.currentWindowMetrics.bounds.width()
        val margin = dp(EDGE - PAD)
        params.x = (x - left).coerceIn(margin, maxOf(margin, screenWidth - width - margin))
        params.y = y - dp(PAD) - dp(FRAME) / 2
    }

    private inner class DragListener : View.OnTouchListener {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var start = 0 to 0
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    start = anchor()
                    dragging = false
                    view.isPressed = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        view.isPressed = false
                    }
                    if (dragging && attached) {
                        dragAnchor = clamp((start.first + dx).roundToInt() to (start.second + dy).roundToInt())
                        place()
                        windowManager.updateViewLayout(root, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    if (dragging) {
                        prefs.bubbleAnchor = dragAnchor
                        dragAnchor = null
                    } else {
                        view.performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    dragAnchor?.let { prefs.bubbleAnchor = it }
                    dragAnchor = null
                }
            }
            return true
        }
    }

    private companion object {
        // Sizes in dp. They agree with bubble.xml.
        const val FRAME = 80
        const val GHOST = 48
        // From the frame edge to the outer edge of the touch area of X or the check.
        const val SPAN = 44
        const val PAD = 6
        const val EDGE = 10
        const val KEYBOARD_GAP = 22
    }
}
