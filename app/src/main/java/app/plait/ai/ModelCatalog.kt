package app.plait.ai

import androidx.annotation.StringRes
import app.plait.R
import app.plait.i18n.AppLocale
import app.plait.i18n.Bidi
import app.plait.i18n.Txt

enum class ModelKind { Text, Speech, Search }

/** Which on-phone runtime loads a text model: MediaPipe (.task files) or LiteRT-LM (.litertlm files, 64-bit phones only). */
enum class Runtime { MediaPipe, LiteRtLm }

data class ModelFile(val name: String, val url: String, val bytes: Long) {
    /** The name this file has on the host, which is also what it is called if the user downloaded it themselves. */
    val sourceName: String get() = url.substringAfterLast('/')
}

/**
 * One model the user can download. Sizes are exact, taken from the host, so what the hub says is what is downloaded.
 * The words about it are resources, read in the app's language each time.
 */
data class ModelSpec(
    val id: String,
    val kind: ModelKind,
    @StringRes private val titleRes: Int,
    @StringRes private val blurbRes: Int,
    @StringRes private val strengthsRes: Int,
    @StringRes private val limitsRes: Int,
    val license: String,
    val files: List<ModelFile>,
    /** Rough RAM the phone needs to run it comfortably, in GB, or 0 when small. */
    val ramGb: Int = 0,
    val runtime: Runtime = Runtime.MediaPipe,
    /** How good its writing is, compared with the other text models; the best installed one is used by default. */
    val quality: Int = 0,
) {
    val title: String get() = Txt.s(titleRes)
    val blurb: String get() = Txt.s(blurbRes)
    val strengths: String get() = Txt.s(strengthsRes)
    val limits: String get() = Txt.s(limitsRes)

    /** LiteRT-LM ships only for 64-bit phones. */
    val supported: Boolean get() = runtime == Runtime.MediaPipe || "arm64-v8a" in android.os.Build.SUPPORTED_ABIS.orEmpty()

    val bytes: Long get() = files.sumOf { it.bytes }
    val sizeLabel: String get() = ModelCatalog.formatSize(bytes)
}

object ModelCatalog {
    private const val HF = "https://huggingface.co"

    private fun whisper(size: String, title: Int, blurb: Int, strengths: Int, limits: Int, enc: Long, dec: Long, ram: Int): ModelSpec {
        val base = "$HF/csukuangfj/sherpa-onnx-whisper-$size/resolve/main"
        return ModelSpec(
            id = "speech-whisper-$size", kind = ModelKind.Speech, titleRes = title,
            blurbRes = blurb, strengthsRes = strengths, limitsRes = limits, license = "MIT (OpenAI Whisper)",
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
            id = "text-qwen3-0.6b", kind = ModelKind.Text, titleRes = R.string.ai_model_qwen3_06b_title,
            blurbRes = R.string.ai_model_qwen3_06b_blurb, strengthsRes = R.string.ai_model_qwen3_06b_strengths, limitsRes = R.string.ai_model_qwen3_06b_limits,
            license = "Apache-2.0",
            files = listOf(
                ModelFile("model.litertlm", "$HF/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm", 347_251_840),
            ),
            ramGb = 4, runtime = Runtime.LiteRtLm, quality = 2,
        ),
        ModelSpec(
            id = "text-qwen3-1.7b", kind = ModelKind.Text, titleRes = R.string.ai_model_qwen3_17b_title,
            blurbRes = R.string.ai_model_qwen3_17b_blurb, strengthsRes = R.string.ai_model_qwen3_17b_strengths, limitsRes = R.string.ai_model_qwen3_17b_limits,
            license = "Apache-2.0",
            files = listOf(
                ModelFile("model.litertlm", "$HF/litert-community/Qwen3-1.7B/resolve/main/Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm", 977_184_032),
            ),
            ramGb = 6, runtime = Runtime.LiteRtLm, quality = 4,
        ),
        ModelSpec(
            id = "text-gemma4-e2b", kind = ModelKind.Text, titleRes = R.string.ai_model_gemma4_e2b_title,
            blurbRes = R.string.ai_model_gemma4_e2b_blurb, strengthsRes = R.string.ai_model_gemma4_e2b_strengths, limitsRes = R.string.ai_model_gemma4_e2b_limits,
            license = "Apache-2.0",
            files = listOf(
                ModelFile("model.litertlm", "$HF/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm", 2_588_147_712),
            ),
            ramGb = 8, runtime = Runtime.LiteRtLm, quality = 5,
        ),
        ModelSpec(
            id = "text-qwen25-0.5b", kind = ModelKind.Text, titleRes = R.string.ai_model_qwen25_05b_title,
            blurbRes = R.string.ai_model_qwen25_05b_blurb, strengthsRes = R.string.ai_model_qwen25_05b_strengths, limitsRes = R.string.ai_model_qwen25_05b_limits,
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
            id = "text-qwen25-1.5b", kind = ModelKind.Text, titleRes = R.string.ai_model_qwen25_15b_title,
            blurbRes = R.string.ai_model_qwen25_15b_blurb, strengthsRes = R.string.ai_model_qwen25_15b_strengths, limitsRes = R.string.ai_model_qwen25_15b_limits,
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
        whisper(
            "tiny", R.string.ai_model_whisper_tiny_title, R.string.ai_model_whisper_tiny_blurb, R.string.ai_model_whisper_tiny_strengths,
            R.string.ai_model_whisper_tiny_limits, 12_937_772, 89_855_401, 3,
        ),
        whisper(
            "base", R.string.ai_model_whisper_base_title, R.string.ai_model_whisper_base_blurb, R.string.ai_model_whisper_base_strengths,
            R.string.ai_model_whisper_base_limits, 29_120_534, 130_672_026, 4,
        ),
        whisper(
            "small", R.string.ai_model_whisper_small_title, R.string.ai_model_whisper_small_blurb, R.string.ai_model_whisper_small_strengths,
            R.string.ai_model_whisper_small_limits, 112_442_483, 262_226_114, 6,
        ),
        whisper(
            "turbo", R.string.ai_model_whisper_turbo_title, R.string.ai_model_whisper_turbo_blurb, R.string.ai_model_whisper_turbo_strengths,
            R.string.ai_model_whisper_turbo_limits, 674_716_297, 361_080_764, 8,
        ),
    )

    val searchModels = listOf(
        ModelSpec(
            id = "search-use", kind = ModelKind.Search, titleRes = R.string.ai_model_search_title,
            blurbRes = R.string.ai_model_search_blurb, strengthsRes = R.string.ai_model_search_strengths, limitsRes = R.string.ai_model_search_limits,
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

    /**
     * "547 MB", "1.6 GB". Decimal units, as phone storage settings show them. In Arabic the size is isolated so a sentence
     * around it cannot turn it into "GB 1.6".
     */
    fun formatSize(bytes: Long): String = Bidi.isolate(
        when {
            bytes >= 1_000_000_000L -> "%.1f GB".format(AppLocale.locale, bytes / 1_000_000_000.0)
            bytes >= 1_000_000L -> "${(bytes + 500_000) / 1_000_000} MB"
            else -> "${(bytes + 500) / 1000} KB"
        },
    )
}
