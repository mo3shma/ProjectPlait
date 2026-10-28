package app.plait

import app.plait.data.Guest
import app.plait.data.NoteLine
import app.plait.data.Source
import app.plait.data.TimeFmt
import app.plait.data.Transcript
import app.plait.data.memo.Checklist
import app.plait.data.memo.OcrLang
import app.plait.data.prep.FreeTime
import app.plait.data.search.Doc
import app.plait.data.search.DocKind
import app.plait.data.search.SearchIndex
import app.plait.data.week.WeekBriefs
import app.plait.data.week.WorkWeek
import app.plait.i18n.AppLocale
import app.plait.i18n.Bidi
import app.plait.ui.screens.hedged
import app.plait.ui.screens.ruleGuess
import app.plait.widget.WidgetModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Fixes found by looking at the Arabic app on a phone; English must read exactly as before. */
class ArabicScreenFixesTest {
    private val ar = Locale.forLanguageTag("ar-u-nu-latn")
    private val nb = ' '

    @After fun english() { AppLocale.locale = Locale.ENGLISH }

    @Test fun arabicTimesKeepTheirMarkOnTheLineAndUseTheArabicComma() {
        assertEquals("3:30 PM", TimeFmt.clock(LocalTime.of(15, 30)))
        AppLocale.locale = ar
        assertEquals("3:30${nb}م", TimeFmt.clock(LocalTime.of(15, 30)))
        val stamp = TimeFmt.fmt("EEE d MMM, h:mm a").format(ZonedDateTime.of(2026, 10, 27, 21, 20, 0, 0, ZoneId.of("UTC")))
        assertTrue(stamp, stamp.endsWith("،" + " 9:20${nb}م") && !stamp.contains(','))
        // The widget still drops the first of two equal marks.
        assertEquals("1:30 – 2:30${nb}م", WidgetModel.range("1:30${nb}م", "2:30${nb}م"))
    }

    @Test fun mixedPhrasesKeepTheirOwnDirection() {
        assertEquals("Hello there. Fine.", Bidi.join(listOf("Hello there.", "Fine.")))
        assertEquals("مرحبا. أهلا.", Bidi.join(listOf("مرحبا.", "أهلا.")))
        assertEquals("⁨مرحبا.⁩ ⁨Ahmed: up 12%.⁩", Bidi.join(listOf("مرحبا.", "Ahmed: up 12%.")))
        val p = Transcript.paragraphs(listOf(NoteLine(0, "مرحبا بالجميع."), NoteLine(1000, "the numbers look good.")))
        assertEquals("⁨مرحبا بالجميع.⁩ ⁨The numbers look good.⁩", p.single().text)
        assertEquals("Hi all. Numbers look good.", Transcript.paragraphs(listOf(NoteLine(0, "hi all."), NoteLine(900, "numbers look good."))).single().text)
    }

    @Test fun checklistWordsAreTranslated() {
        assertEquals("Groceries · 1 of 2 done", Checklist.heading("Groceries", "[x] Milk\n[ ] Eggs"))
        // An Arabic title in the English app keeps "1 of 2 done" in reading order.
        assertEquals("⁨قبل الاجتماع⁩ · ⁨1 of 2 done⁩", Checklist.heading("قبل الاجتماع", "[x] Milk\n[ ] Eggs"))
        AppLocale.locale = ar
        assertEquals("قائمة مهام", Checklist.label(null))
        assertEquals("قبل الاجتماع · أُنجز 1 من 2", Checklist.heading("قبل الاجتماع", "[x] Milk\n[ ] Eggs"))
        assertEquals("⁨Groceries⁩ · ⁨أُنجز 1 من 2⁩", Checklist.heading("Groceries", "[x] Milk\n[ ] Eggs"))
    }

    @Test fun enumLabelsFollowTheAppLanguage() {
        assertEquals("Phone", Source.Other.label)
        assertEquals("Guest", Guest("").label)
        assertEquals("Meeting", DocKind.Event.label)
        assertEquals("English / Latin", OcrLang.Latin.label)
        AppLocale.locale = ar
        assertEquals("الهاتف", Source.Other.label)
        assertEquals("ه", Source.Other.initial)
        assertEquals("Google", Source.Google.label)
        assertEquals("مدعو", Guest("").label)
        assertEquals("متابعة", DocKind.Followup.label)
        assertEquals("تلقائي", OcrLang.Auto.label)
    }

    @Test fun titleGuessKeepsTheTitleAsWrittenInArabic() {
        assertEquals("Likely about dashboard review.", ruleGuess("Dashboard review"))
        assertTrue(hedged("Likely a check-in on the dashboard"))
        AppLocale.locale = ar
        assertEquals("على الأرجح بشأن ⁨Dashboard review⁩.", ruleGuess("Dashboard review"))
        assertTrue(hedged("على الأرجح مراجعة للوحة المؤشرات"))
        assertFalse(hedged("Likely a check-in on the dashboard"))
    }

    @Test fun freeSlotsFollowTheWorkWeek() {
        val utc = ZoneId.of("UTC")
        val thursday = ZonedDateTime.of(2026, 10, 29, 8, 0, 0, 0, utc).toInstant().toEpochMilli()
        val days = { slots: List<app.plait.data.prep.Slot> -> slots.map { java.time.Instant.ofEpochMilli(it.startMs).atZone(utc).dayOfWeek }.toSet() }
        // Default Monday to Friday: Thursday and Friday, then Monday.
        assertEquals(setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.MONDAY), days(FreeTime.find(emptyList(), thursday, 5, 30, zone = utc, perDay = 1)))
        // Sunday to Thursday (Settings, Work week): Friday and Saturday are the weekend.
        val sunToThu = days(FreeTime.find(emptyList(), thursday, 5, 30, zone = utc, perDay = 1, workDay = { WorkWeek.isWorkDay(it, DayOfWeek.SUNDAY) }))
        assertEquals(setOf(DayOfWeek.THURSDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY), sunToThu)
    }

    @Test fun arabicSearchIgnoresClitics() {
        val docs = listOf(Doc("1", DocKind.Memo, "", "", "نراجع الميزانيات غدًا"), Doc("2", DocKind.Memo, "", "", "Lunch plans"), Doc("3", DocKind.Memo, "", "", "تقرير ۲۰۲۶"))
        val index = SearchIndex(docs)
        assertEquals("1", index.search("وبالميزانية").first().doc.id)
        assertEquals("3", index.search("2026").first().doc.id)
    }

    @Test fun arabicWeekTextThatContradictsTheFactsIsDropped() {
        assertNull(WeekBriefs.verify("لا توجد اجتماعات هذا الأسبوع.", "MEETINGS: 4", 4, 70, 4))
        assertNull(WeekBriefs.verify("أسبوع مزدحم.", "MEETINGS: 0", 0, 70, 4))
    }
}
