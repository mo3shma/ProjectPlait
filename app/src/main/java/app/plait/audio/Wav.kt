package app.plait.audio

import java.io.RandomAccessFile

const val SAMPLE_RATE = 16_000

object Wav {
    /** 44-byte RIFF header for mono 16-bit PCM. */
    fun header(dataBytes: Long, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val byteRate = sampleRate * 2
        val b = ByteArray(44)
        fun put(off: Int, s: String) = s.forEachIndexed { i, c -> b[off + i] = c.code.toByte() }
        fun le32(off: Int, v: Long) { for (i in 0..3) b[off + i] = ((v shr (8 * i)) and 0xFF).toByte() }
        fun le16(off: Int, v: Int) { b[off] = (v and 0xFF).toByte(); b[off + 1] = ((v shr 8) and 0xFF).toByte() }
        put(0, "RIFF"); le32(4, 36 + dataBytes); put(8, "WAVE")
        put(12, "fmt "); le32(16, 16); le16(20, 1); le16(22, 1)
        le32(24, sampleRate.toLong()); le32(28, byteRate.toLong()); le16(32, 2); le16(34, 16)
        put(36, "data"); le32(40, dataBytes)
        return b
    }

    fun toLittleEndian(samples: ShortArray, count: Int, out: ByteArray) {
        for (i in 0 until count) {
            out[2 * i] = (samples[i].toInt() and 0xFF).toByte()
            out[2 * i + 1] = ((samples[i].toInt() shr 8) and 0xFF).toByte()
        }
    }

    /** Fills in the real data length once recording has stopped. */
    fun finalize(file: RandomAccessFile, dataBytes: Long) {
        file.seek(0)
        file.write(header(dataBytes))
    }
}

/** Root-mean-square loudness of a PCM buffer, scaled to 0..1 with a gentle curve for display. */
fun levelOf(samples: ShortArray, count: Int): Float {
    if (count <= 0) return 0f
    var sum = 0.0
    for (i in 0 until count) {
        val v = samples[i] / 32768.0
        sum += v * v
    }
    val rms = Math.sqrt(sum / count).toFloat()
    return Math.min(1f, Math.pow(rms.toDouble(), 0.55).toFloat() * 2.2f)
}
