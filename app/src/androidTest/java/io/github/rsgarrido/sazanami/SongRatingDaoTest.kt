package io.github.rsgarrido.sazanami

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.ListeningTrackIdentityEntity
import io.github.rsgarrido.sazanami.data.local.LocalTrackBindingEntity
import io.github.rsgarrido.sazanami.data.local.SongRatingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongRatingDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun insertUpdateSharedProjectionBackupOrderAndDelete() = runBlocking {
        val first = database.listeningTrackIdentityDao().insert(identity("first"))
        val second = database.listeningTrackIdentityDao().insert(identity("second"))
        val dao = database.songRatingDao()

        assertNull(dao.getByTrackIdentityId(first))
        dao.upsert(SongRatingEntity(first, 1, 10L, 10L))
        dao.upsert(SongRatingEntity(second, 5, 11L, 11L))
        assertEquals(1, dao.getByTrackIdentityId(first)?.rating)
        assertEquals(
            listOf(first, second),
            dao.observeAllWithBindings().first().map { it.trackIdentityId }
        )

        dao.upsert(SongRatingEntity(first, 4, 10L, 20L))
        assertEquals(SongRatingEntity(first, 4, 10L, 20L), dao.getByTrackIdentityId(first))
        assertEquals(listOf(first, second), dao.getAllForBackup().map { it.trackIdentityId })

        assertEquals(1, dao.deleteByTrackIdentityId(first))
        assertNull(dao.getByTrackIdentityId(first))
        assertEquals(1L, dao.count())
    }

    @Test
    fun foreignKeyCascadeAndBindingDeletionHaveRequiredOwnership() {
        runBlocking {
            val identityId = database.listeningTrackIdentityDao().insert(identity("owned"))
            val bindingId = database.localTrackBindingDao().insert(binding(identityId))
            database.songRatingDao().upsert(SongRatingEntity(identityId, 3, 1L, 1L))

            database.localTrackBindingDao().deleteById(bindingId)
            assertEquals(3, database.songRatingDao().getByTrackIdentityId(identityId)?.rating)

            database.listeningTrackIdentityDao().deleteAll()
            assertEquals(0L, database.songRatingDao().count())
            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking {
                    database.songRatingDao().upsert(SongRatingEntity(999L, 3, 1L, 1L))
                }
            }
        }
    }

    @Test
    fun duplicateLookingIdentitiesKeepDifferentRatings() = runBlocking {
        val first = database.listeningTrackIdentityDao().insert(identity("same"))
        val second = database.listeningTrackIdentityDao().insert(identity("same"))
        database.songRatingDao().upsert(SongRatingEntity(first, 1, 1L, 1L))
        database.songRatingDao().upsert(SongRatingEntity(second, 5, 2L, 2L))

        assertEquals(1, database.songRatingDao().getByTrackIdentityId(first)?.rating)
        assertEquals(5, database.songRatingDao().getByTrackIdentityId(second)?.rating)
    }

    private fun identity(label: String) = ListeningTrackIdentityEntity(
        titleSnapshot = label,
        artistSnapshot = "artist",
        albumSnapshot = "album",
        albumArtistSnapshot = null,
        durationMsSnapshot = 1L,
        normalizedTitle = label,
        normalizedArtist = "artist",
        normalizedAlbum = "album",
        metadataKey = "portable:$label",
        metadataKeyVersion = 1,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun binding(identityId: Long) = LocalTrackBindingEntity(
        trackIdentityId = identityId,
        referenceKey = "local:owned",
        mediaStoreId = 1L,
        volumeName = "external",
        contentUri = "content://media/1",
        relativePath = "Music/",
        displayName = "owned.flac",
        absolutePath = null,
        fileSizeBytes = 1L,
        dateModifiedEpochSeconds = 1L,
        durationMsSnapshot = 1L,
        legacyStableKey = null,
        portableKey = null,
        portableKeyVersion = null,
        firstSeenAt = 0L,
        lastSeenAt = 0L,
        missingSince = null
    )
}
