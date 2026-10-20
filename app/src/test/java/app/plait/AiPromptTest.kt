package app.plait

import app.plait.data.CalEvent
import app.plait.data.Source
import app.plait.data.brief.BriefBuilder
import app.plait.data.context.AiPrompt
import app.plait.data.context.ContextItem
import app.plait.data.context.ExampleEmail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class AiPromptTest {
    private val zone = ZoneId.of("UTC")
    private val ev = CalEvent("e1", "10:00", "1h", "Budget review", Source.Google, "", startMs = 1_800_000_000_000, endMs = 1_800_003_600_000)

    @Test fun promptStaysWithinBudgetAndKeepsFormat() {
        val long = "We agreed the plan. ".repeat(2000)
        val items = (1..3).map { ContextItem("i$it", "e1", "Subject $it", "A <a@x.com>", "", null, long, 1L, "pasted") }
        val p = AiPrompt.build(ev, BriefBuilder.build(ev), items, zone)
        assertTrue(p.length < 4000)
        assertTrue(p.contains("Budget review"))
        assertTrue(p.contains("[Email 3]"))
        assertTrue(p.trimEnd().endsWith("- up to 3 short bullets"))
    }

    @Test fun parseReadsSections() {
        val s = AiPrompt.parse("Relation: Sets up the budget talk.\nKey points:\n- Cut of 20%\n- Due Thursday\nTo settle in the meeting:\n* Who owns follow-ups")
        assertEquals("Sets up the budget talk.", s.relation)
        assertEquals(listOf("Cut of 20%", "Due Thursday"), s.keyPoints)
        assertEquals(listOf("Who owns follow-ups"), s.toSettle)
    }

    @Test fun parseToleratesFreeText() {
        val s = AiPrompt.parse("Just some prose.")
        assertEquals("Just some prose.", s.relation)
        assertTrue(s.keyPoints.isEmpty())
        assertNull(AiPrompt.parse("").relation)
    }

    @Test fun exampleEmailMentionsMeeting() {
        val m = ExampleEmail.forEvent(ev, zone)
        assertTrue(m.subject.contains("Budget review"))
        assertTrue(m.attachments.isNotEmpty())
    }
}
