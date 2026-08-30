package io.github.rsgarrido.sazanami

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.ListeningStatsRepository
import io.github.rsgarrido.sazanami.data.SongRatingRepository
import io.github.rsgarrido.sazanami.data.backup.BackupSongRating
import io.github.rsgarrido.sazanami.data.backup.BackupSongRatings
import io.github.rsgarrido.sazanami.data.backup.ListeningHistoryBackupRepository
import io.github.rsgarrido.sazanami.data.backup.SongRatingBackupValidator
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.LegacyListeningBaselineEntity
import io.github.rsgarrido.sazanami.data.local.ListeningEndReason
import io.github.rsgarrido.sazanami.data.local.ListeningEventEntity
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationReason
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.data.local.ListeningTrackIdentityEntity
import io.github.rsgarrido.sazanami.data.local.LocalTrackBindingEntity
import io.github.rsgarrido.sazanami.data.local.SongRatingEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongRatingBackupRoundTripTest {
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun canonicalExportAndRestoreRemapBoundUnboundAndDuplicateLookingRatings() = runBlocking {
        val first = source.listeningTrackIdentityDao().insert(identity(createdAt = 10L))
        val second = source.listeningTrackIdentityDao().insert(identity(createdAt = 20L))
        val baselineOnly = source.listeningTrackIdentityDao().insert(
            identity(createdAt = 30L, title = "Unicode — 東京 🎵")
        )
        val eventOnly = source.listeningTrackIdentityDao().insert(identity(createdAt = 40L))
        val missingFile = source.listeningTrackIdentityDao().insert(identity(createdAt = 50L))
        source.listeningTrackIdentityDao().insert(identity(createdAt = 60L, title = "Unrated"))
        source.localTrackBindingDao().insert(binding(first, suffix = "current"))
        source.localTrackBindingDao().insert(binding(first, suffix = "historical", missingSince = 9L))
        source.localTrackBindingDao().insert(binding(missingFile, suffix = "missing", missingSince = 8L))
        source.legacyListeningBaselineDao().insert(
            LegacyListeningBaselineEntity(baselineOnly, 7, 1L, 2L, "baseline-only", 3L)
        )
        source.listeningEventDao().insert(event("event-only", eventOnly, 70L))
        source.songRatingDao().upsert(SongRatingEntity(first, 1, 100L, 100L))
        source.songRatingDao().upsert(SongRatingEntity(second, 5, 200L, 250L))
        source.songRatingDao().upsert(SongRatingEntity(baselineOnly, 2, 300L, 320L))
        source.songRatingDao().upsert(SongRatingEntity(eventOnly, 3, 400L, 430L))
        source.songRatingDao().upsert(SongRatingEntity(missingFile, 4, 500L, 540L))
        val exported = ListeningHistoryBackupRepository(source).exportWithRatings()
        assertEquals(listOf(first, second, baselineOnly, eventOnly, missingFile), exported.ratings.entries.map { it.trackIdentityBackupId })

        target.listeningTrackIdentityDao().insert(identity(createdAt = 999L))
        val targetRepository = ListeningHistoryBackupRepository(target)
        target.withTransaction {
            val idMap = targetRepository.restoreValidatedWithinTransaction(exported.history)
            targetRepository.restoreRatingsValidatedWithinTransaction(exported.ratings, idMap)
        }

        val restoredIdentities = target.listeningTrackIdentityDao().getAll().associateBy { it.createdAt }
        val restoredFirstId = restoredIdentities.getValue(10L).id
        val restoredSecondId = restoredIdentities.getValue(20L).id
        assertEquals(listOf(1, 5, 2, 3, 4), listOf(10L, 20L, 30L, 40L, 50L).map { createdAt ->
            target.songRatingDao().getByTrackIdentityId(restoredIdentities.getValue(createdAt).id)?.rating
        })
        assertEquals(100L, target.songRatingDao().getByTrackIdentityId(restoredFirstId)?.ratedAt)
        assertEquals(250L, target.songRatingDao().getByTrackIdentityId(restoredSecondId)?.updatedAt)
        assertEquals(2, target.localTrackBindingDao().getForTrackIdentity(restoredFirstId).size)
        assertEquals(0, target.localTrackBindingDao().getForTrackIdentity(restoredSecondId).size)
        assertEquals(5L, target.songRatingDao().count())
        assertEquals(1L, target.listeningEventDao().count())
        assertEquals(7, target.legacyListeningBaselineDao().getByTrackIdentityId(
            restoredIdentities.getValue(30L).id
        )?.historicalPlayCount)
        assertTrue(60L !in restoredIdentities)
        val topTracks = ListeningStatsRepository(target).getTopTracksByQualifiedPlays(10)
        val ratingsByIdentity = SongRatingRepository(target).observeRatingSnapshot()
            .first().byTrackIdentityId
        assertEquals(
            listOf(2, 3),
            topTracks.map { track -> ratingsByIdentity.getValue(track.trackIdentityId).value }
        )
    }

    @Test
    fun invalidRatingIsRejectedBeforeMutationAndLeavesCurrentStateUntouched() = runBlocking {
        val currentId = target.listeningTrackIdentityDao().insert(identity(createdAt = 77L))
        target.songRatingDao().upsert(SongRatingEntity(currentId, 4, 10L, 10L))
        val incomingIdentity = source.listeningTrackIdentityDao().insert(identity(createdAt = 88L))
        val exported = ListeningHistoryBackupRepository(source).exportWithRatings()
        val invalid = BackupSongRatings(
            entries = listOf(BackupSongRating(incomingIdentity, 0, 1L, 1L))
        )

        assertThrows(IllegalArgumentException::class.java) {
            SongRatingBackupValidator.validate(invalid, exported.history)
        }
        assertEquals(listOf(77L), target.listeningTrackIdentityDao().getAll().map { it.createdAt })
        assertEquals(4, target.songRatingDao().getByTrackIdentityId(currentId)?.rating)
        assertEquals(1L, target.songRatingDao().count())
    }

    @Test
    fun replacementRestoreRevertsRatingsAndEventsWithoutDuplicates() = runBlocking {
        val backedIdentity = source.listeningTrackIdentityDao().insert(identity(createdAt = 10L))
        source.songRatingDao().upsert(SongRatingEntity(backedIdentity, 2, 100L, 120L))
        source.listeningEventDao().insert(event("backed", backedIdentity, 10L))
        val backup = ListeningHistoryBackupRepository(source).exportWithRatings()

        source.songRatingDao().upsert(SongRatingEntity(backedIdentity, 5, 100L, 200L))
        source.listeningEventDao().insert(event("post-backup", backedIdentity, 20L))
        val postBackupIdentity = source.listeningTrackIdentityDao().insert(identity(createdAt = 30L))
        source.songRatingDao().upsert(SongRatingEntity(postBackupIdentity, 4, 300L, 300L))

        val repository = ListeningHistoryBackupRepository(source)
        source.withTransaction {
            val identityMap = repository.restoreValidatedWithinTransaction(backup.history)
            repository.restoreRatingsValidatedWithinTransaction(backup.ratings, identityMap)
        }

        val restored = repository.exportWithRatings()
        assertEquals(listOf("backed"), restored.history.events.map { it.eventUuid })
        assertEquals(1, restored.ratings.entries.size)
        assertEquals(2, restored.ratings.entries.single().rating)
        assertEquals(100L, restored.ratings.entries.single().ratedAt)
        assertEquals(120L, restored.ratings.entries.single().updatedAt)
        assertEquals(1L, source.songRatingDao().count())
    }

    @Test
    fun largeRatingHistoryUsesBoundedBatchesAndExactIdentityRemapping() = runBlocking {
        val identityIds = ArrayList<Long>(5_000)
        repeat(5_000) { index ->
            identityIds += source.listeningTrackIdentityDao().insert(
                identity(createdAt = index.toLong() + 1L, title = "Scale $index")
            )
        }
        val ratings = (0 until 3_000).map { index ->
            SongRatingEntity(
                trackIdentityId = identityIds[index],
                rating = index % 5 + 1,
                ratedAt = 10_000L + index,
                updatedAt = 20_000L + index
            )
        }
        ratings.chunked(ListeningHistoryBackupRepository.RESTORE_BATCH_SIZE)
            .forEach { source.songRatingDao().insert(it) }
        val events = (0 until 10_000).map { index ->
            event("rating-scale-$index", identityIds[index % identityIds.size], 30_000L + index)
        }
        events.chunked(ListeningHistoryBackupRepository.RESTORE_BATCH_SIZE)
            .forEach { source.listeningEventDao().insert(it) }

        val exported = ListeningHistoryBackupRepository(source).exportWithRatings()
        val targetRepository = ListeningHistoryBackupRepository(target)
        target.withTransaction {
            val identityMap = targetRepository.restoreValidatedWithinTransaction(exported.history)
            targetRepository.restoreRatingsValidatedWithinTransaction(exported.ratings, identityMap)
        }
        val restored = targetRepository.exportWithRatings()

        assertEquals(5_000L, restored.history.summary.identityCount)
        assertEquals(10_000L, restored.history.summary.eventCount)
        assertEquals(3_000, restored.ratings.entries.size)
        assertEquals(3_000L, target.songRatingDao().count())
        assertEquals(10_000L, target.listeningEventDao().count())
        val restoredIdentityCreatedAt = restored.history.identities.associate {
            it.backupIdentityId to it.createdAt
        }
        val semanticRatings = restored.ratings.entries.associate { rating ->
            restoredIdentityCreatedAt.getValue(rating.trackIdentityBackupId) to
                Triple(rating.rating, rating.ratedAt, rating.updatedAt)
        }
        assertEquals(3_000, semanticRatings.size)
        assertEquals(Triple(1, 10_000L, 20_000L), semanticRatings.getValue(1L))
        assertEquals(Triple(5, 12_999L, 22_999L), semanticRatings.getValue(3_000L))
        assertTrue(restored.ratings.entries.map { it.trackIdentityBackupId }.toSet().size == 3_000)
    }

    private fun identity(createdAt: Long, title: String = "同じ曲") = ListeningTrackIdentityEntity(
        titleSnapshot = title,
        artistSnapshot = "Artist",
        albumSnapshot = "Album",
        albumArtistSnapshot = null,
        durationMsSnapshot = 60_000L,
        normalizedTitle = title.lowercase(),
        normalizedArtist = "artist",
        normalizedAlbum = "album",
        metadataKey = "portable:same",
        metadataKeyVersion = 1,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun binding(
        identityId: Long,
        suffix: String = "current",
        missingSince: Long? = null
    ) = LocalTrackBindingEntity(
        trackIdentityId = identityId,
        referenceKey = "local:$identityId:$suffix",
        mediaStoreId = identityId,
        volumeName = "external",
        contentUri = "content://media/$identityId",
        relativePath = "Music/",
        displayName = "同じ曲-$identityId-$suffix.flac",
        absolutePath = null,
        fileSizeBytes = 1L,
        dateModifiedEpochSeconds = 1L,
        durationMsSnapshot = 60_000L,
        legacyStableKey = null,
        portableKey = "portable:same",
        portableKeyVersion = 1,
        firstSeenAt = 1L,
        lastSeenAt = 1L,
        missingSince = missingSince
    )

    private fun event(uuid: String, identityId: Long, startedAt: Long) = ListeningEventEntity(
        eventUuid = uuid,
        source = ListeningSource.CDPLAYA,
        trackIdentityId = identityId,
        localTrackBindingId = null,
        playbackSessionId = null,
        startedAt = startedAt,
        endedAt = startedAt + 10L,
        listenedMs = 10L,
        trackDurationMs = 60_000L,
        qualifiedAsPlay = true,
        qualificationReason = ListeningQualificationReason.TIME_THRESHOLD,
        qualificationRuleVersion = 1,
        endReason = ListeningEndReason.STOPPED,
        sourceEventKey = null,
        importBatchId = null,
        createdAt = startedAt + 10L
    )
}
