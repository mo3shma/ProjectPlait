package app.plait

import app.plait.widget.NextMeetingWidget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetThrottleTest {
    @Test fun skipsRepeatWithinTwoMinutes() = assertTrue(NextMeetingWidget.shouldSkip(1_000, 60_000, true))
    @Test fun runsAfterTwoMinutes() = assertFalse(NextMeetingWidget.shouldSkip(1_000, 121_000, true))
    @Test fun runsWhenWidgetSetChanged() = assertFalse(NextMeetingWidget.shouldSkip(1_000, 2_000, false))
    @Test fun runsWhenClockWentBack() = assertFalse(NextMeetingWidget.shouldSkip(90_000, 1_000, true))
}
