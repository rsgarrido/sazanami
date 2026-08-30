package io.github.rsgarrido.sazanami

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.ListeningNativeTrackResolver
import io.github.rsgarrido.sazanami.data.SongReference
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningNativeTrackResolverTest {
    private lateinit var database: AppDatabase
    private lateinit var resolver: ListeningNativeTrackResolver

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        resolver = ListeningNativeTrackResolver(database) { 123L }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exactReferenceReusesBindingAndIdentity() = runBlocking {
        val first = resolver.resolveOrCreate("local:first", reference(mediaStoreId = 1L))
        val second = resolver.resolveOrCreate("local:first", reference(mediaStoreId = 1L))

        assertEquals(first, second)
        assertEquals(1, database.listeningTrackIdentityDao().getAll().size)
        assertEquals(1, database.localTrackBindingDao().getForTrackIdentity(first.trackIdentityId).size)
    }

    @Test
    fun duplicateLookingLocalTracksNeverMergeByMetadata() = runBlocking {
        val first = resolver.resolveOrCreate("local:first", reference(mediaStoreId = 1L))
        val second = resolver.resolveOrCreate("local:second", reference(mediaStoreId = 2L))

        assertNotEquals(first.trackIdentityId, second.trackIdentityId)
        assertNotEquals(first.localTrackBindingId, second.localTrackBindingId)
        assertEquals(2, database.listeningTrackIdentityDao().getAll().size)
    }

    @Test
    fun exactCurrentReferenceReactivatesAndRefreshesItsBindingWithoutChangingIdentity() =
        runBlocking {
            val first = resolver.resolveOrCreate("local:first", reference(mediaStoreId = 1L))
            database.openHelper.writableDatabase.execSQL(
                "UPDATE local_track_bindings SET missingSince = 99, displayName = 'stale.flac' " +
                    "WHERE id = ?",
                arrayOf(first.localTrackBindingId)
            )

            val second = resolver.resolveOrCreate(
                "local:first",
                reference(mediaStoreId = 1L).copy(displayName = "current.flac"),
                refreshExistingBinding = true
            )

            assertEquals(first, second)
            val binding = database.localTrackBindingDao().getById(first.localTrackBindingId!!)!!
            assertEquals("current.flac", binding.displayName)
            assertEquals(123L, binding.lastSeenAt)
            assertNull(binding.missingSince)
            assertEquals(1, database.listeningTrackIdentityDao().getAll().size)
        }

    private fun reference(mediaStoreId: Long) = SongReference(
        mediaStoreId = mediaStoreId,
        volumeName = "external",
        contentUri = "content://media/$mediaStoreId",
        relativePath = "Music/",
        displayName = "$mediaStoreId.flac",
        fileSizeBytes = 1_000L,
        dateModifiedEpochSeconds = 2_000L,
        duration = 60_000L,
        title = "Same title",
        artist = "Same artist",
        album = "Same album",
        legacyStableKey = "same-legacy-key",
        portableKey = "portable:v1:same",
        portableKeyVersion = 1
    )
}
