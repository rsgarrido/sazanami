package io.github.rsgarrido.sazanami.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class FavoriteBatchPlanTest {
    @Test
    fun `mixed selection adds only missing favorites`() {
        val favorite = song(1)
        val missing = song(2)

        val plan = planFavoriteBatch(
            listOf(favorite, missing),
            setOf(favorite.membershipKey())
        )

        assertEquals(FavoriteBatchOperation.ADD_MISSING, plan.operation)
        assertEquals(listOf(missing.membershipKey()), plan.songs.map(Song::membershipKey))
    }

    @Test
    fun `all favorite selection removes every selected favorite`() {
        val first = song(1)
        val second = song(2)

        val plan = planFavoriteBatch(
            listOf(first, second),
            setOf(first.membershipKey(), second.membershipKey())
        )

        assertEquals(FavoriteBatchOperation.REMOVE_SELECTED, plan.operation)
        assertEquals(listOf(first, second), plan.songs)
    }

    @Test
    fun `duplicate inputs are idempotent`() {
        val selected = song(1)

        val add = planFavoriteBatch(listOf(selected, selected), emptySet())
        val remove = planFavoriteBatch(
            listOf(selected, selected),
            setOf(selected.membershipKey())
        )

        assertEquals(1, add.songs.size)
        assertEquals(1, remove.songs.size)
    }

    private fun song(id: Long): Song {
        val uri = mock(Uri::class.java)
        doReturn("content://media/external/audio/$id").`when`(uri).toString()
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            trackNumber = id.toInt(),
            duration = 1_000L,
            uri = uri,
            filePath = "/music/$id.flac",
            folderPath = "/music",
            albumArtUri = null,
            volumeName = "external_primary",
            relativePath = "Music/",
            displayName = "$id.flac",
            fileSizeBytes = 100L
        )
    }
}
