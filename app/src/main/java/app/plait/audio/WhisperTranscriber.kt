package app.plait.audio

import android.content.Context
import app.plait.ai.ModelCenter
import app.plait.ai.ModelSpec
import app.plait.data.NoteLine
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Transcribes a finished recording with Whisper, running on the phone through sherpa-onnx. It is much more
 * accurate than the built-in live recogniser, handles many languages (Arabic included) and punctuates, but it is
 * slower, so it works on the saved file after the recording ends.
 */
object WhisperTranscriber {
    private val lock = Mutex()
    private var recognizer: OfflineRecognizer? = null
    private var key: String? = null

    /** Phrases Whisper tends to invent over near-silence. Dropped when the stretch it came from is short. */
    private val inventions = setOf("thank you.", "thanks for watching.", "you", "bye.", "thank you for watching.", ".", "شكرا لك.", "شكراً")

    fun isUsable(context: Context, spec: ModelSpec?): Boolean = spec != null && ModelCenter.isInstalled(context, spec.id)

    private fun build(context: Context, spec: ModelSpec, lang: String): OfflineRecognizer {
        val size = spec.id.removePrefix("speech-whisper-")
        fun path(name: String) = ModelCenter.file(context, spec.id, name).path
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = path("$size-encoder.int8.onnx"), decoder = path("$size-decoder.int8.onnx"),
                    language = if (lang == "auto") "" else lang, task = "transcribe", tailPaddings = 1000,
                ),
                tokens = path("$size-tokens.txt"), numThreads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 4), debug = false, provider = "cpu", modelType = "whisper",
            ),
            decodingMethod = "greedy_search",
        )
        return OfflineRecognizer(config = config)
    }

    /** Null when the model could not run. An empty list means no speech was found. */
    suspend fun transcribe(
        context: Context, wav: File, spec: ModelSpec, lang: String, onProgress: (Float) -> Unit = {},
    ): List<NoteLine>? = lock.withLock {
        withContext(Dispatchers.Default) {
            runCatching {
                val k = spec.id + "|" + lang
                if (key != k) { recognizer?.release(); recognizer = build(context.applicationContext, spec, lang); key = k }
                val rec = recognizer!!
                val segments = WavSegments.find(wav)
                val lines = ArrayList<NoteLine>()
                segments.forEachIndexed { i, seg ->
                    val samples = WavSegments.read(wav, seg)
                    if (samples.isNotEmpty()) {
                        val stream = rec.createStream()
                        try {
                            stream.acceptWaveform(samples, SAMPLE_RATE)
                            rec.decode(stream)
                            val text = rec.getResult(stream).text.trim()
                            val invented = seg.lengthMs < 2500 && text.lowercase() in inventions
                            if (text.isNotEmpty() && !invented) lines.add(NoteLine(seg.startMs, text))
                        } finally {
                            stream.release()
                        }
                    }
                    onProgress((i + 1f) / segments.size)
                }
                lines.toList()
            }.getOrNull()
        }
    }

    fun release() { recognizer?.release(); recognizer = null; key = null }
}
