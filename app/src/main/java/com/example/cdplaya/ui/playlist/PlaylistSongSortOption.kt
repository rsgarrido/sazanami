package com.example.cdplaya.ui.playlist

import com.example.cdplaya.data.PlaylistSong
import java.util.Locale

internal enum class PlaylistSongSortField(
    val label: String
) {
    CUSTOM("Custom"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album");

    fun sort(
        rows: List<PlaylistSong>,
        direction: PlaylistSongSortDirection
    ): List<PlaylistSong> {
        if (this == CUSTOM) return rows.sortedWith(manualOrderComparator)

        return rows.sortedWith { left, right ->
            val leftValue = left.sortValue().lowercase(Locale.ROOT)
            val rightValue = right.sortValue().lowercase(Locale.ROOT)
            val metadataComparison = leftValue.compareTo(rightValue)
            when {
                metadataComparison == 0 -> manualOrderComparator.compare(left, right)
                direction == PlaylistSongSortDirection.ASCENDING -> metadataComparison
                else -> -metadataComparison
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

internal enum class PlaylistSongSortDirection {
    ASCENDING,
    DESCENDING;

    fun toggled(): PlaylistSongSortDirection = when (this) {
        ASCENDING -> DESCENDING
        DESCENDING -> ASCENDING
    }
}
