package io.github.rsgarrido.sazanami.ui.playlist

import io.github.rsgarrido.sazanami.data.PlaylistSong
import io.github.rsgarrido.sazanami.ui.library.LibrarySortDirection
import io.github.rsgarrido.sazanami.ui.library.compareLibraryText

internal enum class PlaylistSongSortField(
    val label: String
) {
    CUSTOM("Custom"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album");

    fun sort(
        rows: List<PlaylistSong>,
        direction: LibrarySortDirection
    ): List<PlaylistSong> {
        if (this == CUSTOM) return rows.sortedWith(manualOrderComparator)

        return rows.sortedWith { left, right ->
            val metadataComparison = compareLibraryText(
                left.sortValue(),
                right.sortValue(),
                direction
            )
            when {
                metadataComparison == 0 -> manualOrderComparator.compare(left, right)
                else -> metadataComparison
            }
        }
    }

    private fun PlaylistSong.sortValue(): String = when (this@PlaylistSongSortField) {
        TITLE -> resolvedSong?.title ?: title
        ARTIST -> resolvedSong?.artist ?: artist
        ALBUM -> resolvedSong?.album ?: album
        CUSTOM -> position.toString()
    }

    private companion object {
        val manualOrderComparator = compareBy<PlaylistSong>(
            PlaylistSong::position,
            PlaylistSong::playlistSongId
        )
    }
}
