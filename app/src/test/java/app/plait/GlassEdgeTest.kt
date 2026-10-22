package app.plait

import androidx.compose.ui.graphics.Color
import app.plait.ui.screens.bandHeightDp
import app.plait.ui.screens.edgeBlurRadiusDp
import app.plait.ui.screens.edgeTint
import app.plait.ui.screens.edgeTintAlpha
import app.plait.ui.screens.featherAlphaAt
import app.plait.ui.screens.rimAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassEdgeTest {
    @Test fun band() = assertEquals(72f, bandHeightDp(), 0f)

    @Test fun tintByMode() {
        val c = Color(0xFF123456)
        assertEquals(Color(0xFF0E1316), edgeTint(dark = true, material = false, container = c))
        assertEquals(Color.White, edgeTint(dark = false, material = false, container = c))
        assertEquals(c, edgeTint(dark = true, material = true, container = c))
        assertEquals(c, edgeTint(dark = false, material = true, container = c))
    }

    @Test fun alphas() {
        assertEquals(0.22f, edgeTintAlpha(dark = false, material = false), 0f)
        assertEquals(0.30f, edgeTintAlpha(dark = true, material = false), 0f)
        assertEquals(0.25f, edgeTintAlpha(dark = true, material = true), 0f)
        assertEquals(0.35f, rimAlpha(false), 0f)
        assertEquals(0.18f, rimAlpha(true), 0f)
    }

    @Test fun blurNeedsGlassAndApi31() {
        assertEquals(18f, edgeBlurRadiusDp(true, 31))
        assertNotNull(edgeBlurRadiusDp(true, 36))
        assertNull(edgeBlurRadiusDp(false, 36))
        assertNull(edgeBlurRadiusDp(true, 30))
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
}
