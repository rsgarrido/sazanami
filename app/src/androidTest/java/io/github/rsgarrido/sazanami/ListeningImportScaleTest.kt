package io.github.rsgarrido.sazanami

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.ListeningStatsRepository
import io.github.rsgarrido.sazanami.data.backup.ListeningHistoryBackupRepository
import io.github.rsgarrido.sazanami.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningImportScaleTest {
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
    }
    @After fun tearDown() = database.close()

    @Test fun tenThousandImportedEvents_roundTripWithoutBindLimitOrDuplicateMappings() = runBlocking {
        database.withTransaction {
            val identityIds = database.listeningTrackIdentityDao().insert((0 until 5_000).map { index ->
                ListeningTrackIdentityEntity(titleSnapshot="Track $index", artistSnapshot="Artist",
                    albumSnapshot="Album", albumArtistSnapshot=null, durationMsSnapshot=180_000,
                    normalizedTitle="track $index", normalizedArtist="artist", normalizedAlbum="album",
                    metadataKey=null, metadataKeyVersion=1, createdAt=1, updatedAt=1)
            })
            val sourceId = database.listeningImportSourceDao().insert(ListeningImportSourceEntity(
                stableUuid="scale-profile", sourceType=ListeningSource.SPOTIFY_IMPORT,
                displayLabel="Scale profile", accountIdentityDigest=null, createdAt=1, updatedAt=1))
            val batchIds = database.listeningImportBatchDao().insert((0 until 100).map { index ->
                ListeningImportBatchEntity(stableUuid="scale-batch-$index", sourceProfileId=sourceId,
                    status=ListeningImportBatchStatus.PUBLISHED, parserVersion=1,
                    qualificationPolicy=ListeningQualificationPolicy.SPOTIFY, qualificationRuleVersion=1,
                    startedAt=index.toLong(), completedAt=index.toLong()+1, sourceRangeStart=null,
                    sourceRangeEnd=null, parsedCount=100, insertedCount=100,
                    qualifiedCount=50, createdAppVersion="scale-test")
            })
            database.listeningTrackExternalIdDao().insert((0 until 3_000).map { index ->
                ListeningTrackExternalIdEntity(trackIdentityId=identityIds[index],
                    sourceType=ListeningSource.SPOTIFY_IMPORT, externalId="external-$index",
                    createdAt=1, lastSeenAt=2)
            })
            val events = (0 until 10_000).map { index ->
                ListeningEventEntity(eventUuid="scale-event-$index", source=ListeningSource.SPOTIFY_IMPORT,
                    trackIdentityId=identityIds[index % identityIds.size], localTrackBindingId=null,
                    playbackSessionId=null, startedAt=null, endedAt=10_000L+index,
                    attributionAt=10_000L+index, timestampEvidence=ListeningTimestampEvidence.SOURCE_END_ONLY,
                    listenedMs=30_000, trackDurationMs=180_000, qualifiedAsPlay=index % 2 == 0,
                    qualificationReason=if(index % 2 == 0) ListeningQualificationReason.TIME_THRESHOLD else ListeningQualificationReason.NONE,
                    qualificationRuleVersion=1, qualificationPolicy=ListeningQualificationPolicy.SPOTIFY,
                    endReason=null, completionClassification=ListeningCompletionClassification.NONE,
                    publicationState=ListeningEventPublicationState.IMPORT_PUBLISHED,
                    sourceEventKey=null, importBatchId=null, createdAt=20_000L+index)
            }
            val eventIds = database.listeningEventDao().insert(events)
            database.importedListeningEventEvidenceDao().insert(eventIds.mapIndexed { index, eventId ->
                ImportedListeningEventEvidenceEntity(eventId, sourceId, 1, "group-${index / 5}", index % 5,
                    null, null, ImportedListeningSkippedState.FALSE,
                    if(index < 3_000) ImportedListeningMatchDisposition.EXACT else ImportedListeningMatchDisposition.UNMATCHED)
            })
            database.listeningImportBatchEventDao().insert(buildList {
                eventIds.forEachIndexed { index, eventId ->
                    add(ListeningImportBatchEventEntity(batchIds[index % 100], eventId))
                    if (index % 2 == 0) add(ListeningImportBatchEventEntity(batchIds[(index + 1) % 100], eventId))
                }
            })
        }

        val stats = ListeningStatsRepository(database).getAllTimeOverview(includeLegacyBaseline=false)
        assertEquals(10_000L, stats.detailedEventCount)
        assertEquals(5_000L, stats.qualifiedDetailedPlayCount)
        val repository = ListeningHistoryBackupRepository(database)
        val backup = repository.export()
        assertEquals(5_000, backup.identities.size)
        assertEquals(3_000, backup.externalTrackIds.size)
        assertEquals(100, backup.importBatches.size)
        assertEquals(10_000, backup.events.size)
        assertEquals(10_000, backup.importedEventEvidence.size)
        assertEquals(15_000, backup.batchEventObservations.size)

        repository.restore(backup)
        val restored = repository.export()
        assertEquals(backup.summary, restored.summary)
        assertEquals(3_000, restored.externalTrackIds.map { it.sourceType to it.externalId }.distinct().size)
        assertEquals(10_000, restored.importedEventEvidence.map {
            listOf(it.sourceProfileBackupId, it.fingerprintVersion, it.fingerprint, it.duplicateOrdinal)
        }.distinct().size)
        assertEquals(15_000, restored.batchEventObservations.size)
    }
}
