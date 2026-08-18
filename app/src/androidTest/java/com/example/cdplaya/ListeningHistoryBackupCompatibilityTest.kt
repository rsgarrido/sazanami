package com.example.cdplaya

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.ListeningHistoryRepository
import com.example.cdplaya.data.FavoritesRepository
import com.example.cdplaya.data.PlaylistsRepository
import com.example.cdplaya.data.backup.AppBackupJson
import com.example.cdplaya.data.backup.AppBackup
import com.example.cdplaya.data.backup.ListeningHistoryBackupRepository
import com.example.cdplaya.data.ListeningImportRepository
import com.example.cdplaya.data.importing.spotify.ListeningImportStreamSource
import com.example.cdplaya.data.importing.spotify.SpotifyExtendedStreamingParser
import com.example.cdplaya.data.importing.spotify.SpotifyImportSourceProfileService
import com.example.cdplaya.data.importing.spotify.SpotifyListeningHistoryImportExecutor
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.ListeningEndReason
import com.example.cdplaya.data.local.ListeningEventEntity
import com.example.cdplaya.data.local.ListeningQualificationReason
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.ListeningTrackIdentityEntity
import com.example.cdplaya.data.local.SongRatingEntity
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningHistoryBackupCompatibilityTest {
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
    fun version6_restoresAggregatesAsSeparateCanonicalBaselinesAndCompatibilityOnly() = runBlocking {
        val backup = AppBackupJson.decodeBackup(V6_FIXTURE)
        val favoritesRepository = FavoritesRepository(database.favoriteSongDao())
        val playlistsRepository = PlaylistsRepository(database.playlistDao())
        favoritesRepository.restoreFavoritesFromBackup(backup.favorites)
        playlistsRepository.restorePlaylistsFromBackup(backup.playlists)
        ListeningHistoryRepository(database.songPlayStatsDao())
            .restoreListeningHistoryFromBackup(backup.listeningHistory)
        ListeningHistoryBackupRepository(database).restore(backup.canonicalListeningHistory!!)

        val canonical = ListeningHistoryBackupRepository(database).export()
        assertEquals(2, canonical.identities.size)
        assertEquals(2, canonical.bindings.size)
        assertEquals(listOf(3, 4), canonical.baselines.map { it.historicalPlayCount })
        assertEquals(listOf(10L, 20L), canonical.baselines.map { it.firstKnownPlayedAt })
        assertEquals(listOf(30L, 40L), canonical.baselines.map { it.lastKnownPlayedAt })
        assertTrue(canonical.events.isEmpty())
        assertNotEquals(canonical.identities[0].backupIdentityId, canonical.identities[1].backupIdentityId)
        assertEquals(2, database.songPlayStatsDao().getRecentlyPlayed().size)
        assertEquals(1, favoritesRepository.getFavoritesForBackup().size)
        assertEquals("Saved", playlistsRepository.getPlaylists().single().name)
        assertEquals(1, playlistsRepository.getPlaylistsForBackup().single().songs.size)
    }

    @Test
    fun version8RestoresNativeHistoryAndRatingWithoutProvenanceThenAllowsSpotifyImport() =
        runBlocking {
            val identityId = database.listeningTrackIdentityDao().insert(
                ListeningTrackIdentityEntity(
                    titleSnapshot = "Backup 8 Native",
                    artistSnapshot = "Local Artist",
                    albumSnapshot = "Local Album",
                    albumArtistSnapshot = "Local Artist",
                    durationMsSnapshot = 60_000L,
                    normalizedTitle = "backup 8 native",
                    normalizedArtist = "local artist",
                    normalizedAlbum = "local album",
                    metadataKey = null,
                    metadataKeyVersion = 1,
                    createdAt = 1L,
                    updatedAt = 2L
                )
            )
            database.listeningEventDao().insert(
                ListeningEventEntity(
                    eventUuid = "backup-8-native-event",
                    source = ListeningSource.CDPLAYA,
                    trackIdentityId = identityId,
                    localTrackBindingId = null,
                    playbackSessionId = "backup-8-native-session",
                    startedAt = 1_000L,
                    endedAt = 2_000L,
                    listenedMs = 1_000L,
                    trackDurationMs = 60_000L,
                    qualifiedAsPlay = true,
                    qualificationReason = ListeningQualificationReason.TIME_THRESHOLD,
                    qualificationRuleVersion = 1,
                    endReason = ListeningEndReason.STOPPED,
                    sourceEventKey = null,
                    importBatchId = null,
                    createdAt = 2_001L
                )
            )
            database.songRatingDao().upsert(SongRatingEntity(identityId, 5, 3_000L, 3_000L))
            val repository = ListeningHistoryBackupRepository(database)
            val exported = repository.exportWithRatings()
            val migrated = AppBackupJson.decodeBackup(
                AppBackupJson.encodeBackup(
                    AppBackup(
                        schemaVersion = 8,
                        createdAt = 4_000L,
                        canonicalListeningHistory = exported.history,
                        songRatings = exported.ratings
                    )
                )
            )

            database.withTransaction {
                val identityIds = repository.restoreValidatedWithinTransaction(
                    requireNotNull(migrated.canonicalListeningHistory)
                )
                repository.restoreRatingsValidatedWithinTransaction(migrated.songRatings, identityIds)
            }
            val restored = repository.exportWithRatings()
            assertEquals(1, restored.history.events.size)
            assertEquals("cdplaya", restored.history.events.single().source)
            assertEquals(5, restored.ratings.entries.single().rating)
            assertTrue(restored.history.importSources.isEmpty())
            assertTrue(restored.history.importBatches.isEmpty())
            assertTrue(restored.history.importedEventEvidence.isEmpty())

            val importRepository = ListeningImportRepository(database, nowMillis = { 5_000L })
            val profiles = SpotifyImportSourceProfileService(importRepository) { 5_000L }
            val parser = SpotifyExtendedStreamingParser(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)
            )
            val executor = SpotifyListeningHistoryImportExecutor(
                repository = importRepository,
                sourceProfiles = profiles,
                parser = parser,
                nowMillis = { 5_000L },
                batchUuid = { "after-backup-8" },
                createdAppVersion = "test"
            )
            val imported = executor.execute(
                listOf(ListeningImportStreamSource {
                    ByteArrayInputStream(
                        """[{"ts":"2024-07-01T00:00:00Z","ms_played":31000,"master_metadata_track_name":"Imported Later","master_metadata_album_artist_name":"Import Artist","master_metadata_album_album_name":"Import Album","spotify_track_uri":"spotify:track:Reexport00000000000001"}]"""
                            .toByteArray()
                    )
                })
            )

            assertEquals(1L, imported.newPublished)
            assertEquals(2L, database.listeningEventDao().count())
            assertEquals(5, database.songRatingDao().getByTrackIdentityId(
                database.listeningTrackIdentityDao().getAll().single {
                    it.titleSnapshot == "Backup 8 Native"
                }.id
            )?.rating)
        }

    private companion object {
        val V6_FIXTURE = """
            {
              "schemaVersion": 6,
              "createdAt": 999,
              "favorites": [{"songKey":"favorite","title":"Favorite","artist":"Artist","album":"Album","duration":1000,"createdAt":9}],
              "playlists": [{"name":"Saved","createdAt":1,"updatedAt":2,"songs":[{"songKey":"playlist","position":0,"title":"Playlist","artist":"Artist","album":"Album","duration":1000,"addedAt":3}]}],
              "listeningHistory": [
                {"songKey":"one","title":"Same","artist":"Artist","album":"Album","duration":1000,"playCount":3,"firstPlayedAt":10,"lastPlayedAt":30},
                {"songKey":"two","title":"Same","artist":"Artist","album":"Album","duration":1000,"playCount":4,"firstPlayedAt":20,"lastPlayedAt":40}
              ]
            }
        """.trimIndent()
    }
}
