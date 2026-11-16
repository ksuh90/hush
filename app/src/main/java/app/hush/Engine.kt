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
    // The characters that the loaded model has a token for. The user's words can use only these.
    private var chars: Set<Char> = emptySet()

    fun isReady(language: Language) = store.isReady(language.model)

    private var loadMs: Long? = null

    fun prepare(language: Language) {
        val key = "${language.model.id}/${language.modelLanguage}"
        if (key == loaded) return
        release()
        check(isReady(language)) { "model ${language.model.id} is not downloaded" }
        val start = System.nanoTime()
        recognizer = OfflineRecognizer(config = recognizerConfig(language))
        chars = if (language.model.takesWords) MyWords.chars(tokens(language.model)) else emptySet()
        vad = Vad(config = vadConfig(language.model))
        loaded = key
        loadMs = (System.nanoTime() - start) / 1_000_000
    }

    /** Returns the time of the last model load, one time, or null when no load happened since the last call. */
    fun takeLoadMs(): Long? = loadMs.also { loadMs = null }

    /** The number of the user's words that the last [transcribe] used. */
    var wordsUsed = 0
        private set

    /**
     * Transcribes [samples]. When the model takes the user's [words], each decode gets them, so a change to the
     * list needs no new model load.
     */
    fun transcribe(language: Language, samples: FloatArray, words: List<String> = emptyList()): String {
        prepare(language)
        val recognizer = recognizer!!
        val hotwords = if (language.model.takesWords) MyWords.hotwords(words, chars) else ""
        wordsUsed = if (hotwords.isEmpty()) 0 else hotwords.split('/').size
        val segments = speech(vad!!, samples)
        val chunks = Chunks.pack(segments, language.model.maxChunkSeconds * Recorder.SAMPLE_RATE, Recorder.SAMPLE_RATE / 5)
        return chunks.joinToString(" ") { chunk ->
            val stream = if (hotwords.isEmpty()) recognizer.createStream() else recognizer.createStream(hotwords)
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
        chars = emptySet()
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

    private fun tokens(spec: ModelSpec) = store.file(spec, spec.tokens).readLines()

    /**
     * A model that takes the user's words decodes with modified beam search, also when the list is empty: on the
     * test sets, greedy search did not give the same text, so one path is simpler and as accurate.
     */
    private fun recognizerConfig(language: Language): OfflineRecognizerConfig {
        val modelConfig = modelConfig(language)
        if (!language.model.takesWords) return OfflineRecognizerConfig(modelConfig = modelConfig)
        return OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "modified_beam_search",
            maxActivePaths = BEAM,
            hotwordsScore = WORDS_SCORE,
        )
    }

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
                // To encode the user's words, sherpa-onnx cuts them into the model's BPE pieces.
                modelingUnit = if (spec.takesWords) "bpe" else "",
                bpeVocab = if (spec.takesWords) store.bpeVocab(spec).absolutePath else "",
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
        const val BEAM = 4
        // A higher score found no more words on the test set, but it put in words that the speaker did not say.
        const val WORDS_SCORE = 1.5f
        const val VAD_WINDOW = 512
    }
}
