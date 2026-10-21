package app.plait

import app.plait.data.CalEvent
import app.plait.data.Source
import app.plait.data.brief.DayAnalytics
import app.plait.data.brief.DayBriefBuilder
import app.plait.data.brief.tidyTitle
import app.plait.data.quotes.Quotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class QuotesTest {
    private val banned = listOf(
        "god", "allah", "lord", "bible", "church", "pray", "prophet", "jesus", "president",
        "prime minister", "minister", "election", "vote", "party", "government", "senator",
    )

    @Test fun listIsSane() {
        assertTrue(Quotes.all.size in 60..100)
        assertEquals(Quotes.all.size, Quotes.all.map { it.text }.toSet().size)
        Quotes.all.forEach {
            assertTrue(it.text, it.text.length <= 120)
            assertTrue(it.author.isNotBlank())
            assertTrue(it.text, it.text.all { c -> c.code in 32..126 })
        }
    }

    @Test fun noReligionOrPolitics() {
        Quotes.all.forEach { q ->
            val s = (q.text + " " + q.author).lowercase()
            banned.forEach { assertFalse("${q.text} has $it", Regex("\b${Regex.escape(it)}").containsMatchIn(s)) }
        }
    }

    @Test fun pickIsStableAndVaries() {
        val day = 20_000L
        assertEquals(Quotes.pick(day, 1), Quotes.pick(day, 1))
        assertEquals(3, (0..2).map { Quotes.pick(day, it) }.toSet().size)
        for (s in 0..2) assertTrue((0 until 30).map { Quotes.pick(day + it, s) }.toSet().size >= 25)
        for (s in 0..2) for (start in 0 until 20) {
            assertEquals(7, (0 until 7).map { Quotes.pick(day + start + it, s) }.toSet().size)
        }
    }

    // tidyTitle
    private val messy = "  MOBIHITS & Mobizone /  Weekly   Call  "
    private fun ev(id: String, title: String, from: String, to: String) =
        CalEvent(id, "", "", title, Source.Google, "", startMs = Instant.parse(from).toEpochMilli(), endMs = Instant.parse(to).toEpochMilli())

    @Test fun tidyTitleCollapsesWhitespace() {
        assertEquals("MOBIHITS & Mobizone / Weekly Call", tidyTitle(messy))
        assertEquals("", tidyTitle("   "))
    }

    @Test fun sentencesHaveNoDoubleSpaces() {
        val z = ZoneId.of("UTC")
        val a = ev("a", messy, "2026-10-19T09:00:00Z", "2026-10-19T10:00:00Z")
        val b = ev("b", messy, "2026-10-19T13:00:00Z", "2026-10-19T15:00:00Z")
        val now = Instant.parse("2026-10-19T08:00:00Z").toEpochMilli()
        val texts = listOf(
            DayBriefBuilder.build(listOf(a, b), emptyMap(), emptyMap(), now, z).let { it.headline + " " + it.summary },
            DayBriefBuilder.build(listOf(a, b), emptyMap(), emptyMap(), Instant.parse("2026-10-19T09:30:00Z").toEpochMilli(), z).let { it.headline + " " + it.summary },
            DayAnalytics.sense(listOf(a, b), now, z),
            DayAnalytics.sense(listOf(a, b), Instant.parse("2026-10-19T09:30:00Z").toEpochMilli(), z),
            DayAnalytics.recap(DayAnalytics.compute(listOf(a, b), emptyMap(), emptySet(), Instant.parse("2026-10-19T16:00:00Z").toEpochMilli(), z)),
        )
        texts.forEach { assertFalse(it, it.contains("  ")) }
        assertTrue(texts[0], texts[0].contains("Next is MOBIHITS & Mobizone / Weekly Call at 9:00 AM."))
        assertTrue(texts[4], texts[4].contains("The longest was MOBIHITS & Mobizone / Weekly Call at"))
    }
}
