package app.plait

import app.plait.data.NoteLine
import app.plait.data.SavedNote
import app.plait.data.memo.Memo
import app.plait.data.memo.MemoKind
import app.plait.data.prep.PrepEntry
import app.plait.data.sync.SyncMerge
import app.plait.data.sync.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTest {
    private fun note(id: String, text: String = "hello", ai: String? = null) = SavedNote(id, "e", "t", 1L, 1000, "$id.wav", listOf(NoteLine(0, text)), emptyList(), ai)
    private fun memo(id: String) = Memo(id, "e", MemoKind.Text, 1L, "x")

    @Test fun newItemsFromAnotherPhoneAreAdded() {
        val plan = SyncMerge.plan(SyncState(notes = listOf(note("a"))), SyncState(notes = listOf(note("a"), note("b")), memos = listOf(memo("m"))))
        assertEquals(listOf("b"), plan.addNotes.map { it.id })
        assertEquals(listOf("m"), plan.addMemos.map { it.id })
    }

    @Test fun aRicherVersionOfTheSameNoteWins() {
        val plan = SyncMerge.plan(SyncState(notes = listOf(note("a", "hi"))), SyncState(notes = listOf(note("a", "hi", ai = "Summary: x"))))
        assertEquals(listOf("a"), plan.updateNotes.map { it.id })
        val none = SyncMerge.plan(SyncState(notes = listOf(note("a", "hi", ai = "Summary: x"))), SyncState(notes = listOf(note("a", "hi"))))
        assertTrue(none.updateNotes.isEmpty())
    }

    @Test fun deletionsTravelAndStick() {
        val local = SyncState(notes = listOf(note("a"), note("b")))
        val remote = SyncState(notes = listOf(note("b")), deleted = mapOf("a" to 5L))
        val plan = SyncMerge.plan(local, remote)
        assertEquals(setOf("a"), plan.removeIds)
        assertEquals(mapOf("a" to 5L), plan.newDeleted)
        // an item deleted here is not brought back by a phone that still has it
        val back = SyncMerge.plan(SyncState(deleted = mapOf("z" to 1L)), SyncState(notes = listOf(note("z"))))
        assertTrue(back.addNotes.isEmpty())
    }

    @Test fun doneFollowUpsAndNewerPrepAreShared() {
        val local = SyncState(done = setOf("f1"), prep = mapOf("e" to PrepEntry("old", "You", 1)))
        val remote = SyncState(done = setOf("f1", "f2"), prep = mapOf("e" to PrepEntry("new", "You", 9)))
        val plan = SyncMerge.plan(local, remote)
        assertEquals(setOf("f2"), plan.newDone)
        assertEquals("new", plan.newPrep["e"]!!.text)
        val older = SyncMerge.plan(remote, local)
        assertTrue(older.newPrep.isEmpty())
    }

    @Test fun identicalStatesChangeNothing() {
        val s = SyncState(notes = listOf(note("a")), memos = listOf(memo("m")), done = setOf("f"))
        assertTrue(SyncMerge.plan(s, s).isEmpty)
    }

    @Test fun fingerprintFollowsContent() {
        val a = SyncState(notes = listOf(note("a")))
        assertEquals(SyncMerge.fingerprint(a), SyncMerge.fingerprint(a.copy()))
        assertFalse(SyncMerge.fingerprint(a) == SyncMerge.fingerprint(a.copy(done = setOf("x"))))
        assertFalse(SyncMerge.fingerprint(a) == SyncMerge.fingerprint(SyncState(notes = listOf(note("a", ai = "Summary: y")))))
    }
}

class BundleAndExtrasTest {
    @Test fun extrasAndBundleSurviveTheFile() {
        val dir = java.nio.file.Files.createTempDirectory("plait-bundle").toFile()
        try {
            val data = app.plait.data.backup.BackupData(
                1L, "Pixel", app.plait.data.backup.BackupSettings(), emptyList(), emptyList(), emptyList(),
                deleted = mapOf("x" to 7L), done = setOf("f1"), prep = mapOf("e" to PrepEntry("Goal: g", "You", 3)),
                bundle = app.plait.data.backup.BundleMeta("Vendor call", 99L, "Sam"),
            )
            val out = java.io.ByteArrayOutputStream()
            app.plait.data.backup.Backup.write(out, data, emptyList())
            val back = app.plait.data.backup.Backup.read(out.toByteArray().inputStream(), dir)
            assertEquals(mapOf("x" to 7L), back.deleted)
            assertEquals(setOf("f1"), back.done)
            assertEquals("Goal: g", back.prep["e"]!!.text)
            assertEquals("Sam", back.bundle!!.author)
            assertEquals(99L, back.bundle!!.startMs)
        } finally { dir.deleteRecursively() }
    }

    @Test fun authorsSurviveTheNoteAndMemoFiles() {
        val n = app.plait.data.SavedNote("n", "e", "t", 1, 1, "n.wav", emptyList(), emptyList(), author = "Sam")
        assertEquals("Sam", app.plait.data.NoteJson.decode(app.plait.data.NoteJson.encode(listOf(n))).single().author)
        val m = Memo("m", "e", MemoKind.Text, 1, "x", author = "Sam")
        assertEquals("Sam", app.plait.data.memo.MemoJson.decode(app.plait.data.memo.MemoJson.encode(listOf(m))).single().author)
        assertEquals(null, app.plait.data.NoteJson.decode(app.plait.data.NoteJson.encode(listOf(n.copy(author = null)))).single().author)
    }
}
