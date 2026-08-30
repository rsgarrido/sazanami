package io.github.rsgarrido.sazanami.data

import io.github.rsgarrido.sazanami.data.backup.BackupListeningHistoryEntry
import io.github.rsgarrido.sazanami.data.backup.BackupSongReference
import io.github.rsgarrido.sazanami.data.backup.restoredReferenceKey
import io.github.rsgarrido.sazanami.data.backup.toSongReference
import io.github.rsgarrido.sazanami.data.local.SongPlayStatsDao
import io.github.rsgarrido.sazanami.data.local.SongPlayStatsEntity
import kotlin.math.max
import kotlin.math.min

class ListeningHistoryRepository(
    private val songPlayStatsDao: SongPlayStatsDao
) {
    suspend fun restoreListeningHistoryFromBackup(
        listeningHistory: List<BackupListeningHistoryEntry>
    ) {
        songPlayStatsDao.deleteAllStats()
        songPlayStatsDao.insertOrReplaceStats(listeningHistory.map { it.toLegacyEntity() })
    }

    suspend fun updateSongReferenceAfterTagEdit(
        originalSong: Song,
        editedTags: EditableSongTags
    ) {
        val updatedSong = originalSong.copy(
            title = editedTags.title.trim(),
            artist = editedTags.artist.trim(),
            album = editedTags.album.trim()
        )
        val originalIndex = SongReferenceIndex.build(listOf(originalSong))
        songPlayStatsDao.getRecentlyPlayed().forEach { stats ->
            if (originalIndex.resolve(stats.toSongReference())
                is SongReferenceResolution.Resolved
            ) {
                persistReconciledStats(stats, stats.withSongReference(updatedSong))
            }
        }
    }

    suspend fun reconcileSongReferences(songs: Collection<Song>): SongReferenceReconciliation {
        return reconcileSongReferences(SongReferenceIndex.build(songs))
    }

    internal suspend fun reconcileSongReferences(
        index: SongReferenceIndex
    ): SongReferenceReconciliation {
        val plan = SongReferenceReconciliationPlanner.planHistory(index, loadReferenceRows())
        applyReferenceBackfill(plan)
        return plan.result
    }

    internal suspend fun loadReferenceRows(): List<SongPlayStatsEntity> =
        songPlayStatsDao.getRecentlyPlayed()

    internal suspend fun applyReferenceBackfill(plan: HistoryReferenceBackfill) {
        songPlayStatsDao.applyReferenceBackfill(plan.oldReferenceKeys, plan.rows)
    }

    private suspend fun persistReconciledStats(
        old: SongPlayStatsEntity,
        updated: SongPlayStatsEntity
    ) {
        val existingTarget = if (old.referenceKey == updated.referenceKey) null else {
            songPlayStatsDao.getStatsByReferenceKey(updated.referenceKey)
        }
        val finalStats = if (existingTarget == null) updated else {
            updated.copy(
                playCount = existingTarget.playCount + old.playCount,
                firstPlayedAt = min(existingTarget.firstPlayedAt, old.firstPlayedAt),
                lastPlayedAt = max(existingTarget.lastPlayedAt, old.lastPlayedAt)
            )
        }
        songPlayStatsDao.insertOrReplaceStats(finalStats)
        if (old.referenceKey != finalStats.referenceKey) {
            songPlayStatsDao.deleteStatsByReferenceKey(old.referenceKey)
        }
    }

}

private fun BackupListeningHistoryEntry.toLegacyEntity(): SongPlayStatsEntity {
    val backupReference = reference ?: BackupSongReference(
        duration = duration,
        title = title,
        artist = artist,
        album = album,
        legacyStableKey = songKey,
        portableKey = portableMetadataKey(title, artist, album, duration).orEmpty()
    )
    val restoredReference = backupReference.toSongReference()
    return SongPlayStatsEntity(
    referenceKey = backupReference.restoredReferenceKey(),
    songKey = restoredReference.legacyStableKey.ifBlank { songKey },
    title = restoredReference.title.ifBlank { title },
    artist = restoredReference.artist.ifBlank { artist },
    album = restoredReference.album.ifBlank { album },
    duration = restoredReference.duration.takeIf { it > 0L } ?: duration,
    playCount = playCount,
    firstPlayedAt = firstPlayedAt,
    lastPlayedAt = lastPlayedAt,
    mediaStoreId = null,
    volumeName = "",
    contentUri = "",
    relativePath = restoredReference.relativePath,
    displayName = restoredReference.displayName,
    fileSizeBytes = restoredReference.fileSizeBytes,
    dateModifiedEpochSeconds = 0L,
    albumArtist = restoredReference.albumArtist,
    portableKey = restoredReference.portableKey,
    portableKeyVersion = restoredReference.portableKeyVersion
    )
}
