package com.example.cdplaya.data

import com.example.cdplaya.data.backup.BackupPlaylist
import com.example.cdplaya.data.backup.BackupPlaylistSong
import com.example.cdplaya.data.backup.BackupSongReference
import com.example.cdplaya.data.backup.toBackupSongReference
import com.example.cdplaya.data.backup.toSongReference
import com.example.cdplaya.data.local.PlaylistDao
import com.example.cdplaya.data.local.PlaylistEntity
import com.example.cdplaya.data.local.PlaylistSongEntity
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaylistsRepository(
    private val playlistDao: PlaylistDao
) {
    suspend fun getPlaylists(librarySongs: Collection<Song> = emptyList()): List<Playlist> {
        val playlistRows = playlistDao.getPlaylistsWithSongCount()
        val songsByPlaylistId: Map<Long, List<PlaylistSongEntity>> = if (librarySongs.isEmpty()) {
            emptyMap()
        } else {
            playlistDao.getAllPlaylistSongEntities().groupBy { it.playlistId }
        }

        return withContext(Dispatchers.Default) {
            val songIndex = SongReferenceIndex.build(librarySongs)
            playlistRows.map { playlist ->
                val automaticArtworkSongs = songsByPlaylistId[playlist.playlistId]
                    .orEmpty()
                    .mapNotNull { row ->
                        (songIndex.resolve(row.toSongReference()) as? SongReferenceResolution.Resolved)
                            ?.song
                    }
                    .distinctBy(::playlistArtworkAlbumKey)
                    .take(4)

                Playlist(
                    playlistId = playlist.playlistId,
                    name = playlist.name,
                    songCount = playlist.songCount,
                    totalDuration = playlist.totalDuration,
                    type = PlaylistType.fromStorage(playlist.type),
                    artworkMode = PlaylistArtworkMode.fromStorage(playlist.artworkMode),
                    artworkReference = playlist.artworkReference,
                    createdAt = playlist.createdAt,
                    modifiedAt = playlist.updatedAt,
                    automaticArtworkSongs = automaticArtworkSongs
                )
            }
        }
    }

    suspend fun getPlaylistsForBackup(): List<BackupPlaylist> {
        val songsByPlaylistId = playlistDao.getAllPlaylistSongEntities()
            .groupBy { playlistSong -> playlistSong.playlistId }

        return playlistDao.getAllPlaylistEntities().map { playlist ->
            BackupPlaylist(
                name = playlist.name,
                type = playlist.type,
                artworkMode = playlist.artworkMode,
                artworkReference = playlist.artworkReference,
                createdAt = playlist.createdAt,
                updatedAt = playlist.updatedAt,
                songs = songsByPlaylistId[playlist.playlistId]
                    .orEmpty()
                    .map { playlistSong ->
                        BackupPlaylistSong(
                            songKey = playlistSong.songKey,
                            position = playlistSong.position,
                            title = playlistSong.title,
                            artist = playlistSong.artist,
                            album = playlistSong.album,
                            duration = playlistSong.duration,
                            addedAt = playlistSong.addedAt,
                            reference = playlistSong.toSongReference().toBackupSongReference()
                        )
                    }
            )
        }
    }

    suspend fun restorePlaylistsFromBackup(playlists: List<BackupPlaylist>) {
        playlistDao.deleteAllPlaylistSongs()
        playlistDao.deleteAllPlaylists()

        val restoredNames = mutableListOf<String>()

        playlists.forEach { playlist ->
            val uniqueName = uniquePlaylistName(
                preferredName = playlist.name,
                existingNames = restoredNames
            )
            restoredNames += uniqueName

            val newPlaylistId = playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = uniqueName,
                    type = PlaylistType.fromStorage(playlist.type).name,
                    artworkMode = PlaylistArtworkMode.fromStorage(playlist.artworkMode).name,
                    artworkReference = playlist.artworkReference,
                    createdAt = playlist.createdAt,
                    updatedAt = playlist.updatedAt
                )
            )

            if (playlist.songs.isNotEmpty()) {
                playlistDao.insertPlaylistSongs(
                    playlist.songs.map { playlistSong ->
                        playlistSong.toEntity(newPlaylistId)
                    }
                )
            }
        }
    }

    suspend fun getPlaylistName(playlistId: Long): String {
        return playlistDao.getPlaylistById(playlistId)?.name ?: "Playlist"
    }

    suspend fun getPlaylistSongs(playlistId: Long): List<PlaylistSong> {
        return playlistDao.getPlaylistSongs(playlistId).map { playlistSong ->
            PlaylistSong(
                playlistSongId = playlistSong.playlistSongId,
                playlistId = playlistSong.playlistId,
                songKey = playlistSong.songKey,
                position = playlistSong.position,
                title = playlistSong.title,
                artist = playlistSong.artist,
                album = playlistSong.album,
                duration = playlistSong.duration,
                reference = playlistSong.toSongReference()
            )
        }
    }

    suspend fun createPlaylist(name: String): Boolean {
        return createPlaylistReturningId(name) != null
    }

    suspend fun createPlaylistReturningId(name: String): Long? {
        val trimmedName = name.trim()

        if (trimmedName.isBlank()) {
            return null
        }

        val playlistNameAlreadyExists =
            playlistDao.countPlaylistsWithName(trimmedName) > 0

        if (playlistNameAlreadyExists) {
            return null
        }

        val now = System.currentTimeMillis()

        return playlistDao.insertPlaylist(
            PlaylistEntity(
                name = trimmedName,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun createPlaylistWithUniqueName(
        preferredName: String,
        songs: List<Song>
    ): Playlist {
        require(songs.isNotEmpty()) {
            "Cannot create an imported playlist without songs."
        }

        val uniqueName = uniquePlaylistName(
            preferredName = preferredName,
            existingNames = getPlaylists().map { playlist ->
                playlist.name
            }
        )
        val playlistId = checkNotNull(createPlaylistReturningId(uniqueName)) {
            "Unable to create imported playlist."
        }

        try {
            addSongsToPlaylist(
                playlistId = playlistId,
                songs = songs
            )
        } catch (exception: Exception) {
            playlistDao.deletePlaylist(playlistId)
            throw exception
        }

        val now = System.currentTimeMillis()
        return Playlist(
            playlistId = playlistId,
            name = uniqueName,
            songCount = songs.size,
            totalDuration = songs.sumOf { it.duration.coerceAtLeast(0L) },
            createdAt = now,
            modifiedAt = now,
            automaticArtworkSongs = songs.distinctBy(::playlistArtworkAlbumKey).take(4)
        )
    }

    suspend fun renamePlaylist(
        playlistId: Long,
        newName: String
    ): Boolean {
        val trimmedName = newName.trim()

        if (trimmedName.isBlank()) {
            return false
        }

        val playlistNameAlreadyExists =
            playlistDao.countOtherPlaylistsWithName(
                playlistId = playlistId,
                name = trimmedName
            ) > 0

        if (playlistNameAlreadyExists) {
            return false
        }

        playlistDao.renamePlaylist(
            playlistId = playlistId,
            name = trimmedName,
            updatedAt = System.currentTimeMillis()
        )

        return true
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun setCustomArtwork(
        playlistId: Long,
        artworkReference: String
    ) {
        playlistDao.updatePlaylistArtwork(
            playlistId = playlistId,
            artworkMode = PlaylistArtworkMode.CUSTOM.name,
            artworkReference = artworkReference,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun resetArtwork(playlistId: Long) {
        playlistDao.updatePlaylistArtwork(
            playlistId = playlistId,
            artworkMode = PlaylistArtworkMode.AUTOMATIC.name,
            artworkReference = null,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun addSongToPlaylist(
        playlistId: Long,
        song: Song
    ) {
        addSongsToPlaylist(
            playlistId = playlistId,
            songs = listOf(song)
        )
    }

    suspend fun addSongsToPlaylist(
        playlistId: Long,
        songs: List<Song>
    ): Int {
        if (songs.isEmpty()) {
            return 0
        }

        val now = System.currentTimeMillis()
        val firstPosition = playlistDao.getLastPositionForPlaylist(playlistId) + 1

        val playlistSongEntities = songs.mapIndexed { index, song ->
            playlistSongEntity(
                playlistId = playlistId,
                position = firstPosition + index,
                song = song,
                addedAt = now
            )
        }

        playlistDao.insertPlaylistSongs(playlistSongEntities)

        playlistDao.updatePlaylistTimestamp(
            playlistId = playlistId,
            updatedAt = now
        )

        return songs.size
    }

    suspend fun removePlaylistSong(
        playlistId: Long,
        playlistSongId: Long
    ) {
        playlistDao.deletePlaylistSong(playlistSongId)
        playlistDao.updatePlaylistTimestamp(
            playlistId = playlistId,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun reorderPlaylistSongs(
        playlistId: Long,
        orderedPlaylistSongIds: List<Long>
    ): Boolean {
        return playlistDao.updatePlaylistSongOrder(
            playlistId = playlistId,
            orderedPlaylistSongIds = orderedPlaylistSongIds,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun updateSongReferencesAfterTagEdit(
        originalSong: Song,
        editedTags: EditableSongTags
    ) {
        val updatedSong = originalSong.copy(
            title = editedTags.title.trim(),
            artist = editedTags.artist.trim(),
            album = editedTags.album.trim()
        )
        val originalIndex = SongReferenceIndex.build(listOf(originalSong))
        val updates = playlistDao.getAllPlaylistSongEntities().mapNotNull { playlistSong ->
            if (originalIndex.resolve(playlistSong.toSongReference())
                is SongReferenceResolution.Resolved
            ) {
                playlistSong.withSongReference(updatedSong).takeIf { it != playlistSong }
            } else null
        }
        if (updates.isNotEmpty()) playlistDao.updatePlaylistSongs(updates)
    }

    suspend fun reconcileSongReferences(songs: Collection<Song>): SongReferenceReconciliation {
        return reconcileSongReferences(SongReferenceIndex.build(songs))
    }

    internal suspend fun reconcileSongReferences(
        index: SongReferenceIndex
    ): SongReferenceReconciliation {
        val plan = SongReferenceReconciliationPlanner.planPlaylists(index, loadReferenceRows())
        applyReferenceBackfill(plan)
        return plan.result
    }

    internal suspend fun loadReferenceRows(): List<PlaylistSongEntity> =
        playlistDao.getAllPlaylistSongEntities()

    internal suspend fun applyReferenceBackfill(plan: PlaylistReferenceBackfill) {
        if (plan.rows.isNotEmpty()) playlistDao.updatePlaylistSongs(plan.rows)
    }
}

private fun playlistArtworkAlbumKey(song: Song): String = buildString {
    append(song.albumArtist.ifBlank { song.artist }.trim().lowercase(Locale.ROOT))
    append('\u0000')
    append(song.album.trim().lowercase(Locale.ROOT))
    append('\u0000')
    append(song.folderPath.trim().lowercase(Locale.ROOT))
}

private fun BackupPlaylistSong.toEntity(playlistId: Long): PlaylistSongEntity {
    val backupReference = reference ?: BackupSongReference(
        duration = duration,
        title = title,
        artist = artist,
        album = album,
        legacyStableKey = songKey,
        portableKey = portableMetadataKey(title, artist, album, duration).orEmpty()
    )
    val restoredReference = backupReference.toSongReference()
    return PlaylistSongEntity(
        playlistId = playlistId,
        songKey = restoredReference.legacyStableKey.ifBlank { songKey },
        position = position,
        title = restoredReference.title.ifBlank { title },
        artist = restoredReference.artist.ifBlank { artist },
        album = restoredReference.album.ifBlank { album },
        duration = restoredReference.duration.takeIf { it > 0L } ?: duration,
        addedAt = addedAt,
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

private fun playlistSongEntity(
    playlistId: Long,
    position: Int,
    song: Song,
    addedAt: Long
): PlaylistSongEntity {
    val reference = song.toSongReference()
    return PlaylistSongEntity(
        playlistId = playlistId,
        songKey = reference.legacyStableKey,
        position = position,
        title = reference.title,
        artist = reference.artist,
        album = reference.album,
        duration = reference.duration,
        addedAt = addedAt,
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

internal fun uniquePlaylistName(
    preferredName: String,
    existingNames: Collection<String>
): String {
    val baseName = preferredName.trim().ifBlank { "Imported Playlist" }
    val lowercaseExistingNames = existingNames.mapTo(mutableSetOf()) { name ->
        name.trim().lowercase(Locale.ROOT)
    }

    if (baseName.lowercase(Locale.ROOT) !in lowercaseExistingNames) {
        return baseName
    }

    var suffix = 2

    while (true) {
        val candidate = "$baseName ($suffix)"

        if (candidate.lowercase(Locale.ROOT) !in lowercaseExistingNames) {
            return candidate
        }

        suffix += 1
    }
}
