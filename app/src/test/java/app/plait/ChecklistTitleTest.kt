package app.plait

import app.plait.data.memo.Checklist
import app.plait.data.memo.Memo
import app.plait.data.memo.MemoJson
import app.plait.data.memo.MemoKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecklistTitleTest {
    @Test fun emptyOrBlankFallsBackToChecklist() {
        assertEquals("Checklist", Checklist.label(null))
        assertEquals("Checklist", Checklist.label(""))
        assertEquals("Checklist", Checklist.label("   "))
    }

    @Test fun customTitleIsTrimmedAndShownWithProgress() {
        assertEquals("Groceries", Checklist.label("  Groceries "))
        assertEquals("Groceries · 1 of 2 done", Checklist.heading("Groceries", "[x] Milk\n[ ] Eggs"))
        assertEquals("Checklist · 0 of 1 done", Checklist.heading(null, "[ ] Eggs"))
    }

    @Test fun titleSurvivesJsonAndOldDataLoadsWithoutOne() {
        val m = Memo("1", "", MemoKind.Checklist, 5L, "[ ] a", title = "Trip")
        assertEquals("Trip", MemoJson.decode(MemoJson.encode(listOf(m))).single().title)
        val old = """{"version":1,"memos":[{"id":"2","eventId":"","kind":"Checklist","createdAt":1,"text":"[ ] a"}]}"""
        assertNull(MemoJson.decode(old).single().title)
    }
}
