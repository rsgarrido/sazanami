package com.example.cdplaya.data.importing.spotify

import com.example.cdplaya.data.importing.ImportOccurrenceKey
import com.example.cdplaya.data.importing.ImportProvider
import com.example.cdplaya.data.importing.ImportedCompletionEvidence
import com.example.cdplaya.data.importing.ImportedListeningRecord
import com.example.cdplaya.data.importing.ImportedMediaType
import com.example.cdplaya.data.importing.ImportedTimestampEvidence
import com.example.cdplaya.data.importing.ImportedTriState
import com.example.cdplaya.data.importing.ListeningImportSelectionBuilder
import com.example.cdplaya.data.importing.PreparedListeningOccurrence
import com.example.cdplaya.data.local.ListeningCompletionClassification
import com.example.cdplaya.data.local.ListeningEventPublicationState
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningTimestampEvidence
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyImportedEventMapperTest {
    @Test fun durationQualifiedUnknownReason_mapsExactEndOnlyPendingSemantics() {
        val occurrence = prepared(record(listenedMs = 31_000, reasonEnd = null, skipped = ImportedTriState.UNKNOWN))
        val event = SpotifyImportedEventMapper.map(occurrence, 7, "event", 2_000)

        assertNull(event.startedAt)
        assertEquals(1_000L, event.endedAt)
        assertEquals(1_000L, event.attributionAt)
        assertEquals(ListeningTimestampEvidence.SOURCE_END_ONLY, event.timestampEvidence)
        assertEquals(31_000L, event.listenedMs)
        assertNull(event.trackDurationMs)
        assertTrue(event.qualifiedAsPlay)
        assertEquals(ListeningQualificationReason.TIME_THRESHOLD, event.qualificationReason)
        assertEquals(ListeningCompletionClassification.NONE, event.completionClassification)
        assertNull(event.endReason)
        assertEquals(ListeningEventPublicationState.IMPORT_PENDING, event.publicationState)
    }

    @Test fun shortNaturalCompletionAndManualSkip_useOnlyPolicyEvidence() {
        val natural = SpotifyImportedEventMapper.map(
            prepared(record(5_000, "trackdone", ImportedTriState.FALSE)), 1, "natural", 2_000
        )
        val skipped = SpotifyImportedEventMapper.map(
            prepared(record(5_000, "fwdbtn", ImportedTriState.TRUE)), 2, "skip", 2_000
        )

        assertTrue(natural.qualifiedAsPlay)
        assertEquals(ListeningQualificationReason.NATURAL_END, natural.qualificationReason)
        assertEquals(ListeningCompletionClassification.SOURCE_DOCUMENTED_NATURAL,
            natural.completionClassification)
        assertFalse(skipped.qualifiedAsPlay)
        assertEquals(ListeningQualificationReason.NONE, skipped.qualificationReason)
        assertEquals(ListeningCompletionClassification.NONE, skipped.completionClassification)
    }

    @Test fun firstFileWithMaximumMultiplicity_ownsTheSelectedOccurrences() {
        val one = SpotifyListeningImportFingerprint.create(record(1_000, null, ImportedTriState.UNKNOWN))
        val builder = ListeningImportSelectionBuilder()
        builder.beginFile(); builder.accept(one); builder.endFile()
        builder.beginFile(); builder.accept(one); builder.accept(one); builder.endFile()
        val plan = builder.build()

        assertFalse(plan.isOccurrenceOwner(0, one))
        assertTrue(plan.isOccurrenceOwner(1, one))
        assertEquals(2L, plan.summary.selectedMusicOccurrences)
        assertEquals(1L, plan.summary.overlappingOccurrencesSuppressed)
    }

    private fun prepared(record: ImportedListeningRecord): PreparedListeningOccurrence {
        val fingerprint = SpotifyListeningImportFingerprint.create(record)
        return PreparedListeningOccurrence(
            ImportOccurrenceKey(fingerprint.fingerprintVersion, fingerprint.fingerprint, 0),
            record,
            SpotifyImportPolicy.evaluate(record)
        )
    }

    private fun record(
        listenedMs: Long,
        reasonEnd: String?,
        skipped: ImportedTriState
    ) = ImportedListeningRecord(
        provider = ImportProvider.SPOTIFY,
        externalMediaId = "spotify-id",
        mediaType = ImportedMediaType.MUSIC_TRACK,
        trackTitle = "Track",
        trackArtist = "Artist",
        albumTitle = "Album",
        albumArtist = "Artist",
        sourceStartedAt = null,
        sourceEndedAt = Instant.ofEpochMilli(1_000),
        timestampEvidence = ImportedTimestampEvidence.SOURCE_END_ONLY,
        listenedMs = listenedMs,
        skippedEvidence = skipped,
        completionEvidence = ImportedCompletionEvidence.UNKNOWN,
        providerReasonStart = null,
        providerReasonEnd = reasonEnd
    )
}
