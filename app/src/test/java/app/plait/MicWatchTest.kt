package app.plait

import android.media.MediaRecorder
import app.plait.audio.MIC_SOURCES
import app.plait.audio.MicWatch
import app.plait.audio.MicWatch.Act
import app.plait.audio.Vad
import app.plait.audio.isDigitalSilence
import app.plait.data.NoteJson
import app.plait.data.NoteLine
import app.plait.data.SavedNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class MicWatchTest {
    private val rate = 16_000
    private val rnd = java.util.Random(7)

    /** Amplitude of a sine whose loudness is [db] dBFS. */
    private fun amp(db: Double) = 32768 * 10.0.pow(db / 20) * sqrt(2.0)
    private fun zeros(ms: Int) = ShortArray(rate * ms / 1000)
    private fun room(ms: Int, db: Double = -75.0) = ShortArray(rate * ms / 1000) { (rnd.nextGaussian() * 32768 * 10.0.pow(db / 20)).toInt().toShort() }
    private fun voice(ms: Int, db: Double) = room(ms).let { r -> ShortArray(r.size) { (r[it] + amp(db) * sin(2 * PI * 200 * it / rate)).toInt().toShort() } }

    /** Feeds [audio] in 20 ms reads, like the recorder, and returns every action that was not None. */
    private fun MicWatch.hear(audio: ShortArray): List<Act> =
        audio.toList().chunked(rate / 50).map { feed(it.toShortArray()) }.filter { it != Act.None }

    private fun vadHears(vararg parts: ShortArray): Boolean {
        val vad = Vad(rate)
        return parts.any { vad.feed(it).started }
    }

    @Test fun digitalSilenceQuietSpeechAndSpeechAreTold() {
        // Blocked mic: exact zeros (or a step of dither). Not "nobody talking", and the voice detector hears nothing.
        assertTrue(isDigitalSilence(zeros(500)))
        assertTrue(isDigitalSilence(ShortArray(320) { (if (it % 2 == 0) 1 else -1).toShort() }))
        assertFalse(vadHears(zeros(3000)))
        // A live mic in a silent room is real silence, not a block.
        assertFalse(isDigitalSilence(room(500)))
        assertFalse(vadHears(room(3000), room(3000, -70.0)))
        // Quiet speakerphone speech (-55 dBFS) is live audio, and the detector keeps it: the old -48 dB floor threw it away.
        assertFalse(isDigitalSilence(voice(500, -55.0)))
        assertTrue(vadHears(room(600), voice(1000, -55.0)))
        // Ordinary speech.
        assertFalse(isDigitalSilence(voice(500, -25.0)))
        assertTrue(vadHears(room(600), voice(1000, -25.0)))
    }

    @Test fun onlyTheCountedSamplesAreChecked() {
        val buf = ShortArray(320).also { it[300] = 5000 }
        assertTrue(isDigitalSilence(buf, 200))
        assertFalse(isDigitalSilence(buf, 320))
        assertTrue(isDigitalSilence(buf, 0))
        assertFalse(isDigitalSilence(buf, 10_000)) // a count past the end is clamped
    }

    @Test fun silenceSwitchesSourceOnceThenReportsBlocked() {
        assertEquals(listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC), MIC_SOURCES)
        val w = MicWatch(MIC_SOURCES.size)
        assertEquals(listOf(Act.NextSource), w.hear(zeros(2600)))
        assertEquals(1, w.source)
        assertFalse(w.blocked)
        // The second source is silent too: tell the user, once.
        assertEquals(listOf(Act.Blocked), w.hear(zeros(6000)))
        assertTrue(w.blocked)
        // The call ends and the mic comes back.
        assertEquals(listOf(Act.Clear), w.hear(voice(500, -30.0)))
        assertFalse(w.blocked)
        // Silenced again later: no more sources to try, so straight to Blocked.
        assertEquals(listOf(Act.Blocked), w.hear(zeros(3000)))
        assertEquals(1, w.source)
    }

    @Test fun startupZerosAndQuietRoomsAreNotABlock() {
        val w = MicWatch(MIC_SOURCES.size)
        // Some phones hand over a few hundred ms of zeros while the mic warms up.
        assertEquals(emptyList<Act>(), w.hear(zeros(400) + room(10_000) + zeros(2000) + room(1000)))
        assertEquals(0, w.source)
    }

    @Test fun oneSourceGoesStraightToBlocked() {
        val w = MicWatch(1)
        assertEquals(listOf(Act.Blocked), w.hear(zeros(2600)))
    }

    @Test fun theReasonIsKeptOnTheNote() {
        val n = SavedNote("n", "e", "t", 1, 30_000, "n.wav", emptyList<NoteLine>(), emptyList(), silentReason = MicWatch.BLOCKED_IN_CALL)
        assertEquals(MicWatch.BLOCKED_IN_CALL, NoteJson.decode(NoteJson.encode(listOf(n))).single().silentReason)
        assertEquals(null, NoteJson.decode(NoteJson.encode(listOf(n.copy(silentReason = null)))).single().silentReason)
    }
}
