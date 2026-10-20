package app.plait

import app.plait.ai.NoteSummary
import app.plait.data.NoteLine
import app.plait.data.Transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptTest {
    @Test fun shortPausesDoNotSplitTheNote() {
        val lines = listOf(
            NoteLine(0, "So the plan for the launch"), NoteLine(2_600, "is to start with the onboarding."),
            NoteLine(5_500, "Omar will draft two options."),
            // A long silence: a new paragraph.
            NoteLine(30_000, "Next topic, the budget."), NoteLine(32_400, "We need numbers by Friday."),
        )
        val p = Transcript.paragraphs(lines)
        assertEquals(2, p.size)
        assertEquals("So the plan for the launch is to start with the onboarding. Omar will draft two options.", p[0].text)
        assertEquals(30_000L, p[1].atMs)
        assertEquals("Next topic, the budget. We need numbers by Friday.", p[1].text)
    }

    @Test fun unclearParagraphsAreMarked() {
        val p = Transcript.paragraphs(listOf(NoteLine(0, "mumble", 0.3f), NoteLine(1_000, "words", 0.4f), NoteLine(2_000, "clear", 0.9f)))
        assertEquals(1, p.size)
        assertTrue(p[0].shaky)
    }

    @Test fun longTranscriptsAreReadInParts() {
        val sentence = "We talked about the launch plan and the next steps for the team."
        val text = List(80) { sentence }.joinToString(" ")
        val parts = NoteSummary.parts(text, 1800)
        assertTrue(parts.size >= 3)
        assertTrue(parts.all { it.length <= 1800 })
        // Nothing is lost: every sentence is in some part.
        assertEquals(80, parts.sumOf { p -> Regex("launch plan").findAll(p).count() })
    }
}
