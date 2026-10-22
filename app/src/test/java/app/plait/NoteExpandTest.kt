package app.plait

import androidx.compose.ui.geometry.Rect
import app.plait.ui.screens.fallbackFrom
import app.plait.ui.screens.noteBounds
import app.plait.ui.screens.noteContentAlpha
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteExpandTest {
    private val to = Rect(18f, 100f, 342f, 700f)

    @Test fun endpointsAreRowAndArea() {
        val row = Rect(18f, 400f, 342f, 520f)
        assertEquals(row, noteBounds(row, to, 0f))
        assertEquals(to, noteBounds(row, to, 1f))
    }

    @Test fun missingRowFallsBackToSmallerCenteredArea() {
        val f = fallbackFrom(to)
        assertEquals(to.center.x, f.center.x, 0.01f)
        assertEquals(to.center.y, f.center.y, 0.01f)
        assertEquals(to.width * 0.9f, f.width, 0.01f)
        assertEquals(f.left, noteBounds(null, to, 0f).left, 0.01f)
    }

    @Test fun contentFadesInLate() {
        assertEquals(0f, noteContentAlpha(0.2f), 0f)
        assertEquals(1f, noteContentAlpha(1f), 0f)
        assertEquals(0.5f, noteContentAlpha(0.675f), 0.001f)
    }
}
