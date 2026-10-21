package app.plait

import androidx.compose.ui.graphics.Color
import app.plait.ui.screens.BlurBandDp
import app.plait.ui.screens.bandStartOf
import app.plait.ui.screens.blurRadiusAt
import app.plait.ui.screens.blurTint
import app.plait.ui.screens.tintAlphaAt
import app.plait.ui.screens.usesShader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveBlurTest {
    private val h = 600f
    private val start = bandStartOf(h, 88f)

    @Test fun bandStartIsHeightMinusBand() {
        assertEquals(512f, start, 0f)
        assertEquals(0f, bandStartOf(50f, 88f), 0f)
        assertEquals(88f, BlurBandDp, 0f)
    }

    @Test fun radiusIsZeroAboveBandAndMaxAtBottom() {
        assertEquals(0f, blurRadiusAt(0f, h, start, 14f), 0f)
        assertEquals(0f, blurRadiusAt(start, h, start, 14f), 0f)
        assertEquals(14f, blurRadiusAt(h, h, start, 14f), 1e-4f)
    }

    @Test fun radiusIncreases() {
        val ys = listOf(start, start + 20, start + 40, start + 60, h)
        val r = ys.map { blurRadiusAt(it, h, start, 14f) }
        assertTrue(r.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun tintAlphaMonotonicAndCapped() {
        val a = (0..20).map { tintAlphaAt(start + (h - start) * it / 20f, h, start) }
        assertTrue(a.zipWithNext().all { (x, y) -> y >= x })
        assertEquals(0.55f, a.last(), 1e-4f)
        assertEquals(0.55f, tintAlphaAt(h, h, start, 0.9f), 1e-4f)
        assertEquals(0f, tintAlphaAt(10f, h, start), 0f)
    }

    @Test fun tintByMode() {
        val c = Color(0xFF123456)
        assertEquals(Color(0xFF0E1316), blurTint(dark = true, material = false, container = c))
        assertEquals(Color.White, blurTint(dark = false, material = false, container = c))
        assertEquals(c, blurTint(dark = true, material = true, container = c))
        assertEquals(c, blurTint(dark = false, material = true, container = c))
    }

    @Test fun shaderNeedsApi33() {
        assertFalse(usesShader(26)); assertFalse(usesShader(31)); assertFalse(usesShader(32))
        assertTrue(usesShader(33)); assertTrue(usesShader(36))
    }
}
