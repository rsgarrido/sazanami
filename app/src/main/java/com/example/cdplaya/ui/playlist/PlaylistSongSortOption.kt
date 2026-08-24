package com.example.cdplaya.ui.playlist

import com.example.cdplaya.data.PlaylistSong
import java.util.Locale

internal enum class PlaylistSongSortOption(
    val label: String
) {
    CUSTOM("Custom"),
    TITLE_ASCENDING("Title A\u2013Z"),
    TITLE_DESCENDING("Title Z\u2013A"),
    ARTIST_ASCENDING("Artist A\u2013Z"),
    ARTIST_DESCENDING("Artist Z\u2013A"),
    ALBUM_ASCENDING("Album A\u2013Z"),
    ALBUM_DESCENDING("Album Z\u2013A");

    fun sort(rows: List<PlaylistSong>): List<PlaylistSong> {
        if (this == CUSTOM) return rows.sortedBy(PlaylistSong::position)

        val ascending = when (this) {
            TITLE_ASCENDING, ARTIST_ASCENDING, ALBUM_ASCENDING -> true
            TITLE_DESCENDING, ARTIST_DESCENDING, ALBUM_DESCENDING -> false
            CUSTOM -> true
        }
        return rows.sortedWith { left, right ->
            val leftValue = left.sortValue().lowercase(Locale.ROOT)
            val rightValue = right.sortValue().lowercase(Locale.ROOT)
            val metadataComparison = leftValue.compareTo(rightValue)
            when {
                metadataComparison == 0 -> left.position.compareTo(right.position)
                ascending -> metadataComparison
                else -> -metadataComparison
            }
        }
    }

    private fun PlaylistSong.sortValue(): String = when (this@PlaylistSongSortOption) {
        TITLE_ASCENDING, TITLE_DESCENDING -> resolvedSong?.title ?: title
        ARTIST_ASCENDING, ARTIST_DESCENDING -> resolvedSong?.artist ?: artist
        ALBUM_ASCENDING, ALBUM_DESCENDING -> resolvedSong?.album ?: album
        CUSTOM -> position.toString()
    }
}
