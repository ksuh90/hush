package app.hush

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.floor

/** Shows the bubble on focused text fields and runs one dictation at a time. */
class HushService : AccessibilityService(), Recorder.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val machine = DictationMachine()
    private lateinit var prefs: Prefs
    private lateinit var bubble: Bubble
    private lateinit var engine: Engine
    private lateinit var worker: ExecutorService
    private val refreshTask = Runnable { refresh() }
    private val unloadTask = Runnable { worker.execute { engine.release() } }

    // The focused field and the views around it. Hush reads the views once per focus, and again when the field
    // moves or changes size.
    private var fieldNode: AccessibilityNodeInfo? = null
    private var fieldBounds: SpotPicker.Box? = null
    private var around = Around(emptyList(), emptyList(), emptyList())

    /** The other inputs, the buttons and the labels in the window of the focused field. */
    private class Around(val inputs: List<SpotPicker.Box>, val buttons: List<SpotPicker.Box>, val labels: List<SpotPicker.Box>)

    override fun onServiceConnected() {
        prefs = Prefs(this)
        engine = Engine(this)
        worker = Executors.newSingleThreadExecutor()
        bubble = Bubble(this, prefs, ::onAction)
        Recorder.listener = this
        worker.execute { ModelStore.of(this).prune(this) }
        refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        main.removeCallbacks(refreshTask)
        main.postDelayed(refreshTask, REFRESH_DELAY_MS)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        if (::bubble.isInitialized) {
            if (machine.state is State.Recording) machine.on(Event.Cancel).forEach(::run)
            Recorder.listener = null
            bubble.remove()
            worker.execute { engine.release() }
            worker.shutdown()
        }
        super.onDestroy()
    }

    private fun focusedField(): AccessibilityNodeInfo? =
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable && !it.isPassword }

    private fun keyboardTop(): Int? {
        val keyboard = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return null
        return Rect().also(keyboard::getBoundsInScreen).top
    }

    private fun refresh() {
        if (!::bubble.isInitialized) return
        val keyboard = keyboardTop()
        val busy = machine.state != State.Idle
        val field = focusedField()
        val visible = busy || (keyboard != null && field != null)
        if (!visible) fieldNode = null
        // While Hush records or transcribes, the ghost does not move, so the field is not read.
        bubble.render(visible, machine.state, keyboard, if (visible && !busy) field?.let(::fieldOf) else null)
    }

    private fun fieldOf(field: AccessibilityNodeInfo): SpotPicker.Field {
        val bounds = Rect().also(field::getBoundsInScreen).toBox()
        if (field != fieldNode || bounds != fieldBounds) {
            fieldNode = field
            fieldBounds = bounds
            around = around(field)
        }
        val cursor = if (SpotPicker.isTall(bounds, bubble.sizes)) cursorLine(field, bounds) else null
        return SpotPicker.Field(bounds, cursor, around.inputs, around.buttons, around.labels)
    }

    /** Reads the visible views in the window of the field, other than the field. */
    private fun around(field: AccessibilityNodeInfo): Around {
        val inputs = ArrayList<SpotPicker.Box>()
        val buttons = ArrayList<SpotPicker.Box>()
        val labels = ArrayList<SpotPicker.Box>()
        val root = field.window?.root ?: return Around(inputs, buttons, labels)
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        val rect = Rect()
        var count = 0
        var asked = 0
        while (queue.isNotEmpty() && count < MAX_NODES) {
            val node = queue.removeFirst()
            count++
            if (!node.isVisibleToUser) continue
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            if (node == field) continue
            node.getBoundsInScreen(rect)
            when {
                node.isEditable -> inputs += rect.toBox()
                node.isClickable || node.isLongClickable || node.isFocusable -> buttons += rect.toBox()
                !node.text.isNullOrBlank() -> labels += if (asked++ < MAX_LABELS) textBounds(node, rect.toBox()) else rect.toBox()
                !node.contentDescription.isNullOrBlank() -> labels += rect.toBox()
            }
        }
        return Around(inputs, buttons, labels)
    }

    /**
     * The part of a text view that has text. A text view can be wider than its text, for example a centered
     * line across the screen. When the app does not tell where the characters are, it is the whole view.
     */
    private fun textBounds(node: AccessibilityNodeInfo, bounds: SpotPicker.Box): SpotPicker.Box {
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        val length = node.text?.length ?: 0
        if (length == 0 || key !in node.availableExtraData) return bounds
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, minOf(length, MAX_LABEL_CHARS))
        }
        if (!node.refreshWithExtraData(key, args)) return bounds
        val chars = node.extras.getParcelableArray(key, RectF::class.java)?.filterNotNull()?.filter { !it.isEmpty }
        if (chars.isNullOrEmpty()) return bounds
        return SpotPicker.Box(
            floor(chars.minOf { it.left }).toInt(), floor(chars.minOf { it.top }).toInt(),
            ceil(chars.maxOf { it.right }).toInt(), ceil(chars.maxOf { it.bottom }).toInt(),
        )
    }

    /** The text line with the cursor, across the field, or null when the app does not tell where the cursor is. */
    private fun cursorLine(field: AccessibilityNodeInfo, bounds: SpotPicker.Box): SpotPicker.Box? {
        val text = if (field.isShowingHintText) "" else field.text ?: ""
        if (text.isEmpty()) {
            val line = (EMPTY_LINE_DP * resources.displayMetrics.density).toInt()
            return bounds.copy(bottom = minOf(bounds.bottom, bounds.top + line))
        }
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        if (key !in field.availableExtraData) return null
        // The character after the cursor, or the last one when the cursor is at the end.
        val index = field.textSelectionStart.let { if (it in 0 until text.length) it else text.length - 1 }
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, index)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, 1)
        }
        if (!field.refreshWithExtraData(key, args)) return null
        val char = field.extras.getParcelableArray(key, RectF::class.java)?.firstOrNull() ?: return null
        return bounds.copy(top = floor(char.top).toInt(), bottom = ceil(char.bottom).toInt())
    }

    private fun Rect.toBox() = SpotPicker.Box(left, top, right, bottom)

    private fun onAction(action: Bubble.Action) {
        dispatch(
            when (action) {
                Bubble.Action.MIC -> Event.MicTap(engine.isReady(language()))
                Bubble.Action.CANCEL -> Event.Cancel
                Bubble.Action.CONFIRM -> Event.Confirm
            },
        )
    }

    private fun dispatch(event: Event) {
        val effects = machine.on(event)
        refresh()
        effects.forEach(::run)
    }

    private fun language() = prefs.language

    private fun run(effect: Effect) {
        when (effect) {
            is Effect.StartRecording -> startRecording(effect.session)
            Effect.DiscardRecording -> {
                Recorder.stop()
                MicService.stop(this)
            }
            is Effect.FinishRecording -> finishRecording(effect.session)
            is Effect.Insert -> insert(effect.text)
            is Effect.Notify -> notify(effect.notice)
        }
    }

    private fun startRecording(session: Int) {
        main.removeCallbacks(unloadTask)
        Recorder.expect(session)
        try {
            MicService.start(this, session)
        } catch (e: RuntimeException) {
            Log.w(TAG, "startForegroundService failed", e)
            Diagnostics.mic(this, "startForegroundService failed: ${e.javaClass.simpleName}")
            dispatch(Event.Failed(session, Notice.MIC_FAILED))
            return
        }
        val language = language()
        worker.execute { runCatching { engine.prepare(language) } }
    }

    private fun finishRecording(session: Int) {
        val samples = Recorder.stop()
        MicService.stop(this)
        val seconds = samples.size / Recorder.SAMPLE_RATE.toFloat()
        Diagnostics.mic(this, "recorded %.1f s, peak %.3f".format(seconds, samples.maxOfOrNull { kotlin.math.abs(it) } ?: 0f))
        when {
            samples.size < Recorder.SAMPLE_RATE / 4 -> dispatch(Event.Failed(session, Notice.NO_SPEECH))
            samples.all { it == 0f } -> dispatch(Event.Failed(session, Notice.MIC_SILENT))
            else -> {
                val language = language()
                worker.execute {
                    val event = try {
                        val start = System.nanoTime()
                        val text = engine.transcribe(language, samples)
                        val ms = (System.nanoTime() - start) / 1_000_000
                        val load = engine.takeLoadMs()?.let { ", model load %d ms".format(it) } ?: ""
                        val note = "%s: %.1f s audio in %d ms%s".format(language.model.id, seconds, ms, load)
                        Log.i(TAG, "engine: $note")
                        Diagnostics.engine(this, note)
                        Event.Transcribed(session, text)
                    } catch (e: Exception) {
                        Log.w(TAG, "transcribe failed", e)
                        Diagnostics.engine(this, "failed: ${e.javaClass.simpleName}: ${e.message}")
                        Event.Failed(session, Notice.ENGINE_FAILED)
                    }
                    main.post {
                        dispatch(event)
                        main.postDelayed(unloadTask, UNLOAD_DELAY_MS)
                    }
                }
            }
        }
    }

    private fun insert(text: String) {
        val method = Inserter.insert(text, FieldTarget(this, focusedField()))
        Diagnostics.insert(this, method.name)
        if (method == InsertMethod.CLIPBOARD) toast(R.string.notice_copied)
    }

    private fun notify(notice: Notice) {
        if (notice == Notice.NO_MODEL) {
            startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        toast(
            when (notice) {
                Notice.NO_MODEL -> R.string.notice_no_model
                Notice.NO_SPEECH -> R.string.notice_no_speech
                Notice.MIC_FAILED -> R.string.notice_mic_failed
                Notice.MIC_SILENT -> R.string.notice_mic_silent
                Notice.ENGINE_FAILED -> R.string.notice_engine_failed
            },
        )
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onLevel(level: Float) {
        if (machine.state is State.Recording) bubble.setRms(level)
    }

    override fun onRecordFailed(session: Int, notice: Notice) = dispatch(Event.Failed(session, notice))

    override fun onLimit(session: Int) {
        if ((machine.state as? State.Recording)?.session == session) dispatch(Event.Confirm)
    }

    private companion object {
        const val TAG = "Hush"
        const val REFRESH_DELAY_MS = 80L
        // Enough for the views of a normal screen. Hush stops there to keep the walk short.
        const val MAX_NODES = 500
        // Hush asks where the characters are for this many labels, and for this many characters of each.
        const val MAX_LABELS = 40
        const val MAX_LABEL_CHARS = 200
        // The height of the cursor line in an empty field.
        const val EMPTY_LINE_DP = 48
        const val UNLOAD_DELAY_MS = 60_000L
    }
}
