package com.example.cdplaya.ui.library

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import com.example.cdplaya.data.Song
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class LibraryActionSheetTargetTest {
    @Test
    fun songTargetPreservesExistingActionsAndCallbacks() {
        val song = testSong()
        val invokedActions = mutableListOf<String>()

        val target = songActionSheetTarget(
            song = song,
            wasRecentlyAdded = false,
            isFavorite = false,
            onPlayNextClick = { invokedActions += "play_next" },
            onAddToQueueClick = { invokedActions += "queue" },
            onToggleFavoriteClick = { invokedActions += "favorite" },
            onAddToPlaylistClick = { invokedActions += "playlist" },
            onEditSongTagsClick = { invokedActions += "edit" },
            rateSongLabel = "Rate song",
            onRateSongClick = { invokedActions += "rate" }
        )

        assertEquals(
            listOf(
                "Play next",
                "Add to queue",
                "Add to favorites",
                "Rate song",
                "Add to playlist",
                "Edit tags"
            ),
            target.actions.map { action -> action.label }
        )

        target.actions.forEach { action -> action.onClick() }
        assertEquals(
            listOf("play_next", "queue", "favorite", "rate", "playlist", "edit"),
            invokedActions
        )
    }

    @Test
    fun collectionTargetsPreserveExistingActionOrder() {
        val song = testSong()
        val noOp: (String, List<Song>) -> Unit = { _, _ -> }

        val albumTarget = albumActionSheetTarget(
            albumTitle = "Album",
            subtitle = "Artist • 1 song",
            artworkUri = null,
            albumSongs = listOf(song),
            onPlayClick = noOp,
            onShuffleClick = noOp,
            onPlayNextClick = noOp,
            onAddToQueueClick = noOp,
            onAddToPlaylistClick = noOp
        )
        val artistTarget = artistActionSheetTarget(
            artistName = "Artist",
            subtitle = "1 song",
            artworkUri = null,
            artistSongs = listOf(song),
            onPlayClick = noOp,
            onShuffleClick = noOp,
            onPlayNextClick = noOp,
            onAddToQueueClick = noOp,
            onAddToPlaylistClick = noOp
        )
        val expected = listOf("Play", "Shuffle", "Play next", "Add to queue", "Add to playlist")

        assertEquals(expected, albumTarget.actions.map { action -> action.label })
        assertEquals(expected, artistTarget.actions.map { action -> action.label })
    }

    @Test
    fun homePinActionCanBeInjectedWithoutChangingExistingCollectionActions() {
        val song = testSong()
        val noOp: (String, List<Song>) -> Unit = { _, _ -> }
        var pinInvoked = false
        val pinAction = LibraryItemAction(
            label = "Pin to Home",
            icon = Icons.Filled.PushPin,
            onClick = { pinInvoked = true }
        )

        val songTarget = songActionSheetTarget(
            song = song,
            wasRecentlyAdded = false,
            isFavorite = false,
            onPlayNextClick = {},
            onAddToQueueClick = {},
            onToggleFavoriteClick = {},
            onAddToPlaylistClick = {},
            onEditSongTagsClick = {},
            rateSongLabel = "Rate song",
            onRateSongClick = {},
            homePinAction = pinAction
        )
        val albumTarget = albumActionSheetTarget(
            albumTitle = "Album",
            subtitle = "Artist • 1 song",
            artworkUri = null,
            albumSongs = listOf(song),
            onPlayClick = noOp,
            onShuffleClick = noOp,
            onPlayNextClick = noOp,
            onAddToQueueClick = noOp,
            onAddToPlaylistClick = noOp,
            homePinAction = pinAction
        )
        val artistTarget = artistActionSheetTarget(
            artistName = "Artist",
            subtitle = "1 song",
            artworkUri = null,
            artistSongs = listOf(song),
            onPlayClick = noOp,
            onShuffleClick = noOp,
            onPlayNextClick = noOp,
            onAddToQueueClick = noOp,
            onAddToPlaylistClick = noOp,
            homePinAction = pinAction
        )

        assertEquals(
            listOf(
                "Play next",
                "Add to queue",
                "Add to favorites",
                "Pin to Home",
                "Rate song",
                "Add to playlist",
                "Edit tags"
            ),
            songTarget.actions.map { it.label }
        )
        assertEquals("Pin to Home", albumTarget.actions.last().label)
        assertEquals("Pin to Home", artistTarget.actions.last().label)

        songTarget.actions.first { it.label == "Pin to Home" }.onClick()
        assertEquals(true, pinInvoked)
    }

    private fun testSong(): Song {
        return Song(
            id = 1L,
            title = "Song",
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            duration = 120_000L,
            uri = mock(Uri::class.java),
            filePath = "/music/song.flac",
            folderPath = "/music",
            albumArtUri = null
        )
    }
}
