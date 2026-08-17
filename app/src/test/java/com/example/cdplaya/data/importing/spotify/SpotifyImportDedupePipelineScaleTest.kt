package com.example.cdplaya.data.importing.spotify

import com.example.cdplaya.data.importing.ListeningImportDedupePlanner
import com.example.cdplaya.data.importing.ListeningImportSelectionBuilder
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class SpotifyImportDedupePipelineScaleTest {
    private val parser = SpotifyExtendedStreamingParser(
        Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneOffset.UTC)
    )

    @Test fun policyFingerprintOrdinalAndDedupePipeline10k() = verify(10_000)

    @Test fun policyFingerprintOrdinalAndDedupePipeline100k() = verify(100_000)

    @Test fun policyFingerprintOrdinalAndDedupePipeline500kWhenEnabled() {
        assumeTrue(System.getProperty("spotify.stress500k") == "true")
        verify(500_000)
    }

    private fun verify(count: Int) = runBlocking {
        val file = Files.createTempFile("cdplaya-session2-$count-", ".json")
        try {
            Files.newOutputStream(file).use { output ->
                SyntheticSpotifyHistoryGenerator.write(
                    output,
                    SyntheticSpotifyHistoryGenerator.Configuration(count)
                )
            }
            val selectionBuilder = ListeningImportSelectionBuilder()
            var qualified = 0L
            val parseAndFingerprintMs = measureTimeMillis {
                selectionBuilder.beginFile()
                val result = parser.parse({ Files.newInputStream(file) }) { item ->
                    if (item is SpotifyParseItem.ValidMusic) {
                        if (SpotifyImportPolicy.evaluate(item.record).qualifiedAsPlay) qualified++
                        selectionBuilder.accept(SpotifyListeningImportFingerprint.create(item.record))
                    }
                    SpotifyParseControl.CONTINUE
                }
                check(result is SpotifyFileParseResult.Completed)
                selectionBuilder.endFile()
            }
            val selection = selectionBuilder.build()
            var lookupCount = 0
            var assignedCount = 0L
            val ordinalKeys = selection.occurrenceKeyChunks(500).flatMap { it.asSequence() }
            ordinalKeys.forEach { key ->
                check(key.duplicateOrdinal == 0)
                assignedCount++
            }
            lateinit var dedupe: com.example.cdplaya.data.importing.ListeningImportDedupePlan
            val dedupeMs = measureTimeMillis {
                dedupe = ListeningImportDedupePlanner().plan(1, selection, { _, keys ->
                    lookupCount++
                    require(keys.size <= 500)
                    emptySet()
                })
            }
            assertEquals(count.toLong(), selection.summary.selectedMusicOccurrences)
            assertEquals(count, selection.summary.distinctFingerprints)
            assertEquals(count.toLong(), assignedCount)
            assertEquals((count - 30_000).coerceAtLeast(0).toLong(), qualified)
            assertEquals(count.toLong(), dedupe.newOccurrences)
            assertEquals((count + 499) / 500, lookupCount)
            println(
                "session2Pipeline count=$count parseFingerprintMs=$parseAndFingerprintMs " +
                    "dedupeMs=$dedupeMs hashes=${selection.summary.distinctFingerprints} " +
                    "lookupBatches=$lookupCount"
            )
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
