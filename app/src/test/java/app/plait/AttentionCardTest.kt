package app.plait

import app.plait.data.tasks.AttentionItem
import app.plait.ui.screens.attentionCaption
import org.junit.Assert.assertEquals
import org.junit.Test

class AttentionCardTest {
    private val now = 1_800_000_000_000L
    private fun item(kind: AttentionItem.Kind, title: String, since: Long = now) = AttentionItem("x", kind, "t", "e", title, since)

    @Test fun openWithMeeting() = assertEquals("From Sync · today", attentionCaption(item(AttentionItem.Kind.Open, "Sync"), now))
    @Test fun openFreeNote() = assertEquals("Free note · 3 days ago", attentionCaption(item(AttentionItem.Kind.Open, " ", now - 3 * 86_400_000L), now))
    @Test fun suggestion() = assertEquals("Suggested from Sync", attentionCaption(item(AttentionItem.Kind.Suggestion, "Sync"), now))
}
