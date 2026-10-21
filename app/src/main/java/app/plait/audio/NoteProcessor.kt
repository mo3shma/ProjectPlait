package app.plait.audio

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import app.plait.ai.Ai
import app.plait.ai.AiPrefs
import app.plait.ai.ModelCatalog
import app.plait.ai.ModelCenter
import app.plait.ai.ModelSpec
import app.plait.ai.NoteAiPrompt
import app.plait.ai.NoteSummary
import app.plait.data.Calendars
import app.plait.data.NoteDigest
import app.plait.data.NoteStore
import app.plait.data.SavedNote
import app.plait.data.memo.Memo
import app.plait.data.memo.MemoAi
import app.plait.data.memo.MemoStore
import app.plait.data.tasks.TaskSuggester
import app.plait.data.device.DeviceWrite
import app.plait.data.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Larger first: the best Whisper on the phone writes the transcript when the user has not chosen one. */
private val speechOrder = listOf("turbo", "small", "base", "tiny").map { "speech-whisper-$it" }

/** The chosen speech model if it is installed, else the best installed Whisper, else null (the note stays audio-only). */
internal fun pickSpeechId(chosen: String?, installed: (String) -> Boolean): String? =
    chosen?.takeIf(installed) ?: speechOrder.firstOrNull(installed)

/**
 * Work that follows a saved voice note, in the background so the screen is never held up: Whisper writes the
 * transcript from the saved audio, the chosen text engine writes the summary, then tasks are suggested and, if the
 * user turned it on in the hub, the summary and follow-ups are written into the calendar event.
 */
object NoteProcessor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** What is being done to each note right now ("Transcribing…"), so its card can say so. */
    val working = mutableStateMapOf<String, String>()

    private fun speechFor(app: Context): ModelSpec? =
        ModelCatalog.byId(pickSpeechId(AiPrefs.speechModelId) { ModelCenter.isInstalled(app, it) })

    fun afterSave(context: Context, store: NoteStore, note: SavedNote) {
        val app = context.applicationContext
        scope.launch {
            AiPrefs.load(app)
            // No speech model: the note stays audio-only and its card offers to get one.
            val speech = speechFor(app) ?: return@launch
            var current = note
            try {
                current = improveTranscript(app, store, current, speech) ?: current
                current = summarise(app, store, current) ?: current
                working[note.id] = "Looking for tasks…"
                syncToCalendar(app, current)
                SyncEngine.requestAsync(app, force = true)
                TaskSuggester.suggestAsync(app, current.id, current.eventId, current.lines.joinToString(" ") { it.text })
            } finally {
                working.remove(note.id)
            }
        }
    }

    /** After a text note is saved or edited, or a photo note's text is ready. Not for a note still being typed. */
    fun afterMemoSaved(context: Context, memo: Memo) {
        if (!MemoAi.worthAnalysing(memo) || !MemoAi.Guard.allow(memo.id)) return
        val app = context.applicationContext
        scope.launch { analyseMemo(app, memo.id, manual = false) }
    }

    /** The button on a note card. False when the summary is already up to date for the text and engine. */
    suspend fun summariseMemoNow(context: Context, memo: Memo): Boolean = analyseMemo(context.applicationContext, memo.id, manual = true)

    private suspend fun analyseMemo(app: Context, id: String, manual: Boolean): Boolean {
        val store = MemoStore.get(app)
        val memo = store.items.firstOrNull { it.id == id } ?: return false
        if (!MemoAi.worthAnalysing(memo) || id in working) return false
        working[id] = "Reading the note…"
        try {
            AiPrefs.load(app)
            val status = Ai.status(app)
            val engine = MemoAi.engineId(status)
            if (!MemoAi.needsSummary(memo, if (manual) engine else null)) return false
            val text = memo.text.trim()
            val hash = MemoAi.hash(text)
            working[id] = "Writing the summary…"
            val title = Calendars.find(memo.eventId)?.title.orEmpty()
            val ai = if (status.ready) runCatching { NoteSummary.writeText(app, title, text) }.getOrNull() else null
            val summary = ai ?: MemoAi.rulesSummary(text)
            // Stored on the latest copy, and only if the text is still the one analysed: a newer edit wins.
            val latest = store.items.firstOrNull { it.id == id } ?: return false
            if (summary != null && MemoAi.hash(latest.text) == hash) {
                store.update(latest.copy(ai = summary, aiBy = if (ai != null) status.label else "Plait rules", aiHash = hash, aiEngine = engine))
                SyncEngine.requestAsync(app, force = true)
            }
            working[id] = "Looking for tasks…"
            TaskSuggester.suggestAsync(app, id, memo.eventId, text)
            return true
        } finally {
            working.remove(id)
        }
    }

    /** Re-reads the recording with Whisper. Returns the updated note, or null if nothing changed. */
    suspend fun improveTranscript(context: Context, store: NoteStore, note: SavedNote, speech: ModelSpec): SavedNote? {
        working[note.id] = "Transcribing…"
        val wav = store.audioFile(note)
        if (!wav.exists()) return null
        val lines = WhisperTranscriber.transcribe(context, wav, speech, AiPrefs.speechLang) { p -> working[note.id] = "Transcribing… ${(p * 100).toInt()}%" }
        if (lines.isNullOrEmpty()) return null
        val updated = note.copy(lines = lines, ai = null, aiBy = null)
        store.update(updated)
        markWhisper(context, note.id)
        return updated
    }

    /** Asks the chosen AI for a summary. Returns the updated note, or null when there is no AI or it gave nothing. */
    suspend fun summarise(context: Context, store: NoteStore, note: SavedNote): SavedNote? {
        if (note.lines.size < 2) return null
        val status = Ai.status(context)
        if (!status.ready) return null
        working[note.id] = "Writing the summary…"
        // The whole transcript is read (in parts when it is long), and every line of the summary is checked against it.
        val text = app.plait.ai.NoteSummary.write(context, note) ?: return null
        val updated = note.copy(ai = text, aiBy = status.label)
        store.update(updated)
        return updated
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("plait", Context.MODE_PRIVATE)

    /** True once Whisper has written this note's transcript . */
    fun hasWhisper(context: Context, id: String): Boolean = id in prefs(context).getStringSet("whisper_notes", emptySet()).orEmpty()
    private fun markWhisper(context: Context, id: String) {
        val p = prefs(context)
        p.edit().putStringSet("whisper_notes", p.getStringSet("whisper_notes", emptySet()).orEmpty() + id).apply()
    }

    /**
     * After a speech model arrives: every voice note that has no Whisper transcript yet is transcribed with
     * it, newest first, and then summarised and searched for tasks. Runs in the background, one note at a time.
     */
    fun improveAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            AiPrefs.load(app)
            val speech = speechFor(app) ?: return@launch
            val store = NoteStore.get(app)
            for (note in store.notes.toList().sortedByDescending { it.createdAt }) {
                if (hasWhisper(app, note.id) || !store.audioFile(note).exists()) continue
                try {
                    val better = improveTranscript(app, store, note, speech) ?: continue
                    val done = summarise(app, store, better) ?: better
                    TaskSuggester.suggestAsync(app, done.id, done.eventId, done.lines.joinToString(" ") { it.text })
                } finally {
                    working.remove(note.id)
                }
            }
            SyncEngine.requestAsync(app, force = true)
        }
    }

    /** The button on a note card. */
    suspend fun summariseNow(context: Context, store: NoteStore, note: SavedNote): Boolean {
        val updated = try { summarise(context.applicationContext, store, note) } finally { working.remove(note.id) }
        updated ?: return false
        syncToCalendar(context.applicationContext, updated)
        return true
    }

    suspend fun improveNow(context: Context, store: NoteStore, note: SavedNote, speech: ModelSpec): Boolean {
        val updated = try { improveTranscript(context.applicationContext, store, note, speech) } finally { working.remove(note.id) }
        return updated != null
    }

    private fun syncToCalendar(context: Context, note: SavedNote) {
        if (!Calendars.syncNotes) return
        val eventId = DeviceWrite.eventIdOf(note.eventId) ?: return
        val text = NoteDigest.text(note)
        if (text.isNotBlank()) DeviceWrite.writeNotes(context, eventId, text)
    }
}
