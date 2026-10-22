package app.plait

import app.plait.ui.glass.menuIndexAt
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuHitTest {
    @Test fun rowsAreFoundByY() {
        assertEquals(0, menuIndexAt(108f, 108f, 56f, 3))
        assertEquals(0, menuIndexAt(163.9f, 108f, 56f, 3))
        assertEquals(1, menuIndexAt(164f, 108f, 56f, 3))
        assertEquals(2, menuIndexAt(275f, 108f, 56f, 3))
    }

    @Test fun offTheRowsIsMinusOne() {
        assertEquals(-1, menuIndexAt(107f, 108f, 56f, 3))
        assertEquals(-1, menuIndexAt(276f, 108f, 56f, 3))
        assertEquals(-1, menuIndexAt(10f, 0f, 0f, 3))
    }
}
