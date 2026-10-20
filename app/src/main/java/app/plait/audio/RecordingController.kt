package app.plait.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.plait.data.NoteLine
import app.plait.data.SpokenText
import app.plait.data.NoteStore
import app.plait.data.SavedNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.UUID

enum class RecPhase { Idle, Recording, Paused, Saving, Failed }

/**
 * Drives one voice-note session: microphone → WAV file, same audio → offline transcriber,
 * with everything the UI needs exposed as Compose state.
 */
class RecordingController(
    context: Context,
    private val store: NoteStore,
    val eventId: String,
    val eventTitle: String,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var phase by mutableStateOf(RecPhase.Idle); private set
    var elapsedMs by mutableLongStateOf(0L); private set
    var levels by mutableStateOf(List(LEVEL_BARS) { 0f }); private set
    var partial by mutableStateOf(""); private set
    /** True while the voice detector hears someone talking. */
    var speaking by mutableStateOf(false); private set
    var failure by mutableStateOf<String?>(null); private set
    /** Null while the speech model is loading, true once it is live, false if it failed. */
    var speechReady by mutableStateOf<Boolean?>(null); private set
    val lines = mutableStateListOf<NoteLine>()
    val bookmarksMs = mutableStateListOf<Long>()

    private val id = UUID.randomUUID().toString()
    private val file = store.newAudioFile(id)
    private val transcriber = VoskTranscriber(appContext)
    private val pcm = Channel<ShortArray>(Channel.UNLIMITED)
    private var recorder: WavRecorder? = null
    private var consumer: Job? = null
    private var lastUiPost = 0L

    fun start(): Boolean {
        if (phase != RecPhase.Idle) return phase != RecPhase.Failed
        transcriber.load()
        consumer = scope.launch { consume() }

        val rec = WavRecorder(file) { buf, n ->
            pcm.trySend(buf.copyOf(n))
            val now = System.currentTimeMillis()
            if (now - lastUiPost >= 60) {
                lastUiPost = now
                val level = levelOf(buf, n)
                main.post {
                    levels = levels.drop(1) + level
                    recorder?.let { elapsedMs = it.elapsedMs }
                }
            }
        }
        recorder = rec
        if (!rec.start()) {
            failure = "The microphone is busy or unavailable."
            phase = RecPhase.Failed
            return false
        }
        phase = RecPhase.Recording
        return true
    }

    fun pause() {
        if (phase != RecPhase.Recording) return
        recorder?.paused = true
        phase = RecPhase.Paused
        levels = List(LEVEL_BARS) { 0f }
    }

    fun resume() {
        if (phase != RecPhase.Paused) return
        recorder?.paused = false
        phase = RecPhase.Recording
    }

    fun bookmark() {
        if (phase == RecPhase.Recording || phase == RecPhase.Paused) {
            bookmarksMs.add(recorder?.elapsedMs ?: elapsedMs)
        }
    }

    /**
     * Stops, waits for the transcriber to drain, saves the note and reports its id on the main
     * thread. Recordings shorter than two seconds are discarded and report null.
     */
    fun stop(onDone: (noteId: String?) -> Unit) {
        if (phase == RecPhase.Saving) return
        val wasLive = phase == RecPhase.Recording || phase == RecPhase.Paused
        phase = RecPhase.Saving
        scope.launch {
            val duration = recorder?.also { it.stop() }?.elapsedMs ?: 0L
            pcm.close()
            val keep = wasLive && duration >= 2000
            if (keep) {
                consumer?.join()
                val tail = transcriber.finish().trim()
                val cleaned = SpokenText.tidy(tail)
                if (cleaned.isNotEmpty()) main.post { lines.add(NoteLine(duration, cleaned)) }
            } else {
                // Too short to keep: don't wait for the speech model to finish loading.
                consumer?.cancel()
            }
            transcriber.close()

            if (keep) {
                main.post {
                    val note = SavedNote(
                        id = id, eventId = eventId, title = eventTitle,
                        createdAt = System.currentTimeMillis(), durationMs = duration,
                        audioFile = file.name, lines = lines.toList(), bookmarksMs = bookmarksMs.toList(),
                    )
                    store.add(note)
                    NoteProcessor.afterSave(appContext, store, note)
                    onDone(id)
                }
            } else {
                file.delete()
                main.post { onDone(null) }
            }
        }
    }

    /** Called when the screen is torn down without stop(); never leaves the microphone open. */
    fun release() {
        recorder?.stop()
        recorder = null
        pcm.close()
        scope.cancel()
        transcriber.close()
    }

    private suspend fun consume() {
        val ok = transcriber.awaitReady()
        main.post { speechReady = ok }
        // Only speech reaches the recogniser: silence and room noise are dropped before they can become words.
        val vad = Vad(SAMPLE_RATE)
        var startMs = -1L

        fun addLine(u: TranscriptUpdate?, at: Long) {
            val text = SpokenText.tidy(u?.text.orEmpty())
            main.post {
                partial = ""
                if (text.isNotEmpty()) lines.add(NoteLine(at, text, u?.conf ?: 1f))
            }
        }

        for (chunk in pcm) {
            val out = vad.feed(chunk)
            if (!ok) continue
            if (out.started) {
                startMs = vad.speechStartMs
                main.post { speaking = true }
            }
            for (seg in out.audio) {
                val u = transcriber.accept(seg) ?: continue
                if (u.isFinal) {
                    addLine(u, if (startMs >= 0) startMs else vad.elapsedMs)
                    startMs = vad.elapsedMs // a long stretch of speech continues as the next phrase
                } else {
                    val text = u.text
                    main.post { partial = text }
                }
            }
            if (out.ended) {
                addLine(transcriber.flush(), if (startMs >= 0) startMs else vad.elapsedMs)
                startMs = -1
                main.post { speaking = false }
            }
        }
        if (ok) {
            val rest = vad.remainder()
            if (rest.isNotEmpty()) transcriber.accept(rest)
        }
        main.post { speaking = false }
    }

    companion object {
        const val LEVEL_BARS = 30
    }
}
