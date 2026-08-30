package com.example.cdplaya.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.cdplaya.data.FavoritesRepository
import com.example.cdplaya.data.ArtistPictureAssignment
import com.example.cdplaya.data.ArtistPictureRepository
import com.example.cdplaya.data.artistIdentity
import com.example.cdplaya.data.FolderSelection
import com.example.cdplaya.data.ListeningHistoryRepository
import com.example.cdplaya.data.PlayerTheme
import com.example.cdplaya.data.PlaylistsRepository
import com.example.cdplaya.data.PlaylistArtworkMode
import com.example.cdplaya.data.visual.VisualAssetOwnerType
import com.example.cdplaya.data.visual.VisualAssetStore
import com.example.cdplaya.data.visual.VisualAssetVariant
import com.example.cdplaya.data.home.HomePin
import com.example.cdplaya.data.home.HomePinType
import com.example.cdplaya.data.home.sanitizeHomePins
import com.example.cdplaya.player.replaygain.ReplayGainMode
import com.example.cdplaya.data.preferences.AppPreferencesRepository
import com.example.cdplaya.data.preferences.AppPreferencesState
import com.example.cdplaya.data.preferences.CrossfadePreferences
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.player.audio.AudioOffloadPreference
import com.example.cdplaya.player.equalizer.EqualizerPreferencesState
import com.example.cdplaya.player.equalizer.EqualizerMode
import com.example.cdplaya.player.equalizer.UserEqualizerPreset
import com.example.cdplaya.player.equalizer.parametric.ParametricEqualizerPreset
import com.example.cdplaya.player.equalizer.parametric.ParametricEqualizerState
import com.example.cdplaya.player.equalizer.parametric.ParametricFilter
import com.example.cdplaya.player.equalizer.parametric.gainDbOrNull
import com.example.cdplaya.player.equalizer.parametric.qOrNull
import com.example.cdplaya.player.equalizer.parametric.slopeOrNull
import com.example.cdplaya.ui.library.LibraryViewCategory
import com.example.cdplaya.ui.library.LibraryViewMode
import com.example.cdplaya.ui.player.modern.ModernArtworkTransitionStyle
import com.example.cdplaya.ui.player.modern.ModernArtworkAppearance
import com.example.cdplaya.ui.player.modern.ModernArtworkFit
import com.example.cdplaya.ui.player.modern.ModernArtworkShadow
import com.example.cdplaya.ui.player.modern.ModernArtworkShape
import com.example.cdplaya.ui.player.modern.ModernArtworkSize
import com.example.cdplaya.ui.player.modern.ModernBackgroundAppearance
import com.example.cdplaya.ui.player.modern.ModernBackgroundStyle
import com.example.cdplaya.ui.player.modern.ModernBlurStrength
import com.example.cdplaya.ui.player.modern.ModernDimmingStrength
import com.example.cdplaya.ui.player.modern.ModernControlAccent
import com.example.cdplaya.ui.player.modern.ModernControlAppearance
import com.example.cdplaya.ui.player.modern.ModernControlSize
import com.example.cdplaya.ui.player.modern.ModernControlStyle
import com.example.cdplaya.ui.player.modern.ModernLayoutAppearance
import com.example.cdplaya.ui.player.modern.ModernLayoutDensity
import com.example.cdplaya.ui.player.modern.ModernMetadataAlignment
import com.example.cdplaya.ui.player.modern.ModernPlayerAppearance
import com.example.cdplaya.ui.player.modern.ModernSeekbarAppearance
import com.example.cdplaya.ui.player.modern.ModernSeekbarColorMode
import com.example.cdplaya.ui.player.modern.ModernSeekbarStyle
import com.example.cdplaya.ui.player.modern.ModernWaveformDensity
import com.example.cdplaya.ui.player.modern.ModernWaveformSize
import com.example.cdplaya.ui.player.modern.sanitizeModernSolidColorArgb
import com.example.cdplaya.ui.player.theme.PlayerThemeTokenField
import com.example.cdplaya.ui.player.theme.PlayerThemeTokenOverrides
import com.example.cdplaya.ui.player.theme.customizationOptions
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

private data class RestoredVisualAssets(val count: Int)

class BackupRepository(
    context: Context,
    private val favoritesRepository: FavoritesRepository,
    private val playlistsRepository: PlaylistsRepository,
    private val listeningHistoryRepository: ListeningHistoryRepository,
    private val appDatabase: AppDatabase,
    private val appPreferencesRepository: AppPreferencesRepository =
        AppPreferencesRepository.getInstance(context)
) {
    private val context = context.applicationContext ?: context
    private val canonicalHistoryRepository = ListeningHistoryBackupRepository(appDatabase)
    private val smartPlaylistBackupRepository = SmartPlaylistBackupRepository(appDatabase)
    private val artistPictureRepository = ArtistPictureRepository(
        appDatabase.artistPictureAssignmentDao()
    )
    private val visualAssetStore = VisualAssetStore(this.context)

    suspend fun createBackup(): AppBackup = withContext(Dispatchers.IO) {
        val appPreferences = appPreferencesRepository.awaitLoadedState()
        val canonical = canonicalHistoryRepository.exportWithRatings()
        val backup = AppBackup(
            schemaVersion = AppBackupJson.CURRENT_SCHEMA_VERSION,
            createdAt = System.currentTimeMillis(),
            appName = APP_NAME,
            favorites = favoritesRepository.getFavoritesForBackup(),
            playlistFolders = playlistsRepository.getPlaylistFoldersForBackup(),
            playlists = smartPlaylistBackupRepository.attachTo(
                playlistsRepository.getPlaylistsForBackup()
            ),
            listeningHistory = emptyList(),
            canonicalListeningHistory = canonical.history,
            songRatings = canonical.ratings,
            preferences = BackupPreferences(
                folderSelectionMode = appPreferences.folderSelectionMode.name,
                selectedLibraryFolders = appPreferences.selectedLibraryFolders
                    .map { it.toPortableFolderSelection() }
                    .filter { it.isNotBlank() }
                    .sorted(),
                selectedPlayerThemeId = appPreferences.selectedPlayerTheme.id,
                replayGainMode = appPreferences.replayGainMode.name,
                audioOffloadPreference = appPreferences.audioOffloadPreference.name,
                smoothPlayPauseEnabled = appPreferences.smoothPlayPauseEnabled,
                crossfadeEnabled = appPreferences.crossfadeEnabled,
                crossfadeDurationMs = appPreferences.crossfadeDurationMs,
                preserveAlbumTransitions = appPreferences.preserveAlbumTransitions,
                modernArtworkTransitionStyle =
                    appPreferences.modernArtworkTransitionStyle.storageValue,
                modernSeekbarStyle = appPreferences.modernSeekbarStyle.storageValue,
                modernWaveformSize =
                    appPreferences.modernPlayerAppearance.seekbar.waveformSize.storageValue,
                modernWaveformDensity =
                    appPreferences.modernPlayerAppearance.seekbar.waveformDensity.storageValue,
                modernSeekbarColorMode =
                    appPreferences.modernPlayerAppearance.seekbar.colorMode.storageValue,
                modernBackgroundStyle =
                    appPreferences.modernPlayerAppearance.background.style.storageValue,
                modernBlurStrength =
                    appPreferences.modernPlayerAppearance.background.blurStrength.storageValue,
                modernDimmingStrength =
                    appPreferences.modernPlayerAppearance.background.dimmingStrength.storageValue,
                modernSolidColorArgb =
                    sanitizeModernSolidColorArgb(
                        appPreferences.modernPlayerAppearance.background.solidColorArgb
                    ),
                modernArtworkShape =
                    appPreferences.modernPlayerAppearance.artwork.shape.storageValue,
                modernArtworkSize =
                    appPreferences.modernPlayerAppearance.artwork.size.storageValue,
                modernArtworkFit =
                    appPreferences.modernPlayerAppearance.artwork.fit.storageValue,
                modernArtworkShadow =
                    appPreferences.modernPlayerAppearance.artwork.shadow.storageValue,
                modernControlStyle =
                    appPreferences.modernPlayerAppearance.controls.style.storageValue,
                modernControlSize =
                    appPreferences.modernPlayerAppearance.controls.size.storageValue,
                modernControlAccent =
                    appPreferences.modernPlayerAppearance.controls.accent.storageValue,
                modernLayoutDensity =
                    appPreferences.modernPlayerAppearance.layout.density.storageValue,
                modernMetadataAlignment =
                    appPreferences.modernPlayerAppearance.layout.metadataAlignment.storageValue,
                modernShowAudioQualityBadge =
                    appPreferences.modernPlayerAppearance.layout.showAudioQualityBadge,
                playerThemeTokenOverrides = createThemeTokenBackup(appPreferences),
                songsViewMode = appPreferences.songsViewMode.storageValue,
                albumsViewMode = appPreferences.albumsViewMode.storageValue,
                artistsViewMode = appPreferences.artistsViewMode.storageValue,
                playlistsViewMode = appPreferences.playlistsViewMode.storageValue,
                songsGridColumnCount = appPreferences.songsGridColumnCount,
                albumsGridColumnCount = appPreferences.albumsGridColumnCount,
                artistsGridColumnCount = appPreferences.artistsGridColumnCount,
                playlistsGridColumnCount = appPreferences.playlistsGridColumnCount,
                homePins = appPreferences.homePins.map { pin ->
                    BackupHomePin(
                        id = pin.id,
                        type = pin.type.name,
                        title = pin.title,
                        subtitle = pin.subtitle,
                        anchor = pin.anchor?.toBackupSongReference(),
                        playlistId = pin.playlistId
                    )
                },
                showRecentlyAddedOnHome = appPreferences.showRecentlyAddedOnHome,
                equalizer = appPreferences
                    .equalizerPreferences
                    .toBackupEqualizerPreferences()
            )
        )
        val payloads = collectVisualAssetPayloads(
            playlists = backup.playlists,
            artistAssignments = artistPictureRepository.getAll()
        )
        backup.copy(
            visualAssets = payloads.map(BackupVisualAssetPayload::metadata),
            visualAssetPayloads = payloads
        )
    }

    suspend fun writeBackupToUri(uri: Uri): BackupExportResult = withContext(Dispatchers.IO) {
        val backup = createBackup()
        val outputStream = context.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("Unable to open backup destination.")

        outputStream.use { stream ->
            BackupArchive.write(backup, stream)
        }

        backup.toBackupExportResult()
    }

    suspend fun readBackupFromUri(uri: Uri): AppBackup = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open backup source.")
        inputStream.use { stream ->
            BackupArchive.read(stream)
        }
    }

    fun summarizeRestore(backup: AppBackup): BackupRestoreSummary {
        return backup.toBackupRestoreSummary()
    }

    suspend fun restoreBackup(backup: AppBackup): BackupRestoreResult =
        withContext(Dispatchers.IO) {
            val summary = summarizeRestore(backup)
            val validatedHistory = ListeningHistoryBackupValidator.validate(
                backup.requiredCanonicalListeningHistory()
            )
            val validatedRatings = SongRatingBackupValidator.validate(
                backup.songRatings,
                validatedHistory
            )

            val previousArtistPictures = artistPictureRepository.getAll()
            val restoredPlaylistIds = appDatabase.withTransaction {
                val restoredIdentityIds =
                    canonicalHistoryRepository.restoreValidatedWithinTransaction(validatedHistory)
                canonicalHistoryRepository.restoreRatingsValidatedWithinTransaction(
                    validatedRatings,
                    restoredIdentityIds
                )
                favoritesRepository.restoreFavoritesFromBackup(backup.favorites)
                val playlistIds = playlistsRepository.restorePlaylistsFromBackup(
                    folders = backup.playlistFolders,
                    playlists = backup.playlists
                )
                smartPlaylistBackupRepository.restoreWithinTransaction(
                    backup.playlists,
                    playlistIds
                )
                listeningHistoryRepository.restoreListeningHistoryFromBackup(
                    backup.listeningHistory
                )
                appDatabase.artistPictureAssignmentDao().deleteAll()
                playlistIds
            }
            val restoredVisualAssets = restoreVisualAssets(backup, restoredPlaylistIds)
            previousArtistPictures.forEach { assignment ->
                visualAssetStore.delete(
                    VisualAssetOwnerType.ARTIST_IMAGE,
                    assignment.artistKey,
                    assignment.assetReference
                )
            }
            restorePreferences(backup.preferences, restoredPlaylistIds)

            BackupRestoreResult(
                favoriteCount = summary.favoriteCount,
                playlistCount = summary.playlistCount,
                playlistSongCount = summary.playlistSongCount,
                listeningHistoryCount = summary.listeningHistoryCount,
                selectedFolderCount = summary.selectedFolderCount,
                visualAssetCount = restoredVisualAssets.count
            )
        }

    private fun collectVisualAssetPayloads(
        playlists: List<BackupPlaylist>,
        artistAssignments: List<ArtistPictureAssignment>
    ): List<BackupVisualAssetPayload> = buildList {
        artistAssignments.forEach { assignment ->
            collectVisualAssetPayload(
                ownerType = VisualAssetOwnerType.ARTIST_IMAGE,
                ownerKey = assignment.artistKey,
                reference = assignment.assetReference,
                normalizedArtistName = assignment.normalizedArtistName,
                archiveOwnerDirectory = "artists/${assignment.artistKey}"
            )?.let(::add)
        }
        playlists.forEach { playlist ->
            val playlistId = playlist.playlistId ?: return@forEach
            val reference = playlist.artworkReference
                ?.takeIf { playlist.artworkMode == PlaylistArtworkMode.CUSTOM.name }
                ?: return@forEach
            collectVisualAssetPayload(
                ownerType = VisualAssetOwnerType.PLAYLIST_IMAGE,
                ownerKey = playlistId.toString(),
                reference = reference,
                normalizedArtistName = null,
                archiveOwnerDirectory = "playlists/$playlistId"
            )?.let(::add)
        }
    }

    private fun collectVisualAssetPayload(
        ownerType: VisualAssetOwnerType,
        ownerKey: String,
        reference: String,
        normalizedArtistName: String?,
        archiveOwnerDirectory: String
    ): BackupVisualAssetPayload? {
        val thumbnail = visualAssetStore.file(
            ownerType, ownerKey, reference, VisualAssetVariant.THUMBNAIL
        ) ?: return null
        val display = visualAssetStore.file(
            ownerType, ownerKey, reference, VisualAssetVariant.DISPLAY
        ) ?: return null
        return runCatching {
            val root = "visual_assets/$archiveOwnerDirectory/$reference"
            val metadata = BackupVisualAsset(
                ownerType = ownerType.name,
                ownerKey = ownerKey,
                assetReference = reference,
                normalizedArtistName = normalizedArtistName,
                thumbnailEntry = "$root/${VisualAssetVariant.THUMBNAIL.fileName}",
                displayEntry = "$root/${VisualAssetVariant.DISPLAY.fileName}"
            )
            BackupVisualAssetPayload(metadata, thumbnail.readBytes(), display.readBytes())
        }.getOrNull()
    }

    private suspend fun restoreVisualAssets(
        backup: AppBackup,
        restoredPlaylistIds: Map<Long, Long>
    ): RestoredVisualAssets {
        var restoredCount = 0
        val restoredPlaylistBackupIds = mutableSetOf<Long>()
        backup.visualAssetPayloads.forEach { payload ->
            val restored = runCatching {
                when (payload.metadata.ownerType) {
                    VisualAssetOwnerType.ARTIST_IMAGE.name -> {
                        val normalizedName = payload.metadata.normalizedArtistName
                            ?.takeIf(String::isNotBlank) ?: return@runCatching false
                        val identity = artistIdentity(normalizedName)
                        if (!identity.supportsCustomPicture ||
                            identity.key != payload.metadata.ownerKey
                        ) return@runCatching false
                        val imported = visualAssetStore.restoreVariants(
                            ownerType = VisualAssetOwnerType.ARTIST_IMAGE,
                            ownerKey = payload.metadata.ownerKey,
                            thumbnailBytes = payload.thumbnailBytes,
                            displayBytes = payload.displayBytes
                        )
                        try {
                            artistPictureRepository.upsert(
                                ArtistPictureAssignment(
                                    artistKey = payload.metadata.ownerKey,
                                    normalizedArtistName = normalizedName,
                                    assetReference = imported.reference,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        } catch (failure: Throwable) {
                            visualAssetStore.delete(
                                VisualAssetOwnerType.ARTIST_IMAGE,
                                payload.metadata.ownerKey,
                                imported.reference
                            )
                            throw failure
                        }
                        true
                    }
                    VisualAssetOwnerType.PLAYLIST_IMAGE.name -> {
                        val oldPlaylistId = payload.metadata.ownerKey.toLongOrNull()
                            ?: return@runCatching false
                        val newPlaylistId = restoredPlaylistIds[oldPlaylistId]
                            ?: return@runCatching false
                        val imported = visualAssetStore.restoreVariants(
                            ownerType = VisualAssetOwnerType.PLAYLIST_IMAGE,
                            ownerKey = newPlaylistId.toString(),
                            thumbnailBytes = payload.thumbnailBytes,
                            displayBytes = payload.displayBytes
                        )
                        try {
                            playlistsRepository.setCustomArtwork(
                                newPlaylistId,
                                imported.reference
                            )
                        } catch (failure: Throwable) {
                            visualAssetStore.delete(
                                VisualAssetOwnerType.PLAYLIST_IMAGE,
                                newPlaylistId.toString(),
                                imported.reference
                            )
                            throw failure
                        }
                        restoredPlaylistBackupIds += oldPlaylistId
                        true
                    }
                    else -> false
                }
            }.fold(
                onSuccess = { it },
                onFailure = { failure ->
                    if (failure is CancellationException) throw failure
                    false
                }
            )
            if (restored) restoredCount += 1
        }
        backup.visualAssets.asSequence()
            .filter { it.ownerType == VisualAssetOwnerType.PLAYLIST_IMAGE.name }
            .mapNotNull { it.ownerKey.toLongOrNull() }
            .filterNot(restoredPlaylistBackupIds::contains)
            .mapNotNull(restoredPlaylistIds::get)
            .distinct()
            .forEach { playlistId -> playlistsRepository.resetArtwork(playlistId) }
        return RestoredVisualAssets(restoredCount)
    }

    private fun createThemeTokenBackup(
        preferences: AppPreferencesState
    ): Map<String, BackupPlayerThemeTokenOverrides> {
        return PlayerTheme.entries.mapNotNull { theme ->
            val supportedFields = theme.customizationOptions().map { it.field }.toSet()
            if (supportedFields.isEmpty()) return@mapNotNull null
            val overrides = preferences.playerThemeTokenOverrides[theme]
                ?: PlayerThemeTokenOverrides()
            val backup = BackupPlayerThemeTokenOverrides(
                shellArgb = overrides.shellColor.toBackupArgbIfSupported(
                    PlayerThemeTokenField.SHELL in supportedFields
                ),
                accentArgb = overrides.accentColor.toBackupArgbIfSupported(
                    PlayerThemeTokenField.ACCENT in supportedFields
                ),
                displayBackgroundArgb = overrides.displayBackgroundColor.toBackupArgbIfSupported(
                    PlayerThemeTokenField.DISPLAY_BACKGROUND in supportedFields
                ),
                displayTextArgb = overrides.displayTextColor.toBackupArgbIfSupported(
                    PlayerThemeTokenField.DISPLAY_TEXT in supportedFields
                ),
                secondaryAccentArgb = overrides.secondaryAccentColor.toBackupArgbIfSupported(
                    PlayerThemeTokenField.SECONDARY_ACCENT in supportedFields
                )
            )
            if (backup.hasAnyValue()) theme.id to backup else null
        }.toMap()
    }

    private suspend fun restorePreferences(
        preferences: BackupPreferences,
        restoredPlaylistIds: Map<Long, Long>
    ) {
        val overrides = PlayerTheme.entries.mapNotNull { theme ->
            val backup = preferences.playerThemeTokenOverrides[theme.id] ?: return@mapNotNull null
            val supportedFields = theme.customizationOptions().map { it.field }.toSet()
            if (supportedFields.isEmpty()) return@mapNotNull null
            theme to PlayerThemeTokenOverrides(
                shellColor = backup.shellArgb.toColorIfSupported(
                    PlayerThemeTokenField.SHELL in supportedFields
                ),
                accentColor = backup.accentArgb.toColorIfSupported(
                    PlayerThemeTokenField.ACCENT in supportedFields
                ),
                displayBackgroundColor = backup.displayBackgroundArgb.toColorIfSupported(
                    PlayerThemeTokenField.DISPLAY_BACKGROUND in supportedFields
                ),
                displayTextColor = backup.displayTextArgb.toColorIfSupported(
                    PlayerThemeTokenField.DISPLAY_TEXT in supportedFields
                ),
                secondaryAccentColor = backup.secondaryAccentArgb.toColorIfSupported(
                    PlayerThemeTokenField.SECONDARY_ACCENT in supportedFields
                )
            )
        }.toMap()
        appPreferencesRepository.replaceAll(
            AppPreferencesState(
                selectedPlayerTheme = PlayerTheme.fromId(preferences.selectedPlayerThemeId),
                playerThemeTokenOverrides = overrides,
                modernArtworkTransitionStyle = ModernArtworkTransitionStyle.fromStorageValue(
                    preferences.modernArtworkTransitionStyle
                ),
                modernPlayerAppearance = ModernPlayerAppearance(
                    seekbar = ModernSeekbarAppearance(
                        style = ModernSeekbarStyle.fromStorageValue(
                            preferences.modernSeekbarStyle
                        ),
                        waveformSize = ModernWaveformSize.fromStorageValue(
                            preferences.modernWaveformSize
                        ),
                        waveformDensity = ModernWaveformDensity.fromStorageValue(
                            preferences.modernWaveformDensity
                        ),
                        colorMode = ModernSeekbarColorMode.fromStorageValue(
                            preferences.modernSeekbarColorMode
                        )
                    ),
                    background = ModernBackgroundAppearance(
                        style = ModernBackgroundStyle.fromStorageValue(
                            preferences.modernBackgroundStyle
                        ),
                        blurStrength = ModernBlurStrength.fromStorageValue(
                            preferences.modernBlurStrength
                        ),
                        dimmingStrength = ModernDimmingStrength.fromStorageValue(
                            preferences.modernDimmingStrength
                        ),
                        solidColorArgb = sanitizeModernSolidColorArgb(
                            preferences.modernSolidColorArgb
                        )
                    ),
                    artwork = ModernArtworkAppearance(
                        shape = ModernArtworkShape.fromStorageValue(
                            preferences.modernArtworkShape
                        ),
                        size = ModernArtworkSize.fromStorageValue(
                            preferences.modernArtworkSize
                        ),
                        fit = ModernArtworkFit.fromStorageValue(
                            preferences.modernArtworkFit
                        ),
                        shadow = ModernArtworkShadow.fromStorageValue(
                            preferences.modernArtworkShadow
                        )
                    ),
                    controls = ModernControlAppearance(
                        style = ModernControlStyle.fromStorageValue(
                            preferences.modernControlStyle
                        ),
                        size = ModernControlSize.fromStorageValue(
                            preferences.modernControlSize
                        ),
                        accent = ModernControlAccent.fromStorageValue(
                            preferences.modernControlAccent
                        )
                    ),
                    layout = ModernLayoutAppearance(
                        density = ModernLayoutDensity.fromStorageValue(
                            preferences.modernLayoutDensity
                        ),
                        metadataAlignment = ModernMetadataAlignment.fromStorageValue(
                            preferences.modernMetadataAlignment
                        ),
                        showAudioQualityBadge = preferences.modernShowAudioQualityBadge
                    )
                ),
                replayGainMode = runCatching { ReplayGainMode.valueOf(preferences.replayGainMode) }
                    .getOrDefault(ReplayGainMode.OFF),
                audioOffloadPreference = AudioOffloadPreference.fromStorageValue(
                    preferences.audioOffloadPreference
                ),
                smoothPlayPauseEnabled = preferences.smoothPlayPauseEnabled,
                crossfadeEnabled = preferences.crossfadeEnabled,
                crossfadeDurationMs = CrossfadePreferences.clampDurationMs(
                    preferences.crossfadeDurationMs
                ),
                preserveAlbumTransitions = preferences.preserveAlbumTransitions,
                folderSelectionMode = FolderSelection.fromStored(
                    storedMode = preferences.folderSelectionMode,
                    storedFolders = preferences.selectedLibraryFolders.toSet()
                ).mode,
                selectedLibraryFolders = FolderSelection.fromStored(
                    storedMode = preferences.folderSelectionMode,
                    storedFolders = preferences.selectedLibraryFolders.toSet()
                ).customFolders,
                songsViewMode = LibraryViewMode.fromStorageValue(preferences.songsViewMode),
                albumsViewMode = LibraryViewMode.fromStorageValue(preferences.albumsViewMode),
                artistsViewMode = LibraryViewMode.fromStorageValue(preferences.artistsViewMode),
                playlistsViewMode = LibraryViewMode.fromStorageValue(preferences.playlistsViewMode),
                songsGridColumnCount = preferences.songsGridColumnCount,
                albumsGridColumnCount = preferences.albumsGridColumnCount,
                artistsGridColumnCount = preferences.artistsGridColumnCount,
                playlistsGridColumnCount = preferences.playlistsGridColumnCount,
                homePins = sanitizeHomePins(
                    preferences.homePins.mapNotNull { backupPin ->
                        val type = runCatching { HomePinType.valueOf(backupPin.type) }
                            .getOrNull() ?: return@mapNotNull null
                        HomePin(
                            id = backupPin.id,
                            type = type,
                            title = backupPin.title,
                            subtitle = backupPin.subtitle,
                            anchor = backupPin.anchor?.toSongReference(),
                            playlistId = if (type == HomePinType.PLAYLIST) {
                                backupPin.playlistId?.let(restoredPlaylistIds::get)
                            } else {
                                null
                            }
                        ).normalizedForPersistence()
                    }
                ),
                showRecentlyAddedOnHome = preferences.showRecentlyAddedOnHome,
                equalizerPreferences =
                    preferences.equalizer
                        .toEqualizerPreferencesState(),
                isLoaded = true
            )
        )
    }

    private companion object {
        const val APP_NAME = "CDPlaya"
    }
}

private fun EqualizerPreferencesState
        .toBackupEqualizerPreferences() =
    BackupEqualizerPreferences(
        enabled = enabled,
        preampDb = preampDb,
        automaticHeadroomEnabled =
            automaticHeadroomEnabled,
        bandGainsDb = bandGainsDb.toList(),
        limiterEnabled = limiterEnabled,
        limiterCeilingDbfs = limiterCeilingDbfs,
        userPresets = userPresets.map { preset ->
            BackupEqualizerPreset(
                id = preset.id,
                name = preset.name,
                preampDb = preset.preampDb,
                automaticHeadroomEnabled =
                    preset.automaticHeadroomEnabled,
                bandGainsDb = preset.bandGainsDb.toList()
            )
        },
        mode = mode.name,
        parametricPreampDb = parametricState.preampDb,
        parametricAutomaticHeadroomEnabled =
            parametricState.automaticHeadroomEnabled,
        parametricFilters = parametricState.filters.map {
                filter -> filter.toBackup()
        },
        parametricUserPresets =
            parametricState.userPresets.map { preset ->
                BackupParametricEqualizerPreset(
                    id = preset.id,
                    name = preset.name,
                    preampDb = preset.preampDb,
                    automaticHeadroomEnabled =
                        preset.automaticHeadroomEnabled,
                    filters = preset.filters.map { filter ->
                        filter.toBackup()
                    }
                )
            }
    )

private fun BackupEqualizerPreferences
        .toEqualizerPreferencesState() =
    EqualizerPreferencesState(
        enabled = enabled,
        preampDb = preampDb,
        automaticHeadroomEnabled =
            automaticHeadroomEnabled,
        bandGainsDb = bandGainsDb,
        limiterEnabled = limiterEnabled,
        limiterCeilingDbfs = limiterCeilingDbfs,
        userPresets = userPresets.map { preset ->
            UserEqualizerPreset(
                id = preset.id,
                name = preset.name.trim(),
                preampDb = preset.preampDb,
                automaticHeadroomEnabled =
                    preset.automaticHeadroomEnabled,
                bandGainsDb = preset.bandGainsDb
            )
        },
        mode = EqualizerMode.valueOf(mode),
        parametricState = ParametricEqualizerState(
            preampDb = parametricPreampDb,
            automaticHeadroomEnabled =
                parametricAutomaticHeadroomEnabled,
            filters = parametricFilters.map { filter ->
                filter.toDomain()
            },
            userPresets = parametricUserPresets.map { preset ->
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
        )
    )

private fun ParametricFilter.toBackup() = BackupParametricFilter(
    id = id,
    type = type.name,
    enabled = enabled,
    frequencyHz = frequencyHz,
    gainDb = gainDbOrNull,
    q = qOrNull,
    slope = slopeOrNull
)

private fun Color?.toBackupArgbIfSupported(isSupported: Boolean): Long? {
    return if (isSupported && this != null) toArgb().toUInt().toLong() else null
}

private fun Long?.toColorIfSupported(isSupported: Boolean): Color? {
    return if (isSupported && this != null && this in 0..0xFFFF_FFFFL) {
        Color(toInt())
    } else {
        null
    }
}

private fun BackupPlayerThemeTokenOverrides.hasAnyValue(): Boolean {
    return shellArgb != null || accentArgb != null || displayBackgroundArgb != null ||
            displayTextArgb != null || secondaryAccentArgb != null
}

data class BackupExportResult(
    val favoriteCount: Int,
    val playlistCount: Int,
    val playlistSongCount: Int,
    val listeningHistoryCount: Int,
    val visualAssetCount: Int = 0
)

data class BackupRestoreSummary(
    val favoriteCount: Int,
    val playlistCount: Int,
    val playlistSongCount: Int,
    val listeningHistoryCount: Int,
    val selectedFolderCount: Int,
    val visualAssetCount: Int = 0
)

data class BackupRestoreResult(
    val favoriteCount: Int,
    val playlistCount: Int,
    val playlistSongCount: Int,
    val listeningHistoryCount: Int,
    val selectedFolderCount: Int,
    val visualAssetCount: Int = 0
)

internal fun AppBackup.toBackupExportResult(): BackupExportResult {
    return BackupExportResult(
        favoriteCount = favorites.size,
        playlistCount = playlists.size,
        playlistSongCount = playlists.sumOf { playlist -> playlist.songs.size },
        listeningHistoryCount = requiredCanonicalListeningHistory()
            .summary.identityCount.toDisplayCount(),
        visualAssetCount = visualAssetPayloads.size
    )
}

internal fun AppBackup.toBackupRestoreSummary(): BackupRestoreSummary {
    return BackupRestoreSummary(
        favoriteCount = favorites.size,
        playlistCount = playlists.size,
        playlistSongCount = playlists.sumOf { playlist -> playlist.songs.size },
        listeningHistoryCount = requiredCanonicalListeningHistory()
            .summary.identityCount.toDisplayCount(),
        selectedFolderCount = preferences.selectedLibraryFolders.size,
        visualAssetCount = visualAssetPayloads.size
    )
}

private fun Long.toDisplayCount(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

private fun AppBackup.requiredCanonicalListeningHistory(): BackupListeningHistoryV2 =
    requireNotNull(canonicalListeningHistory) {
        "Sazanami backup schema 10 requires canonical listening history."
    }
