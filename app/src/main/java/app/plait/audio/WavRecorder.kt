package app.plait.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
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
    /** Called on the capture thread when Android starts (true) or stops (false) sending silence instead of the microphone. */
    private val onBlocked: (Boolean) -> Unit = {},
    private val onChunk: (buffer: ShortArray, count: Int) -> Unit,
) {
    @Volatile var paused = false

    private val written = AtomicLong(0)
    @Volatile private var running = false
    private var thread: Thread? = null
    private var effects: List<android.media.audiofx.AudioEffect> = emptyList()
    private var bufferBytes = 0

    /** Milliseconds of audio actually written, so paused time is not counted. */
    val elapsedMs: Long get() = written.get() * 1000L / SAMPLE_RATE

    /** Opens the microphone on [source] with its cleanup effects, or null if it cannot be opened. */
    @SuppressLint("MissingPermission")
    private fun open(source: Int): AudioRecord? {
        val record = try {
            AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes)
        } catch (e: Exception) {
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        // Phone-side cleanup of the signal helps a small speech model a lot: noise suppression and level control.
        effects = listOfNotNull(
            runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId) else null }.getOrNull(),
            runCatching { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(record.audioSessionId) else null }.getOrNull(),
        )
        return record
    }

    private fun close(record: AudioRecord) {
        runCatching { record.stop() }
        effects.forEach { runCatching { it.release() } }
        effects = emptyList()
        record.release()
    }

    /** Returns false if the microphone could not be opened (permission missing, or in use). */
    fun start(): Boolean {
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) return false
        bufferBytes = maxOf(minBytes * 2, SAMPLE_RATE) // at least half a second of headroom
        val first = open(MIC_SOURCES[0]) ?: return false
        file.parentFile?.mkdirs()
        val out = RandomAccessFile(file, "rw")
        out.setLength(0)
        out.write(Wav.header(0))

        running = true
        first.startRecording()
        thread = Thread({
            val shorts = ShortArray(SAMPLE_RATE / 50) // 20 ms per read: about 50 loudness updates a second
            val bytes = ByteArray(shorts.size * 2)
            val watch = MicWatch(MIC_SOURCES.size)
            var record: AudioRecord? = first
            try {
                while (running) {
                    val rec = record ?: break
                    val n = rec.read(shorts, 0, shorts.size)
                    if (n < 0) { onFail(); break } // the audio service died; looping would spin forever
                    if (n == 0) continue
                    if (paused) continue
                    Wav.toLittleEndian(shorts, n, bytes)
                    out.write(bytes, 0, n * 2)
                    written.addAndGet(n.toLong())
                    onChunk(shorts, n)
                    when (watch.feed(shorts, n)) {
                        MicWatch.Act.NextSource -> {
                            // Android silenced this capture (a call holds the mic); a fresh one on another source sometimes gets it back.
                            close(rec)
                            record = (open(MIC_SOURCES[watch.source]) ?: open(MIC_SOURCES[0]))?.also { it.startRecording() }
                            if (record == null) onFail()
                        }
                        MicWatch.Act.Blocked -> onBlocked(true)
                        MicWatch.Act.Clear -> onBlocked(false)
                        MicWatch.Act.None -> Unit
                    }
                }
            } catch (e: java.io.IOException) {
                onFail() // for example a full disk
            } finally {
                record?.let(::close)
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
