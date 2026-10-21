package app.plait

import app.plait.ui.BadgeKind
import app.plait.ui.screens.BadgeSpec
import app.plait.ui.screens.aiRecommendedBadge
import app.plait.ui.screens.aiSummaryText
import app.plait.ui.screens.hubSectionIds
import app.plait.ui.screens.hubTone
import app.plait.ui.screens.modelBadges
import app.plait.ui.screens.noTranscriptionBadges
import app.plait.ui.screens.remindersSummaryText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDesignTest {
    @Test fun thirteenSectionsAreUnique() {
        assertEquals(13, hubSectionIds.size)
        assertEquals(13, hubSectionIds.toSet().size)
    }

    @Test fun neighbouringSectionsNeverShareATone() {
        hubSectionIds.zipWithNext().forEach { (a, b) -> assertNotEquals("$a / $b", hubTone(a), hubTone(b)) }
    }

    @Test fun aiHeroIsLavender() = assertEquals(app.plait.ui.GlassTone.Lavender, hubTone("ai"))

    @Test fun recommendedBadgeOnlyWhenNeitherIsInstalled() {
        assertEquals("Recommended: Qwen3 0.6B + Whisper Turbo", aiRecommendedBadge(false, false))
        assertNull(aiRecommendedBadge(true, false))
        assertNull(aiRecommendedBadge(false, true))
        assertNull(aiRecommendedBadge(true, true))
    }

    @Test fun modelBadges() {
        assertEquals(listOf(BadgeSpec("347 MB", BadgeKind.Size)), modelBadges("347 MB", isDefault = false))
        assertEquals(listOf(BadgeSpec("Default", BadgeKind.Default), BadgeSpec("347 MB", BadgeKind.Size)), modelBadges("347 MB", isDefault = true))
        assertEquals("Included", modelBadges("90 MB", isDefault = true, included = true).first().label)
    }

    @Test fun noTranscriptionBadges() {
        assertEquals(listOf(BadgeSpec("Nothing to download", BadgeKind.Info)), noTranscriptionBadges(false))
        assertEquals("Default", noTranscriptionBadges(true).first().label)
    }

    @Test fun summaries() {
        assertEquals("Writing: Automatic · Voice: none yet", aiSummaryText("Automatic", null))
        assertEquals("Writing: Rules only · Voice: Whisper Turbo", aiSummaryText("Rules only", "Whisper Turbo"))
        assertEquals("Off", remindersSummaryText(false, 10, true))
        assertEquals("10 min before", remindersSummaryText(true, 10, false))
        assertEquals("15 min before · evening reminder", remindersSummaryText(true, 15, true))
        assertTrue(aiRecommendedBadge(false, false)!!.startsWith("Recommended"))
    }
}
