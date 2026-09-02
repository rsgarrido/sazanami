package io.github.rsgarrido.sazanami.ui.library

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.ui.state.LibrarySelectionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class LibrarySelectionActionPolicyTest {
    @Test
    fun `one song combines batch safe and exact item actions in order`() {
        val song = song(1L)
        val target = songSelectionActionSheetTarget(
            selectedSongs = listOf(song),
            singleSongTarget = exactSongTarget(song),
            favoriteMembershipKeys = emptySet(),
            rateSongLabel = "Rate song",
            onAddToAnotherQueue = {},
            onPlayInNewQueue = { _, _ -> },
            onApplyFavoriteBatch = {},
            onClearSelection = {}
        )

        assertNotNull(target)
        assertEquals("Song 1", target?.title)
        assertEquals(
            listOf(
                "Add to another queue...",
                "Play in new queue",
                "Add to favorites",
                "Pin to Home",
                "Rate song",
                "Edit tags"
            ),
            target?.actions?.map(LibraryItemAction::label)
        )
    }

    @Test
    fun `multiple songs contain only batch safe secondary actions`() {
        val songs = listOf(song(1L), song(2L))
        val target = songSelectionActionSheetTarget(
            selectedSongs = songs,
            singleSongTarget = null,
            favoriteMembershipKeys = emptySet(),
            rateSongLabel = "Rate song",
            onAddToAnotherQueue = {},
            onPlayInNewQueue = { _, _ -> },
            onApplyFavoriteBatch = {},
            onClearSelection = {}
        )

        assertEquals("2 songs selected", target?.title)
        assertEquals(
            listOf(
                "Add to another queue...",
                "Play in new queue",
                "Add to favorites"
            ),
            target?.actions?.map(LibraryItemAction::label)
        )
        assertFalse(target?.actions.orEmpty().any { it.label == "Pin to Home" })
        assertFalse(target?.actions.orEmpty().any { it.label == "Rate song" })
        assertFalse(target?.actions.orEmpty().any { it.label == "Edit tags" })
    }

    @Test
    fun `favorites action removes only when every selected song is favorite`() {
        val songs = listOf(song(1L), song(2L))
        val mixedTarget = songSelectionActionSheetTarget(
            selectedSongs = songs,
            singleSongTarget = null,
            favoriteMembershipKeys = setOf(songs.first().membershipKey()),
            rateSongLabel = "Rate song",
            onAddToAnotherQueue = {},
            onPlayInNewQueue = { _, _ -> },
            onApplyFavoriteBatch = {},
            onClearSelection = {}
        )
        val allFavoriteTarget = songSelectionActionSheetTarget(
            selectedSongs = songs,
            singleSongTarget = null,
            favoriteMembershipKeys = songs.mapTo(mutableSetOf(), Song::membershipKey),
            rateSongLabel = "Rate song",
            onAddToAnotherQueue = {},
            onPlayInNewQueue = { _, _ -> },
            onApplyFavoriteBatch = {},
            onClearSelection = {}
        )

        assertTrue(mixedTarget?.actions.orEmpty().any { it.label == "Add to favorites" })
        assertTrue(
            allFavoriteTarget?.actions.orEmpty().any { it.label == "Remove from favorites" }
        )
    }

    @Test
    fun `one album combines queue and available exact album actions`() {
        val album = album(1L)
        val target = albumSelectionActionSheetTarget(
            selectedAlbums = listOf(album),
            singleAlbumTarget = exactAlbumTarget(album),
            onAddToAnotherQueue = {},
            onPlayInNewQueue = { _, _ -> },
            onClearSelection = {}
        )

        assertEquals("Album 1", target?.title)
        assertEquals(
            listOf(
                "Add to another queue...",
                "Play in new queue",
                "Play",
                "Shuffle",
                "Pin to Home",
                "Edit album metadata"
            ),
            target?.actions?.map(LibraryItemAction::label)
        )
    }

    @Test
    fun `multiple albums contain only batch safe queue actions`() {
        val target = albumSelectionActionSheetTarget(
            selectedAlbums = listOf(album(1L), album(2L)),
            singleAlbumTarget = null,
            onAddToAnotherQueue = {},
            onPlayInNewQueue = { _, _ -> },
            onClearSelection = {}
        )

        assertEquals("2 albums selected", target?.title)
        assertEquals(
            listOf("Add to another queue...", "Play in new queue"),
            target?.actions?.map(LibraryItemAction::label)
        )
    }

    @Test
    fun `another queue retains selection while direct and exact actions clear it`() {
        val events = mutableListOf<String>()
        val selectedSong = song(1L)
        val target = songSelectionActionSheetTarget(
            selectedSongs = listOf(selectedSong),
            singleSongTarget = exactSongTarget(selectedSong) { events += it },
            favoriteMembershipKeys = emptySet(),
            rateSongLabel = "Rate song",
            onAddToAnotherQueue = { events += "another" },
            onPlayInNewQueue = { _, _ -> events += "new" },
            onApplyFavoriteBatch = { events += "favorite" },
            onClearSelection = { events += "clear" }
        ) ?: error("Expected a selection action target")

        target.actions.first { it.label == "Add to another queue..." }.onClick()
        assertEquals(listOf("another"), events)

        events.clear()
        target.actions.first { it.label == "Play in new queue" }.onClick()
        assertEquals(listOf("new", "clear"), events)

        events.clear()
        target.actions.first { it.label == "Add to favorites" }.onClick()
        assertEquals(listOf("favorite", "clear"), events)

        events.clear()
        target.actions.first { it.label == "Pin to Home" }.onClick()
        assertEquals(listOf("clear", "pin"), events)
    }

    @Test
    fun `header action state dismisses without dropping same entity provider`() {
        val state = LibrarySelectionHeaderState()
        val target = selectionSummaryTarget("Selection")
        state.bind(LibrarySelectionEntity.SONG, listOf("song"), false) { target }

        state.showMore()
        assertEquals(target, state.activeActionTarget)
        state.dismissActions()
        assertNull(state.activeActionTarget)

        state.bind(LibrarySelectionEntity.SONG, emptyList(), true, null)
        assertTrue(state.binding?.hasMoreAction == true)
        state.showMore()
        assertEquals(target, state.activeActionTarget)
    }

    private fun exactSongTarget(
        song: Song,
        onAction: (String) -> Unit = {}
    ) = LibraryItemActionSheetTarget(
        title = song.title,
        subtitle = song.artist,
        artworkUri = song.albumArtUri,
        artworkDescription = "Album art for ${song.title}",
        actions = listOf(
            LibraryItemAction("Pin to Home", Icons.Filled.PushPin) { onAction("pin") },
            LibraryItemAction("Rate song", Icons.Filled.Star) { onAction("rate") },
            LibraryItemAction("Edit tags", Icons.Filled.Edit) { onAction("edit") }
        )
    )

    private fun exactAlbumTarget(album: LibraryAlbumGroup) = LibraryItemActionSheetTarget(
        title = album.title,
        subtitle = album.artistText,
        artworkUri = album.songs.firstOrNull()?.albumArtUri,
        artworkDescription = "Album art for ${album.title}",
        actions = listOf(
            LibraryItemAction("Play", Icons.Filled.PlayArrow) {},
            LibraryItemAction("Shuffle", Icons.Filled.Shuffle) {},
            LibraryItemAction("Pin to Home", Icons.Filled.PushPin) {},
            LibraryItemAction("Edit album metadata", Icons.Filled.EditNote) {}
        )
    )

    private fun selectionSummaryTarget(title: String) = LibraryItemActionSheetTarget(
        title = title,
        subtitle = null,
        artworkUri = null,
        artworkDescription = title,
        actions = emptyList()
    )

    private fun album(id: Long): LibraryAlbumGroup = LibraryAlbumGroup(
        key = "album-$id",
        title = "Album $id",
        artistText = "Artist $id",
        songs = listOf(song(id))
    )

    private fun song(id: Long): Song = Song(
        id = id,
        title = "Song $id",
        artist = "Artist $id",
        album = "Album $id",
        trackNumber = 1,
        duration = 120_000L,
        uri = mock(Uri::class.java),
        filePath = "/music/song-$id.flac",
        folderPath = "/music/album-$id",
        albumArtUri = null
    )
}
