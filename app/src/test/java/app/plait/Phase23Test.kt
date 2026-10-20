package app.plait

import app.plait.data.CalEvent
import app.plait.data.Source
import app.plait.data.brief.BriefBuilder
import app.plait.data.crypto.Crypt
import app.plait.data.prep.FreeTime
import app.plait.data.prep.Prep
import app.plait.data.prep.PrepInput
import app.plait.data.prep.SinceLast
import app.plait.data.share.ExportData
import app.plait.data.share.MeetingExport
import app.plait.data.share.TextBlock
import app.plait.data.share.VoiceBlock
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private val U = ZoneId.of("UTC")
private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

class PrepTest {
    private val ev = CalEvent("e", "", "", "Vendor call", Source.Outlook, "", description = "Purpose: Settle the renewal quote.", startMs = ms("2026-10-20T13:30:00Z"), endMs = ms("2026-10-20T14:00:00Z"))
    private val brief = BriefBuilder.build(ev)
    private val input = PrepInput(ev, brief, "They agreed to hold the price", listOf("Send SLA draft"), listOf("Risk: the timeline is tight"))

    @Test fun promptCarriesTheFactsAndTheFormat() {
        val p = Prep.prompt(input, U, 1500)
        assertTrue(p.contains("Vendor call")); assertTrue(p.contains("Send SLA draft")); assertTrue(p.contains("timeline is tight"))
        assertTrue(p.trimEnd().endsWith("up to 3 short questions to raise"))
        assertTrue(p.length < 1700)
    }

    @Test fun answersAreParsedAndRenderedBack() {
        val s = Prep.parse("Goal: Lock the price.\nRemember:\n- They hold the price\n- SLA is open\nAsk:\n- Who signs?\n- none")
        assertEquals("Lock the price.", s.goal)
        assertEquals(2, s.remember.size)
        assertEquals(listOf("Who signs?"), s.ask)
        assertEquals(s.copy(raw = ""), Prep.parse(Prep.render(s)).copy(raw = ""))
        assertTrue(Prep.parse("").isEmpty)
    }

    @Test fun rulesBuildAPlanWithoutAnyAi() {
        val s = Prep.rules(input)
        assertEquals("Settle the renewal quote.", s.goal)
        assertTrue(s.remember.first().startsWith("Last time:"))
        assertEquals(listOf("Is this done: Send SLA draft"), s.ask)
    }
}

class SinceLastTest {
    private fun note(id: String, event: String, title: String, at: Long, ai: String? = null, line: String = "We need to send the draft by Friday.") =
        app.plait.data.SavedNote(id, event, title, at, 1000, "$id.wav", listOf(app.plait.data.NoteLine(0, line)), emptyList(), ai)

    @Test fun findsOpenFollowUpsAndDecisionsFromTheSeries() {
        val ev = CalEvent("now", "", "", "Weekly sync 21", Source.Google, "", startMs = 5_000)
        val older = note("a", "old", "Weekly sync 14", 1_000, "Summary: x\nDecisions:\n- Keep the date\nFollow-ups:\n- Send the draft")
        val other = note("b", "x", "Lunch", 2_000)
        val got = SinceLast.compute(ev, listOf(older, other), emptyList()) { true }!!
        assertEquals(listOf("Keep the date"), got.decisions)
        assertEquals(listOf("Send the draft"), got.stillOpen.map { it.text })
        assertTrue(SinceLast.compute(ev, listOf(older), emptyList()) { false }!!.stillOpen.isEmpty())
    }

    @Test fun noEarlierMeetingMeansNothingToShow() {
        val ev = CalEvent("now", "", "", "Brand new", Source.Google, "", startMs = 5_000)
        assertNull(SinceLast.compute(ev, listOf(note("a", "old", "Other", 1_000)), emptyList()) { true })
    }
}

class FreeTimeTest {
    private val monday9 = ms("2026-10-19T09:00:00Z") // a Monday

    @Test fun skipsBusyTimeAndWeekends() {
        val busy = listOf(ms("2026-10-19T09:00:00Z") to ms("2026-10-19T11:00:00Z"), ms("2026-10-19T13:00:00Z") to ms("2026-10-19T17:00:00Z"))
        val slots = FreeTime.find(busy, monday9, days = 7, durationMin = 60, zone = U, perDay = 2, limit = 6)
        assertEquals(ms("2026-10-19T11:00:00Z"), slots.first().startMs)
        assertTrue(slots.none { it.startMs in ms("2026-10-19T13:00:00Z") until ms("2026-10-19T17:00:00Z") })
        assertTrue(slots.none { java.time.Instant.ofEpochMilli(it.startMs).atZone(U).dayOfWeek.value >= 6 })
        assertTrue(slots.size <= 6)
    }

    @Test fun bufferKeepsDistance() {
        val busy = listOf(ms("2026-10-19T10:00:00Z") to ms("2026-10-19T11:00:00Z"))
        val slots = FreeTime.find(busy, monday9, days = 1, durationMin = 30, zone = U, bufferMin = 30, perDay = 5)
        assertTrue(slots.none { it.startMs in ms("2026-10-19T09:30:00Z") until ms("2026-10-19T11:30:00Z") })
    }

    @Test fun neverOffersThePast() {
        val slots = FreeTime.find(emptyList(), ms("2026-10-19T12:10:00Z"), days = 1, durationMin = 30, zone = U)
        assertTrue(slots.all { it.startMs >= ms("2026-10-19T12:10:00Z") })
    }

    @Test fun shareTextListsTwelveHourOptions() {
        val t = FreeTime.shareText("Vendor call", listOf(app.plait.data.prep.Slot(ms("2026-10-20T14:00:00Z"), ms("2026-10-20T14:30:00Z"))), U)
        assertTrue(t, t.contains("Tue 20 Oct, 2:00 PM – 2:30 PM"))
        assertTrue(t.contains("Vendor call"))
    }
}

class ExportTest {
    private val d = ExportData(
        "Vendor call", "Tue 20 Oct, 1:30 PM – 2:00 PM", "Teams", listOf("Jo", "Sam"), "Settle the quote.", listOf("Price", "SLA"),
        "Goal: Lock the price.", listOf(VoiceBlock("Mon 3:00 PM", "Summary: ok", "We agreed.")), listOf(TextBlock("Mon 3:10 PM", "Photo", "Agenda page")),
        listOf("Northwind renewal"), listOf("Send draft"),
    )

    @Test fun markdownHasEverySection() {
        val m = MeetingExport.markdown(d)
        for (s in listOf("# Vendor call", "## Agenda", "## Prep", "## Voice note", "## Photo", "## Emails", "- [ ] Send draft")) assertTrue(s, m.contains(s))
    }

    @Test fun plainTextHasNoMarkup() {
        val p = MeetingExport.plain(d)
        assertFalse(p.contains("##")); assertFalse(p.contains("**")); assertTrue(p.contains("• Send draft"))
    }

    @Test fun pdfSectionsSkipEmptyParts() {
        val s = MeetingExport.sections(ExportData("T", "now"))
        assertEquals(1, s.size)
    }
}

class CryptTest {
    private val pass = "correct horse".toCharArray()

    @Test fun roundTripsAcrossManyChunks() {
        val data = ByteArray(300_000) { (it * 31).toByte() }
        val sealed = java.io.ByteArrayOutputStream().also { bo -> Crypt.encrypt(bo, pass, chunk = 64_000, rounds = 2_000).use { it.write(data) } }.toByteArray()
        assertTrue(Crypt.isEncrypted(sealed))
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("\u0000\u0001\u0002"))
        val ins = sealed.inputStream(); ins.skip(Crypt.MAGIC.size.toLong())
        assertArrayEquals(data, Crypt.decrypt(ins, pass).readBytes())
    }

    @Test fun emptyInputAndExactChunkBoundaryWork() {
        assertArrayEquals(ByteArray(0), Crypt.decryptBytes(Crypt.encryptBytes(ByteArray(0), pass, 2_000), pass))
        val sealed = java.io.ByteArrayOutputStream().also { bo -> Crypt.encrypt(bo, pass, chunk = 1000, rounds = 2_000).use { it.write(ByteArray(3000) { 7 }) } }.toByteArray()
        val ins = sealed.inputStream(); ins.skip(Crypt.MAGIC.size.toLong())
        assertEquals(3000, Crypt.decrypt(ins, pass).readBytes().size)
    }

    @Test fun wrongPassphraseIsRejected() {
        val sealed = Crypt.encryptBytes("secret".toByteArray(), pass, 2_000)
        val e = try { Crypt.decryptBytes(sealed, "nope".toCharArray()); null } catch (e: Crypt.BadPassphrase) { e }
        assertTrue(e != null)
    }

    @Test fun tamperingAndTruncationAreDetected() {
        val data = ByteArray(10_000) { it.toByte() }
        val sealed = java.io.ByteArrayOutputStream().also { bo -> Crypt.encrypt(bo, pass, chunk = 4_000, rounds = 2_000).use { it.write(data) } }.toByteArray()
        val flipped = sealed.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 1).toByte() }
        assertTrue(try { Crypt.decryptBytes(flipped, pass); false } catch (e: Crypt.BadPassphrase) { true })
        val cut = sealed.copyOf(sealed.size - 4_050) // drops the last chunk entirely
        assertTrue(try { Crypt.decryptBytes(cut, pass); false } catch (e: Crypt.BadPassphrase) { true })
    }

    @Test fun plainFilesAreNotMistakenForEncrypted() {
        assertFalse(Crypt.isEncrypted(byteArrayOf(0x50, 0x4B, 3, 4)))
    }
}

class FocusTimeTest {
    private val monday = ms("2026-10-19T08:00:00Z")

    @Test fun picksTheLongestFreeStretchAndCapsIt() {
        val busy = listOf(ms("2026-10-19T09:00:00Z") to ms("2026-10-19T10:00:00Z"), ms("2026-10-19T15:30:00Z") to ms("2026-10-19T16:00:00Z"))
        val s = app.plait.data.prep.FocusTime.suggest(busy, monday, days = 1, zone = U).single()
        assertEquals(ms("2026-10-19T10:00:00Z"), s.startMs)
        assertEquals(ms("2026-10-19T13:00:00Z"), s.endMs) // capped at 3 hours
    }

    @Test fun noStretchLongEnoughMeansNoSuggestion() {
        val busy = listOf(ms("2026-10-19T09:00:00Z") to ms("2026-10-19T11:00:00Z"), ms("2026-10-19T12:00:00Z") to ms("2026-10-19T14:00:00Z"), ms("2026-10-19T15:00:00Z") to ms("2026-10-19T17:00:00Z"))
        assertTrue(app.plait.data.prep.FocusTime.suggest(busy, monday, days = 1, zone = U).isEmpty())
    }

    @Test fun weekendsAndThePastAreSkipped() {
        val sat = ms("2026-10-24T08:00:00Z")
        assertTrue(app.plait.data.prep.FocusTime.suggest(emptyList(), sat, days = 2, zone = U).isEmpty())
        val late = ms("2026-10-19T16:00:00Z")
        assertTrue(app.plait.data.prep.FocusTime.suggest(emptyList(), late, days = 1, zone = U).isEmpty())
    }

    @Test fun titlesAndLengths() {
        assertEquals("To do: Send the draft", app.plait.data.prep.FocusTime.todoTitle("Send the draft."))
        assertTrue(app.plait.data.prep.FocusTime.todoTitle("word ".repeat(30)).endsWith("…"))
        assertEquals("2 h", app.plait.data.prep.FocusTime.lengthLabel(app.plait.data.prep.Slot(0, 2 * 3_600_000)))
        assertEquals("1 h 30 min", app.plait.data.prep.FocusTime.lengthLabel(app.plait.data.prep.Slot(0, 90 * 60_000)))
    }
}
