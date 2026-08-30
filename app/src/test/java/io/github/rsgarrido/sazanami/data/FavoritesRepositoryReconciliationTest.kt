package io.github.rsgarrido.sazanami.data

import android.net.Uri
import io.github.rsgarrido.sazanami.data.local.FavoriteSongDao
import io.github.rsgarrido.sazanami.data.local.FavoriteSongEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class FavoritesRepositoryReconciliationTest {
    @Test
    fun uniqueLegacyFavoriteBackfillsAndReconciliationIsIdempotent() = runBlocking {
        val song = song(7)
        val dao = FakeFavoriteDao(mutableListOf(legacyFavorite(song)))
        val repository = FavoritesRepository(dao)

        val first = repository.reconcileSongReferences(listOf(song))
        val second = repository.reconcileSongReferences(listOf(song))

        assertEquals(setOf(song.membershipKey()), first.resolvedMembershipKeys)
        assertEquals(1, first.backfilledCount)
        assertEquals(0, second.backfilledCount)
        assertEquals(song.id, dao.rows.single().mediaStoreId)
        assertEquals(song.toSongReference().portableKey, dao.rows.single().portableKey)
    }

    @Test
    fun duplicateLegacyFavoriteRemainsAmbiguousAndIsNotRewritten() = runBlocking {
        val first = song(1)
        val second = song(2)
        val original = legacyFavorite(first)
        val dao = FakeFavoriteDao(mutableListOf(original))

        val result = FavoritesRepository(dao).reconcileSongReferences(listOf(first, second))

        assertEquals(1, result.ambiguousCount)
        assertTrue(result.resolvedMembershipKeys.isEmpty())
        assertEquals(original, dao.rows.single())
    }

    private fun legacyFavorite(song: Song) = FavoriteSongEntity(
        referenceKey = "legacy:${song.stableKey()}",
        songKey = song.stableKey(),
        title = song.title,
        artist = song.artist,
        album = song.album,
        duration = song.duration,
        createdAt = 123L,
        mediaStoreId = null,
        volumeName = "",
        contentUri = "",
        relativePath = "",
        displayName = "",
        fileSizeBytes = 0L,
        dateModifiedEpochSeconds = 0L,
        albumArtist = "",
        portableKey = "",
        portableKeyVersion = SongIdentity.PORTABLE_KEY_VERSION
    )

    private fun song(id: Long): Song {
        val uri = mock(Uri::class.java)
        doReturn("content://media/external/audio/$id").`when`(uri).toString()
        return Song(
            id = id,
            title = "Title",
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            duration = 180_000L,
            uri = uri,
            filePath = "/music/$id.flac",
            folderPath = "/music",
            albumArtUri = null,
            volumeName = "external_primary",
            relativePath = "Music/",
            displayName = "$id.flac",
            fileSizeBytes = 1_000L
        )
    }
}

private class FakeFavoriteDao(
    val rows: MutableList<FavoriteSongEntity>
) : FavoriteSongDao {
    override suspend fun getAllFavorites(): List<FavoriteSongEntity> = rows.sortedByDescending {
        it.createdAt
    }

    override suspend fun insertFavorite(favoriteSong: FavoriteSongEntity) {
        rows.removeAll { it.referenceKey == favoriteSong.referenceKey }
        rows += favoriteSong
    }

    override suspend fun insertFavorites(favorites: List<FavoriteSongEntity>) {
        favorites.forEach { insertFavorite(it) }
    }

    override suspend fun deleteFavoritesByReferenceKeys(referenceKeys: List<String>) {
        rows.removeAll { it.referenceKey in referenceKeys }
    }

    override suspend fun countFavoriteByReferenceKey(referenceKey: String): Int =
        rows.count { it.referenceKey == referenceKey }

    override suspend fun deleteFavoriteByReferenceKey(referenceKey: String) {
        rows.removeAll { it.referenceKey == referenceKey }
    }

    override suspend fun deleteFavoriteByKey(songKey: String) {
        rows.removeAll { it.songKey == songKey }
    }

    override suspend fun deleteAllFavorites() {
        rows.clear()
    }
}
