package io.github.rsgarrido.sazanami.data

import io.github.rsgarrido.sazanami.data.backup.BackupFavoriteSong
import io.github.rsgarrido.sazanami.data.backup.BackupSongReference
import io.github.rsgarrido.sazanami.data.backup.restoredReferenceKey
import io.github.rsgarrido.sazanami.data.backup.toBackupSongReference
import io.github.rsgarrido.sazanami.data.backup.toSongReference
import io.github.rsgarrido.sazanami.data.local.FavoriteSongDao
import io.github.rsgarrido.sazanami.data.local.FavoriteSongEntity

class FavoritesRepository(
    private val favoriteSongDao: FavoriteSongDao
) {
    suspend fun getFavoriteMembershipKeys(): Set<String> {
        return favoriteSongDao.getAllFavorites().mapTo(mutableSetOf()) { it.referenceKey }
    }

    suspend fun reconcileSongReferences(songs: Collection<Song>): SongReferenceReconciliation {
        return reconcileSongReferences(SongReferenceIndex.build(songs))
    }

    internal suspend fun reconcileSongReferences(
        index: SongReferenceIndex
    ): SongReferenceReconciliation {
        val plan = SongReferenceReconciliationPlanner.planFavorites(index, loadReferenceRows())
        applyReferenceBackfill(plan)
        return plan.result
    }

    internal suspend fun loadReferenceRows(): List<FavoriteSongEntity> =
        favoriteSongDao.getAllFavorites()

    internal suspend fun applyReferenceBackfill(plan: FavoriteReferenceBackfill) {
        favoriteSongDao.applyReferenceBackfill(plan.oldReferenceKeys, plan.rows)
    }

    suspend fun getFavoritesForBackup(): List<BackupFavoriteSong> {
        return favoriteSongDao.getAllFavorites().map { favorite ->
            BackupFavoriteSong(
                songKey = favorite.songKey,
                title = favorite.title,
                artist = favorite.artist,
                album = favorite.album,
                duration = favorite.duration,
                createdAt = favorite.createdAt,
                reference = favorite.toSongReference().toBackupSongReference()
            )
        }
    }

    suspend fun restoreFavoritesFromBackup(favorites: List<BackupFavoriteSong>) {
        favoriteSongDao.deleteAllFavorites()
        favoriteSongDao.insertFavorites(favorites.map { favorite -> favorite.toLegacyEntity() })
    }

    suspend fun addFavorite(song: Song) {
        favoriteSongDao.insertFavorite(song.toFavoriteEntity())
    }

    suspend fun addFavorites(songs: List<Song>) {
        val rows = songs.distinctBy(Song::membershipKey).map { it.toFavoriteEntity() }
        if (rows.isNotEmpty()) favoriteSongDao.insertFavorites(rows)
    }

    suspend fun removeFavorite(song: Song) {
        favoriteSongDao.deleteFavoriteByReferenceKey(song.membershipKey())
        favoriteSongDao.deleteFavoriteByKey(song.stableKey())
    }

    suspend fun removeFavorites(songs: List<Song>) {
        val keys = songs.map(Song::membershipKey).distinct()
        if (keys.isNotEmpty()) favoriteSongDao.deleteFavoritesByReferenceKeys(keys)
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
        favoriteSongDao.getAllFavorites().forEach { favorite ->
            if (originalIndex.resolve(favorite.toSongReference())
                is SongReferenceResolution.Resolved
            ) {
                persistReconciledFavorite(favorite, favorite.withSongReference(updatedSong))
            }
        }
    }

    private suspend fun persistReconciledFavorite(
        old: FavoriteSongEntity,
        updated: FavoriteSongEntity
    ) {
        if (old.referenceKey != updated.referenceKey &&
            favoriteSongDao.countFavoriteByReferenceKey(updated.referenceKey) > 0
        ) {
            favoriteSongDao.deleteFavoriteByReferenceKey(old.referenceKey)
            return
        }
        favoriteSongDao.insertFavorite(updated)
        if (old.referenceKey != updated.referenceKey) {
            favoriteSongDao.deleteFavoriteByReferenceKey(old.referenceKey)
        }
    }
}

private fun Song.toFavoriteEntity(): FavoriteSongEntity {
    val reference = toSongReference()
    return FavoriteSongEntity(
        referenceKey = membershipKey(),
        songKey = reference.legacyStableKey,
        title = reference.title,
        artist = reference.artist,
        album = reference.album,
        duration = reference.duration,
        createdAt = System.currentTimeMillis(),
        mediaStoreId = reference.mediaStoreId,
        volumeName = reference.volumeName,
        contentUri = reference.contentUri,
        relativePath = reference.relativePath,
        displayName = reference.displayName,
        fileSizeBytes = reference.fileSizeBytes,
        dateModifiedEpochSeconds = reference.dateModifiedEpochSeconds,
        albumArtist = reference.albumArtist,
        portableKey = reference.portableKey,
        portableKeyVersion = reference.portableKeyVersion
    )
}

private fun BackupFavoriteSong.toLegacyEntity(): FavoriteSongEntity {
    val backupReference = reference ?: BackupSongReference(
        duration = duration,
        title = title,
        artist = artist,
        album = album,
        legacyStableKey = songKey,
        portableKey = portableMetadataKey(title, artist, album, duration).orEmpty()
    )
    val restoredReference = backupReference.toSongReference()
    return FavoriteSongEntity(
    referenceKey = backupReference.restoredReferenceKey(),
    songKey = restoredReference.legacyStableKey.ifBlank { songKey },
    title = restoredReference.title.ifBlank { title },
    artist = restoredReference.artist.ifBlank { artist },
    album = restoredReference.album.ifBlank { album },
    duration = restoredReference.duration.takeIf { it > 0L } ?: duration,
    createdAt = createdAt,
    mediaStoreId = null,
    volumeName = restoredReference.volumeName,
    contentUri = restoredReference.contentUri,
    relativePath = restoredReference.relativePath,
    displayName = restoredReference.displayName,
    fileSizeBytes = restoredReference.fileSizeBytes,
    dateModifiedEpochSeconds = 0L,
    albumArtist = restoredReference.albumArtist,
    portableKey = restoredReference.portableKey,
    portableKeyVersion = restoredReference.portableKeyVersion
    )
}
