package app.plait.audio

import android.media.MediaRecorder

/** Tried in order: when Android silences the first, the recorder reopens the microphone once with the next. */
val MIC_SOURCES = listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)

/**
 * True when a buffer is digital silence: the exact zeros Android sends an app it has cut off from the microphone
 * (another app in a call holds it, or microphone access is off). A live microphone in a silent room still moves
 * by more than a couple of steps, so a quiet room, or quiet speech, is never mistaken for this.
 */
fun isDigitalSilence(buf: ShortArray, n: Int = buf.size): Boolean {
    for (i in 0 until n.coerceIn(0, buf.size)) if (buf[i] > 2 || buf[i] < -2) return false
    return true
}

/**
 * Watches the live microphone for digital silence. After [afterMs] of it, the first time asks for the next audio
 * source ([Act.NextSource]); once the sources are used up, reports [Act.Blocked]. Any real sound clears it.
 */
class MicWatch(private val sources: Int, private val sampleRate: Int = SAMPLE_RATE, private val afterMs: Long = 2_500) {
    enum class Act { None, NextSource, Blocked, Clear }

    /** Index of the source in use. */
    var source = 0; private set
    var blocked = false; private set
    private var silent = 0L

    fun feed(buf: ShortArray, n: Int = buf.size): Act {
        if (!isDigitalSilence(buf, n)) {
            silent = 0
            if (!blocked) return Act.None
            blocked = false
            return Act.Clear
        }
        silent += n
        if (blocked || silent < sampleRate * afterMs / 1000) return Act.None
        silent = 0
        if (source + 1 < sources) { source++; return Act.NextSource }
        blocked = true
        return Act.Blocked
    }

    companion object {
        /** Shown on the record screen and kept on the note when Android blocked the microphone during a call. */
        val BLOCKED_IN_CALL: String get() = app.plait.i18n.Txt.s(app.plait.R.string.note_mic_blocked_call)
        /** The same, when no call was running: another app or the microphone switch took it. */
        val BLOCKED: String get() = app.plait.i18n.Txt.s(app.plait.R.string.note_mic_blocked)
    }
}
