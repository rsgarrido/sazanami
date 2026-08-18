package com.example.cdplaya

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.ListeningStatsRepository
import com.example.cdplaya.data.backup.BackupListeningHistoryV2
import com.example.cdplaya.data.backup.ListeningHistoryBackupRepository
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.LegacyListeningBaselineEntity
import com.example.cdplaya.data.local.ListeningEndReason
import com.example.cdplaya.data.local.ListeningEventEntity
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.ListeningEventPublicationState
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningTrackIdentityEntity
import com.example.cdplaya.data.local.LocalTrackBindingEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningHistoryBackupRoundTripTest {
    private lateinit var database: AppDatabase
    private lateinit var backupRepository: ListeningHistoryBackupRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        backupRepository = ListeningHistoryBackupRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun v7RoundTrip_preservesCanonicalSemanticsAfterDatabaseIdRemapping() = runBlocking {
        insertRichFixture()
        val source = backupRepository.export()
        val originalFirstIdentityId = source.identities.first().backupIdentityId

        backupRepository.restore(source)
        val restored = backupRepository.export()

        assertNotEquals(originalFirstIdentityId, restored.identities.first().backupIdentityId)
        assertEquals(source.semantic(), restored.semantic())
        assertEquals(listOf(1L, 2L, 3L, 4L), source.identities.map { it.backupIdentityId })
        assertEquals(
            source.bindings.sortedWith(compareBy({ it.trackIdentityBackupId }, { it.backupBindingId })),
            source.bindings
        )
        assertEquals(source.events.sortedWith(compareBy({ it.startedAt }, { it.eventUuid })), source.events)
        assertEquals(4L, source.summary.identityCount)
        assertEquals(3L, source.summary.bindingCount)
        assertEquals(2L, source.summary.baselineCount)
        assertEquals(6L, source.summary.eventCount)
        assertEquals(3L, source.summary.qualifiedEventCount)
        assertEquals(3L, source.summary.nonQualifiedEventCount)
        assertEquals(100L, source.summary.earliestDetailedEventAt)
        assertEquals(600L, source.summary.latestDetailedEventAt)
        assertNull(restored.events.first { it.eventUuid == "event-null-binding" }
            .localTrackBindingBackupId)
        assertEquals(2_000L, restored.events.first { it.eventUuid == "event-over-duration" }.listenedMs)
    }

    @Test
    fun restore_invalidatesReactiveProductionHistoryWithoutManualRefresh() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity("Before", 1))
        database.listeningEventDao().insert(event("before", identityId, null, 100, true))
        val statsRepository = ListeningStatsRepository(database)
        assertEquals("Before", statsRepository.observeProductionHistory().first().recentlyPlayed.single().track.title)

        val replacementDatabase = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        val replacementIdentity = replacementDatabase.listeningTrackIdentityDao()
            .insert(identity("After", 2))
        replacementDatabase.listeningEventDao().insert(
            event("after-1", replacementIdentity, null, 200, true)
        )
        replacementDatabase.listeningEventDao().insert(
            event("after-2", replacementIdentity, null, 300, true)
        )
        val replacement = ListeningHistoryBackupRepository(replacementDatabase).export()
        replacementDatabase.close()

        val refreshed = async {
            withTimeout(5_000) {
                statsRepository.observeProductionHistory().first { projection ->
                    projection.recentlyPlayed.singleOrNull()?.track?.title == "After" &&
                        projection.mostPlayed.singleOrNull()?.track?.playCounts?.totalPlayCount == 2L
                }
            }
        }
        backupRepository.restore(replacement)

        assertEquals("After", refreshed.await().mostPlayed.single().track.title)
    }

    @Test
    fun largeHistory_usesPagedExportAndBatchedExactRestore() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity("Large", 1))
        val events = List(10_000) { index ->
            event(
                uuid = "large-${index.toString().padStart(5, '0')}",
                identityId = identityId,
                bindingId = null,
                startedAt = index.toLong(),
                qualified = index % 2 == 0
            )
        }
        events.chunked(ListeningHistoryBackupRepository.RESTORE_BATCH_SIZE).forEach { batch ->
            database.listeningEventDao().insert(batch)
        }

        val exported = backupRepository.export()
        backupRepository.restore(exported)
        val restored = backupRepository.export()

        assertEquals(10_000L, restored.summary.eventCount)
        assertEquals(5_000L, restored.summary.qualifiedEventCount)
        assertEquals("large-00000", restored.events.first().eventUuid)
        assertEquals("large-09999", restored.events.last().eventUuid)
        assertEquals(10_000L, database.listeningEventDao().count())
        assertEquals(1_000, ListeningHistoryBackupRepository.EVENT_PAGE_SIZE)
        assertEquals(500, ListeningHistoryBackupRepository.RESTORE_BATCH_SIZE)
    }

    private suspend fun insertRichFixture() {
        val baselineOnly = database.listeningTrackIdentityDao().insert(identity("Same", 1))
        val detailedOnly = database.listeningTrackIdentityDao().insert(identity("Same", 2))
        val both = database.listeningTrackIdentityDao().insert(identity("Unicode — “Track” 🎵", 3))
        val missingOnly = database.listeningTrackIdentityDao().insert(identity("Missing", 4))
        val firstBinding = database.localTrackBindingDao().insert(binding(detailedOnly, "binding-a", false))
        val secondBinding = database.localTrackBindingDao().insert(binding(detailedOnly, "binding-b", true))
        database.localTrackBindingDao().insert(binding(missingOnly, "binding-c", true))
        database.legacyListeningBaselineDao().insert(
            LegacyListeningBaselineEntity(baselineOnly, 7, 10, 90, "baseline-a", 99)
        )
        database.legacyListeningBaselineDao().insert(
            LegacyListeningBaselineEntity(both, 2, 20, 80, "baseline-b", 99)
        )
        listOf(
            event("event-error", both, null, 600, false, ListeningSource.CDPLAYA, ListeningEndReason.ERROR),
            event("event-natural", detailedOnly, firstBinding, 100, true, ListeningSource.CDPLAYA, ListeningEndReason.NATURAL_END, "session-1", "source-1"),
            event("event-transition", detailedOnly, secondBinding, 200, false, ListeningSource.SPOTIFY_IMPORT, ListeningEndReason.TRANSITION, null, "source-1"),
            event("event-stopped", both, null, 300, true, ListeningSource.LASTFM_IMPORT, ListeningEndReason.STOPPED, "session-3"),
            event("event-null-binding", both, null, 400, false),
            event("event-over-duration", both, null, 500, true, listenedMs = 2_000, duration = 1_000)
        ).forEach { database.listeningEventDao().insert(it) }
    }

    private fun identity(title: String, seed: Long) = ListeningTrackIdentityEntity(
        titleSnapshot = title,
        artistSnapshot = "Artist",
        albumSnapshot = "Album",
        albumArtistSnapshot = if (seed % 2L == 0L) "Album Artist" else null,
        durationMsSnapshot = 1_000,
        normalizedTitle = title.lowercase(),
        normalizedArtist = "artist",
        normalizedAlbum = "album",
        metadataKey = "metadata-$seed",
        metadataKeyVersion = 1,
        createdAt = seed,
        updatedAt = seed + 1
    )

    private fun binding(identityId: Long, key: String, missing: Boolean) = LocalTrackBindingEntity(
        trackIdentityId = identityId,
        referenceKey = key,
        mediaStoreId = key.hashCode().toLong(),
        volumeName = "external",
        contentUri = "content://$key",
        relativePath = "Music/Album/",
        displayName = "$key.flac",
        absolutePath = "/private/$key.flac",
        fileSizeBytes = 42,
        dateModifiedEpochSeconds = 7,
        durationMsSnapshot = 1_000,
        legacyStableKey = "legacy-$key",
        portableKey = "portable-$key",
        portableKeyVersion = 1,
        firstSeenAt = 1,
        lastSeenAt = 2,
        missingSince = if (missing) 3 else null
    )

    private fun event(
        uuid: String,
        identityId: Long,
        bindingId: Long?,
        startedAt: Long,
        qualified: Boolean,
        source: ListeningSource = ListeningSource.CDPLAYA,
        endReason: ListeningEndReason = ListeningEndReason.STOPPED,
        sessionId: String? = null,
        sourceKey: String? = null,
        listenedMs: Long = 500,
        duration: Long? = 1_000
    ) = ListeningEventEntity(
        eventUuid = uuid,
        source = source,
        trackIdentityId = identityId,
        localTrackBindingId = bindingId,
        playbackSessionId = sessionId,
        startedAt = startedAt,
        endedAt = startedAt + 10,
        listenedMs = listenedMs,
        trackDurationMs = duration,
        qualifiedAsPlay = qualified,
        qualificationReason = if (qualified) {
            if (endReason == ListeningEndReason.NATURAL_END) ListeningQualificationReason.NATURAL_END
            else ListeningQualificationReason.TIME_THRESHOLD
        } else ListeningQualificationReason.NONE,
        qualificationRuleVersion = 1,
        qualificationPolicy = when (source) {
            ListeningSource.CDPLAYA -> ListeningQualificationPolicy.CDPLAYA
            ListeningSource.SPOTIFY_IMPORT -> ListeningQualificationPolicy.SPOTIFY
            ListeningSource.LASTFM_IMPORT -> ListeningQualificationPolicy.LASTFM
        },
        endReason = endReason,
        publicationState = if (source == ListeningSource.CDPLAYA) {
            ListeningEventPublicationState.NATIVE
        } else {
            ListeningEventPublicationState.IMPORT_PUBLISHED
        },
        sourceEventKey = sourceKey,
        importBatchId = if (source == ListeningSource.CDPLAYA) null else 44,
        createdAt = startedAt + 20
    )
}

private fun BackupListeningHistoryV2.semantic(): BackupListeningHistoryV2 {
    val identityIds = identities.mapIndexed { index, identity -> identity.backupIdentityId to index + 1L }.toMap()
    val bindingIds = bindings.mapIndexed { index, binding -> binding.backupBindingId to index + 1L }.toMap()
    return copy(
        identities = identities.map { it.copy(backupIdentityId = identityIds.getValue(it.backupIdentityId)) },
        bindings = bindings.map { binding ->
            binding.copy(
                backupBindingId = bindingIds.getValue(binding.backupBindingId),
                trackIdentityBackupId = identityIds.getValue(binding.trackIdentityBackupId)
            )
        },
        baselines = baselines.map { it.copy(trackIdentityBackupId = identityIds.getValue(it.trackIdentityBackupId)) },
        events = events.map { event ->
            event.copy(
                trackIdentityBackupId = identityIds.getValue(event.trackIdentityBackupId),
                localTrackBindingBackupId = event.localTrackBindingBackupId?.let(bindingIds::getValue)
            )
        }
    )
}
