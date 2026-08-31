package io.github.rsgarrido.sazanami.controller

import io.github.rsgarrido.sazanami.data.HistoricalReconciliationItem
import io.github.rsgarrido.sazanami.data.HistoricalReconciliationMetrics
import io.github.rsgarrido.sazanami.data.HistoricalReconciliationSource
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationCandidate
import io.github.rsgarrido.sazanami.data.LocalReconciliationTarget
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateCategory
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateDisposition
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateEvidence
import io.github.rsgarrido.sazanami.data.ReconciliationMatchConfidence
import io.github.rsgarrido.sazanami.data.ReconciliationMetadataRelation
import io.github.rsgarrido.sazanami.data.ReconciliationVersionRelation
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningHistoryReconciliationPresentationTest {
    @Test
    fun searchMatchesImportedTitleArtistAndAlbumCaseInsensitivelyAndUnicodeSafely() {
        val dataset = prepareReconciliationDataset(
            reviewItems = listOf(
                item(1, "Smaller and Smaller", "Faith No More", "Angel Dust"),
                item(2, "Jóga", "Björk", "Homogenic")
            ),
            linkedItems = emptyList()
        )

        assertEquals(listOf(1L), search(dataset, "Smaller"))
        assertEquals(listOf(1L), search(dataset, "FAITH NO MORE"))
        assertEquals(listOf(1L), search(dataset, "angel dust"))
        assertEquals(listOf(2L), search(dataset, "BJORK"))
        assertEquals(listOf(2L), search(dataset, "jóga"))
    }

    @Test
    fun trackSortsAreStableForHistoricalPlaysTitleArtistAndAlbum() {
        val dataset = prepareReconciliationDataset(
            listOf(
                item(1, "Beta", "Zulu", "Second", plays = 2),
                item(2, "Alpha", "Echo", "Third", plays = 9),
                item(3, "Gamma", "Alpha", "First", plays = 4)
            ),
            emptyList()
        )

        assertEquals(listOf(2L, 3L, 1L), ids(dataset, ReconciliationSortOption.HISTORICAL_PLAYS))
        assertEquals(listOf(2L, 1L, 3L), ids(dataset, ReconciliationSortOption.TRACK_TITLE))
        assertEquals(listOf(3L, 2L, 1L), ids(dataset, ReconciliationSortOption.ARTIST))
        assertEquals(listOf(3L, 1L, 2L), ids(dataset, ReconciliationSortOption.ALBUM))
    }

    @Test
    fun reviewReasonsDistinguishPunctuationAccentSimilarAmbiguousAndVersionFormatting() {
        val queen = item(
            1,
            "Good Old-Fashioned Lover Boy",
            "Queen",
            "Greatest Hits",
            localTitle = "Good Old Fashioned Lover Boy"
        )
        val shake = item(
            2,
            "Shake That",
            "BAND-MAID",
            "New Beginning",
            localTitle = "Shake That!!!"
        )
        val accent = item(3, "Déjà Vu", "Artist", "Album", localTitle = "Deja Vu")
        val similar = item(4, "Enemie", "Artist", "Album", localTitle = "Enemies")
        val river = item(
            5,
            "River's Soul - Live Session",
            "The Warning",
            "Album",
            localTitle = "River's Soul (Live Session)",
            category = ReconciliationCandidateCategory.TYPOGRAPHY_VARIANT,
            titleRelation = ReconciliationMetadataRelation.FUZZY,
            versionRelation = ReconciliationVersionRelation.SAME
        )
        val ambiguous = ambiguousItem(6, "Duplicate", "Artist", "Album")

        assertEquals(ReconciliationMatchReason.PUNCTUATION, reconciliationMatchReason(queen))
        assertEquals(ReconciliationMatchReason.PUNCTUATION, reconciliationMatchReason(shake))
        assertEquals("Punctuation difference", reconciliationMatchReason(queen).label)
        assertFalse(shake.isDeterministic)
        assertEquals(ReconciliationMatchReason.ACCENT_DIACRITIC, reconciliationMatchReason(accent))
        assertEquals(ReconciliationMatchReason.SIMILAR_TITLE, reconciliationMatchReason(similar))
        assertEquals(ReconciliationMatchReason.VERSION_FORMATTING, reconciliationMatchReason(river))
        assertFalse(river.isDeterministic)
        assertEquals(ReconciliationMatchReason.MULTIPLE_MATCHES, reconciliationMatchReason(ambiguous))

        val dataset = prepareReconciliationDataset(
            listOf(queen, shake, accent, similar, river, ambiguous),
            emptyList()
        )
        assertEquals(
            setOf(1L, 2L, 5L),
            filteredIds(dataset, ReconciliationReviewFilter.TITLE_FORMATTING)
        )
        assertEquals(setOf(3L), filteredIds(dataset, ReconciliationReviewFilter.ACCENT_DIACRITIC))
        assertEquals(setOf(4L), filteredIds(dataset, ReconciliationReviewFilter.SIMILAR_TITLE))
        assertEquals(setOf(6L), filteredIds(dataset, ReconciliationReviewFilter.AMBIGUOUS))
    }

    @Test
    fun albumGroupingKeepsIdentityRowsAndReportsAllStatusesWithoutMergingArtists() {
        val review = item(1, "Review", "Faith No More", "Angel Dust")
        val unmatched = unmatchedItem(2, "Missing", "Faith No More", "Angel Dust")
        val otherArtist = unmatchedItem(3, "Other", "Another Artist", "Angel Dust")
        val linkedSource = source(4, "Linked", "Faith No More", "Angel Dust", 7)
        val linked = LinkedHistoricalReconciliation(linkedSource, target(40, "Linked"), 1)
        val dataset = prepareReconciliationDataset(
            listOf(review, unmatched, otherArtist),
            listOf(linked)
        )
        val reviewVisible = filterAndSortReconciliationTracks(
            dataset,
            ReconciliationTrackStatus.REVIEW,
            "",
            ReconciliationSortOption.HISTORICAL_PLAYS
        )

        val groups = groupReconciliationAlbums(dataset.tracks, reviewVisible,
            ReconciliationSortOption.HISTORICAL_PLAYS)

        assertEquals(1, groups.size)
        val angelDust = groups.single()
        assertEquals(3, angelDust.importedCount)
        assertEquals(1, angelDust.linkedCount)
        assertEquals(1, angelDust.reviewCount)
        assertEquals(1, angelDust.unmatchedCount)
        assertEquals(listOf(1L), angelDust.tracks.map { it.sourceId })
        assertEquals(2, groupReconciliationAlbums(dataset.tracks, dataset.tracks,
            ReconciliationSortOption.ALBUM).size)
        assertEquals(4, dataset.tracks.map { it.sourceId }.distinct().size)
    }

    @Test
    fun artistGroupingAggregatesAlbumsAndLinkedAndUnmatchedBrowsingUseImportedMetadata() {
        val review = item(1, "Review", "Faith No More", "Angel Dust")
        val unmatched = unmatchedItem(2, "Missing", "Faith No More", "King for a Day")
        val linkedSource = source(3, "Smaller and Smaller", "Faith No More", "Angel Dust", 5)
        val linked = LinkedHistoricalReconciliation(linkedSource, target(30, "Local title"), 1)
        val dataset = prepareReconciliationDataset(listOf(review, unmatched), listOf(linked))

        val artists = groupReconciliationArtists(dataset.tracks, dataset.tracks,
            ReconciliationSortOption.ARTIST)
        assertEquals(1, artists.size)
        assertEquals(3, artists.single().importedCount)
        assertEquals(2, artists.single().albums.size)
        assertEquals(1, artists.single().linkedCount)
        assertEquals(1, artists.single().reviewCount)
        assertEquals(1, artists.single().unmatchedCount)

        val linkedSearch = filterAndSortReconciliationTracks(
            dataset, ReconciliationTrackStatus.LINKED, "smaller",
            ReconciliationSortOption.TRACK_TITLE
        )
        assertEquals(listOf(3L), linkedSearch.map { it.sourceId })
        val missingAlbums = groupReconciliationAlbums(
            dataset.tracks,
            filterAndSortReconciliationTracks(
                dataset, ReconciliationTrackStatus.UNMATCHED, "King for a Day",
                ReconciliationSortOption.ALBUM
            ),
            ReconciliationSortOption.ALBUM
        )
        assertEquals("King for a Day", missingAlbums.single().title)
        assertEquals(1, missingAlbums.single().unmatchedCount)
    }

    @Test
    fun unknownCompilationAndJapaneseGroupingRemainStableAndIdentityPreserving() {
        val japanese = item(1, "夜に駆ける", "YOASOBI", "THE BOOK")
        val compilation = item(2, "Guest track", "Various Artists", "Collection")
        val distinctArtist = item(3, "Other track", "Guest Artist", "Collection")
        val unknownA = unmatchedItem(4, "Unknown A", "", "")
        val unknownB = unmatchedItem(5, "Unknown B", "", "")
        val dataset = prepareReconciliationDataset(
            listOf(japanese, compilation, distinctArtist, unknownA, unknownB),
            emptyList()
        )
        val originalIds = dataset.tracks.map(ReconciliationTrackPresentation::sourceId)

        assertEquals(listOf(1L), search(dataset, "夜に"))
        val collectionAlbums = groupReconciliationAlbums(
            dataset.tracks,
            dataset.tracks.filter { it.source.album == "Collection" },
            ReconciliationSortOption.ALBUM
        )
        assertEquals(2, collectionAlbums.size)
        assertEquals(setOf("Various Artists", "Guest Artist"),
            collectionAlbums.mapTo(mutableSetOf()) { it.artist })

        val unknownAlbum = groupReconciliationAlbums(
            dataset.tracks,
            dataset.tracks.filter { it.source.album.isBlank() && it.source.artist.isBlank() },
            ReconciliationSortOption.HISTORICAL_PLAYS
        ).single()
        assertEquals("Unknown album", unknownAlbum.title)
        assertEquals("Unknown artist", unknownAlbum.artist)
        assertEquals(2, unknownAlbum.importedCount)
        assertEquals(2, unknownAlbum.unmatchedCount)

        ReconciliationSortOption.entries.forEach { sort ->
            groupReconciliationArtists(dataset.tracks, dataset.tracks, sort)
        }
        assertEquals(originalIds, dataset.tracks.map(ReconciliationTrackPresentation::sourceId))
    }

    private fun search(dataset: ReconciliationPreparedDataset, query: String) =
        filterAndSortReconciliationTracks(
            dataset,
            ReconciliationTrackStatus.REVIEW,
            query,
            ReconciliationSortOption.TRACK_TITLE
        ).map { it.sourceId }

    private fun ids(dataset: ReconciliationPreparedDataset, sort: ReconciliationSortOption) =
        filterAndSortReconciliationTracks(
            dataset,
            ReconciliationTrackStatus.REVIEW,
            "",
            sort
        ).map { it.sourceId }

    private fun filteredIds(
        dataset: ReconciliationPreparedDataset,
        filter: ReconciliationReviewFilter
    ) = filterAndSortReconciliationTracks(
        dataset,
        ReconciliationTrackStatus.REVIEW,
        "",
        ReconciliationSortOption.TRACK_TITLE,
        filter
    ).mapTo(mutableSetOf()) { it.sourceId }

    private fun item(
        id: Long,
        title: String,
        artist: String,
        album: String,
        plays: Long = 1,
        localTitle: String = title,
        category: ReconciliationCandidateCategory = ReconciliationCandidateCategory.TYPOGRAPHY_VARIANT,
        titleRelation: ReconciliationMetadataRelation = ReconciliationMetadataRelation.FUZZY,
        versionRelation: ReconciliationVersionRelation = ReconciliationVersionRelation.NONE
    ): HistoricalReconciliationItem {
        val source = source(id, title, artist, album, plays)
        return HistoricalReconciliationItem(
            source,
            listOf(ListeningIdentityReconciliationCandidate(
                target(id + 100, localTitle, artist, album),
                evidence(category, titleRelation, versionRelation)
            )),
            ReconciliationCandidateDisposition.SUGGESTED,
            false,
            confidence = ReconciliationMatchConfidence.FUZZY
        )
    }

    private fun unmatchedItem(id: Long, title: String, artist: String, album: String) =
        HistoricalReconciliationItem(
            source(id, title, artist, album, 1),
            emptyList(),
            ReconciliationCandidateDisposition.NO_CANDIDATE,
            false
        )

    private fun ambiguousItem(id: Long, title: String, artist: String, album: String) =
        HistoricalReconciliationItem(
            source(id, title, artist, album, 1),
            listOf(
                ListeningIdentityReconciliationCandidate(
                    target(id + 100, title, artist, album),
                    evidence(ReconciliationCandidateCategory.AMBIGUOUS)
                ),
                ListeningIdentityReconciliationCandidate(
                    target(id + 200, title, artist, "Other"),
                    evidence(ReconciliationCandidateCategory.AMBIGUOUS)
                )
            ),
            ReconciliationCandidateDisposition.AMBIGUOUS,
            false,
            confidence = ReconciliationMatchConfidence.AMBIGUOUS
        )

    private fun evidence(
        category: ReconciliationCandidateCategory,
        titleRelation: ReconciliationMetadataRelation = ReconciliationMetadataRelation.FUZZY,
        versionRelation: ReconciliationVersionRelation = ReconciliationVersionRelation.NONE
    ) = ReconciliationCandidateEvidence(
        titleRelation,
        ReconciliationMetadataRelation.EXACT,
        ReconciliationMetadataRelation.EXACT,
        versionRelation,
        emptySet(),
        category
    )

    private fun source(
        id: Long,
        title: String,
        artist: String,
        album: String,
        plays: Long
    ) = HistoricalReconciliationSource(
        id,
        title,
        artist,
        album,
        null,
        setOf(ListeningSource.SPOTIFY_IMPORT),
        true,
        HistoricalReconciliationMetrics(plays, plays, plays * 30_000, plays, 1, 2)
    )

    private fun target(
        id: Long,
        title: String,
        artist: String = "Artist",
        album: String = "Album"
    ) = LocalReconciliationTarget(
        id, id + 1_000, "ref-$id", title, artist, album, null,
        180_000, "$title.flac", "flac", "Music/$artist/$album"
    )
}
