package io.github.rsgarrido.sazanami.ui.navigation

import androidx.compose.runtime.saveable.listSaver
import io.github.rsgarrido.sazanami.data.FolderBrowseIndex
import io.github.rsgarrido.sazanami.data.FolderId
import io.github.rsgarrido.sazanami.ui.library.LibraryTab
import io.github.rsgarrido.sazanami.ui.library.resolveFolderBrowseSelection

sealed interface PlaybackLaunchContext {
    data object Home : PlaybackLaunchContext

    data class LibrarySection(
        val tab: LibraryTab
    ) : PlaybackLaunchContext

    data class AlbumDetail(
        val albumKey: String
    ) : PlaybackLaunchContext

    data class ArtistDetail(
        val artistName: String
    ) : PlaybackLaunchContext

    data class GenreDetail(
        val genreKey: String
    ) : PlaybackLaunchContext

    data class PlaylistDetail(
        val playlistId: Long
    ) : PlaybackLaunchContext

    data class FolderDetail(
        val folderId: FolderId
    ) : PlaybackLaunchContext

    data class Search(
        val query: String
    ) : PlaybackLaunchContext
}

val playbackLaunchContextSaver = listSaver<PlaybackLaunchContext, String>(
    save = { context -> context.toSavedValues() },
    restore = { saved -> playbackLaunchContextFromSavedValues(saved) }
)

internal fun PlaybackLaunchContext.toSavedValues(): List<String> = when (this) {
    PlaybackLaunchContext.Home -> listOf("home")
    is PlaybackLaunchContext.LibrarySection -> listOf("library", tab.name)
    is PlaybackLaunchContext.AlbumDetail -> listOf("album", albumKey)
    is PlaybackLaunchContext.ArtistDetail -> listOf("artist", artistName)
    is PlaybackLaunchContext.GenreDetail -> listOf("genre", genreKey)
    is PlaybackLaunchContext.PlaylistDetail -> listOf("playlist", playlistId.toString())
    is PlaybackLaunchContext.FolderDetail -> {
        listOf("folder", folderId.volumeName, folderId.normalizedPath)
    }

    is PlaybackLaunchContext.Search -> listOf("search", query)
}

internal fun playbackLaunchContextFromSavedValues(saved: List<String>): PlaybackLaunchContext? =
    when (saved.firstOrNull()) {
        "home" -> PlaybackLaunchContext.Home
        "library" -> saved.getOrNull(1)
            ?.let { tabName -> runCatching { LibraryTab.valueOf(tabName) }.getOrNull() }
            ?.let { tab -> PlaybackLaunchContext.LibrarySection(tab) }

        "album" -> saved.getOrNull(1)?.let { PlaybackLaunchContext.AlbumDetail(it) }
        "artist" -> saved.getOrNull(1)?.let { PlaybackLaunchContext.ArtistDetail(it) }
        "genre" -> saved.getOrNull(1)?.let { PlaybackLaunchContext.GenreDetail(it) }
        "playlist" -> saved.getOrNull(1)
            ?.toLongOrNull()
            ?.let { PlaybackLaunchContext.PlaylistDetail(it) }

        "folder" -> if (saved.size == 3) {
            PlaybackLaunchContext.FolderDetail(
                FolderId(volumeName = saved[1], normalizedPath = saved[2])
            )
        } else {
            null
        }

        "search" -> saved.getOrNull(1)?.let { PlaybackLaunchContext.Search(it) }
        else -> null
    }

fun capturePlaybackLaunchContext(
    mainDestination: MainDestination,
    selectedLibraryTab: LibraryTab,
    selectedAlbumKey: String?,
    selectedArtistName: String?,
    selectedGenreKey: String?,
    selectedPlaylistId: Long?,
    searchQuery: String,
    selectedFolderId: FolderId? = null
): PlaybackLaunchContext {
    when (mainDestination) {
        MainDestination.HOME -> return PlaybackLaunchContext.Home
        MainDestination.SEARCH -> Unit // Search can own an open library detail.
        MainDestination.LIBRARY -> Unit
    }

    return when {
        selectedAlbumKey != null -> {
            PlaybackLaunchContext.AlbumDetail(selectedAlbumKey)
        }

        selectedArtistName != null -> PlaybackLaunchContext.ArtistDetail(selectedArtistName)
        selectedGenreKey != null -> PlaybackLaunchContext.GenreDetail(selectedGenreKey)
        selectedPlaylistId != null -> PlaybackLaunchContext.PlaylistDetail(selectedPlaylistId)
        mainDestination == MainDestination.LIBRARY &&
                selectedLibraryTab == LibraryTab.FOLDERS &&
                selectedFolderId != null -> PlaybackLaunchContext.FolderDetail(selectedFolderId)
        mainDestination == MainDestination.SEARCH || searchQuery.isNotBlank() ->
            PlaybackLaunchContext.Search(searchQuery)
        else -> PlaybackLaunchContext.LibrarySection(selectedLibraryTab)
    }
}

fun PlaybackLaunchContext.withValidDetails(
    albumKeys: Set<String>,
    artistNames: Set<String>,
    genreKeys: Set<String>,
    playlistIds: Set<Long>,
    folderBrowseIndex: FolderBrowseIndex = FolderBrowseIndex.Empty
): PlaybackLaunchContext {
    return when (this) {
        is PlaybackLaunchContext.AlbumDetail -> {
            if (albumKey in albumKeys) {
                this
            } else {
                PlaybackLaunchContext.LibrarySection(LibraryTab.ALBUMS)
            }
        }

        is PlaybackLaunchContext.ArtistDetail -> {
            if (artistName in artistNames) {
                this
            } else {
                PlaybackLaunchContext.LibrarySection(LibraryTab.ARTISTS)
            }
        }

        is PlaybackLaunchContext.GenreDetail -> {
            if (genreKey in genreKeys) {
                this
            } else {
                PlaybackLaunchContext.LibrarySection(LibraryTab.GENRES)
            }
        }

        is PlaybackLaunchContext.PlaylistDetail -> {
            if (playlistId in playlistIds) {
                this
            } else {
                PlaybackLaunchContext.LibrarySection(LibraryTab.PLAYLISTS)
            }
        }

        is PlaybackLaunchContext.FolderDetail -> {
            resolveFolderBrowseSelection(folderBrowseIndex, folderId)
                ?.let { PlaybackLaunchContext.FolderDetail(it) }
                ?: PlaybackLaunchContext.LibrarySection(LibraryTab.FOLDERS)
        }

        else -> this
    }
}
