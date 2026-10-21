package app.plait.audio

import android.content.Context
import app.plait.ai.AiPrefs
import app.plait.ai.ModelCatalog
import app.plait.ai.ModelCenter
import kotlinx.coroutines.CompletableDeferred

/**
 * Live words from Whisper, one phrase at a time. The voice detector hands over speech only; it is collected until the
 * speaker pauses, then written in the language the user picked. Words appear about a sentence behind, which is the
 * price of a model that is far better than a streaming one, and works in Arabic and mixed speech too.
 */
class LiveWhisper(private val context: Context) : LiveTranscriber {
    private val ready = CompletableDeferred<Boolean>()
    private var whisper: WhisperTranscriber.Built? = null
    private var mixed: List<String>? = null
    private var previous: String? = null
    private val buffer = PhraseBuffer()
    private var closed = false

    @Volatile var error: String? = null
        private set

    override fun load() {
        Thread {
            try {
                AiPrefs.load(context)
                // The smallest installed model: it has to keep up with speech.
                val spec = ModelCatalog.speechModels.firstOrNull { ModelCenter.isInstalled(context, it.id) }
                if (spec == null) {
                    error = "Whisper is not installed"
                    ready.complete(false)
                } else {
                    val lang = AiPrefs.speechLang
                    mixed = WhisperTranscriber.pair(lang)
                    val built = WhisperTranscriber.build(context, spec, if (mixed != null) "auto" else lang)
                    // Stopped while the model was loading: nobody will close it later, so do it here.
                    val keep = synchronized(this) { if (closed) false else { whisper = built; true } }
                    if (!keep) built.release()
                    ready.complete(keep)
                }
            } catch (e: Throwable) {
                error = e.message ?: "Speech engine failed to start"
                ready.complete(false)
            }
        }.start()
    }

    override suspend fun awaitReady(): Boolean = ready.await()

    @Synchronized override fun accept(pcm: ShortArray): TranscriptUpdate? = buffer.add(pcm)?.let(::write)

    @Synchronized override fun flush(): TranscriptUpdate? = buffer.take()?.let(::write)

    override fun finish(): String = flush()?.text.orEmpty()

    private fun write(pcm: ShortArray): TranscriptUpdate? {
        val b = whisper ?: return null
        val ms = pcm.size * 1000L / SAMPLE_RATE
        val (text, lang) = WhisperTranscriber.piece(b, FloatArray(pcm.size) { pcm[it] / 32768f }, ms, mixed, previous)
        if (lang != null) previous = lang
        return if (WhisperTranscriber.worthKeeping(text, ms)) TranscriptUpdate(text) else null
    }

    @Synchronized override fun close() {
        closed = true
        runCatching { whisper?.release() }
        whisper = null
    }

    /**
     * Speech collected for one piece. A piece shorter than [MIN_MS] is dropped (Whisper makes words up from a click or a
     * cough), and a long stretch is handed over at [MAX_MS] so a speaker who never pauses still sees words.
     */
    class PhraseBuffer(private val minMs: Int = MIN_MS, private val maxMs: Int = MAX_MS) {
        private val chunks = ArrayList<ShortArray>()
        private var samples = 0

        fun add(pcm: ShortArray): ShortArray? {
            chunks.add(pcm); samples += pcm.size
            return if (samples * 1000L / SAMPLE_RATE >= maxMs) drain() else null
        }

        /** Everything held, or null when it is too short to be worth reading. */
        fun take(): ShortArray? = drain().takeIf { it.size * 1000L / SAMPLE_RATE >= minMs }

        private fun drain(): ShortArray {
            val out = ShortArray(samples)
            var at = 0
            for (c in chunks) { c.copyInto(out, at); at += c.size }
            chunks.clear(); samples = 0
            return out
        }
    }

    companion object {
        const val MIN_MS = 600
        const val MAX_MS = 12_000
    }
}
