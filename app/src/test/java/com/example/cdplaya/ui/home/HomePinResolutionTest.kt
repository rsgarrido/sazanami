package com.example.cdplaya.ui.home

import android.net.Uri
import com.example.cdplaya.data.Playlist
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.SongReference
import com.example.cdplaya.data.home.HomePin
import com.example.cdplaya.data.home.HomePinType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class HomePinResolutionTest {
    @Test
    fun albumPinFollowsItsAnchorWhenAlbumFolderMoves() {
        val before = song(id = 1L, relativePath = "Music/Old Album/")
        val moved = song(id = 9L, relativePath = "Music/New Album/")
        val pin = requireNotNull(HomePin.album("Album", "Artist", listOf(before)))

        val resolved = resolveHomePins(listOf(pin), listOf(moved)).single()
        val target = resolved.target as HomePinTarget.AlbumTarget

        assertEquals(moved.folderPath, target.album.key)
        assertEquals("Album", target.album.title)
    }

    @Test
    fun artistPinUsesCurrentArtistAfterAnchorTrackIsRetagged() {
        val before = song(id = 1L, artist = "Old Artist")
        val retagged = song(id = 22L, artist = "New Artist")
        val pin = requireNotNull(HomePin.artist("Old Artist", listOf(before)))

        val resolved = resolveHomePins(listOf(pin), listOf(retagged)).single()
        val target = resolved.target as HomePinTarget.ArtistTarget

        assertEquals("New Artist", target.artist.name)
    }

    @Test
    fun unresolvedPinIsKeptSoUserCanStillManageOrRemoveIt() {
        val pin = HomePin(
            id = "missing-pin",
            type = HomePinType.SONG,
            title = "Missing Song",
            subtitle = "Missing Artist",
            anchor = SongReference(
                relativePath = "Music/Missing/",
                displayName = "missing.flac"
            )
        )

        val resolved = resolveHomePins(listOf(pin), emptyList()).single()

        assertEquals("Missing Song", resolved.title)
        assertNull(resolved.target)
    }

    @Test
    fun songPinResolvesAfterIdPathAndFilenameChangeThroughPortableIdentity() {
        val before = song(id = 1L)
        val restored = song(
            id = 33L,
            relativePath = "Restored/Album/",
            displayName = "renamed.flac",
            fileSizeBytes = 99L
        )
        val pin = HomePin.song(before)

        val resolved = resolveHomePins(listOf(pin), listOf(restored)).single()

        assertTrue(resolved.target is HomePinTarget.SongTarget)
        assertEquals(33L, (resolved.target as HomePinTarget.SongTarget).song.id)
    }

    @Test
    fun playlistPinResolvesByStablePlaylistId() {
        val original = Playlist(playlistId = 41L, name = "Old name", songCount = 1)
        val renamed = original.copy(name = "New name", songCount = 3)
        val pin = HomePin.playlist(original)

        val resolved = resolveHomePins(
            pins = listOf(pin),
            songs = emptyList(),
            playlists = listOf(renamed)
        ).single()

        val target = resolved.target as HomePinTarget.PlaylistTarget
        assertEquals(41L, target.playlist.playlistId)
        assertEquals("New name", resolved.title)
    }

    @Test
    fun deletedPlaylistPinIsOmittedFromResolvedHomePins() {
        val pin = HomePin.playlist(
            Playlist(playlistId = 41L, name = "Deleted", songCount = 1)
        )

        assertTrue(
            resolveHomePins(
                pins = listOf(pin),
                songs = emptyList(),
                playlists = emptyList()
            ).isEmpty()
        )
    }

    private fun song(
        id: Long,
        artist: String = "Artist",
        relativePath: String = "Music/Album/",
        displayName: String = "track.flac",
        fileSizeBytes: Long = 12_000L
    ): Song {
        val mockedUri = mock(Uri::class.java)
        doReturn("content://media/external/audio/$id").`when`(mockedUri).toString()
        return Song(
            id = id,
            title = "Title",
            artist = artist,
            album = "Album",
            trackNumber = 1,
            duration = 180_000L,
            uri = mockedUri,
            filePath = "/storage/$relativePath$displayName",
            folderPath = "/storage/${relativePath.trimEnd('/')}",
            albumArtUri = null,
            volumeName = "external_primary",
            relativePath = relativePath,
            displayName = displayName,
            fileSizeBytes = fileSizeBytes,
            dateModifiedEpochSeconds = 1_700_000_000L
        )
    }
}
