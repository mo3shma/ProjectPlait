package app.plait

import androidx.compose.ui.geometry.Offset
import app.plait.ui.screens.cellAt
import org.junit.Assert.assertEquals
import org.junit.Test

/** The hand-drawn month grid mirrors in Arabic: a touch at the right edge is the week's first day. */
class CalendarGridRtlTest {
    private fun at(x: Float, y: Float, rtl: Boolean) = cellAt(Offset(x, y), 700f, 630f, 30f, 6, rtl)

    @Test fun leftToRight() {
        assertEquals(0, at(5f, 40f, false))
        assertEquals(6, at(695f, 40f, false))
        assertEquals(8, at(150f, 140f, false))
        assertEquals(-1, at(5f, 10f, false))
    }

    @Test fun rightToLeft() {
        assertEquals(6, at(5f, 40f, true))
        assertEquals(0, at(695f, 40f, true))
        assertEquals(12, at(150f, 140f, true))
    }
}
