package app.plait

import app.plait.data.CalEvent
import app.plait.data.Source
import app.plait.data.memo.Memo
import app.plait.data.memo.MemoKind
import app.plait.data.tasks.AttentionItem
import app.plait.data.tasks.AttentionTasks
import app.plait.data.tasks.Due
import app.plait.data.tasks.TaskIdea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AttentionTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(d: Int, h: Int) = LocalDateTime.of(2026, 10, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = ms(21, 12)
    private val events = mapOf(
        "past" to CalEvent("past", "9:00 AM", "1h", "Design review", Source.Other, "", startMs = ms(19, 9)),
        "older" to CalEvent("older", "9:00 AM", "1h", "Kickoff", Source.Other, "", startMs = ms(14, 9)),
        "future" to CalEvent("future", "9:00 AM", "1h", "Planning", Source.Other, "", startMs = ms(22, 9)),
    )
    private fun memo(id: String, event: String, text: String, at: Long = ms(20, 10)) = Memo(id, event, MemoKind.Checklist, at, text)
    private fun idea(id: String, event: String, text: String, at: Long = ms(20, 10)) = TaskIdea(id, "src", event, text, Due.None, at)
    private fun collect(ideas: List<TaskIdea>, memos: List<Memo>) = AttentionTasks.collect(ideas, memos, { events[it] }, now)

    @Test fun suggestionsAndOpenItemsOfPastMeetings() {
        val out = collect(listOf(idea("i1", "past", "Book a test")), listOf(memo("m1", "past", "[x] Send deck\n[ ] Call Sam")))
        assertEquals(listOf("Book a test", "Call Sam"), out.map { it.text })
        val open = out.single { it.kind == AttentionItem.Kind.Open }
        assertEquals("memo:m1:1", open.id)
        assertEquals(1, open.index)
        assertEquals("Design review", open.eventTitle)
        assertEquals("idea:i1", out.single { it.kind == AttentionItem.Kind.Suggestion }.id)
    }

    @Test fun futureChecklistsLeftOutFreeNotesAndUnknownKept() {
        val out = collect(emptyList(), listOf(memo("m1", "future", "[ ] Agenda item"), memo("m2", "", "[ ] Free task"), memo("m3", "gone", "[ ] Lost task")))
        assertEquals(setOf("Free task", "Lost task"), out.map { it.text }.toSet())
        assertEquals("", out.single { it.text == "Free task" }.eventTitle)
    }

    @Test fun textNotesIgnored() {
        assertEquals(0, collect(emptyList(), listOf(Memo("t", "past", MemoKind.Text, 0, "[ ] Not a list"))).size)
    }

    @Test fun openItemHidesSameSuggestion() {
        val out = collect(listOf(idea("i1", "past", "call sam"), idea("i2", "older", "Call Sam")), listOf(memo("m1", "past", "[ ] Call Sam")))
        assertEquals(listOf(AttentionItem.Kind.Suggestion to "older", AttentionItem.Kind.Open to "past"), out.map { it.kind to it.eventId })
    }

    @Test fun stalestFirstTiesByText() {
        val out = collect(
            listOf(idea("i1", "past", "Zeta")),
            listOf(memo("m1", "older", "[ ] Old"), memo("m2", "", "[ ] Newest", ms(21, 8)), memo("m3", "past", "[ ] Alpha")),
        )
        assertEquals(listOf("Old", "Alpha", "Zeta", "Newest"), out.map { it.text })
    }

    @Test fun ago() {
        assertEquals("today", AttentionTasks.ago(ms(21, 0), now, zone))
        assertEquals("yesterday", AttentionTasks.ago(ms(20, 23), now, zone))
        assertEquals("3 days ago", AttentionTasks.ago(ms(18, 12), now, zone))
        assertEquals("last week", AttentionTasks.ago(ms(12, 12), now, zone))
        assertEquals("2 weeks ago", AttentionTasks.ago(ms(6, 12), now, zone))
    }

    @Test fun tickFlipsOnlyTheMatchingLine() {
        val list = "[ ] Call Sam\n[ ] Call Sam\n[x] Done"
        assertEquals("[ ] Call Sam\n[x] Call Sam\n[x] Done", AttentionTasks.ticked(list, 1, "Call Sam"))
        assertNull(AttentionTasks.ticked(list, 0, "Other"))
        assertNull(AttentionTasks.ticked(list, 2, "Done"))
        assertNull(AttentionTasks.ticked(list, 9, "Call Sam"))
    }
}
