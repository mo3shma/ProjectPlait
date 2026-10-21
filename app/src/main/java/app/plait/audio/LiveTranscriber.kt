package app.plait.audio

/** One step of live transcription: either a partial guess or a finished utterance. */
data class TranscriptUpdate(val text: String, val isFinal: Boolean, val conf: Float = 1f)

/** Words shown while a note is being recorded. The recording itself is never touched. */
interface LiveTranscriber {
    fun load()
    suspend fun awaitReady(): Boolean

    /** Takes speech audio; returns a finished piece of text when one is ready, else null. */
    fun accept(pcm: ShortArray): TranscriptUpdate?

    /** The speaker paused: writes what has been heard since the last piece. */
    fun flush(): TranscriptUpdate?

    /** Flushes whatever is still held when the recording stops. */
    fun finish(): String
    fun close()
}
