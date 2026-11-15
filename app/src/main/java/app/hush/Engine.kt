package app.hush

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/** Offline speech recognition with sherpa-onnx. Call it from one worker thread only. */
class Engine(context: Context) {
    private val store = ModelStore.of(context)
    private var loaded: String? = null
    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null

    fun isReady(language: Language) = store.isReady(language.model)

    private var loadMs: Long? = null

    fun prepare(language: Language) {
        val key = "${language.model.id}/${language.modelLanguage}"
        if (key == loaded) return
        release()
        check(isReady(language)) { "model ${language.model.id} is not downloaded" }
        val start = System.nanoTime()
        recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = modelConfig(language)))
        vad = Vad(config = vadConfig(language.model))
        loaded = key
        loadMs = (System.nanoTime() - start) / 1_000_000
    }

    /** Returns the time of the last model load, one time, or null when no load happened since the last call. */
    fun takeLoadMs(): Long? = loadMs.also { loadMs = null }

    fun transcribe(language: Language, samples: FloatArray): String {
        prepare(language)
        val recognizer = recognizer!!
        val segments = speech(vad!!, samples)
        val chunks = Chunks.pack(segments, language.model.maxChunkSeconds * Recorder.SAMPLE_RATE, Recorder.SAMPLE_RATE / 5)
        return chunks.joinToString(" ") { chunk ->
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(chunk, Recorder.SAMPLE_RATE)
                recognizer.decode(stream)
                recognizer.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        }.trim()
    }

    fun release() {
        recognizer?.release()
        vad?.release()
        recognizer = null
        vad = null
        loaded = null
    }

    private fun speech(vad: Vad, samples: FloatArray): List<FloatArray> {
        val segments = mutableListOf<FloatArray>()
        fun drain() {
            while (!vad.empty()) {
                segments += vad.front().samples
                vad.pop()
            }
        }
        vad.reset()
        var at = 0
        while (at + VAD_WINDOW <= samples.size) {
            vad.acceptWaveform(samples.copyOfRange(at, at + VAD_WINDOW))
            drain()
            at += VAD_WINDOW
        }
        vad.flush()
        drain()
        return segments
    }

    private fun path(spec: ModelSpec, file: ModelFile) = store.file(spec, file).absolutePath

    private fun modelConfig(language: Language): OfflineModelConfig {
        val spec = language.model
        val tokens = path(spec, spec.tokens)
        return when (spec.family) {
            Family.TRANSDUCER -> OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = path(spec, spec.encoder),
                    decoder = path(spec, spec.decoder),
                    joiner = path(spec, checkNotNull(spec.joiner)),
                ),
                tokens = tokens,
                numThreads = THREADS,
                modelType = "nemo_transducer",
            )
            Family.MOONSHINE -> OfflineModelConfig(
                moonshine = OfflineMoonshineModelConfig(
                    encoder = path(spec, spec.encoder),
                    mergedDecoder = path(spec, spec.decoder),
                ),
                tokens = tokens,
                numThreads = THREADS,
            )
            Family.WHISPER -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = path(spec, spec.encoder),
                    decoder = path(spec, spec.decoder),
                    language = language.modelLanguage,
                    task = "transcribe",
                ),
                tokens = tokens,
                numThreads = THREADS,
            )
        }
    }

    private fun vadConfig(spec: ModelSpec) = VadModelConfig(
        sileroVadModelConfig = SileroVadModelConfig(
            model = store.vadFile().absolutePath,
            minSilenceDuration = 0.5f,
            minSpeechDuration = 0.25f,
            windowSize = VAD_WINDOW,
            maxSpeechDuration = minOf(spec.maxChunkSeconds, 20).toFloat(),
        ),
        sampleRate = Recorder.SAMPLE_RATE,
        numThreads = 1,
    )

    private companion object {
        const val THREADS = 4
        const val VAD_WINDOW = 512
    }
}
