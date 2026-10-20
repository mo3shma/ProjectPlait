package app.plait

import app.plait.data.CalEvent
import app.plait.data.Guest
import app.plait.data.NoteLine
import app.plait.data.Rsvp
import app.plait.data.SavedNote
import app.plait.data.Source
import app.plait.data.brief.BriefBuilder
import app.plait.data.brief.DescriptionCleaner
import app.plait.data.brief.MeetingKind
import app.plait.data.brief.Summarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun ev(
    title: String, description: String? = null, guests: List<Guest> = emptyList(), location: String? = null,
    recurring: Boolean = false, id: String = "e1", startMs: Long = 1_000_000_000L,
) = CalEvent(id, "10:00", "30m", title, Source.Google, "", location = location, description = description, startMs = startMs, endMs = startMs + 1_800_000, guests = guests, recurring = recurring)

private fun g(name: String, email: String, rsvp: Rsvp = Rsvp.Accepted, self: Boolean = false, organizer: Boolean = false) =
    Guest(name, email, rsvp, organizer = organizer, self = self)

private val TEAMS_INVITE = """
Weekly sync on the Northwind renewal.

Agenda:
- Review the quote
- SLA questions
- Next steps

________________________________________________________________________________
Microsoft Teams meeting
Join: https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc/0
Meeting ID: 123 456 789 123
Passcode: abc123
Need help? | System reference
Dial in by phone
+44 20 1234 5678,,123456789# United Kingdom, London
Find a local number | Reset PIN
Learn More | Meeting options
""".trimIndent()

class DescriptionCleanerTest {
    @Test fun stripsTeamsJoinInstructionsAndKeepsWhatWasWritten() {
        assertEquals(
            "Weekly sync on the Northwind renewal.\n\nAgenda:\n- Review the quote\n- SLA questions\n- Next steps",
            DescriptionCleaner.clean(TEAMS_INVITE),
        )
    }

    @Test fun convertsGoogleStyleHtml() {
        val html = "<p>Let&#39;s decide the pricing for Q4.</p><ul><li>Option A</li><li>Option B</li></ul><br>Join with Google Meet<br>https://meet.google.com/abc-defg-hij"
        val out = DescriptionCleaner.clean(html)
        assertTrue(out, out.startsWith("Let's decide the pricing for Q4."))
        assertTrue(out.contains("- Option A"))
        assertFalse(out.contains("meet.google.com"))
        assertFalse(out.contains("<"))
    }

    @Test fun dropsZoomBlocksAndSafeLinks() {
        val zoom = "Quarterly numbers walkthrough\nJoin Zoom Meeting\nhttps://acme.zoom.us/j/81234567890?pwd=x\nMeeting ID: 812 3456 7890\nPasscode: 123\nOne tap mobile\n+13462487799,,81234567890# US"
        assertEquals("Quarterly numbers walkthrough", DescriptionCleaner.clean(zoom))
        val safe = "See https://nam12.safelinks.protection.outlook.com/?url=https%3A%2F%2Fexample.com%2Fdoc&data=1 before we meet"
        assertEquals("See before we meet", DescriptionCleaner.clean(safe))
    }

    @Test fun emptyInputsGiveEmptyText() {
        assertEquals("", DescriptionCleaner.clean(null))
        assertEquals("", DescriptionCleaner.clean("   \n  "))
        assertEquals("", DescriptionCleaner.clean("Microsoft Teams meeting\nJoin: https://teams.microsoft.com/meet/1?p=2"))
    }
}

class SummarizerTest {
    private val text = "The onboarding flow loses people at step four. Step four asks for a phone number. " +
        "We want to merge phone verification into the email step. The weather has been lovely lately. " +
        "Merging verification would cut the onboarding flow to three steps."

    @Test fun extractKeepsTheMostRepresentativeSentencesInOrder() {
        val out = Summarizer.extract(text, max = 2)
        assertTrue(out, out.length <= 240)
        assertFalse(out, out.contains("weather"))
        assertEquals(out, Summarizer.extract(text, max = 2)) // deterministic
        assertTrue(out.indexOf("step four") in 0..60 || out.contains("Merging"))
    }

    @Test fun shortTextIsReturnedWhole() {
        assertEquals("Weekly sync on the Northwind renewal.", Summarizer.extract("Weekly sync on the Northwind renewal."))
    }

    @Test fun clipsAtAWordBoundary() {
        val out = Summarizer.clip("alpha beta gamma delta epsilon", 14)
        assertEquals("alpha beta…", out)
    }

    @Test fun findsActionItemsInTranscriptLines() {
        val lines = listOf(
            "okay noting down the decision on the signup flow",
            "we need to send the revised prototypes by wednesday",
            "the room was a bit cold today",
            "follow up with sam about the phone number for eu users",
        )
        val actions = Summarizer.actionItems(lines)
        assertEquals(2, actions.size)
        assertTrue(actions[0].startsWith("We need to send"))
        assertTrue(actions.all { it.endsWith(".") })
    }

    @Test fun bulletsFallBackToAllSentencesWhenShort() {
        assertEquals(listOf("We are going from five steps to three."), Summarizer.bullets(listOf("we are going from five steps to three")))
        assertTrue(Summarizer.bullets(emptyList()).isEmpty())
    }
}

class BriefTest {
    @Test fun classifiesByTitle() {
        fun k(t: String) = BriefBuilder.classify(ev(t))
        assertEquals(MeetingKind.Review, k("Design review: Q4 onboarding"))
        assertEquals(MeetingKind.OneOnOne, k("1:1 with Sam"))
        assertEquals(MeetingKind.Standup, k("Team standup"))
        assertEquals(MeetingKind.Planning, k("Sprint planning"))
        assertEquals(MeetingKind.Social, k("Lunch with Priya"))
        assertEquals(MeetingKind.Personal, k("Climbing"))
        assertEquals(MeetingKind.Personal, k("Dentist"))
        assertEquals(MeetingKind.Interview, k("Interview: backend"))
        assertEquals(MeetingKind.External, k("Vendor call: Northwind"))
        assertEquals(MeetingKind.Focus, k("Focus time"))
        assertEquals(MeetingKind.General, k("Project Atlas"))
    }

    @Test fun guestListCanDecideWhenTheTitleDoesNot() {
        val me = g("Me", "me@acme.com", self = true)
        assertEquals(MeetingKind.OneOnOne, BriefBuilder.classify(ev("Project Atlas", guests = listOf(me, g("Sam", "sam@acme.com")))))
        val team = listOf(me, g("A", "a@acme.com"), g("B", "b@acme.com"), g("C", "c@acme.com"))
        assertEquals(MeetingKind.General, BriefBuilder.classify(ev("Project Atlas", guests = team)))
        val ext = listOf(me, g("X", "x@northwind.com"), g("Y", "y@northwind.com"), g("Z", "z@acme.com"))
        assertEquals(MeetingKind.External, BriefBuilder.classify(ev("Project Atlas", guests = ext)))
    }

    @Test fun readsTheAgendaAndPurposeFromAnInvite() {
        val b = BriefBuilder.build(ev("Northwind weekly", description = TEAMS_INVITE))
        assertEquals(listOf("Review the quote", "SLA questions", "Next steps"), b.agenda)
        assertEquals("Weekly sync on the Northwind renewal.", b.about)
        assertFalse(b.aboutIsGuess)
    }

    @Test fun anExplicitPurposeLineWins() {
        val b = BriefBuilder.build(ev("Contract chat", description = "Some background text that is long enough to summarise properly here.\nPurpose: Decide whether to renew the Northwind contract."))
        assertEquals("Decide whether to renew the Northwind contract.", b.about)
    }

    @Test fun bulletsWithoutAHeadingStillBecomeAnAgenda() {
        val b = BriefBuilder.build(ev("Pricing", description = "Let's settle pricing.\n- Option A\n- Option B\n- Option C"))
        assertEquals(listOf("Option A", "Option B", "Option C"), b.agenda)
        assertEquals("Let's settle pricing.", b.about)
    }

    @Test fun anEmptyInviteGetsAHonestGuess() {
        val b = BriefBuilder.build(ev("1:1 with Sam", guests = listOf(g("Me", "me@acme.com", self = true), g("Sam Aoki", "sam@acme.com"))))
        assertTrue(b.aboutIsGuess)
        assertTrue(b.about, b.about.contains("Sam Aoki"))
        assertTrue(b.about, b.about.contains("doesn't say"))
    }

    @Test fun bootlerplateOnlyInvitesAreTreatedAsEmpty() {
        val b = BriefBuilder.build(ev("Project Atlas", description = "Microsoft Teams meeting\nJoin: https://teams.microsoft.com/meet/1?p=2\nMeeting ID: 1 2 3"))
        assertTrue(b.aboutIsGuess)
        assertTrue(b.agenda.isEmpty())
    }

    @Test fun chipsMentionRepeatsAndExternalCompanies() {
        val guests = listOf(g("Me", "me@acme.com", self = true), g("Lee", "lee@northwind.com"), g("Ann", "ann@northwind.com"))
        val b = BriefBuilder.build(ev("Renewal", guests = guests, recurring = true))
        assertTrue(b.chips.toString(), "Repeats" in b.chips)
        assertTrue(b.chips.toString(), "External · Northwind" in b.chips)
        assertTrue(b.chips.toString(), "3 people" in b.chips)
    }

    @Test fun personalAddressesDoNotNameACompany() {
        val guests = listOf(g("Me", "me@acme.com", self = true), g("Pat", "pat@gmail.com"), g("Kim", "kim@gmail.com"))
        val b = BriefBuilder.build(ev("Advisory", guests = guests))
        assertTrue(b.chips.toString(), "External guests" in b.chips)
    }

    @Test fun withAndRsvpLines() {
        val guests = listOf(
            g("Me", "me@acme.com", self = true), g("Priya Raman", "p@acme.com", organizer = true), g("Sam Aoki", "s@acme.com"),
            g("Lena K", "l@acme.com", Rsvp.Declined), g("Dev", "d@acme.com", Rsvp.Pending),
        )
        val e = ev("Planning", guests = guests)
        assertEquals("with Priya, Sam +2", BriefBuilder.withLine(e))
        assertEquals("3 of 5 accepted · 1 declined · 1 waiting", BriefBuilder.rsvpLine(e))
        assertEquals("", BriefBuilder.rsvpLine(ev("Solo")))
    }

    @Test fun lastTimeComesFromTheLatestEarlierNoteWithTheSameTitle() {
        fun note(id: String, event: String, title: String, at: Long, vararg lines: String) =
            SavedNote(id, event, title, at, 60_000, "$id.wav", lines.mapIndexed { i, t -> NoteLine(i * 1000L, t) }, emptyList())
        val notes = listOf(
            note("old", "e0", "1:1 with Sam", 100, "talked about the roadmap in general terms today"),
            note("new", "e00", "1:1 with Sam", 500, "we need to raise the on-call rotation again next week", "sam is happy with the new tooling"),
            note("other", "e9", "Team standup", 600, "nothing to report here today really"),
            note("future", "e2", "1:1 with Sam", 9_999_999_999, "this one has not happened yet at all"),
        )
        val e = ev("1:1 with Sam", id = "e1", startMs = 1_000_000)
        val last = BriefBuilder.lastTime(e, notes, nowMs = 1_000_000)
        assertNotNull(last)
        assertEquals(500L, last!!.whenMs)
        assertTrue(last.summary, last.summary.startsWith("We need to raise the on-call rotation"))
        assertEquals(1, last.followUps)
        assertNull(BriefBuilder.lastTime(ev("Brand new meeting"), notes, 1_000_000))
    }

    @Test fun sameEventsOwnNotesAreNotLastTime() {
        val n = SavedNote("n", "e1", "Weekly", 10, 1000, "n.wav", listOf(NoteLine(0, "something was said in this meeting")), emptyList())
        assertNull(BriefBuilder.lastTime(ev("Weekly", id = "e1"), listOf(n), 1_000_000))
    }
}

class GuestTest {
    @Test fun labelsAndInitialsFallBackToTheAddress() {
        val g = Guest("", "priya.raman@acme.com")
        assertEquals("Priya Raman", g.label)
        assertEquals("PR", g.initials)
        assertEquals("acme.com", g.domain)
        assertEquals("Guest", Guest("").label)
        assertEquals("SA", Guest("Sam Aoki", "s@x.com").initials)
    }
}
