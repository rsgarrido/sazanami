package io.github.rsgarrido.sazanami

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.controller.DefaultListeningHistoryReconciliationOperations
import io.github.rsgarrido.sazanami.controller.groupLinkedReconciliations
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationLinkResult
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationRepository
import io.github.rsgarrido.sazanami.data.ListeningStatsRepository
import io.github.rsgarrido.sazanami.data.backup.AppBackup
import io.github.rsgarrido.sazanami.data.backup.AppBackupJson
import io.github.rsgarrido.sazanami.data.backup.ListeningHistoryBackupRepository
import io.github.rsgarrido.sazanami.data.backup.ListeningHistoryBackupValidator
import io.github.rsgarrido.sazanami.data.backup.SongRatingBackupValidator
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.ImportedListeningEventEvidenceEntity
import io.github.rsgarrido.sazanami.data.local.ImportedListeningMatchDisposition
import io.github.rsgarrido.sazanami.data.local.ImportedListeningSkippedState
import io.github.rsgarrido.sazanami.data.local.ListeningCompletionClassification
import io.github.rsgarrido.sazanami.data.local.ListeningEventEntity
import io.github.rsgarrido.sazanami.data.local.ListeningEventPublicationState
import io.github.rsgarrido.sazanami.data.local.ListeningImportSourceEntity
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationPolicy
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationReason
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.data.local.ListeningTimestampEvidence
import io.github.rsgarrido.sazanami.data.local.ListeningTrackExternalIdEntity
import io.github.rsgarrido.sazanami.data.local.ListeningTrackIdentityEntity
import io.github.rsgarrido.sazanami.data.local.LocalTrackBindingEntity
import io.github.rsgarrido.sazanami.data.local.SongRatingEntity
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

        val canonicalTopTrack = ListeningStatsRepository(database)
            .getTopTracksByQualifiedPlays(10)
            .single()
        assertEquals(restoredTarget, canonicalTopTrack.trackIdentityId)
        assertEquals("Local target", canonicalTopTrack.title)
        assertEquals(2L, canonicalTopTrack.playCounts.totalPlayCount)
        assertEquals(5, canonicalTopTrack.effectiveRating)
    }

    @Test
    fun realisticBackup10RoundTripPreservesMissingUnmatchedFragmentsAndLinkedGrouping() = runBlocking {
        val localOne = identity("Local one")
        val historicalA1 = identity("Historical A1")
        val historicalA2 = identity("Historical A2")
        val localTwo = identity("Local two missing")
        val historicalB1 = identity("Historical B1")
        val unmatched = identity("Unmatched U1")
        val fragmentOne = identity("Fragment F1")
        val fragmentTwo = identity("Fragment F2")
        val localOneBinding = database.localTrackBindingDao().insert(localBinding(localOne, null))
        database.localTrackBindingDao().insert(localBinding(localTwo, 999L))
        nativeEvent(localOne, localOneBinding)
        val profile = database.listeningImportSourceDao().insert(
            ListeningImportSourceEntity(
                stableUuid = "realistic-profile",
                sourceType = ListeningSource.SPOTIFY_IMPORT,
                displayLabel = "Fictional account",
                accountIdentityDigest = "fictional-digest",
                createdAt = 1L,
                updatedAt = 2L
            )
        )
        val historicalIds = listOf(
            historicalA1,
            historicalA2,
            historicalB1,
            unmatched,
            fragmentOne,
            fragmentTwo
        )
        historicalIds.forEachIndexed { index, identityId ->
            val eventId = importedEvent(identityId, "realistic-$index")
            evidence(eventId, profile, "realistic-fingerprint-$index", index)
        }
        database.listeningTrackExternalIdDao().insert(
            listOf(
                ListeningTrackExternalIdEntity(
                    0L, historicalA1, ListeningSource.SPOTIFY_IMPORT, "spotify:a1", 10L, 11L
                ),
                ListeningTrackExternalIdEntity(
                    0L, historicalA2, ListeningSource.SPOTIFY_IMPORT, "spotify:a2", 12L, 13L
                ),
                ListeningTrackExternalIdEntity(
                    0L, historicalB1, ListeningSource.SPOTIFY_IMPORT, "spotify:b1", 14L, 15L
                ),
                ListeningTrackExternalIdEntity(
                    0L, unmatched, ListeningSource.SPOTIFY_IMPORT, "spotify:u1", 16L, 17L
                )
            )
        )
        database.songRatingDao().insert(
            listOf(
                SongRatingEntity(localOne, 5, 20L, 21L),
                SongRatingEntity(historicalA1, 2, 22L, 23L),
                SongRatingEntity(localTwo, 4, 24L, 25L),
                SongRatingEntity(historicalB1, 1, 26L, 27L)
            )
        )
        val repository = ListeningIdentityReconciliationRepository(database)
        listOf(historicalA1, historicalA2).forEachIndexed { index, source ->
            assertTrue(repository.link(source, localOne, 100L + index) is
                ListeningIdentityReconciliationLinkResult.Linked)
        }
        listOf(historicalB1, fragmentOne, fragmentTwo).forEachIndexed { index, source ->
            assertTrue(repository.link(source, localTwo, 200L + index) is
                ListeningIdentityReconciliationLinkResult.Linked)
        }

        val backupRepository = ListeningHistoryBackupRepository(database)
        val backup = backupRepository.exportWithRatings()
        database.withTransaction {
            val remapped = backupRepository.restoreValidatedWithinTransaction(
                ListeningHistoryBackupValidator.validate(backup.history)
            )
            backupRepository.restoreRatingsValidatedWithinTransaction(
                SongRatingBackupValidator.validate(backup.ratings, backup.history),
                remapped
            )
        }

        val restored = backupRepository.exportWithRatings()
        val idsByTitle = restored.history.identities.associate { it.titleSnapshot to it.backupIdentityId }
        val restoredLocalOne = idsByTitle.getValue("Local one")
        val restoredLocalTwo = idsByTitle.getValue("Local two missing")
        val restoredUnmatched = idsByTitle.getValue("Unmatched U1")
        assertEquals(8L, restored.history.summary.identityCount)
        assertEquals(7L, restored.history.summary.eventCount)
        assertEquals(5, restored.history.reconciliations.size)
        assertEquals(
            2,
            restored.history.reconciliations.count { it.targetIdentityBackupId == restoredLocalOne }
        )
        assertEquals(
            3,
            restored.history.reconciliations.count { it.targetIdentityBackupId == restoredLocalTwo }
        )
        assertTrue(restored.history.reconciliations.none { it.sourceIdentityBackupId == restoredUnmatched })
        assertEquals(
            999L,
            restored.history.bindings.single {
                it.trackIdentityBackupId == restoredLocalTwo
            }.missingSince
        )
        assertEquals(
            setOf("spotify:a1", "spotify:a2", "spotify:b1", "spotify:u1"),
            restored.history.externalTrackIds.map { it.externalId }.toSet()
        )
        assertEquals(
            (0..5).map { "realistic-fingerprint-$it" to it }.toSet(),
            restored.history.importedEventEvidence
                .map { it.fingerprint to it.duplicateOrdinal }.toSet()
        )
        assertEquals(5, restored.ratings.entries.single {
            it.trackIdentityBackupId == restoredLocalOne
        }.rating)
        assertEquals(2, restored.ratings.entries.single {
            it.trackIdentityBackupId == idsByTitle.getValue("Historical A1")
        }.rating)

        val top = ListeningStatsRepository(database).getTopTracksByQualifiedPlays(10)
        assertEquals(3L, top.single { it.trackIdentityId == restoredLocalTwo }.playCounts.totalPlayCount)
        assertEquals(2L, top.single { it.trackIdentityId == restoredLocalOne }.playCounts.totalPlayCount)
        assertEquals(1L, top.single { it.trackIdentityId == restoredUnmatched }.playCounts.totalPlayCount)
        assertEquals(5, top.single { it.trackIdentityId == restoredLocalOne }.effectiveRating)
        assertEquals(4, top.single { it.trackIdentityId == restoredLocalTwo }.effectiveRating)

        val linkedSnapshot = DefaultListeningHistoryReconciliationOperations(
            database = database,
            currentSongs = { emptyList() }
        ).load()
        val groups = groupLinkedReconciliations(linkedSnapshot.linkedItems)
        assertEquals(2, groups.size)
        assertEquals(setOf(2, 3), groups.map { it.historicalIdentityCount }.toSet())
        assertEquals(5, groups.sumOf { it.historicalIdentityCount })
        assertTrue(linkedSnapshot.reviewItems.any { it.source.identityId == restoredUnmatched })
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
        assertEquals(AppBackupJson.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
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
                source = ListeningSource.NATIVE,
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
                qualificationPolicy = ListeningQualificationPolicy.NATIVE,
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
