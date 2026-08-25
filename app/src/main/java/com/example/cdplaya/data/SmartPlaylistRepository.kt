package com.example.cdplaya.data

import androidx.room.withTransaction
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.CachedSongEntity
import com.example.cdplaya.data.local.GeneratedPlaylistSongEntity
import com.example.cdplaya.data.local.GeneratedPlaylistStateEntity
import com.example.cdplaya.data.local.PlaylistEntity
import com.example.cdplaya.data.local.SmartPlaylistCachedSongEntity
import com.example.cdplaya.data.local.SmartPlaylistCandidateRow
import com.example.cdplaya.data.local.SmartPlaylistDefinitionEntity
import com.example.cdplaya.data.local.SmartPlaylistDependencies
import com.example.cdplaya.data.local.SmartPlaylistQueries
import com.example.cdplaya.data.local.SmartPlaylistResolutionStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SmartPlaylistRepository(
    private val database: AppDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val dao = database.smartPlaylistDao()
    private val playlistDao = database.playlistDao()
    private val resolutionMutex = Mutex()

    suspend fun createSmartPlaylist(
        name: String,
        draft: SmartPlaylistDraft,
        folderId: Long? = null
    ): SmartPlaylistDefinition? = createDefinition(
        name = name,
        draft = draft,
        folderId = folderId,
        generated = null
    )

    suspend fun createGeneratedPlaylist(
        name: String,
        templateKey: String,
        draft: SmartPlaylistDraft,
        membershipMode: String = GeneratedPlaylistMembershipMode.SNAPSHOT,
        refreshPolicy: String = GeneratedPlaylistRefreshPolicy.MANUAL,
        refreshIntervalMillis: Long? = null,
        folderId: Long? = null
    ): SmartPlaylistDefinition? {
        require(templateKey.isNotBlank())
        require(membershipMode in setOf(
            GeneratedPlaylistMembershipMode.LIVE_DERIVED,
            GeneratedPlaylistMembershipMode.SNAPSHOT
        ))
        require(refreshPolicy.isNotBlank())
        require(refreshIntervalMillis == null || refreshIntervalMillis > 0L)
        return createDefinition(
            name = name,
            draft = draft,
            folderId = folderId,
            generated = NewGeneratedState(
                templateKey,
                membershipMode,
                refreshPolicy,
                refreshIntervalMillis
            )
        )
    }

    suspend fun updateSmartPlaylistDefinition(
        playlistId: Long,
        draft: SmartPlaylistDraft
    ): SmartPlaylistDefinition? {
        val validated = draft.validated()
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return null
        if (PlaylistType.fromStorage(playlist.type) != PlaylistType.SMART) return null
        val updatedAt = nowMillis()
        database.withTransaction {
            dao.upsertDefinition(validated.toEntity(playlistId, updatedAt))
            dao.deleteCachedSongs(playlistId)
            dao.upsertResolutionState(
                SmartPlaylistResolutionStateEntity(playlistId = playlistId, isDirty = true)
            )
            playlistDao.updatePlaylistTimestamp(playlistId, updatedAt)
        }
        return SmartPlaylistDefinition(playlistId, validated, updatedAt)
    }

    suspend fun loadSmartPlaylistDefinition(playlistId: Long): SmartPlaylistDefinition? =
        dao.getDefinition(playlistId)?.toDomain()

    fun observeSmartPlaylistDefinition(playlistId: Long): Flow<SmartPlaylistDefinition?> =
        dao.observeDefinition(playlistId).map { it?.toDomain() }

    /** Dependency-filtered staleness signal for Session 2 count/artwork/detail consumers. */
    fun observeResolutionStaleness(playlistId: Long): Flow<Boolean> =
        dao.observeResolutionState(playlistId).map { state -> state?.isDirty ?: true }

    suspend fun previewMatchingSongs(draft: SmartPlaylistDraft): SmartPlaylistResolution =
        evaluate(draft.validated(), playlistId = null)

    suspend fun evaluateMatchingSongs(draft: SmartPlaylistDraft): List<Song> =
        previewMatchingSongs(draft).songs

    suspend fun getMatchingCount(draft: SmartPlaylistDraft): Int =
        dao.count(SmartPlaylistQueries.count(draft.validated(), nowMillis())).count

    suspend fun getQualifiedCount(playlistId: Long): Int {
        val definition = requireNotNull(dao.getDefinition(playlistId)).toDomain()
        return getMatchingCount(definition.draft)
    }

    suspend fun getMatchingCount(playlistId: Long): Int =
        resolveFinalMembership(playlistId).count

    /**
     * Returns an immutable list snapshot suitable for creating a playback queue. Later database
     * invalidations never mutate a queue already built from this return value.
     */
    suspend fun resolveFinalMembership(playlistId: Long): SmartPlaylistResolution =
        resolutionMutex.withLock { resolveFinalMembershipLocked(playlistId) }

    private suspend fun resolveFinalMembershipLocked(playlistId: Long): SmartPlaylistResolution {
        require(playlistId > 0L)
        dao.getGeneratedState(playlistId)?.let { generated ->
            if (generated.membershipMode == GeneratedPlaylistMembershipMode.SNAPSHOT) {
                return resolveGeneratedSnapshot(generated)
            }
        }
        val definition = requireNotNull(dao.getDefinition(playlistId)) {
            "Playlist $playlistId has no Smart Playlist definition."
        }.toDomain()
        val now = nowMillis()
        val state = dao.getResolutionState(playlistId)
        val cacheIsFresh = state != null && !state.isDirty &&
            (state.validUntil == null || now < state.validUntil)
        if (cacheIsFresh) {
            return SmartPlaylistResolution(
                playlistId = playlistId,
                songs = dao.getCachedSongs(playlistId).map(CachedSongEntity::toSong),
                resolvedAt = state.resolvedAt ?: now,
                fromDerivedCache = true
            )
        }

        val resolution = evaluate(definition.draft, playlistId)
        val validUntil = if (SmartPlaylistDependencies.isTimeSensitive(definition.draft)) {
            resolution.resolvedAt
        } else {
            null
        }
        database.withTransaction {
            dao.deleteCachedSongs(playlistId)
            dao.insertCachedSongs(
                resolution.songs.mapIndexed { position, song ->
                    SmartPlaylistCachedSongEntity(
                        playlistId = playlistId,
                        position = position,
                        mediaStoreId = song.id,
                        volumeName = song.volumeName
                    )
                }
            )
            dao.upsertResolutionState(
                SmartPlaylistResolutionStateEntity(
                    playlistId = playlistId,
                    isDirty = validUntil != null,
                    resolvedAt = resolution.resolvedAt,
                    validUntil = validUntil,
                    resultCount = resolution.count
                )
            )
        }
        return resolution
    }

    suspend fun getMembershipBehavior(playlistId: Long): PlaylistMembershipBehavior {
        val playlist = requireNotNull(playlistDao.getPlaylistById(playlistId))
        val generated = dao.getGeneratedState(playlistId)
        return when {
            PlaylistType.fromStorage(playlist.type) == PlaylistType.MANUAL ->
                PlaylistMembershipBehavior.MANUAL
            generated?.membershipMode == GeneratedPlaylistMembershipMode.LIVE_DERIVED ->
                PlaylistMembershipBehavior.GENERATED_SMART_LIVE
            generated != null -> PlaylistMembershipBehavior.GENERATED_SMART_SNAPSHOT
            else -> PlaylistMembershipBehavior.USER_SMART_LIVE
        }
    }

    suspend fun loadGeneratedPlaylistState(playlistId: Long): GeneratedPlaylistState? =
        dao.getGeneratedState(playlistId)?.toDomain()

    suspend fun refreshGeneratedSnapshot(playlistId: Long): SmartPlaylistResolution {
        val generated = requireNotNull(dao.getGeneratedState(playlistId)) {
            "Playlist $playlistId is not a generated Smart Playlist."
        }
        require(generated.membershipMode == GeneratedPlaylistMembershipMode.SNAPSHOT) {
            "Live-derived generated playlists do not have durable snapshot membership."
        }
        val definition = requireNotNull(dao.getDefinition(playlistId)).toDomain()
        val evaluated = evaluate(definition.draft, playlistId)
        val refreshedAt = evaluated.resolvedAt
        database.withTransaction {
            dao.deleteGeneratedSongs(playlistId)
            dao.insertGeneratedSongs(evaluated.songs.mapIndexed { position, song ->
                song.toGeneratedEntity(playlistId, position)
            })
            dao.upsertGeneratedState(generated.copy(lastRefreshedAt = refreshedAt))
        }
        return evaluated.copy(generatedSnapshot = true)
    }

    private suspend fun createDefinition(
        name: String,
        draft: SmartPlaylistDraft,
        folderId: Long?,
        generated: NewGeneratedState?
    ): SmartPlaylistDefinition? {
        val trimmedName = name.trim()
        if (trimmedName.isBlank() || playlistDao.countPlaylistsWithName(trimmedName) > 0) return null
        if (folderId != null && playlistDao.getPlaylistFolderById(folderId) == null) return null
        val validated = draft.validated()
        val now = nowMillis()
        var playlistId = 0L
        database.withTransaction {
            playlistId = playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = trimmedName,
                    type = PlaylistType.SMART.name,
                    folderId = folderId,
                    createdAt = now,
                    updatedAt = now
                )
            )
            dao.upsertDefinition(validated.toEntity(playlistId, now))
            dao.upsertResolutionState(SmartPlaylistResolutionStateEntity(playlistId))
            generated?.let {
                dao.upsertGeneratedState(
                    GeneratedPlaylistStateEntity(
                        playlistId = playlistId,
                        templateKey = it.templateKey,
                        membershipMode = it.membershipMode,
                        refreshPolicy = it.refreshPolicy,
                        refreshIntervalMillis = it.refreshIntervalMillis,
                        lastRefreshedAt = null,
                        snapshotVersion = CURRENT_GENERATED_SNAPSHOT_VERSION
                    )
                )
            }
        }
        return SmartPlaylistDefinition(playlistId, validated, now)
    }

    private suspend fun evaluate(
        draft: SmartPlaylistDraft,
        playlistId: Long?
    ): SmartPlaylistResolution {
        val resolvedAt = nowMillis()
        val songs = dao.evaluate(SmartPlaylistQueries.resolve(draft, resolvedAt))
            .map(SmartPlaylistCandidateRow::toSong)
        return SmartPlaylistResolution(
            playlistId = playlistId,
            songs = songs.toList(),
            resolvedAt = resolvedAt,
            fromDerivedCache = false
        )
    }

    private suspend fun resolveGeneratedSnapshot(
        state: GeneratedPlaylistStateEntity
    ): SmartPlaylistResolution {
        val library = database.cachedSongDao().getAllCachedSongs().map(CachedSongEntity::toSong)
        val index = SongReferenceIndex.build(library)
        val songs = dao.getGeneratedSongs(state.playlistId).mapNotNull { row ->
            (index.resolve(row.toSongReference()) as? SongReferenceResolution.Resolved)?.song
        }
        return SmartPlaylistResolution(
            playlistId = state.playlistId,
            songs = songs.toList(),
            resolvedAt = state.lastRefreshedAt ?: 0L,
            fromDerivedCache = false,
            generatedSnapshot = true
        )
    }

    private data class NewGeneratedState(
        val templateKey: String,
        val membershipMode: String,
        val refreshPolicy: String,
        val refreshIntervalMillis: Long?
    )
}

internal object SmartPlaylistRuleJson {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(rules: List<SmartPlaylistRule>): String = json.encodeToString(rules)
    fun decode(value: String): List<SmartPlaylistRule> =
        json.decodeFromString<List<SmartPlaylistRule>>(value)
}

private fun SmartPlaylistDraft.toEntity(
    playlistId: Long,
    updatedAt: Long
) = SmartPlaylistDefinitionEntity(
    playlistId = playlistId,
    matchMode = matchMode,
    rulesJson = SmartPlaylistRuleJson.encode(rules),
    sortField = sortField,
    sortDirection = sortDirection,
    resultLimit = resultLimit,
    definitionVersion = definitionVersion,
    dependencyMask = SmartPlaylistDependencies.forDefinition(this),
    updatedAt = updatedAt
)

internal fun SmartPlaylistDefinitionEntity.toDomain() = SmartPlaylistDefinition(
    playlistId = playlistId,
    draft = SmartPlaylistDraft(
        matchMode = matchMode,
        rules = SmartPlaylistRuleJson.decode(rulesJson),
        sortField = sortField,
        sortDirection = sortDirection,
        resultLimit = resultLimit,
        definitionVersion = definitionVersion
    ).validated(),
    updatedAt = updatedAt
)

private fun SmartPlaylistCandidateRow.toSong(): Song = CachedSongEntity(
    mediaStoreId = mediaStoreId,
    title = title,
    artist = artist,
    album = album,
    trackNumber = trackNumber,
    duration = duration,
    uriString = uriString,
    filePath = filePath,
    folderPath = folderPath,
    albumArtUriString = albumArtUriString,
    albumArtist = albumArtist,
    volumeName = volumeName,
    displayName = displayName,
    relativePath = relativePath,
    fileSizeBytes = fileSizeBytes,
    dateAddedEpochSeconds = dateAddedEpochSeconds,
    dateModifiedEpochSeconds = dateModifiedEpochSeconds,
    year = year,
    artworkEnrichmentVersion = artworkEnrichmentVersion,
    cachedAt = cachedAt
).toSong()

internal fun GeneratedPlaylistStateEntity.toDomain() = GeneratedPlaylistState(
    playlistId = playlistId,
    templateKey = templateKey,
    membershipMode = membershipMode,
    refreshPolicy = refreshPolicy,
    refreshIntervalMillis = refreshIntervalMillis,
    lastRefreshedAt = lastRefreshedAt,
    snapshotVersion = snapshotVersion
)

internal fun GeneratedPlaylistSongEntity.toSongReference() = SongReference(
    mediaStoreId = mediaStoreId,
    volumeName = volumeName,
    contentUri = contentUri,
    relativePath = relativePath,
    displayName = displayName,
    fileSizeBytes = fileSizeBytes,
    dateModifiedEpochSeconds = dateModifiedEpochSeconds,
    duration = duration,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    legacyStableKey = songKey,
    portableKey = portableKey,
    portableKeyVersion = portableKeyVersion
)

internal fun Song.toGeneratedEntity(playlistId: Long, position: Int): GeneratedPlaylistSongEntity {
    val reference = toSongReference().normalizedForPersistence()
    return GeneratedPlaylistSongEntity(
        playlistId = playlistId,
        position = position,
        songKey = reference.legacyStableKey,
        title = reference.title,
        artist = reference.artist,
        album = reference.album,
        duration = reference.duration,
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
