package com.example.cdplaya

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.ListeningIdentityReconciliationLinkResult
import com.example.cdplaya.data.ListeningIdentityReconciliationRepository
import com.example.cdplaya.data.backup.AppBackup
import com.example.cdplaya.data.backup.AppBackupJson
import com.example.cdplaya.data.backup.ListeningHistoryBackupRepository
import com.example.cdplaya.data.backup.ListeningHistoryBackupValidator
import com.example.cdplaya.data.backup.SongRatingBackupValidator
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.ImportedListeningEventEvidenceEntity
import com.example.cdplaya.data.local.ImportedListeningMatchDisposition
import com.example.cdplaya.data.local.ImportedListeningSkippedState
import com.example.cdplaya.data.local.ListeningCompletionClassification
import com.example.cdplaya.data.local.ListeningEventEntity
import com.example.cdplaya.data.local.ListeningEventPublicationState
import com.example.cdplaya.data.local.ListeningImportSourceEntity
import com.example.cdplaya.data.local.ListeningQualificationPolicy
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningTimestampEvidence
import com.example.cdplaya.data.local.ListeningTrackExternalIdEntity
import com.example.cdplaya.data.local.ListeningTrackIdentityEntity
import com.example.cdplaya.data.local.LocalTrackBindingEntity
import com.example.cdplaya.data.local.SongRatingEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningIdentityReconciliationBackupRoundTripTest {
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
    fun backup10RoundTrip_preservesManyToOneLinksRatingsAndProviderEvidenceAfterIdRemapping() = runBlocking {
        val target = identity("Local target")
        val sourceA = identity("Historical A")
        val sourceB = identity("Historical B")
        val binding = database.localTrackBindingDao().insert(localBinding(target, missingSince = 99L))
        nativeEvent(target, binding)
        val profile = database.listeningImportSourceDao().insert(
            ListeningImportSourceEntity(
                stableUuid = "fictional-profile",
                sourceType = ListeningSource.SPOTIFY_IMPORT,
                displayLabel = "Fictional account",
                accountIdentityDigest = "fictional-digest",
                createdAt = 1L,
                updatedAt = 2L
            )
        )
        val eventA = importedEvent(sourceA, "import-a")
        val eventB = importedEvent(sourceB, "import-b")
        evidence(eventA, profile, "fingerprint-a", 0)
        evidence(eventB, profile, "fingerprint-b", 4)
        database.listeningTrackExternalIdDao().insert(
            listOf(
                ListeningTrackExternalIdEntity(0L, sourceA, ListeningSource.SPOTIFY_IMPORT, "spotify:a", 10L, 11L),
                ListeningTrackExternalIdEntity(0L, sourceB, ListeningSource.SPOTIFY_IMPORT, "spotify:b", 12L, 13L)
            )
        )
        database.songRatingDao().insert(
            listOf(
                SongRatingEntity(sourceA, 3, 20L, 21L),
                SongRatingEntity(sourceB, 4, 22L, 23L),
                SongRatingEntity(target, 5, 24L, 25L)
            )
        )
        val reconciliation = ListeningIdentityReconciliationRepository(database)
        assertTrue(reconciliation.link(sourceA, target, 100L) is ListeningIdentityReconciliationLinkResult.Linked)
        assertTrue(reconciliation.link(sourceB, target, 101L) is ListeningIdentityReconciliationLinkResult.Linked)

        val backupRepository = ListeningHistoryBackupRepository(database)
        val backup = backupRepository.exportWithRatings()
        val originalTargetBackupId = backup.history.identities.single { it.titleSnapshot == "Local target" }.backupIdentityId
        assertEquals(listOf(100L, 101L), backup.history.reconciliations.map { it.reconciledAt })

        val history = ListeningHistoryBackupValidator.validate(backup.history)
        val ratings = SongRatingBackupValidator.validate(backup.ratings, history)
        database.withTransaction {
            val remapped = backupRepository.restoreValidatedWithinTransaction(history)
            backupRepository.restoreRatingsValidatedWithinTransaction(ratings, remapped)
        }

        val restored = backupRepository.exportWithRatings()
        val restoredTarget = restored.history.identities.single { it.titleSnapshot == "Local target" }.backupIdentityId
        val restoredSources = restored.history.identities
            .filter { it.titleSnapshot.startsWith("Historical") }
            .associateBy { it.titleSnapshot }
        assertNotEquals(originalTargetBackupId, restoredTarget)
        assertEquals(2, restored.history.reconciliations.size)
        assertTrue(restored.history.reconciliations.all { it.targetIdentityBackupId == restoredTarget })
        assertEquals(setOf(100L, 101L), restored.history.reconciliations.map { it.reconciledAt }.toSet())
        assertEquals(3, restored.ratings.entries.size)
        assertEquals(3, restored.ratings.entries.single {
            it.trackIdentityBackupId == restoredSources.getValue("Historical A").backupIdentityId
        }.rating)
        assertEquals("Historical A", restored.history.identities.single {
            it.backupIdentityId == restored.history.externalTrackIds.single { external ->
                external.externalId == "spotify:a"
            }.trackIdentityBackupId
        }.titleSnapshot)
        assertEquals(setOf("fingerprint-a" to 0, "fingerprint-b" to 4), restored.history.importedEventEvidence.map {
            it.fingerprint to it.duplicateOrdinal
        }.toSet())
        assertEquals(3L, restored.history.summary.identityCount)
        assertEquals(3L, restored.history.summary.eventCount)
    }

    @Test
    fun invalidReconciliationRestore_isRejectedBeforeAtomicReplacement() = runBlocking {
        val target = identity("Current target")
        val source = identity("Current source")
        database.localTrackBindingDao().insert(localBinding(target, null))
        importedEvent(source, "current-import")
        val repository = ListeningIdentityReconciliationRepository(database)
        assertTrue(repository.link(source, target, 44L) is ListeningIdentityReconciliationLinkResult.Linked)
        val backupRepository = ListeningHistoryBackupRepository(database)
        val valid = backupRepository.export()
        val invalid = valid.copy(
            reconciliations = valid.reconciliations.map { it.copy(targetIdentityBackupId = 999L) }
        )

        assertTrue(runCatching { backupRepository.restore(invalid) }.isFailure)

        assertEquals(2L, database.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM listening_track_identities"
        ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) })
        assertEquals(target, repository.findTargetForSource(source)?.targetIdentityId)
        assertEquals(1L, database.listeningEventDao().count())
    }

    @Test
    fun backup9RestoresWithNoInferredLinksAndCanBeReconciledNormallyAfterward() = runBlocking {
        val target = identity("Backup 9 target")
        val source = identity("Backup 9 source")
        database.localTrackBindingDao().insert(localBinding(target, 77L))
        importedEvent(source, "backup-9-import")
        val backupRepository = ListeningHistoryBackupRepository(database)
        val history9 = backupRepository.export().copy(formatVersion = 1, reconciliations = emptyList())
        val migrated = AppBackupJson.decodeBackup(
            AppBackupJson.encodeBackup(
                AppBackup(
                    schemaVersion = 9,
                    createdAt = 1_000L,
                    canonicalListeningHistory = history9
                )
            )
        )

        val migratedHistory = requireNotNull(migrated.canonicalListeningHistory)
        assertEquals(10, migrated.schemaVersion)
        assertEquals(2, migratedHistory.formatVersion)
        assertTrue(migratedHistory.reconciliations.isEmpty())
        backupRepository.restore(migratedHistory)
        val restoredIdentities = database.listeningTrackIdentityDao().getAll().associateBy { it.titleSnapshot }
        val restoredSource = restoredIdentities.getValue("Backup 9 source").id
        val restoredTarget = restoredIdentities.getValue("Backup 9 target").id

        assertTrue(ListeningIdentityReconciliationRepository(database).link(
            restoredSource,
            restoredTarget,
            1_001L
        ) is ListeningIdentityReconciliationLinkResult.Linked)
    }

    private suspend fun identity(title: String) = database.listeningTrackIdentityDao().insert(
        ListeningTrackIdentityEntity(
            titleSnapshot = title,
            artistSnapshot = "Fictional Artist",
            albumSnapshot = "Fictional Album",
            albumArtistSnapshot = null,
            durationMsSnapshot = 180_000L,
            normalizedTitle = title.lowercase(),
            normalizedArtist = "fictional artist",
            normalizedAlbum = "fictional album",
            metadataKey = null,
            metadataKeyVersion = 1,
            createdAt = 1L,
            updatedAt = 2L
        )
    )

    private fun localBinding(identityId: Long, missingSince: Long?) = LocalTrackBindingEntity(
        trackIdentityId = identityId,
        referenceKey = "local-$identityId",
        mediaStoreId = identityId,
        volumeName = "external",
        contentUri = "content://fictional/$identityId",
        relativePath = "Music/Fictional/",
        displayName = "target.flac",
        absolutePath = null,
        fileSizeBytes = 1_000L,
        dateModifiedEpochSeconds = 1L,
        durationMsSnapshot = 180_000L,
        legacyStableKey = null,
        portableKey = "portable-$identityId",
        portableKeyVersion = 1,
        firstSeenAt = 1L,
        lastSeenAt = 2L,
        missingSince = missingSince
    )

    private suspend fun nativeEvent(identityId: Long, bindingId: Long) {
        database.listeningEventDao().insert(
            ListeningEventEntity(
                eventUuid = "native",
                source = ListeningSource.CDPLAYA,
                trackIdentityId = identityId,
                localTrackBindingId = bindingId,
                playbackSessionId = "native-session",
                startedAt = 10L,
                endedAt = 20L,
                listenedMs = 10L,
                trackDurationMs = 180_000L,
                qualifiedAsPlay = false,
                qualificationReason = ListeningQualificationReason.NONE,
                qualificationRuleVersion = 1,
                qualificationPolicy = ListeningQualificationPolicy.CDPLAYA,
                endReason = null,
                publicationState = ListeningEventPublicationState.NATIVE,
                sourceEventKey = null,
                importBatchId = null,
                createdAt = 21L
            )
        )
    }

    private suspend fun importedEvent(identityId: Long, uuid: String): Long =
        database.listeningEventDao().insert(
            ListeningEventEntity(
                eventUuid = uuid,
                source = ListeningSource.SPOTIFY_IMPORT,
                trackIdentityId = identityId,
                localTrackBindingId = null,
                playbackSessionId = null,
                startedAt = null,
                endedAt = 50L + identityId,
                attributionAt = 50L + identityId,
                timestampEvidence = ListeningTimestampEvidence.SOURCE_END_ONLY,
                listenedMs = 30_000L,
                trackDurationMs = null,
                qualifiedAsPlay = true,
                qualificationReason = ListeningQualificationReason.TIME_THRESHOLD,
                qualificationRuleVersion = 1,
                qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
                endReason = null,
                completionClassification = ListeningCompletionClassification.NONE,
                publicationState = ListeningEventPublicationState.IMPORT_PUBLISHED,
                sourceEventKey = "source-$uuid",
                importBatchId = null,
                createdAt = 60L + identityId
            )
        )

    private suspend fun evidence(
        eventId: Long,
        sourceProfileId: Long,
        fingerprint: String,
        ordinal: Int
    ) {
        database.importedListeningEventEvidenceDao().insert(
            ImportedListeningEventEvidenceEntity(
                eventId = eventId,
                sourceProfileId = sourceProfileId,
                fingerprintVersion = 1,
                fingerprint = fingerprint,
                duplicateOrdinal = ordinal,
                normalizedReasonStart = null,
                normalizedReasonEnd = null,
                skippedState = ImportedListeningSkippedState.FALSE,
                matchDispositionAtImport = ImportedListeningMatchDisposition.CREATED_HISTORICAL_IDENTITY
            )
        )
    }
}
