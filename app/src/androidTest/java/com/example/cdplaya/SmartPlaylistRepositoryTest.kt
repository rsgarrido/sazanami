package com.example.cdplaya

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.GeneratedPlaylistRefreshPolicy
import com.example.cdplaya.data.PlaylistsRepository
import com.example.cdplaya.data.SmartPlaylistDraft
import com.example.cdplaya.data.SmartPlaylistMatchMode
import com.example.cdplaya.data.SmartPlaylistOperator
import com.example.cdplaya.data.SmartPlaylistRepository
import com.example.cdplaya.data.SmartPlaylistRule
import com.example.cdplaya.data.SmartPlaylistRuleField
import com.example.cdplaya.data.SmartPlaylistSortDirection
import com.example.cdplaya.data.SmartPlaylistSortField
import com.example.cdplaya.data.toSong
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.CachedSongEntity
import com.example.cdplaya.data.local.LegacyListeningBaselineEntity
import com.example.cdplaya.data.local.ListeningTrackIdentityEntity
import com.example.cdplaya.data.local.ListeningEndReason
import com.example.cdplaya.data.local.ListeningEventEntity
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.LocalTrackBindingEntity
import com.example.cdplaya.data.local.SmartPlaylistDatabaseTriggers
import com.example.cdplaya.data.local.SongRatingEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartPlaylistRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private var now = 2_000_000_000_000L

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        SmartPlaylistDatabaseTriggers.install(database.openHelper.writableDatabase)
        seedSong(1L, "Alpha", "One", "First", 2020, 100_000L, rating = 5, plays = 8)
        seedSong(2L, "Beta", "Two", "Second", 2021, 200_000L, rating = null, plays = 0)
        seedSong(3L, "Gamma", "One", "Second", 2021, 300_000L, rating = 3, plays = 2)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun allAnyRatingUnratedTextNumericOrderingAndLimit() = runBlocking {
        val repository = SmartPlaylistRepository(database) { now }
        val rating = SmartPlaylistRule(
            SmartPlaylistRuleField.RATING,
            SmartPlaylistOperator.AT_LEAST,
            listOf("4")
        )
        val artist = SmartPlaylistRule(
            SmartPlaylistRuleField.ARTIST,
            SmartPlaylistOperator.IS,
            listOf("one")
        )

        assertEquals(listOf("Alpha"), repository.previewMatchingSongs(
            SmartPlaylistDraft(rules = listOf(rating, artist))
        ).songs.map { it.title })
        assertEquals(listOf("Alpha", "Gamma"), repository.previewMatchingSongs(
            SmartPlaylistDraft(
                matchMode = SmartPlaylistMatchMode.ANY,
                rules = listOf(rating, artist),
                sortField = SmartPlaylistSortField.TITLE
            )
        ).songs.map { it.title })
        assertEquals(listOf("Beta"), repository.previewMatchingSongs(
            SmartPlaylistDraft(rules = listOf(SmartPlaylistRule(
                SmartPlaylistRuleField.RATING,
                SmartPlaylistOperator.UNRATED
            )))
        ).songs.map { it.title })
        assertEquals(listOf("Gamma"), repository.previewMatchingSongs(
            SmartPlaylistDraft(
                rules = listOf(SmartPlaylistRule(
                    SmartPlaylistRuleField.DURATION,
                    SmartPlaylistOperator.BETWEEN,
                    listOf("250000", "350000")
                )),
                sortField = SmartPlaylistSortField.PLAY_COUNT,
                sortDirection = SmartPlaylistSortDirection.DESCENDING,
                resultLimit = 1
            )
        ).songs.map { it.title })
        assertEquals(3, repository.getMatchingCount(SmartPlaylistDraft(resultLimit = 1)))
    }

    @Test
    fun neverPlayedAndYearUseNullDistinctAuthoritativeRows() = runBlocking {
        val repository = SmartPlaylistRepository(database) { now }

        assertEquals(listOf("Beta"), repository.previewMatchingSongs(
            SmartPlaylistDraft(rules = listOf(SmartPlaylistRule(
                SmartPlaylistRuleField.LAST_PLAYED,
                SmartPlaylistOperator.NEVER
            )))
        ).songs.map { it.title })
        assertEquals(listOf("Beta", "Gamma"), repository.previewMatchingSongs(
            SmartPlaylistDraft(
                rules = listOf(SmartPlaylistRule(
                    SmartPlaylistRuleField.YEAR,
                    SmartPlaylistOperator.EQUALS,
                    listOf("2021")
                )),
                sortField = SmartPlaylistSortField.TITLE
            )
        ).songs.map { it.title })
    }

    @Test
    fun recentPlayCountUsesQualifiedEventWindow() = runBlocking {
        seedQualifiedPlay(1L, now - 10L * 86_400_000L)
        seedQualifiedPlay(3L, now - 40L * 86_400_000L)
        val repository = SmartPlaylistRepository(database) { now }

        assertEquals(listOf("Alpha"), repository.previewMatchingSongs(
            SmartPlaylistDraft(rules = listOf(SmartPlaylistRule(
                field = SmartPlaylistRuleField.RECENT_PLAY_COUNT,
                operator = SmartPlaylistOperator.AT_LEAST,
                values = listOf("1"),
                parameters = mapOf("days" to "30")
            )))
        ).songs.map { it.title })
    }

    @Test
    fun previewDoesNotPersistAndLiveCacheInvalidatesOnRatingChange() = runBlocking {
        val repository = SmartPlaylistRepository(database) { now }
        val draft = SmartPlaylistDraft(rules = listOf(SmartPlaylistRule(
            SmartPlaylistRuleField.RATING,
            SmartPlaylistOperator.AT_LEAST,
            listOf("4")
        )))

        repository.previewMatchingSongs(draft)
        assertEquals(0, database.smartPlaylistDao().getAllDefinitions().size)

        val definition = repository.createSmartPlaylist("Rated", draft)
        assertNotNull(definition)
        val playlistId = requireNotNull(definition).playlistId
        assertFalse(repository.resolveFinalMembership(playlistId).fromDerivedCache)
        assertEquals(true, repository.resolveFinalMembership(playlistId).fromDerivedCache)

        val betaIdentity = database.localTrackBindingDao().getByReferenceKey("ref-2")!!.trackIdentityId
        database.songRatingDao().upsert(SongRatingEntity(betaIdentity, 4, now, now))
        assertEquals(listOf("Alpha", "Beta"), repository.resolveFinalMembership(playlistId)
            .songs.map { it.title })
    }

    @Test
    fun generatedSnapshotRemainsStableUntilExplicitRefresh() = runBlocking {
        val repository = SmartPlaylistRepository(database) { now++ }
        val definition = requireNotNull(repository.createGeneratedPlaylist(
            name = "Heavy Rotation",
            templateKey = "heavy_rotation",
            draft = SmartPlaylistDraft(sortField = SmartPlaylistSortField.TITLE),
            refreshPolicy = GeneratedPlaylistRefreshPolicy.PERIODIC,
            refreshIntervalMillis = 86_400_000L
        ))
        repository.refreshGeneratedSnapshot(definition.playlistId)
        seedSong(4L, "Delta", "Three", "Third", 2022, 150_000L, null, 0)

        assertEquals(3, repository.resolveFinalMembership(definition.playlistId).count)
        assertEquals(4, repository.refreshGeneratedSnapshot(definition.playlistId).count)
    }

    @Test
    fun manualAndSmartMembershipStoresRemainSeparated() = runBlocking {
        val manualRepository = PlaylistsRepository(database.playlistDao())
        val smartRepository = SmartPlaylistRepository(database) { now }
        val manualId = requireNotNull(manualRepository.createPlaylistReturningId("Manual"))
        val smartId = requireNotNull(smartRepository.createSmartPlaylist(
            "Smart",
            SmartPlaylistDraft()
        )).playlistId

        assertEquals(0, database.playlistDao().getPlaylistSongs(manualId).size)
        assertEquals(0, database.playlistDao().getPlaylistSongs(smartId).size)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manualRepository.addSongsToPlaylist(smartId, listOf(
                database.cachedSongDao().getAllCachedSongs().first().toSong()
            )) }
        }
        Unit
    }

    private suspend fun seedSong(
        mediaStoreId: Long,
        title: String,
        artist: String,
        album: String,
        year: Int,
        duration: Long,
        rating: Int?,
        plays: Int
    ) {
        database.cachedSongDao().insertCachedSongs(listOf(CachedSongEntity(
            mediaStoreId = mediaStoreId,
            title = title,
            artist = artist,
            album = album,
            trackNumber = 1,
            duration = duration,
            uriString = "content://media/external/audio/$mediaStoreId",
            filePath = "/music/$title.mp3",
            folderPath = "/music",
            albumArtUriString = null,
            albumArtist = artist,
            volumeName = "external",
            displayName = "$title.mp3",
            relativePath = "Music/",
            fileSizeBytes = duration,
            dateAddedEpochSeconds = 1_700_000_000L + mediaStoreId,
            dateModifiedEpochSeconds = 1_700_000_000L,
            year = year,
            artworkEnrichmentVersion = 1,
            cachedAt = now
        )))
        val identityId = database.listeningTrackIdentityDao().insert(
            ListeningTrackIdentityEntity(
                titleSnapshot = title,
                artistSnapshot = artist,
                albumSnapshot = album,
                albumArtistSnapshot = artist,
                durationMsSnapshot = duration,
                normalizedTitle = title.lowercase(),
                normalizedArtist = artist.lowercase(),
                normalizedAlbum = album.lowercase(),
                metadataKey = "metadata-$mediaStoreId",
                metadataKeyVersion = 1,
                createdAt = now,
                updatedAt = now
            )
        )
        database.localTrackBindingDao().insert(LocalTrackBindingEntity(
            trackIdentityId = identityId,
            referenceKey = "ref-$mediaStoreId",
            mediaStoreId = mediaStoreId,
            volumeName = "external",
            contentUri = "content://media/external/audio/$mediaStoreId",
            relativePath = "Music/",
            displayName = "$title.mp3",
            absolutePath = null,
            fileSizeBytes = duration,
            dateModifiedEpochSeconds = 1_700_000_000L,
            durationMsSnapshot = duration,
            legacyStableKey = "legacy-$mediaStoreId",
            portableKey = "metadata-$mediaStoreId",
            portableKeyVersion = 1,
            firstSeenAt = now,
            lastSeenAt = now,
            missingSince = null
        ))
        if (plays > 0) {
            database.legacyListeningBaselineDao().insert(LegacyListeningBaselineEntity(
                trackIdentityId = identityId,
                historicalPlayCount = plays,
                firstKnownPlayedAt = now - 100_000L,
                lastKnownPlayedAt = now - mediaStoreId * 1_000L,
                legacyReferenceKey = "baseline-$mediaStoreId",
                migratedAt = now
            ))
        }
        rating?.let {
            database.songRatingDao().upsert(SongRatingEntity(identityId, it, now, now))
        }
    }

    private suspend fun seedQualifiedPlay(mediaStoreId: Long, startedAt: Long) {
        val binding = requireNotNull(
            database.localTrackBindingDao().getByReferenceKey("ref-$mediaStoreId")
        )
        database.listeningEventDao().insert(
            ListeningEventEntity(
                eventUuid = "event-$mediaStoreId-$startedAt",
                source = ListeningSource.CDPLAYA,
                trackIdentityId = binding.trackIdentityId,
                localTrackBindingId = binding.id,
                playbackSessionId = "session-$mediaStoreId-$startedAt",
                startedAt = startedAt,
                endedAt = startedAt + 60_000L,
                listenedMs = 60_000L,
                trackDurationMs = 100_000L,
                qualifiedAsPlay = true,
                qualificationReason = ListeningQualificationReason.TIME_THRESHOLD,
                qualificationRuleVersion = 1,
                endReason = ListeningEndReason.STOPPED,
                sourceEventKey = null,
                importBatchId = null,
                createdAt = startedAt + 60_000L
            )
        )
    }
}
