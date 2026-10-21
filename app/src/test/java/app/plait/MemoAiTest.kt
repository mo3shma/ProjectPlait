package app.plait

import app.plait.data.memo.Memo
import app.plait.data.memo.MemoAi
import app.plait.data.memo.MemoJson
import app.plait.data.memo.MemoKind
import app.plait.data.sync.SyncMerge
import app.plait.data.sync.SyncState
import app.plait.data.tasks.Due
import app.plait.data.tasks.TaskIdea
import app.plait.data.tasks.TaskSuggester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoAiTest {
    private val long = "We agreed to move the launch to March. The design review needs another week. Sam will send the budget to finance."

    @Test fun oldJsonLoadsWithoutTheNewFields() {
        val old = """{"version":1,"memos":[{"id":"a","eventId":"e","kind":"Text","createdAt":5,"text":"hi"}]}"""
        val m = MemoJson.decode(old).single()
        assertNull(m.ai); assertNull(m.aiBy); assertNull(m.aiEngine); assertEquals(0, m.aiHash)
    }

    @Test fun newFieldsSurviveTheRoundTrip() {
        val m = Memo("a", "e", MemoKind.Photo, 5, long, "a.jpg", ai = "Launch moves.", aiBy = "Qwen3 0.6B", aiHash = MemoAi.hash(long), aiEngine = "Local:q")
        assertEquals(m, MemoJson.decode(MemoJson.encode(listOf(m))).single())
        val plain = Memo("b", "e", MemoKind.Text, 5, "x")
        assertFalse(MemoJson.encode(listOf(plain)).contains("aiHash"))
        assertEquals(plain, MemoJson.decode(MemoJson.encode(listOf(plain))).single())
    }

    @Test fun skipRules() {
        assertTrue(MemoAi.worthAnalysing(long))
        assertFalse(MemoAi.worthAnalysing("Call Sam tomorrow"))
        assertFalse(MemoAi.worthAnalysing("https://example.com/a/very/long/link/to/some/document/page"))
        assertFalse(MemoAi.worthAnalysing("123456789012345678901234567890 12345"))
        assertFalse(MemoAi.worthAnalysing(Memo("c", "", MemoKind.Checklist, 1, long)))
    }

    @Test fun sameHashIsNotAnalysedTwice() {
        val m = Memo("a", "e", MemoKind.Text, 1, long)
        assertTrue(MemoAi.needsSummary(m))
        val done = m.copy(ai = "s", aiHash = MemoAi.hash(long), aiEngine = "Rules:")
        assertFalse(MemoAi.needsSummary(done))
        assertFalse(MemoAi.needsSummary(done.copy(text = "$long  ")))
        assertTrue(MemoAi.needsSummary(done.copy(text = "$long More.")))
        assertTrue(MemoAi.needsSummary(done, "Local:q"))
        assertFalse(MemoAi.needsSummary(done, "Rules:"))
    }

    @Test fun rulesSummaryEnglishAndArabic() {
        assertEquals("We agreed to move the launch to March. The design review needs another week.", MemoAi.rulesSummary(long))
        val ar = "اتفقنا على تأجيل الإطلاق إلى مارس. يحتاج مراجعة التصميم إلى أسبوع آخر. سيرسل سامي الميزانية إلى المالية."
        assertEquals("اتفقنا على تأجيل الإطلاق إلى مارس. يحتاج مراجعة التصميم إلى أسبوع آخر.", MemoAi.rulesSummary(ar))
        val words = MemoAi.rulesSummary("word ".repeat(100))!!
        assertTrue(words.split(' ').size <= 46)
        assertNull(MemoAi.rulesSummary("ok"))
    }

    @Test fun doesNotAnalyseTwiceWithinTwoSeconds() {
        assertTrue(MemoAi.Guard.allow("g1", 10_000))
        assertFalse(MemoAi.Guard.allow("g1", 11_500))
        assertTrue(MemoAi.Guard.allow("g2", 11_500))
        assertTrue(MemoAi.Guard.allow("g1", 12_100))
    }

    @Test fun freeNotesGetTaskIdeas() {
        val found = TaskSuggester.fromRules("Good thought. We need to send the deck to the client by Friday.")
        assertEquals(1, found.size)
        assertEquals(Due.ThisWeek, found[0].second)
        val idea = TaskIdea(TaskSuggester.id("m1", found[0].first), "m1", "", found[0].first, found[0].second, 1L)
        assertEquals("", idea.eventId)
    }

    @Test fun syncKeepsTheNewerLocalText() {
        val local = Memo("a", "e", MemoKind.Text, 1, "edited text here", ai = "new", aiHash = MemoAi.hash("edited text here"))
        val remote = Memo("a", "e", MemoKind.Text, 1, "old text", ai = "old summary", aiHash = MemoAi.hash("old text"))
        val plan = SyncMerge.plan(SyncState(memos = listOf(local)), SyncState(memos = listOf(remote)))
        assertTrue(plan.addMemos.isEmpty())
        val fresh = SyncMerge.plan(SyncState(), SyncState(memos = listOf(remote)))
        assertNotNull(fresh.addMemos.single().ai)
    }
}
