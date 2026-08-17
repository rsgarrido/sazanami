package com.example.cdplaya.data.importing.spotify

import com.example.cdplaya.data.importing.ImportDedupeDisposition
import com.example.cdplaya.data.importing.ImportOccurrenceKey
import com.example.cdplaya.data.importing.ListeningImportDedupePlanner
import com.example.cdplaya.data.importing.ListeningImportDuplicateOrdinalAssigner
import com.example.cdplaya.data.importing.ListeningImportFingerprint
import com.example.cdplaya.data.importing.ListeningImportSelectionPlanner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SpotifyDedupeFixtureTest {
    private val parser = SpotifyExtendedStreamingParser(
        Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneOffset.UTC)
    )

    @Test fun exactDuplicateFixtureGetsOrdinalsWithoutCollapsingMultiplicity() {
        val fingerprints = fingerprints("spotify_extended_duplicate_records.json")
        val assigner = ListeningImportDuplicateOrdinalAssigner()
        val keys = fingerprints.map(assigner::assign)
        assertEquals(fingerprints[0], fingerprints[1])
        assertNotEquals(fingerprints[0], fingerprints[2])
        assertEquals(listOf(0, 1, 0), keys.map { it.duplicateOrdinal })
    }

    @Test fun overlappingFixturesSuppressOnlySharedSelectionEvidence() {
        val selection = ListeningImportSelectionPlanner().plan(
            listOf(
                fingerprints("spotify_extended_overlap_a.json").asSequence(),
                fingerprints("spotify_extended_overlap_b.json").asSequence()
            )
        )
        assertEquals(4, selection.summary.importableMusicOccurrencesAcrossFiles)
        assertEquals(3, selection.summary.selectedMusicOccurrences)
        assertEquals(1, selection.summary.overlappingOccurrencesSuppressed)
    }

    @Test fun laterReexportAddsOnlyTheNewOccurrence() = runBlocking {
        val initial = ListeningImportSelectionPlanner().plan(
            listOf(fingerprints("spotify_extended_reexport_initial.json").asSequence())
        )
        val persisted = initial.occurrenceKeyChunks(500).flatten().toSet()
        val later = ListeningImportSelectionPlanner().plan(
            listOf(fingerprints("spotify_extended_reexport_later.json").asSequence())
        )
        val decisions = mutableListOf<Pair<ImportOccurrenceKey, ImportDedupeDisposition>>()
        val result = ListeningImportDedupePlanner().plan(1, later, { _, keys ->
            keys.filterTo(mutableSetOf()) { it in persisted }
        }, { decisions += it.key to it.disposition })
        assertEquals(2, result.alreadyImportedOccurrences)
        assertEquals(1, result.newOccurrences)
        assertEquals(1, decisions.count { it.second == ImportDedupeDisposition.NEW })
    }

    @Test fun identityFixtureUriLessEvidenceRemainsProviderPreserving() {
        val uriLess = records("spotify_extended_identity_edges.json").last()
        val variants = listOf(
            uriLess.copy(trackTitle = "Unavailable Memory!"),
            uriLess.copy(trackTitle = "UNAVAILABLE MEMORY"),
            uriLess.copy(trackTitle = "Unavailable Memory (Live)"),
            uriLess.copy(albumTitle = "Edition Two"),
            uriLess.copy(trackArtist = "Deleted Fiction feat. Guest")
        )
        val fingerprints = (listOf(uriLess) + variants)
            .map(SpotifyListeningImportFingerprint::create)
        assertEquals(fingerprints.size, fingerprints.distinct().size)
    }

    private fun fingerprints(name: String): List<ListeningImportFingerprint> =
        records(name).map(SpotifyListeningImportFingerprint::create)

    private fun records(name: String): List<com.example.cdplaya.data.importing.ImportedListeningRecord> {
        val records = mutableListOf<com.example.cdplaya.data.importing.ImportedListeningRecord>()
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
}
