package app.plait

import app.plait.data.CalEvent
import app.plait.data.CallProvider
import app.plait.data.Source
import app.plait.ui.WidgetLaunch
import app.plait.widget.JoinLook
import app.plait.widget.NextMeetingWidget
import app.plait.widget.NextMeetingWidgetQuick
import app.plait.widget.NextMeetingWidgetSmall
import app.plait.widget.TasksWidget
import app.plait.widget.MonthWidget
import app.plait.widget.WidgetModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class WidgetQuickTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private val meet = "https://meet.google.com/abc-defg-hij"
    private fun ev(id: String, title: String, start: Long, end: Long = start + 3_600_000L, source: Source = Source.Other, join: String? = null, place: String = "") =
        CalEvent(id, "", "", title, source, place, conferenceUri = join, startMs = start, endMs = end, allDay = false)
    private fun model(vararg e: CalEvent, at: Long = ms(21, 9)) = WidgetModel.build(e.toList(), at, zone, Locale.ENGLISH)

    @Test fun sourceMapsToItsServiceColour() {
        assertEquals(JoinLook.Google, model(ev("a", "A", ms(21, 10), source = Source.Google)).look)
        assertEquals(JoinLook.Outlook, model(ev("a", "A", ms(21, 10), source = Source.Outlook)).look)
        assertEquals(JoinLook.ICloud, model(ev("a", "A", ms(21, 10), source = Source.ICloud)).look)
        assertEquals(JoinLook.Other, model(ev("a", "A", ms(21, 10), source = Source.Other)).look)
        for (s in Source.entries) assertTrue(JoinLook.of(null, s).argb != 0)
    }

    @Test fun callProviderLeadsLikeThePalette() {
        assertEquals(JoinLook.Meet, model(ev("a", "A", ms(21, 10), source = Source.Outlook, join = meet)).look)
        assertEquals(JoinLook.Teams, JoinLook.of(CallProvider.Teams, Source.Google))
        assertEquals(JoinLook.Zoom, JoinLook.of(CallProvider.Zoom, Source.Other))
        assertEquals(JoinLook.Webex, JoinLook.of(CallProvider.Webex, Source.ICloud))
        for (p in CallProvider.entries) assertTrue(JoinLook.of(p, Source.Other) != JoinLook.Other)
    }

    @Test fun tintAndAccentAreOpaqueArgb() {
        val m = model(ev("a", "A", ms(21, 10), source = Source.Google))
        assertEquals(0xFF4285F4.toInt(), m.joinTint); assertEquals(m.joinTint, m.accent)
        assertEquals(0xFF, (m.joinTint ushr 24))
        assertEquals(0xFF7B8CDE.toInt(), model().accent)
    }

    @Test fun quickStripTexts() {
        val link = WidgetModel.quickLines(model(ev("a", "Design review", ms(21, 13, 30), join = meet), ev("b", "B", ms(21, 15))))
        assertEquals("1:30 PM", link.top); assertEquals("Design review", link.title); assertTrue(link.showJoin)
        val running = WidgetModel.quickLines(model(ev("a", "Workshop", ms(21, 8), ms(21, 10, 30), join = meet)))
        assertEquals("Now · until 10:30 AM", running.top); assertTrue(running.showJoin)
        val then = WidgetModel.quickLines(model(ev("a", "A", ms(21, 10)), ev("b", "Design review", ms(21, 12))))
        assertEquals("10:00 AM · Then Design review 12:00 PM", then.top); assertFalse(then.showJoin)
        assertEquals("10:00 AM · Last one today", WidgetModel.quickLines(model(ev("a", "A", ms(21, 10)))).top)
        val clear = WidgetModel.quickLines(model(ev("a", "Stand-up", ms(26, 9))))
        assertEquals("Next: Mon 9:00 AM", clear.top); assertEquals("A clear day", clear.title); assertFalse(clear.showJoin)
        val none = WidgetModel.quickLines(model())
        assertEquals("Nothing coming up", none.top); assertEquals("A clear day", none.title); assertFalse(none.showJoin)
    }

    @Test fun updateAllCoversAllFiveProviders() {
        assertEquals(
            listOf(NextMeetingWidget::class.java, NextMeetingWidgetSmall::class.java, NextMeetingWidgetQuick::class.java, TasksWidget::class.java, MonthWidget::class.java),
            NextMeetingWidget.providers,
        )
    }

    @Test fun quickAddKindParsing() {
        assertEquals("voice", WidgetLaunch.parse("voice")); assertEquals("list", WidgetLaunch.parse("list")); assertEquals("text", WidgetLaunch.parse("text"))
        assertNull(WidgetLaunch.parse("photo")); assertNull(WidgetLaunch.parse("")); assertNull(WidgetLaunch.parse("VOICE")); assertNull(WidgetLaunch.parse(null))
    }

    @Test fun titleLineDecisionAtTwentyFourCharacters() {
        val short = WidgetModel.wideLines(model(ev("a", "x".repeat(24), ms(21, 10), join = meet)))
        assertEquals(1, short.titleLines); assertEquals("Online", short.sub); assertEquals(18, short.titleSp)
        val long = WidgetModel.wideLines(model(ev("a", "x".repeat(25), ms(21, 10), join = meet)))
        assertEquals(2, long.titleLines); assertNull(long.sub); assertEquals(16, long.titleSp)
        assertEquals(20, WidgetModel.wideTitleSp("x".repeat(16))); assertEquals(18, WidgetModel.wideTitleSp("x".repeat(17)))
    }

    @Test fun timeRangeDropsRepeatedSuffix() {
        assertEquals("1:30 – 2:30 PM", WidgetModel.range("1:30 PM", "2:30 PM"))
        assertEquals("11:30 AM – 12:30 PM", WidgetModel.range("11:30 AM", "12:30 PM"))
    }

    @Test fun worstCaseBudgets() {
        val titles = listOf("x".repeat(300), "MOBIHITS & Mobizone / Weekly", "Short", "x".repeat(24), "x".repeat(25))
        for (t in titles) for (j in listOf(null, meet)) for (p in listOf("", "Room 4")) for (run in listOf(false, true)) {
            val e = ev("a", t, if (run) ms(21, 8) else ms(21, 10), ms(21, 11), join = j, place = p)
            val m = model(e, ev("b", "B", ms(21, 12)), ev("c", "C", ms(21, 13)))
            assertTrue("wide $t $j $p $run", WidgetModel.wideMainDp(WidgetModel.wideLines(m)) <= WidgetModel.WIDE_MAIN_DP)
            assertTrue("small $t $j $p $run", WidgetModel.smallDp(WidgetModel.smallLines(m), false) <= WidgetModel.SMALL_INNER_DP)
        }
        // 4x2: 110 - 2 x 8 padding = 94 = top row 16 + main 58 + strip 20.
        assertEquals(94.0, 16 + WidgetModel.WIDE_MAIN_DP + 20, 0.0)
        // 2x2: 110 - 2 x 6 padding = 98.
        assertEquals(98.0, WidgetModel.SMALL_INNER_DP, 0.0)
        // 4x3: 180 - 2 x 12 padding = 156 >= strip 52 + gap 8 + tiles 88.
        assertTrue(WidgetModel.QUICK_STRIP_DP + WidgetModel.QUICK_GAP_DP + WidgetModel.QUICK_TILE_DP <= WidgetModel.QUICK_INNER_DP)
        // The strip holds a 12sp and a 14sp line.
        assertTrue(12 * 1.25 + 14 * 1.25 <= WidgetModel.QUICK_STRIP_DP)
    }
}
