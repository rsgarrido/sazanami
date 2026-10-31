package io.github.rsgarrido.sazanami.lyrics

import android.net.Uri
import io.github.rsgarrido.sazanami.data.*
import io.github.rsgarrido.sazanami.data.local.LyricsOverrideEntity
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class LyricsPersistencePolicyTest {
    @Test fun revisionGuardRejectsLostUpdatesMissingRowsAndAmbiguousOverrides() {
        val source = LrcParser().parseSource("Original")
        val row = LyricsOverrideEntity("id", "{}", "key", source.serializeUnchanged(), "revision", source.revision,
            "PLAIN", "origin", "old", 1, 1, "BOUND")
        val baseline = LyricsDraftState("epoch", "origin", "old", baselineChecksum = source.revision)
        assertNull(LyricsCommitPolicy.previous(emptyList(), baseline))
        expect(LyricsEditError.REVISION_CONFLICT) { LyricsCommitPolicy.previous(listOf(row), baseline) }
        val editing = baseline.copy(overrideId = row.id, overrideRevision = row.revision)
        assertSame(row, LyricsCommitPolicy.previous(listOf(row), editing))
        expect(LyricsEditError.REVISION_CONFLICT) { LyricsCommitPolicy.previous(listOf(row.copy(revision = "other")), editing) }
        expect(LyricsEditError.REVISION_CONFLICT) { LyricsCommitPolicy.previous(emptyList(), editing) }
        expect(LyricsEditError.TARGET_AMBIGUOUS) { LyricsCommitPolicy.previous(listOf(row, row.copy(id = "other")), editing) }
    }

    @Test fun storageBudgetsAllowReplacementAndRejectUnboundedAccumulation() {
        LyricsCommitPolicy.checkBudget(0, 0, LyricsEditingLimits.DOCUMENT_BYTES)
        LyricsCommitPolicy.checkBudget(10, LyricsEditingLimits.TOTAL_OVERRIDE_BYTES - 10L, 10)
        expect(LyricsEditError.TOO_LARGE) { LyricsCommitPolicy.checkBudget(0, 0, LyricsEditingLimits.DOCUMENT_BYTES + 1) }
        expect(LyricsEditError.TOO_LARGE) { LyricsCommitPolicy.checkBudget(LyricsEditingLimits.OVERRIDES, 0, 10) }
        expect(LyricsEditError.TOO_LARGE) { LyricsCommitPolicy.checkBudget(0, LyricsEditingLimits.TOTAL_OVERRIDE_BYTES.toLong(), 1) }
    }

    @Test fun qualifiedIdentitySurvivesRetagAndRescanButRejectsMetadataOnlyReattachment() {
        val primary = song(1, "primary")
        val card = song(1, "card")
        val index = SongReferenceIndex.build(listOf(primary, card))
        assertEquals(primary, (resolveLyricsReference(primary.toSongReference(), index) as SongReferenceResolution.Resolved).song)
        assertEquals(card, (resolveLyricsReference(card.toSongReference(), index) as SongReferenceResolution.Resolved).song)
        val retagged = primary.copy(title = "New title", artist = "New artist")
        assertTrue(resolveLyricsReference(primary.toSongReference(), SongReferenceIndex.build(listOf(retagged))) is SongReferenceResolution.Resolved)
        val portable = primary.toSongReference().copy(mediaStoreId = null, contentUri = "", volumeName = "")
        val rescanned = song(88, "new-device")
        assertTrue(resolveLyricsReference(portable, SongReferenceIndex.build(listOf(rescanned))) is SongReferenceResolution.Resolved)
        assertTrue(resolveLyricsReference(portable, SongReferenceIndex.build(listOf(primary, card))) is SongReferenceResolution.Ambiguous)
        val unrelated = rescanned.copy(displayName = "unrelated.flac", relativePath = "Elsewhere/", fileSizeBytes = 999)
        assertEquals(SongReferenceResolution.NotFound, resolveLyricsReference(portable, SongReferenceIndex.build(listOf(unrelated))))
        assertEquals(SongReferenceResolution.NotFound, resolveLyricsReference(portable, SongReferenceIndex.EMPTY))
        val recycledId = primary.copy(displayName = "another.flac", relativePath = "Other/")
        val renumberedOriginal = song(99, "primary")
        assertEquals(renumberedOriginal, (resolveLyricsReference(primary.toSongReference(),
            SongReferenceIndex.build(listOf(recycledId, renumberedOriginal))) as SongReferenceResolution.Resolved).song)
    }
    @Test fun descriptiveReferenceSnapshotsAreBoundedWithoutTruncatingIdentity() {
        val original = song(1, "primary").toSongReference().copy(title = "x".repeat(10_000))
        val stored = LyricsOverrideStore.decodeReference(LyricsOverrideStore.encodeReference(original))
        assertEquals(4096, stored.title.length)
        assertEquals(original.contentUri, stored.contentUri)
        assertEquals(original.displayName, stored.displayName)
        assertEquals(original.volumeName, stored.volumeName)
        expect(LyricsEditError.TOO_LARGE) { LyricsOverrideStore.encodeReference(original.copy(displayName = "x".repeat(4097))) }
    }

    private fun song(id: Long, volume: String): Song {
        val uri = mock(Uri::class.java)
        doReturn("content://media/$volume/audio/$id").`when`(uri).toString()
        return Song(id, "Synthetic", "Author", "Album", 1, 10_000, uri, "", "", null,
            volumeName = volume, displayName = "track.flac", relativePath = "Music/", fileSizeBytes = 100)
    }
    private fun expect(reason: LyricsEditError, block: () -> Unit) {
        try { block(); fail("Expected $reason") } catch (failure: LyricsEditException) { assertEquals(reason, failure.reason) }
    }
}
