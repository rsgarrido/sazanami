package io.github.rsgarrido.sazanami.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.SongReference
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class Media3PlaybackQueueRuntimeTest {
    @Test
    fun playingSwitchUsesOneSetPrepareAndPlayPath() {
        val player = mock(Player::class.java)
        `when`(player.playWhenReady).thenReturn(true)
        var beforeCount = 0
        var restoredBaseIds = emptyList<Long>()
        val runtime = Media3PlaybackQueueRuntime(
            player = player,
            isLogicalShuffleEnabled = { false },
            logicalBaseSongs = { listOf(song(1L), song(2L)) },
            beforeTimelineReplacement = { beforeCount += 1 },
            onBaseSongsRestored = { songs, _ -> restoredBaseIds = songs.map(Song::id) },
            mediaItemFactory = { mock(MediaItem::class.java) }
        )

        runtime.replaceTimeline(restoration(shouldPlay = true))

        verify(player, times(1)).pause()
        verify(player, times(1)).setMediaItems(anyList(), eq(1), eq(500L))
        verify(player, times(1)).prepare()
        verify(player, times(1)).play()
        assertEquals(1, beforeCount)
        assertEquals(listOf(1L, 2L), restoredBaseIds)
    }

    @Test
    fun pausedSwitchPreparesOnceWithoutPlaying() {
        val player = mock(Player::class.java)
        `when`(player.playWhenReady).thenReturn(false)
        val runtime = Media3PlaybackQueueRuntime(
            player = player,
            isLogicalShuffleEnabled = { false },
            logicalBaseSongs = { listOf(song(1L), song(2L)) },
            beforeTimelineReplacement = {},
            onBaseSongsRestored = { _, _ -> },
            mediaItemFactory = { mock(MediaItem::class.java) }
        )

        runtime.replaceTimeline(restoration(shouldPlay = false))

        verify(player, never()).pause()
        verify(player, times(1)).setMediaItems(anyList(), eq(1), eq(500L))
        verify(player, times(1)).prepare()
        verify(player, never()).play()
    }

    @Test
    fun seekTargetsExactDuplicateEntryWithoutReplacingOrPreparingTimeline() {
        val player = mock(Player::class.java)
        val firstDuplicateItem = mock(MediaItem::class.java)
        val otherItem = mock(MediaItem::class.java)
        val secondDuplicateItem = mock(MediaItem::class.java)
        val evidenceByItem = mapOf(
            firstDuplicateItem to evidence("duplicate-1", 7L),
            otherItem to evidence("other", 8L),
            secondDuplicateItem to evidence("duplicate-2", 7L)
        )
        `when`(player.mediaItemCount).thenReturn(3)
        `when`(player.currentMediaItemIndex).thenReturn(0)
        `when`(player.getMediaItemAt(0)).thenReturn(firstDuplicateItem)
        `when`(player.getMediaItemAt(1)).thenReturn(otherItem)
        `when`(player.getMediaItemAt(2)).thenReturn(secondDuplicateItem)
        val runtime = Media3PlaybackQueueRuntime(
            player = player,
            isLogicalShuffleEnabled = { false },
            logicalBaseSongs = { emptyList() },
            beforeTimelineReplacement = {},
            onBaseSongsRestored = { _, _ -> },
            evidenceReader = evidenceByItem::get
        )

        assertEquals(true, runtime.seekToEntry("duplicate-2"))

        verify(player).seekToDefaultPosition(2)
        verify(player, never()).setMediaItems(anyList())
        verify(player, never()).prepare()
        verify(player, never()).play()
    }

    @Test
    fun seekingCurrentEntryIsHarmless() {
        val player = mock(Player::class.java)
        val currentItem = mock(MediaItem::class.java)
        val evidenceByItem = mapOf(currentItem to evidence("current", 1L))
        `when`(player.mediaItemCount).thenReturn(1)
        `when`(player.currentMediaItemIndex).thenReturn(0)
        `when`(player.getMediaItemAt(0)).thenReturn(currentItem)
        val runtime = Media3PlaybackQueueRuntime(
            player = player,
            isLogicalShuffleEnabled = { false },
            logicalBaseSongs = { emptyList() },
            beforeTimelineReplacement = {},
            onBaseSongsRestored = { _, _ -> },
            evidenceReader = evidenceByItem::get
        )

        assertEquals(true, runtime.seekToEntry("current"))

        verify(player, never()).seekToDefaultPosition(0)
        verify(player, never()).prepare()
    }

    @Test
    fun moveRejectsCurrentBoundaryAndAllowsUpcomingReorderAfterCurrent() {
        val player = mock(Player::class.java)
        val currentItem = mock(MediaItem::class.java)
        val firstUpcomingItem = mock(MediaItem::class.java)
        val secondUpcomingItem = mock(MediaItem::class.java)
        val evidenceByItem = mapOf(
            currentItem to evidence("current", 1L),
            firstUpcomingItem to evidence("next-1", 2L),
            secondUpcomingItem to evidence("next-2", 3L)
        )
        `when`(player.mediaItemCount).thenReturn(3)
        `when`(player.currentMediaItemIndex).thenReturn(0)
        `when`(player.getMediaItemAt(0)).thenReturn(currentItem)
        `when`(player.getMediaItemAt(1)).thenReturn(firstUpcomingItem)
        `when`(player.getMediaItemAt(2)).thenReturn(secondUpcomingItem)
        val runtime = Media3PlaybackQueueRuntime(
            player = player,
            isLogicalShuffleEnabled = { false },
            logicalBaseSongs = { emptyList() },
            beforeTimelineReplacement = {},
            onBaseSongsRestored = { _, _ -> },
            evidenceReader = evidenceByItem::get
        )

        assertEquals(false, runtime.moveEntry("next-2", 0))
        assertEquals(false, runtime.moveEntry("current", 2))
        assertEquals(true, runtime.moveEntry("next-2", 1))

        verify(player, times(1)).moveMediaItem(2, 1)
        verify(player, never()).moveMediaItem(2, 0)
        verify(player, never()).moveMediaItem(0, 2)
    }

    private fun restoration(shouldPlay: Boolean) = PlaybackQueueRestoration(
        queueId = "queue",
        entries = listOf(
            resolved("first", song(1L), base = 0, playback = 1),
            resolved("second", song(2L), base = 1, playback = 0)
        ),
        currentEntryId = "first",
        currentPositionMs = 500L,
        shouldPlay = shouldPlay,
        shuffleEnabled = true,
        repeatMode = PersistedQueueRepeatMode.ALL
    )

    private fun resolved(
        entryId: String,
        song: Song,
        base: Int,
        playback: Int
    ) = ResolvedPlaybackQueueItem(
        persistedEntry = PlaybackQueueEntryEntity(
            entryId = entryId,
            queueId = "queue",
            trackIdentityId = song.id,
            localTrackBindingId = null,
            baseOrder = base,
            playbackOrder = playback
        ),
        song = song
    )

    private fun evidence(entryId: String, songId: Long) = ListeningMediaItemEvidence(
        itemInstanceId = entryId,
        referenceKey = "test-song-$songId",
        reference = SongReference(mediaStoreId = songId)
    )

    private fun song(id: Long): Song {
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn("content://media/$id")
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            trackNumber = id.toInt(),
            duration = 180_000L,
            uri = uri,
            filePath = "/music/$id.flac",
            folderPath = "/music",
            albumArtUri = null,
            volumeName = "external",
            displayName = "$id.flac",
            relativePath = "Music/",
            fileSizeBytes = 1_000L,
            dateModifiedEpochSeconds = 1L
        )
    }
}
