package io.github.rsgarrido.sazanami

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.SongRatingRepository
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongRatingRepositoryTest {
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
    fun lifecycleTimestampsValidationClearAndReactiveReads() = runBlocking {
        val times = ArrayDeque(listOf(0L, 100L, 200L, 300L, 400L, 500L, 600L))
        val repository = SongRatingRepository(database, nowMillis = { times.removeFirst() })
        val song = song(1L, "one.flac")

        (1..5).forEach { value ->
            val result = repository.setRating(song, value)
            when (value) {
                1 -> {
                    assertEquals(100L, result.ratedAt)
                    assertEquals(100L, result.updatedAt)
                }
                2 -> {
                    assertEquals(100L, result.ratedAt)
                    assertEquals(200L, result.updatedAt)
                }
            }
        }
        val current = repository.setRating(song, 5)
        val same = repository.setRating(song, 5)
        assertEquals(current, same)
        assertEquals(current, repository.getRatingForSong(song))
        assertEquals(
            current,
            repository.observeRatingSnapshot().first().byTrackIdentityId.getValue(current.trackIdentityId)
        )

        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.setRating(song, 0) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.setRating(song, 6) } }
        assertTrue(repository.clearRating(song))
        assertNull(database.songRatingDao().getByTrackIdentityId(current.trackIdentityId))
        assertFalse(repository.clearRating(song))

        val rerated = repository.setRating(song, 3)
        assertEquals(600L, rerated.ratedAt)
        assertEquals(600L, rerated.updatedAt)
    }

    @Test
    fun newAndDuplicateLookingSongsUseOnlyExactKeysAndClearUnknownCreatesNothing() = runBlocking {
        var now = 1L
        val repository = SongRatingRepository(database, nowMillis = { now++ })
        val firstSong = song(1L, "same.flac")
        val secondSong = song(2L, "copy.flac")

        assertFalse(repository.clearRating(firstSong))
        assertEquals(0, database.listeningTrackIdentityDao().getAll().size)

        val first = repository.setRating(firstSong, 1)
        val firstAgain = repository.setRating(firstSong, 4)
        val second = repository.setRating(secondSong, 5)
        assertEquals(first.trackIdentityId, firstAgain.trackIdentityId)
        assertNotEquals(first.trackIdentityId, second.trackIdentityId)
        assertEquals(2, database.listeningTrackIdentityDao().getAll().size)
        assertEquals(1, database.localTrackBindingDao().getForTrackIdentity(first.trackIdentityId).size)

        val binding = database.localTrackBindingDao().getForTrackIdentity(first.trackIdentityId).single()
        database.localTrackBindingDao().deleteById(binding.id)
        assertEquals(4, database.songRatingDao().getByTrackIdentityId(first.trackIdentityId)?.rating)
    }

    @Test
    fun bulkSnapshotMapsExactBindingsAndRetainsMissingHistoricalIdentityRatings() = runBlocking {
        var now = 1L
        val repository = SongRatingRepository(database, nowMillis = { now++ })
        assertTrue(repository.observeRatingSnapshot().first().byReferenceKey.isEmpty())

        val firstSong = song(1L, "same.flac")
        val secondSong = song(2L, "copy.flac")
        val first = repository.setRating(firstSong, 2)
        val second = repository.setRating(secondSong, 5)
        val snapshot = repository.observeRatingSnapshot().first()

        assertEquals(2, snapshot.byReferenceKey.getValue(firstSong.membershipKey()).value)
        assertEquals(5, snapshot.byReferenceKey.getValue(secondSong.membershipKey()).value)
        assertEquals(2, snapshot.byTrackIdentityId.getValue(first.trackIdentityId).value)
        assertEquals(5, snapshot.byTrackIdentityId.getValue(second.trackIdentityId).value)

        val binding = database.localTrackBindingDao()
            .getForTrackIdentity(first.trackIdentityId).single()
        database.localTrackBindingDao().deleteById(binding.id)
        val missingSnapshot = repository.observeRatingSnapshot().first()
        assertFalse(missingSnapshot.byReferenceKey.containsKey(firstSong.membershipKey()))
        assertEquals(2, missingSnapshot.byTrackIdentityId.getValue(first.trackIdentityId).value)
    }

    @Test
    fun sharedSnapshotHandlesSeveralThousandCurrentSongsAndRatings() = runBlocking {
        var now = 1L
        val repository = SongRatingRepository(database, nowMillis = { now++ })
        val songs = (1L..3_000L).map { id -> song(id, "track-$id.flac") }

        songs.forEachIndexed { index, song ->
            repository.setRating(song, (index % 5) + 1)
        }

        val snapshot = repository.observeRatingSnapshot().first()
        assertEquals(3_000, snapshot.byReferenceKey.size)
        assertEquals(3_000, snapshot.byTrackIdentityId.size)
        songs.forEachIndexed { index, song ->
            assertEquals((index % 5) + 1, snapshot.byReferenceKey.getValue(song.membershipKey()).value)
        }
    }

    private fun song(id: Long, displayName: String) = Song(
        id = id,
        title = "Same title",
        artist = "Same artist",
        album = "Same album",
        trackNumber = 1,
        duration = 60_000L,
        uri = Uri.parse("content://media/$id"),
        filePath = "/music/$displayName",
        folderPath = "/music",
        albumArtUri = null,
        volumeName = "external",
        displayName = displayName,
        relativePath = "Music/",
        fileSizeBytes = 1_000L,
        dateModifiedEpochSeconds = 2_000L
    )
}
