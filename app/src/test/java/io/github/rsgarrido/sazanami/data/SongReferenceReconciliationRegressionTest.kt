package io.github.rsgarrido.sazanami.data

import android.net.Uri
import io.github.rsgarrido.sazanami.data.local.FavoriteSongDao
import io.github.rsgarrido.sazanami.data.local.FavoriteSongEntity
import io.github.rsgarrido.sazanami.data.local.PlaylistSongEntity
import io.github.rsgarrido.sazanami.data.local.SongPlayStatsEntity
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class SongReferenceReconciliationRegressionTest {
    @Test
    fun secondReconciliationPassPerformsZeroWrites() {
        val song = song(1)
        val index = SongReferenceIndex.build(listOf(song))
        val initial = PersistedSongReferenceRows(
            favorites = listOf(legacyFavorite(song)),
            playlistRows = listOf(legacyPlaylistRow(10, 4, song)),
            historyRows = listOf(legacyHistoryRow(song))
        )

        val first = SongReferenceReconciliationPlanner.plan(index, initial)
        val afterFirst = applyPlan(initial, first)
        val second = SongReferenceReconciliationPlanner.plan(index, afterFirst)

        assertEquals(3, first.writeCount)
        assertEquals(0, second.writeCount)
        assertEquals(3, second.inspectedRowCount)
    }

    @Test
    fun backfillRoundTripIsStable() {
        val song = song(2)
        val favorite = legacyFavorite(song).withSongReference(song)
        val playlist = legacyPlaylistRow(3, 7, song).withSongReference(song)
        val history = legacyHistoryRow(song).withSongReference(song)

        assertEquals(song.toSongReference(), favorite.toSongReference())
        assertEquals(song.toSongReference(), playlist.toSongReference())
        assertEquals(song.toSongReference(), history.toSongReference())
        assertEquals(favorite, favorite.withSongReference(song))
        assertEquals(playlist, playlist.withSongReference(song))
        assertEquals(history, history.withSongReference(song))
    }

    @Test
    fun repositoryBackfillDoesNotTriggerRecursiveReconciliation() = runBlocking {
        val song = song(3)
        val dao = CountingFavoriteDao(mutableListOf(legacyFavorite(song)))
        val repository = FavoritesRepository(dao)

        val result = repository.reconcileSongReferences(SongReferenceIndex.build(listOf(song)))

        assertEquals(1, result.backfilledCount)
        assertEquals(1, dao.readCount)
        assertEquals(1, dao.batchWriteCount)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun overlappingGenerationsDoNotRunConcurrently() = runBlocking {
        val coordinator = ReconciliationGenerationCoordinator()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val firstGeneration = coordinator.nextGeneration()
        val first = async(Dispatchers.Default) {
            coordinator.runLatest(firstGeneration) {
                val nowActive = active.incrementAndGet()
                maxActive.updateAndGet { previous -> maxOf(previous, nowActive) }
                firstStarted.complete(Unit)
                releaseFirst.await()
                active.decrementAndGet()
                "old"
            }
        }
        firstStarted.await()
        val secondGeneration = coordinator.nextGeneration()
        val second = async(Dispatchers.Default) {
            coordinator.runLatest(secondGeneration) {
                val nowActive = active.incrementAndGet()
                maxActive.updateAndGet { previous -> maxOf(previous, nowActive) }
                active.decrementAndGet()
                "new"
            }
        }

        releaseFirst.complete(Unit)

        assertNull(first.await())
        assertEquals("new", second.await())
        assertEquals(1, maxActive.get())
    }

    @Test
    fun supersededGenerationCannotPublishOlderResults() = runBlocking {
        val coordinator = ReconciliationGenerationCoordinator()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val oldGeneration = coordinator.nextGeneration()
        val old = async(Dispatchers.Default) {
            coordinator.runLatest(oldGeneration) {
                started.complete(Unit)
                release.await()
                1
            }
        }
        started.await()
        val currentGeneration = coordinator.nextGeneration()
        release.complete(Unit)

        assertNull(old.await())
        assertEquals(2, coordinator.runLatest(currentGeneration) { 2 })
    }

    @Test
    fun resolverIndexBuildsOncePerLibrarySnapshot() {
        val songs = (1L..40L).map(::song)
        var indexBuilds = 0
        val index = SongReferenceIndex.build(songs).also { indexBuilds += 1 }

        repeat(4) {
            songs.forEach { candidate ->
                assertTrue(index.resolve(candidate.toSongReference()) is SongReferenceResolution.Resolved)
            }
        }

        assertEquals(1, indexBuilds)
    }

    @Test
    fun batchBackfillPreservesPlaylistOrderAndDuplicates() {
        val song = song(4)
        val rows = listOf(
            legacyPlaylistRow(id = 40, position = 8, song = song),
            legacyPlaylistRow(id = 41, position = 9, song = song)
        )

        val plan = SongReferenceReconciliationPlanner.planPlaylists(
            SongReferenceIndex.build(listOf(song)),
            rows
        )

        assertEquals(2, plan.rows.size)
        assertEquals(listOf(40L, 41L), plan.rows.map { it.playlistSongId })
        assertEquals(listOf(8, 9), plan.rows.map { it.position })
        assertEquals(2, plan.rows.count { it.songKey == song.stableKey() })
    }

    @Test
    fun historyBatchMergePreservesCountsAndTimestamps() {
        val song = song(44)
        val existing = legacyHistoryRow(song).withSongReference(song).copy(
            playCount = 4,
            firstPlayedAt = 20,
            lastPlayedAt = 50
        )
        val legacy = legacyHistoryRow(song).copy(
            referenceKey = "restored:${song.stableKey()}",
            playCount = 6,
            firstPlayedAt = 10,
            lastPlayedAt = 70
        )

        val plan = SongReferenceReconciliationPlanner.planHistory(
            SongReferenceIndex.build(listOf(song)),
            listOf(existing, legacy)
        )

        assertEquals(listOf(legacy.referenceKey), plan.oldReferenceKeys)
        assertEquals(1, plan.rows.size)
        assertEquals(10, plan.rows.single().playCount)
        assertEquals(10L, plan.rows.single().firstPlayedAt)
        assertEquals(70L, plan.rows.single().lastPlayedAt)
    }

    @Test
    fun ambiguousRowsRemainUnchanged() {
        val first = song(5, relativePath = "A/")
        val second = song(6, relativePath = "B/").copy(
            title = first.title,
            displayName = first.displayName,
            fileSizeBytes = first.fileSizeBytes
        )
        val ambiguous = legacyFavorite(first).copy(
            songKey = "",
            portableKey = first.songIdentity().portableKey.orEmpty()
        )
        val ambiguousPlaylist = legacyPlaylistRow(50, 0, first).copy(
            songKey = "",
            portableKey = first.songIdentity().portableKey.orEmpty()
        )
        val ambiguousHistory = legacyHistoryRow(first).copy(
            songKey = "",
            portableKey = first.songIdentity().portableKey.orEmpty()
        )

        val plan = SongReferenceReconciliationPlanner.plan(
            SongReferenceIndex.build(listOf(first, second)),
            PersistedSongReferenceRows(
                favorites = listOf(ambiguous),
                playlistRows = listOf(ambiguousPlaylist),
                historyRows = listOf(ambiguousHistory)
            )
        )

        assertEquals(1, plan.favorites.result.ambiguousCount)
        assertEquals(1, plan.playlists.result.ambiguousCount)
        assertEquals(1, plan.history.result.ambiguousCount)
        assertEquals(0, plan.writeCount)
        assertTrue(plan.favorites.rows.isEmpty())
        assertTrue(plan.playlists.rows.isEmpty())
        assertTrue(plan.history.rows.isEmpty())
        assertTrue(plan.favorites.oldReferenceKeys.isEmpty())
        assertTrue(plan.history.oldReferenceKeys.isEmpty())
    }

    @Test
    fun unchangedStrongReferencesProduceNoDatabaseWrites() {
        val song = song(7)
        val rows = PersistedSongReferenceRows(
            favorites = listOf(legacyFavorite(song).withSongReference(song)),
            playlistRows = listOf(legacyPlaylistRow(70, 1, song).withSongReference(song)),
            historyRows = listOf(legacyHistoryRow(song).withSongReference(song))
        )

        val plan = SongReferenceReconciliationPlanner.plan(
            SongReferenceIndex.build(listOf(song)),
            rows
        )

        assertEquals(0, plan.writeCount)
        assertTrue(plan.favorites.rows.isEmpty())
        assertTrue(plan.playlists.rows.isEmpty())
        assertTrue(plan.history.rows.isEmpty())
    }

    @Test
    fun nullBlankAndZeroNormalizationIsStable() {
        val original = SongReference(
            mediaStoreId = 0,
            volumeName = "   ",
            contentUri = "  content://song/1  ",
            relativePath = "  Music/  ",
            displayName = "  song.flac  ",
            fileSizeBytes = -1,
            dateModifiedEpochSeconds = -2,
            duration = -3,
            title = "  Title  ",
            portableKeyVersion = 0
        )

        val normalized = original.normalizedForPersistence()

        assertNull(normalized.mediaStoreId)
        assertEquals("", normalized.volumeName)
        assertEquals("content://song/1", normalized.contentUri)
        assertEquals("Music/", normalized.relativePath)
        assertEquals("song.flac", normalized.displayName)
        assertEquals(0L, normalized.fileSizeBytes)
        assertEquals(0L, normalized.dateModifiedEpochSeconds)
        assertEquals(0L, normalized.duration)
        assertEquals("Title", normalized.title)
        assertEquals(SongIdentity.PORTABLE_KEY_VERSION, normalized.portableKeyVersion)
        assertEquals(normalized, normalized.normalizedForPersistence())
    }

    @Test
    fun representativePassInspectsEachRowOnce() {
        val songs = (1L..120L).map(::song)
        val rows = PersistedSongReferenceRows(
            favorites = songs.map { legacyFavorite(it).withSongReference(it) },
            playlistRows = songs.mapIndexed { index, value ->
                legacyPlaylistRow(index.toLong() + 1, index, value).withSongReference(value)
            },
            historyRows = songs.map { legacyHistoryRow(it).withSongReference(it) }
        )

        val plan = SongReferenceReconciliationPlanner.plan(
            SongReferenceIndex.build(songs),
            rows
        )

        assertEquals(360, plan.inspectedRowCount)
        assertEquals(0, plan.writeCount)
    }

    private fun applyPlan(
        original: PersistedSongReferenceRows,
        plan: SongReferenceReconciliationPlan
    ): PersistedSongReferenceRows {
        val favorites = original.favorites.associateByTo(linkedMapOf()) { it.referenceKey }
        plan.favorites.oldReferenceKeys.forEach(favorites::remove)
        plan.favorites.rows.forEach { favorites[it.referenceKey] = it }

        val playlistRows = original.playlistRows.associateByTo(linkedMapOf()) {
            it.playlistSongId
        }
        plan.playlists.rows.forEach { playlistRows[it.playlistSongId] = it }

        val historyRows = original.historyRows.associateByTo(linkedMapOf()) { it.referenceKey }
        plan.history.oldReferenceKeys.forEach(historyRows::remove)
        plan.history.rows.forEach { historyRows[it.referenceKey] = it }
        return PersistedSongReferenceRows(
            favorites = favorites.values.toList(),
            playlistRows = playlistRows.values.toList(),
            historyRows = historyRows.values.toList()
        )
    }

    private fun legacyFavorite(song: Song) = FavoriteSongEntity(
        referenceKey = "legacy:${song.stableKey()}",
        songKey = song.stableKey(),
        title = song.title,
        artist = song.artist,
        album = song.album,
        duration = song.duration,
        createdAt = song.id,
        mediaStoreId = null,
        volumeName = "",
        contentUri = "",
        relativePath = "",
        displayName = "",
        fileSizeBytes = 0,
        dateModifiedEpochSeconds = 0,
        albumArtist = "",
        portableKey = "",
        portableKeyVersion = SongIdentity.PORTABLE_KEY_VERSION
    )

    private fun legacyPlaylistRow(id: Long, position: Int, song: Song) = PlaylistSongEntity(
        playlistSongId = id,
        playlistId = 1,
        songKey = song.stableKey(),
        position = position,
        title = song.title,
        artist = song.artist,
        album = song.album,
        duration = song.duration,
        addedAt = id,
        mediaStoreId = null,
        volumeName = "",
        contentUri = "",
        relativePath = "",
        displayName = "",
        fileSizeBytes = 0,
        dateModifiedEpochSeconds = 0,
        albumArtist = "",
        portableKey = "",
        portableKeyVersion = SongIdentity.PORTABLE_KEY_VERSION
    )

    private fun legacyHistoryRow(song: Song) = SongPlayStatsEntity(
        referenceKey = "legacy:${song.stableKey()}",
        songKey = song.stableKey(),
        title = song.title,
        artist = song.artist,
        album = song.album,
        duration = song.duration,
        playCount = 3,
        firstPlayedAt = 10,
        lastPlayedAt = 20,
        mediaStoreId = null,
        volumeName = "",
        contentUri = "",
        relativePath = "",
        displayName = "",
        fileSizeBytes = 0,
        dateModifiedEpochSeconds = 0,
        albumArtist = "",
        portableKey = "",
        portableKeyVersion = SongIdentity.PORTABLE_KEY_VERSION
    )

    private fun song(id: Long, relativePath: String = "Music/"): Song {
        val uri = mock(Uri::class.java)
        doReturn("content://media/external/audio/$id").`when`(uri).toString()
        return Song(
            id = id,
            title = "Title $id",
            artist = "Artist",
            album = "Album",
            trackNumber = id.toInt(),
            duration = 180_000,
            uri = uri,
            filePath = "/storage/$relativePath$id.flac",
            folderPath = "/storage/${relativePath.trimEnd('/')}",
            albumArtUri = null,
            volumeName = "external_primary",
            relativePath = relativePath,
            displayName = "$id.flac",
            fileSizeBytes = 1_000 + id,
            dateModifiedEpochSeconds = 1_700_000_000
        )
    }
}

private class CountingFavoriteDao(
    val rows: MutableList<FavoriteSongEntity>
) : FavoriteSongDao {
    var readCount = 0
    var batchWriteCount = 0

    override suspend fun getAllFavorites(): List<FavoriteSongEntity> {
        readCount += 1
        return rows.toList()
    }

    override suspend fun applyReferenceBackfill(
        oldReferenceKeys: List<String>,
        favorites: List<FavoriteSongEntity>
    ) {
        batchWriteCount += 1
        deleteFavoritesByReferenceKeys(oldReferenceKeys)
        insertFavorites(favorites)
    }

    override suspend fun insertFavorite(favoriteSong: FavoriteSongEntity) {
        rows.removeAll { it.referenceKey == favoriteSong.referenceKey }
        rows += favoriteSong
    }

    override suspend fun insertFavorites(favorites: List<FavoriteSongEntity>) {
        favorites.forEach { insertFavorite(it) }
    }

    override suspend fun deleteFavoritesByReferenceKeys(referenceKeys: List<String>) {
        rows.removeAll { it.referenceKey in referenceKeys }
    }

    override suspend fun countFavoriteByReferenceKey(referenceKey: String): Int =
        rows.count { it.referenceKey == referenceKey }

    override suspend fun deleteFavoriteByReferenceKey(referenceKey: String) {
        rows.removeAll { it.referenceKey == referenceKey }
    }

    override suspend fun deleteFavoriteByKey(songKey: String) {
        rows.removeAll { it.songKey == songKey }
    }

    override suspend fun deleteAllFavorites() {
        rows.clear()
    }
}
