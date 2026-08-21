package com.example.cdplaya.data

import com.example.cdplaya.data.local.ListeningSource
import java.util.Locale
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningIdentityReconciliationCandidateMatcherTest {
    private val matcher = ReconciliationCandidateMatcher()

    @Test
    fun exactMetadataIsStrongAndNeverProducesAConfirmedLink() {
        val result = discover(source(1, "S.A.S.S", "Fictional Artist", "Signals"),
            target(10, "S.A.S.S", "Fictional Artist", "Signals"))

        val item = result.items.single()
        assertEquals(ReconciliationCandidateDisposition.SUGGESTED, item.disposition)
        assertEquals(ReconciliationCandidateCategory.STRONG_METADATA,
            item.candidates.single().evidence.category)
        assertEquals(10, item.candidates.single().target.identityId)
        assertFalse(item.hasMoreCandidates)
        // The candidate API intentionally has no link/chosen-candidate result state.
        assertEquals(ReconciliationState.UNMATCHED, item.source.reconciliationState)
    }

    @Test
    fun unicodeNfcCaseAndWhitespaceUseTheConservativeStrongTier() {
        val sources = listOf(
            source(1, "夢中猫", "作家", "作品"),
            source(2, "Cafe\u0301", "ARTIST", "  An   Album  "),
            source(3, "Mixed Case", "Artist", "Album")
        )
        val targets = listOf(
            target(11, "夢中猫", "作家", "作品"),
            target(12, "Café", "artist", "an album"),
            target(13, "mixed case", "artist", "album")
        )

        val result = matcher.discover(sources, targets)

        assertEquals(3, result.items.size)
        assertTrue(result.items.all {
            it.candidates.single().evidence.category == ReconciliationCandidateCategory.STRONG_METADATA
        })
        assertEquals("Cafe\u0301", result.items.single { it.source.identityId == 2L }.source.title)
    }

    @Test
    fun apostrophesDashesAccentsAndBoundedPunctuationAreWeakSearchOnlyEvidence() {
        val sources = listOf(
            source(1, "It's Me", "Artist", "Album"),
            source(2, "Signal - One", "Artist", "Album"),
            source(3, "Ser Humano N°2", "Artist", "Ser Humano"),
            source(4, "S.A.S.S", "Artist", "Album"),
            source(5, "In Motion # 1", "Artist", "Album")
        )
        val targets = listOf(
            target(11, "It’s Me", "Artist", "Album"),
            target(12, "Signal — One", "Artist", "Album"),
            target(13, "Ser hümáno N°2", "Artist", "Ser hümáno"),
            target(14, "SASS", "Artist", "Album"),
            target(15, "In Motion #1", "Artist", "Album")
        )

        val result = matcher.discover(sources, targets)

        assertTrue(result.items.all {
            it.candidates.single().evidence.category ==
                ReconciliationCandidateCategory.TYPOGRAPHY_VARIANT
        })
        assertTrue(result.items.all {
            it.candidates.single().evidence.titleRelation == ReconciliationMetadataRelation.NORMALIZED
        })
    }

    @Test
    fun missingAlbumIsIncompleteForOneTargetAndAmbiguousForMultipleVersions() {
        val historical = source(1, "Six Feet Deep", "The Warning", "")
        val studio = target(11, "Six Feet Deep", "The Warning", "Keep Me Fed")
        val live = target(12, "Six Feet Deep", "The Warning",
            "Live From Auditorio Nacional, CDMX")

        val one = matcher.discover(listOf(historical), listOf(studio)).items.single()
        assertEquals(ReconciliationCandidateCategory.INCOMPLETE_EVIDENCE,
            one.candidates.single().evidence.category)
        assertEquals(setOf(ReconciliationMissingField.ALBUM),
            one.candidates.single().evidence.missingFields)

        val two = matcher.discover(listOf(historical), listOf(studio, live)).items.single()
        assertEquals(ReconciliationCandidateDisposition.AMBIGUOUS, two.disposition)
        assertEquals(listOf(11L, 12L), two.candidates.map { it.target.identityId })
        assertTrue(two.candidates.all {
            it.evidence.category == ReconciliationCandidateCategory.AMBIGUOUS
        })

        val numbered = discover(
            source(2, "In Motion #1", "Fictional Artist", ""),
            target(13, "In Motion #1", "Fictional Artist", "Numbered Works")
        ).items.single()
        assertEquals(ReconciliationCandidateCategory.INCOMPLETE_EVIDENCE,
            numbered.candidates.single().evidence.category)
    }

    @Test
    fun exactAlbumSelectsTheMatchingStudioOrLiveBucketWithoutArbitraryAlternative() {
        val targets = listOf(
            target(11, "Six Feet Deep", "The Warning", "Keep Me Fed"),
            target(12, "Six Feet Deep", "The Warning", "Live From Auditorio Nacional, CDMX")
        )
        val sources = listOf(
            source(1, "Six Feet Deep", "The Warning", "Keep Me Fed"),
            source(2, "Six Feet Deep", "The Warning", "Live From Auditorio Nacional, CDMX")
        )

        val result = matcher.discover(sources, targets)

        assertEquals(11L, result.items.single { it.source.identityId == 1L }
            .candidates.single().target.identityId)
        assertEquals(12L, result.items.single { it.source.identityId == 2L }
            .candidates.single().target.identityId)
    }

    @Test
    fun remasterAcousticAndRadioEditDifferencesAreVersionSensitive() {
        val sources = listOf(
            source(1, "Land of Sunshine - Remastered 2015", "Band", "Angel Dust"),
            source(2, "Fictional Song (Acoustic)", "Band", "Album"),
            source(3, "Fictional Song - Radio Edit", "Band", "Album")
        )
        val targets = listOf(
            target(11, "Land of Sunshine", "Band", "Angel Dust"),
            target(12, "Fictional Song", "Band", "Album")
        )

        val result = matcher.discover(sources, targets)

        assertTrue(result.items.all {
            it.candidates.single().evidence.category ==
                ReconciliationCandidateCategory.VERSION_SENSITIVE
        })
        assertTrue(result.items.all {
            it.candidates.single().evidence.versionRelation == ReconciliationVersionRelation.DIFFERENT
        })
    }

    @Test
    fun documentedVersionMarkerFormsRemainProtected() {
        val markedTitles = listOf(
            "Song (Live)",
            "Song - Live",
            "Song [Live]",
            "Song - Remastered 2015",
            "Song (Acoustic)",
            "Song - Radio Edit",
            "Song Demo",
            "Song Remix",
            "Song (Mono)",
            "Song (Stereo)",
            "Song (Anniversary Edition)",
            "Song (Deluxe)",
            "Song (Session Version)"
        )
        val sources = markedTitles.mapIndexed { index, title ->
            source(index + 1L, title, "Band", "Album")
        }

        val result = matcher.discover(sources, listOf(target(100, "Song", "Band", "Album")))

        assertEquals(markedTitles.size, result.items.size)
        assertTrue(result.items.all {
            it.candidates.single().evidence.category ==
                ReconciliationCandidateCategory.VERSION_SENSITIVE
        })
    }

    @Test
    fun originalExactMetadataRemainsStrongAndLiveWireIsNotAFalseVersionMarker() {
        val result = matcher.discover(
            listOf(
                source(1, "Land of Sunshine", "Band", "Angel Dust"),
                source(2, "Live Wire", "Band", "Album")
            ),
            listOf(
                target(11, "Land of Sunshine", "Band", "Angel Dust"),
                target(12, "Wire", "Band", "Album"),
                target(13, "Live Wire", "Band", "Album")
            )
        )

        assertEquals(ReconciliationCandidateCategory.STRONG_METADATA,
            result.items.single { it.source.identityId == 1L }.candidates.single().evidence.category)
        assertEquals(13L, result.items.single { it.source.identityId == 2L }
            .candidates.single().target.identityId)
    }

    @Test
    fun missingArtistIsBoundedAndMissingTitleHasNoCandidate() {
        val targets = listOf(target(10, "Only One", "Artist", "Album"))
        val result = matcher.discover(
            listOf(
                source(1, "Only One", "", "Album"),
                source(2, "", "Artist", "Album"),
                source(3, "   ", "Artist", "Album")
            ),
            targets
        )

        assertEquals(ReconciliationCandidateCategory.INCOMPLETE_EVIDENCE,
            result.items.single { it.source.identityId == 1L }.candidates.single().evidence.category)
        assertTrue(result.items.filter { it.source.identityId != 1L }.all {
            it.disposition == ReconciliationCandidateDisposition.NO_CANDIDATE
        })
    }

    @Test
    fun uriLessFragmentsAndDistinctProviderIdentitiesRemainSeparateButShareATarget() {
        val sources = listOf(
            source(1, "50Mila", "Nina Fiction", "Nina Fiction", stableId = false),
            source(2, "50Mila", "Nina Fiction", "Nina Fiction", stableId = false),
            source(3, "Revenge of B", "Band", "Album", stableId = true),
            source(4, "Revenge of B", "Band", "Album", stableId = true)
        )
        val targets = listOf(
            target(11, "50Mila", "Nina Fiction", "Nina Fiction"),
            target(12, "Revenge of B", "Band", "Album")
        )

        val result = matcher.discover(sources, targets)

        assertEquals(listOf(1L, 2L, 3L, 4L), result.items.map { it.source.identityId }.sorted())
        assertEquals(setOf(11L), result.items.filter { it.source.identityId <= 2L }
            .map { it.candidates.single().target.identityId }.toSet())
        assertEquals(setOf(12L), result.items.filter { it.source.identityId >= 3L }
            .map { it.candidates.single().target.identityId }.toSet())
    }

    @Test
    fun noMatchIsValidAndDoesNotUseWeakSameTitleDifferentArtistEvidence() {
        val item = discover(
            source(1, "Satellite Hearts", "Neon Harbor", "Glass Signals"),
            target(10, "Satellite Hearts", "Other Artist", "Other Album")
        ).items.single()

        assertEquals(ReconciliationCandidateDisposition.NO_CANDIDATE, item.disposition)
        assertTrue(item.candidates.isEmpty())
    }

    @Test
    fun candidateLimitIsBoundedAndDeterministicallyOrderedWithoutImplicitWinner() {
        val targets = (20L downTo 1L).map {
            target(it, "Home", "Common Artist", "Album ${it.toString().padStart(2, '0')}")
        }
        val item = ReconciliationCandidateMatcher(maxCandidates = 8).discover(
            listOf(source(1_000, "Home", "Common Artist", "")), targets
        ).items.single()

        assertEquals(ReconciliationCandidateDisposition.AMBIGUOUS, item.disposition)
        assertEquals(8, item.candidates.size)
        assertTrue(item.hasMoreCandidates)
        assertEquals((1L..8L).toList(), item.candidates.map { it.target.identityId })
    }

    @Test
    fun queueOrderingIsActionableThenAmbiguousThenNoCandidateAndUsesHistoryFacts() {
        val strongLow = source(1, "Strong Low", "Artist", "Album", eventCount = 1)
        val strongHigh = source(2, "Strong High", "Artist", "Album", eventCount = 10)
        val ambiguous = source(3, "Ambiguous", "Artist", "")
        val none = source(4, "None", "Artist", "Album")
        val result = matcher.discover(
            listOf(none, ambiguous, strongLow, strongHigh),
            listOf(
                target(11, "Strong Low", "Artist", "Album"),
                target(12, "Strong High", "Artist", "Album"),
                target(13, "Ambiguous", "Artist", "A"),
                target(14, "Ambiguous", "Artist", "B")
            )
        )

        assertEquals(listOf(2L, 1L, 3L, 4L), result.items.map { it.source.identityId })
        assertEquals(ReconciliationCandidateSummary(4, 2, 1, 1), result.summary)
    }

    @Test
    fun localeIndependentNormalizationDoesNotUseTurkishDefaultLocale() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("title i", candidateConservativeNormalize("TITLE I"))
            val item = discover(source(1, "TITLE I", "ARTIST", "ALBUM"),
                target(10, "title i", "artist", "album")).items.single()
            assertEquals(ReconciliationCandidateCategory.STRONG_METADATA,
                item.candidates.single().evidence.category)
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun scaleUsesIndexedBucketsForOneThousandByFiveThousand() {
        val sources = (1L..1_000L).map {
            source(it, "Track $it", "Artist ${it % 100}", "Album ${it % 50}")
        }
        val targets = (1L..5_000L).map {
            target(10_000L + it, "Track $it", "Artist ${it % 100}", "Album ${it % 50}")
        }
        lateinit var result: ReconciliationCandidateDiscovery

        val elapsed = measureTimeMillis { result = matcher.discover(sources, targets) }

        assertEquals(1_000, result.items.size)
        assertEquals(1_000, result.summary.withSuggestions)
        assertTrue(result.items.all { it.candidates.size == 1 })
        println("candidate scale 1000x5000 observed ${elapsed}ms")
    }

    @Test
    fun thousandsOfUnrelatedTargetsAndLargeCommonTitleBucketStayBounded() {
        val unrelated = (1L..5_000L).map {
            target(it, "Unrelated $it", "Artist $it", "Album $it")
        }
        val common = (6_000L..7_000L).map {
            target(it, "Intro", "Artist $it", "Album")
        }
        val result = matcher.discover(
            listOf(
                source(20_000, "Not Present", "Nobody", "Nowhere"),
                source(20_001, "Intro", "", "")
            ),
            unrelated + common
        )

        assertTrue(result.items.all { it.disposition == ReconciliationCandidateDisposition.NO_CANDIDATE })
        assertTrue(result.items.single { it.source.identityId == 20_001L }.hasMoreCandidates)
    }

    private fun discover(
        source: HistoricalReconciliationSource,
        vararg targets: LocalReconciliationTarget
    ) = matcher.discover(listOf(source), targets.toList())

    private fun source(
        id: Long,
        title: String,
        artist: String,
        album: String,
        stableId: Boolean = true,
        eventCount: Long = 3
    ) = HistoricalReconciliationSource(
        identityId = id,
        title = title,
        artist = artist,
        album = album,
        albumArtist = null,
        importedProviders = setOf(ListeningSource.SPOTIFY_IMPORT),
        hasStableExternalId = stableId,
        metrics = HistoricalReconciliationMetrics(
            importedEventCount = eventCount,
            qualifiedPlayCount = eventCount,
            recordedListeningMs = eventCount * 60_000,
            completedCount = 0,
            firstListenedAt = 100,
            lastListenedAt = 200
        )
    )

    private fun target(
        id: Long,
        title: String,
        artist: String,
        album: String
    ) = LocalReconciliationTarget(
        identityId = id,
        localBindingId = id + 100_000,
        referenceKey = "reference-$id",
        title = title,
        artist = artist,
        album = album,
        albumArtist = null,
        durationMs = 180_000,
        displayName = "$title.flac",
        fileExtension = "flac",
        relativeFolder = "Music/Fictional"
    )
}
