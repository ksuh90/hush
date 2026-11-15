package app.hush

data class ModelFile(val name: String, val url: String, val sha256: String, val bytes: Long)

/** Model families that the engine can load. To add a family, add a value and a branch in [Engine]. */
enum class Family { TRANSDUCER, WHISPER }

data class ModelSpec(
    val id: String,
    val family: Family,
    val encoder: ModelFile,
    val decoder: ModelFile,
    val tokens: ModelFile,
    val joiner: ModelFile? = null,
    /** Longest audio in seconds for one decode. */
    val maxChunkSeconds: Int,
) {
    val files get() = listOfNotNull(encoder, decoder, joiner, tokens)
    val bytes get() = files.sumOf { it.bytes }
}

/** A dictation language. [modelLanguage] is the code that a multilingual model gets. */
data class Language(
    val code: String,
    val label: String,
    val note: String,
    val model: ModelSpec,
    val modelLanguage: String = code,
)

object Catalog {
    val vad = ModelFile(
        "silero_vad.onnx",
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
        643_854,
    )

    private const val PARAKEET_V2_URL =
        "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/84b478d541cdbf0c296a7ab5f1e20a6f8f998114"

    val parakeetV2 = ModelSpec(
        id = "parakeet-tdt-0.6b-v2-int8",
        family = Family.TRANSDUCER,
        encoder = ModelFile(
            "encoder.int8.onnx", "$PARAKEET_V2_URL/encoder.int8.onnx",
            "92a10bd9c23936ea0566c29f963bc57c2a5df4261b6a8921c39abea66557ca8b", 652_282_309,
        ),
        decoder = ModelFile(
            "decoder.int8.onnx", "$PARAKEET_V2_URL/decoder.int8.onnx",
            "4d097bae3bac5c96018d5293ba39f1b657296102b142affda40378f38b020f41", 7_257_753,
        ),
        joiner = ModelFile(
            "joiner.int8.onnx", "$PARAKEET_V2_URL/joiner.int8.onnx",
            "77a29f3e5ac7d37074e0af465391b80f314015d5b97f0f90c6c7767f1d56f428", 1_739_080,
        ),
        tokens = ModelFile(
            "tokens.txt", "$PARAKEET_V2_URL/tokens.txt",
            "ec182b70dd42113aff6c5372c75cac58c952443eb22322f57bbd7f53977d497d", 9_384,
        ),
        maxChunkSeconds = 20,
    )

    private const val WHISPER_BASE_URL =
        "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/bb53ee204431c90d314c1cc08d28d23e5b7927cc"

    val whisperBase = ModelSpec(
        id = "whisper-base-int8",
        family = Family.WHISPER,
        encoder = ModelFile(
            "base-encoder.int8.onnx", "$WHISPER_BASE_URL/base-encoder.int8.onnx",
            "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11", 29_120_534,
        ),
        decoder = ModelFile(
            "base-decoder.int8.onnx", "$WHISPER_BASE_URL/base-decoder.int8.onnx",
            "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d", 130_672_026,
        ),
        tokens = ModelFile(
            "base-tokens.txt", "$WHISPER_BASE_URL/base-tokens.txt",
            "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126", 816_730,
        ),
        // Whisper always reads a 30 s window, so long chunks cost no more time.
        maxChunkSeconds = 28,
    )

    val languages = listOf(
        Language("en", "English", "Parakeet v2, high accuracy", parakeetV2),
        Language("ko", "한국어 (Korean)", "Whisper Base, slower, basic accuracy", whisperBase),
    )

    val models get() = languages.map { it.model }.distinct()

    fun language(code: String): Language = languages.firstOrNull { it.code == code } ?: languages.first()
}
