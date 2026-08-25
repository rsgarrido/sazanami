package com.example.cdplaya.data

import com.example.cdplaya.data.backup.BackupPlaylist
import com.example.cdplaya.data.backup.BackupPlaylistFolder
import com.example.cdplaya.data.backup.BackupPlaylistSong
import com.example.cdplaya.data.backup.BackupSongReference
import com.example.cdplaya.data.backup.toBackupSongReference
import com.example.cdplaya.data.backup.toSongReference
import com.example.cdplaya.data.local.PlaylistDao
import com.example.cdplaya.data.local.PlaylistEntity
import com.example.cdplaya.data.local.PlaylistFolderEntity
import com.example.cdplaya.data.local.PlaylistSongEntity
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PlaylistsRepository(
    private val playlistDao: PlaylistDao
) {
    private val membershipMutationMutex = Mutex()
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
                val resolvedSongs = songsByPlaylistId[playlist.playlistId]
                    .orEmpty()
                    .mapNotNull { row ->
                        (songIndex.resolve(row.toSongReference()) as? SongReferenceResolution.Resolved)
                            ?.song
                    }
                val automaticArtworkSongs = resolvedSongs
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
                    folderId = playlist.folderId,
                    createdAt = playlist.createdAt,
                    modifiedAt = playlist.updatedAt,
                    songMembershipKeys = resolvedSongs.mapTo(linkedSetOf(), Song::membershipKey),
                    automaticArtworkSongs = automaticArtworkSongs
                )
            }
        }
    }

    suspend fun getPlaylistFolders(): List<PlaylistFolder> =
        playlistDao.getPlaylistFoldersWithCount().map { folder ->
            PlaylistFolder(
                folderId = folder.folderId,
                name = folder.name,
                playlistCount = folder.playlistCount,
                createdAt = folder.createdAt,
                modifiedAt = folder.updatedAt
            )
        }

    suspend fun getPlaylistsForBackup(): List<BackupPlaylist> {
        val songsByPlaylistId = playlistDao.getAllPlaylistSongEntities()
            .groupBy { playlistSong -> playlistSong.playlistId }

        return playlistDao.getAllPlaylistEntities().map { playlist ->
            BackupPlaylist(
                playlistId = playlist.playlistId,
                name = playlist.name,
                type = playlist.type,
                artworkMode = playlist.artworkMode,
                artworkReference = playlist.artworkReference,
                folderId = playlist.folderId,
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

    suspend fun getPlaylistFoldersForBackup(): List<BackupPlaylistFolder> =
        playlistDao.getAllPlaylistFolderEntities().map { folder ->
            BackupPlaylistFolder(
                folderId = folder.folderId,
                name = folder.name,
                createdAt = folder.createdAt,
                updatedAt = folder.updatedAt
            )
        }

    suspend fun restorePlaylistsFromBackup(
        folders: List<BackupPlaylistFolder>,
        playlists: List<BackupPlaylist>
    ): Map<Long, Long> {
        playlistDao.deleteAllPlaylistSongs()
        playlistDao.deleteAllPlaylists()
        playlistDao.deleteAllPlaylistFolders()

        val restoredFolderNames = mutableListOf<String>()
        val restoredFolderIds = folders.associate { folder ->
            val uniqueName = uniquePlaylistFolderName(folder.name, restoredFolderNames)
            restoredFolderNames += uniqueName
            folder.folderId to playlistDao.insertPlaylistFolder(
                PlaylistFolderEntity(
                    name = uniqueName,
                    createdAt = folder.createdAt,
                    updatedAt = folder.updatedAt
                )
            )
        }

        val restoredNames = mutableListOf<String>()
        val restoredPlaylistIds = mutableMapOf<Long, Long>()

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
                    folderId = playlist.folderId?.let(restoredFolderIds::get),
                    createdAt = playlist.createdAt,
                    updatedAt = playlist.updatedAt
                )
            )
            playlist.playlistId?.takeIf { it > 0L }?.let { backupPlaylistId ->
                restoredPlaylistIds[backupPlaylistId] = newPlaylistId
            }

            if (playlist.songs.isNotEmpty()) {
                playlistDao.insertPlaylistSongs(
                    playlist.songs.map { playlistSong ->
                        playlistSong.toEntity(newPlaylistId)
                    }
                )
            }
        }
        return restoredPlaylistIds
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

    suspend fun createPlaylist(name: String, folderId: Long? = null): Boolean {
        return createPlaylistReturningId(name, folderId) != null
    }

    suspend fun createPlaylist(
        name: String,
        initialSongs: List<Song>,
        folderId: Long? = null
    ): Playlist? {
        val playlistId = createPlaylistReturningId(name, folderId) ?: return null
        try {
            addSongsToPlaylist(playlistId, initialSongs)
        } catch (failure: Throwable) {
            playlistDao.deletePlaylist(playlistId)
            throw failure
        }
        return getPlaylists(initialSongs).firstOrNull { it.playlistId == playlistId }
    }

    suspend fun createPlaylistReturningId(name: String, folderId: Long? = null): Long? {
        val trimmedName = name.trim()

        if (trimmedName.isBlank()) {
            return null
        }

        val playlistNameAlreadyExists =
            playlistDao.countPlaylistsWithName(trimmedName) > 0

        if (playlistNameAlreadyExists) {
            return null
        }
        if (folderId != null && playlistDao.getPlaylistFolderById(folderId) == null) return null

        val now = System.currentTimeMillis()

        return playlistDao.insertPlaylist(
            PlaylistEntity(
                name = trimmedName,
                folderId = folderId,
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
        return checkNotNull(createPlaylist(uniqueName, songs)) {
            "Unable to create imported playlist."
        }
    }

    suspend fun restorePlaylistsFromBackup(playlists: List<BackupPlaylist>): Map<Long, Long> =
        restorePlaylistsFromBackup(folders = emptyList(), playlists = playlists)

    suspend fun createPlaylistFolder(name: String): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isBlank() || playlistDao.countPlaylistFoldersWithName(trimmedName) > 0) {
            return false
        }
        val now = System.currentTimeMillis()
        playlistDao.insertPlaylistFolder(
            PlaylistFolderEntity(name = trimmedName, createdAt = now, updatedAt = now)
        )
        return true
    }

    suspend fun renamePlaylistFolder(folderId: Long, newName: String): Boolean {
        val trimmedName = newName.trim()
        if (
            trimmedName.isBlank() ||
            playlistDao.countOtherPlaylistFoldersWithName(folderId, trimmedName) > 0
        ) {
            return false
        }
        playlistDao.renamePlaylistFolder(folderId, trimmedName, System.currentTimeMillis())
        return true
    }

    suspend fun deletePlaylistFolder(folderId: Long) {
        playlistDao.deletePlaylistFolder(folderId, System.currentTimeMillis())
    }

    suspend fun movePlaylistToFolder(playlistId: Long, folderId: Long?): Boolean =
        playlistDao.movePlaylistToFolder(
            playlistId = playlistId,
            folderId = folderId,
            updatedAt = System.currentTimeMillis()
        )

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
    ): Int = membershipMutationMutex.withLock {
        if (songs.isEmpty()) {
            return@withLock 0
        }

        val distinctSongs = songs.distinctBy(Song::membershipKey)
        val incomingIndex = SongReferenceIndex.build(distinctSongs)
        val existingMembershipKeys = playlistDao.getPlaylistSongs(playlistId)
            .mapNotNullTo(mutableSetOf()) { existing ->
                (incomingIndex.resolve(existing.toSongReference()) as? SongReferenceResolution.Resolved)
                    ?.song
                    ?.membershipKey()
            }
        val songsToInsert = distinctSongs.filter { it.membershipKey() !in existingMembershipKeys }
        if (songsToInsert.isEmpty()) return@withLock 0

        val now = System.currentTimeMillis()
        val firstPosition = playlistDao.getLastPositionForPlaylist(playlistId) + 1

        val playlistSongEntities = songsToInsert.mapIndexed { index, song ->
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

        songsToInsert.size
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

private fun uniquePlaylistFolderName(
    preferredName: String,
    existingNames: Collection<String>
): String {
    val baseName = preferredName.trim().ifBlank { "Playlist Folder" }
    val lowercaseExistingNames = existingNames.mapTo(mutableSetOf()) {
        it.trim().lowercase(Locale.ROOT)
    }
    if (baseName.lowercase(Locale.ROOT) !in lowercaseExistingNames) return baseName

    var suffix = 2
    while (true) {
        val candidate = "$baseName ($suffix)"
        if (candidate.lowercase(Locale.ROOT) !in lowercaseExistingNames) return candidate
        suffix += 1
    }
}
