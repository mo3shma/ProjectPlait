package app.plait

import app.plait.data.device.QuickAdd
import app.plait.i18n.AppLocale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Quick add reads Arabic sentences (in any app language) and Arabic-Indic digits; English is checked in QuickAddTest. */
class QuickAddArabicTest {
    private val monday = LocalDate.of(2026, 10, 19) // a Monday

    @After fun english() { AppLocale.locale = Locale.ENGLISH }

    @Test fun fullArabicSentence() {
        val r = QuickAdd.parse("غداء مع sam@acme.com الجمعة 1 م لمدة ساعة في مقهى نيرو", monday)
        assertEquals("غداء مع Sam", r.title)
        assertEquals(LocalDate.of(2026, 10, 23), r.date)
        assertEquals(LocalTime.of(13, 0), r.time)
        assertEquals(60, r.durationMin)
        assertEquals(listOf("sam@acme.com"), r.guests)
        assertEquals("مقهى نيرو", r.place)
    }

    @Test fun sameResultInTheArabicApp() {
        AppLocale.locale = Locale.forLanguageTag("ar-u-nu-latn")
        val r = QuickAdd.parse("اجتماع الفريق بكرة الساعة 3:30 لمدة 45 دقيقة", monday)
        assertEquals("اجتماع الفريق", r.title)
        assertEquals(monday.plusDays(1), r.date)
        assertEquals(LocalTime.of(15, 30), r.time)
        assertEquals(45, r.durationMin)
    }

    @Test fun weekdayWithNextAndMorning() {
        val r = QuickAdd.parse("مراجعة التصميم يوم الأحد القادم 10 صباحًا", monday)
        assertEquals("مراجعة التصميم", r.title)
        assertEquals(LocalDate.of(2026, 10, 25), r.date)
        assertEquals(LocalTime.of(10, 0), r.time)
        assertEquals(monday.plusWeeks(1), QuickAdd.parse("مراجعة الاثنين القادم", monday).date)
        assertEquals(LocalDate.of(2026, 10, 23), QuickAdd.parse("عشاء الجمعة الجاية", monday).date)
        assertEquals(LocalDate.of(2026, 10, 21), QuickAdd.parse("مكالمة يوم الأربعاء", monday).date)
    }

    @Test fun arabicIndicDigitsAreRead() {
        val r = QuickAdd.parse("مكالمة مع العميل ٣:٣٠ م اليوم", monday)
        assertEquals("مكالمة مع العميل", r.title)
        assertEquals(monday, r.date)
        assertEquals(LocalTime.of(15, 30), r.time)
        // In an English sentence too.
        assertEquals(LocalTime.of(15, 0), QuickAdd.parse("Sync tomorrow ٣pm", monday).time)
    }

    @Test fun rangesAndSpokenLengths() {
        val r = QuickAdd.parse("عرض تقديمي الخميس من 2 إلى 4 م", monday)
        assertEquals("عرض تقديمي", r.title)
        assertEquals(LocalDate.of(2026, 10, 22), r.date)
        assertEquals(LocalTime.of(14, 0), r.time)
        assertEquals(120, r.durationMin)
        val g = QuickAdd.parse("غداء بعد غد 1 ظهرًا لمدة ساعتين", monday)
        assertEquals("غداء", g.title)
        assertEquals(monday.plusDays(2), g.date)
        assertEquals(LocalTime.of(13, 0), g.time)
        assertEquals(120, g.durationMin)
        val h = QuickAdd.parse("اجتماع ساعة ونصف 9 مساءً", monday)
        assertEquals(90, h.durationMin); assertEquals(LocalTime.of(21, 0), h.time)
        assertEquals(30, QuickAdd.parse("مكالمة نصف ساعة", monday).durationMin)
        assertEquals(120, QuickAdd.parse("ورشة لمدة 2 ساعات", monday).durationMin)
    }

    @Test fun monthNamesAndPlaces() {
        val r = QuickAdd.parse("مقابلة 15 أكتوبر 11 ص عند المكتب الرئيسي", monday)
        assertEquals("مقابلة", r.title)
        assertEquals(LocalDate.of(2027, 10, 15), r.date)
        assertEquals(LocalTime.of(11, 0), r.time)
        assertEquals("المكتب الرئيسي", r.place)
        assertEquals(LocalDate.of(2026, 12, 3), QuickAdd.parse("حفلة 3 كانون الأول", monday).date)
    }

    @Test fun relativeDaysAndTonight() {
        assertEquals(monday.plusWeeks(1), QuickAdd.parse("مراجعة الأسبوع القادم", monday).date)
        assertEquals(monday.plusDays(3), QuickAdd.parse("متابعة بعد 3 أيام", monday).date)
        assertEquals(monday.plusWeeks(2), QuickAdd.parse("متابعة بعد أسبوعين", monday).date)
        val t = QuickAdd.parse("عشاء الليلة", monday)
        assertEquals(monday, t.date); assertEquals(LocalTime.of(19, 0), t.time)
        assertEquals(LocalTime.of(9, 0), QuickAdd.parse("اجتماع غدًا الساعة 9", monday).time)
        assertEquals(LocalTime.of(0, 0), QuickAdd.parse("إطلاق الساعة 12 ليلًا", monday).time)
        assertEquals(LocalTime.NOON, QuickAdd.parse("غداء عمل الظهر", monday).time)
    }

    @Test fun wordsThatOnlyLookLikeTimesStayInTheTitle() {
        val r = QuickAdd.parse("شراء ساعة جديدة", monday)
        assertEquals("شراء ساعة جديدة", r.title)
        assertNull(r.durationMin); assertNull(r.time); assertNull(r.date)
        // "غداء" (lunch) is not "غدا" (tomorrow).
        assertNull(QuickAdd.parse("غداء", monday).date)
    }
}
