package app.plait

import app.plait.data.CalEvent
import app.plait.data.CallLinkInput
import app.plait.data.Source
import app.plait.data.ZoneHint
import app.plait.data.device.MeetingDraft
import app.plait.data.google.GoogleApiException
import app.plait.data.google.GoogleEventBody
import app.plait.data.google.GoogleParser
import app.plait.data.share.ExportData
import app.plait.data.share.MeetingExport
import app.plait.data.share.TextBlock
import app.plait.data.share.VoiceBlock
import app.plait.i18n.AppLocale
import app.plait.i18n.Txt
import app.plait.reminder.LiveLogic
import app.plait.reminder.TaskReminders
import app.plait.share.ShareFormat
import app.plait.widget.WidgetModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Alerts, widgets, exports and calendar messages: English exactly as before, Arabic natural with isolated names and times. */
class AlertsShareArabicTest {
    private val ar = Locale.forLanguageTag("ar-u-nu-latn")
    private val utc = ZoneId.of("UTC")
    private fun iso(s: String) = "⁨$s⁩"
    private fun ms(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, utc).toInstant().toEpochMilli()
    private fun ev(id: String, title: String, start: Long, end: Long = start + 3_600_000L) =
        CalEvent(id, "", "", title, Source.Other, "", startMs = start, endMs = end)

    @After fun english() { AppLocale.locale = Locale.ENGLISH }

    @Test fun englishAlertsAreUnchanged() {
        assertEquals("In 10 min: Design review", LiveLogic.meetingTitle(10, "Design review"))
        assertEquals("In 1 min: Standup", LiveLogic.meetingTitle(1, "Standup"))
        assertEquals("Starting now: Standup", LiveLogic.meetingTitle(0, "Standup"))
        assertEquals("Retro has no notes yet.", LiveLogic.nudgeText(1, "Retro"))
        assertEquals("3 meetings have no notes yet, like Retro.", LiveLogic.nudgeText(3, "Retro"))
        assertEquals("Due 3:00 PM", LiveLogic.taskLine("3:00 PM", ""))
        assertEquals("Due 3:00 PM · Follow-up from Retro", LiveLogic.taskLine("3:00 PM", "Retro"))
        assertEquals("Snooze 5 min", Txt.plural(R.plurals.alert_snooze, 5))
        assertEquals("Meetings and notes", Txt.s(R.string.alert_channel_meetings))
        assertEquals("Live alerts", Txt.s(R.string.alert_channel_live))
        assertEquals("Text", ShareFormat.Text.label)
        assertEquals("PDF", ShareFormat.Pdf.label)
    }

    @Test fun arabicAlertsUsePluralsAndIsolateTheTitle() {
        AppLocale.locale = ar
        assertEquals("بعد دقيقة: ${iso("Design review")}", LiveLogic.meetingTitle(1, "Design review"))
        assertEquals("بعد دقيقتين: ${iso("Design review")}", LiveLogic.meetingTitle(2, "Design review"))
        assertEquals("بعد 10 دقائق: ${iso("Design review")}", LiveLogic.meetingTitle(10, "Design review"))
        assertEquals("بعد 15 دقيقة: ${iso("Design review")}", LiveLogic.meetingTitle(15, "Design review"))
        assertEquals("يبدأ الآن: ${iso("مراجعة")}", LiveLogic.meetingTitle(0, "مراجعة"))
        assertEquals("تأجيل 5 دقائق", Txt.plural(R.plurals.alert_snooze, 5))
        assertEquals("تأجيل 10 دقائق", Txt.plural(R.plurals.alert_snooze, 10))
        assertEquals("لا ملاحظات بعد لاجتماع ${iso("Retro")}.", LiveLogic.nudgeText(1, "Retro"))
        assertEquals("اجتماعان بلا ملاحظات بعد، منهما ${iso("Retro")}.", LiveLogic.nudgeText(2, "Retro"))
        assertEquals("11 اجتماعًا بلا ملاحظات بعد، منها ${iso("Retro")}.", LiveLogic.nudgeText(11, "Retro"))
        assertEquals("موعدها ${iso("3:00 م")} · متابعة من اجتماع ${iso("Retro")}", LiveLogic.taskLine("3:00 م", "Retro"))
        assertEquals("الاجتماعات والملاحظات", Txt.s(R.string.alert_channel_meetings))
        assertEquals("نص", ShareFormat.Text.label)
        assertEquals("Markdown", ShareFormat.Markdown.label)
    }

    @Test fun taskLabelInBothLanguages() {
        val now = ms(20, 8)
        assertEquals("Today 5:00 PM", TaskReminders.label(ms(20, 17), now, utc))
        AppLocale.locale = ar
        assertEquals("اليوم 5:00\u00A0م", TaskReminders.label(ms(20, 17), now, utc))
        assertEquals("غدًا 9:00\u00A0ص", TaskReminders.label(ms(21, 9), now, utc))
    }

    @Test fun widgetInArabic() {
        AppLocale.locale = ar
        assertEquals("1:30 – 2:30 م", WidgetModel.range("1:30 م", "2:30 م"))
        assertEquals("11:30 ص – 12:30 م", WidgetModel.range("11:30 ص", "12:30 م"))
        val m = WidgetModel.build(listOf(ev("a", "Standup", ms(21, 10)), ev("b", "B", ms(21, 11)), ev("c", "C", ms(21, 12))), ms(21, 9), utc, ar)
        assertEquals("ثم ${iso("B")} ${iso("11:00\u00A0ص")} · +1 أخرى", m.footer)
        val wide = WidgetModel.wideLines(m)
        assertEquals("3 اجتماعات", wide.countLabel)
        assertEquals("10:00 – 11:00\u00A0ص", wide.timeLine)
        val running = WidgetModel.build(listOf(ev("a", "Standup", ms(21, 8), ms(21, 10))), ms(21, 9), utc, ar)
        assertEquals("حتى ${iso("10:00\u00A0ص")}", WidgetModel.smallLines(running).timeLine)
        assertEquals("الآن", WidgetModel.smallLines(running).topEnd)
        assertEquals("الأخير اليوم", WidgetModel.wideLines(running).lastText)
        val clear = WidgetModel.build(emptyList(), ms(21, 9), utc, ar)
        assertEquals("يوم خالٍ", clear.title)
        assertEquals("لا مواعيد قادمة", clear.whenLine)
    }

    private val export = ExportData(
        "Vendor call", "Tue 20 Oct, 1:30 PM – 2:00 PM", "Teams", listOf("Jo", "Sam"), "Settle the quote.", listOf("Price"),
        "Goal: Lock the price.", listOf(VoiceBlock("Mon 3:00 PM", "ok", "We agreed.")), listOf(TextBlock("Mon 3:10 PM", "Photo", "Page")),
        listOf("Northwind renewal"), listOf("Send draft"),
    )

    @Test fun exportEnglishIsUnchanged() {
        val m = MeetingExport.markdown(export)
        for (s in listOf("With: Jo, Sam", "## Why we are meeting", "## Agenda", "## Prep", "## Voice note · Mon 3:00 PM", "Transcript:", "## Photo · Mon 3:10 PM", "## Emails", "## Follow-ups"))
            assertTrue(s, m.contains(s))
        val s = MeetingExport.sections(export)
        assertEquals(listOf("", "Why we are meeting", "Agenda", "Prep", "Voice note · Mon 3:00 PM", "Photo · Mon 3:10 PM", "Emails", "Follow-ups"), s.map { it.first })
        assertEquals("Tue 20 Oct, 1:30 PM – 2:00 PM · Teams\nWith: Jo, Sam", s[0].second)
    }

    @Test fun exportArabicHeadingsKeepTheMeetingsOwnWords() {
        AppLocale.locale = ar
        val m = MeetingExport.markdown(export.copy(written = listOf(TextBlock("الاثنين 3:10 م", "صورة", "صفحة"))))
        for (s in listOf("# Vendor call", "الحضور: Jo، Sam", "## سبب الاجتماع", "Settle the quote.", "## جدول الأعمال", "## التحضير",
            "## ملاحظة صوتية · Mon 3:00 PM", "التفريغ النصي:", "## صورة · الاثنين 3:10 م", "## رسائل البريد", "## المتابعات", "- [ ] Send draft"))
            assertTrue(s, m.contains(s))
        assertTrue(MeetingExport.plain(export).contains("• Send draft"))
        assertEquals("سبب الاجتماع", MeetingExport.sections(export)[1].first)
    }

    @Test fun calendarAndLinkMessages() {
        val dubai = CalEvent("e", "", "", "t", Source.Google, "", startMs = Instant.parse("2026-01-15T11:00:00Z").toEpochMilli(), endMs = 0, zone = "Asia/Dubai")
        val ny = ZoneId.of("America/New_York")
        assertEquals("1h 5m", GoogleParser.formatDuration(65))
        assertEquals("Google can't add a Meet link to this calendar.", GoogleEventBody.whyNoMeet(GoogleApiException(400, "x")))
        assertEquals("Google couldn't make a Meet link (error 500).", GoogleEventBody.whyNoMeet(GoogleApiException(500, "x")))
        assertEquals("Give the meeting a title.", MeetingDraft(" ", LocalDate.of(2026, 10, 20), LocalTime.NOON, 30).problem())
        assertEquals("Join: https://zoom.us/j/1", CallLinkInput.place("https://zoom.us/j/1", "Room 4", "").second)
        AppLocale.locale = ar
        assertEquals("${iso("3:00\u00A0م")} ${iso("Dubai")} · ${iso("6:00\u00A0ص")} بتوقيتك", ZoneHint.line(dubai, ny))
        assertEquals("1 س 5 د", GoogleParser.formatDuration(65))
        assertEquals("45 د", GoogleParser.formatDuration(45))
        assertEquals("لا يستطيع Google إضافة رابط Meet إلى هذا التقويم.", GoogleEventBody.whyNoMeet(GoogleApiException(400, "x")))
        assertEquals("يلزم عنوان للاجتماع.", MeetingDraft(" ", LocalDate.of(2026, 10, 20), LocalTime.NOON, 30).problem())
        assertEquals(CallLinkInput.Parsed.Bad("يُرجى لصق رابط المكالمة."), CallLinkInput.parse("  "))
        assertEquals(CallLinkInput.Parsed.Bad("يجب أن يبدأ الرابط بـ ⁦https://⁩."), CallLinkInput.parse("zoom.us/j/1"))
        assertEquals("رابط الانضمام: https://zoom.us/j/1", CallLinkInput.place("https://zoom.us/j/1", "Room 4", "").second)
    }
}
