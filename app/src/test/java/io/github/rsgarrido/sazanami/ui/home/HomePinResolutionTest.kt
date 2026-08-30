package io.github.rsgarrido.sazanami.ui.home

import android.net.Uri
import io.github.rsgarrido.sazanami.data.ArtistPictureAssignment
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.PlaylistArtworkMode
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.SongReference
import io.github.rsgarrido.sazanami.data.UNKNOWN_ARTIST_IDENTITY
import io.github.rsgarrido.sazanami.data.artistIdentity
import io.github.rsgarrido.sazanami.data.home.HomePin
import io.github.rsgarrido.sazanami.data.home.HomePinType
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
        val albumArtwork = mock(Uri::class.java)
        val moved = song(
            id = 9L,
            relativePath = "Music/New Album/",
            albumArtUri = albumArtwork
        )
        val pin = requireNotNull(HomePin.album("Album", "Artist", listOf(before)))

        val resolved = resolveHomePins(listOf(pin), listOf(moved)).single()
        val target = resolved.target as HomePinTarget.AlbumTarget

        assertEquals(moved.folderPath, target.album.key)
        assertEquals("Album", target.album.title)
        assertNull(resolved.artistPictureIdentityOrNull())
        assertEquals(albumArtwork, resolved.artworkUri)
    }

    @Test
    fun albumPinAnchorResolvesToCombinedMultiDiscAlbum() {
        val discOne = song(
            id = 1L,
            relativePath = "Music/Album/CD1/",
            discNumber = 1
        )
        val discTwo = song(
            id = 2L,
            relativePath = "Music/Album/CD2/",
            discNumber = 2
        )
        val pin = requireNotNull(HomePin.album("Album", "Artist", listOf(discOne)))

        val resolved = resolveHomePins(listOf(pin), listOf(discOne, discTwo)).single()
        val target = resolved.target as HomePinTarget.AlbumTarget

        assertTrue(target.album.key.startsWith("multi-disc:"))
        assertEquals(listOf(1L, 2L), target.album.songs.map(Song::id))
    }

    @Test
    fun artistPinUsesCurrentArtistAfterAnchorTrackIsRetagged() {
        val before = song(id = 1L, artist = "Old Artist")
        val retagged = song(id = 22L, artist = "New Artist")
        val pin = requireNotNull(HomePin.artist("Old Artist", listOf(before)))

        val resolved = resolveHomePins(listOf(pin), listOf(retagged)).single()
        val target = resolved.target as HomePinTarget.ArtistTarget

        assertEquals("New Artist", target.artist.name)
        assertEquals(artistIdentity("New Artist"), resolved.artistPictureIdentityOrNull())
    }

    @Test
    fun artistPinUsesTheSameCaseAndWhitespaceNormalizedPictureIdentity() {
        val artistSong = song(id = 1L, artist = "  THE   WARNING ")
        val pin = requireNotNull(HomePin.artist("The Warning", listOf(artistSong)))

        val resolved = resolveHomePins(listOf(pin), listOf(artistSong)).single()

        assertEquals(artistIdentity("the warning"), resolved.artistPictureIdentityOrNull())
    }

    @Test
    fun artistPinAssignmentLookupTracksSetReplacementAndRemoval() {
        val artistSong = song(id = 1L, artist = "Artist")
        val pin = requireNotNull(HomePin.artist("Artist", listOf(artistSong)))
        val resolved = resolveHomePins(listOf(pin), listOf(artistSong)).single()
        val identity = artistIdentity("Artist")
        val first = ArtistPictureAssignment(
            artistKey = identity.key,
            normalizedArtistName = identity.normalizedName,
            assetReference = "artist-first.image",
            updatedAt = 1L
        )
        val replacement = first.copy(
            assetReference = "artist-replacement.image",
            updatedAt = 2L
        )

        assertNull(resolved.artistPictureAssignmentOrNull(emptyMap()))
        assertEquals(first, resolved.artistPictureAssignmentOrNull(mapOf(identity.key to first)))
        assertEquals(
            replacement,
            resolved.artistPictureAssignmentOrNull(mapOf(identity.key to replacement))
        )
        assertNull(resolved.artistPictureAssignmentOrNull(emptyMap()))
    }

    @Test
    fun unknownArtistPinDoesNotExposeCustomPictureIdentity() {
        val unknownSong = song(id = 1L, artist = "")
        val pin = requireNotNull(HomePin.artist("Unknown Artist", listOf(unknownSong)))

        val resolved = resolveHomePins(listOf(pin), listOf(unknownSong)).single()

        assertEquals(UNKNOWN_ARTIST_IDENTITY, (resolved.target as HomePinTarget.ArtistTarget).artist.identity)
        assertNull(resolved.artistPictureIdentityOrNull())
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

    @Test
    fun pinnedPlaylistPublishesCustomArtworkAheadOfAutomaticSources() {
        val initialPlaylist = Playlist(
            playlistId = 41L,
            name = "Pinned",
            songCount = 1
        )
        val pin = HomePin.playlist(initialPlaylist)
        val initialResolved = resolveHomePins(
            pins = listOf(pin),
            songs = emptyList(),
            playlists = listOf(initialPlaylist)
        )
        val automaticCover = mock(Uri::class.java)
        val customPlaylist = initialPlaylist.copy(
            artworkMode = PlaylistArtworkMode.CUSTOM,
            artworkReference = "playlist-41-custom-v2",
            automaticArtworkSongs = listOf(song(id = 6L, albumArtUri = automaticCover))
        )
        val updatedResolved = resolveHomePins(
            pins = listOf(pin),
            songs = emptyList(),
            playlists = listOf(customPlaylist)
        )

        val displayed = homePinsInVisualOrder(
            authoritativePins = updatedResolved,
            visualPinIds = initialResolved.map { resolved -> resolved.pin.id }
        ).single()
        val displayedPlaylist =
            (displayed.target as HomePinTarget.PlaylistTarget).playlist

        assertEquals(PlaylistArtworkMode.CUSTOM, displayedPlaylist.artworkMode)
        assertEquals("playlist-41-custom-v2", displayedPlaylist.artworkReference)
        assertTrue(displayedPlaylist.automaticArtworkSongs.isNotEmpty())
    }

    @Test
    fun pinnedPlaylistPublishesAutomaticCollageSourcesWithoutChangingVisualOrder() {
        val initialPlaylist = Playlist(
            playlistId = 42L,
            name = "Pinned",
            songCount = 1
        )
        val pin = HomePin.playlist(initialPlaylist)
        val initialResolved = resolveHomePins(
            pins = listOf(pin),
            songs = emptyList(),
            playlists = listOf(initialPlaylist)
        )
        val cover = mock(Uri::class.java)
        val collageSong = song(id = 7L, albumArtUri = cover)
        val updatedResolved = resolveHomePins(
            pins = listOf(pin),
            songs = emptyList(),
            playlists = listOf(
                initialPlaylist.copy(automaticArtworkSongs = listOf(collageSong))
            )
        )

        val displayed = homePinsInVisualOrder(
            authoritativePins = updatedResolved,
            visualPinIds = initialResolved.map { resolved -> resolved.pin.id }
        ).single()
        val displayedPlaylist =
            (displayed.target as HomePinTarget.PlaylistTarget).playlist

        assertEquals(PlaylistArtworkMode.AUTOMATIC, displayedPlaylist.artworkMode)
        assertEquals(listOf(collageSong), displayedPlaylist.automaticArtworkSongs)
    }

    @Test
    fun pinnedPlaylistWithoutArtworkRemainsAPlaceholderCandidate() {
        val playlist = Playlist(
            playlistId = 43L,
            name = "Pinned",
            songCount = 0
        )
        val pin = HomePin.playlist(playlist)
        val resolved = resolveHomePins(
            pins = listOf(pin),
            songs = emptyList(),
            playlists = listOf(playlist)
        )

        val displayed = homePinsInVisualOrder(
            authoritativePins = resolved,
            visualPinIds = resolved.map { current -> current.pin.id }
        ).single()
        val displayedPlaylist =
            (displayed.target as HomePinTarget.PlaylistTarget).playlist

        assertNull(displayedPlaylist.artworkReference)
        assertTrue(displayedPlaylist.automaticArtworkSongs.isEmpty())
    }

    @Test
    fun visualOrderProjectionPreservesArtistAndAlbumTargets() {
        val albumSong = song(id = 1L, artist = "Album Artist")
        val artistSong = song(id = 2L, artist = "Pinned Artist")
        val albumPin = requireNotNull(
            HomePin.album("Album", "Album Artist", listOf(albumSong))
        )
        val artistPin = requireNotNull(
            HomePin.artist("Pinned Artist", listOf(artistSong))
        )
        val authoritative = resolveHomePins(
            pins = listOf(albumPin, artistPin),
            songs = listOf(albumSong, artistSong)
        )

        val displayed = homePinsInVisualOrder(
            authoritativePins = authoritative,
            visualPinIds = listOf(artistPin.id, albumPin.id)
        )

        assertEquals(
            listOf(artistPin.id, albumPin.id),
            displayed.map { current -> current.pin.id }
        )
        assertTrue(displayed[0].target is HomePinTarget.ArtistTarget)
        assertTrue(displayed[1].target is HomePinTarget.AlbumTarget)
    }

    private fun song(
        id: Long,
        artist: String = "Artist",
        relativePath: String = "Music/Album/",
        displayName: String = "track.flac",
        fileSizeBytes: Long = 12_000L,
        discNumber: Int? = null,
        albumArtUri: Uri? = null
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
            albumArtUri = albumArtUri,
            volumeName = "external_primary",
            relativePath = relativePath,
            displayName = displayName,
            fileSizeBytes = fileSizeBytes,
            dateModifiedEpochSeconds = 1_700_000_000L,
            albumArtist = "Artist",
            discNumber = discNumber,
            discTotal = discNumber?.let { 2 }
        )
    }
}
