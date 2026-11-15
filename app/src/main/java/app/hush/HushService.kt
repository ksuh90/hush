package app.hush

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    override fun onServiceConnected() {
        prefs = Prefs(this)
        engine = Engine(this)
        worker = Executors.newSingleThreadExecutor()
        bubble = Bubble(this, prefs, ::onAction)
        Recorder.listener = this
        worker.execute { ModelStore.of(this).prune(Catalog.models) }
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
        val visible = busy || (keyboard != null && focusedField() != null)
        bubble.render(visible, machine.state, keyboard)
    }

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

    private fun language() = Catalog.language(prefs.languageCode)

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
        const val UNLOAD_DELAY_MS = 60_000L
    }
}
