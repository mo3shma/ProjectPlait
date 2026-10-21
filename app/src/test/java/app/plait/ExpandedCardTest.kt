package app.plait

import androidx.compose.ui.geometry.Rect
import app.plait.ui.screens.ExpandDamping
import app.plait.ui.screens.ExpandStiffness
import app.plait.ui.screens.GlassCardRadius
import app.plait.ui.screens.MaterialCardRadius
import app.plait.ui.screens.OpenRadius
import app.plait.ui.screens.PullToCloseDp
import app.plait.ui.screens.expandedBounds
import app.plait.ui.screens.morphRadius
import app.plait.ui.screens.quoteSizes
import app.plait.ui.screens.shouldClose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedCardTest {
    private val card = Rect(18f, 400f, 342f, 900f)
    private val area = Rect(18f, 100f, 342f, 1500f)

    @Test fun boundsGrowFromTheCardToTheArea() {
        assertEquals(card, expandedBounds(card, area, 0f))
        assertEquals(area, expandedBounds(card, area, 1f))
        assertEquals(Rect(18f, 250f, 342f, 1200f), expandedBounds(card, area, 0.5f))
    }

    @Test fun cornersMorphToTheOpenRadius() {
        assertEquals(GlassCardRadius, morphRadius(GlassCardRadius, 0f), 1e-6f)
        assertEquals(OpenRadius, morphRadius(GlassCardRadius, 1f), 1e-6f)
        assertEquals(40f, morphRadius(GlassCardRadius, 0.5f), 1e-6f)
        // Material cards already have the open radius.
        assertEquals(OpenRadius, morphRadius(MaterialCardRadius, 0.3f), 1e-6f)
    }

    @Test fun quoteSizeShrinksWithLength() {
        assertEquals(32 to 40, quoteSizes(1))
        assertEquals(32 to 40, quoteSizes(60))
        assertEquals(28 to 36, quoteSizes(61))
        assertEquals(28 to 36, quoteSizes(100))
        assertEquals(24 to 32, quoteSizes(101))
        assertEquals(24 to 32, quoteSizes(400))
    }

    @Test fun pullClosesOnlyPastTheThreshold() {
        assertEquals(120f, PullToCloseDp, 0f)
        assertFalse(shouldClose(119f, 120f))
        assertFalse(shouldClose(120f, 120f))
        assertTrue(shouldClose(121f, 120f))
        assertFalse(shouldClose(-300f, 120f))
    }

    @Test fun openSpring() {
        assertEquals(0.8f, ExpandDamping, 0f)
        assertEquals(380f, ExpandStiffness, 0f)
    }
}
