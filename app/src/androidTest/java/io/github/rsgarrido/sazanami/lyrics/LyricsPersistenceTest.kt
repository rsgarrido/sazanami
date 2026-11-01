package io.github.rsgarrido.sazanami.lyrics

import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.backup.LyricsBackup
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricsPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "lyrics-persistence-${UUID.randomUUID()}.db"
    private lateinit var database: AppDatabase
    private lateinit var storage: LyricsOverrideStore
    private val parser = LrcParser()
    private val song = song(1, "primary")
    @Before fun setUp() = runBlocking { reopen(); storage.updateLibrary(listOf(song)) }
    @After fun tearDown() { database.close(); context.deleteDatabase(name) }
    private fun reopen() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        storage = LyricsOverrideStore(database)
    }
    private suspend fun baseline(song: Song = this.song): LyricsDraftState {
        val rows = storage.associated(song.toLyricsIdentity())
        return LyricsDraftState(storage.epoch(), "", "", rows.singleOrNull()?.id, rows.singleOrNull()?.revision, "baseline")
    }

    @Test fun savesReplacesRemovesAndSurvivesDatabaseAndRepositoryRecreation() = runBlocking {
        val target = song.toLyricsIdentity()
        val initial = baseline()
        val first = storage.commit(target, parser.parseSource("Original local text"), initial) as LyricsCommitResult.Saved
        database.close()
        reopen()
        storage.updateLibrary(listOf(song))
        val loaded = storage.find(target) as LyricsLookupResult.Found
        assertEquals(LyricsSource.LocalOverride(first.row.id), loaded.lyrics.source)
        assertEquals("Original local text", loaded.lyrics.sourceDocument!!.originalText)
        val second = storage.commit(target, parser.parseSource("[00:01]<00:01>Edited"), baseline()) as LyricsCommitResult.Saved
        assertEquals(first.row.id, second.row.id)
        assertNotEquals(first.row.revision, second.row.revision)
        assertEquals(1, database.lyricsDao().overrides().size)
        assertEquals(LyricsTiming.WORD, (storage.find(target) as LyricsLookupResult.Found).lyrics.timing)
        assertEquals(LyricsEditError.REVISION_CONFLICT, (storage.commit(target, parser.parseSource("Stale writer"), initial) as LyricsCommitResult.Failed).reason)
        assertNull(storage.remove(target, baseline()))
        assertNull(storage.find(target))
    }

    @Test fun failedTransactionalReplacementPreservesPreviousOverrideAndDraft() = runBlocking {
        val target = song.toLyricsIdentity()
        storage.commit(target, parser.parseSource("Previous"), baseline())
        val old = database.lyricsDao().overrides().single()
        val state = baseline()
        val changed = parser.parseSource("Changed")
        storage.checkpoint(target, changed.serializeUnchanged(), state)
        database.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_lyrics BEFORE INSERT ON lyrics_overrides
            WHEN NEW.checksum = '${changed.revision}' BEGIN SELECT RAISE(ABORT, 'synthetic storage failure'); END""")
        try {
            val result = storage.commit(target, changed, state) as LyricsCommitResult.Failed
            assertEquals(LyricsEditError.STORAGE, result.reason)
            val retained = database.lyricsDao().overrides().single()
            assertEquals(old.revision, retained.revision)
            assertArrayEquals(old.sourceBytes, retained.sourceBytes)
            assertNotNull(storage.loadDraft(target))
        } finally { database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_lyrics") }
    }

    @Test fun successfulCommitClearsDraftButFailedValidationAndRestoreRetainIt() = runBlocking {
        val target = song.toLyricsIdentity()
        val state = baseline()
        storage.checkpoint(target, "Draft work".toByteArray(), state.copy(mode = LyricsEditorMode.STRUCTURED, selectedLine = 2))
        database.close(); reopen(); storage.updateLibrary(listOf(song))
        assertEquals("Draft work", storage.loadDraft(target)!!.sourceBytes.toString(Charsets.UTF_8))
        assertEquals(LyricsEditError.EMPTY, (storage.commit(target, parser.parseSource(""), state) as LyricsCommitResult.Failed).reason)
        assertNotNull(storage.loadDraft(target))
        storage.commit(target, parser.parseSource("Committed"), state)
        assertNull(storage.loadDraft(target))
    }

    @Test fun sameIdOnDifferentVolumesNeverCollidesAndRescansRetainAssociation() = runBlocking {
        val card = song(1, "card")
        storage.updateLibrary(listOf(song, card))
        storage.commit(song.toLyricsIdentity(), parser.parseSource("Primary lyrics"), baseline(song))
        storage.commit(card.toLyricsIdentity(), parser.parseSource("Card lyrics"), baseline(card))
        assertEquals(2, database.lyricsDao().overrides().size)
        assertEquals("Primary lyrics", (storage.find(song.toLyricsIdentity()) as LyricsLookupResult.Found).lyrics.sourceDocument!!.originalText)
        assertEquals("Card lyrics", (storage.find(card.toLyricsIdentity()) as LyricsLookupResult.Found).lyrics.sourceDocument!!.originalText)
        val rescanned = song(99, "primary").copy(title = "Retagged")
        storage.updateLibrary(listOf(rescanned, card))
        assertEquals("Primary lyrics", (storage.find(rescanned.toLyricsIdentity()) as LyricsLookupResult.Found).lyrics.sourceDocument!!.originalText)
        val portable = LyricsBackup.export(database.lyricsDao().overrides())
        database.withTransaction { LyricsBackup.restoreWithinTransaction(database, LyricsBackup.validate(portable)) }
        storage.updateLibrary(listOf(rescanned, card))
        assertTrue(database.lyricsDao().overrides().all { it.reconciliation == "AMBIGUOUS" && it.boundTrackKey == null })
        assertNull(storage.find(rescanned.toLyricsIdentity()))
        assertEquals(2, database.lyricsDao().overrides().size)
    }

    @Test fun restoreIsAtomicInvalidPayloadCannotReplaceContentAndOldDraftCannotCommit() = runBlocking {
        val target = song.toLyricsIdentity()
        storage.commit(target, parser.parseSource("Original"), baseline())
        val before = database.lyricsDao().overrides().single()
        val state = baseline()
        storage.checkpoint(target, "Unfinished".toByteArray(), state)
        val payload = LyricsBackup.export(listOf(before)).single()
        try { LyricsBackup.validate(listOf(payload.copy(checksum = "invalid"))); fail("Expected validation failure") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before.revision, database.lyricsDao().overrides().single().revision)
        val epoch = storage.epoch()
        try {
            database.withTransaction {
                LyricsBackup.restoreWithinTransaction(database, emptyList())
                throw IOException("Synthetic failure restoring another table")
            }
        } catch (_: IOException) { }
        assertEquals(before.revision, database.lyricsDao().overrides().single().revision)
        assertEquals(epoch, storage.epoch())
        database.withTransaction { LyricsBackup.restoreWithinTransaction(database, LyricsBackup.validate(listOf(payload))) }
        storage.updateLibrary(listOf(song))
        assertNotNull(storage.loadDraft(target))
        val stale = storage.commit(target, parser.parseSource("Old draft"), state) as LyricsCommitResult.Failed
        assertEquals(LyricsEditError.RESTORE_CONFLICT, stale.reason)
    }

    @Test fun missingAndAmbiguousTracksAreTypedAndRecoveryRecordsRemainStored() = runBlocking {
        val target = song.toLyricsIdentity()
        val state = baseline()
        storage.updateLibrary(emptyList())
        assertEquals(LyricsEditError.TARGET_MISSING, (storage.commit(target, parser.parseSource("Work"), state) as LyricsCommitResult.Failed).reason)
        storage.updateLibrary(listOf(song, song.copy(title = "Conflicting library row")))
        assertEquals(LyricsEditError.TARGET_AMBIGUOUS, (storage.commit(target, parser.parseSource("Work"), state) as LyricsCommitResult.Failed).reason)
        assertTrue(database.lyricsDao().overrides().isEmpty())
    }

    @Test fun corruptedChecksumIsTypedAndInvalidReferenceCannotBreakLibraryPublication() = runBlocking {
        val target = song.toLyricsIdentity()
        storage.commit(target, parser.parseSource("Original"), baseline())
        val original = database.lyricsDao().overrides().single()
        database.lyricsDao().putOverride(original.copy(checksum = "corrupt"))
        val unavailable = storage.find(target) as LyricsLookupResult.Unavailable
        assertTrue(unavailable.issues.any { it.kind == LyricsSourceIssue.Kind.READ_FAILURE })
        database.lyricsDao().putOverride(original.copy(referenceJson = "invalid JSON"))
        storage.updateLibrary(listOf(song))
        assertEquals("INVALID_REFERENCE", database.lyricsDao().overrides().single().reconciliation)
        assertNull(storage.find(target))
        assertEquals(1, database.lyricsDao().overrides().size)
    }

    @Test fun unverifiedDraftIsNotTreatedAsAbsentBeforeLibraryPublication() = runBlocking {
        val target = song.toLyricsIdentity()
        storage.checkpoint(target, "Recover this work".toByteArray(), baseline())
        storage.updateLibrary(emptyList())
        try { storage.loadDraft(target); fail("Expected unverified target rejection") }
        catch (failure: LyricsEditException) { assertEquals(LyricsEditError.TARGET_MISSING, failure.reason) }
        assertEquals(1, database.lyricsDao().drafts().size)
        storage.updateLibrary(listOf(song))
        assertEquals("Recover this work", storage.loadDraft(target)!!.sourceBytes.toString(Charsets.UTF_8))
    }

    @Test fun draftLimitNeverSilentlyEvictsWorkAndExplicitDiscardFreesCapacity() = runBlocking {
        val state = baseline()
        val targets = (10L until 10L + LyricsEditingLimits.DRAFTS).map { song(it, "primary").toLyricsIdentity() }
        targets.forEachIndexed { index, target -> storage.checkpoint(target, "Draft $index".toByteArray(), state) }
        val extra = song(999, "primary").toLyricsIdentity()
        try { storage.checkpoint(extra, "New work".toByteArray(), state); fail("Expected bounded draft rejection") }
        catch (failure: LyricsEditException) { assertEquals(LyricsEditError.TOO_LARGE, failure.reason) }
        assertEquals(LyricsEditingLimits.DRAFTS, database.lyricsDao().drafts().size)
        assertEquals("Draft 0", database.lyricsDao().draft(targets.first().trackKey)!!.sourceBytes.toString(Charsets.UTF_8))
        storage.discardDraft(targets.first().trackKey)
        storage.checkpoint(extra, "New work".toByteArray(), state)
        assertEquals(LyricsEditingLimits.DRAFTS, database.lyricsDao().drafts().size)
        assertNotNull(database.lyricsDao().draft(extra.trackKey))
    }

    private fun song(id: Long, volume: String) = Song(id, "Synthetic", "Original author", "Album", 1, 10_000,
        Uri.parse("content://media/$volume/audio/media/$id"), "", "", null,
        volumeName = volume, relativePath = "Music/", displayName = "synthetic.mp3", fileSizeBytes = 100)
}
