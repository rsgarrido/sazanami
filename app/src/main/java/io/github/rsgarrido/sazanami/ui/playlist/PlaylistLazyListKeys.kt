package io.github.rsgarrido.sazanami.ui.playlist

internal const val CREATE_PLAYLIST_LAZY_LIST_KEY = "playlist-action:create"
internal const val EMPTY_PLAYLISTS_LAZY_LIST_KEY = "playlist-empty"
internal const val PLAYLIST_ROOT_FOLDER_LAZY_LIST_KEY = "playlist-folder:root"

internal fun playlistLazyListKey(playlistId: Long): String = "playlist:$playlistId"

internal fun playlistFolderLazyListKey(folderId: Long): String = "playlist-folder:$folderId"
