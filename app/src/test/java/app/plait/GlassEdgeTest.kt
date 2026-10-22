package app.plait

import androidx.compose.ui.graphics.Color
import app.plait.ui.screens.bandHeightDp
import app.plait.ui.screens.edgeTint
import app.plait.ui.screens.edgeTintAlpha
import app.plait.ui.screens.fadeCurve
import app.plait.ui.screens.featherAlphaAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassEdgeTest {
    @Test fun band() = assertEquals(130f, bandHeightDp(), 0f)

    @Test fun tintByMode() {
        val c = Color(0xFF123456)
        assertEquals(Color(0xFF3A3A3E), edgeTint(dark = true, material = false, container = c))
        assertEquals(Color.White, edgeTint(dark = false, material = false, container = c))
        assertEquals(c, edgeTint(dark = true, material = true, container = c))
        assertEquals(c, edgeTint(dark = false, material = true, container = c))
    }

    @Test fun alphas() {
        assertEquals(0.97f, edgeTintAlpha(dark = false, material = false), 0f)
        assertEquals(0.96f, edgeTintAlpha(dark = true, material = false), 0f)
        assertEquals(0.97f, edgeTintAlpha(dark = true, material = true), 0f)
    }

    @Test fun featherRamps() {
        assertEquals(0f, featherAlphaAt(0f, 60f), 0f)
        assertEquals(1f, featherAlphaAt(60f, 60f), 0f)
        assertEquals(1f, featherAlphaAt(200f, 60f), 0f)
        assertEquals(0f, featherAlphaAt(-5f, 60f), 0f)
        assertEquals(1f, featherAlphaAt(0f, 0f), 0f)
        val a = (0..12).map { featherAlphaAt(it * 5f, 60f) }
        assertTrue(a.zipWithNext().all { (x, y) -> y >= x })
    }

    @Test fun fadeCurveIsFlatAtEnds() {
        assertEquals(0f, fadeCurve(0f), 0f)
        assertEquals(1f, fadeCurve(1f), 0f)
        assertEquals(0.5f, fadeCurve(0.5f), 1e-6f)
        assertTrue(fadeCurve(0.1f) < 0.1f)
    }
}
