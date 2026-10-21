package app.plait

import app.plait.ui.screens.AboutEntry
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
}
