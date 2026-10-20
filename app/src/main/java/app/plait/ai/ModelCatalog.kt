package app.plait.ai

enum class ModelKind { Text, Speech, Search }

/** Which on-phone runtime loads a text model: MediaPipe (.task files) or LiteRT-LM (.litertlm files, 64-bit phones only). */
enum class Runtime { MediaPipe, LiteRtLm }

data class ModelFile(val name: String, val url: String, val bytes: Long) {
    /** The name this file has on the host, which is also what it is called if the user downloaded it themselves. */
    val sourceName: String get() = url.substringAfterLast('/')
}

/** One model the user can download. Sizes are exact, taken from the host, so what the hub says is what is downloaded. */
data class ModelSpec(
    val id: String,
    val kind: ModelKind,
    val title: String,
    val blurb: String,
    val strengths: String,
    val limits: String,
    val license: String,
    val files: List<ModelFile>,
    /** Rough RAM the phone needs to run it comfortably, in GB, or 0 when small. */
    val ramGb: Int = 0,
    val runtime: Runtime = Runtime.MediaPipe,
    /** How good its writing is, compared with the other text models; the best installed one is used by default. */
    val quality: Int = 0,
) {
    /** LiteRT-LM ships only for 64-bit phones. */
    val supported: Boolean get() = runtime == Runtime.MediaPipe || "arm64-v8a" in android.os.Build.SUPPORTED_ABIS.orEmpty()

    val bytes: Long get() = files.sumOf { it.bytes }
    val sizeLabel: String get() = ModelCatalog.formatSize(bytes)
}

object ModelCatalog {
    private const val HF = "https://huggingface.co"

    private fun whisper(size: String, blurb: String, strengths: String, limits: String, enc: Long, dec: Long, ram: Int): ModelSpec {
        val base = "$HF/csukuangfj/sherpa-onnx-whisper-$size/resolve/main"
        return ModelSpec(
            id = "speech-whisper-$size", kind = ModelKind.Speech, title = "Whisper ${size.replaceFirstChar { it.uppercase() }}",
            blurb = blurb, strengths = strengths, limits = limits, license = "MIT (OpenAI Whisper)",
            files = listOf(
                ModelFile("$size-encoder.int8.onnx", "$base/$size-encoder.int8.onnx", enc),
                ModelFile("$size-decoder.int8.onnx", "$base/$size-decoder.int8.onnx", dec),
                ModelFile("$size-tokens.txt", "$base/$size-tokens.txt", 816_730),
            ),
            ramGb = ram,
        )
    }

    val textModels = listOf(
        ModelSpec(
            id = "text-qwen3-0.6b", kind = ModelKind.Text, title = "Qwen3 · 0.6B",
            blurb = "The new small writing model. Smaller and smarter than Qwen 2.5 0.5B, good at following Plait's rules.",
            strengths = "Quick and light. Runs well on phones with 4 GB of RAM.",
            limits = "Short, plain answers. It can still miss nuance.",
            license = "Apache-2.0",
            files = listOf(
                ModelFile("model.litertlm", "$HF/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm", 347_251_840),
            ),
            ramGb = 4, runtime = Runtime.LiteRtLm, quality = 2,
        ),
        ModelSpec(
            id = "text-qwen3-1.7b", kind = ModelKind.Text, title = "Qwen3 · 1.7B",
            blurb = "A clear step up for summaries, prep and answers, at under 1 GB.",
            strengths = "Better writing than the small models, and better with Arabic.",
            limits = "Slower and warmer than the small ones; wants 6 GB of RAM.",
            license = "Apache-2.0",
            files = listOf(
                ModelFile("model.litertlm", "$HF/litert-community/Qwen3-1.7B/resolve/main/Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm", 977_184_032),
            ),
            ramGb = 6, runtime = Runtime.LiteRtLm, quality = 4,
        ),
        ModelSpec(
            id = "text-gemma4-e2b", kind = ModelKind.Text, title = "Gemma 4 · E2B",
            blurb = "Google's best small model. The most natural summaries and answers here, Arabic included.",
            strengths = "Clearly the best writing of these models.",
            limits = "A big download, and it wants 8 GB of RAM. Slow on older phones.",
            license = "Apache-2.0",
            files = listOf(
                ModelFile("model.litertlm", "$HF/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm", 2_588_147_712),
            ),
            ramGb = 8, runtime = Runtime.LiteRtLm, quality = 5,
        ),
        ModelSpec(
            id = "text-qwen25-0.5b", kind = ModelKind.Text, title = "Qwen 2.5 · 0.5B",
            blurb = "A small writing model. Quick, light on battery, fine for short summaries.",
            strengths = "Fast on most phones with 4 GB of RAM.",
            limits = "Plain summaries only. It misses nuance and can make things up.",
            license = "Apache-2.0",
            files = listOf(
                ModelFile(
                    "model.task",
                    "$HF/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                    546_660_344,
                ),
            ),
            ramGb = 4, quality = 1,
        ),
        ModelSpec(
            id = "text-qwen25-1.5b", kind = ModelKind.Text, title = "Qwen 2.5 · 1.5B",
            blurb = "A better writing model. Clearer summaries and follow-ups, and better with Arabic.",
            strengths = "Noticeably better answers than the small one.",
            limits = "Slower, warms the phone, wants 6 GB of RAM. Still smaller than cloud AI.",
            license = "Apache-2.0",
            files = listOf(
                ModelFile(
                    "model.task",
                    "$HF/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                    1_597_913_616,
                ),
            ),
            ramGb = 6, quality = 3,
        ),
    )

    val speechModels = listOf(
        whisper("tiny", "Fast, light, a clear step up from the built-in speech model.", "Quick on any phone. Handles many languages including Arabic.", "Mishears names and accents.", 12_937_772, 89_855_401, 3),
        whisper("base", "A good balance of speed and accuracy.", "Better with accents and Arabic than Tiny.", "Takes a while on long notes on older phones.", 29_120_534, 130_672_026, 4),
        whisper("small", "The most accurate option here.", "Best with names, accents and Arabic.", "Slowest; needs 6 GB of RAM. Long notes can take minutes.", 112_442_483, 262_226_114, 6),
    )

    val searchModels = listOf(
        ModelSpec(
            id = "search-use", kind = ModelKind.Search, title = "Meaning search",
            blurb = "Lets search find notes by meaning, not only by the exact words.",
            strengths = "Tiny and fast.",
            limits = "English only. Arabic and other languages still use word search.",
            license = "Apache-2.0 (Google Universal Sentence Encoder)",
            files = listOf(
                ModelFile(
                    "embedder.tflite",
                    "https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/float32/latest/universal_sentence_encoder.tflite",
                    6_120_274,
                ),
            ),
        ),
    )

    val all: List<ModelSpec> = textModels + speechModels + searchModels

    fun byId(id: String?): ModelSpec? = all.firstOrNull { it.id == id }

    /** "547 MB", "1.6 GB". Decimal units, as phone storage settings show them. */
    fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> "%.1f GB".format(java.util.Locale.ENGLISH, bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> "${(bytes + 500_000) / 1_000_000} MB"
        else -> "${(bytes + 500) / 1000} KB"
    }
}
