package app.hush

import android.content.Context

/** Spike engine: it does not transcribe. It reports the length of the recording. */
class Engine(@Suppress("unused") private val context: Context) {
    fun isReady(language: Language): Boolean = true

    fun prepare(language: Language) = Unit

    fun transcribe(language: Language, samples: FloatArray): String =
        "Hush test %.1f s".format(samples.size / Recorder.SAMPLE_RATE.toFloat())

    fun release() = Unit
}
