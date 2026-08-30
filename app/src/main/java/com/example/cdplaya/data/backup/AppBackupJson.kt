package com.example.cdplaya.data.backup

import com.example.cdplaya.data.identityNormalized
import com.example.cdplaya.data.portableMetadataKey
import com.example.cdplaya.data.PlaylistType
import com.example.cdplaya.data.SmartPlaylistDraft
import com.example.cdplaya.data.preferences.CrossfadePreferences
import com.example.cdplaya.player.equalizer.GraphicEqualizerPresets
import com.example.cdplaya.player.equalizer.EqualizerMode
import com.example.cdplaya.player.equalizer.parametric.MAX_PARAMETRIC_FILTER_COUNT
import com.example.cdplaya.player.equalizer.parametric.ParametricEqualizerPreset
import com.example.cdplaya.player.equalizer.parametric.ParametricEqualizerState
import com.example.cdplaya.player.equalizer.parametric.ParametricFilter
import com.example.cdplaya.player.equalizer.parametric.ParametricFilterType
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

object AppBackupJson {
    const val CURRENT_SCHEMA_VERSION = 15
    private const val OLDEST_SUPPORTED_SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeBackup(backup: AppBackup): String = json.encodeToString(backup.sanitizedForExport())

    @OptIn(ExperimentalSerializationApi::class)
    fun encodeBackup(backup: AppBackup, output: OutputStream) {
        json.encodeToStream(backup.sanitizedForExport(), output)
    }

    fun decodeBackup(jsonText: String): AppBackup {
        val backup = try {
            json.decodeFromString<AppBackup>(jsonText)
        } catch (exception: SerializationException) {
            throw IllegalArgumentException("Invalid Sazanami backup JSON.", exception)
        }

        return migrateAndValidate(backup)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun decodeBackup(input: InputStream): AppBackup {
        val backup = try {
            json.decodeFromStream<AppBackup>(input)
        } catch (exception: SerializationException) {
            throw IllegalArgumentException("Invalid Sazanami backup JSON.", exception)
        }
        return migrateAndValidate(backup)
    }

    private fun migrateAndValidate(backup: AppBackup): AppBackup {
        require(backup.schemaVersion in OLDEST_SUPPORTED_SCHEMA_VERSION..CURRENT_SCHEMA_VERSION) {
            "Unsupported Sazanami backup schema version ${backup.schemaVersion}; " +
                    "supported versions are $OLDEST_SUPPORTED_SCHEMA_VERSION through " +
                    "$CURRENT_SCHEMA_VERSION."
        }

        var migrated = backup
        if (migrated.schemaVersion == 1) {
            migrated = migrateV1ToV2(migrated)
        }
        if (migrated.schemaVersion == 2) {
            migrated = migrateV2ToV3(migrated)
        }
        if (migrated.schemaVersion == 3) {
            migrated = migrateV3ToV4(migrated)
        }
        if (migrated.schemaVersion == 4) {
            migrated = migrateV4ToV5(migrated)
        }
        if (migrated.schemaVersion == 5) {
            migrated = migrateV5ToV6(migrated)
        }
        if (migrated.schemaVersion == 6) {
            migrated = migrateV6ToV7(migrated)
        }
        if (migrated.schemaVersion == 7) {
            migrated = migrateV7ToV8(migrated)
        }
        if (migrated.schemaVersion == 8) {
            migrated = migrateV8ToV9(migrated)
        }
        if (migrated.schemaVersion == 9) {
            migrated = migrateV9ToV10(migrated)
        }
        if (migrated.schemaVersion == 10) {
            migrated = migrateV10ToV11(migrated)
        }
        if (migrated.schemaVersion == 11) {
            migrated = migrateV11ToV12(migrated)
        }
        if (migrated.schemaVersion == 12) {
            migrated = migrateV12ToV13(migrated)
        }
        if (migrated.schemaVersion == 13) {
            migrated = migrateV13ToV14(migrated)
        }
        if (migrated.schemaVersion == 14) {
            migrated = migrateV14ToV15(migrated)
        }
        migrated = migrated.copy(
            preferences = migrated.preferences.copy(
                crossfadeDurationMs = CrossfadePreferences.clampDurationMs(
                    migrated.preferences.crossfadeDurationMs
                )
            )
        )
        validateEqualizerBackup(migrated.preferences.equalizer)
        val history = requireNotNull(migrated.canonicalListeningHistory) {
            "Sazanami backup schema 10 requires canonical listening history."
        }
        ListeningHistoryBackupValidator.validate(history)
        SongRatingBackupValidator.validate(migrated.songRatings, history)
        validateSmartPlaylists(migrated.playlists)
        return migrated
    }

    private fun migrateV1ToV2(backup: AppBackup): AppBackup {
        return backup.copy(
            schemaVersion = 2,
            preferences = backup.preferences.copy(
                modernArtworkTransitionStyle = "slide",
                modernSeekbarStyle = "classic_bar",
                playerThemeTokenOverrides = emptyMap(),
                songsViewMode = "list",
                albumsViewMode = "list",
                artistsViewMode = "list",
                songsGridColumnCount = 2,
                albumsGridColumnCount = 2,
                artistsGridColumnCount = 2
            )
        )
    }

    private fun migrateV2ToV3(backup: AppBackup): AppBackup {
        return backup.copy(
            schemaVersion = 3,
            favorites = backup.favorites.map { favorite ->
                favorite.copy(reference = favorite.reference ?: favorite.legacyReference())
            },
            playlists = backup.playlists.map { playlist ->
                playlist.copy(
                    songs = playlist.songs.map { song ->
                        song.copy(reference = song.reference ?: song.legacyReference())
                    }
                )
            },
            listeningHistory = backup.listeningHistory.map { history ->
                history.copy(reference = history.reference ?: history.legacyReference())
            }
        )
    }

    private fun migrateV3ToV4(backup: AppBackup): AppBackup {
        return backup.copy(
            schemaVersion = 4,
            preferences = backup.preferences.copy(
                equalizer = BackupEqualizerPreferences()
            )
        )
    }

    private fun migrateV4ToV5(backup: AppBackup): AppBackup {
        return backup.copy(
            schemaVersion = 5,
            preferences = backup.preferences.copy(
                equalizer = backup.preferences.equalizer.copy(
                    limiterEnabled = false,
                    limiterCeilingDbfs = -1.0
                )
            )
        )
    }

    private fun migrateV5ToV6(backup: AppBackup): AppBackup =
        backup.copy(
            schemaVersion = 6,
            preferences = backup.preferences.copy(
                equalizer = backup.preferences.equalizer.copy(
                    mode = EqualizerMode.GRAPHIC.name,
                    parametricPreampDb = 0.0,
                    parametricAutomaticHeadroomEnabled = true,
                    parametricFilters = emptyList(),
                    parametricUserPresets = emptyList()
                )
            )
        )

    private fun migrateV6ToV7(backup: AppBackup): AppBackup {
        val identities = mutableListOf<BackupListeningTrackIdentity>()
        val bindings = mutableListOf<BackupLocalTrackBinding>()
        val baselines = mutableListOf<BackupLegacyListeningBaseline>()
        backup.listeningHistory.forEachIndexed { index, entry ->
            val backupId = index.toLong() + 1L
            val reference = entry.reference ?: entry.legacyReference()
            val title = reference.title.ifBlank { entry.title }
            val artist = reference.artist.ifBlank { entry.artist }
            val album = reference.album.ifBlank { entry.album }
            val duration = reference.duration.takeIf { it > 0L }
                ?: entry.duration.takeIf { it > 0L }
            val portableKey = reference.portableKey.takeIf { it.isNotBlank() }
                ?: portableMetadataKey(title, artist, album, duration ?: 0L)
            identities += BackupListeningTrackIdentity(
                backupIdentityId = backupId,
                titleSnapshot = title,
                artistSnapshot = artist,
                albumSnapshot = album,
                albumArtistSnapshot = reference.albumArtist.takeIf { it.isNotBlank() },
                durationMsSnapshot = duration,
                normalizedTitle = title.identityNormalized(),
                normalizedArtist = artist.identityNormalized(),
                normalizedAlbum = album.identityNormalized(),
                metadataKey = portableKey,
                metadataKeyVersion = reference.portableKeyVersion.takeIf { it > 0 } ?: 1,
                createdAt = backup.createdAt,
                updatedAt = backup.createdAt
            )
            val uniqueReferenceKey = "backup:v6:$backupId:${reference.restoredReferenceKey()}"
            bindings += BackupLocalTrackBinding(
                backupBindingId = backupId,
                trackIdentityBackupId = backupId,
                referenceKey = uniqueReferenceKey,
                mediaStoreId = null,
                volumeName = null,
                contentUri = null,
                relativePath = reference.relativePath.takeIf { it.isNotBlank() },
                displayName = reference.displayName.takeIf { it.isNotBlank() },
                absolutePath = null,
                fileSizeBytes = reference.fileSizeBytes.takeIf { it > 0L },
                dateModifiedEpochSeconds = null,
                durationMsSnapshot = duration,
                legacyStableKey = reference.legacyStableKey.ifBlank { entry.songKey }
                    .takeIf { it.isNotBlank() },
                portableKey = portableKey,
                portableKeyVersion = reference.portableKeyVersion.takeIf { it > 0 } ?: 1,
                firstSeenAt = backup.createdAt,
                lastSeenAt = backup.createdAt,
                missingSince = backup.createdAt
            )
            baselines += BackupLegacyListeningBaseline(
                trackIdentityBackupId = backupId,
                historicalPlayCount = entry.playCount,
                firstKnownPlayedAt = entry.firstPlayedAt,
                lastKnownPlayedAt = entry.lastPlayedAt,
                legacyReferenceKey = "backup:v6:baseline:$backupId:${reference.restoredReferenceKey()}",
                migratedAt = backup.createdAt
            )
        }
        val history = BackupListeningHistoryV2(
            identities = identities,
            bindings = bindings,
            baselines = baselines,
            events = emptyList()
        ).let { it.copy(summary = it.recordsSummary()) }
        return backup.copy(
            schemaVersion = 7,
            canonicalListeningHistory = history
        )
    }

    private fun migrateV7ToV8(backup: AppBackup): AppBackup = backup.copy(
        schemaVersion = 8,
        songRatings = BackupSongRatings()
    )

    private fun migrateV8ToV9(backup: AppBackup): AppBackup {
        val history = requireNotNull(backup.canonicalListeningHistory) {
            "Sazanami backup schema 8 requires canonical listening history."
        }
        val importedSources = history.events.map { it.source }.filter { it != "cdplaya" }.distinct()
        val sourceIds = importedSources.mapIndexed { index, source -> source to index.toLong() + 1L }.toMap()
        val sources = importedSources.map { source ->
            BackupListeningImportSource(
                backupSourceProfileId = sourceIds.getValue(source),
                stableUuid = "legacy-unscoped:$source", sourceType = source,
                displayLabel = "Legacy unscoped $source", accountIdentityDigest = null,
                createdAt = history.events.filter { it.source == source }.minOfOrNull { it.createdAt } ?: backup.createdAt,
                updatedAt = history.events.filter { it.source == source }.maxOfOrNull { it.createdAt } ?: backup.createdAt
            )
        }
        val legacyGroups = history.events.filter { it.source != "cdplaya" && it.importBatchId != null }
            .groupBy { it.source to requireNotNull(it.importBatchId) }
        val batches = legacyGroups.entries.mapIndexed { index, (key, events) ->
            BackupListeningImportBatch(
                backupBatchId = index.toLong() + 1L, stableUuid = "legacy-batch:${key.first}:${key.second}",
                sourceProfileBackupId = sourceIds.getValue(key.first), status = "published", parserVersion = 0,
                qualificationPolicy = events.first().qualificationPolicy, qualificationRuleVersion = events.maxOf { it.qualificationRuleVersion },
                startedAt = events.minOf { it.attributionAt }, completedAt = events.maxOf { it.attributionAt },
                sourceRangeStart = events.minOf { it.attributionAt }, sourceRangeEnd = events.maxOf { it.attributionAt },
                parsedCount = events.size.toLong(), insertedCount = events.size.toLong(), duplicateCount = 0,
                ignoredCount = 0, invalidCount = 0, exactMatchCount = 0, ambiguousMatchCount = 0,
                unmatchedCount = events.size.toLong(), qualifiedCount = events.count { it.qualifiedAsPlay }.toLong(),
                failureCategory = null, createdAppVersion = "backup-v8-legacy"
            )
        }
        val batchByStable = batches.associateBy { it.stableUuid }
        val links = legacyGroups.flatMap { (key, events) ->
            val batch = batchByStable.getValue("legacy-batch:${key.first}:${key.second}")
            events.map { BackupListeningImportBatchEvent(batch.backupBatchId, it.eventUuid) }
        }
        return backup.copy(schemaVersion = 9, canonicalListeningHistory = history.copy(
            importSources = sources, importBatches = batches, batchEventObservations = links
        ).let { it.copy(summary = it.recordsSummary()) })
    }

    private fun migrateV9ToV10(backup: AppBackup): AppBackup {
        val history = requireNotNull(backup.canonicalListeningHistory) {
            "Sazanami backup schema 9 requires canonical listening history."
        }
        return backup.copy(
            schemaVersion = 10,
            canonicalListeningHistory = history.copy(
                formatVersion = BackupListeningHistoryV2.CURRENT_FORMAT_VERSION,
                reconciliations = emptyList()
            )
        )
    }

    private fun migrateV10ToV11(backup: AppBackup): AppBackup = backup.copy(
        schemaVersion = 11,
        playlists = backup.playlists.map { playlist ->
            playlist.copy(
                type = playlist.type.ifBlank { "MANUAL" },
                artworkMode = playlist.artworkMode.ifBlank { "AUTOMATIC" },
                artworkReference = playlist.artworkReference?.takeIf { it.isNotBlank() }
            )
        }
    )

    private fun migrateV11ToV12(backup: AppBackup): AppBackup = backup.copy(
        schemaVersion = 12,
        playlistFolders = emptyList(),
        playlists = backup.playlists.map { it.copy(folderId = null) }
    )

    private fun migrateV12ToV13(backup: AppBackup): AppBackup = backup.copy(
        schemaVersion = 13
    )

    private fun migrateV13ToV14(backup: AppBackup): AppBackup = backup.copy(
        schemaVersion = 14
    )

    private fun migrateV14ToV15(backup: AppBackup): AppBackup = backup.copy(
        schemaVersion = 15,
        visualAssets = emptyList()
    )

    private fun validateEqualizerBackup(
        equalizer: BackupEqualizerPreferences
    ) {
        require(equalizer.bandGainsDb.size == 10) {
            "Backup equalizer must contain exactly 10 band gains."
        }
        require(
            equalizer.preampDb.isFinite() &&
                    equalizer.preampDb in -15.0..6.0
        ) {
            "Backup equalizer preamp is invalid."
        }
        require(
            equalizer.bandGainsDb.all { gain ->
                gain.isFinite() && gain in -12.0..12.0
            }
        ) {
            "Backup equalizer band gain is invalid."
        }
        require(
            equalizer.limiterCeilingDbfs.isFinite() &&
                    equalizer.limiterCeilingDbfs in -3.0..0.0
        ) {
            "Backup limiter ceiling is invalid."
        }
        equalizer.userPresets.forEach { preset ->
            require(preset.bandGainsDb.size == 10) {
                "Backup equalizer preset must contain exactly 10 band gains."
            }
            require(
                preset.id.isNotBlank() &&
                        preset.name.isNotBlank() &&
                        preset.name.length <= 40 &&
                        preset.preampDb.isFinite() &&
                        preset.preampDb in -15.0..6.0 &&
                        preset.bandGainsDb.all { gain ->
                            gain.isFinite() && gain in -12.0..12.0
                        }
            ) {
                "Backup equalizer preset is invalid."
            }
        }
        require(
            equalizer.userPresets
                .map { preset -> preset.id }
                .distinct().size ==
                    equalizer.userPresets.size
        ) {
            "Backup equalizer preset IDs must be unique."
        }
        val presetNames = equalizer.userPresets.map { preset ->
            preset.name.trim().lowercase()
        }
        require(presetNames.distinct().size == presetNames.size) {
            "Backup equalizer preset names must be unique."
        }
        require(
            presetNames.none { name ->
                name in GraphicEqualizerPresets
                    .builtInNamesLowercase
            }
        ) {
            "Backup equalizer preset name conflicts with a built-in."
        }
        val mode = runCatching {
            EqualizerMode.valueOf(equalizer.mode)
        }.getOrNull()
        require(mode != null) {
            "Backup equalizer mode is invalid."
        }
        val activeFilters = equalizer.parametricFilters
            .map { filter -> filter.toDomain() }
        val userPresets = equalizer.parametricUserPresets.map { preset ->
            ParametricEqualizerPreset(
                id = preset.id,
                name = preset.name,
                preampDb = preset.preampDb,
                automaticHeadroomEnabled =
                    preset.automaticHeadroomEnabled,
                filters = preset.filters.map { filter ->
                    filter.toDomain()
                }
            )
        }
        require(
            activeFilters.size <= MAX_PARAMETRIC_FILTER_COUNT
        ) {
            "Backup parametric equalizer has too many filters."
        }
        ParametricEqualizerState(
            preampDb = equalizer.parametricPreampDb,
            automaticHeadroomEnabled =
                equalizer.parametricAutomaticHeadroomEnabled,
            filters = activeFilters,
            userPresets = userPresets
        )
    }

    private fun validateSmartPlaylists(playlists: List<BackupPlaylist>) {
        playlists.forEach { playlist ->
            val type = PlaylistType.fromStorage(playlist.type)
            if (type == PlaylistType.MANUAL) {
                require(playlist.smartDefinition == null && playlist.generatedState == null) {
                    "Manual playlist backup cannot contain Smart Playlist state."
                }
                return@forEach
            }
            playlist.smartDefinition?.let { definition ->
                SmartPlaylistDraft(
                    matchMode = definition.matchMode,
                    rules = definition.rules,
                    sortField = definition.sortField,
                    sortDirection = definition.sortDirection,
                    resultLimit = definition.resultLimit,
                    definitionVersion = definition.definitionVersion
                ).validated()
            }
            playlist.generatedState?.let { generated ->
                require(generated.templateKey.isNotBlank())
                require(generated.membershipMode in setOf("live_derived", "snapshot"))
                require(generated.refreshPolicy.isNotBlank())
                require(generated.refreshIntervalMillis == null ||
                    generated.refreshIntervalMillis > 0L)
                require(generated.snapshotVersion > 0)
                require(generated.songs.all { it.position >= 0 })
                require(generated.songs.map { it.position }.distinct().size == generated.songs.size)
                require(generated.membershipMode != "live_derived" || generated.songs.isEmpty())
            }
        }
    }
}

private fun AppBackup.sanitizedForExport(): AppBackup = copy(
    canonicalListeningHistory = canonicalListeningHistory
        ?: BackupListeningHistoryV2(),
    preferences = preferences.copy(
        crossfadeDurationMs = CrossfadePreferences.clampDurationMs(
            preferences.crossfadeDurationMs
        ),
        selectedLibraryFolders = preferences.selectedLibraryFolders
            .map { it.toPortableFolderSelection() }
            .filter { it.isNotBlank() },
        homePins = preferences.homePins.map { pin ->
            pin.copy(anchor = pin.anchor?.withoutAbsolutePath())
        }
    ),
    favorites = favorites.map { favorite ->
        favorite.copy(reference = favorite.reference?.withoutAbsolutePath())
    },
    playlists = playlists.map { playlist ->
        playlist.copy(
            songs = playlist.songs.map { song ->
                song.copy(reference = song.reference?.withoutAbsolutePath())
            },
            generatedState = playlist.generatedState?.let { generated ->
                generated.copy(
                    songs = generated.songs.map { song ->
                        song.copy(reference = song.reference.withoutAbsolutePath())
                    }
                )
            }
        )
    },
    listeningHistory = listeningHistory.map { history ->
        history.copy(reference = history.reference?.withoutAbsolutePath())
    }
)

private fun BackupSongReference.withoutAbsolutePath(): BackupSongReference = copy(
    relativePath = relativePath.takeUnless { it.isAbsolutePathLike() }.orEmpty()
)

private fun BackupFavoriteSong.legacyReference() = BackupSongReference(
    duration = duration,
    title = title,
    artist = artist,
    album = album,
    legacyStableKey = songKey
)

private fun BackupPlaylistSong.legacyReference() = BackupSongReference(
    duration = duration,
    title = title,
    artist = artist,
    album = album,
    legacyStableKey = songKey
)

private fun BackupListeningHistoryEntry.legacyReference() = BackupSongReference(
    duration = duration,
    title = title,
    artist = artist,
    album = album,
    legacyStableKey = songKey
)

internal fun BackupParametricFilter.toDomain(): ParametricFilter {
    val filterType = runCatching {
        ParametricFilterType.valueOf(type)
    }.getOrElse {
        throw IllegalArgumentException(
            "Unknown backup parametric filter type: $type"
        )
    }
    return when (filterType) {
        ParametricFilterType.PEAKING -> {
            require(slope == null)
            ParametricFilter.Peaking(
                id, enabled, frequencyHz,
                requireNotNull(gainDb), requireNotNull(q)
            )
        }
        ParametricFilterType.LOW_SHELF -> {
            require(q == null)
            ParametricFilter.LowShelf(
                id, enabled, frequencyHz,
                requireNotNull(gainDb), requireNotNull(slope)
            )
        }
        ParametricFilterType.HIGH_SHELF -> {
            require(q == null)
            ParametricFilter.HighShelf(
                id, enabled, frequencyHz,
                requireNotNull(gainDb), requireNotNull(slope)
            )
        }
        ParametricFilterType.LOW_PASS -> {
            require(gainDb == null && slope == null)
            ParametricFilter.LowPass(
                id, enabled, frequencyHz, requireNotNull(q)
            )
        }
        ParametricFilterType.HIGH_PASS -> {
            require(gainDb == null && slope == null)
            ParametricFilter.HighPass(
                id, enabled, frequencyHz, requireNotNull(q)
            )
        }
        ParametricFilterType.NOTCH -> {
            require(gainDb == null && slope == null)
            ParametricFilter.Notch(
                id, enabled, frequencyHz, requireNotNull(q)
            )
        }
        ParametricFilterType.BAND_PASS -> {
            require(gainDb == null && slope == null)
            ParametricFilter.BandPass(
                id, enabled, frequencyHz, requireNotNull(q)
            )
        }
    }
}
