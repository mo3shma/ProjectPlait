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
        RandomAccessFile(file, "r").use { f ->
            f.seek(HEADER_BYTES)
            val vad = Vad(SAMPLE_RATE)
            val bytes = ByteArray(32_000)
            var start = -1L
            while (true) {
                val n = f.read(bytes)
                if (n <= 0) break
                val shorts = ShortArray(n / 2) { i -> ((bytes[2 * i + 1].toInt() shl 8) or (bytes[2 * i].toInt() and 0xFF)).toShort() }
                val out = vad.feed(shorts)
                if (out.started) start = vad.speechStartMs
                if (out.ended && start >= 0) { raw.add(Segment(start, vad.elapsedMs)); start = -1 }
            }
            if (start >= 0) raw.add(Segment(start, vad.elapsedMs))
        }
        return chunk(raw, maxMs, padMs, joinGapMs, totalMs = ((file.length() - HEADER_BYTES) / 2) * 1000L / SAMPLE_RATE)
    }

    /** Joins stretches separated by a short pause, pads them, and cuts anything longer than [maxMs]. */
    fun chunk(raw: List<Segment>, maxMs: Long, padMs: Long, joinGapMs: Long, totalMs: Long): List<Segment> {
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
            else generateSequence(s.startMs) { it + maxMs }.takeWhile { it < s.endMs }.map { Segment(it, minOf(it + maxMs, s.endMs)) }.toList()
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
