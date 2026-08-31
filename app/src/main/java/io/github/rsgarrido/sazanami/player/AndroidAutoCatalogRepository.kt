package io.github.rsgarrido.sazanami.player

import android.content.Context
import android.net.Uri
import io.github.rsgarrido.sazanami.data.ArtistPictureRepository
import io.github.rsgarrido.sazanami.data.FolderSelection
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
import io.github.rsgarrido.sazanami.data.visual.PlaylistCollageStore
import io.github.rsgarrido.sazanami.data.visual.VisualAssetIdentity
import io.github.rsgarrido.sazanami.data.visual.VisualAssetOwnerType
import io.github.rsgarrido.sazanami.data.visual.VisualAssetProvider
import io.github.rsgarrido.sazanami.data.visual.VisualAssetVariant
import io.github.rsgarrido.sazanami.data.visual.playlistCollageSignature
import io.github.rsgarrido.sazanami.ui.library.buildLibraryArtistGroups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A service-owned catalog snapshot so Android Auto works even when the phone UI is cold. */
data class AndroidAutoCatalogSnapshot(
    val songs: List<Song>,
    val playlists: List<AutoPlaylistEntry>,
    val artistArtworkUris: Map<String, Uri>
) {
    companion object {
        val EMPTY = AndroidAutoCatalogSnapshot(emptyList(), emptyList(), emptyMap())
    }
}

class AndroidAutoCatalogRepository(
    context: Context,
    private val database: AppDatabase,
    private val preferencesRepository: AppPreferencesRepository,
    private val liveSongsProvider: () -> List<Song> = { PlaybackLibraryBridge.songs }
) {
    private val appContext = context.applicationContext ?: context
    private val libraryCacheRepository = LibraryCacheRepository(database.cachedSongDao())
    private val playlistsRepository = PlaylistsRepository(database.playlistDao())
    private val artistPictureRepository = ArtistPictureRepository(database.artistPictureAssignmentDao())
    private val collageStore = PlaylistCollageStore(appContext)

    suspend fun loadSnapshot(): AndroidAutoCatalogSnapshot = withContext(Dispatchers.IO) {
        val preferences = preferencesRepository.awaitLoadedState()
        val folderSelection = FolderSelection.fromStored(
            storedMode = preferences.folderSelectionMode.name,
            storedFolders = preferences.selectedLibraryFolders
        )
        val liveSongs = liveSongsProvider()
        val songs = (liveSongs.takeIf { it.isNotEmpty() }
            ?: libraryCacheRepository.getAllCachedSongs())
            .filter { song -> folderSelection.includes(song.folderPath) }

        if (songs.isEmpty()) return@withContext AndroidAutoCatalogSnapshot.EMPTY

        val smartPlaylistsRepository = SmartPlaylistRepository(
            database = database,
            eligibleFolderSelection = { folderSelection }
        )
        val playlists = playlistsRepository.getPlaylists(songs).mapNotNull { playlist ->
            val resolvedSongs = resolvePlaylistSongs(
                playlist = playlist,
                librarySongs = songs,
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
            songs = songs,
            playlists = playlists,
            artistArtworkUris = artistArtworkUris
        )
    }

    private suspend fun resolvePlaylistSongs(
        playlist: Playlist,
        librarySongs: List<Song>,
        smartPlaylistsRepository: SmartPlaylistRepository
    ): List<Song> = when (playlist.type) {
        PlaylistType.SMART -> runCatching {
            smartPlaylistsRepository.resolveFinalMembership(playlist.playlistId).songs
        }.getOrDefault(emptyList())
        PlaylistType.MANUAL -> {
            val index = SongReferenceIndex.build(librarySongs)
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
        val collage = runCatching {
            collageStore.ensure(
                playlistId = playlist.playlistId,
                signature = signature,
                orderedArtworkUris = artworkSongs.mapNotNull(Song::albumArtUri)
            )
        }.getOrNull()

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
