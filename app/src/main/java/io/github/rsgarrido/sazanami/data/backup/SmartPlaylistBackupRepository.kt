package io.github.rsgarrido.sazanami.data.backup

import io.github.rsgarrido.sazanami.data.CURRENT_GENERATED_SNAPSHOT_VERSION
import io.github.rsgarrido.sazanami.data.PlaylistType
import io.github.rsgarrido.sazanami.data.SmartPlaylistDraft
import io.github.rsgarrido.sazanami.data.SmartPlaylistRuleJson
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.GeneratedPlaylistSongEntity
import io.github.rsgarrido.sazanami.data.local.GeneratedPlaylistStateEntity
import io.github.rsgarrido.sazanami.data.local.SmartPlaylistDefinitionEntity
import io.github.rsgarrido.sazanami.data.local.SmartPlaylistDependencies
import io.github.rsgarrido.sazanami.data.local.SmartPlaylistResolutionStateEntity

internal class SmartPlaylistBackupRepository(
    private val database: AppDatabase
) {
    private val dao = database.smartPlaylistDao()

    suspend fun attachTo(playlists: List<BackupPlaylist>): List<BackupPlaylist> {
        val definitions = dao.getAllDefinitions().associateBy { it.playlistId }
        val generatedStates = dao.getAllGeneratedStates().associateBy { it.playlistId }
        val generatedSongs = dao.getAllGeneratedSongs().groupBy { it.playlistId }
        return playlists.map { playlist ->
            val definition = playlist.playlistId?.let(definitions::get)
            val generated = playlist.playlistId?.let(generatedStates::get)
            playlist.copy(
                songs = if (PlaylistType.fromStorage(playlist.type) == PlaylistType.MANUAL) {
                    playlist.songs
                } else {
                    emptyList()
                },
                smartDefinition = definition?.let { row ->
                    BackupSmartPlaylistDefinition(
                        matchMode = row.matchMode,
                        rules = SmartPlaylistRuleJson.decode(row.rulesJson),
                        sortField = row.sortField,
                        sortDirection = row.sortDirection,
                        resultLimit = row.resultLimit,
                        definitionVersion = row.definitionVersion,
                        updatedAt = row.updatedAt
                    )
                },
                generatedState = generated?.let { state ->
                    BackupGeneratedPlaylistState(
                        templateKey = state.templateKey,
                        membershipMode = state.membershipMode,
                        refreshPolicy = state.refreshPolicy,
                        refreshIntervalMillis = state.refreshIntervalMillis,
                        lastRefreshedAt = state.lastRefreshedAt,
                        snapshotVersion = state.snapshotVersion,
                        songs = generatedSongs[state.playlistId].orEmpty().map { song ->
                            BackupGeneratedPlaylistSong(
                                position = song.position,
                                reference = song.toBackupReference()
                            )
                        }
                    )
                }
            )
        }
    }

    suspend fun restoreWithinTransaction(
        playlists: List<BackupPlaylist>,
        restoredPlaylistIds: Map<Long, Long>
    ) {
        playlists.forEach { backup ->
            if (PlaylistType.fromStorage(backup.type) != PlaylistType.SMART) return@forEach
            val oldId = backup.playlistId ?: return@forEach
            val playlistId = restoredPlaylistIds[oldId] ?: return@forEach
            val definition = backup.smartDefinition
            val draft = if (definition == null) {
                SmartPlaylistDraft()
            } else {
                SmartPlaylistDraft(
                    matchMode = definition.matchMode,
                    rules = definition.rules,
                    sortField = definition.sortField,
                    sortDirection = definition.sortDirection,
                    resultLimit = definition.resultLimit,
                    definitionVersion = definition.definitionVersion
                ).validated()
            }
            dao.upsertDefinition(
                SmartPlaylistDefinitionEntity(
                    playlistId = playlistId,
                    matchMode = draft.matchMode,
                    rulesJson = SmartPlaylistRuleJson.encode(draft.rules),
                    sortField = draft.sortField,
                    sortDirection = draft.sortDirection,
                    resultLimit = draft.resultLimit,
                    definitionVersion = draft.definitionVersion,
                    dependencyMask = SmartPlaylistDependencies.forDefinition(draft),
                    updatedAt = definition?.updatedAt ?: backup.updatedAt
                )
            )
            dao.upsertResolutionState(SmartPlaylistResolutionStateEntity(playlistId))
            backup.generatedState?.let { state ->
                dao.upsertGeneratedState(
                    GeneratedPlaylistStateEntity(
                        playlistId = playlistId,
                        templateKey = state.templateKey,
                        membershipMode = state.membershipMode,
                        refreshPolicy = state.refreshPolicy,
                        refreshIntervalMillis = state.refreshIntervalMillis,
                        lastRefreshedAt = state.lastRefreshedAt,
                        snapshotVersion = state.snapshotVersion
                            .takeIf { it > 0 } ?: CURRENT_GENERATED_SNAPSHOT_VERSION
                    )
                )
                dao.insertGeneratedSongs(
                    state.songs.sortedBy { it.position }.map { song ->
                        song.toEntity(playlistId)
                    }
                )
            }
        }
    }
}

private fun GeneratedPlaylistSongEntity.toBackupReference() = BackupSongReference(
    relativePath = relativePath,
    displayName = displayName,
    fileSizeBytes = fileSizeBytes,
    duration = duration,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    legacyStableKey = songKey,
    portableKey = portableKey,
    portableKeyVersion = portableKeyVersion
)

private fun BackupGeneratedPlaylistSong.toEntity(playlistId: Long): GeneratedPlaylistSongEntity =
    GeneratedPlaylistSongEntity(
        playlistId = playlistId,
        position = position,
        songKey = reference.legacyStableKey,
        title = reference.title,
        artist = reference.artist,
        album = reference.album,
        duration = reference.duration,
        mediaStoreId = null,
        volumeName = "",
        contentUri = "",
        relativePath = reference.relativePath,
        displayName = reference.displayName,
        fileSizeBytes = reference.fileSizeBytes,
        dateModifiedEpochSeconds = 0L,
        albumArtist = reference.albumArtist,
        portableKey = reference.portableKey,
        portableKeyVersion = reference.portableKeyVersion
    )
