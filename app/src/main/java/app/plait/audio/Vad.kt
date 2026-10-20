package app.plait.audio

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Voice activity detection on 20 ms frames. It learns the room's background level as it goes and
 * treats a frame as speech when it is clearly louder than that, so a fan or traffic hum does not
 * count and a quiet voice in a quiet room does.
 *
 * Audio is only forwarded while someone is speaking. The 300 ms before a word starts is kept and
 * sent with it, so the first syllable is not clipped, and the speaker gets 440 ms of silence to
 * pause before the phrase is closed.
 */
class Vad(private val sampleRate: Int = 16_000) {
    class Output(val audio: List<ShortArray>, val started: Boolean, val ended: Boolean)

    private val frameSize = sampleRate / 50
    private val carry = ShortArray(frameSize)
    private var carryN = 0
    private val preroll = ArrayDeque<ShortArray>()

    private var noiseDb = -55.0
    private var calibrating = CALIBRATION_FRAMES
    private var calibSum = 0.0
    private var hot = 0
    private var cold = 0

    var speaking = false
        private set

    /** Loudness of the latest frame as 0..1, for the level meter. */
    var level = 0f
        private set

    /** Milliseconds of audio seen so far. */
    var elapsedMs = 0L
        private set

    /** When the current (or last) phrase began, in milliseconds from the start. */
    var speechStartMs = 0L
        private set

    fun feed(samples: ShortArray, count: Int = samples.size): Output {
        val audio = ArrayList<ShortArray>()
        var started = false
        var ended = false
        var i = 0
        while (i < count) {
            val take = minOf(frameSize - carryN, count - i)
            System.arraycopy(samples, i, carry, carryN, take)
            carryN += take
            i += take
            if (carryN == frameSize) {
                val frame = carry.copyOf()
                carryN = 0
                elapsedMs += 20
                val db = decibels(frame)
                level = ((db + 60.0) / 50.0).coerceIn(0.0, 1.0).toFloat()
                val loud = isSpeechLike(db)
                if (calibrating > 0) {
                    // The first fraction of a second sets the room level, unless it is already loud.
                    calibSum += db
                    if (--calibrating == 0) noiseDb = (calibSum / CALIBRATION_FRAMES).coerceIn(-80.0, -38.0)
                    preroll.addLast(frame); trim()
                    continue
                }
                if (!speaking) {
                    if (!loud) adapt(db)
                    hot = if (loud) hot + 1 else 0
                    preroll.addLast(frame); trim()
                    if (hot >= ONSET_FRAMES) {
                        speaking = true
                        started = true
                        cold = 0
                        speechStartMs = (elapsedMs - preroll.size * 20L).coerceAtLeast(0)
                        audio.addAll(preroll)
                        preroll.clear()
                    }
                } else {
                    audio.add(frame)
                    cold = if (loud) 0 else cold + 1
                    if (cold >= HANGOVER_FRAMES) {
                        speaking = false
                        ended = true
                        hot = 0
                    }
                }
            }
        }
        return Output(audio, started, ended)
    }

    /** Frames still waiting for a full 20 ms, to be forwarded when the recording stops mid-phrase. */
    fun remainder(): ShortArray = if (speaking && carryN > 0) carry.copyOf(carryN) else ShortArray(0)

    private fun isSpeechLike(db: Double) = db > maxOf(noiseDb + MARGIN_DB, ABSOLUTE_FLOOR_DB)

    private fun adapt(db: Double) {
        // Falls quickly when the room gets quieter, rises slowly so speech never becomes "the noise".
        noiseDb = if (db < noiseDb) noiseDb * 0.8 + db * 0.2 else noiseDb * 0.98 + db * 0.02
        noiseDb = noiseDb.coerceIn(-80.0, -38.0)
    }

    private fun trim() { while (preroll.size > PREROLL_FRAMES) preroll.removeFirst() }

    private fun decibels(frame: ShortArray): Double {
        var sum = 0.0
        for (s in frame) sum += s.toDouble() * s
        val rms = sqrt(sum / frame.size) / 32768.0
        return 20.0 * log10(rms + 1e-9)
    }

    companion object {
        const val MARGIN_DB = 9.0
        const val ABSOLUTE_FLOOR_DB = -48.0
        const val ONSET_FRAMES = 3
        const val HANGOVER_FRAMES = 22
        const val PREROLL_FRAMES = 15
        const val CALIBRATION_FRAMES = 10
    }
}
