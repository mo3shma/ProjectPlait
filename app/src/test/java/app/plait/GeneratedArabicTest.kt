package app.plait

import app.plait.data.Agenda
import app.plait.data.CalEvent
import app.plait.data.Guest
import app.plait.data.Rsvp
import app.plait.data.Source
import app.plait.data.brief.BriefBuilder
import app.plait.data.brief.DayAnalytics
import app.plait.data.brief.DayBriefBuilder
import app.plait.data.brief.MeetingKind
import app.plait.data.brief.Phase
import app.plait.data.quotes.Quotes
import app.plait.data.tasks.AttentionTasks
import app.plait.data.tasks.Due
import app.plait.data.week.WeekAnalytics
import app.plait.data.week.WeekBriefs
import app.plait.data.week.WeekPreviews
import app.plait.i18n.AppLocale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** The text Plait writes itself (day brief, recaps, week briefs, due labels), in Arabic. */
class GeneratedArabicTest {
    private val utc = ZoneId.of("UTC")
    private fun i(s: String) = "⁨$s⁩" // what Bidi.isolate adds in Arabic
    private fun at(s: String) = Instant.parse(s).toEpochMilli()
    private fun ev(id: String, title: String, start: String, mins: Long, guests: List<Guest> = emptyList()) =
        CalEvent(id, "", "", title, Source.Google, "", startMs = at(start), endMs = at(start) + mins * 60_000, guests = guests)

    @Before fun arabic() { AppLocale.locale = Locale.forLanguageTag("ar-u-nu-latn") }
    @After fun english() { AppLocale.locale = Locale.ENGLISH }

    @Test fun durationsUseEveryPluralForm() {
        val got = listOf(0L, 1, 2, 3, 11, 45, 60, 90, 120, 150, 180, 660, 6000).associateWith { DayAnalytics.span(it) }
        assertEquals(
            mapOf(
                0L to "0 دقيقة", 1L to "دقيقة", 2L to "دقيقتان", 3L to "3 دقائق", 11L to "11 دقيقة", 45L to "45 دقيقة",
                60L to "ساعة", 90L to "ساعة و30 دقيقة", 120L to "ساعتان", 150L to "ساعتان و30 دقيقة", 180L to "3 ساعات",
                660L to "11 ساعة", 6000L to "100 ساعة",
            ),
            got,
        )
    }

    @Test fun countdownOnTheUpNextCard() {
        val e = ev("a", "Sync", "2026-10-27T12:00:00Z", 30)
        assertEquals("الآن", Agenda.countdown(e, at("2026-10-27T12:00:00Z")))
        assertEquals("20 دقيقة", Agenda.countdown(e, at("2026-10-27T11:40:00Z")))
        assertEquals("ساعتان", Agenda.countdown(e, at("2026-10-27T10:00:00Z")))
        assertEquals("ساعة و20 دقيقة", Agenda.countdown(e, at("2026-10-27T10:40:00Z")))
    }

    private fun headline(n: Int): String {
        val events = List(n) { ev("e$it", "M$it", "2026-10-27T10:00:00Z", 1).let { e -> e.copy(startMs = e.startMs + it * 60_000L, endMs = e.endMs + it * 60_000L) } }
        return DayBriefBuilder.build(events, emptyMap(), emptyMap(), at("2026-10-27T08:00:00Z"), utc).headline
    }

    @Test fun meetingsAheadByCount() {
        assertEquals("يوم خالٍ.", headline(0))
        assertEquals("بقي اجتماع واحد.", headline(1))
        assertEquals("اجتماعان قادمان.", headline(2))
        assertEquals("3 اجتماعات قادمة.", headline(3))
        assertEquals("11 اجتماعًا قادمًا.", headline(11))
        assertEquals("100 اجتماع قادم.", headline(100))
    }

    @Test fun dayBriefSentences() {
        val a = ev("a", "Standup", "2026-10-27T09:00:00Z", 30)
        val b = ev("b", "Design review", "2026-10-27T16:00:00Z", 60)
        val soon = DayBriefBuilder.build(listOf(a, b), emptyMap(), emptyMap(), at("2026-10-27T08:55:00Z"), utc)
        assertEquals("يبدأ ${i("Standup")} بعد 5 دقائق.", soon.headline)
        assertEquals("يبدأ ${i("Standup")} بعد دقيقتين.", DayBriefBuilder.build(listOf(a, b), emptyMap(), emptyMap(), at("2026-10-27T08:58:00Z"), utc).headline)
        assertEquals(
            "التالي: ${i("Standup")} الساعة ${i("9:00\u00A0ص")}. لا اجتماعات بعد ${i("5:00\u00A0م")}. أطول استراحة ${i("9:30\u00A0ص")}–${i("4:00\u00A0م")} (6 ساعات و30 دقيقة).",
            soon.summary,
        )
        val now = DayBriefBuilder.build(listOf(a, b), emptyMap(), emptyMap(), at("2026-10-27T09:10:00Z"), utc)
        assertEquals("جارٍ الآن: ${i("Standup")}.", now.headline)
        val done = DayBriefBuilder.build(listOf(a, b), emptyMap(), emptyMap(), at("2026-10-27T20:00:00Z"), utc)
        assertEquals("انتهت اجتماعات اليوم.", done.headline)
        assertEquals("انتهى اجتماعان. لا شيء آخر اليوم.", done.summary)
    }

    @Test fun senseLine() {
        val a = ev("a", "Design review", "2026-10-27T16:10:00Z", 50)
        assertEquals("أولًا: ${i("Design review")} الساعة ${i("4:10\u00A0م")}، ويبقى على موعده 20 دقيقة.", DayAnalytics.sense(listOf(a), at("2026-10-27T15:50:00Z"), utc))
        assertEquals("جارٍ الآن: ${i("Design review")} حتى ${i("5:00\u00A0م")}.", DayAnalytics.sense(listOf(a), at("2026-10-27T16:30:00Z"), utc))
        assertEquals("انتهى آخر اجتماعاتك الساعة ${i("5:00\u00A0م")}.", DayAnalytics.sense(listOf(a), at("2026-10-27T18:00:00Z"), utc))
        assertEquals("لا شيء مجدول.", DayAnalytics.sense(emptyList(), 0, utc))
    }

    @Test fun phaseBoundariesAndGreetings() {
        fun phase(t: String) = Phase.at(at("2026-10-27T${t}:00Z"), utc)
        assertEquals(Phase.Morning, phase("11:59"))
        assertEquals(Phase.Afternoon, phase("12:00"))
        assertEquals(Phase.Afternoon, phase("16:59"))
        assertEquals(Phase.Evening, phase("17:00"))
        assertEquals(listOf("صباح الخير", "نهارك سعيد", "مساء الخير"), Phase.entries.map { it.greeting })
        assertEquals(listOf("الصباح", "منتصف النهار", "المساء"), Phase.entries.map { it.label })
    }

    @Test fun eveningRecap() {
        val day = listOf(ev("a", "Plan", "2026-10-27T09:00:00Z", 60), ev("b", "Review", "2026-10-27T11:00:00Z", 60), ev("c", "Retro", "2026-10-27T13:00:00Z", 60))
        val s = DayAnalytics.compute(day, emptyMap(), emptySet(), at("2026-10-27T18:00:00Z"), utc)
        assertEquals("وقتك في 3 اجتماعات: 3 ساعات. كان أطولها ${i("Plan")} (ساعة). 3 اجتماعات بلا ملاحظات بعد.", DayAnalytics.recap(s))
        val one = DayAnalytics.compute(day.take(1), emptyMap(), setOf("a"), at("2026-10-27T18:00:00Z"), utc)
        assertEquals("وقتك في اجتماع واحد: ساعة. لكل اجتماع منتهٍ ملاحظة.", DayAnalytics.recap(one))
        assertEquals("لا اجتماعات اليوم، فكان اليوم كله لك.", DayAnalytics.recap(DayAnalytics.compute(emptyList(), emptyMap(), emptySet(), 0, utc)))
    }

    @Test fun meetingBriefLines() {
        val guests = listOf(
            Guest("Me", "me@acme.com", Rsvp.Accepted, self = true), Guest("Priya Shah", "priya@acme.com", Rsvp.Accepted),
            Guest("Sam Lee", "sam@acme.com", Rsvp.Declined), Guest("Lee Wong", "lee@acme.com", Rsvp.Pending),
        )
        val e = ev("a", "Budget", "2026-10-27T09:00:00Z", 30, guests)
        val b = BriefBuilder.build(e)
        assertEquals(MeetingKind.General, b.kind)
        assertEquals("اجتماع", b.kind.label)
        assertEquals("مع ${i("Priya")}، ${i("Sam")} +1", b.withLine)
        assertEquals("وافق 2 من 4 · اعتذر 1 · 1 بانتظار الرد", b.rsvpLine)
        assertEquals("اجتماع مع 4 أشخاص. لا وصف في الدعوة.", b.about)
        assertEquals(listOf("4 أشخاص"), b.chips)
        assertEquals("مراجعة", MeetingKind.Review.label)
        assertEquals("الأسبوع القادم", Due.NextWeek.label)
    }

    @Test fun dueLabelsAndAge() {
        val now = at("2026-10-27T08:00:00Z") // a Tuesday
        fun due(s: String) = AttentionTasks.dueLabel(at(s), now, utc)
        assertEquals("مستحقة اليوم ${i("5:00\u00A0م")}", due("2026-10-27T17:00:00Z"))
        assertEquals("مستحقة غدًا ${i("9:00\u00A0ص")}", due("2026-10-28T09:00:00Z"))
        assertEquals("مستحقة الجمعة ${i("9:00\u00A0ص")}", due("2026-10-30T09:00:00Z"))
        assertEquals("مستحقة بعد ساعتين", due("2026-10-27T10:00:00Z"))
        assertEquals("متأخرة 3 ساعات", due("2026-10-27T05:00:00Z"))
        assertEquals("متأخرة يومين", due("2026-10-25T08:00:00Z"))
        assertEquals("متأخرة 11 يومًا", due("2026-10-16T08:00:00Z"))
        fun ago(s: String) = AttentionTasks.ago(at(s), now, utc)
        assertEquals("أمس", ago("2026-10-26T08:00:00Z"))
        assertEquals("منذ يومين", ago("2026-10-25T08:00:00Z"))
        assertEquals("منذ 3 أيام", ago("2026-10-24T08:00:00Z"))
        assertEquals("منذ 3 أسابيع", ago("2026-10-06T08:00:00Z"))
    }

    @Test fun englishDueLabelsUnchanged() {
        AppLocale.locale = Locale.ENGLISH
        val now = at("2026-10-27T08:00:00Z")
        assertEquals("Due today 5:00 PM", AttentionTasks.dueLabel(at("2026-10-27T17:00:00Z"), now, utc))
        assertEquals("Overdue by 2 days", AttentionTasks.dueLabel(at("2026-10-25T08:00:00Z"), now, utc))
        assertEquals("1h 20m", Agenda.countdown(ev("a", "Sync", "2026-10-27T12:00:00Z", 30), at("2026-10-27T10:40:00Z")))
        assertEquals("1 h 30 min", DayAnalytics.span(90))
    }

    @Test fun weekRecapAndPreview() {
        val work = (19..23).map { LocalDate.of(2026, 10, it) }
        val events = listOf(
            ev("a", "Design review", "2026-10-19T09:00:00Z", 60), ev("b", "Budget talk", "2026-10-20T10:00:00Z", 90),
            ev("c", "Roadmap sync", "2026-10-20T14:00:00Z", 60), ev("d", "Hiring loop", "2026-10-22T09:00:00Z", 30),
        )
        val stats = WeekAnalytics.compute(work, events, emptyMap(), setOf("a", "b"), 2, 3, at("2026-10-23T18:00:00Z"), utc)
        assertEquals(
            "انتهى أسبوع عملك، ${i("Sam")}. كان لديك 4 اجتماعات مجموعها 4 ساعات تقريبًا، وكان يوم الثلاثاء الأكثر ازدحامًا. " +
                "3 مهام لا تزال مفتوحة، واجتماعان بلا ملاحظات. يستحق ذلك نظرة قبل بداية الأسبوع.",
            WeekBriefs.recapRules(stats, "Sam"),
        )
        val next = (26..30).map { LocalDate.of(2026, 10, it) }
        assertEquals("الأسبوع القادم خالٍ تمامًا.", WeekBriefs.previewRules(WeekPreviews.compute(next, emptyList(), emptyMap(), utc), null, utc))
        val one = WeekPreviews.compute(next, listOf(ev("x", "Kickoff", "2026-10-27T09:00:00Z", 30)), emptyMap(), utc)
        assertEquals(
            "يبدو الأسبوع القادم خفيفًا: اجتماع واحد فقط، ${i("Kickoff")} يوم الثلاثاء الساعة ${i("9:00\u00A0ص")}. بقية الأسبوع بلا اجتماعات.",
            WeekBriefs.previewRules(one, null, utc),
        )
    }

    @Test fun arabicQuotes() {
        assertTrue(Quotes.arabic.size >= 30)
        assertEquals(Quotes.arabic.size, Quotes.arabic.map { it.text }.toSet().size)
        Quotes.arabic.forEach { assertTrue(it.text, it.text.length <= 120 && it.author.isNotBlank() && it.text.none { c -> c in 'a'..'z' || c in 'A'..'Z' }) }
        val day = 20_000L
        assertTrue(Quotes.arabic.contains(Quotes.pick(day, 0)))
        for (s in 0..2) for (start in 0 until 20) assertEquals(7, (0 until 7).map { Quotes.pick(day + start + it, s) }.toSet().size)
        AppLocale.locale = Locale.ENGLISH
        assertTrue(Quotes.all.contains(Quotes.pick(day, 0)))
    }
}
