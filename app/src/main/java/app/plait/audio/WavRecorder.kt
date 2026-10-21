package app.plait.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

/**
 * Captures the microphone as 16 kHz mono PCM and writes it to a WAV file. Every buffer is also
 * handed to [onChunk] so a speech engine can read the same audio the file contains.
 * [onChunk] runs on the capture thread, and its buffer is reused: copy before keeping it.
 */
class WavRecorder(
    private val file: File,
    /** Called once, on the capture thread, if the microphone or the storage fails. What was recorded so far is kept. */
    private val onFail: () -> Unit = {},
    private val onChunk: (buffer: ShortArray, count: Int) -> Unit,
) {
    @Volatile var paused = false

    private val written = AtomicLong(0)
    @Volatile private var running = false
    private var thread: Thread? = null
    private var effects: List<android.media.audiofx.AudioEffect> = emptyList()

    /** Milliseconds of audio actually written, so paused time is not counted. */
    val elapsedMs: Long get() = written.get() * 1000L / SAMPLE_RATE

    /** Returns false if the microphone could not be opened (permission missing, or in use). */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) return false
        val bufferBytes = maxOf(minBytes * 2, SAMPLE_RATE) // at least half a second of headroom
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes,
            )
        } catch (e: Exception) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        // Phone-side cleanup of the signal helps a small speech model a lot: noise suppression and level control.
        effects = listOfNotNull(
            runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId) else null }.getOrNull(),
            runCatching { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(record.audioSessionId) else null }.getOrNull(),
        )
        file.parentFile?.mkdirs()
        val out = RandomAccessFile(file, "rw")
        out.setLength(0)
        out.write(Wav.header(0))

        running = true
        record.startRecording()
        thread = Thread({
            val shorts = ShortArray(SAMPLE_RATE / 50) // 20 ms per read: about 50 loudness updates a second
            val bytes = ByteArray(shorts.size * 2)
            try {
                while (running) {
                    val n = record.read(shorts, 0, shorts.size)
                    if (n < 0) { onFail(); break } // the audio service died; looping would spin forever
                    if (n == 0) continue
                    if (paused) continue
                    Wav.toLittleEndian(shorts, n, bytes)
                    out.write(bytes, 0, n * 2)
                    written.addAndGet(n.toLong())
                    onChunk(shorts, n)
                }
            } catch (e: java.io.IOException) {
                onFail() // for example a full disk
            } finally {
                runCatching { record.stop() }
                effects.forEach { runCatching { it.release() } }
                effects = emptyList()
                record.release()
                runCatching {
                    Wav.finalize(out, written.get() * 2)
                    out.close()
                }
            }
        }, "plait-recorder").also { it.start() }
        return true
    }

    /** Stops capture and waits until the WAV header has been finalized. */
    fun stop() {
        running = false
        thread?.join(3000)
        thread = null
    }
}
