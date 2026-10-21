package app.plait

import app.plait.data.brief.Phase
import app.plait.data.quotes.Quotes
import app.plait.data.tasks.AttentionItem
import app.plait.ui.screens.CardShare
import app.plait.ui.screens.CollapsedRows
import app.plait.ui.screens.DragSquish
import app.plait.ui.screens.FeedCardKind
import app.plait.ui.screens.FlickThreshold
import app.plait.ui.screens.NeighbourLag
import app.plait.ui.screens.RubberShare
import app.plait.ui.screens.SnapDamping
import app.plait.ui.screens.attentionView
import app.plait.ui.screens.cardExtent
import app.plait.ui.screens.feedCards
import app.plait.ui.screens.initialPhasePage
import app.plait.ui.screens.neighbourLag
import app.plait.ui.screens.overflows
import app.plait.ui.screens.peekAlpha
import app.plait.ui.screens.peekScale
import app.plait.ui.screens.phaseAlpha
import app.plait.ui.screens.quoteSlot
import app.plait.ui.screens.rubberBand
import app.plait.ui.screens.snapStart
import app.plait.ui.screens.squish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test fun collapsedTasksShowFourRowsAndCountTheRest() {
        val many = List(7) { item("o$it", AttentionItem.Kind.Open) }
        val v = attentionView(many, CollapsedRows)
        assertEquals(4, v.rows.size)
        assertEquals(3, v.more)
        // Open, every row is listed.
        assertEquals(7, attentionView(many, many.size).rows.size)
        assertEquals(0, attentionView(many, many.size).more)
    }

    @Test fun phasePageOpensOnNowUnlessChosenInThisPartOfTheDay() {
        assertEquals(1, initialPhasePage(savedPage = -1, savedPhase = -1, nowPhase = 1))
        assertEquals(2, initialPhasePage(savedPage = 2, savedPhase = 1, nowPhase = 1))
        assertEquals(0, initialPhasePage(savedPage = 2, savedPhase = 1, nowPhase = 0))
        // Chip index, pager page and phase are the same number.
        Phase.entries.forEachIndexed { i, p -> assertEquals(i, p.ordinal) }
    }

    @Test fun phasePagesBesideTheOpenOneSitBack() {
        assertEquals(1f, phaseAlpha(0f), 1e-6f)
        assertEquals(0.6f, phaseAlpha(1f), 1e-6f)
        assertEquals(0.6f, phaseAlpha(2f), 1e-6f)
        assertEquals(0.94f, peekScale(1f), 1e-6f)
    }

    @Test fun rubberBandIsZeroAtRestMonotonicAndAtMostItsShare() {
        val limit = 300f
        assertEquals(0f, rubberBand(0f, limit), 0f)
        var last = 0f
        for (d in 1..2000) {
            val r = rubberBand(d.toFloat(), limit)
            assertTrue(r > last)
            assertTrue(r <= RubberShare * d + 1e-3f)
            assertTrue(r < limit)
            last = r
        }
        assertEquals(-rubberBand(80f, limit), rubberBand(-80f, limit), 1e-5f)
        // A short pull moves the feed by about a third of it.
        assertEquals(RubberShare * 10f, rubberBand(10f, limit), 0.1f)
    }

    @Test fun dragSquishAndNeighbourLag() {
        assertEquals(1f, squish(0f, 0f, 100f), 1e-6f)
        assertEquals(DragSquish, squish(0.5f, 0f, 100f), 1e-6f)
        assertEquals(DragSquish, squish(-0.5f, 0f, 100f), 1e-6f)
        assertEquals(0.99f, squish(0.25f, 0f, 100f), 1e-6f)
        assertEquals(DragSquish, squish(0f, 60f, 100f), 1e-6f)
        assertEquals(0f, neighbourLag(0f, 30f), 0f)
        // Opposite to the finger, and never past the limit.
        assertTrue(neighbourLag(-50f, 30f) > 0f)
        assertTrue(neighbourLag(50f, 30f) < 0f)
        assertEquals(30f, neighbourLag(-10_000f, 30f), 0f)
        assertEquals(-30f, neighbourLag(10_000f, 30f), 0f)
    }

    @Test fun swipeConstants() {
        assertEquals(0.2f, FlickThreshold, 0f)
        assertTrue(SnapDamping in 0.5f..0.6f)
        assertEquals(0.35f, RubberShare, 0f)
        assertEquals(0.98f, DragSquish, 0f)
        assertEquals(12f, NeighbourLag.value, 0f)
    }

    @Test fun overflowOnlyWhenTheContentIsTaller() {
        assertFalse(overflows(contentHeight = 500, cardHeight = 600))
        assertFalse(overflows(contentHeight = 600, cardHeight = 600))
        assertTrue(overflows(contentHeight = 601, cardHeight = 600))
        // Before the card is measured nothing is cut.
        assertFalse(overflows(contentHeight = 400, cardHeight = 0))
    }

    @Test fun moreRowsThanEightAreCounted() {
        val many = List(11) { item("o$it", AttentionItem.Kind.Open) }
        val v = attentionView(many)
        assertEquals(8, v.rows.size)
        assertEquals(3, v.more)
        assertEquals(0, attentionView(many.take(8)).more)
    }
}
