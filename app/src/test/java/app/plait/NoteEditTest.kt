package app.plait

import app.plait.data.NoteEdit
import app.plait.data.NoteLine
import app.plait.data.SavedNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteEditTest {
    private fun note(vararg t: String) = SavedNote(
        "n", "e", "T", 0L, 9_000L, "", t.mapIndexed { i, s -> NoteLine(i * 1000L, s) }, emptyList(), ai = "old", aiBy = "x",
    )

    @Test fun roundTripKeepsEverything() {
        val n = note("a", "b")
        val out = NoteEdit.applyTranscript(n, NoteEdit.transcriptText(n))
        assertEquals(n.lines, out.lines)
        assertEquals("old", out.ai)
    }

    @Test fun editKeepsTimesAndDropsStaleSummary() {
        val out = NoteEdit.applyTranscript(note("a", "b"), "a\nchanged")
        assertEquals(listOf(0L, 1000L), out.lines.map { it.atMs })
        assertNull(out.ai)
    }

    @Test fun blankLinesAreIgnored() {
        assertEquals(2, NoteEdit.applyTranscript(note("a", "b", "c"), "x\n\n  \ny").lines.size)
    }
}

class MeetingInfoTest {
    @Test fun inviteMachineryIsRemoved() {
        val raw = "Discuss Q4 plan\n________________\nMicrosoft Teams meeting\nJoin: https://teams.microsoft.com/l/meetup-join/x\nMeeting ID: 123 456\nPasscode: ab12"
        assertEquals("Discuss Q4 plan", app.plait.ui.screens.cleanInvite(raw))
    }

    @Test fun madeUpNamesAndNumbersAreDropped() {
        val src = "Subscriptions discussion\nReview the renewal with Karim"
        val ai = "Purpose: Review the renewal with Karim.\nDetails: Budget is 5000 and Sarah joins."
        assertEquals("Purpose: Review the renewal with Karim.", app.plait.ui.screens.grounded(ai, src))
    }
}

class EnforceBriefTest {
    @Test fun strayLabelsAndEscapesAreCleaned() {
        assertEquals("", app.plait.ui.screens.enforceBrief("\\n Summary:"))
        assertEquals("Purpose: Agree the plan.", app.plait.ui.screens.enforceBrief("Summary: Agree the plan.\nJoin: https://x"))
        assertEquals("Agenda:\n- Bugs\n- Date", app.plait.ui.screens.enforceBrief("Agenda:\n- Bugs\n- Date"))
    }
}

class InviteCodeTest {
    @Test fun styleSheetLinesAreNotInviteText() {
        val raw = "color: #1a73e8 !important;\na { text-decoration: none; }\nPlan the Q1 roadmap together"
        assertEquals("Plan the Q1 roadmap together", app.plait.ui.screens.cleanInvite(raw))
    }
}
