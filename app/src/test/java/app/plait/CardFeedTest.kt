package app.plait

import app.plait.data.brief.Phase
import app.plait.data.quotes.Quotes
import app.plait.data.tasks.AttentionItem
import app.plait.ui.screens.CardShare
import app.plait.ui.screens.FeedCardKind
import app.plait.ui.screens.attentionView
import app.plait.ui.screens.cardExtent
import app.plait.ui.screens.feedCards
import app.plait.ui.screens.peekAlpha
import app.plait.ui.screens.peekScale
import app.plait.ui.screens.quoteSlot
import app.plait.ui.screens.snapStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CardFeedTest {
    @Test fun morningAndMiddayHaveMeetingsThenTasks() {
        assertEquals(listOf(FeedCardKind.Meetings, FeedCardKind.Tasks), feedCards(Phase.Morning, false))
        assertEquals(listOf(FeedCardKind.Meetings, FeedCardKind.Tasks), feedCards(Phase.Afternoon, true))
    }

    @Test fun eveningIsDayThenTomorrow() = assertEquals(listOf(FeedCardKind.Day, FeedCardKind.Tomorrow), feedCards(Phase.Evening, false))

    @Test fun lastWorkDayEveningStartsWithTheWeekRecap() =
        assertEquals(listOf(FeedCardKind.WeekRecap, FeedCardKind.Day, FeedCardKind.Tomorrow), feedCards(Phase.Evening, true))

    @Test fun fadeAndScaleInterpolate() {
        assertEquals(1f, peekAlpha(0f), 1e-6f)
        assertEquals(0.35f, peekAlpha(1f), 1e-6f)
        assertEquals(0.675f, peekAlpha(0.5f), 1e-6f)
        assertEquals(0.94f, peekScale(1f), 1e-6f)
        assertEquals(0.97f, peekScale(0.5f), 1e-6f)
        // Past one page away nothing fades further; a negative offset is the active card.
        assertEquals(0.35f, peekAlpha(3f), 1e-6f)
        assertEquals(1f, peekAlpha(-1f), 1e-6f)
    }

    @Test fun firstCardSnapsToTheTopOthersToTheBottom() {
        assertEquals(0, snapStart(0, 1000, 750))
        assertEquals(250, snapStart(1, 1000, 750))
        assertEquals(250, snapStart(5, 1000, 750))
    }

    @Test fun cardIsThreeQuartersOfTheArea() {
        assertEquals(0.75f, CardShare, 0f)
        assertEquals(600f, cardExtent(800f), 1e-4f)
    }

    @Test fun quoteSlotIsStableAndDiffersPerCard() {
        val day = 20_000L
        for (p in Phase.entries) {
            assertEquals(quoteSlot(p, FeedCardKind.Meetings), quoteSlot(p, FeedCardKind.Meetings))
            assertNotEquals(quoteSlot(p, FeedCardKind.Meetings), quoteSlot(p, FeedCardKind.Tasks))
            assertNotEquals(Quotes.pick(day, quoteSlot(p, FeedCardKind.Meetings)), Quotes.pick(day, quoteSlot(p, FeedCardKind.Tasks)))
        }
        assertNotEquals(Quotes.pick(day, quoteSlot(Phase.Evening, FeedCardKind.Day)), Quotes.pick(day, quoteSlot(Phase.Evening, FeedCardKind.Tomorrow)))
        assertEquals(Quotes.pick(day, 3), Quotes.pick(day, quoteSlot(Phase.Morning, FeedCardKind.Tasks)))
    }

    private fun item(id: String, kind: AttentionItem.Kind, urgent: Boolean = false) =
        AttentionItem(id, kind, id, "", "", 0L, dueMs = if (urgent) 1L else null, urgent = urgent)

    @Test fun tasksListUrgentThenSuggestionsThenTheRest() {
        val items = listOf(
            item("open1", AttentionItem.Kind.Open), item("sug1", AttentionItem.Kind.Suggestion), item("u1", AttentionItem.Kind.Open, urgent = true),
            item("open2", AttentionItem.Kind.Open), item("u2", AttentionItem.Kind.Open, urgent = true), item("sug2", AttentionItem.Kind.Suggestion),
        )
        assertEquals(listOf("u1", "u2", "sug1", "sug2", "open1", "open2"), attentionView(items).rows.map { it.id })
    }

    @Test fun countIsUrgentWhenThereAreAnyElseTotal() {
        val mixed = listOf(item("a", AttentionItem.Kind.Open, urgent = true), item("b", AttentionItem.Kind.Suggestion), item("c", AttentionItem.Kind.Open))
        assertEquals(1, attentionView(mixed).count)
        assertEquals(2, attentionView(mixed.drop(1)).count)
        assertEquals(0, attentionView(emptyList()).count)
    }

    @Test fun moreRowsThanEightAreCounted() {
        val many = List(11) { item("o$it", AttentionItem.Kind.Open) }
        val v = attentionView(many)
        assertEquals(8, v.rows.size)
        assertEquals(3, v.more)
        assertEquals(0, attentionView(many.take(8)).more)
    }
}
