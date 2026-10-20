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
    private var config: OfflineRecognizerConfig? = null

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
        this.config = config
        return OfflineRecognizer(config = config)
    }

    /**
     * Whisper sometimes gets stuck repeating itself ("بيلا بيلا بيلا…", "ha-ha-ha-ha…"), mostly over unclear or mixed
     * speech. A word, a group of up to four words, or a hyphenated syllable said more than twice in a row is kept once.
     */
    fun cleanRepeats(text: String): String {
        var t = text.replace(Regex("(\\b[\\p{L}\\p{N}']{1,6}-)(\\1){2,}[\\p{L}\\p{N}']*-?"), "$1").replace(Regex("-+(\\s|$)"), "$1")
        val words = t.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        fun norm(w: String) = w.lowercase().trim('.', ',', '!', '?', '؟', '،', '…')
        for (n in 1..4) {
            var i = 0
            while (i + 2 * n <= words.size) {
                var reps = 1
                while (i + (reps + 1) * n <= words.size && (0 until n).all { norm(words[i + it]) == norm(words[i + reps * n + it]) }) reps++
                if (reps >= 3) repeat((reps - 1) * n) { words.removeAt(i + n) } else i++
            }
        }
        t = words.joinToString(" ")
        return t.trim()
    }

    /** "ar+en" (two languages spoken in one note) as its two codes, the first being the main one; null for one language. */
    fun pair(lang: String): List<String>? = lang.split('+').map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.size == 2 }

    /** "<|ar|>" or "ar" as "ar". */
    internal fun langCode(raw: String): String = raw.trim().removePrefix("<|").removeSuffix("|>").lowercase()

    /**
     * Null when the model could not run. An empty list means no speech was found.
     *
     * For a mixed note ("ar+en"), the recording is cut into short pieces at pauses, about a sentence each, and every
     * piece is written in its own language: Whisper detects it, and a guess outside the two languages (Egyptian Arabic
     * heard as Persian, say) is redone in the main one. Pieces too short to tell take the language of the one before.
     */
    suspend fun transcribe(
        context: Context, wav: File, spec: ModelSpec, lang: String, onProgress: (Float) -> Unit = {},
    ): List<NoteLine>? = lock.withLock {
        withContext(Dispatchers.Default) {
            runCatching {
                val mixed = pair(lang)
                val buildLang = if (mixed != null) "auto" else lang
                val k = spec.id + "|" + buildLang
                if (key != k) { recognizer?.release(); recognizer = build(context.applicationContext, spec, buildLang); key = k }
                val rec = recognizer!!
                val segments = if (mixed != null) WavSegments.find(wav, maxMs = 12_000, joinGapMs = 300) else WavSegments.find(wav)
                val lines = ArrayList<NoteLine>()
                var previous = mixed?.first()
                segments.forEachIndexed { i, seg ->
                    val samples = WavSegments.read(wav, seg)
                    if (samples.isNotEmpty()) {
                        val text = if (mixed == null) decode(rec, samples).first else {
                            val (heard, detected) = if (seg.lengthMs < 1_500) decodeIn(rec, samples, previous!!) to previous!!
                                else decode(rec, samples).let { it.first to langCode(it.second) }
                            if (detected in mixed) { previous = detected; heard }
                            else decodeIn(rec, samples, mixed.first()).also { previous = mixed.first() }
                        }.trim().let(::cleanRepeats)
                        val invented = seg.lengthMs < 2500 && text.lowercase() in inventions
                        if (text.isNotEmpty() && !invented) lines.add(NoteLine(seg.startMs, text))
                    }
                    onProgress((i + 1f) / segments.size)
                }
                lines.toList()
            }.getOrNull()
        }
    }

    /** Text and the language Whisper heard, with the recogniser's current language setting. */
    private fun decode(rec: OfflineRecognizer, samples: FloatArray): Pair<String, String> {
        val stream = rec.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            rec.decode(stream)
            val r = rec.getResult(stream)
            return r.text to r.lang
        } finally {
            stream.release()
        }
    }

    /** Decodes in one given language, then puts the recogniser back to detecting the language itself. */
    private fun decodeIn(rec: OfflineRecognizer, samples: FloatArray, code: String): String {
        val base = config ?: return decode(rec, samples).first
        rec.setConfig(base.copy(modelConfig = base.modelConfig.copy(whisper = base.modelConfig.whisper.copy(language = code))))
        try {
            return decode(rec, samples).first
        } finally {
            rec.setConfig(base)
        }
    }

    fun release() { recognizer?.release(); recognizer = null; key = null; config = null }
}
