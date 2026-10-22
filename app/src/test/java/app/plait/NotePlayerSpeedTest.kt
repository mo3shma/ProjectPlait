package app.plait

import app.plait.audio.NotePlayer
import org.junit.Assert.assertEquals
import org.junit.Test

class NotePlayerSpeedTest {
    @Test fun speedCyclesAndWraps() {
        assertEquals(1.25f, NotePlayer.nextSpeed(1f), 0f)
        assertEquals(1.5f, NotePlayer.nextSpeed(1.25f), 0f)
        assertEquals(2f, NotePlayer.nextSpeed(1.5f), 0f)
        assertEquals(1f, NotePlayer.nextSpeed(2f), 0f)
        assertEquals(1f, NotePlayer.nextSpeed(0.7f), 0f) // an unknown value goes to 1x
    }

    @Test fun seekIsClamped() {
        assertEquals(15_000L, NotePlayer.seekTarget(5_000, 10_000, 60_000))
        assertEquals(0L, NotePlayer.seekTarget(4_000, -10_000, 60_000))
        assertEquals(60_000L, NotePlayer.seekTarget(55_000, 10_000, 60_000))
        assertEquals(0L, NotePlayer.seekTarget(0, 10_000, 0))
    }

    @Test fun labels() {
        assertEquals("1x", NotePlayer.label(1f)); assertEquals("1.25x", NotePlayer.label(1.25f))
        assertEquals("1.5x", NotePlayer.label(1.5f)); assertEquals("2x", NotePlayer.label(2f))
    }
}
