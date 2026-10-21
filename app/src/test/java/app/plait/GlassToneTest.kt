package app.plait

import androidx.compose.ui.graphics.Color
import app.plait.ui.BadgeKind
import app.plait.ui.ButtonSize
import app.plait.ui.ControlHeights
import app.plait.ui.GlassTone
import app.plait.ui.ToneStyles
import app.plait.ui.badgeTone
import app.plait.ui.toneAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassToneTest {
    @Test fun heightScaleValues() {
        assertEquals(40f, ControlHeights.Small.value, 0f)
        assertEquals(48f, ControlHeights.Medium.value, 0f)
        assertEquals(56f, ControlHeights.Large.value, 0f)
        assertEquals(64f, ControlHeights.Bar.value, 0f)
    }

    @Test fun heightScaleIsMonotonic() {
        assertTrue(ControlHeights.Small < ControlHeights.Medium)
        assertTrue(ControlHeights.Medium < ControlHeights.Large)
        assertTrue(ControlHeights.Large < ControlHeights.Bar)
    }

    @Test fun fabMatchesBarAndIconButtonsMatchButtons() {
        assertEquals(ControlHeights.Bar, ControlHeights.Fab)
        assertEquals(ControlHeights.Medium, ControlHeights.IconButton)
    }

    @Test fun buttonSizesComeFromTheScale() {
        assertEquals(ControlHeights.Small, ButtonSize.Small.minHeight)
        assertEquals(ControlHeights.Medium, ButtonSize.Regular.minHeight)
        assertEquals(ControlHeights.Large, ButtonSize.Large.minHeight)
    }

    @Test fun inkIsWhiteOnDarkAndBlackOnLight() {
        GlassTone.entries.forEach {
            assertEquals(Color.White, ToneStyles.resolve(it, dark = true).ink)
            assertEquals(Color.Black, ToneStyles.resolve(it, dark = false).ink)
        }
    }

    @Test fun selectedIsStrongerThanResting() {
        GlassTone.entries.forEach {
            assertTrue(ToneStyles.resolve(it, true, selected = true).fill.alpha > ToneStyles.resolve(it, true).fill.alpha)
            assertNotEquals(ToneStyles.resolve(it, false, selected = true).fill, ToneStyles.resolve(it, false).fill)
        }
    }

    @Test fun flatRimIsSixtyPercentOfGlassRim() {
        val full = ToneStyles.resolve(GlassTone.Mint, false).rim.alpha
        val flat = ToneStyles.resolve(GlassTone.Mint, false, rimStrength = ToneStyles.FLAT_RIM).rim.alpha
        assertEquals(full * 0.6f, flat, 0.01f)
    }

    @Test fun lightFillIsThePastel() {
        assertEquals(GlassTone.Lavender.pastel.copy(alpha = 0.92f), ToneStyles.resolve(GlassTone.Lavender, false).fill)
    }

    @Test fun fromTintKeepsAWashNotASolid() {
        val f = ToneStyles.fromTint(Color(0xFFE8725F).copy(alpha = 0.9f), dark = false).fill
        assertEquals(0.5f, f.alpha, 0.01f)
        assertEquals(0.28f, ToneStyles.fromTint(Color(0xFFE8725F).copy(alpha = 0.1f), dark = true).fill.alpha, 0.01f)
    }

    @Test fun badgesMapToTones() {
        assertEquals(GlassTone.Mint, badgeTone(BadgeKind.Default))
        assertEquals(GlassTone.Mint, badgeTone(BadgeKind.Included))
        assertEquals(GlassTone.Lavender, badgeTone(BadgeKind.BestMatch))
        assertEquals(GlassTone.Sky, badgeTone(BadgeKind.Size))
        assertEquals(GlassTone.Rose, badgeTone(BadgeKind.Warning))
        BadgeKind.entries.forEach { badgeTone(it) }
    }

    @Test fun menuRowTonesRotateAndWrap() {
        assertEquals(GlassTone.Lavender, toneAt(0))
        assertEquals(GlassTone.entries[1], toneAt(1))
        assertEquals(GlassTone.Lavender, toneAt(GlassTone.entries.size))
        assertEquals(GlassTone.entries.last(), toneAt(-1))
    }
}
