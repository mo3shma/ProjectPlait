package app.plait

import app.plait.ai.AiHarness
import app.plait.data.tasks.Due
import app.plait.data.tasks.TaskSuggester
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TasksAndAboutTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test fun paragraphKeepsTwoWholeSentences() {
        val src = "Review the onboarding flow. Walk through the screens. Agree the launch scope."
        val out = AiHarness.paragraph("A review of the onboarding flow. The team walks through the screens. Then they agree the scope.", src, 35)
        assertEquals("A review of the onboarding flow. The team walks through the screens.", out)
    }

    @Test fun paragraphCutsALongSentenceAndRejectsMadeUpNames() {
        val long = (1..50).joinToString(" ") { "word" }
        assertTrue(AiHarness.paragraph(long, long, 35)!!.endsWith("…"))
        assertNull(AiHarness.paragraph("A review with Karim of the budget.", "budget review", 35))
    }

    @Test fun guessMustStayOnTheTitle() {
        assertTrue(AiHarness.onTopic("Likely a check-in on the dashboard", "Dashboard sync"))
        assertTrue(AiHarness.onTopic("Likely revising the contract with legal", "Contract revision w/ legal"))
        assertFalse(AiHarness.onTopic("Likely planning the company offsite party", "Dashboard sync"))
        // A title with no topic words lets a careful guess through.
        assertTrue(AiHarness.onTopic("Likely a regular catch-up", "Weekly sync"))
    }

    @Test fun ruleGuessIsOneShortLine() {
        assertEquals("Likely about dashboard review.", app.plait.ui.screens.ruleGuess("Dashboard review"))
    }

    @Test fun dueTimesComeFromTheWords() {
        assertEquals(Due.Today, TaskSuggester.dueFrom("Send the deck today"))
        assertEquals(Due.Tomorrow, TaskSuggester.dueFrom("call them tomorrow"))
        assertEquals(Due.ThisWeek, TaskSuggester.dueFrom("draft two options by Thursday"))
        assertEquals(Due.NextWeek, TaskSuggester.dueFrom("book a test next week"))
        assertEquals(Due.None, TaskSuggester.dueFrom("we need to book a usability test"))
    }

    @Test fun aiTasksAreCheckedAgainstTheNote() {
        val note = "Omar will draft two options for the permissions step by Thursday. We need to book a usability test."
        val o = JSONObject("""{"tasks": [
            {"task": "Draft two options for the permissions step", "due": "this week"},
            {"task": "Email Karim the contract", "due": "today"},
            {"task": "Book a usability test", "due": "tomorrow"}
        ]}""")
        val out = TaskSuggester.parse(o, note)
        assertEquals(listOf("Draft two options for the permissions step", "Book a usability test"), out.map { it.first })
        assertEquals(Due.ThisWeek, out[0].second)
    }

    @Test fun rulesFindTasksWithoutAnAi() {
        val out = TaskSuggester.fromRules("Good meeting overall. We need to send the deck to the client by Friday. The design looks fine.")
        assertEquals(1, out.size)
        assertTrue(out[0].first.contains("send the deck", ignoreCase = true))
        assertEquals(Due.ThisWeek, out[0].second)
    }

    @Test fun remindersLandAtSensibleTimes() {
        val tue10 = ms(2026, 10, 20, 10) // a Tuesday
        assertEquals(ms(2026, 10, 20, 17), TaskSuggester.remindAt(Due.Today, tue10, zone))
        assertEquals(ms(2026, 10, 21, 9), TaskSuggester.remindAt(Due.Tomorrow, tue10, zone))
        assertEquals(ms(2026, 10, 21, 9), TaskSuggester.remindAt(Due.None, tue10, zone))
        assertEquals(ms(2026, 10, 23, 9), TaskSuggester.remindAt(Due.ThisWeek, tue10, zone))
        assertEquals(ms(2026, 10, 26, 9), TaskSuggester.remindAt(Due.NextWeek, tue10, zone))
        // Late in the day, "today" means in an hour.
        val tue1645 = ms(2026, 10, 20, 16, 45)
        assertEquals(tue1645 + 3_600_000L, TaskSuggester.remindAt(Due.Today, tue1645, zone))
    }

    @Test fun reminderLabels() {
        val now = ms(2026, 10, 20, 10)
        assertEquals("Tomorrow 9:00 AM", app.plait.reminder.TaskReminders.label(ms(2026, 10, 21, 9), now, zone))
        assertEquals("Fri 9:00 AM", app.plait.reminder.TaskReminders.label(ms(2026, 10, 23, 9), now, zone))
    }

    @Test fun thinkingIsStrippedFromAnswers() {
        assertEquals("{\"about\": \"x\"}", app.plait.ai.LocalLlm.stripThinking("<think>\nLet me see.\n</think>\n{\"about\": \"x\"}"))
        assertEquals("plain", app.plait.ai.LocalLlm.stripThinking("plain"))
    }

    @Test fun bestModelIsByQualityNotSize() {
        val best = app.plait.ai.ModelCatalog.textModels.maxByOrNull { it.quality }!!
        assertEquals("text-gemma4-e2b", best.id)
        val q3 = app.plait.ai.ModelCatalog.byId("text-qwen3-1.7b")!!
        val q25 = app.plait.ai.ModelCatalog.byId("text-qwen25-1.5b")!!
        assertTrue(q3.quality > q25.quality && q3.bytes < q25.bytes)
    }
}
