package app.plait.audio

import android.content.Context
import app.plait.data.CalEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.plait.data.NoteStore

/**
 * Owns the one recording in progress so it survives the screen being left, rotated or
 * recreated. The Record screen attaches to whatever session is live instead of starting one.
 */
object RecordingHub {
    @Volatile private var current: RecordingController? = null

    val active: RecordingController?
        get() = current?.takeIf { it.phase == RecPhase.Recording || it.phase == RecPhase.Paused || it.phase == RecPhase.Saving }

    /** Returns the live session if there is one, otherwise a fresh controller for [event]. */
    @Synchronized
    fun open(context: Context, event: CalEvent): RecordingController {
        active?.let { return it }
        current?.release()
        return RecordingController(context, NoteStore.get(context), event.id, event.title).also { current = it }
    }

    /** Set when the notification's Stop saved the note, so an open Record screen can leave: the controller and the note id (null if too short). */
    var externalStopped by mutableStateOf<Pair<RecordingController, String?>?>(null)

    /** Stop from the notification: saves exactly like the Stop button, whether or not the Record screen is showing. */
    fun requestStop(context: Context) {
        val c = active ?: return
        val app = context.applicationContext
        c.stop { id ->
            finish(app, c)
            externalStopped = c to id
        }
    }

    /** Starts capture and the foreground service that keeps it alive in the background. */
    fun begin(context: Context, controller: RecordingController): Boolean {
        val started = controller.start()
        if (started) RecordingService.start(context.applicationContext, controller.eventTitle)
        return started
    }

    /** Called once a session has been saved, discarded or abandoned. */
    @Synchronized
    fun finish(context: Context, controller: RecordingController) {
        RecordingService.stop(context.applicationContext)
        controller.release()
        if (current === controller) current = null
    }
}
