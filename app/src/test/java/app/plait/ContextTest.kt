package app.plait

import app.plait.data.CalEvent
import app.plait.data.Guest
import app.plait.data.Rsvp
import app.plait.data.Source
import app.plait.data.brief.BriefBuilder
import app.plait.data.context.ContextAnalyzer
import app.plait.data.context.ContextItem
import app.plait.data.context.ContextJson
import app.plait.data.context.DateMentions
import app.plait.data.context.EmailParser
import app.plait.data.context.EventMatcher
import app.plait.data.context.HighlightKind
import app.plait.data.context.Strength
import app.plait.data.context.Threads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val LDN = ZoneId.of("Europe/London")
private fun utcMs(iso: String) = Instant.parse(iso).toEpochMilli()

private val EML = """
From: =?UTF-8?B?Sm8gUGFyaw==?= <jo.park@northwind.com>
To: You <you@acme.com>, Sam Aoki <sam@acme.com>
Subject: =?UTF-8?Q?Re=3A_Northwind_renewal_=E2=80=93_SLA_questions?=
Date: Fri, 16 Oct 2026 14:05:00 +0100
MIME-Version: 1.0
Content-Type: multipart/mixed; boundary="MIX"

--MIX
Content-Type: multipart/alternative; boundary="ALT"

--ALT
Content-Type: text/plain; charset="utf-8"
Content-Transfer-Encoding: quoted-printable

Hi Sam,

Thanks for the call. We=E2=80=99ve agreed the renewal at =C2=A348k per year. Can you confirm the=
 SLA credits before Tuesday's meeting?

Best regards,
Jo
--ALT
Content-Type: text/html; charset="utf-8"

<p>html version</p>
--ALT--
--MIX
Content-Type: application/pdf; name="quote-v3.pdf"
Content-Disposition: attachment; filename="quote-v3.pdf"
Content-Transfer-Encoding: base64

JVBERi0=
--MIX--
""".trimIndent()

class EmailParserTest {
    private val mail = EmailParser.parseEml(EML)

    @Test fun decodesEncodedHeaders() {
        assertEquals("Re: Northwind renewal – SLA questions", mail.subject)
        assertEquals("Jo Park <jo.park@northwind.com>", mail.from)
        assertTrue(mail.to.contains("sam@acme.com"))
    }

    @Test fun parsesTheDateHeader() {
        assertEquals(utcMs("2026-10-16T13:05:00Z"), mail.dateMs)
    }

    @Test fun prefersThePlainTextAndDecodesQuotedPrintable() {
        assertTrue(mail.body, mail.body.contains("We’ve agreed the renewal at £48k per year."))
        assertTrue(mail.body, mail.body.contains("confirm the SLA credits before Tuesday's meeting?"))
        assertFalse(mail.body.contains("html version"))
    }

    @Test fun listsAttachmentsWithoutReadingThem() {
        assertEquals(listOf("quote-v3.pdf"), mail.attachments)
        assertFalse(mail.body.contains("JVBER"))
    }

    @Test fun decodesBase64HtmlWhenThereIsNoPlainPart() {
        val html = java.util.Base64.getEncoder().encodeToString("<p>Hello <b>team</b></p><p>Agenda is attached.</p>".toByteArray())
        val raw = "From: a@b.com\nSubject: Hi\nDate: Fri, 16 Oct 2026 14:05:00 +0100\nContent-Type: text/html; charset=utf-8\nContent-Transfer-Encoding: base64\n\n$html\n"
        assertEquals("Hello team\nAgenda is attached.", EmailParser.parseEml(raw).body)
    }

    @Test fun recognisesHeaderBlocksAndPlainText() {
        assertTrue(EmailParser.looksLikeEml(EML))
        assertFalse(EmailParser.looksLikeEml("Hi Sam,\nCan we move the meeting to Tuesday?\nThanks"))
        val shared = EmailParser.fromText("Can we move the meeting to Tuesday?", subjectHint = "Re: Planning")
        assertEquals("Re: Planning", shared.subject)
        assertEquals("Can we move the meeting to Tuesday?", shared.body)
    }

    @Test fun pastedHeaderLinesFillInSubjectAndSender() {
        val p = EmailParser.fromText("Subject: Budget\nFrom: Pat <pat@x.com>\n\nNumbers attached.")
        assertEquals("Budget", p.subject)
        assertEquals("Pat <pat@x.com>", p.from)
    }

    @Test fun displayNames() {
        assertEquals("Jo Park", EmailParser.displayName("Jo Park <jo@x.com>"))
        assertEquals("Jo Park", EmailParser.displayName("\"Park, Jo\" <jo@x.com>"))
        assertEquals("Priya Raman", EmailParser.displayName("priya.raman@acme.com"))
    }

    @Test fun findsAddresses() {
        assertEquals(listOf("jo@x.com", "sam@acme.com"), EmailParser.addresses("Jo <JO@x.com>, sam@acme.com; jo@x.com"))
    }
}

class ThreadsTest {
    @Test fun splitsAGmailStyleQuotedThreadIntoMessages() {
        val body = """
Sounds good, thanks. Can we also look at the 99.9% uptime clause on Tuesday?

On Fri, 16 Oct 2026 at 14:05, Jo Park <jo.park@northwind.com> wrote:

> Hi Sam,
> We've agreed the renewal at £48k per year. Can you confirm the SLA credits before Tuesday's meeting?
>
> Best regards,
> Jo
>
> On Thu, 15 Oct 2026 at 09:10, Sam Aoki <sam@acme.com> wrote:
> > We need the revised quote by Thursday.
""".trimIndent()
        val msgs = Threads.split(body, "Sam Aoki <sam@acme.com>")
        assertEquals(3, msgs.size)
        assertEquals("Sam Aoki", msgs[0].from)
        assertEquals("Jo Park", msgs[1].from)
        assertEquals("Sam Aoki", msgs[2].from)
        assertTrue(msgs[1].text, msgs[1].text.startsWith("We've agreed the renewal"))
        assertFalse(msgs[1].text.contains("Best regards"))
        assertFalse(msgs[1].text.contains("Hi Sam"))
        assertEquals("We need the revised quote by Thursday.", msgs[2].text)
    }

    @Test fun splitsAnOutlookStyleThread() {
        val body = """
Thanks Jo. Let's lock the uptime clause on Tuesday.

________________________________
From: Jo Park <jo.park@northwind.com>
Sent: 16 October 2026 14:05
To: Sam Aoki <sam@acme.com>
Subject: RE: Northwind renewal

Hi Sam,
We can't go below 99.5% uptime.

Kind regards
Jo
""".trimIndent()
        val msgs = Threads.split(body, null)
        assertEquals(2, msgs.size)
        assertEquals("Jo Park", msgs[1].from)
        assertEquals("16 October 2026 14:05", msgs[1].dateText)
        assertEquals("We can't go below 99.5% uptime.", msgs[1].text)
    }

    @Test fun stripsSignaturesDisclaimersAndMobileFooters() {
        val body = "Quick note: the quote is attached.\n\nThanks,\nJo\n\nSent from my iPhone\n\nThis email and any attachments are confidential and intended solely for the addressee."
        assertEquals("Quick note: the quote is attached.", Threads.split(body).single().text)
        val sig = "Numbers look fine.\n-- \nJo Park\nNorthwind Ltd\n+44 20 0000 0000"
        assertEquals("Numbers look fine.", Threads.split(sig).single().text)
    }

    @Test fun aPlainMessageIsOneMessage() {
        val m = Threads.split("Can we meet Tuesday to settle pricing?", "Pat <pat@x.com>").single()
        assertEquals("Pat", m.from)
        assertEquals("Can we meet Tuesday to settle pricing?", m.text)
    }
}

class DateMentionsTest {
    private val tue = LocalDate.of(2026, 10, 20) // a Tuesday

    @Test fun weekdaysAndDates() {
        assertEquals("Tuesday", DateMentions.find("Let's talk on Tuesday", tue, null))
        assertEquals("Tuesday", DateMentions.find("see you tue", tue, null))
        assertNull(DateMentions.find("see you on Wednesday", tue, null))
        assertEquals("20 Oct", DateMentions.find("deadline is 20th October", tue, null))
        assertEquals("20 Oct", DateMentions.find("due Oct 20", tue, null))
        assertEquals("20 Oct", DateMentions.find("on 20/10 we meet", tue, null))
        assertEquals("20 Oct", DateMentions.find("on 10/20 we meet", tue, null))
        assertNull(DateMentions.find("on the 21st of October", tue, null))
    }

    @Test fun times() {
        assertEquals("13:30", DateMentions.find("at 13:30 sharp", null, "13:30"))
        assertEquals("13:30", DateMentions.find("at 1:30pm sharp", null, "13:30"))
        assertEquals("10:30", DateMentions.find("10:30 am works", null, "10:30"))
        assertNull(DateMentions.find("at 2:30pm", null, "13:30"))
    }
}

private fun vendorCall(): CalEvent {
    fun g(n: String, e: String, org: Boolean = false, self: Boolean = false) = Guest(n, e, Rsvp.Accepted, organizer = org, self = self)
    return CalEvent(
        "vendor", "13:30", "30m", "Vendor call: Northwind", Source.Outlook, "Microsoft Teams",
        description = "Purpose: Walk through the Northwind renewal quote and the open SLA questions.",
        startMs = utcMs("2026-10-20T12:30:00Z"), endMs = utcMs("2026-10-20T13:00:00Z"),
        guests = listOf(g("Jo Park", "jo.park@northwind.com", org = true), g("Lee Chen", "lee.chen@northwind.com"), g("Sam Aoki", "sam@acme.com"), g("You", "you@acme.com", self = true)),
    )
}

private fun itemOf(mail: app.plait.data.context.ParsedEmail, id: String = "c1", event: String = "vendor") =
    ContextItem(id, event, mail.subject, mail.from, mail.to, mail.dateMs, mail.body, 1L, "file", mail.attachments)

class ContextAnalyzerTest {
    private val ev = vendorCall()
    private val brief = BriefBuilder.build(ev)
    private val summary = ContextAnalyzer.analyze(ev, brief, listOf(itemOf(EmailParser.parseEml(EML))), LDN)

    @Test fun anEmailFromAGuestAboutTheTopicIsAStrongLink() {
        assertEquals(Strength.Strong, summary.strength)
        assertTrue(summary.relation.toString(), summary.relation.any { it.startsWith("Mentions Northwind") })
        assertTrue(summary.relation.toString(), summary.relation.any { it.contains("Jo Park") })
        assertTrue(summary.relation.toString(), "Mentions Tuesday" in summary.relation)
    }

    @Test fun picksOutTheDecisionAndTheDeadlineAsk() {
        val kinds = summary.highlights.associate { it.kind to it.text }
        assertTrue(summary.highlights.toString(), kinds.getValue(HighlightKind.Decision).contains("agreed the renewal"))
        assertTrue(summary.highlights.toString(), summary.highlights.any { it.text.contains("confirm the SLA credits") })
    }

    @Test fun listsWhatStillNeedsSettling() {
        assertTrue(summary.toSettle.toString(), summary.toSettle.any { it.text.contains("SLA credits") })
        assertTrue(summary.toSettle.none { it.kind == HighlightKind.Decision })
    }

    @Test fun theOverviewUsesTheRelevantSentencesAndDropsSignOffs() {
        assertTrue(summary.overview, summary.overview.contains("renewal") || summary.overview.contains("SLA"))
        assertFalse(summary.overview.contains("Best regards"))
        assertFalse(summary.overview.contains("Hi Sam"))
    }

    @Test fun anUnrelatedEmailIsAWeakLinkWithNoHighlights() {
        val spam = EmailParser.fromText("Our summer sale starts Monday! 20% off all shoes. Unsubscribe any time.", "Summer sale")
        val s = ContextAnalyzer.analyze(ev, brief, listOf(itemOf(spam, "c2")), LDN)
        assertEquals(Strength.Weak, s.strength)
        assertTrue(s.highlights.toString(), s.highlights.isEmpty())
    }

    @Test fun severalEmailsAreMergedAndRankedTogether() {
        val second = EmailParser.fromText(
            "We are worried the SLA response time of 4 hours is a blocker for Northwind. Lee will send the revised quote by Thursday.",
            "Northwind SLA",
        )
        val s = ContextAnalyzer.analyze(ev, brief, listOf(itemOf(EmailParser.parseEml(EML)), itemOf(second, "c2")), LDN)
        assertEquals(2, s.items.size)
        assertTrue(s.highlights.any { it.kind == HighlightKind.Risk })
        assertTrue(s.highlights.any { it.text.contains("revised quote") })
        assertTrue(s.highlights.size <= 8)
    }

    @Test fun classifiesSentences() {
        assertEquals(HighlightKind.Decision, ContextAnalyzer.kindOf("We've agreed to go with plan B."))
        assertEquals(HighlightKind.Risk, ContextAnalyzer.kindOf("This is a real blocker for the launch."))
        assertEquals(HighlightKind.Deadline, ContextAnalyzer.kindOf("Please send it by Friday."))
        assertEquals(HighlightKind.Ask, ContextAnalyzer.kindOf("Do you have the numbers?"))
        assertEquals(HighlightKind.Action, ContextAnalyzer.kindOf("I'll share the deck tomorrow."))
        assertEquals(HighlightKind.Number, ContextAnalyzer.kindOf("The budget is \$120k."))
        assertEquals(HighlightKind.Attachment, ContextAnalyzer.kindOf("See quote-v3.pdf for details."))
        assertNull(ContextAnalyzer.kindOf("The weather was nice yesterday."))
    }
}

class EventMatcherTest {
    @Test fun suggestsTheMeetingTheEmailIsAbout() {
        val vendor = vendorCall()
        val design = CalEvent("design", "10:30", "45m", "Design review: Q4 onboarding", Source.Google, "Meet",
            startMs = utcMs("2026-10-20T09:30:00Z"), endMs = utcMs("2026-10-20T10:15:00Z"),
            guests = listOf(Guest("Priya Raman", "priya@acme.com", Rsvp.Accepted, organizer = true), Guest("You", "you@acme.com", self = true)))
        val lunch = CalEvent("lunch", "12:00", "1h", "Lunch with Priya", Source.ICloud, "Sourdough",
            startMs = utcMs("2026-10-20T11:00:00Z"), endMs = utcMs("2026-10-20T12:00:00Z"))
        val mail = EmailParser.parseEml(EML)
        val ranked = EventMatcher.rank(mail, listOf(design, lunch, vendor), LDN, utcMs("2026-10-19T09:00:00Z"))
        assertEquals("vendor", ranked.first().event.id)
        assertTrue(ranked.first().reasons.toString(), ranked.first().reasons.any { it.contains("Jo Park") || it.startsWith("Mentions") })
        assertTrue(ranked.first().score > ranked.last().score)
    }

    @Test fun ignoresEventsFarInThePastOrFuture() {
        val old = vendorCall().copy(id = "old", startMs = utcMs("2026-08-01T12:00:00Z"), endMs = utcMs("2026-08-01T12:30:00Z"))
        val ranked = EventMatcher.rank(EmailParser.parseEml(EML), listOf(old), LDN, utcMs("2026-10-19T09:00:00Z"))
        assertTrue(ranked.isEmpty())
    }
}

class ContextStoreJsonTest {
    @Test fun roundTrips() {
        val items = listOf(
            ContextItem("a", "e1", "Subject, with \"quotes\"", "Jo <jo@x.com>", "sam@x.com", 123L, "Line one\nLine two", 456L, "shared", listOf("q.pdf"), "AI text"),
            ContextItem("b", "e2", "", "", "", null, "x", 1L, "pasted"),
        )
        assertEquals(items, ContextJson.decode(ContextJson.encode(items)))
        assertNotNull(ContextJson.decode(ContextJson.encode(items)).first().aiSummary)
    }
}
