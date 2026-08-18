package com.example.cdplaya.data.importing.spotify

import com.example.cdplaya.data.importing.ListeningImportAnalyzer
import com.example.cdplaya.data.importing.ListeningImportFileResult
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class SpotifyLargeParserTest {
    private val analyzer = ListeningImportAnalyzer(
        SpotifyExtendedStreamingParser(
            Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneOffset.UTC)
        )
    )

    @Test
    fun parses10kGeneratedRecords() = verifyGenerated(10_000)

    @Test
    fun parses100kGeneratedRecords() = verifyGenerated(100_000)

    @Test
    fun parses500kGeneratedRecordsWhenExplicitlyEnabled() {
        assumeTrue(System.getProperty("spotify.stress500k") == "true")
        verifyGenerated(500_000)
    }

    private fun verifyGenerated(count: Int) {
        val file = Files.createTempFile("cdplaya-spotify-$count-", ".json")
        try {
            Files.newOutputStream(file).use { output ->
                SyntheticSpotifyHistoryGenerator.write(
                    output,
                    SyntheticSpotifyHistoryGenerator.Configuration(count)
                )
            }
            val result = analyzer.analyzeSpotify { Files.newInputStream(file) }
                as ListeningImportFileResult.Success
            assertEquals(count.toLong(), result.analysis.totalRecords)
            assertEquals(count.toLong(), result.analysis.validMusicRecords)
            assertEquals(0L, result.analysis.invalidRecords)
            if (count <= ListeningImportAnalyzer.DEFAULT_UNIQUE_EXTERNAL_ID_LIMIT) {
                assertEquals(count.toLong(), result.analysis.uniqueExternalTrackIds)
            } else {
                assertNull(result.analysis.uniqueExternalTrackIds)
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
