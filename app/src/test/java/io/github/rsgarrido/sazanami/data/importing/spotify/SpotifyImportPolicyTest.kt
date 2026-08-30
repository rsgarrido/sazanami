package io.github.rsgarrido.sazanami.data.importing.spotify

import io.github.rsgarrido.sazanami.data.importing.ImportProvider
import io.github.rsgarrido.sazanami.data.importing.ImportedCompletionEvidence
import io.github.rsgarrido.sazanami.data.importing.ImportedListeningRecord
import io.github.rsgarrido.sazanami.data.importing.ImportedMediaType
import io.github.rsgarrido.sazanami.data.importing.ImportedTimestampEvidence
import io.github.rsgarrido.sazanami.data.importing.ImportedTriState
import io.github.rsgarrido.sazanami.data.local.ImportedListeningSkippedState
import io.github.rsgarrido.sazanami.data.local.ListeningCompletionClassification
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationPolicy
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationReason
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyImportPolicyTest {
    @Test fun qualificationFixtureDefinesThresholdAndUnknownTokenBehavior() {
        val results = parseFixture("spotify_extended_qualification_edges.json")
            .map(SpotifyImportPolicy::evaluate)
        assertEquals(
            listOf(false, false, false, true, true, false, true),
            results.map { it.qualifiedAsPlay }
        )
        assertEquals(ListeningQualificationReason.TIME_THRESHOLD, results[3].qualificationReason)
        assertEquals(ListeningQualificationPolicy.SPOTIFY, results[3].qualificationPolicy)
        assertEquals(1, results[3].qualificationRuleVersion)
        assertEquals("future_reason_token", results.last().normalizedReasonEnd)
        assertEquals(ListeningCompletionClassification.NONE, results.last().completionClassification)
    }

    @Test fun completionIsConservativeAndIndependentFromQualification() {
        val short = record(listenedMs = 1_200, reasonEnd = "trackdone")
        val natural = SpotifyImportPolicy.evaluate(short.copy(skippedEvidence = ImportedTriState.FALSE))
        assertTrue(natural.qualifiedAsPlay)
        assertEquals(ListeningQualificationReason.NATURAL_END, natural.qualificationReason)
        assertEquals(
            ListeningCompletionClassification.SOURCE_DOCUMENTED_NATURAL,
            natural.completionClassification
        )

        listOf(ImportedTriState.TRUE, ImportedTriState.UNKNOWN).forEach { skipped ->
            val result = SpotifyImportPolicy.evaluate(short.copy(skippedEvidence = skipped))
            assertFalse(result.qualifiedAsPlay)
            assertEquals(ListeningCompletionClassification.NONE, result.completionClassification)
        }

        val qualifiedUnknownEnd = SpotifyImportPolicy.evaluate(
            record(31_000, "future-token", ImportedTriState.UNKNOWN)
        )
        assertTrue(qualifiedUnknownEnd.qualifiedAsPlay)
        assertEquals(ListeningCompletionClassification.NONE, qualifiedUnknownEnd.completionClassification)
    }

    @Test fun onlyExactObservedNaturalTokenIsRecognizedAndManualEndsAreNotCompletion() {
        val nonNatural = listOf("fwdbtn", "backbtn", "endplay", "unexpected-exit", "unknown", null)
        nonNatural.forEach { token ->
            val result = SpotifyImportPolicy.evaluate(
                record(20_000, token, ImportedTriState.FALSE)
            )
            assertFalse("Unexpected completion for $token", result.qualifiedAsPlay)
            assertEquals(ListeningCompletionClassification.NONE, result.completionClassification)
        }
        assertEquals(
            ListeningCompletionClassification.NONE,
            SpotifyImportPolicy.evaluate(record(20_000, "TRACKDONE", ImportedTriState.FALSE))
                .completionClassification
        )
    }

    @Test fun reasonNormalizationTrimsOnlyEdgesAndPreservesUnknownTokens() {
        val result = SpotifyImportPolicy.evaluate(
            record(0, "\u2003 Future-Token.v2 \u2003", ImportedTriState.UNKNOWN)
                .copy(providerReasonStart = "  MixedCase  ")
        )
        assertEquals("MixedCase", result.normalizedReasonStart)
        assertEquals("Future-Token.v2", result.normalizedReasonEnd)
        assertEquals(ImportedListeningSkippedState.UNKNOWN, result.skippedState)
    }

    private fun parseFixture(name: String): List<ImportedListeningRecord> {
        val records = mutableListOf<ImportedListeningRecord>()
        val parser = SpotifyExtendedStreamingParser(
            Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneOffset.UTC)
        )
        val result = parser.parse(
            { requireNotNull(javaClass.getResourceAsStream("/importing/spotify/$name")) },
            { item ->
                if (item is SpotifyParseItem.ValidMusic) records += item.record
                SpotifyParseControl.CONTINUE
            }
        )
        check(result is SpotifyFileParseResult.Completed)
        return records
    }

    private fun record(
        listenedMs: Long,
        reasonEnd: String?,
        skipped: ImportedTriState = ImportedTriState.FALSE
    ) = ImportedListeningRecord(
        provider = ImportProvider.SPOTIFY,
        externalMediaId = "track-id",
        mediaType = ImportedMediaType.MUSIC_TRACK,
        trackTitle = "Short Track",
        trackArtist = "Policy Lab",
        albumTitle = "Tests",
        albumArtist = "Policy Lab",
        sourceStartedAt = null,
        sourceEndedAt = Instant.parse("2024-01-01T00:00:00Z"),
        timestampEvidence = ImportedTimestampEvidence.SOURCE_END_ONLY,
        listenedMs = listenedMs,
        skippedEvidence = skipped,
        completionEvidence = ImportedCompletionEvidence.UNKNOWN,
        providerReasonStart = null,
        providerReasonEnd = reasonEnd
    )
}

