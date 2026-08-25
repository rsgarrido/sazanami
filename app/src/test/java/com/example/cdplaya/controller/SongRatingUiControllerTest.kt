package com.example.cdplaya.controller

import android.net.Uri
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.SongRating
import com.example.cdplaya.data.SongRatingDataSource
import com.example.cdplaya.data.SongRatingSnapshot
import com.example.cdplaya.data.membershipKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class SongRatingUiControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val repository = FakeSongRatingDataSource()
    private val controller = SongRatingUiController(repository, scope)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun openingRatedAndUnratedSongsLoadsWithoutCreatingIdentity() {
        val rated = song(1, "Rated")
        val unrated = song(2, "Unrated")
        repository.ratings[rated.membershipKey()] = rating(41, 4)

        controller.open(rated)
        assertEquals(4, controller.state.value.dialog?.selectedValue)
        assertEquals(4, controller.state.value.dialog?.persistedRating?.value)

        controller.open(unrated)
        assertNull(controller.state.value.dialog?.selectedValue)
        assertFalse(controller.state.value.dialog?.isLoading ?: true)
        assertEquals(0, repository.setCalls)
        assertEquals(0, repository.clearCalls)
    }

    @Test
    fun selectionIsLocalCancelPersistsNothingAndOnlyOneDialogExists() {
        val first = song(1, "First")
        val second = song(2, "Second")
        controller.open(first)
        controller.selectRating(5)
        assertEquals(5, controller.state.value.dialog?.selectedValue)
        assertEquals(0, repository.setCalls)

        controller.open(second)
        assertEquals(second.membershipKey(), controller.state.value.dialog?.song?.membershipKey())
        controller.close()
        assertNull(controller.state.value.dialog)
        assertEquals(0, repository.setCalls)
    }

    @Test
    fun saveValidValuePersistsAndClosesWhileUnselectedSaveIsBlocked() {
        val song = song(1, "Song")
        controller.open(song)
        controller.save()
        assertEquals(0, repository.setCalls)
        assertTrue(controller.state.value.dialog != null)

        controller.selectRating(3)
        controller.save()
        assertEquals(1, repository.setCalls)
        assertEquals(3, repository.ratings[song.membershipKey()]?.value)
        assertNull(controller.state.value.dialog)
    }

    @Test
    fun sameValueSaveUsesRepositoryAndCloses() {
        val song = song(1, "Song")
        repository.ratings[song.membershipKey()] = rating(11, 4)
        controller.open(song)
        controller.save()
        assertEquals(1, repository.setCalls)
        assertNull(controller.state.value.dialog)
    }

    @Test
    fun clearDeletesAndUnknownSongCannotClearOrCreate() {
        val rated = song(1, "Rated")
        repository.ratings[rated.membershipKey()] = rating(12, 2)
        controller.open(rated)
        controller.clear()
        assertEquals(1, repository.clearCalls)
        assertFalse(repository.ratings.containsKey(rated.membershipKey()))
        assertNull(controller.state.value.dialog)

        controller.open(song(2, "Unknown"))
        controller.clear()
        assertEquals(1, repository.clearCalls)
        assertEquals(0, repository.identitiesCreatedByLoadOrClear)
    }

    @Test
    fun saveAndClearFailuresKeepDialogSelectionForRetry() {
        val song = song(1, "Song")
        controller.open(song)
        controller.selectRating(5)
        repository.failSet = true
        controller.save()
        assertEquals(SongRatingUiError.SAVE, controller.state.value.dialog?.error)
        assertEquals(5, controller.state.value.dialog?.selectedValue)
        assertFalse(controller.state.value.dialog?.isSaving ?: true)

        repository.failSet = false
        repository.ratings[song.membershipKey()] = rating(10, 5)
        controller.open(song)
        repository.failClear = true
        controller.clear()
        assertEquals(SongRatingUiError.CLEAR, controller.state.value.dialog?.error)
        assertEquals(5, controller.state.value.dialog?.selectedValue)
    }

    @Test
    fun staleLoadAndClosedLoadCannotOverwriteCurrentDialog() = runBlocking {
        val first = song(1, "First")
        val second = song(2, "Second")
        val pending = CompletableDeferred<SongRating?>()
        repository.pendingLoads[first.membershipKey()] = pending

        controller.open(first)
        controller.open(second)
        pending.complete(rating(1, 5))
        assertEquals(second.membershipKey(), controller.state.value.dialog?.song?.membershipKey())
        assertNull(controller.state.value.dialog?.selectedValue)

        repository.pendingLoads[first.membershipKey()] = CompletableDeferred()
        controller.open(first)
        controller.close()
        repository.pendingLoads[first.membershipKey()]?.complete(rating(1, 4))
        assertNull(controller.state.value.dialog)
    }

    @Test
    fun reactiveSnapshotUpdatesSharedMaps() {
        repository.snapshot.value = SongRatingSnapshot(
            byTrackIdentityId = mapOf(7L to rating(7, 5)),
            byReferenceKey = mapOf("exact" to rating(7, 5))
        )
        assertEquals(5, controller.state.value.ratingsByTrackIdentityId[7L])
        assertEquals(5, controller.state.value.ratingsByReferenceKey["exact"])
    }

    @Test
    fun directRatingModeSetsChangesAndClearsWithoutOpeningDialogs() {
        val song = song(8, "Quick rate")

        controller.setDirectRating(song, 2)
        assertEquals(2, repository.ratings[song.membershipKey()]?.value)
        controller.setDirectRating(song, 5)
        assertEquals(5, repository.ratings[song.membershipKey()]?.value)
        controller.setDirectRating(song, null)

        assertFalse(repository.ratings.containsKey(song.membershipKey()))
        assertNull(controller.state.value.dialog)
    }

    @Test
    fun observationFailureRetainsLastSuccessfulSharedMaps() {
        val failingRepository = object : SongRatingDataSource {
            override fun observeRatingSnapshot(): Flow<SongRatingSnapshot> = flow {
                emit(
                    SongRatingSnapshot(
                        byTrackIdentityId = mapOf(9L to rating(9, 4)),
                        byReferenceKey = mapOf("retained" to rating(9, 4))
                    )
                )
                error("observation failed")
            }

            override suspend fun getRatingForSong(song: Song): SongRating? = null
            override suspend fun setRating(song: Song, rating: Int): SongRating =
                error("unused")
            override suspend fun clearRating(song: Song): Boolean = error("unused")
        }

        val failingController = SongRatingUiController(failingRepository, scope)
        assertEquals(4, failingController.state.value.ratingsByTrackIdentityId[9L])
        assertEquals(4, failingController.state.value.ratingsByReferenceKey["retained"])
    }

    private fun song(id: Long, title: String) = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 1_000,
        uri = mock(Uri::class.java),
        filePath = "/music/$id.mp3",
        folderPath = "/music",
        albumArtUri = null
    )

    private fun rating(identityId: Long, value: Int) = SongRating(
        trackIdentityId = identityId,
        value = value,
        ratedAt = 1,
        updatedAt = 1
    )

    private class FakeSongRatingDataSource : SongRatingDataSource {
        val snapshot = MutableStateFlow(SongRatingSnapshot())
        val ratings = mutableMapOf<String, SongRating>()
        val pendingLoads = mutableMapOf<String, CompletableDeferred<SongRating?>>()
        var setCalls = 0
        var clearCalls = 0
        var identitiesCreatedByLoadOrClear = 0
        var failSet = false
        var failClear = false

        override fun observeRatingSnapshot(): Flow<SongRatingSnapshot> = snapshot

        override suspend fun getRatingForSong(song: Song): SongRating? =
            pendingLoads[song.membershipKey()]?.await() ?: ratings[song.membershipKey()]

        override suspend fun setRating(song: Song, rating: Int): SongRating {
            setCalls++
            if (failSet) error("save failed")
            return ratings.getOrPut(song.membershipKey()) {
                SongRating(setCalls.toLong(), rating, 1, 1)
            }.let { current ->
                current.copy(value = rating).also { ratings[song.membershipKey()] = it }
            }
        }

        override suspend fun clearRating(song: Song): Boolean {
            clearCalls++
            if (failClear) error("clear failed")
            return ratings.remove(song.membershipKey()) != null
        }
    }
}
