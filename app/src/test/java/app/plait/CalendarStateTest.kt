package app.plait

import app.plait.ui.screens.CalView
import app.plait.ui.screens.CalendarState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CalendarStateTest {
    private val oct20 = LocalDate.of(2026, 10, 20)

    @Test fun steppingMovesByTheViewsUnit() {
        val s = CalendarState(oct20)
        s.switchTo(CalView.Month); s.step(1)
        assertEquals(LocalDate.of(2026, 11, 20), s.anchor)
        s.switchTo(CalView.Day); s.step(1)
        assertEquals(LocalDate.of(2026, 11, 21), s.anchor)
        assertEquals(1, s.direction)
        s.step(-1)
        assertEquals(-1, s.direction)
    }

    @Test fun selectingADayOpensThePaneAndKeepsTheMonth() {
        val s = CalendarState(oct20)
        s.select(LocalDate.of(2026, 10, 7))
        assertEquals(LocalDate.of(2026, 10, 7), s.selected)
        assertEquals(oct20, s.anchor)
    }

    @Test fun selectingADayInAnotherMonthTurnsThePage() {
        val s = CalendarState(oct20)
        s.select(LocalDate.of(2026, 11, 1))
        assertEquals(LocalDate.of(2026, 11, 1), s.anchor)
        assertEquals(LocalDate.of(2026, 11, 1), s.selected)
        assertEquals(1, s.direction)
        s.select(LocalDate.of(2026, 9, 30))
        assertEquals(-1, s.direction)
    }

    @Test fun anOpenPaneFollowsTheMonthWhenStepping() {
        val s = CalendarState(oct20)
        s.select(LocalDate.of(2026, 10, 31))
        s.step(1)
        assertEquals(LocalDate.of(2026, 11, 30), s.selected) // clamped to the shorter month
    }

    @Test fun inTheDayViewSelectingJustNavigates() {
        val s = CalendarState(oct20)
        s.switchTo(CalView.Day)
        s.select(LocalDate.of(2026, 10, 22))
        assertEquals(LocalDate.of(2026, 10, 22), s.anchor)
        assertNull(s.selected)
    }

    @Test fun leavingForTheDayViewClosesThePaneAndKeepsTheDay() {
        val s = CalendarState(oct20)
        s.select(LocalDate.of(2026, 10, 7))
        s.switchTo(CalView.Day)
        assertNull(s.selected)
        assertEquals(LocalDate.of(2026, 10, 7), s.anchor)
    }

    @Test fun dayViewSelectionIsTheCenteredDayAndOpensNoSheet() {
        val s = CalendarState(oct20)
        s.switchTo(CalView.Day)
        s.select(oct20.plusDays(10))
        assertEquals(oct20.plusDays(10), s.anchor)
        assertEquals(null, s.selected)
    }

    @Test fun leavingMonthForDayClosesTheSheet() {
        val s = CalendarState(oct20)
        s.select(oct20.plusDays(1))
        s.switchTo(CalView.Day)
        assertEquals(null, s.selected)
        assertEquals(oct20.plusDays(1), s.anchor)
    }

    @Test fun todayJumpsBackAndKeepsAnOpenPaneOnToday() {
        val s = CalendarState(oct20)
        s.step(3)
        s.select(s.anchor)
        s.goToday(oct20)
        assertEquals(oct20, s.anchor)
        assertEquals(oct20, s.selected)
        assertEquals(-1, s.direction)
    }

    @Test fun closeClearsTheSelection() {
        val s = CalendarState(oct20)
        s.select(oct20)
        s.close()
        assertNull(s.selected)
    }
}
