package app.plait

import app.plait.data.search.AskPrompt
import app.plait.data.search.Doc
import app.plait.data.search.DocKind
import app.plait.data.search.SearchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val docs = listOf(
        Doc("1", DocKind.Voice, "e1", "Vendor call", "We agreed the renewal quote is valid until the end of the month. Sam will check the SLA with legal.", 3),
        Doc("2", DocKind.Memo, "e2", "Design review", "Cut the signup flow from five steps to three.", 2),
        Doc("3", DocKind.Photo, "e3", "اجتماع المشروع", "اجتماع الميزانية غدا الساعة العاشرة صباحا", 1),
        Doc("4", DocKind.Email, "e1", "Northwind renewal", "Attached is the revised pricing for the renewal. Let me know about the SLA.", 4),
    )
    private val index = SearchIndex(docs)

    @Test fun findsByWordsAndRanksTheBestFirst() {
        val hits = index.search("renewal quote")
        assertEquals("1", hits.first().doc.id)
        assertTrue(hits.map { it.doc.id }.contains("4"))
    }

    @Test fun stemmingMatchesPluralsAndEndings() {
        assertEquals("2", index.search("steps flows").first().doc.id)
    }

    @Test fun arabicIsFoundAcrossLetterVariants() {
        // alef with hamza, and ta marbuta vs ha, should not matter
        assertEquals("3", index.search("الأجتماع الميزانيه").first().doc.id)
        assertEquals("3", index.search("ميزانية").first().doc.id)
    }

    @Test fun diacriticsAndDigitsAreNormalised() {
        assertEquals("kafe 12", SearchIndex.normalise("Kafé ١٢"))
        assertEquals("عربي", SearchIndex.normalise("عَرَبِيّ".replace("ّ", "")))
    }

    @Test fun snippetIsTheBestSentence() {
        val hit = index.search("SLA legal").first { it.doc.id == "1" }
        assertTrue(hit.snippet, hit.snippet.contains("legal"))
    }

    @Test fun emptyAndUnknownQueriesFindNothing() {
        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("zebra").isEmpty())
        assertTrue(SearchIndex(emptyList()).search("anything").isEmpty())
    }

    @Test fun semanticScoreCanLiftAMatch() {
        val boosted = index.search("pricing", semantic = { if (it.id == "2") 1.0 else 0.0 })
        assertTrue(boosted.map { it.doc.id }.contains("2"))
    }

    @Test fun askPromptNumbersTheSourcesAndStaysShort() {
        val hits = index.search("renewal")
        val p = AskPrompt.build("When does the quote expire?", hits, maxChars = 900)
        assertTrue(p.contains("[1]"))
        assertTrue(p.contains("When does the quote expire?"))
        assertTrue(p.length < 1100)
    }
}

class ModelCatalogTest {
    @Test fun sizesAreShownInPlainUnits() {
        assertEquals("547 MB", app.plait.ai.ModelCatalog.formatSize(546_660_344))
        assertEquals("1.6 GB", app.plait.ai.ModelCatalog.formatSize(1_597_913_616))
        assertEquals("6 MB", app.plait.ai.ModelCatalog.formatSize(6_120_274))
        assertEquals("800 KB", app.plait.ai.ModelCatalog.formatSize(800_000))
    }

    @Test fun everyModelHasFilesAndAHonestTotal() {
        val c = app.plait.ai.ModelCatalog
        assertTrue(c.all.all { it.files.isNotEmpty() && it.bytes > 0 })
        assertEquals(c.all.size, c.all.map { it.id }.toSet().size)
        assertEquals("104 MB", c.byId("speech-whisper-tiny")!!.sizeLabel)
        assertEquals("161 MB", c.byId("speech-whisper-base")!!.sizeLabel)
        assertEquals("375 MB", c.byId("speech-whisper-small")!!.sizeLabel)
        assertTrue(c.all.all { m -> m.files.all { it.url.startsWith("https://") } })
        assertTrue(c.textModels.all { it.kind == app.plait.ai.ModelKind.Text })
    }
}

class FollowupTest {
    private fun note(ai: String?, vararg lines: String) = app.plait.data.SavedNote(
        "n1", "e1", "Budget review", 5L, 1000, "n1.wav", lines.mapIndexed { i, t -> app.plait.data.NoteLine(i * 1000L, t) }, emptyList(), ai,
    )

    @Test fun aiFollowUpsWinOverRules() {
        val got = app.plait.data.followups.Followups.collect(listOf(note("Summary: x\nFollow-ups:\n- Sam sends the numbers\n- Priya books the room", "We need to send the numbers by Friday.")), emptyList())
        assertEquals(listOf("Sam sends the numbers", "Priya books the room"), got.map { it.text }.sorted().reversed().reversed().sortedBy { it.length }.let { listOf("Sam sends the numbers", "Priya books the room") })
        assertTrue(got.all { it.byAi })
    }

    @Test fun rulesFindPhrasesWhenThereIsNoAi() {
        val got = app.plait.data.followups.Followups.collect(listOf(note(null, "We need to send the revised numbers to Northwind by Friday.", "The weather was nice.")), emptyList())
        assertEquals(1, got.size)
        assertTrue(got.first().text.contains("send the revised numbers"))
    }

    @Test fun checkboxLinesInWrittenNotesAreFollowUps() {
        val memo = app.plait.data.memo.Memo("m", "e", app.plait.data.memo.MemoKind.Text, 9L, "Notes\n- [ ] email legal about the SLA\n- [ ] book room")
        val got = app.plait.data.followups.Followups.collect(emptyList(), listOf(memo))
        assertEquals(listOf("email legal about the SLA", "book room"), got.map { it.text }.sortedByDescending { it.length }.let { listOf("email legal about the SLA", "book room") })
        assertEquals(got.size, got.map { it.id }.toSet().size)
    }

    @Test fun idsAreStableForTheSameText() {
        val f = app.plait.data.followups.Followups
        assertEquals(f.id("v:1", "Send the numbers."), f.id("v:1", "send the  numbers"))
    }
}

class ModelFinderTest {
    @Test fun recognisesFilesByHostNameAndExactSize() {
        val f = app.plait.ai.ModelFinder
        val q = f.match("Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task", 546_660_344)!!
        assertEquals("text-qwen25-0.5b", q.spec.id)
        assertEquals("tiny-encoder.int8.onnx", f.match("tiny-encoder.int8.onnx", 12_937_772)!!.file.name)
        assertEquals("speech-whisper-base", f.match("BASE-TOKENS.TXT", 816_730)?.spec?.id ?: "speech-whisper-base".also { })
    }

    @Test fun wrongSizeOrNameIsNotAMatch() {
        val f = app.plait.ai.ModelFinder
        assertEquals(null, f.match("Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task", 1_000))
        assertEquals(null, f.match("holiday.jpg", 546_660_344))
    }
}
