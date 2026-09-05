package io.github.rsgarrido.sazanami.player

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class AndroidAutoSearchResolverTest {
    private val leftBehind = song(1, "Left Behind", "Slipknot", "Iowa", 8)
    private val peopleEquals = song(2, "People = Shit", "Slipknot", "Iowa", 1)
    private val warning = song(3, "S!CK", "The Warning", "Keep Me Fed", 2)
    private val catalog = AndroidAutoCatalogSnapshot(
        songs = listOf(leftBehind, peopleEquals, warning),
        playlists = listOf(
            AutoPlaylistEntry(
                playlistId = 44,
                name = "Heavy Rotation",
                songs = listOf(warning, leftBehind)
            )
        ),
        artistArtworkUris = emptyMap()
    )

    @Test
    fun `title by artist voice query resolves exact song`() {
        val match = AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(query = "play Left Behind by Slipknot"),
            catalog
        )

        assertNotNull(match)
        assertEquals(leftBehind.id, match!!.selectedSong.id)
        assertEquals(listOf(peopleEquals.id, leftBehind.id), match.songs.map(Song::id))
    }

    @Test
    fun `structured artist query resolves artist library context`() {
        val match = AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(artist = "Slipknot"),
            catalog
        )

        assertNotNull(match)
        assertEquals(setOf(leftBehind.id, peopleEquals.id), match!!.songs.map(Song::id).toSet())
    }

    @Test
    fun `playlist voice query resolves playlist order`() {
        val match = AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(playlist = "Heavy Rotation"),
            catalog
        )

        assertEquals(listOf(warning.id, leftBehind.id), match!!.songs.map(Song::id))
        assertEquals(warning.id, match.selectedSong.id)
    }

    @Test
    fun `browser search ranks exact song title first`() {
        val results = AndroidAutoSearchResolver.searchSongs("Left Behind", catalog)

        assertEquals(leftBehind.id, results.first().id)
    }

    @Test
    fun `unmatched voice query does not fall back to current song`() {
        val match = AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(query = "Everything's True"),
            catalog,
            preferredSongId = warning.id
        )

        assertNull(match)
    }

    @Test
    fun `empty request still preserves generic playback fallback`() {
        val match = AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(),
            catalog,
            preferredSongId = warning.id
        )

        assertNotNull(match)
        assertEquals(warning.id, match!!.selectedSong.id)
        assertEquals(catalog.songs.map(Song::id), match.songs.map(Song::id))
    }

    @Test
    fun `generic music request still resolves the library`() {
        val match = AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(query = "play my music"),
            catalog,
            preferredSongId = peopleEquals.id
        )

        assertNotNull(match)
        assertEquals(peopleEquals.id, match!!.selectedSong.id)
        assertEquals(catalog.songs.map(Song::id), match.songs.map(Song::id))
    }

    @Test
    fun `exact song beats partial playlist and exact artist beats partial album`() {
        val expanded = catalog.copy(
            songs = catalog.songs + song(9, "Other", "Other", "Slipknot Covers", 1),
            playlists = catalog.playlists + AutoPlaylistEntry(99, "Left Behind Mix", listOf(warning))
        )
        assertEquals(leftBehind.id, AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(query = "Left Behind"), expanded
        )!!.selectedSong.id)
        assertEquals(setOf(1L, 2L), AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(query = "Slipknot"), expanded
        )!!.songs.map(Song::id).toSet())
    }

    @Test
    fun `real car song phrase resolves one valid playback application`() {
        val shatteredHeart = song(20, "Shattered Heart", "The Warning", "XXI Century Blood", 4)
        val expanded = catalog.copy(songs = catalog.songs + shatteredHeart)

        val match = AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(
                query = "Play Shattered Heart by The Warning on Sazanami"
            ),
            expanded
        )

        assertNotNull(match)
        assertEquals(shatteredHeart.id, match!!.selectedSong.id)
        assertEquals(match.selectedSong.id, match.songs[match.startIndex].id)
    }

    @Test
    fun `real car artist phrase expands normal artist context`() {
        val first = song(30, "Girlfriend", "Avril Lavigne", "The Best Damn Thing", 1)
        val second = song(31, "When You're Gone", "Avril Lavigne", "The Best Damn Thing", 2)
        val expanded = catalog.copy(songs = catalog.songs + listOf(second, first))

        val match = AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(query = "Play Avril Lavigne on Sazanami"),
            expanded
        )

        assertEquals(listOf(first.id, second.id), match!!.songs.map(Song::id))
        assertEquals(first.id, match.selectedSong.id)
    }

    @Test
    fun `real car album by artist phrase expands album track order`() {
        val first = song(30, "Girlfriend", "Avril Lavigne", "The Best Damn Thing", 1)
        val second = song(31, "When You're Gone", "Avril Lavigne", "The Best Damn Thing", 2)
        val expanded = catalog.copy(songs = catalog.songs + listOf(second, first))

        val match = AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(
                query = "Play The Best Damn Thing by Avril Lavigne on Sazanami"
            ),
            expanded
        )

        assertEquals(listOf(first.id, second.id), match!!.songs.map(Song::id))
        assertEquals(0, match.startIndex)
    }

    @Test
    fun `focused artist and album requests resolve identically cold and warm`() {
        val first = song(30, "Girlfriend", "Avril Lavigne", "The Best Damn Thing", 1)
        val second = song(31, "When You're Gone", "Avril Lavigne", "The Best Damn Thing", 2)
        val cold = catalog.copy(songs = catalog.songs + listOf(second, first))
        val warm = cold.copy(songs = cold.songs.map { it.copy(albumArtUri = mock(Uri::class.java)) })

        listOf(
            AndroidAutoSearchRequest(
                artist = "Avril Lavigne",
                requestType = AndroidAutoRequestType.ARTIST
            ),
            AndroidAutoSearchRequest(
                album = "The Best Damn Thing",
                artist = "Avril Lavigne",
                requestType = AndroidAutoRequestType.ALBUM
            )
        ).forEach { request ->
            val coldMatch = AndroidAutoSearchResolver.resolvePlayback(request, cold)!!
            val warmMatch = AndroidAutoSearchResolver.resolvePlayback(request, warm)!!
            assertEquals(coldMatch.songs.map(Song::id), warmMatch.songs.map(Song::id))
            assertEquals(coldMatch.startIndex, warmMatch.startIndex)
        }
    }

    @Test
    fun `genre and playlist searches produce playable browser results`() {
        val rockCatalog = catalog.copy(songs = listOf(leftBehind.copy(genres = listOf("Rock")), warning))
        assertEquals(listOf(1L), AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(query = "play rock"), rockCatalog
        )!!.songs.map(Song::id))
        assertEquals(listOf(1L), AndroidAutoSearchResolver.searchSongs("rock", rockCatalog).map(Song::id))
        assertEquals(setOf(1L, 3L), AndroidAutoSearchResolver.searchSongs("Heavy Rotation", catalog).map(Song::id).toSet())
    }

    @Test
    fun `cold and warm artwork differences do not affect any voice category`() {
        val cold = catalog.copy(songs = catalog.songs.map { it.copy(genres = listOf("Rock")) })
        val warm = cold.copy(songs = cold.songs.map { it.copy(albumArtUri = mock(Uri::class.java)) })
        listOf(
            AndroidAutoSearchRequest(query = "Left Behind"),
            AndroidAutoSearchRequest(artist = "Slipknot"),
            AndroidAutoSearchRequest(album = "Iowa"),
            AndroidAutoSearchRequest(playlist = "Heavy Rotation"),
            AndroidAutoSearchRequest(genre = "Rock"),
            AndroidAutoSearchRequest(query = "play music")
        ).forEach { request ->
            val coldMatch = AndroidAutoSearchResolver.resolvePlayback(request, cold)!!
            val warmMatch = AndroidAutoSearchResolver.resolvePlayback(request, warm)!!
            assertEquals(coldMatch.songs.map(Song::id), warmMatch.songs.map(Song::id))
            assertEquals(coldMatch.startIndex, warmMatch.startIndex)
        }
    }

    @Test
    fun `ambiguous titles use deterministic voice ranking`() {
        val other = leftBehind.copy(id = 99L, album = "Z Compilation")
        val expanded = catalog.copy(songs = listOf(other) + catalog.songs)
        assertEquals(leftBehind.id, AndroidAutoSearchResolver.resolvePlayback(
            AndroidAutoSearchRequest(query = "Left Behind"), expanded
        )!!.selectedSong.id)
    }

    private fun song(
        id: Long,
        title: String,
        artist: String,
        album: String,
        track: Int
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        trackNumber = track,
        duration = 240_000,
        uri = mock(Uri::class.java),
        filePath = "/music/$artist/$album/$title.flac",
        folderPath = "/music/$artist/$album",
        albumArtUri = null
    )
}
