package app.hush

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The floating ghost with its X and check buttons. They use TYPE_ACCESSIBILITY_OVERLAY windows and never take
 * input focus. Each one has its own window: when a window changes size and position together, Android
 * animates the move, and then the ghost jumps aside and slides back when the buttons show or go away.
 */
class Bubble(
    private val context: Context,
    private val prefs: Prefs,
    private val onAction: (Action) -> Unit,
) {
    enum class Action { MIC, CANCEL, CONFIRM }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val main = Overlay(R.layout.bubble)
    private val cancelWindow = Overlay(R.layout.bubble_button)
    private val confirmWindow = Overlay(R.layout.bubble_button)
    private val mic: ImageButton = main.root.findViewById(R.id.mic)
    private val ring: VoiceRing = main.root.findViewById(R.id.ring)
    private val ghost: GhostView = main.root.findViewById(R.id.ghost)
    private val cancel: ImageButton = cancelWindow.root.findViewById(R.id.button)
    private val confirm: ImageButton = confirmWindow.root.findViewById(R.id.button)

    private var visible = false
    private var expanded = false
    private var confirmShown = false
    private var keyboardTop: Int? = null
    private var dragAnchor: Pair<Int, Int>? = null
    private var scene: SpotPicker.Scene? = null
    private var spot: Pair<Int, Int>? = null

    // DragListener calls performClick for a tap.
    @SuppressLint("ClickableViewAccessibility")
    private fun listen() {
        cancel.setOnClickListener { onAction(Action.CANCEL) }
        confirm.setOnClickListener { onAction(Action.CONFIRM) }
        mic.setOnClickListener { onAction(Action.MIC) }
        mic.setOnTouchListener(DragListener())
    }

    init {
        cancel.setBackgroundResource(R.drawable.bubble_cancel)
        cancel.setImageResource(R.drawable.ic_x)
        cancel.contentDescription = context.getString(R.string.bubble_cancel)
        confirm.setBackgroundResource(R.drawable.bubble_confirm)
        confirm.setImageResource(R.drawable.ic_check)
        confirm.contentDescription = context.getString(R.string.bubble_confirm)
        listen()
    }

    /** [field] is the focused field and the views around it, or null when it is not known. */
    fun render(visible: Boolean, state: State, keyboardTop: Int?, field: SpotPicker.Field?) {
        if (!visible) {
            this.keyboardTop = keyboardTop
            remove()
            return
        }
        this.visible = true
        val recording = state is State.Recording
        val transcribing = state is State.Transcribing
        // While Hush records or transcribes, the ghost stays where it is.
        if (!recording && !transcribing) {
            this.keyboardTop = keyboardTop
            pickSpot(field)
        }
        expanded = recording || transcribing
        confirmShown = recording
        ring.mode = when {
            recording -> VoiceRing.Mode.LISTENING
            transcribing -> VoiceRing.Mode.BUSY
            else -> VoiceRing.Mode.OFF
        }
        ghost.mode = when {
            recording -> GhostMotion.Mode.LISTENING
            transcribing -> GhostMotion.Mode.BUSY
            else -> GhostMotion.Mode.IDLE
        }
        mic.contentDescription = context.getString(if (recording) R.string.bubble_stop else R.string.bubble_start)
        place()
    }

    fun setRms(rms: Float) = ghost.setRms(rms)

    fun remove() {
        visible = false
        confirmWindow.hide()
        cancelWindow.hide()
        main.hide()
    }

    private fun dp(value: Int) = (value * density).roundToInt()

    val sizes
        get() = SpotPicker.Sizes(
            ghost = dp(GHOST_HALF), button = button / 2, gap = dp(BUTTON_GAP), space = dp(SPACE), edge = dp(EDGE), step = dp(STEP),
        )

    /** Picks the default spot again only when the field, the views around it or the keyboard changed. */
    private fun pickSpot(field: SpotPicker.Field?) {
        if (field == null) {
            scene = null
            spot = null
            return
        }
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val statusBar = metrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top
        val next = SpotPicker.Scene(SpotPicker.Box(bounds.left, bounds.top + statusBar, bounds.right, bounds.bottom), keyboardTop, field)
        if (next == scene) return
        // When only the views near the field changed, the ghost stays while its place is still clear.
        val last = scene
        val same = last != null && last.screen == next.screen && last.keyboardTop == next.keyboardTop &&
            last.field.bounds == next.field.bounds
        scene = next
        spot = spot?.takeIf { same && SpotPicker.isClear(it, next, sizes) } ?: SpotPicker.pick(next, sizes)
    }

    // Sizes in pixels. The window padding is the same in both layouts.
    private val pad get() = main.root.paddingStart
    private val frame get() = ring.layoutParams.width
    private val button get() = cancel.layoutParams.width

    /** From the ghost center to the outer edge of a button. */
    private val reach get() = dp(BUTTON_GAP) + button / 2

    private fun anchor(): Pair<Int, Int> {
        dragAnchor?.let { return it }
        prefs.bubbleAnchor?.let { return clamp(it) }
        spot?.let { return it }
        // Without the field bounds: above the keyboard on the right.
        val bounds = windowManager.currentWindowMetrics.bounds
        // Leave space for the check button, so that the ghost does not move when recording starts.
        val x = bounds.width() - dp(EDGE) - reach
        val bottom = keyboardTop ?: (bounds.height() * 0.6f).roundToInt()
        return clamp(x to bottom - dp(KEYBOARD_GAP) - dp(START_HALF))
    }

    private fun clamp(point: Pair<Int, Int>): Pair<Int, Int> {
        val bounds = windowManager.currentWindowMetrics.bounds
        val half = frame / 2 + pad
        return point.first.coerceIn(half, bounds.width() - half) to point.second.coerceIn(half, bounds.height() - half)
    }

    /**
     * Puts the ghost center on the anchor and the buttons beside it. Near a screen edge, the ghost moves to keep
     * all buttons on the screen. The windows only move here and never change size, so Android does not animate them.
     */
    private fun place() {
        if (!visible) return
        val (x, y) = anchor()
        val screenWidth = windowManager.currentWindowMetrics.bounds.width()
        val low = dp(EDGE) + if (expanded) reach else frame / 2
        val high = screenWidth - dp(EDGE) - if (confirmShown) reach else frame / 2
        val cx = x.coerceIn(low, maxOf(low, high))
        main.show(cx - pad - frame / 2, y - pad - frame / 2)
        val buttonY = y - pad - button / 2
        if (expanded) cancelWindow.show(cx - reach - pad, buttonY) else cancelWindow.hide()
        if (confirmShown) confirmWindow.show(cx + reach - button - pad, buttonY) else confirmWindow.hide()
    }

    /** One overlay window. The window added last is on top, so the buttons draw over the edge of the ring frame. */
    private inner class Overlay(layout: Int) {
        // The view is the root of its own window, so it has no parent.
        @SuppressLint("InflateParams")
        val root: View = LayoutInflater.from(context).inflate(layout, null)
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

        fun show(x: Int, y: Int) {
            if (attached && params.x == x && params.y == y) return
            params.x = x
            params.y = y
            if (attached) {
                windowManager.updateViewLayout(root, params)
            } else {
                windowManager.addView(root, params)
                attached = true
            }
        }

        fun hide() {
            if (attached) {
                windowManager.removeView(root)
                attached = false
            }
        }
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
                    if (dragging) {
                        dragAnchor = clamp((start.first + dx).roundToInt() to (start.second + dy).roundToInt())
                        place()
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
        // Sizes in dp.
        // Half of the 0.3.0 ghost (48 dp), for the spot when the field bounds are not known.
        const val START_HALF = 24
        // For the spot picker: half of the ghost with its ring, the free space around it, and the search step.
        const val GHOST_HALF = 30
        const val SPACE = 4
        const val STEP = 12
        // From the ghost center to the center of the X or the check.
        const val BUTTON_GAP = 60
        // From the screen edge to the outer edge of the check button.
        const val EDGE = 10
        const val KEYBOARD_GAP = 22
    }
}
