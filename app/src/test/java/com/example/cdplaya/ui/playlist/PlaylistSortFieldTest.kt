package com.example.cdplaya.ui.playlist

import com.example.cdplaya.data.Playlist
import com.example.cdplaya.ui.library.LibrarySortDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistSortFieldTest {
    @Test
    fun nameSortSupportsBothDirections() {
        val alpha = playlist(1, "Alpha")
        val beta = playlist(2, "Beta")

        assertEquals(
            listOf(alpha, beta),
            PlaylistSortField.NAME.sort(
                listOf(beta, alpha),
                LibrarySortDirection.ASCENDING
            )
        )
        assertEquals(
            listOf(beta, alpha),
            PlaylistSortField.NAME.sort(
                listOf(alpha, beta),
                LibrarySortDirection.DESCENDING
            )
        )
    }

    @Test
    fun timestampSortKeepsUnknownValuesLastInBothDirections() {
        val unknown = playlist(1, "Unknown", modifiedAt = 0L)
        val older = playlist(2, "Older", modifiedAt = 100L)
        val newer = playlist(3, "Newer", modifiedAt = 200L)
        val playlists = listOf(unknown, newer, older)

        assertEquals(
            listOf(older, newer, unknown),
            PlaylistSortField.MODIFIED.sort(playlists, LibrarySortDirection.ASCENDING)
        )
        assertEquals(
            listOf(newer, older, unknown),
            PlaylistSortField.MODIFIED.sort(playlists, LibrarySortDirection.DESCENDING)
        )
    }

    private fun playlist(
        id: Long,
        name: String,
        modifiedAt: Long = id
    ) = Playlist(
        playlistId = id,
        name = name,
        songCount = 0,
        createdAt = modifiedAt,
        modifiedAt = modifiedAt
    )
}
