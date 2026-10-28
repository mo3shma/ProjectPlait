package app.plait.audio

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.plait.data.NoteStore
import app.plait.data.SavedNote
import app.plait.ui.screens.loudnessOf
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
 * Drives one voice-note session: microphone → WAV file, with the level meter and a "hearing you" flag exposed as
 * Compose state. Words come later: NoteProcessor transcribes the saved file.
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
    /** 0..1 loudness of the latest audio buffer. Written on the capture thread, read by the wave animation each frame. */
    @Volatile var loudness = 0f; private set
    /** True while the voice detector hears someone talking. */
    var speaking by mutableStateOf(false); private set
    var failure by mutableStateOf<String?>(null); private set
    /** Why only silence is arriving, while Android keeps the microphone from Plait (a call holds it); null when audio flows. */
    var blocked by mutableStateOf<String?>(null); private set
    /** The last [blocked] reason seen, kept on the note so an empty transcript says why. */
    private var silentReason: String? = null
    val bookmarksMs = mutableStateListOf<Long>()

    private val id = UUID.randomUUID().toString()
    private val file = store.newAudioFile(id)
    private val pcm = Channel<ShortArray>(Channel.UNLIMITED)
    private var recorder: WavRecorder? = null
    private var consumer: Job? = null
    private var lastUiPost = 0L

    fun start(): Boolean {
        if (phase != RecPhase.Idle) return phase != RecPhase.Failed
        consumer = scope.launch { listen() }

        val rec = WavRecorder(file, onFail = {
            main.post { failure = app.plait.i18n.Txt.s(app.plait.R.string.note_rec_failed_storage) }
        }, onBlocked = { on ->
            val mode = appContext.getSystemService(AudioManager::class.java)?.mode
            val reason = if (mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_IN_CALL) MicWatch.BLOCKED_IN_CALL else MicWatch.BLOCKED
            main.post { blocked = if (on) reason else null; if (on) silentReason = reason }
        }) { buf, n ->
            pcm.trySend(buf.copyOf(n))
            loudness = loudnessOf(buf, n)
            val now = System.currentTimeMillis()
            if (now - lastUiPost >= 60) {
                lastUiPost = now
                main.post { recorder?.let { elapsedMs = it.elapsedMs } }
            }
        }
        recorder = rec
        if (!rec.start()) {
            failure = app.plait.i18n.Txt.s(app.plait.R.string.note_rec_mic_busy)
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
        loudness = 0f
        speaking = false
        RecordingService.refresh(appContext)
    }

    fun resume() {
        if (phase != RecPhase.Paused) return
        recorder?.paused = false
        phase = RecPhase.Recording
        RecordingService.refresh(appContext)
    }

    fun bookmark() {
        if (phase == RecPhase.Recording || phase == RecPhase.Paused) {
            bookmarksMs.add(recorder?.elapsedMs ?: elapsedMs)
        }
    }

    /**
     * Stops, saves the note and reports its id on the main thread. Recordings shorter than two seconds are
     * discarded and report null.
     */
    fun stop(onDone: (noteId: String?) -> Unit) {
        if (phase == RecPhase.Saving) return
        val wasLive = phase == RecPhase.Recording || phase == RecPhase.Paused
        phase = RecPhase.Saving
        scope.launch {
            val duration = recorder?.also { it.stop() }?.elapsedMs ?: 0L
            pcm.close()
            consumer?.cancel()
            if (wasLive && duration >= 2000) {
                main.post {
                    val note = SavedNote(
                        id = id, eventId = eventId, title = eventTitle,
                        createdAt = System.currentTimeMillis(), durationMs = duration,
                        audioFile = file.name, lines = emptyList(), bookmarksMs = bookmarksMs.toList(),
                        silentReason = silentReason,
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
    }

    /** Feeds the voice detector only to drive the "hearing you" flag; nothing is cut from the recording. */
    private suspend fun listen() {
        val vad = Vad(SAMPLE_RATE)
        var heard = false
        for (chunk in pcm) {
            vad.feed(chunk)
            if (vad.speaking != heard) { heard = vad.speaking; main.post { speaking = heard } }
        }
    }
}
