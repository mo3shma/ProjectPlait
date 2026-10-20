package app.plait.audio

import java.io.File
import java.io.RandomAccessFile

/** A stretch of a recording that contains speech, in milliseconds. */
data class Segment(val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs
}

/**
 * Finds the talking parts of a recording so a speech model only hears speech, in pieces short enough for it
 * (Whisper takes about 30 seconds at a time). Reads the file in blocks, so a long recording never sits in memory.
 */
object WavSegments {
    const val HEADER_BYTES = 44L

    fun find(file: File, maxMs: Long = 25_000, padMs: Long = 250, joinGapMs: Long = 500): List<Segment> {
        val raw = ArrayList<Segment>()
        val loud = ArrayList<Float>()
        RandomAccessFile(file, "r").use { f ->
            f.seek(HEADER_BYTES)
            val vad = Vad(SAMPLE_RATE)
            val bytes = ByteArray(32_000)
            var start = -1L
            // Loudness of every 100 ms, so a long stretch of talk can be cut at its quietest moment.
            var acc = 0.0; var count = 0
            while (true) {
                val n = f.read(bytes)
                if (n <= 0) break
                val shorts = ShortArray(n / 2) { i -> ((bytes[2 * i + 1].toInt() shl 8) or (bytes[2 * i].toInt() and 0xFF)).toShort() }
                for (v in shorts) {
                    acc += v.toDouble() * v; count++
                    if (count == SAMPLE_RATE / 10) { loud.add((acc / count).toFloat()); acc = 0.0; count = 0 }
                }
                val out = vad.feed(shorts)
                if (out.started) start = vad.speechStartMs
                if (out.ended && start >= 0) { raw.add(Segment(start, vad.elapsedMs)); start = -1 }
            }
            if (start >= 0) raw.add(Segment(start, vad.elapsedMs))
        }
        return chunk(raw, maxMs, padMs, joinGapMs, totalMs = ((file.length() - HEADER_BYTES) / 2) * 1000L / SAMPLE_RATE) { from, to ->
            // The quietest 100 ms between [from] and [to], so the cut falls between words.
            val a = (from / 100).toInt().coerceIn(0, loud.size); val b = (to / 100).toInt().coerceIn(a, loud.size)
            if (b <= a) to else (a until b).minByOrNull { loud[it] }!!.toLong() * 100
        }
    }

    /** Joins stretches separated by a short pause, pads them, and cuts anything longer than [maxMs]. */
    fun chunk(
        raw: List<Segment>, maxMs: Long, padMs: Long, joinGapMs: Long, totalMs: Long,
        quietest: ((fromMs: Long, toMs: Long) -> Long)? = null,
    ): List<Segment> {
        val joined = ArrayList<Segment>()
        for (s in raw) {
            val last = joined.lastOrNull()
            if (last != null && s.startMs - last.endMs <= joinGapMs && s.endMs - last.startMs <= maxMs) {
                joined[joined.lastIndex] = Segment(last.startMs, maxOf(last.endMs, s.endMs))
            } else joined.add(s)
        }
        val padded = joined.map { Segment((it.startMs - padMs).coerceAtLeast(0), (it.endMs + padMs).coerceAtMost(totalMs)) }
        return padded.flatMap { s ->
            if (s.lengthMs <= maxMs) listOf(s)
            else {
                // Too long for Whisper: cut it, at the quietest moment in the last stretch before the limit when known.
                val parts = ArrayList<Segment>()
                var at = s.startMs
                while (s.endMs - at > maxMs) {
                    val cut = quietest?.invoke(at + maxMs * 3 / 5, at + maxMs)?.coerceIn(at + maxMs * 3 / 5, at + maxMs) ?: (at + maxMs)
                    parts.add(Segment(at, cut)); at = cut
                }
                parts.add(Segment(at, s.endMs))
                parts
            }
        }.filter { it.lengthMs >= 300 }
    }

    /** Reads [seg] as floats in -1..1, the form the speech model takes. */
    fun read(file: File, seg: Segment): FloatArray {
        RandomAccessFile(file, "r").use { f ->
            val startByte = HEADER_BYTES + (seg.startMs * SAMPLE_RATE / 1000) * 2
            val count = (seg.lengthMs * SAMPLE_RATE / 1000).toInt()
            f.seek(startByte)
            val bytes = ByteArray(count * 2)
            val n = f.read(bytes).coerceAtLeast(0)
            return FloatArray(n / 2) { i -> (((bytes[2 * i + 1].toInt() shl 8) or (bytes[2 * i].toInt() and 0xFF)).toShort()) / 32768f }
        }
    }
}
