package app.plait

import app.plait.data.followups.Followups
import app.plait.data.memo.Checklist
import app.plait.data.tasks.Due
import app.plait.data.tasks.TaskSuggester
import app.plait.data.tasks.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class ChecklistDueTest {
    private val zone = ZoneId.systemDefault()
    private fun ms(d: Int, h: Int, mi: Int = 0, mo: Int = 10) = LocalDateTime.of(2026, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    private fun parse(t: String) = Checklist.parse(t, zone)
    private fun format(i: List<Checklist.Item>) = Checklist.format(i, zone)

    @Test fun roundTripWithAndWithoutDue() {
        val items = listOf(Checklist.Item("Send deck", false, ms(25, 17)), Checklist.Item("Call Sam", true), Checklist.Item("Book room", true, ms(26, 9, 30)))
        val text = format(items)
        assertEquals("[ ] Send deck ⏰2026-10-25T17:00\n[x] Call Sam\n[x] Book room ⏰2026-10-26T09:30", text)
        assertEquals(items, parse(text))
        assertEquals(text, format(parse(text)))
    }

    @Test fun legacyLinesHaveNoDue() {
        val out = parse("[ ] One\n[x] Two\n- Three\n• Four\nFive")
        assertEquals(listOf("One", "Two", "Three", "Four", "Five"), out.map { it.text })
        assertTrue(out.all { it.dueMs == null })
        assertEquals("[ ] One\n[x] Two\n[ ] Three\n[ ] Four\n[ ] Five", format(out))
    }

    @Test fun tokensInsideTextOrMalformedStayText() {
        val mid = parse("[ ] Plan ⏰2026-10-25T17:00 with Sam")
        assertEquals("Plan ⏰2026-10-25T17:00 with Sam", mid.single().text)
        assertNull(mid.single().dueMs)
        val bad = parse("[ ] Plan ⏰2026-13-45T99:00")
        assertEquals("Plan ⏰2026-13-45T99:00", bad.single().text)
        assertNull(bad.single().dueMs)
        // Plain lines without a checkbox carry a due token too.
        assertEquals(ms(25, 17), parse("Call Sam ⏰2026-10-25T17:00").single().dueMs)
    }

    @Test fun dueOnlyLineIsDropped() {
        assertEquals(0, parse("[ ] ⏰2026-10-25T17:00").size)
    }

    @Test fun editKeepsTickAndDueOfUnchangedText() {
        val old = format(listOf(Checklist.Item("Send deck", true, ms(25, 17)), Checklist.Item("Call Sam", false)))
        val edited = Checklist.edit(old, "Send deck\nCall Sam now")
        assertEquals("[x] Send deck ⏰2026-10-25T17:00\n[ ] Call Sam now", edited)
        assertEquals("Send deck\nCall Sam", Checklist.plain(old))
    }

    @Test fun toggleAddAndSetDueKeepOtherDates() {
        val t = format(listOf(Checklist.Item("A", false, ms(25, 17)), Checklist.Item("B", false)))
        assertEquals(listOf(true, false), parse(Checklist.toggle(t, 0)).map { it.done })
        assertEquals(ms(25, 17), parse(Checklist.toggle(t, 1))[0].dueMs)
        assertEquals(ms(30, 8), parse(Checklist.add(t, " C ", ms(30, 8)))[2].dueMs)
        assertNull(parse(Checklist.setDue(t, 0, null))[0].dueMs)
        assertEquals(ms(27, 9), parse(Checklist.setDue(t, 1, ms(27, 9)))[1].dueMs)
    }

    @Test fun exportAndSearchStripTheToken() {
        val t = format(listOf(Checklist.Item("Send deck", false, ms(25, 17)), Checklist.Item("Call Sam", true)))
        assertEquals("[ ] Send deck (due Sun 25 Oct, 5:00 PM)\n[x] Call Sam", Checklist.export(t, zone))
        assertEquals("due Sun 25 Oct, 5:00 PM", Checklist.dueText(ms(25, 17), zone))
        assertFalse(Checklist.plain(t).contains("⏰"))
        assertFalse(Checklist.export(t, zone).contains("⏰"))
        assertEquals("Send deck", Checklist.stripDue("Send deck ⏰2026-10-25T17:00"))
    }

    @Test fun followupsDoNotCarryTheToken() {
        val t = format(listOf(Checklist.Item("Send deck", false, ms(25, 17))))
        val f = Followups.collect(emptyList(), listOf(app.plait.data.memo.Memo("m", "e", app.plait.data.memo.MemoKind.Checklist, 0, t)))
        assertEquals(listOf("Send deck"), f.map { it.text })
    }

    // 2026-10-19 is a Monday.
    @Test fun dueResolutionOnEveryWeekday() {
        val mon = DayOfWeek.MONDAY
        fun d(due: Due, day: Int, h: Int = 10, mi: Int = 0, mo: Int = 10) = TaskSuggester.dueMs(due, ms(day, h, mi, mo), zone, mon)
        for (day in 19..25) {
            assertEquals(ms(day, 18), d(Due.Today, day))
            assertEquals(ms(day + 1, 17), d(Due.Tomorrow, day))
            assertEquals(ms(26, 10), d(Due.NextWeek, day))
            assertNull(d(Due.None, day))
        }
        for (day in 19..22) assertEquals("this week from day $day", ms(23, 17), d(Due.ThisWeek, day))
        // Friday is the last work day itself, then the weekend: always tomorrow 5 PM.
        for (day in 23..25) assertEquals(ms(day + 1, 17), d(Due.ThisWeek, day))
    }

    @Test fun dueResolutionHonoursOtherWeekStarts() {
        // Sunday-start week: Sun..Thu are work days, Thursday is the last. 2026-10-25 is a Sunday.
        val now = ms(25, 10)
        assertEquals(ms(29, 17), TaskSuggester.dueMs(Due.ThisWeek, now, zone, DayOfWeek.SUNDAY))
        assertEquals(ms(1, 10, mo = 11), TaskSuggester.dueMs(Due.NextWeek, now, zone, DayOfWeek.SUNDAY))
    }

    @Test fun dueResolutionLateNightAndBoundaries() {
        val mon = DayOfWeek.MONDAY
        val late = ms(21, 23, 30)
        assertEquals(ms(22, 1, 30), TaskSuggester.dueMs(Due.Today, late, zone, mon))
        assertEquals(ms(22, 17), TaskSuggester.dueMs(Due.Tomorrow, late, zone, mon))
        assertEquals(ms(23, 17), TaskSuggester.dueMs(Due.ThisWeek, late, zone, mon))
        // Exactly 6 PM counts as passed; 5:59 PM does not.
        assertEquals(ms(21, 20), TaskSuggester.dueMs(Due.Today, ms(21, 18), zone, mon))
        assertEquals(ms(21, 18), TaskSuggester.dueMs(Due.Today, ms(21, 17, 59), zone, mon))
        // Minutes are whole even when "now" has seconds.
        assertEquals(ms(21, 20, 1), TaskSuggester.dueMs(Due.Today, ms(21, 18, 1) + 30_000L, zone, mon))
    }

    @Test fun reminderPlanTracksOpenDatedLines() {
        val now = ms(21, 12)
        fun I(t: String, done: Boolean = false, due: Long? = null) = Checklist.Item(t, done, due)
        // A new future date is scheduled.
        assertEquals(listOf("A"), Tasks.reminderPlan(listOf(I("A")), listOf(I("A", due = now + 1000)), now).schedule.map { it.text })
        // Ticking, clearing, deleting cancel.
        val dated = listOf(I("A", due = now + 1000))
        assertEquals(listOf("A"), Tasks.reminderPlan(dated, listOf(I("A", true, now + 1000)), now).cancel)
        assertEquals(listOf("A"), Tasks.reminderPlan(dated, listOf(I("A")), now).cancel)
        assertEquals(listOf("A"), Tasks.reminderPlan(dated, emptyList(), now).cancel)
        // Moving reschedules without a separate cancel; moving into the past cancels.
        val moved = Tasks.reminderPlan(dated, listOf(I("A", due = now + 5000)), now)
        assertEquals(listOf("A"), moved.schedule.map { it.text }); assertTrue(moved.cancel.isEmpty())
        assertEquals(listOf("A"), Tasks.reminderPlan(dated, listOf(I("A", due = now - 5000)), now).cancel)
        // Unchanged lines and already-past new dates do nothing.
        assertTrue(Tasks.reminderPlan(dated, dated, now).let { it.cancel.isEmpty() && it.schedule.isEmpty() })
        assertTrue(Tasks.reminderPlan(emptyList(), listOf(I("B", due = now - 1)), now).schedule.isEmpty())
    }
}
