package app.hush

data class ModelFile(val name: String, val url: String, val sha256: String, val bytes: Long)

/** Model families that the engine can load. To add a family, add a value and a branch in [Engine]. */
enum class Family { MOONSHINE, WHISPER }

data class ModelSpec(
    val id: String,
    val family: Family,
    val encoder: ModelFile,
    val decoder: ModelFile,
    val tokens: ModelFile,
    /** Longest audio in seconds for one decode. */
    val maxChunkSeconds: Int,
) {
    val files get() = listOf(encoder, decoder, tokens)
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

    private const val MOONSHINE_BASE_EN_URL =
        "https://huggingface.co/csukuangfj2/sherpa-onnx-moonshine-base-en-quantized-2026-02-27/resolve/8f4d6c58c03d40bcea40043bb7120a878f2bbef6"

    val moonshineBaseEn = ModelSpec(
        id = "moonshine-base-en-quantized-2026-02-27",
        family = Family.MOONSHINE,
        encoder = ModelFile(
            "encoder_model.ort", "$MOONSHINE_BASE_EN_URL/encoder_model.ort",
            "7c66495948d0d08ec1af454cd4b5514862ae6511e94712a60e6d83eaec8dc8cf", 31_326_816,
        ),
        decoder = ModelFile(
            "decoder_model_merged.ort", "$MOONSHINE_BASE_EN_URL/decoder_model_merged.ort",
            "d9d7b333af34bc552580576ddcf248a1c6c839e0d3b43b09afb9376ed009899d", 109_424_400,
        ),
        tokens = ModelFile(
            "tokens.txt", "$MOONSHINE_BASE_EN_URL/tokens.txt",
            "2870d843e14c1e187bf1913a521562a63b53933814bd7f2145120468f494a049", 549_350,
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
        Language("en", "English", "Moonshine Base, fast", moonshineBaseEn),
        Language("ko", "한국어 (Korean)", "Whisper Base, slower, basic accuracy", whisperBase),
    )

    fun language(code: String): Language = languages.firstOrNull { it.code == code } ?: languages.first()
}
