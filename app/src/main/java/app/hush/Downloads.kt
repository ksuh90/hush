package app.hush

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CancellationException

/** Runs one model download at a time. It continues when the setup screen closes. */
object Downloads {
    data class Status(val specId: String, val done: Long, val total: Long, val running: Boolean, val error: String? = null)

    @Volatile var status: Status? = null
        private set
    var listener: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var cancelled = false
    private var lastPost = 0L

    fun start(context: Context, spec: ModelSpec) {
        if (status?.running == true) return
        val store = ModelStore.of(context.applicationContext)
        val parts = store.parts(spec).filter { (model, file) -> file.length() != model.bytes }
        val total = parts.sumOf { it.first.bytes }
        cancelled = false
        update(Status(spec.id, 0, total, running = true), force = true)
        Thread({
            var before = 0L
            try {
                for ((model, file) in parts) {
                    Downloader().fetch(model, file, { done -> update(Status(spec.id, before + done, total, true)) }) { cancelled }
                    before += model.bytes
                }
                // The new model is ready, so the other model of the same language can go.
                store.prune(context.applicationContext)
                update(Status(spec.id, total, total, running = false), force = true)
            } catch (e: CancellationException) {
                update(null, force = true)
            } catch (e: Exception) {
                Log.w("Hush", "download failed", e)
                update(Status(spec.id, before, total, running = false, error = e.message ?: e.javaClass.simpleName), force = true)
            }
        }, "hush-download").start()
    }

    fun cancel() {
        cancelled = true
    }

    private fun update(value: Status?, force: Boolean = false) {
        status = value
        val now = System.currentTimeMillis()
        if (!force && now - lastPost < 250) return
        lastPost = now
        main.post { listener?.invoke() }
    }
}
