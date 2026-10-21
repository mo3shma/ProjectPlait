package app.plait

import app.plait.ui.screens.envelope
import app.plait.ui.screens.loudnessOf
import app.plait.ui.screens.smoothAttackRelease
import app.plait.ui.screens.waveY
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceWavesTest {
    private fun constant(v: Int, n: Int = 320) = ShortArray(n) { v.toShort() }

    @Test fun silenceIsZero() {
        assertEquals(0f, loudnessOf(ShortArray(320), 320), 0f)
        assertEquals(0f, loudnessOf(constant(1), 320), 0f) // about -90 dB
    }

    @Test fun fullScaleIsOne() {
        assertEquals(1f, loudnessOf(constant(32767), 320), 1e-6f)
        assertEquals(1f, loudnessOf(constant(-32768), 320), 1e-6f)
    }

    @Test fun midLevelsMapLinearlyInDb() {
        // -31 dB is the middle of -52..-10.
        val v = (32768 * Math.pow(10.0, -31.0 / 20)).toInt()
        assertEquals(0.5f, loudnessOf(constant(v), 320), 0.01f)
        // -52 dB and below is 0, -10 dB and above is 1.
        assertEquals(0f, loudnessOf(constant((32768 * Math.pow(10.0, -60.0 / 20)).toInt()), 320), 0f)
        assertEquals(1f, loudnessOf(constant((32768 * Math.pow(10.0, -5.0 / 20)).toInt()), 320), 0f)
    }

    @Test fun shortBuffersOnlyCountTheirSamples() {
        val buf = ShortArray(320).also { it.fill(32767, 0, 10) }
        assertEquals(1f, loudnessOf(buf, 10), 1e-6f)
        assertEquals(0f, loudnessOf(buf, 0), 0f)
        assertEquals(0f, loudnessOf(buf, -3), 0f)
        assertEquals(1f, loudnessOf(buf.copyOf(10), 500), 1e-6f) // n beyond the array is clamped
    }

    @Test fun smoothingConvergesAndFallsSlower() {
        var a = 0f
        repeat(120) { a = smoothAttackRelease(a, 1f, 0.016f) }
        assertEquals(1f, a, 0.001f)
        var up = smoothAttackRelease(0f, 1f, 0.016f)
        var down = 1f - smoothAttackRelease(1f, 0f, 0.016f)
        assertTrue(up > down)
    }

    @Test fun smoothingIsFrameRateIndependent() {
        val once = smoothAttackRelease(0.2f, 0.9f, 0.016f)
        val twice = smoothAttackRelease(smoothAttackRelease(0.2f, 0.9f, 0.008f), 0.9f, 0.008f)
        assertEquals(once, twice, 1e-4f)
        val dOnce = smoothAttackRelease(0.9f, 0.1f, 0.016f)
        val dTwice = smoothAttackRelease(smoothAttackRelease(0.9f, 0.1f, 0.008f), 0.1f, 0.008f)
        assertEquals(dOnce, dTwice, 1e-4f)
    }

    @Test fun envelopeIsFlatAtTheEdgesAndFullInTheMiddle() {
        assertEquals(0f, envelope(0f), 1e-6f)
        assertEquals(0f, envelope(1f), 1e-6f)
        assertEquals(1f, envelope(0.5f), 1e-6f)
    }

    @Test fun waveIsSymmetricAndBounded() {
        for (i in 0..100) {
            val x = i / 100f
            val up = waveY(100f, 50f, x, 2f, 0f) - 100f
            val mirrored = waveY(100f, 50f, 1f - x, 2f, 0f) - 100f
            assertEquals(-up, mirrored, 1e-3f)
            assertTrue(abs(up) <= 50f + 1e-3f)
        }
        assertEquals(100f, waveY(100f, 50f, 0f, 1.2f, 1.3f), 1e-4f)
        assertEquals(100f, waveY(100f, 50f, 1f, 3.1f, 2.2f), 1e-3f)
    }
}
