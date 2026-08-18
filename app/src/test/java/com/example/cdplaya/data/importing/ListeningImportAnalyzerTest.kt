package com.example.cdplaya.data.importing

import com.example.cdplaya.data.importing.spotify.SpotifyExtendedStreamingParser
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningImportAnalyzerTest {
    private val analyzer = ListeningImportAnalyzer(
        SpotifyExtendedStreamingParser(
            Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneOffset.UTC)
        )
    )

    @Test
    fun mixedMediaAnalysisIsFactualAndIncremental() {
        val analysis = success("spotify_extended_non_music.json")
        assertEquals(6L, analysis.totalRecords)
        assertEquals(1L, analysis.validMusicRecords)
        assertEquals(1L, analysis.podcastRecords)
        assertEquals(1L, analysis.audiobookRecords)
        assertEquals(1L, analysis.videoRecords)
        assertEquals(1L, analysis.unknownRecords)
        assertEquals(1L, analysis.invalidRecords)
        assertEquals(0L, analysis.uniqueExternalTrackIds)
        assertEquals(1L,
            analysis.invalidReasonCounts[ImportRecordErrorReason.AMBIGUOUS_MEDIA_TYPE])
    }

    @Test
    fun outOfOrderAnalysisComputesIndependentMinimumAndMaximum() {
        val analysis = success("spotify_extended_out_of_order.json")
        assertEquals(Instant.parse("2024-12-03T01:00:00Z"), analysis.earliestAt)
        assertEquals(Instant.parse("2024-12-03T03:00:00Z"), analysis.latestAt)
        assertEquals(3L, analysis.uniqueExternalTrackIds)
    }

    @Test
    fun diagnosticsRetainOnlyBoundedSafeIndexAndReason() {
        val bounded = ListeningImportAnalyzer(
            SpotifyExtendedStreamingParser(
                Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneOffset.UTC)
            ),
            diagnosticLimit = 2
        )
        val result = bounded.analyzeSpotify {
            requireNotNull(javaClass.getResourceAsStream(
                "/importing/spotify/spotify_extended_invalid_records.json"
            ))
        } as ListeningImportFileResult.Success
        assertEquals(7L, result.analysis.invalidRecords)
        assertEquals(2, result.analysis.diagnosticExamples.size)
        assertEquals(setOf("recordIndex", "reason"),
            ImportRecordDiagnostic::class.java.declaredFields
                .filterNot { it.isSynthetic || it.name.startsWith('$') }
                .map { it.name }.toSet())
    }

    @Test
    fun duplicateAndQualificationEvidenceAreCountedWithoutPolicyOrDedupe() {
        val duplicates = success("spotify_extended_duplicate_records.json")
        assertEquals(3L, duplicates.validMusicRecords)
        assertEquals(1L, duplicates.uniqueExternalTrackIds)

        val edges = success("spotify_extended_qualification_edges.json")
        assertEquals(7L, edges.validMusicRecords)
        assertEquals(1L, edges.zeroMsMusicRecords)
    }

    @Test
    fun independentlyAnalyzedFilesComposeWithoutFalseCrossFileUniqueness() {
        val a = success("spotify_extended_overlap_a.json")
        val b = success("spotify_extended_overlap_b.json")
        val combined = ListeningImportAnalysis.combine(listOf(a, b))
        assertEquals(4L, combined.totalRecords)
        assertEquals(4L, combined.validMusicRecords)
        assertNull(combined.uniqueExternalTrackIds)
    }

    @Test
    fun exactUniqueIdTrackingBecomesUnknownAtConfiguredBound() {
        val bounded = ListeningImportAnalyzer(
            SpotifyExtendedStreamingParser(
                Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneOffset.UTC)
            ),
            uniqueExternalIdLimit = 2
        )
        val result = bounded.analyzeSpotify {
            requireNotNull(javaClass.getResourceAsStream(
                "/importing/spotify/spotify_extended_minimal_music.json"
            ))
        } as ListeningImportFileResult.Success
        assertNull(result.analysis.uniqueExternalTrackIds)
    }

    @Test
    fun malformedFileReturnsFatalFailureWithSafePartialCounts() {
        val result = analyzer.analyzeSpotify {
            requireNotNull(javaClass.getResourceAsStream(
                "/importing/spotify/spotify_extended_malformed_truncated.json"
            ))
        } as ListeningImportFileResult.Failure
        assertEquals(ImportFileFailureReason.MALFORMED_JSON, result.reason)
        assertTrue(result.safeMessage.contains("valid JSON"))
    }

    private fun success(name: String): ListeningImportAnalysis {
        val result = analyzer.analyzeSpotify {
            requireNotNull(javaClass.getResourceAsStream("/importing/spotify/$name"))
        }
        assertTrue("Unexpected result: $result", result is ListeningImportFileResult.Success)
        return (result as ListeningImportFileResult.Success).analysis
    }
}
