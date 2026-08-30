package io.github.rsgarrido.sazanami

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.ArtistPictureAssignmentEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtistPictureAssignmentPersistenceTest {
    @Test
    fun assignmentCanBeInsertedReplacedLookedUpAndRemoved() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.artistPictureAssignmentDao()
            val first = ArtistPictureAssignmentEntity(
                artistKey = "artist_abc",
                normalizedArtistName = "the warning",
                assetReference = "artist-abc-1.image",
                updatedAt = 1L
            )
            dao.upsert(first)
            assertEquals(first, dao.get(first.artistKey))

            val replacement = first.copy(
                assetReference = "artist-abc-2.image",
                updatedAt = 2L
            )
            dao.upsert(replacement)
            assertEquals(listOf(replacement), dao.getAll())

            dao.delete(first.artistKey)
            assertNull(dao.get(first.artistKey))
        } finally {
            database.close()
        }
    }
}
