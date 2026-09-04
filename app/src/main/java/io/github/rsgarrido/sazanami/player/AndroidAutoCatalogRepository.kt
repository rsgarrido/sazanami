package io.github.rsgarrido.sazanami.player

import android.content.Context
import android.net.Uri
import androidx.room.InvalidationTracker
import io.github.rsgarrido.sazanami.data.ArtistPictureRepository
import io.github.rsgarrido.sazanami.data.FolderSelection
import io.github.rsgarrido.sazanami.data.EmbeddedArtworkResolver
import io.github.rsgarrido.sazanami.data.CURRENT_ARTWORK_ENRICHMENT_VERSION
import io.github.rsgarrido.sazanami.data.LibraryCacheRepository
import io.github.rsgarrido.sazanami.data.Playlist
import io.github.rsgarrido.sazanami.data.PlaylistArtworkMode
import io.github.rsgarrido.sazanami.data.PlaylistArtworkStore
import io.github.rsgarrido.sazanami.data.PlaylistType
import io.github.rsgarrido.sazanami.data.PlaylistsRepository
import io.github.rsgarrido.sazanami.data.SmartPlaylistRepository
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.SongReferenceIndex
import io.github.rsgarrido.sazanami.data.SongReferenceResolution
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import io.github.rsgarrido.sazanami.data.visual.AndroidAutoArtworkCache
import io.github.rsgarrido.sazanami.data.visual.PlaylistCollageStore
import io.github.rsgarrido.sazanami.data.visual.VisualAssetIdentity
import io.github.rsgarrido.sazanami.data.visual.VisualAssetOwnerType
import io.github.rsgarrido.sazanami.data.visual.VisualAssetProvider
import io.github.rsgarrido.sazanami.data.visual.VisualAssetVariant
import io.github.rsgarrido.sazanami.data.visual.playlistCollageSignature
import io.github.rsgarrido.sazanami.ui.library.buildLibraryArtistGroups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A service-owned catalog snapshot so Android Auto works even when the phone UI is cold. */
data class AndroidAutoCatalogSnapshot(
    val songs: List<Song>,
    val playlists: List<AutoPlaylistEntry>,
    val artistArtworkUris: Map<String, Uri>
) {
    private val browseTree by lazy {
        buildAndroidAutoBrowseTree(songs, "Sazanami", playlists, artistArtworkUris)
    }

    fun browseTree(rootTitle: String): AutoBrowseNode = browseTree.copy(title = rootTitle)

    companion object {
        val EMPTY = AndroidAutoCatalogSnapshot(emptyList(), emptyList(), emptyMap())
    }
}

class AndroidAutoCatalogRepository(
    context: Context,
    private val database: AppDatabase,
    private val preferencesRepository: AppPreferencesRepository,
    private val liveSongsProvider: () -> List<Song> = { PlaybackLibraryBridge.songs },
    private val cachedSongsLoader: suspend () -> List<Song> = {
        LibraryCacheRepository(database.cachedSongDao()).getAllCachedSongs()
    }
) {
    private val appContext = context.applicationContext ?: context
    private val playlistsRepository = PlaylistsRepository(database.playlistDao())
    private val artistPictureRepository = ArtistPictureRepository(database.artistPictureAssignmentDao())
    private val collageStore by lazy { PlaylistCollageStore(appContext) }
    private val androidAutoArtworkCache = AndroidAutoArtworkCache(appContext)
    private val mutex = Mutex()
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    private var cachedRevision = -1L
    private var cachedSelection: FolderSelection? = null
    private var cachedLiveSongs: List<Song>? = null
    private var cachedSnapshot: AndroidAutoCatalogSnapshot? = null
    private var cachedAtNanos = 0L
    private val observer = object : InvalidationTracker.Observer(
        "cached_songs", "playlists", "playlist_songs", "artist_picture_assignments",
        "smart_playlist_definitions", "song_ratings", "favorite_songs"
    ) {
        override fun onInvalidated(tables: Set<String>) = invalidate()
    }

    // Called from background initialization; opening Room must not delay service creation.
    fun startObserving() { database.invalidationTracker.addObserver(observer) }

    fun invalidate() { revision.update { it + 1 } }

    fun close() { database.invalidationTracker.removeObserver(observer) }

    /** Only visible/current items may trigger embedded extraction, never root/catalog loading. */
    suspend fun artworkUriFor(song: Song): Uri? = withContext(Dispatchers.IO) {
        try {
            AndroidAutoDiagnostics.measure("visibleArtwork") {
                val source = song.albumArtUri ?: if (
                    song.artworkEnrichmentVersion < CURRENT_ARTWORK_ENRICHMENT_VERSION
                ) EmbeddedArtworkResolver(appContext).resolve(song) else null
                androidAutoArtworkCache.externallyReadableUri(source)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AndroidAutoDiagnostics.log("visibleArtwork unavailable type=${error.javaClass.simpleName}")
            null
        }
    }

    suspend fun externallyReadableArtwork(uri: Uri?): Uri? = withContext(Dispatchers.IO) {
        androidAutoArtworkCache.externallyReadableUri(uri)
    }

    suspend fun loadSnapshot(): AndroidAutoCatalogSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val preferences = AndroidAutoDiagnostics.measure("preferences") {
                preferencesRepository.awaitLoadedState()
            }
            val folderSelection = FolderSelection.fromStored(
                storedMode = preferences.folderSelectionMode.name,
                storedFolders = preferences.selectedLibraryFolders
            )
            val liveSongs = liveSongsProvider()
            val generation = revision.value
            cachedSnapshot?.takeIf {
                cachedRevision == generation && cachedSelection == folderSelection &&
                    cachedLiveSongs === liveSongs && System.nanoTime() - cachedAtNanos < 30_000_000_000L
            }?.let { return@withLock it }

            val snapshot = AndroidAutoDiagnostics.measure("catalog") {
                val songs = (liveSongs.takeIf { it.isNotEmpty() }
                    ?: AndroidAutoDiagnostics.measure("roomSongs") {
                        cachedSongsLoader()
                    })
                    .filter { song -> folderSelection.includes(song.folderPath) }

                if (songs.isEmpty()) return@measure AndroidAutoCatalogSnapshot.EMPTY

                val smartPlaylistsRepository = SmartPlaylistRepository(
                    database = database,
                    eligibleFolderSelection = { folderSelection }
                )
                val referenceIndex by lazy { SongReferenceIndex.build(songs) }
                val playlists = AndroidAutoDiagnostics.measure("playlists") {
                    playlistsRepository.getPlaylists(songs).mapNotNull { playlist ->
                        val resolvedSongs = resolvePlaylistSongs(
                            playlist = playlist,
                            referenceIndex = { referenceIndex },
                            smartPlaylistsRepository = smartPlaylistsRepository
                        )
                        if (resolvedSongs.isEmpty()) return@mapNotNull null
                        AutoPlaylistEntry(
                            playlistId = playlist.playlistId,
                            name = playlist.name,
                            songs = resolvedSongs,
                            artworkUri = resolvePlaylistArtworkUri(playlist, resolvedSongs)
                        )
                    }
                }

                val externalArtworkBySource = mutableMapOf<String, Uri?>()
                fun externallyReadableArtwork(uri: Uri?): Uri? {
                    uri ?: return null
                    return externalArtworkBySource.getOrPut(uri.toString()) {
                        androidAutoArtworkCache.externallyReadableUri(uri)
                    }
                }
                fun songForAndroidAuto(song: Song): Song {
                    val artworkUri = externallyReadableArtwork(song.albumArtUri)
                    return if (artworkUri == song.albumArtUri) song else song.copy(albumArtUri = artworkUri)
                }

                val androidAutoSongs = AndroidAutoDiagnostics.measure("artworkUris") {
                    songs.map(::songForAndroidAuto)
                }
                val androidAutoPlaylists = playlists.map { playlist ->
                    playlist.copy(
                        songs = playlist.songs.map(::songForAndroidAuto),
                        artworkUri = externallyReadableArtwork(playlist.artworkUri)
                    )
                }

                val assignmentByArtistKey = artistPictureRepository.getAll()
                    .associateBy { assignment -> assignment.artistKey }
                val artistArtworkUris = buildLibraryArtistGroups(songs).mapNotNull { artist ->
                    val assignment = assignmentByArtistKey[artist.key] ?: return@mapNotNull null
                    artist.key to VisualAssetProvider.uriFor(
                        packageName = appContext.packageName,
                        identity = VisualAssetIdentity(
                            ownerType = VisualAssetOwnerType.ARTIST_IMAGE,
                            ownerKey = artist.key,
                            revision = assignment.assetReference
                        ),
                        variant = VisualAssetVariant.THUMBNAIL
                    )
                }.toMap()

                AndroidAutoCatalogSnapshot(
                    songs = androidAutoSongs,
                    playlists = androidAutoPlaylists,
                    artistArtworkUris = artistArtworkUris
                )
            }
            // If invalidated during a suspended read, the next reader retries this generation.
            cachedRevision = generation
            cachedSelection = folderSelection
            cachedLiveSongs = liveSongs
            cachedSnapshot = snapshot
            // Request-driven expiry also refreshes time-sensitive smart playlists. No timer
            // rebuilds the browse tree (or playback timeline) while the user is scrolling.
            cachedAtNanos = System.nanoTime()
            snapshot
        }
    }

    private suspend fun resolvePlaylistSongs(
        playlist: Playlist,
        referenceIndex: () -> SongReferenceIndex,
        smartPlaylistsRepository: SmartPlaylistRepository
    ): List<Song> = when (playlist.type) {
        PlaylistType.SMART -> try {
            smartPlaylistsRepository.resolveFinalMembership(playlist.playlistId).songs
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        PlaylistType.MANUAL -> {
            val index = referenceIndex()
            playlistsRepository.getPlaylistSongs(playlist.playlistId).mapNotNull { row ->
                (index.resolve(row.reference) as? SongReferenceResolution.Resolved)?.song
            }
        }
    }

    private suspend fun resolvePlaylistArtworkUri(
        playlist: Playlist,
        songs: List<Song>
    ): Uri? {
        if (
            playlist.artworkMode == PlaylistArtworkMode.CUSTOM &&
            !playlist.artworkReference.isNullOrBlank()
        ) {
            return VisualAssetProvider.uriFor(
                packageName = appContext.packageName,
                identity = PlaylistArtworkStore.identity(
                    playlistId = playlist.playlistId,
                    reference = playlist.artworkReference
                ) ?: return songs.firstArtworkUri(),
                variant = VisualAssetVariant.THUMBNAIL
            )
        }

        val artworkSongs = songs
            .asSequence()
            .filter { song -> song.albumArtUri != null }
            .distinctBy { song -> song.albumArtUri.toString() }
            .take(4)
            .toList()
        if (artworkSongs.isEmpty()) return null

        val signature = playlistCollageSignature(
            playlistId = playlist.playlistId,
            orderedArtworkIdentities = artworkSongs.map { song ->
                "${song.albumArtUri}:${song.artworkEnrichmentVersion}:${song.dateModifiedEpochSeconds}"
            }
        )
        // Browse must not decode four images per playlist. Use an existing collage or the
        // first cover; phone-side enrichment can publish a collage later.
        val collage = collageStore.expected(playlist.playlistId, signature)
            .takeIf { it.thumbnailFile.isFile }

        return collage?.identity?.let { identity ->
            VisualAssetProvider.uriFor(
                packageName = appContext.packageName,
                identity = identity,
                variant = VisualAssetVariant.THUMBNAIL
            )
        } ?: artworkSongs.firstArtworkUri()
    }
}

private fun List<Song>.firstArtworkUri(): Uri? =
    asSequence().mapNotNull(Song::albumArtUri).firstOrNull()
