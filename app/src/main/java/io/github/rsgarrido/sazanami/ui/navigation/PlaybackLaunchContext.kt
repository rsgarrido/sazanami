package io.github.rsgarrido.sazanami.ui.navigation

import androidx.compose.runtime.saveable.listSaver
import io.github.rsgarrido.sazanami.ui.library.LibraryTab

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

    data class Search(
        val query: String
    ) : PlaybackLaunchContext
}

val playbackLaunchContextSaver = listSaver<PlaybackLaunchContext, String>(
    save = { context ->
        when (context) {
            PlaybackLaunchContext.Home -> listOf("home")
            is PlaybackLaunchContext.LibrarySection -> {
                listOf("library", context.tab.name)
            }

            is PlaybackLaunchContext.AlbumDetail -> {
                listOf("album", context.albumKey)
            }

            is PlaybackLaunchContext.ArtistDetail -> {
                listOf("artist", context.artistName)
            }

            is PlaybackLaunchContext.GenreDetail -> {
                listOf("genre", context.genreKey)
            }

            is PlaybackLaunchContext.PlaylistDetail -> {
                listOf("playlist", context.playlistId.toString())
            }

            is PlaybackLaunchContext.Search -> listOf("search", context.query)
        }
    },
    restore = { saved ->
        when (saved.firstOrNull()) {
            "home" -> PlaybackLaunchContext.Home
            "library" -> saved.getOrNull(1)
                ?.let { tabName -> runCatching { LibraryTab.valueOf(tabName) }.getOrNull() }
                ?.let { tab -> PlaybackLaunchContext.LibrarySection(tab) }

            "album" -> saved.getOrNull(1)
                ?.let { albumKey -> PlaybackLaunchContext.AlbumDetail(albumKey) }

            "artist" -> saved.getOrNull(1)
                ?.let { artistName -> PlaybackLaunchContext.ArtistDetail(artistName) }

            "genre" -> saved.getOrNull(1)
                ?.let { genreKey -> PlaybackLaunchContext.GenreDetail(genreKey) }

            "playlist" -> saved.getOrNull(1)
                ?.toLongOrNull()
                ?.let { playlistId -> PlaybackLaunchContext.PlaylistDetail(playlistId) }

            "search" -> saved.getOrNull(1)
                ?.let { query -> PlaybackLaunchContext.Search(query) }
            else -> null
        }
    }
)

fun capturePlaybackLaunchContext(
    mainDestination: MainDestination,
    selectedLibraryTab: LibraryTab,
    selectedAlbumKey: String?,
    selectedArtistName: String?,
    selectedGenreKey: String?,
    selectedPlaylistId: Long?,
    searchQuery: String
): PlaybackLaunchContext {
    when (mainDestination) {
        MainDestination.HOME -> return PlaybackLaunchContext.Home
        MainDestination.SEARCH -> return PlaybackLaunchContext.Search(searchQuery)
        MainDestination.LIBRARY -> Unit
    }

    return when {
        selectedAlbumKey != null -> {
            PlaybackLaunchContext.AlbumDetail(selectedAlbumKey)
        }

        selectedArtistName != null -> PlaybackLaunchContext.ArtistDetail(selectedArtistName)
        selectedGenreKey != null -> PlaybackLaunchContext.GenreDetail(selectedGenreKey)
        selectedPlaylistId != null -> PlaybackLaunchContext.PlaylistDetail(selectedPlaylistId)
        searchQuery.isNotBlank() -> PlaybackLaunchContext.Search(searchQuery)
        else -> PlaybackLaunchContext.LibrarySection(selectedLibraryTab)
    }
}

fun PlaybackLaunchContext.withValidDetails(
    albumKeys: Set<String>,
    artistNames: Set<String>,
    genreKeys: Set<String>,
    playlistIds: Set<Long>
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

        else -> this
    }
}
