package io.github.rsgarrido.sazanami

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationBindingRequest
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationLinkResult
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationRepository
import io.github.rsgarrido.sazanami.data.SmartPlaylistDraft
import io.github.rsgarrido.sazanami.data.SmartPlaylistOperator
import io.github.rsgarrido.sazanami.data.SmartPlaylistRepository
import io.github.rsgarrido.sazanami.data.SmartPlaylistRule
import io.github.rsgarrido.sazanami.data.SmartPlaylistRuleField
import io.github.rsgarrido.sazanami.data.SmartPlaylistSortField
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.CachedSongEntity
import io.github.rsgarrido.sazanami.data.local.LegacyListeningBaselineEntity
import io.github.rsgarrido.sazanami.data.local.ListeningCompletionClassification
import io.github.rsgarrido.sazanami.data.local.ListeningEventEntity
import io.github.rsgarrido.sazanami.data.local.ListeningEventPublicationState
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationPolicy
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationReason
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.data.local.ListeningTimestampEvidence
import io.github.rsgarrido.sazanami.data.local.ListeningTrackIdentityEntity
import io.github.rsgarrido.sazanami.data.local.LocalTrackBindingEntity
import io.github.rsgarrido.sazanami.data.local.SmartPlaylistDatabaseTriggers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartPlaylistReconciliationTest {
    private lateinit var database: AppDatabase
    private lateinit var reconciliation: ListeningIdentityReconciliationRepository
    private lateinit var smartPlaylists: SmartPlaylistRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        SmartPlaylistDatabaseTriggers.install(database.openHelper.writableDatabase)
        reconciliation = ListeningIdentityReconciliationRepository(database) { NOW }
        smartPlaylists = SmartPlaylistRepository(database) { NOW }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun linkedImportedHistoryDrivesExistingCountAndForgottenRulesReversibly() = runBlocking {
        val target = localSong(1, "Canonical song", localPlays = 0)
        localSong(2, "Unrelated local song", localPlays = 0)
        val source = importedIdentity("Provider title", eventCount = 2)
        val eventsBefore = database.listeningEventDao().getBackupPage(100, 0)
        val exactImportedPlayCount = draft(
            SmartPlaylistRule(
                SmartPlaylistRuleField.TOTAL_PLAY_COUNT,
                SmartPlaylistOperator.EQUALS,
                listOf("2")
            )
        )
        val forgotten = draft(
            SmartPlaylistRule(
                SmartPlaylistRuleField.LAST_PLAYED,
                SmartPlaylistOperator.MORE_THAN_DAYS_AGO,
                listOf("30")
            )
        )

        assertTrue(smartPlaylists.previewMatchingSongs(exactImportedPlayCount).songs.isEmpty())
        assertTrue(reconciliation.link(source, target) is
            ListeningIdentityReconciliationLinkResult.Linked)
        assertEquals(
            listOf("Canonical song"),
            smartPlaylists.previewMatchingSongs(exactImportedPlayCount).songs.map { it.title }
        )
        assertEquals(
            listOf("Canonical song"),
            smartPlaylists.previewMatchingSongs(forgotten).songs.map { it.title }
        )
        assertEquals(eventsBefore, database.listeningEventDao().getBackupPage(100, 0))

        assertTrue(reconciliation.unlink(source))
        assertTrue(smartPlaylists.previewMatchingSongs(exactImportedPlayCount).songs.isEmpty())
        assertTrue(smartPlaylists.previewMatchingSongs(forgotten).songs.isEmpty())
        assertEquals(eventsBefore, database.listeningEventDao().getBackupPage(100, 0))

        assertTrue(reconciliation.link(source, target) is
            ListeningIdentityReconciliationLinkResult.Linked)
        assertEquals(
            listOf("Canonical song"),
            smartPlaylists.previewMatchingSongs(exactImportedPlayCount).songs.map { it.title }
        )
        assertEquals(
            listOf("Canonical song"),
            smartPlaylists.previewMatchingSongs(forgotten).songs.map { it.title }
        )
        assertEquals(eventsBefore, database.listeningEventDao().getBackupPage(100, 0))
    }

    @Test
    fun singleAndBatchBindingsProduceEquivalentHistoryAwareMembership() = runBlocking {
        val singleTarget = localSong(10, "Single target", localPlays = 1)
        val batchTarget = localSong(20, "Batch target", localPlays = 1)
        val singleSource = importedIdentity("Single source", eventCount = 2)
        val batchSource = importedIdentity("Batch source", eventCount = 2)
        val eventsBefore = database.listeningEventDao().getBackupPage(100, 0)

        assertTrue(reconciliation.link(singleSource, singleTarget) is
            ListeningIdentityReconciliationLinkResult.Linked)
        val batch = reconciliation.linkBatch(listOf(
            ListeningIdentityReconciliationBindingRequest(batchSource, batchTarget)
        ))

        assertEquals(1, batch.newlyLinked)
        assertEquals(
            listOf("Batch target", "Single target"),
            smartPlaylists.previewMatchingSongs(draft(
                SmartPlaylistRule(
                    SmartPlaylistRuleField.TOTAL_PLAY_COUNT,
                    SmartPlaylistOperator.EQUALS,
                    listOf("3")
                )
            )).songs.map { it.title }
        )
        assertEquals(eventsBefore, database.listeningEventDao().getBackupPage(100, 0))
        assertEquals(2L, database.listeningEventDao().countForTrackIdentity(singleSource))
        assertEquals(2L, database.listeningEventDao().countForTrackIdentity(batchSource))
    }

    private fun draft(rule: SmartPlaylistRule) = SmartPlaylistDraft(
        rules = listOf(rule),
        sortField = SmartPlaylistSortField.TITLE
    )

    private suspend fun localSong(mediaStoreId: Long, title: String, localPlays: Int): Long {
        database.cachedSongDao().insertCachedSongs(listOf(CachedSongEntity(
            mediaStoreId = mediaStoreId,
            title = title,
            artist = "Local artist",
            album = "Local album",
            trackNumber = 1,
            duration = 180_000L,
            uriString = "content://media/external/audio/$mediaStoreId",
            filePath = "/music/$title.flac",
            folderPath = "/music",
            albumArtUriString = null,
            albumArtist = "Local artist",
            volumeName = "external",
            displayName = "$title.flac",
            relativePath = "Music/",
            fileSizeBytes = 1_000L,
            dateAddedEpochSeconds = 1_700_000_000L,
            dateModifiedEpochSeconds = 1_700_000_000L,
            year = 2020,
            artworkEnrichmentVersion = 1,
            cachedAt = NOW
        )))
        val identityId = identity(title, "Local artist", "Local album")
        database.localTrackBindingDao().insert(LocalTrackBindingEntity(
            trackIdentityId = identityId,
            referenceKey = "ref-$mediaStoreId",
            mediaStoreId = mediaStoreId,
            volumeName = "external",
            contentUri = "content://media/external/audio/$mediaStoreId",
            relativePath = "Music/",
            displayName = "$title.flac",
            absolutePath = null,
            fileSizeBytes = 1_000L,
            dateModifiedEpochSeconds = 1_700_000_000L,
            durationMsSnapshot = 180_000L,
            legacyStableKey = "legacy-$mediaStoreId",
            portableKey = null,
            portableKeyVersion = 1,
            firstSeenAt = NOW,
            lastSeenAt = NOW,
            missingSince = null
        ))
        if (localPlays > 0) {
            database.legacyListeningBaselineDao().insert(LegacyListeningBaselineEntity(
                trackIdentityId = identityId,
                historicalPlayCount = localPlays,
                firstKnownPlayedAt = NOW - 70 * DAY,
                lastKnownPlayedAt = NOW - 60 * DAY,
                legacyReferenceKey = "baseline-$mediaStoreId",
                migratedAt = NOW
            ))
        }
        return identityId
    }

    private suspend fun importedIdentity(title: String, eventCount: Int): Long {
        val identityId = identity(title, "Provider artist", "Provider album")
        database.listeningEventDao().insert((0 until eventCount).map { index ->
            val at = NOW - 45 * DAY + index
            ListeningEventEntity(
                eventUuid = "import-$identityId-$index",
                source = ListeningSource.SPOTIFY_IMPORT,
                trackIdentityId = identityId,
                localTrackBindingId = null,
                playbackSessionId = null,
                startedAt = null,
                endedAt = at,
                attributionAt = at,
                timestampEvidence = ListeningTimestampEvidence.SOURCE_END_ONLY,
                listenedMs = 180_000L,
                trackDurationMs = 180_000L,
                qualifiedAsPlay = true,
                qualificationReason = ListeningQualificationReason.TIME_THRESHOLD,
                qualificationRuleVersion = 1,
                qualificationPolicy = ListeningQualificationPolicy.SPOTIFY,
                endReason = null,
                completionClassification = ListeningCompletionClassification.NONE,
                publicationState = ListeningEventPublicationState.IMPORT_PUBLISHED,
                sourceEventKey = "source-$identityId-$index",
                importBatchId = null,
                createdAt = at
            )
        })
        return identityId
    }

    private suspend fun identity(title: String, artist: String, album: String): Long =
        database.listeningTrackIdentityDao().insert(ListeningTrackIdentityEntity(
            titleSnapshot = title,
            artistSnapshot = artist,
            albumSnapshot = album,
            albumArtistSnapshot = artist,
            durationMsSnapshot = 180_000L,
            normalizedTitle = title.lowercase(),
            normalizedArtist = artist.lowercase(),
            normalizedAlbum = album.lowercase(),
            metadataKey = null,
            metadataKeyVersion = 1,
            createdAt = NOW,
            updatedAt = NOW
        ))

    private companion object {
        const val NOW = 2_000_000_000_000L
        const val DAY = 86_400_000L
    }
}
