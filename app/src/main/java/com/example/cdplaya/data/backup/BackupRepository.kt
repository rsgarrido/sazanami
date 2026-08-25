package com.example.cdplaya.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.cdplaya.data.FavoritesRepository
import com.example.cdplaya.data.FolderSelection
import com.example.cdplaya.data.ListeningHistoryRepository
import com.example.cdplaya.data.PlayerTheme
import com.example.cdplaya.data.PlaylistsRepository
import com.example.cdplaya.data.home.HomePin
import com.example.cdplaya.data.home.HomePinType
import com.example.cdplaya.data.home.sanitizeHomePins
import com.example.cdplaya.player.replaygain.ReplayGainMode
import com.example.cdplaya.data.preferences.AppPreferencesRepository
import com.example.cdplaya.data.preferences.AppPreferencesState
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
import com.example.cdplaya.ui.player.modern.ModernSeekbarStyle
import com.example.cdplaya.ui.player.theme.PlayerThemeTokenField
import com.example.cdplaya.ui.player.theme.PlayerThemeTokenOverrides
import com.example.cdplaya.ui.player.theme.customizationOptions
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun createBackup(): AppBackup = withContext(Dispatchers.IO) {
        val appPreferences = appPreferencesRepository.awaitLoadedState()
        val canonical = canonicalHistoryRepository.exportWithRatings()
        AppBackup(
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
                modernArtworkTransitionStyle =
                    appPreferences.modernArtworkTransitionStyle.storageValue,
                modernSeekbarStyle = appPreferences.modernSeekbarStyle.storageValue,
                playerThemeTokenOverrides = createThemeTokenBackup(appPreferences),
                songsViewMode = appPreferences.songsViewMode.storageValue,
                albumsViewMode = appPreferences.albumsViewMode.storageValue,
                artistsViewMode = appPreferences.artistsViewMode.storageValue,
                songsGridColumnCount = appPreferences.songsGridColumnCount,
                albumsGridColumnCount = appPreferences.albumsGridColumnCount,
                artistsGridColumnCount = appPreferences.artistsGridColumnCount,
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
    }

    suspend fun writeBackupToUri(uri: Uri): BackupExportResult = withContext(Dispatchers.IO) {
        val backup = createBackup()
        val outputStream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Unable to open backup destination.")

        outputStream.use { stream ->
            AppBackupJson.encodeBackup(backup, stream)
        }

        backup.toBackupExportResult()
    }

    suspend fun readBackupFromUri(uri: Uri): AppBackup = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open backup source.")
        inputStream.use { stream ->
            AppBackupJson.decodeBackup(stream)
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
                playlistIds
            }
            restorePreferences(backup.preferences, restoredPlaylistIds)

            BackupRestoreResult(
                favoriteCount = summary.favoriteCount,
                playlistCount = summary.playlistCount,
                playlistSongCount = summary.playlistSongCount,
                listeningHistoryCount = summary.listeningHistoryCount,
                selectedFolderCount = summary.selectedFolderCount
            )
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
                modernSeekbarStyle = ModernSeekbarStyle.fromStorageValue(
                    preferences.modernSeekbarStyle
                ),
                replayGainMode = runCatching { ReplayGainMode.valueOf(preferences.replayGainMode) }
                    .getOrDefault(ReplayGainMode.OFF),
                audioOffloadPreference = AudioOffloadPreference.fromStorageValue(
                    preferences.audioOffloadPreference
                ),
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
                songsGridColumnCount = preferences.songsGridColumnCount,
                albumsGridColumnCount = preferences.albumsGridColumnCount,
                artistsGridColumnCount = preferences.artistsGridColumnCount,
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
    val listeningHistoryCount: Int
)

data class BackupRestoreSummary(
    val favoriteCount: Int,
    val playlistCount: Int,
    val playlistSongCount: Int,
    val listeningHistoryCount: Int,
    val selectedFolderCount: Int
)

data class BackupRestoreResult(
    val favoriteCount: Int,
    val playlistCount: Int,
    val playlistSongCount: Int,
    val listeningHistoryCount: Int,
    val selectedFolderCount: Int
)

internal fun AppBackup.toBackupExportResult(): BackupExportResult {
    return BackupExportResult(
        favoriteCount = favorites.size,
        playlistCount = playlists.size,
        playlistSongCount = playlists.sumOf { playlist -> playlist.songs.size },
        listeningHistoryCount = requiredCanonicalListeningHistory()
            .summary.identityCount.toDisplayCount()
    )
}

internal fun AppBackup.toBackupRestoreSummary(): BackupRestoreSummary {
    return BackupRestoreSummary(
        favoriteCount = favorites.size,
        playlistCount = playlists.size,
        playlistSongCount = playlists.sumOf { playlist -> playlist.songs.size },
        listeningHistoryCount = requiredCanonicalListeningHistory()
            .summary.identityCount.toDisplayCount(),
        selectedFolderCount = preferences.selectedLibraryFolders.size
    )
}

private fun Long.toDisplayCount(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

private fun AppBackup.requiredCanonicalListeningHistory(): BackupListeningHistoryV2 =
    requireNotNull(canonicalListeningHistory) {
        "CDPlaya backup schema 10 requires canonical listening history."
    }
