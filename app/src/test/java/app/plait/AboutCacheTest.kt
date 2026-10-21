package app.plait

import app.plait.ui.screens.AboutEntry
import app.plait.ui.screens.AboutLinked
import app.plait.ui.screens.shouldRefresh
import app.plait.ui.screens.AboutSource.Invite
import app.plait.ui.screens.AboutSource.Title
import app.plait.ui.screens.cachedAbout
import app.plait.ui.screens.shouldWrite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutCacheTest {
    private val t0 = 1_000_000_000L
    private val day = AboutEntry.RETRY_MS
    private fun written(kind: app.plait.ui.screens.AboutSource, hash: Int = 1) = AboutEntry("A review of the plan.", "ai", kind, hash, t0)
    private fun tried(kind: app.plait.ui.screens.AboutSource, hash: Int = 1) = AboutEntry(null, "ai", kind, hash, t0)

    @Test fun writtenEntryNeverRewritten() {
        assertFalse(shouldWrite(written(Invite), Invite, 99, true, t0 + 30 * day))
        assertFalse(shouldWrite(written(Title), Title, 99, true, t0 + 30 * day))
        assertFalse(shouldWrite(written(Invite), Title, 99, true, t0 + 30 * day))
    }

    @Test fun titleGuessUpgradesOnceWhenInviteHasContent() {
        assertTrue(shouldWrite(written(Title), Invite, 2, true, t0 + 1))
        assertNull(cachedAbout(written(Title), Invite))
        // The upgraded entry is permanent again.
        assertFalse(shouldWrite(written(Invite, 2), Invite, 3, true, t0 + 30 * day))
        assertEquals("A review of the plan.", cachedAbout(written(Invite), Title))
    }

    @Test fun triedEntryWaitsADayAndAChangedInput() {
        assertFalse(shouldWrite(tried(Invite), Invite, 2, true, t0 + day - 1))
        assertTrue(shouldWrite(tried(Invite), Invite, 2, true, t0 + day))
        assertFalse(shouldWrite(tried(Invite), Invite, 1, true, t0 + 30 * day))
    }

    @Test fun noAiNeverWrites() {
        assertFalse(shouldWrite(null, Invite, 1, false, t0))
        assertFalse(shouldWrite(written(Title), Invite, 1, false, t0))
        assertFalse(shouldWrite(tried(Invite), Invite, 2, false, t0 + 30 * day))
    }

    @Test fun refreshClearsSoItWritesAgain() {
        // Refresh removes the entry; with none stored the AI writes now.
        assertTrue(shouldWrite(null, Invite, 1, true, t0))
        assertTrue(shouldWrite(null, Title, 1, true, t0))
    }

    @Test fun entryRoundTrips() {
        val e = AboutEntry("Two lines\nof text: with colons.", "ai", Invite, -12345, t0)
        assertEquals(e, AboutEntry.decode(e.encode()))
        val t = tried(Title, 7)
        assertEquals(t, AboutEntry.decode(t.encode()))
        assertNull(cachedAbout(AboutEntry.decode(t.encode()), Title))
    }

    @Test fun oldFormatIsNotRead() {
        assertNull(AboutEntry.decode("ai\u0000Some old text"))
        assertNull(AboutEntry.decode(null))
        assertEquals("v12:ev1", AboutEntry.key("ev1"))
    }

    private val notes = AboutLinked(listOf(1L to "Agreed the launch date."), listOf("Send the deck"))
    private fun stored(l: AboutLinked, engine: String = "Nano:") = written(Invite).copy(engine = engine, engineLabel = "Gemini Nano", linkedHash = l.hash)

    @Test fun refreshSkippedWhenNothingChanged() {
        assertFalse(shouldRefresh(stored(notes), "Nano:", notes.hash))
        assertFalse(shouldRefresh(stored(AboutLinked(emptyList(), emptyList())), "Nano:", 0))
    }

    @Test fun refreshWhenNewNotesEngineOrNoEntry() {
        val more = notes.copy(notes = notes.notes + (2L to "Booked the room."))
        assertTrue(shouldRefresh(stored(notes), "Nano:", more.hash))
        assertTrue(shouldRefresh(stored(notes), "Local:qwen", notes.hash))
        assertTrue(shouldRefresh(null, "Nano:", 0))
    }

    @Test fun unknownOldEntryRefreshesOnlyWithLinkedMaterial() {
        val old = AboutEntry.decode("ai\u0000Invite\u00001\u00001000\u0000Old text")!!
        assertEquals(AboutEntry.UNKNOWN_ENGINE, old.engine)
        assertEquals(0, old.linkedHash)
        assertFalse(shouldRefresh(old, "Nano:", AboutLinked(emptyList(), emptyList()).hash))
        assertTrue(shouldRefresh(old, "Nano:", notes.hash))
    }

    @Test fun newEntryRoundTripsWithEngineAndHash() {
        val e = AboutEntry("Text\nwith lines.", "ai", Invite, 5, t0, "Local:qwen", "Qwen 2.5", -77)
        assertEquals(e, AboutEntry.decode(e.encode()))
        val t = AboutEntry(null, "ai", Title, 5, t0, "Nano:", "Gemini Nano", 3)
        assertEquals(t, AboutEntry.decode(t.encode()))
    }

    @Test fun linkedHashIsStableAndSensitive() {
        assertEquals(notes.hash, AboutLinked(listOf(1L to "Agreed the launch date."), listOf("Send the deck")).hash)
        assertEquals(0, AboutLinked(emptyList(), listOf(" ")).hash)
        assertTrue(notes.hash != notes.copy(tasks = notes.tasks + "Book room").hash)
        assertTrue(notes.hash != notes.copy(notes = notes.notes + (2L to "More")).hash)
        val list = AboutLinked(listOf(1L to "[ ] Call Sam\n[ ] Pay"), emptyList())
        assertTrue(list.hash != AboutLinked(listOf(1L to "[x] Call Sam\n[ ] Pay"), emptyList()).hash)
    }

    @Test fun digestLimits() {
        val many = AboutLinked((1L..5L).map { it to "note$it " + "x".repeat(300) }, (1..8).map { "task $it" })
        val lines = many.digest().lines()
        assertEquals(3, lines.count { it.startsWith("- note") })
        assertTrue(lines.filter { it.startsWith("- note") }.all { it.length == 2 + 200 })
        assertTrue(many.digest().contains("note5") && many.digest().contains("note3") && !many.digest().contains("note2"))
        assertEquals(5, lines.count { it.startsWith("- task") })
        assertEquals("", AboutLinked(emptyList(), emptyList()).digest())
    }
}
