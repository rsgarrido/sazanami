package io.github.rsgarrido.sazanami.player

import android.content.Context
import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.ArtistPictureAssignmentDao
import io.github.rsgarrido.sazanami.data.local.PlaylistDao
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesState
import io.github.rsgarrido.sazanami.performance.PerformanceTracing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

class AndroidAutoCatalogRepositoryTest {
    private val context = mock(Context::class.java)
    private val database = mock(AppDatabase::class.java)
    private val playlists = mock(PlaylistDao::class.java)
    private val artists = mock(ArtistPictureAssignmentDao::class.java)
    private val preferences = mock(AppPreferencesRepository::class.java)
    private var live = emptyList<Song>()
    private var cached = emptyList<Song>()
    private var reads = 0

    @Before
    fun setup() = runBlocking {
        PerformanceTracing.bypassForTests = true
        `when`(context.applicationContext).thenReturn(context)
        `when`(database.playlistDao()).thenReturn(playlists)
        `when`(database.artistPictureAssignmentDao()).thenReturn(artists)
        `when`(playlists.getPlaylistsWithSongCount()).thenReturn(emptyList())
        `when`(playlists.getAllPlaylistSongEntities()).thenReturn(emptyList())
        `when`(artists.getAll()).thenReturn(emptyList())
        `when`(preferences.awaitLoadedState()).thenReturn(AppPreferencesState(isLoaded = true))
        Unit
    }

    @After
    fun cleanup() { PerformanceTracing.bypassForTests = false }

    private fun repository(loader: suspend () -> List<Song> = { reads++; cached }) =
        AndroidAutoCatalogRepository(context, database, preferences, { live }, loader)

    @Test
    fun `concurrent cold requests share one read and empty bridge cannot hide cached songs`() = runBlocking {
        cached = listOf(song(1))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = repository { reads++; entered.complete(Unit); release.await(); cached }
        val requests = List(8) { async { repository.loadSnapshot() } }
        entered.await()
        release.complete(Unit)
        val snapshots = requests.awaitAll()
        assertEquals(1, reads)
        snapshots.forEach { assertSame(snapshots.first(), it) }
        assertEquals(1, snapshots.first().browseTree("Sazanami").findNode(SONGS_ID)!!.children.size)
    }

    @Test
    fun `empty cache has valid root and can recover after invalidation`() = runBlocking {
        val repository = repository()
        assertTrue(repository.loadSnapshot().browseTree("Sazanami").isBrowsable)
        cached = listOf(song(2))
        repository.invalidate()
        assertEquals(listOf(2L), repository.loadSnapshot().songs.map(Song::id))
    }

    @Test
    fun `live publication refreshes snapshot and preserves cached cold voice results`() = runBlocking {
        cached = listOf(song(3))
        val repository = repository()
        val cold = repository.loadSnapshot()
        live = listOf(cached.single().copy(genres = listOf("Rock")))
        val warm = repository.loadSnapshot()
        assertNotSame(cold, warm)
        assertEquals(1, reads)
        assertEquals(listOf("Rock"), warm.songs.single().genres)
        val request = AndroidAutoSearchRequest(query = "Song 3")
        assertEquals(AndroidAutoSearchResolver.resolvePlayback(request, cold)!!.selectedSong.id,
            AndroidAutoSearchResolver.resolvePlayback(request, warm)!!.selectedSong.id)
    }

    @Test
    fun `changed folder preference cannot reuse catalog from previous selection`() = runBlocking {
        cached = listOf(song(1), song(2).copy(folderPath = "/other"))
        val repository = repository()
        assertEquals(2, repository.loadSnapshot().songs.size)
        `when`(preferences.awaitLoadedState()).thenReturn(AppPreferencesState(
            isLoaded = true, folderSelectionMode = FolderSelectionMode.CUSTOM,
            selectedLibraryFolders = setOf("/music")))
        assertEquals(listOf(1L), repository.loadSnapshot().songs.map(Song::id))
    }

    @Test
    fun `read failure is not cached and next request retries`() = runBlocking {
        val repository = repository {
            reads++
            if (reads == 1) throw java.io.IOException("Unavailable")
            listOf(song(1))
        }
        assertTrue(runCatching { repository.loadSnapshot() }.isFailure)
        assertEquals(1, repository.loadSnapshot().songs.size)
    }

    private fun song(id: Long) = Song(id = id, title = "Song $id", artist = "Artist", album = "Album",
        trackNumber = id.toInt(), duration = 10000, uri = mock(Uri::class.java),
        filePath = "/music/$id.flac", folderPath = "/music", albumArtUri = null)
}
