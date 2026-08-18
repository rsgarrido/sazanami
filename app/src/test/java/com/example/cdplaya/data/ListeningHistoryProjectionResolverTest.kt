package com.example.cdplaya.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class ListeningHistoryProjectionResolverTest {
    @Test
    fun importedHistoricalIdentityWithoutLocalBindingIsNotMadePlayable() {
        val importedOnly = track(
            id = 99,
            bindings = emptyList(),
            legacy = 0L,
            detailed = 3L
        )

        val librarySong = song(99)
        val result = ListeningHistoryProjectionResolver.resolve(
            projections(importedOnly),
            SongReferenceIndex.build(listOf(librarySong)),
            setOf(librarySong.membershipKey())
        )

        assertTrue(result.recentlyPlayed.isEmpty())
        assertTrue(result.mostPlayed.isEmpty())
    }

    @Test
    fun exactBindingsPreserveEachProjectionOrderWhenMissingRowsAreOmitted() {
        val first = song(1, title = "Twin")
        val second = song(2, title = "Twin")
        val missing = track(3, listOf(binding(30, mediaStoreId = 30)))
        val firstTrack = track(1, listOf(binding(first)))
        val secondTrack = track(2, listOf(binding(second)), legacy = 0L, detailed = 1L)
        val projections = ProductionListeningHistoryProjections(
            recentlyPlayed = listOf(firstTrack, missing, secondTrack).map(::RecentlyPlayedProjection),
            mostPlayed = listOf(secondTrack, missing, firstTrack).map(::MostPlayedProjection)
        )
        val library = listOf(second, first)

        val result = ListeningHistoryProjectionResolver.resolve(
            projections,
            SongReferenceIndex.build(library),
            library.mapTo(mutableSetOf(), Song::membershipKey)
        )

        assertEquals(listOf(first, second), result.recentlyPlayed)
        assertEquals(listOf(second, first), result.mostPlayed)
        assertEquals(1L, firstTrack.playCounts.legacyPlayCount)
        assertEquals(1L, secondTrack.playCounts.detailedPlayCount)
    }

    @Test
    fun folderFilteringMissingFilesAndRescansOnlyRemapWithoutChangingHistory() {
        val current = song(7)
        val projections = projections(track(7, listOf(binding(current))))

        val excluded = ListeningHistoryProjectionResolver.resolve(
            projections,
            SongReferenceIndex.build(listOf(current)),
            emptySet()
        )
        val missing = ListeningHistoryProjectionResolver.resolve(
            projections,
            SongReferenceIndex.EMPTY,
            emptySet()
        )
        val reappeared = ListeningHistoryProjectionResolver.resolve(
            projections,
            SongReferenceIndex.build(listOf(current)),
            setOf(current.membershipKey())
        )

        assertTrue(excluded.recentlyPlayed.isEmpty())
        assertTrue(missing.mostPlayed.isEmpty())
        assertEquals(listOf(current), reappeared.recentlyPlayed)
        assertEquals(listOf(current), reappeared.mostPlayed)
    }

    @Test
    fun identicalMetadataIdentitiesRemainDistinctWhenExactBindingsDiffer() {
        val first = song(11, title = "Same", relativePath = "A/")
        val second = song(12, title = "Same", relativePath = "B/")
        val projections = ProductionListeningHistoryProjections(
            recentlyPlayed = listOf(
                RecentlyPlayedProjection(track(101, listOf(binding(first)))),
                RecentlyPlayedProjection(track(102, listOf(binding(second))))
            ),
            mostPlayed = emptyList()
        )
        val library = listOf(second, first)

        val result = ListeningHistoryProjectionResolver.resolve(
            projections,
            SongReferenceIndex.build(library),
            library.mapTo(mutableSetOf(), Song::membershipKey)
        )

        assertEquals(listOf(first, second), result.recentlyPlayed)
    }

    @Test
    fun ambiguousPortableEvidenceIsOmittedInsteadOfChoosingByMetadata() {
        val first = song(21, title = "Duplicate", relativePath = "A/")
        val second = song(22, title = "Duplicate", relativePath = "B/")
        val portableKey = requireNotNull(first.songIdentity().portableKey)
        val ambiguousBinding = binding(
            id = 210,
            mediaStoreId = null,
            volumeName = null,
            contentUri = null,
            relativePath = null,
            displayName = null,
            fileSizeBytes = null,
            portableKey = portableKey
        )
        val projections = projections(track(210, listOf(ambiguousBinding), title = "Duplicate"))
        val library = listOf(first, second)

        val result = ListeningHistoryProjectionResolver.resolve(
            projections,
            SongReferenceIndex.build(library),
            library.mapTo(mutableSetOf(), Song::membershipKey)
        )

        assertTrue(result.recentlyPlayed.isEmpty())
        assertTrue(result.mostPlayed.isEmpty())
    }

    @Test
    fun preferredBindingWinsAndAnOlderKnownBindingCanResolveAfterPreferredDisappears() {
        val preferred = song(31, relativePath = "New/")
        val older = song(32, relativePath = "Old/")
        val preferredTrack = track(31, listOf(binding(preferred), binding(older)))
        val fallbackTrack = track(
            32,
            listOf(binding(999, mediaStoreId = 999), binding(older))
        )
        val library = listOf(older, preferred)
        val visible = library.mapTo(mutableSetOf(), Song::membershipKey)

        val preferredResult = ListeningHistoryProjectionResolver.resolve(
            projections(preferredTrack),
            SongReferenceIndex.build(library),
            visible
        )
        val fallbackResult = ListeningHistoryProjectionResolver.resolve(
            projections(fallbackTrack),
            SongReferenceIndex.build(library),
            visible
        )

        assertEquals(listOf(preferred), preferredResult.recentlyPlayed)
        assertEquals(listOf(older), fallbackResult.recentlyPlayed)
    }

    private fun projections(track: TrackListeningStats) = ProductionListeningHistoryProjections(
        recentlyPlayed = listOf(RecentlyPlayedProjection(track)),
        mostPlayed = listOf(MostPlayedProjection(track))
    )

    private fun track(
        id: Long,
        bindings: List<ListeningBindingSnapshot>,
        title: String = "Track $id",
        legacy: Long = 1L,
        detailed: Long = 0L
    ) = TrackListeningStats(
        trackIdentityId = id,
        title = title,
        artist = "Artist",
        album = "Album",
        albumArtist = null,
        durationMs = 180_000L,
        binding = bindings.firstOrNull(),
        knownBindings = bindings,
        playCounts = ListeningPlayCountBreakdown(legacy + detailed, legacy, detailed),
        confirmedDetailedListeningMs = 0L,
        detailedEventCount = detailed,
        naturalCompletionCount = 0L,
        nonQualifiedAttemptCount = 0L,
        firstKnownPlayAt = 1L,
        latestKnownPlayAt = 2L,
        latestDetailedEventAt = null
    )

    private fun binding(song: Song) = binding(
        id = song.id,
        mediaStoreId = song.id,
        volumeName = song.volumeName,
        contentUri = song.uri.toString(),
        relativePath = song.relativePath,
        displayName = song.displayName,
        fileSizeBytes = song.fileSizeBytes,
        portableKey = song.songIdentity().portableKey
    )

    private fun binding(
        id: Long,
        mediaStoreId: Long? = id,
        volumeName: String? = "external_primary",
        contentUri: String? = "content://media/external/audio/$id",
        relativePath: String? = "Music/",
        displayName: String? = "$id.flac",
        fileSizeBytes: Long? = 12_000L,
        portableKey: String? = null
    ) = ListeningBindingSnapshot(
        localTrackBindingId = id,
        referenceKey = "binding:$id",
        mediaStoreId = mediaStoreId,
        volumeName = volumeName,
        contentUri = contentUri,
        relativePath = relativePath,
        displayName = displayName,
        fileSizeBytes = fileSizeBytes,
        dateModifiedEpochSeconds = 1_700_000_000L,
        durationMs = 180_000L,
        legacyStableKey = null,
        portableKey = portableKey,
        portableKeyVersion = SongIdentity.PORTABLE_KEY_VERSION,
        missingSince = null
    )

    private fun song(
        id: Long,
        title: String = "Track $id",
        relativePath: String = "Music/"
    ): Song {
        val uri = mock(Uri::class.java)
        doReturn("content://media/external/audio/$id").`when`(uri).toString()
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            duration = 180_000L,
            uri = uri,
            filePath = "/storage/$relativePath$id.flac",
            folderPath = "/storage/${relativePath.trimEnd('/')}",
            albumArtUri = null,
            volumeName = "external_primary",
            displayName = "$id.flac",
            relativePath = relativePath,
            fileSizeBytes = 12_000L,
            dateModifiedEpochSeconds = 1_700_000_000L
        )
    }
}
