package app.plait

import app.plait.audio.Wav
import app.plait.audio.levelOf
import app.plait.data.CallLinks
import app.plait.data.CallProvider
import app.plait.data.NoteJson
import app.plait.data.NoteLine
import app.plait.data.SavedNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLinksTest {
    @Test fun findsMeetLink() {
        val link = CallLinks.find("Join: https://meet.google.com/xkq-mfpa-dsh?authuser=0 thanks")
        assertEquals(CallProvider.Meet, link?.provider)
        assertEquals("https://meet.google.com/xkq-mfpa-dsh?authuser=0", link?.url)
    }

    @Test fun findsZoomAndStripsTrailingPunctuation() {
        val link = CallLinks.find("Zoom: https://acme.zoom.us/j/81234567890?pwd=abc123.")
        assertEquals(CallProvider.Zoom, link?.provider)
        assertEquals("https://acme.zoom.us/j/81234567890?pwd=abc123", link?.url)
    }

    @Test fun findsTeamsLink() {
        val link = CallLinks.find("<a href=\"https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc/0?context=%7b%7d\">Join</a>")
        assertEquals(CallProvider.Teams, link?.provider)
    }

    @Test fun decodesHtmlAmpersands() {
        val link = CallLinks.find("https://acme.zoom.us/j/1?pwd=a&amp;uname=b")
        assertEquals("https://acme.zoom.us/j/1?pwd=a&uname=b", link?.url)
    }

    @Test fun locationWinsOverDescription() {
        val link = CallLinks.find("https://meet.google.com/aaa-bbbb-ccc", "https://acme.zoom.us/j/1")
        assertEquals(CallProvider.Meet, link?.provider)
    }

    @Test fun earliestLinkWinsWithinOneField() {
        val link = CallLinks.find("Zoom https://acme.zoom.us/j/1 or Meet https://meet.google.com/aaa-bbbb-ccc")
        assertEquals(CallProvider.Zoom, link?.provider)
    }

    @Test fun noLinkReturnsNull() {
        assertNull(CallLinks.find(null, "", "Lunch at Sourdough & Co. https://example.com/menu"))
    }
}

class NoteJsonTest {
    @Test fun roundTrips() {
        val note = SavedNote(
            id = "n1", eventId = "design-review", title = "Design review", createdAt = 1_760_000_000_000L,
            durationMs = 134_000L, audioFile = "n1.wav",
            lines = listOf(NoteLine(1_200, "cut it to three steps"), NoteLine(6_500, "ask \"legal\" about EU")),
            bookmarksMs = listOf(9_000L, 40_000L),
        )
        val decoded = NoteJson.decode(NoteJson.encode(listOf(note)))
        assertEquals(listOf(note), decoded)
    }

    @Test fun emptyIndexDecodesToEmptyList() {
        assertTrue(NoteJson.decode("""{"version":1,"notes":[]}""").isEmpty())
    }
}

class WavTest {
    @Test fun headerDescribesMono16kPcm() {
        val h = Wav.header(32_000)
        assertEquals(44, h.size)
        assertEquals("RIFF", String(h, 0, 4))
        assertEquals("WAVE", String(h, 8, 4))
        assertEquals("data", String(h, 36, 4))
        fun le32(o: Int) = (0..3).sumOf { (h[o + it].toLong() and 0xFF) shl (8 * it) }
        assertEquals(36L + 32_000, le32(4))
        assertEquals(16_000L, le32(24))
        assertEquals(32_000L, le32(28))
        assertEquals(32_000L, le32(40))
    }

    @Test fun littleEndianBytes() {
        val out = ByteArray(4)
        Wav.toLittleEndian(shortArrayOf(0x0102, -2), 2, out)
        assertEquals(listOf<Byte>(0x02, 0x01, -2, -1), out.toList())
    }

    @Test fun levelIsZeroForSilenceAndHighForLoudSignal() {
        assertEquals(0f, levelOf(ShortArray(512), 512), 0.0001f)
        val loud = ShortArray(512) { if (it % 2 == 0) 20_000 else -20_000 }
        assertTrue(levelOf(loud, 512) > 0.8f)
    }
}

class MicrosoftLinkTest {
    @Test fun unwrapsOutlookSafeLinksToTheTeamsMeeting() {
        val wrapped = "https://nam12.safelinks.protection.outlook.com/?url=https%3A%2F%2Fteams.microsoft.com%2Fl%2Fmeetup-join%2F19%253ameeting_Nzk4%2F0%3Fcontext%3D%257b%257d&data=05%7C01&sdata=abc&reserved=0"
        val link = CallLinks.find("Join the meeting now $wrapped")
        assertEquals(CallProvider.Teams, link?.provider)
        assertEquals("https://teams.microsoft.com/l/meetup-join/19%3ameeting_Nzk4/0?context=%7b%7d", link?.url)
    }

    @Test fun safeLinksInsideHtmlAnchors() {
        val html = """<a href="https://eur01.safelinks.protection.outlook.com/?url=https%3A%2F%2Fteams.microsoft.com%2Fmeet%2F9876543210%3Fp%3Dabc&amp;data=05">Join</a>"""
        val link = CallLinks.find(html)
        assertEquals(CallProvider.Teams, link?.provider)
        assertEquals("https://teams.microsoft.com/meet/9876543210?p=abc", link?.url)
    }

    @Test fun newTeamsMeetLinksAndPersonalTeams() {
        assertEquals(CallProvider.Teams, CallLinks.find("https://teams.microsoft.com/meet/9876543210?p=abc")?.provider)
        assertEquals(CallProvider.Teams, CallLinks.find("https://teams.live.com/meet/9400000000000")?.provider)
    }

    @Test fun ordinaryLinksAreUntouched() {
        assertNull(CallLinks.find("https://nam12.safelinks.protection.outlook.com/?url=https%3A%2F%2Fexample.com%2Fmenu&data=1"))
    }
}
