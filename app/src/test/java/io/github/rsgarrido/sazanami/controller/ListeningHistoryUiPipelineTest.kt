package io.github.rsgarrido.sazanami.controller

import android.net.Uri
import io.github.rsgarrido.sazanami.data.ListeningBindingSnapshot
import io.github.rsgarrido.sazanami.data.ListeningPlayCountBreakdown
import io.github.rsgarrido.sazanami.data.MostPlayedProjection
import io.github.rsgarrido.sazanami.data.ProductionListeningHistoryProjections
import io.github.rsgarrido.sazanami.data.RecentlyPlayedProjection
import io.github.rsgarrido.sazanami.data.ResolvedProductionListeningHistory
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.SongIdentity
import io.github.rsgarrido.sazanami.data.SongReferenceIndex
import io.github.rsgarrido.sazanami.data.TrackListeningStats
import io.github.rsgarrido.sazanami.data.membershipKey
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class ListeningHistoryUiPipelineTest {
    @Test
    fun publishesBothProductionListsAndRemapsTheSameHistoryOnLibraryChanges() = runBlocking {
        val first = song(1)
        val second = song(2)
        val firstTrack = track(first)
        val secondTrack = track(second)
        val history = MutableStateFlow(
            ProductionListeningHistoryProjections(
                recentlyPlayed = listOf(firstTrack, secondTrack).map(::RecentlyPlayedProjection),
                mostPlayed = listOf(secondTrack, firstTrack).map(::MostPlayedProjection)
            )
        )
        val library = MutableStateFlow(
            IndexedLibrarySnapshot(SongReferenceIndex.EMPTY, emptySet())
        )
        val publications = Channel<ResolvedProductionListeningHistory>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            collectProductionListeningHistory(history, library) { publications.trySend(it) }
        }

        assertTrue(receiveUntil(publications) { it.recentlyPlayed.isEmpty() }.mostPlayed.isEmpty())

        val indexedSongs = listOf(second, first)
        library.value = IndexedLibrarySnapshot(
            SongReferenceIndex.build(indexedSongs),
            indexedSongs.mapTo(mutableSetOf(), Song::membershipKey)
        )
        val populated = receiveUntil(publications) { it.recentlyPlayed.isNotEmpty() }
        assertEquals(listOf(first, second), populated.recentlyPlayed)
        assertEquals(listOf(second, first), populated.mostPlayed)

        library.value = library.value.copy(visibleMembershipKeys = setOf(second.membershipKey()))
        val filtered = receiveUntil(publications) { it.recentlyPlayed == listOf(second) }
        assertEquals(listOf(second), filtered.recentlyPlayed)
        assertEquals(listOf(second), filtered.mostPlayed)

        collector.cancelAndJoin()
        assertTrue(publications.close())
    }

    private suspend fun receiveUntil(
        channel: Channel<ResolvedProductionListeningHistory>,
        predicate: (ResolvedProductionListeningHistory) -> Boolean
    ): ResolvedProductionListeningHistory = withTimeout(5_000L) {
        while (true) {
            val value = channel.receive()
            if (predicate(value)) return@withTimeout value
        }
        error("unreachable")
    }

    private fun track(song: Song): TrackListeningStats {
        val binding = ListeningBindingSnapshot(
            localTrackBindingId = song.id,
            referenceKey = "binding:${song.id}",
            mediaStoreId = song.id,
            volumeName = song.volumeName,
            contentUri = song.uri.toString(),
            relativePath = song.relativePath,
            displayName = song.displayName,
            fileSizeBytes = song.fileSizeBytes,
            dateModifiedEpochSeconds = song.dateModifiedEpochSeconds,
            durationMs = song.duration,
            legacyStableKey = null,
            portableKey = null,
            portableKeyVersion = SongIdentity.PORTABLE_KEY_VERSION,
            missingSince = null
        )
        return TrackListeningStats(
            trackIdentityId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            albumArtist = song.albumArtist,
            durationMs = song.duration,
            binding = binding,
            knownBindings = listOf(binding),
            playCounts = ListeningPlayCountBreakdown(1L, 1L, 0L),
            confirmedDetailedListeningMs = 0L,
            detailedEventCount = 0L,
            naturalCompletionCount = 0L,
            nonQualifiedAttemptCount = 0L,
            firstKnownPlayAt = 1L,
            latestKnownPlayAt = 2L,
            latestDetailedEventAt = null
        )
    }

    private fun song(id: Long): Song {
        val uri = mock(Uri::class.java)
        doReturn("content://media/external/audio/$id").`when`(uri).toString()
        return Song(
            id = id,
            title = "Track $id",
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            duration = 180_000L,
            uri = uri,
            filePath = "/storage/Music/$id.flac",
            folderPath = "/storage/Music",
            albumArtUri = null,
            volumeName = "external_primary",
            displayName = "$id.flac",
            relativePath = "Music/",
            fileSizeBytes = 12_000L,
            dateModifiedEpochSeconds = 1_700_000_000L
        )
    }
}
