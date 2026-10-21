package app.plait

import app.plait.ai.NoteAiPrompt
import app.plait.audio.Vad
import app.plait.audio.LiveWhisper
import app.plait.audio.SAMPLE_RATE
import app.plait.audio.WhisperTranscriber
import app.plait.data.CalEvent
import app.plait.data.NoteDigest
import app.plait.data.NoteLine
import app.plait.data.NoteJson
import app.plait.data.SavedNote
import app.plait.data.SpokenText
import app.plait.data.Source
import app.plait.data.brief.BriefBuilder
import app.plait.data.brief.DayBriefBuilder
import app.plait.data.device.MeetingDraft
import app.plait.data.device.MeetingGuests
import app.plait.data.device.NotesBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin

private val UTC = ZoneId.of("UTC")
private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
private fun meeting(id: String, title: String, from: String, to: String, desc: String? = null) =
    CalEvent(id, "", "", title, Source.Google, "", description = desc, startMs = at(from), endMs = at(to))

class DayBriefTest {
    private val now = at("2026-10-19T09:00:00Z")

    @Test fun emptyDayHasCalmHeadline() {
        val d = DayBriefBuilder.build(emptyList(), emptyMap(), emptyMap(), now, UTC)
        assertEquals("A clear day.", d.headline)
    }

    @Test fun countsMeetingsAheadAndNamesTheNext() {
        val a = meeting("a", "Standup", "2026-10-19T10:00:00Z", "2026-10-19T10:15:00Z")
        val b = meeting("b", "Design review", "2026-10-19T13:00:00Z", "2026-10-19T14:00:00Z")
        val day = listOf(a, b)
        val briefs = day.associate { it.id to BriefBuilder.build(it, nowMs = now) }
        val d = DayBriefBuilder.build(day, briefs, emptyMap(), now, UTC)
        assertEquals("Two meetings ahead.", d.headline)
        assertTrue(d.summary, d.summary.contains("Next is Standup at 10:00 AM"))
        assertTrue(d.summary, d.summary.contains("Free from 2:00 PM"))
        assertTrue(d.summary, d.summary.contains("Longest break 10:15 AM–1:00 PM"))
    }

    @Test fun imminentMeetingBecomesTheHeadline() {
        val a = meeting("a", "Vendor call", "2026-10-19T09:10:00Z", "2026-10-19T09:40:00Z")
        val d = DayBriefBuilder.build(listOf(a), mapOf("a" to BriefBuilder.build(a, nowMs = now)), emptyMap(), now, UTC)
        assertEquals("Vendor call starts in 10 min.", d.headline)
    }

    @Test fun finishedDaySaysWrap() {
        val a = meeting("a", "Standup", "2026-10-19T07:00:00Z", "2026-10-19T07:15:00Z")
        val d = DayBriefBuilder.build(listOf(a), mapOf("a" to BriefBuilder.build(a, nowMs = now)), emptyMap(), now, UTC)
        assertEquals("That's a wrap.", d.headline)
    }

    @Test fun emailFlagsAndMissingAgendaAreFlagged() {
        val ext = CalEvent(
            "x", "", "", "Vendor call", Source.Outlook, "", startMs = at("2026-10-19T15:00:00Z"), endMs = at("2026-10-19T15:30:00Z"),
            guests = listOf(
                app.plait.data.Guest("You", "me@acme.com", self = true),
                app.plait.data.Guest("Jo", "jo@northwind.com"),
            ),
        )
        val brief = BriefBuilder.build(ext, nowMs = now)
        val none = DayBriefBuilder.build(listOf(ext), mapOf("x" to brief), emptyMap(), now, UTC)
        assertEquals("No agenda", none.flags["x"])
        val mail = DayBriefBuilder.build(listOf(ext), mapOf("x" to brief), mapOf("x" to "2 emails · risk"), now, UTC)
        assertEquals("2 emails · risk", mail.flags["x"])
    }

    @Test fun backToBackRunIsCounted() {
        val e = listOf(
            meeting("1", "a", "2026-10-19T10:00:00Z", "2026-10-19T11:00:00Z"),
            meeting("2", "b", "2026-10-19T11:00:00Z", "2026-10-19T12:00:00Z"),
            meeting("3", "c", "2026-10-19T12:05:00Z", "2026-10-19T13:00:00Z"),
            meeting("4", "d", "2026-10-19T15:00:00Z", "2026-10-19T16:00:00Z"),
        )
        assertEquals(3, DayBriefBuilder.backToBack(e))
    }

    @Test fun dayStripBlocksStayInsideTheDay() {
        val a = meeting("a", "Standup", "2026-10-19T10:00:00Z", "2026-10-19T11:00:00Z")
        val d = DayBriefBuilder.build(listOf(a), mapOf("a" to BriefBuilder.build(a, nowMs = now)), emptyMap(), now, UTC)
        val block = d.load.single()
        assertTrue(block.from in 0f..1f && block.to in 0f..1f && block.to > block.from)
        assertEquals(8, d.loadStartHour)
        assertEquals(18, d.loadEndHour)
        // 10:00 is two hours into an 8:00 to 18:00 strip.
        assertEquals(0.2f, block.from, 0.001f)
    }
}

class MeetingDraftTest {
    private val draft = MeetingDraft("Planning", LocalDate.of(2026, 10, 20), LocalTime.of(9, 30), 45)

    @Test fun timesFollowTheZone() {
        assertEquals(at("2026-10-20T09:30:00Z"), draft.startMs(UTC))
        assertEquals(at("2026-10-20T10:15:00Z"), draft.endMs(UTC))
        assertEquals(at("2026-10-20T07:30:00Z"), draft.startMs(ZoneId.of("Europe/Paris")))
    }

    @Test fun allDayUsesUtcMidnights() {
        val d = draft.copy(allDay = true)
        assertEquals(at("2026-10-20T00:00:00Z"), d.allDayStartMs())
        assertEquals(at("2026-10-21T00:00:00Z"), d.allDayEndMs())
    }

    @Test fun problemsAreReported() {
        assertNull(draft.problem())
        assertNotNull(draft.copy(title = " ").problem())
        assertNotNull(draft.copy(durationMin = 0).problem())
        assertNotNull(draft.copy(guests = listOf("not-an-email")).problem())
        assertNull(draft.copy(durationMin = 0, allDay = true).problem())
    }

    @Test fun guestsAreParsedFromWhatPeopleType() {
        assertEquals(
            listOf("a@x.com", "b@y.org", "c@z.io"),
            MeetingGuests.parse("A@x.com, Bob <b@y.org>; c@z.io  a@x.com"),
        )
        assertTrue(MeetingGuests.parse("no addresses here").isEmpty())
        assertTrue(MeetingGuests.valid("jo.park@northwind.com"))
        assertFalse(MeetingGuests.valid("jo@"))
    }

    @Test fun notesBlockReplacesItselfInsteadOfStacking() {
        val first = NotesBlock.merge("Agenda:\n- budget", "Summary: one")
        assertTrue(first.startsWith("Agenda:"))
        val second = NotesBlock.merge(first, "Summary: two")
        assertEquals(1, Regex(Regex.escape(NotesBlock.START)).findAll(second).count())
        assertTrue(second.contains("Summary: two"))
        assertFalse(second.contains("Summary: one"))
        assertTrue(second.startsWith("Agenda:\n- budget"))
        assertEquals("Agenda:\n- budget", NotesBlock.strip(second))
        assertEquals("Summary: x".let { NotesBlock.START + "\n" + it + "\n" + NotesBlock.END }, NotesBlock.merge(null, "Summary: x"))
    }
}

class VadTest {
    private val rate = 16_000
    private fun silence(ms: Int, amp: Int = 40) = ShortArray(rate * ms / 1000) { (if (it % 2 == 0) amp else -amp).toShort() }
    private fun tone(ms: Int, amp: Int = 6000) = ShortArray(rate * ms / 1000) { (amp * sin(2 * PI * 220 * it / rate)).toInt().toShort() }

    @Test fun speechIsDetectedAndSilenceIsDropped() {
        val vad = Vad(rate)
        var forwarded = 0
        var starts = 0
        var ends = 0
        for (chunk in listOf(silence(600), tone(1000), silence(1000))) {
            val out = vad.feed(chunk)
            forwarded += out.audio.sumOf { it.size }
            if (out.started) starts++
            if (out.ended) ends++
        }
        assertEquals(1, starts)
        assertEquals(1, ends)
        // 1 s of tone + 300 ms before it + about 440 ms after it; far less than the 2.6 s fed.
        val ms = forwarded * 1000 / rate
        assertTrue("forwarded $ms ms", ms in 1500..2000)
        assertFalse(vad.speaking)
    }

    @Test fun firstSyllableIsKept() {
        val vad = Vad(rate)
        vad.feed(silence(600))
        val out = vad.feed(tone(400))
        assertTrue(out.started)
        // The audio sent at the start includes the quiet frames just before it.
        assertTrue(out.audio.sumOf { it.size } > rate * 400 / 1000)
    }

    @Test fun steadyNoiseIsNotSpeech() {
        val vad = Vad(rate)
        val rnd = java.util.Random(1)
        val noise = ShortArray(rate * 3) { (rnd.nextGaussian() * 600).toInt().toShort() }
        val out = vad.feed(noise)
        assertFalse(out.started)
        assertTrue(out.audio.isEmpty())
    }

    @Test fun shortPauseDoesNotSplitAPhrase() {
        val vad = Vad(rate)
        vad.feed(silence(600))
        var ends = 0
        for (chunk in listOf(tone(600), silence(250), tone(600), silence(800))) if (vad.feed(chunk).ended) ends++
        assertEquals(1, ends)
    }

    @Test fun speechOverLoudRoomStillStarts() {
        val vad = Vad(rate)
        val rnd = java.util.Random(2)
        val hum = ShortArray(rate) { (rnd.nextGaussian() * 300).toInt().toShort() }
        vad.feed(hum)
        val voice = ShortArray(rate) { (rnd.nextGaussian() * 300 + 9000 * sin(2 * PI * 200 * it / rate)).toInt().toShort() }
        assertTrue(vad.feed(voice).started)
    }
}

class SpokenTextTest {
    @Test fun fillersStuttersAndCasingAreFixed() {
        assertEquals("So I think we should move it to Friday.", SpokenText.tidy("um so i think we we should uh move it to friday"))
    }

    @Test fun questionsGetQuestionMarks() {
        assertEquals("Can you send me the numbers?", SpokenText.tidy("can you send me the numbers"))
        assertEquals("The budget is fine.", SpokenText.tidy("the budget is fine"))
    }

    @Test fun contractionsAndBlanks() {
        assertEquals("I'll send it.", SpokenText.tidy("i'll send it"))
        assertEquals("", SpokenText.tidy("um uh"))
        assertEquals("Done.", SpokenText.tidy("done."))
    }
}

class LivePhraseTest {
    private fun ms(n: Int) = ShortArray(SAMPLE_RATE * n / 1000)

    @Test fun aPausedPhraseIsHandedOverWhole() {
        val b = LiveWhisper.PhraseBuffer()
        assertNull(b.add(ms(500)))
        assertNull(b.add(ms(700)))
        assertEquals(ms(1200).size, b.take()!!.size)
        assertNull(b.take()) // taken once, now empty
    }

    @Test fun clicksAndCoughsAreTooShortToRead() {
        val b = LiveWhisper.PhraseBuffer()
        b.add(ms(300))
        assertNull(b.take())
        assertNull(b.take())
    }

    @Test fun nonStopSpeechIsHandedOverInPieces() {
        val b = LiveWhisper.PhraseBuffer()
        var out: ShortArray? = null
        repeat(13) { out = b.add(ms(1000)) ?: out }
        assertEquals(ms(12_000).size, out!!.size)
        assertEquals(ms(1000).size, b.take()!!.size)
    }

    @Test fun inventedPhrasesOverShortSpeechAreDropped() {
        assertFalse(WhisperTranscriber.worthKeeping("Thank you.", 1200))
        assertTrue(WhisperTranscriber.worthKeeping("Thank you.", 4000))
        assertFalse(WhisperTranscriber.worthKeeping("", 4000))
    }
}

class NoteAiTest {
    private fun note(ai: String? = null) = SavedNote(
        "n", "e", "Budget review", 1L, 5000, "n.wav",
        listOf(NoteLine(0, "We need to send the numbers by Friday."), NoteLine(4000, "Sam will own the follow-up.", 0.4f)),
        emptyList(), ai,
    )

    @Test fun promptCarriesTitleAndTranscript() {
        val p = NoteAiPrompt.build(note())
        assertTrue(p.contains("Budget review"))
        assertTrue(p.contains("send the numbers by Friday"))
        assertTrue(p.trimEnd().endsWith("or write - none"))
    }

    @Test fun answerIsParsedAndNoneIsIgnored() {
        val s = NoteAiPrompt.parse("Summary: Numbers go out Friday.\nDecisions:\n- none\nFollow-ups:\n- Sam sends the numbers\n- Priya books the room")
        assertEquals("Numbers go out Friday.", s.summary)
        assertTrue(s.decisions.isEmpty())
        assertEquals(listOf("Sam sends the numbers", "Priya books the room"), s.followUps)
        assertTrue(NoteAiPrompt.render(s).contains("Follow-ups:\n- Sam sends the numbers"))
    }

    @Test fun digestPrefersTheAiTextAndFallsBackToRules() {
        assertEquals("Summary: ok", NoteDigest.text(note("Summary: ok")))
        val rules = NoteDigest.text(note())
        assertTrue(rules, rules.contains("Summary:") || rules.contains("Follow-ups:"))
    }

    @Test fun noteJsonKeepsConfidenceAndAi() {
        val back = NoteJson.decode(NoteJson.encode(listOf(note("Summary: ok")))).single()
        assertEquals("Summary: ok", back.ai)
        assertEquals(0.4f, back.lines[1].conf, 0.001f)
        // Notes saved before these fields existed still load.
        val old = NoteJson.decode("""{"version":1,"notes":[{"id":"a","eventId":"e","title":"t","createdAt":1,"durationMs":2,"audioFile":"a.wav","lines":[{"atMs":0,"text":"hi"}],"bookmarksMs":[]}]}""").single()
        assertNull(old.ai)
        assertEquals(1f, old.lines[0].conf, 0f)
    }
}

class BackupTest {
    private fun note(id: String) = SavedNote(id, "e", "t", 1L, 2000, "$id.wav", listOf(NoteLine(0, "hello")), emptyList(), "Summary: x")

    @Test fun roundTripKeepsEverythingAndMedia() {
        val dir = java.nio.file.Files.createTempDirectory("plait-backup").toFile()
        try {
            val audio = java.io.File(dir, "n1.wav").also { it.writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val data = app.plait.data.backup.BackupData(
                9L, "Pixel",
                app.plait.data.backup.BackupSettings(
                    "Sam", listOf(app.plait.data.ics.Feed("https://x.org/a.ics", "Work", Source.Outlook)), setOf("me@work.com"), true, "Arabic",
                ),
                listOf(note("n1")),
                listOf(app.plait.data.memo.Memo("m1", "e", app.plait.data.memo.MemoKind.Text, 3L, "remember")),
                listOf(app.plait.data.context.ContextItem("c1", "e", "Subj", "a@x.com", "", null, "body", 4L, "pasted")),
            )
            val out = java.io.ByteArrayOutputStream()
            app.plait.data.backup.Backup.write(out, data, listOf("n1.wav" to audio))
            val media = java.io.File(dir, "unpacked")
            val back = app.plait.data.backup.Backup.read(out.toByteArray().inputStream(), media)
            assertEquals("Pixel", back.deviceLabel)
            assertEquals("Sam", back.settings.profileName)
            assertEquals(setOf("me@work.com"), back.settings.enabledAccounts)
            assertEquals(true, back.settings.syncNotes)
            assertEquals("https://x.org/a.ics", back.settings.feeds.single().url)
            assertEquals("n1", back.notes.single().id)
            assertEquals("Summary: x", back.notes.single().ai)
            assertEquals("remember", back.memos.single().text)
            assertEquals("body", back.emails.single().body)
            assertEquals(listOf<Byte>(1, 2, 3, 4), java.io.File(media, "n1.wav").readBytes().toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun otherFilesAreRejectedNicely() {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z -> z.putNextEntry(java.util.zip.ZipEntry("hello.txt")); z.write(1); z.closeEntry() }
        val ex = try { app.plait.data.backup.Backup.read(out.toByteArray().inputStream(), java.io.File("/tmp/plait-x")); null } catch (e: app.plait.data.backup.Backup.Unreadable) { e }
        assertNotNull(ex)
        val junk = try { app.plait.data.backup.Backup.read("not a zip".byteInputStream(), java.io.File("/tmp/plait-y")); null } catch (e: app.plait.data.backup.Backup.Unreadable) { e }
        assertNotNull(junk)
    }

    @Test fun mediaCannotEscapeTheFolder() {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("manifest.json")); z.write("""{"version":1}""".toByteArray()); z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("media/../../evil.txt")); z.write(7); z.closeEntry()
        }
        val dir = java.nio.file.Files.createTempDirectory("plait-zip").toFile()
        try {
            app.plait.data.backup.Backup.read(out.toByteArray().inputStream(), java.io.File(dir, "m"))
            assertTrue(java.io.File(dir, "m/evil.txt").exists())
            assertFalse(java.io.File(dir, "evil.txt").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun onlyNewItemsAreImported() {
        val got = app.plait.data.backup.Backup.fresh(listOf(note("a"), note("b"), note("b")), setOf("a")) { it.id }
        assertEquals(listOf("b"), got.map { it.id })
    }
}

class AccountAndTimeTest {
    private fun ev(id: String, account: String, cal: Long) =
        CalEvent(id, "", "", id, Source.Google, "", startMs = 1, endMs = 2, calId = cal, account = account)

    @Test fun accountsAndCalendarsFilterEvents() {
        val e = listOf(ev("a", "work@x.com", 1), ev("b", "me@gmail.com", 2), ev("c", "work@x.com", 3))
        assertEquals(3, app.plait.data.AccountFilter.visible(e, null, emptySet()).size)
        assertEquals(listOf("a", "c"), app.plait.data.AccountFilter.visible(e, setOf("work@x.com"), emptySet()).map { it.id })
        assertEquals(listOf("a"), app.plait.data.AccountFilter.visible(e, setOf("work@x.com"), setOf(3L)).map { it.id })
        assertEquals(listOf("b"), app.plait.data.AccountFilter.visible(e, null, setOf(1L, 3L)).map { it.id })
    }

    @Test fun noisyCalendarsStartHidden() {
        fun cal(id: Long, name: String) = app.plait.data.device.DeviceCalendar.DeviceCal(id, name, "me@x.com", "com.google", 0, false, false)
        val hidden = app.plait.data.AccountFilter.defaultHidden(listOf(cal(1, "Work"), cal(2, "Holidays in United Kingdom"), cal(3, "Birthdays")))
        assertEquals(setOf(2L, 3L), hidden)
    }

    @Test fun everyTimeIsTwelveHour() {
        assertEquals("3:30 PM", app.plait.data.TimeFmt.clock(15 * 60 + 30))
        assertEquals("12:05 AM", app.plait.data.TimeFmt.clock(5))
        assertEquals("12:00 PM", app.plait.data.TimeFmt.clock(12 * 60))
        assertEquals("9 AM", app.plait.data.TimeFmt.hour(9))
        assertEquals("12 AM", app.plait.data.TimeFmt.hour(24))
        assertEquals("1 PM", app.plait.data.TimeFmt.hour(13))
        assertEquals("3:30 PM", app.plait.data.TimeFmt.legacy("15:30"))
        assertEquals("All day", app.plait.data.TimeFmt.legacy("All day"))
        assertEquals("3:30 PM", app.plait.data.TimeFmt.legacy("3:30 PM"))
    }
}

class WavSegmentsTest {
    private val rate = 16_000
    private fun silence(ms: Int) = ShortArray(rate * ms / 1000) { (if (it % 2 == 0) 30 else -30).toShort() }
    private fun tone(ms: Int) = ShortArray(rate * ms / 1000) { (6000 * kotlin.math.sin(2 * Math.PI * 220 * it / rate)).toInt().toShort() }

    private fun wav(vararg parts: ShortArray): java.io.File {
        val f = java.io.File.createTempFile("plait", ".wav")
        val data = parts.flatMap { it.toList() }.toShortArray()
        val bytes = ByteArray(data.size * 2)
        app.plait.audio.Wav.toLittleEndian(data, data.size, bytes)
        f.outputStream().use { it.write(app.plait.audio.Wav.header(bytes.size.toLong())); it.write(bytes) }
        return f
    }

    @Test fun findsTheTalkingPartsOfARecording() {
        val f = wav(silence(800), tone(1500), silence(1500), tone(1000), silence(600))
        try {
            val segs = app.plait.audio.WavSegments.find(f)
            assertEquals(2, segs.size)
            assertTrue("first starts near 0.8 s: ${segs[0]}", segs[0].startMs in 100..1000)
            assertTrue(segs[0].lengthMs in 1500..3300)
            assertTrue(segs[1].startMs > 3000)
        } finally { f.delete() }
    }

    @Test fun shortPausesJoinAndLongSpeechIsCut() {
        val joined = app.plait.audio.WavSegments.chunk(
            listOf(app.plait.audio.Segment(1000, 3000), app.plait.audio.Segment(3300, 5000)), 25_000, 200, 500, 10_000,
        )
        assertEquals(1, joined.size)
        val cut = app.plait.audio.WavSegments.chunk(listOf(app.plait.audio.Segment(0, 60_000)), 25_000, 0, 700, 60_000)
        assertEquals(3, cut.size)
        assertTrue(cut.all { it.lengthMs <= 25_000 })
    }

    @Test fun readingASegmentGivesFloatsInRange() {
        val f = wav(silence(500), tone(1000))
        try {
            val s = app.plait.audio.WavSegments.read(f, app.plait.audio.Segment(500, 1500))
            assertEquals(rate, s.size)
            assertTrue(s.all { it in -1f..1f })
            assertTrue(s.maxOrNull()!! > 0.1f)
        } finally { f.delete() }
    }
}
