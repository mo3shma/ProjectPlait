package app.plait.audio

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Recognizer
import org.vosk.android.StorageService

/** One step of live transcription: either a partial guess or a finished utterance. */
data class TranscriptUpdate(val text: String, val isFinal: Boolean, val conf: Float = 1f)

/**
 * Offline speech-to-text with Vosk. The model ships in the APK (assets/model-en-us) and is
 * unpacked to private storage on first use, so no audio leaves the phone.
 */
class VoskTranscriber(private val context: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private var recognizer: Recognizer? = null

    @Volatile var error: String? = null
        private set

    fun load() {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        StorageService.unpack(
            context, "model-en-us", "model",
            { model ->
                try {
                    recognizer = Recognizer(model, SAMPLE_RATE.toFloat()).also { it.setWords(true) }
                    ready.complete(true)
                } catch (e: Exception) {
                    error = e.message ?: "Speech engine failed to start"
                    ready.complete(false)
                }
            },
            { e ->
                error = e.message ?: "Speech model could not be unpacked"
                ready.complete(false)
            },
        )
    }

    suspend fun awaitReady(): Boolean = ready.await()

    fun accept(pcm: ShortArray): TranscriptUpdate? {
        val r = recognizer ?: return null
        return if (r.acceptWaveForm(pcm, pcm.size)) {
            readResult(r.result)
        } else {
            TranscriptUpdate(JSONObject(r.partialResult).optString("partial"), isFinal = false)
        }
    }

    /** Ends the current phrase now (the speaker stopped) and returns what was said. */
    fun flush(): TranscriptUpdate? = recognizer?.let { readResult(it.finalResult) }

    /** Flushes whatever the recognizer is still holding. */
    fun finish(): String = flush()?.text.orEmpty()

    private fun readResult(json: String): TranscriptUpdate = parseResult(json)

    companion object {
        /**
         * Reads a Vosk result. Words the engine is unsure of are dropped when the rest of the phrase is
         * solid, and the average confidence is kept so the transcript can mark shaky phrases.
         */
        fun parseResult(json: String): TranscriptUpdate {
            val o = JSONObject(json)
            val text = o.optString("text")
            val words = o.optJSONArray("result") ?: return TranscriptUpdate(text, isFinal = true)
            if (words.length() == 0) return TranscriptUpdate(text, isFinal = true)
            val kept = ArrayList<String>()
            var sum = 0.0
            for (i in 0 until words.length()) {
                val w = words.getJSONObject(i)
                val c = w.optDouble("conf", 1.0)
                sum += c
                if (c >= WORD_DROP || words.length() <= 2) kept.add(w.optString("word"))
            }
            val avg = (sum / words.length()).toFloat()
            val useful = kept.size >= (words.length() * 0.6).toInt()
            return TranscriptUpdate(if (useful) kept.joinToString(" ") else text, isFinal = true, conf = avg)
        }

        /** Words below this confidence are dropped from a phrase that is otherwise reliable. */
        const val WORD_DROP = 0.45
    }

    fun close() {
        runCatching { recognizer?.close() }
        recognizer = null
    }
}
