package app.plait

import app.plait.reminder.LiveLogic
import app.plait.reminder.TaskPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveAlertTest {
    private val m = 60_000L
    private val start = 1_000 * m
    private val end = start + 30 * m

    @Test fun snoozeIsNowPlusMinutes() = assertEquals(100 * m + 5 * m, LiveLogic.snoozeAt(100 * m, 5, end))

    @Test fun snoozeNeverReachesTheMeetingEnd() {
        assertNull(LiveLogic.snoozeAt(end - 5 * m, 5, end))
        assertNull(LiveLogic.snoozeAt(end - 3 * m, 10, end))
        assertEquals(end - m, LiveLogic.snoozeAt(end - 6 * m, 5, end))
        assertEquals(end - 4 * m, LiveLogic.snoozeAt(end - 9 * m, 5, end))
    }

    @Test fun snoozeWithUnknownEndStillWorks() = assertEquals(10 * m, LiveLogic.snoozeAt(0, 10, 0))

    @Test fun taskPhases() {
        val at = 500 * m
        assertEquals(TaskPhase.None, LiveLogic.taskPhase(at - 15 * m - 1, at))
        assertEquals(TaskPhase.Soon, LiveLogic.taskPhase(at - 15 * m, at))
        assertEquals(TaskPhase.Due, LiveLogic.taskPhase(at, at))
        assertEquals(TaskPhase.Due, LiveLogic.taskPhase(at + 99 * m, at))
    }

    @Test fun minutesLeftRoundsUp() {
        assertEquals(10, LiveLogic.minutesLeft(start - 10 * m + 100, start))
        assertEquals(1, LiveLogic.minutesLeft(start - 1, start))
        assertEquals(0, LiveLogic.minutesLeft(start, start))
    }
}
