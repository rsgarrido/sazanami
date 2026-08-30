package io.github.rsgarrido.sazanami.data.preferences

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.FolderSelection
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.home.HomePin
import io.github.rsgarrido.sazanami.data.home.HomePinType
import io.github.rsgarrido.sazanami.data.home.sanitizeHomePins
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerMode
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPreferencesState
import io.github.rsgarrido.sazanami.player.equalizer.GraphicEqualizerPresets
import io.github.rsgarrido.sazanami.player.equalizer.UserEqualizerPreset
import io.github.rsgarrido.sazanami.player.equalizer.normalizeBandGains
import io.github.rsgarrido.sazanami.player.equalizer.normalizeEqualizerDb
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerPreset
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerPresets
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerState
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilterType
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.ui.library.LibraryGridColumns
import io.github.rsgarrido.sazanami.ui.library.LibraryViewCategory
import io.github.rsgarrido.sazanami.ui.library.LibraryViewMode
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkFit
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkShadow
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkShape
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkSize
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBackgroundAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBackgroundStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBlurStrength
import io.github.rsgarrido.sazanami.ui.player.modern.ModernDimmingStrength
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlAccent
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlSize
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLayoutAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLayoutDensity
import io.github.rsgarrido.sazanami.ui.player.modern.ModernMetadataAlignment
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarColorMode
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernWaveformDensity
import io.github.rsgarrido.sazanami.ui.player.modern.ModernWaveformSize
import io.github.rsgarrido.sazanami.ui.player.modern.sanitizeModernSolidColorArgb
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenOverrides
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class AppPreferencesState(
    val selectedPlayerTheme: PlayerTheme = PlayerTheme.DEFAULT,
    val playerThemeTokenOverrides: Map<PlayerTheme, PlayerThemeTokenOverrides> = emptyMap(),
    val modernArtworkTransitionStyle: ModernArtworkTransitionStyle =
        ModernArtworkTransitionStyle.SLIDE,
    val modernPlayerAppearance: ModernPlayerAppearance = ModernPlayerAppearance.Default,
    val replayGainMode: ReplayGainMode = ReplayGainMode.OFF,
    val audioOffloadPreference: AudioOffloadPreference = AudioOffloadPreference.DISABLED,
    val smoothPlayPauseEnabled: Boolean = true,
    val crossfadeEnabled: Boolean = false,
    val crossfadeDurationMs: Int = CrossfadePreferences.DEFAULT_DURATION_MS,
    val preserveAlbumTransitions: Boolean = true,
    val equalizerPreferences: EqualizerPreferencesState =
        EqualizerPreferencesState(),
    val folderSelectionMode: FolderSelectionMode = FolderSelectionMode.ALL,
    val selectedLibraryFolders: Set<String> = emptySet(),
    val songsViewMode: LibraryViewMode = LibraryViewMode.LIST,
    val albumsViewMode: LibraryViewMode = LibraryViewMode.LIST,
    val artistsViewMode: LibraryViewMode = LibraryViewMode.LIST,
    val playlistsViewMode: LibraryViewMode = LibraryViewMode.LIST,
    val songsGridColumnCount: Int = LibraryGridColumns.DEFAULT,
    val albumsGridColumnCount: Int = LibraryGridColumns.DEFAULT,
    val artistsGridColumnCount: Int = LibraryGridColumns.DEFAULT,
    val playlistsGridColumnCount: Int = LibraryGridColumns.DEFAULT,
    val homePins: List<HomePin> = emptyList(),
    val showRecentlyAddedOnHome: Boolean = true,
    val isLoaded: Boolean = false
) {
    val modernSeekbarStyle: ModernSeekbarStyle
        get() = modernPlayerAppearance.seekbar.style
}

class AppPreferencesRepository private constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope
) {
    val state: StateFlow<AppPreferencesState> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::decodeAppPreferences)
        .stateIn(scope, SharingStarted.Eagerly, AppPreferencesState())

    suspend fun awaitLoadedState(): AppPreferencesState = state.filter { it.isLoaded }.first()

    suspend fun setSelectedPlayerTheme(theme: PlayerTheme) = edit {
        it[Keys.selectedPlayerTheme] = theme.id
    }

    suspend fun setModernArtworkTransitionStyle(style: ModernArtworkTransitionStyle) = edit {
        it[Keys.modernArtworkTransitionStyle] = style.storageValue
    }

    suspend fun setModernSeekbarStyle(style: ModernSeekbarStyle) = edit {
        it[Keys.modernSeekbarStyle] = style.storageValue
    }

    suspend fun setModernPlayerAppearance(appearance: ModernPlayerAppearance) = edit {
        it.writeModernPlayerAppearance(appearance)
    }

    suspend fun setModernWaveformSize(size: ModernWaveformSize) = edit {
        it[Keys.modernWaveformSize] = size.storageValue
    }

    suspend fun setModernWaveformDensity(density: ModernWaveformDensity) = edit {
        it[Keys.modernWaveformDensity] = density.storageValue
    }

    suspend fun setModernSeekbarColorMode(mode: ModernSeekbarColorMode) = edit {
        it[Keys.modernSeekbarColorMode] = mode.storageValue
    }

    suspend fun setModernBackgroundStyle(style: ModernBackgroundStyle) = edit {
        it[Keys.modernBackgroundStyle] = style.storageValue
    }

    suspend fun setModernBlurStrength(strength: ModernBlurStrength) = edit {
        it[Keys.modernBlurStrength] = strength.storageValue
    }

    suspend fun setModernDimmingStrength(strength: ModernDimmingStrength) = edit {
        it[Keys.modernDimmingStrength] = strength.storageValue
    }

    suspend fun resetModernPlayerAppearance() = edit { preferences ->
        preferences.clearModernPlayerAppearance()
    }

    suspend fun setReplayGainMode(mode: ReplayGainMode) = edit {
        it[Keys.replayGainMode] = mode.name
    }

    suspend fun setAudioOffloadPreference(preference: AudioOffloadPreference) = edit {
        it[Keys.audioOffloadPreference] = preference.name
    }

    suspend fun setSmoothPlayPauseEnabled(enabled: Boolean) = edit {
        it[Keys.smoothPlayPauseEnabled] = enabled
    }

    suspend fun setCrossfadeEnabled(enabled: Boolean) = edit {
        it[Keys.crossfadeEnabled] = enabled
    }

    suspend fun setCrossfadeDurationMs(durationMs: Int) = edit {
        it[Keys.crossfadeDurationMs] = CrossfadePreferences.clampDurationMs(durationMs)
    }

    suspend fun setPreserveAlbumTransitions(enabled: Boolean) = edit {
        it[Keys.preserveAlbumTransitions] = enabled
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) = edit {
        it[Keys.equalizerEnabled] = enabled
    }

    suspend fun setEqualizerMode(mode: EqualizerMode) = edit {
        it[Keys.equalizerMode] = mode.name
    }

    suspend fun setEqualizerPreampDb(preampDb: Double) = edit {
        val updated = decodeAppPreferences(it).equalizerPreferences
            .withPreampDb(preampDb)
        it[Keys.equalizerPreampDb] = updated.preampDb
    }

    suspend fun setEqualizerAutomaticHeadroomEnabled(
        enabled: Boolean
    ) = edit {
        it[Keys.equalizerAutomaticHeadroom] = enabled
    }

    suspend fun setLimiterEnabled(enabled: Boolean) = edit {
        it[Keys.limiterEnabled] = enabled
    }

    suspend fun setLimiterCeilingDbfs(ceilingDbfs: Double) = edit {
        val updated = decodeAppPreferences(it)
            .equalizerPreferences
            .withLimiterCeilingDbfs(ceilingDbfs)
        it[Keys.limiterCeilingDbfs] =
            updated.limiterCeilingDbfs
    }

    suspend fun setEqualizerBandGainDb(
        index: Int,
        gainDb: Double
    ) = edit { preferences ->
        val updated = decodeAppPreferences(preferences)
            .equalizerPreferences
            .withBandGainDb(index, gainDb)
        preferences[Keys.equalizerBandGains[index]] =
            updated.bandGainsDb[index]
    }

    suspend fun replaceEqualizerCurve(
        preampDb: Double,
        automaticHeadroomEnabled: Boolean,
        bandGainsDb: List<Double>
    ) = edit { preferences ->
        val updated = decodeAppPreferences(preferences)
            .equalizerPreferences.withCurve(
                preampDb = preampDb,
                automaticHeadroomEnabled =
                    automaticHeadroomEnabled,
                bandGainsDb = bandGainsDb
            )
        preferences.writeEqualizerPreferences(updated)
    }

    suspend fun replaceEqualizerPreferences(
        equalizerPreferences: EqualizerPreferencesState
    ) = edit { preferences ->
        preferences.writeEqualizerPreferences(
            equalizerPreferences.copy(
                bandGainsDb =
                    equalizerPreferences.bandGainsDb.toList(),
                userPresets =
                    equalizerPreferences.userPresets.toList(),
                parametricState =
                    equalizerPreferences.parametricState.copy(
                        filters = equalizerPreferences
                            .parametricState.filters.toList(),
                        userPresets = equalizerPreferences
                            .parametricState.userPresets.toList()
                    )
            )
        )
    }

    suspend fun replaceParametricEqualizerState(
        state: ParametricEqualizerState
    ) = edit { preferences ->
        val current = decodeAppPreferences(preferences)
            .equalizerPreferences
        preferences.writeEqualizerPreferences(
            current.withParametricState(state)
        )
    }

    suspend fun saveParametricEqualizerPreset(
        name: String,
        curve: ParametricEqualizerState? = null
    ): ParametricEqualizerPreset {
        lateinit var preset: ParametricEqualizerPreset
        dataStore.edit { preferences ->
            val current = decodeAppPreferences(preferences)
                .equalizerPreferences
            val durableParametric = current.parametricState
            val source = curve?.copy(
                userPresets = durableParametric.userPresets
            ) ?: durableParametric
            preset = ParametricEqualizerPresets.createUserPreset(
                name = name,
                state = source
            )
            preferences.writeEqualizerPreferences(
                current.withParametricState(
                    source.copy(
                        userPresets =
                            durableParametric.userPresets + preset
                    )
                )
            )
        }
        return preset
    }

    /**
     * Applies and/or saves an imported profile in one DataStore transaction.
     * Existing Graphic, limiter, global-enable, and offload state is retained.
     */
    suspend fun importParametricEqualizerProfile(
        curve: ParametricEqualizerState,
        presetName: String? = null,
        apply: Boolean
    ): ParametricEqualizerPreset? {
        var createdPreset: ParametricEqualizerPreset? = null
        dataStore.edit { preferences ->
            val current = decodeAppPreferences(preferences)
                .equalizerPreferences
            val currentParametric = current.parametricState
            val source = curve.copy(
                userPresets = currentParametric.userPresets
            )
            createdPreset = presetName?.let { name ->
                ParametricEqualizerPresets.createUserPreset(
                    name = name,
                    state = source
                )
            }
            val presets = currentParametric.userPresets +
                    listOfNotNull(createdPreset)
            val parametric = if (apply) {
                source.copy(userPresets = presets)
            } else {
                currentParametric.copy(userPresets = presets)
            }
            preferences.writeEqualizerPreferences(
                current.copy(
                    mode = if (apply) {
                        EqualizerMode.PARAMETRIC
                    } else {
                        current.mode
                    },
                    parametricState = parametric
                )
            )
        }
        return createdPreset
    }

    suspend fun renameParametricEqualizerPreset(
        presetId: String,
        newName: String
    ) = edit { preferences ->
        val current = decodeAppPreferences(preferences)
            .equalizerPreferences
        val parametric = current.parametricState
        val presets =
            ParametricEqualizerPresets.renameUserPreset(
                presetId = presetId,
                newName = newName,
                userPresets = parametric.userPresets
            )
        preferences.writeEqualizerPreferences(
            current.withParametricState(
                parametric.copy(userPresets = presets)
            )
        )
    }

    suspend fun deleteParametricEqualizerPreset(
        presetId: String
    ) = edit { preferences ->
        val current = decodeAppPreferences(preferences)
            .equalizerPreferences
        val parametric = current.parametricState
        require(
            parametric.userPresets.any { preset ->
                preset.id == presetId
            }
        ) {
            "Unknown parametric preset ID: $presetId"
        }
        preferences.writeEqualizerPreferences(
            current.withParametricState(
                parametric.copy(
                    userPresets =
                        parametric.userPresets.filterNot { preset ->
                            preset.id == presetId
                        }
                )
            )
        )
    }

    suspend fun saveUserEqualizerPreset(
        name: String,
        curve: EqualizerPreferencesState? = null
    ): UserEqualizerPreset {
        lateinit var preset: UserEqualizerPreset
        dataStore.edit { preferences ->
            val current = decodeAppPreferences(preferences)
                .equalizerPreferences
            val source = curve?.copy(
                userPresets = current.userPresets
            ) ?: current
            preset = GraphicEqualizerPresets.createUserPreset(
                name = name,
                state = source
            )
            preferences.writeEqualizerPreferences(
                source.copy(
                    userPresets =
                        current.userPresets + preset
                )
            )
        }
        return preset
    }

    suspend fun renameUserEqualizerPreset(
        presetId: String,
        newName: String
    ) = edit { preferences ->
        val current = decodeAppPreferences(preferences)
            .equalizerPreferences
        val updated = GraphicEqualizerPresets.renameUserPreset(
            presetId = presetId,
            newName = newName,
            userPresets = current.userPresets
        )
        preferences.writeUserEqualizerPresets(updated)
    }

    suspend fun deleteUserEqualizerPreset(
        presetId: String
    ) = edit { preferences ->
        val current = decodeAppPreferences(preferences)
            .equalizerPreferences
            .userPresets
        require(current.any { preset -> preset.id == presetId }) {
            "Unknown user equalizer preset ID: $presetId"
        }
        preferences.writeUserEqualizerPresets(
            current.filterNot { preset -> preset.id == presetId }
        )
    }

    suspend fun replaceUserEqualizerPresets(
        userPresets: List<UserEqualizerPreset>
    ) = edit { preferences ->
        EqualizerPreferencesState(
            userPresets = userPresets
        )
        preferences.writeUserEqualizerPresets(userPresets)
    }

    suspend fun setLibraryFolderSelection(selection: FolderSelection) = edit {
        it[Keys.folderSelectionMode] = selection.mode.name
        it[Keys.selectedLibraryFolders] = selection.toStoredFolders()
    }

    @Deprecated("Use setLibraryFolderSelection so an empty custom selection is unambiguous.")
    suspend fun setSelectedLibraryFolders(folders: Set<String>) =
        setLibraryFolderSelection(FolderSelection.fromStored(null, folders))

    suspend fun setLibraryView(
        category: LibraryViewCategory,
        mode: LibraryViewMode,
        gridColumnCount: Int
    ) = edit { preferences ->
        preferences[Keys.viewMode(category)] = mode.storageValue
        preferences[Keys.gridColumns(category)] = LibraryGridColumns.normalize(gridColumnCount)
    }

    suspend fun addHomePin(pin: HomePin) = edit { preferences ->
        val current = decodeAppPreferences(preferences).homePins
        val updated = sanitizeHomePins(current + pin)
        preferences.writeHomePins(updated)
    }

    suspend fun replaceHomePin(index: Int, pin: HomePin) = edit { preferences ->
        val current = decodeAppPreferences(preferences).homePins
        if (index !in current.indices) return@edit
        val updated = current.toMutableList().apply { this[index] = pin }
        preferences.writeHomePins(sanitizeHomePins(updated))
    }

    suspend fun removeHomePin(pinId: String) = edit { preferences ->
        val current = decodeAppPreferences(preferences).homePins
        preferences.writeHomePins(current.filterNot { pin -> pin.id == pinId })
    }

    suspend fun removeHomePinsForPlaylist(playlistId: Long) = edit { preferences ->
        val current = decodeAppPreferences(preferences).homePins
        preferences.writeHomePins(
            current.filterNot { pin ->
                pin.type == HomePinType.PLAYLIST && pin.playlistId == playlistId
            }
        )
    }

    suspend fun moveHomePin(pinId: String, offset: Int) = edit { preferences ->
        if (offset == 0) return@edit
        val current = decodeAppPreferences(preferences).homePins.toMutableList()
        val fromIndex = current.indexOfFirst { pin -> pin.id == pinId }
        if (fromIndex < 0) return@edit
        val toIndex = (fromIndex + offset).coerceIn(current.indices)
        if (toIndex == fromIndex) return@edit
        val pin = current.removeAt(fromIndex)
        current.add(toIndex, pin)
        preferences.writeHomePins(current)
    }

    suspend fun setShowRecentlyAddedOnHome(show: Boolean) = edit { preferences ->
        preferences[Keys.showRecentlyAddedOnHome] = show
    }

    suspend fun setThemeTokenOverrides(
        theme: PlayerTheme,
        overrides: PlayerThemeTokenOverrides
    ) = edit { preferences ->
        Keys.themeFields.forEach { field -> preferences.remove(Keys.themeColor(theme, field)) }
        preferences.putColor(theme, Keys.SHELL, overrides.shellColor)
        preferences.putColor(theme, Keys.ACCENT, overrides.accentColor)
        preferences.putColor(theme, Keys.DISPLAY_BACKGROUND, overrides.displayBackgroundColor)
        preferences.putColor(theme, Keys.DISPLAY_TEXT, overrides.displayTextColor)
        preferences.putColor(theme, Keys.SECONDARY_ACCENT, overrides.secondaryAccentColor)
    }

    suspend fun clearThemeTokenOverrides(theme: PlayerTheme) = edit { preferences ->
        Keys.themeFields.forEach { field -> preferences.remove(Keys.themeColor(theme, field)) }
    }

    suspend fun replaceAll(restored: AppPreferencesState) = edit { preferences ->
        preferences.clear()
        preferences[Keys.selectedPlayerTheme] = restored.selectedPlayerTheme.id
        preferences[Keys.modernArtworkTransitionStyle] =
            restored.modernArtworkTransitionStyle.storageValue
        preferences.writeModernPlayerAppearance(restored.modernPlayerAppearance)
        preferences[Keys.replayGainMode] = restored.replayGainMode.name
        preferences[Keys.audioOffloadPreference] = restored.audioOffloadPreference.name
        preferences[Keys.smoothPlayPauseEnabled] = restored.smoothPlayPauseEnabled
        preferences[Keys.crossfadeEnabled] = restored.crossfadeEnabled
        preferences[Keys.crossfadeDurationMs] =
            CrossfadePreferences.clampDurationMs(restored.crossfadeDurationMs)
        preferences[Keys.preserveAlbumTransitions] = restored.preserveAlbumTransitions
        preferences.writeEqualizerPreferences(
            restored.equalizerPreferences
        )
        preferences[Keys.folderSelectionMode] = restored.folderSelectionMode.name
        preferences[Keys.selectedLibraryFolders] = restored.selectedLibraryFolders.toSet()
        LibraryViewCategory.entries.forEach { category ->
            val (mode, columns) = restored.libraryView(category)
            preferences[Keys.viewMode(category)] = mode.storageValue
            preferences[Keys.gridColumns(category)] = LibraryGridColumns.normalize(columns)
        }
        preferences.writeHomePins(restored.homePins)
        preferences[Keys.showRecentlyAddedOnHome] = restored.showRecentlyAddedOnHome
        restored.playerThemeTokenOverrides.forEach { (theme, overrides) ->
            preferences.putColor(theme, Keys.SHELL, overrides.shellColor)
            preferences.putColor(theme, Keys.ACCENT, overrides.accentColor)
            preferences.putColor(theme, Keys.DISPLAY_BACKGROUND, overrides.displayBackgroundColor)
            preferences.putColor(theme, Keys.DISPLAY_TEXT, overrides.displayTextColor)
            preferences.putColor(theme, Keys.SECONDARY_ACCENT, overrides.secondaryAccentColor)
        }
    }

    private suspend inline fun edit(crossinline transform: (MutablePreferences) -> Unit) {
        dataStore.edit { preferences -> transform(preferences) }
    }

    companion object {
        private const val DATASTORE_FILE = "app_preferences.preferences_pb"
        private val LEGACY_STORES = listOf(
            "player_theme_preferences",
            "player_theme_token_preferences",
            "modern_player_preferences",
            "replay_gain_preferences",
            "library_preferences",
            "library_view_preferences"
        )

        @Volatile private var instance: AppPreferencesRepository? = null

        fun getInstance(context: Context): AppPreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }
        }

        internal fun create(
            context: Context,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            dataStoreFileName: String = DATASTORE_FILE,
            legacyStores: List<String> = LEGACY_STORES
        ): AppPreferencesRepository {
            val dataStore = PreferenceDataStoreFactory.create(
                migrations = legacyStores.map { name ->
                    SharedPreferencesMigration(context, name)
                },
                scope = scope,
                produceFile = { context.preferencesDataStoreFile(dataStoreFileName) }
            )
            return AppPreferencesRepository(dataStore, scope)
        }
    }
}

internal fun decodeAppPreferences(preferences: Preferences): AppPreferencesState {
    val storedFolders = preferences[Keys.selectedLibraryFolders]?.toSet().orEmpty()
    val folderSelection = FolderSelection.fromStored(
        storedMode = preferences[Keys.folderSelectionMode],
        storedFolders = storedFolders
    )
    return AppPreferencesState(
        selectedPlayerTheme = PlayerTheme.fromId(preferences[Keys.selectedPlayerTheme]),
        playerThemeTokenOverrides = PlayerTheme.entries.associateWith { emptyOverrides() }
            .mapValues { (theme, _) ->
                PlayerThemeTokenOverrides(
                    shellColor = preferences.color(theme, Keys.SHELL),
                    accentColor = preferences.color(theme, Keys.ACCENT),
                    displayBackgroundColor = preferences.color(theme, Keys.DISPLAY_BACKGROUND),
                    displayTextColor = preferences.color(theme, Keys.DISPLAY_TEXT),
                    secondaryAccentColor = preferences.color(theme, Keys.SECONDARY_ACCENT)
                )
            }
            .filterValues { it != emptyOverrides() },
        modernArtworkTransitionStyle = ModernArtworkTransitionStyle.fromStorageValue(
            preferences[Keys.modernArtworkTransitionStyle]
        ),
        modernPlayerAppearance = ModernPlayerAppearance(
            seekbar = ModernSeekbarAppearance(
                style = ModernSeekbarStyle.fromStorageValue(
                    preferences[Keys.modernSeekbarStyle]
                ),
                waveformSize = ModernWaveformSize.fromStorageValue(
                    preferences[Keys.modernWaveformSize]
                ),
                waveformDensity = ModernWaveformDensity.fromStorageValue(
                    preferences[Keys.modernWaveformDensity]
                ),
                colorMode = ModernSeekbarColorMode.fromStorageValue(
                    preferences[Keys.modernSeekbarColorMode]
                )
            ),
            background = ModernBackgroundAppearance(
                style = ModernBackgroundStyle.fromStorageValue(
                    preferences[Keys.modernBackgroundStyle]
                ),
                blurStrength = ModernBlurStrength.fromStorageValue(
                    preferences[Keys.modernBlurStrength]
                ),
                dimmingStrength = ModernDimmingStrength.fromStorageValue(
                    preferences[Keys.modernDimmingStrength]
                ),
                solidColorArgb = sanitizeModernSolidColorArgb(
                    preferences[Keys.modernSolidColorArgb]
                )
            ),
            artwork = ModernArtworkAppearance(
                shape = ModernArtworkShape.fromStorageValue(
                    preferences[Keys.modernArtworkShape]
                ),
                size = ModernArtworkSize.fromStorageValue(
                    preferences[Keys.modernArtworkSize]
                ),
                fit = ModernArtworkFit.fromStorageValue(
                    preferences[Keys.modernArtworkFit]
                ),
                shadow = ModernArtworkShadow.fromStorageValue(
                    preferences[Keys.modernArtworkShadow]
                )
            ),
            controls = ModernControlAppearance(
                style = ModernControlStyle.fromStorageValue(
                    preferences[Keys.modernControlStyle]
                ),
                size = ModernControlSize.fromStorageValue(
                    preferences[Keys.modernControlSize]
                ),
                accent = ModernControlAccent.fromStorageValue(
                    preferences[Keys.modernControlAccent]
                )
            ),
            layout = ModernLayoutAppearance(
                density = ModernLayoutDensity.fromStorageValue(
                    preferences[Keys.modernLayoutDensity]
                ),
                metadataAlignment = ModernMetadataAlignment.fromStorageValue(
                    preferences[Keys.modernMetadataAlignment]
                ),
                showAudioQualityBadge = preferences[Keys.modernShowAudioQualityBadge] ?: true
            )
        ),
        replayGainMode = runCatching {
            ReplayGainMode.valueOf(preferences[Keys.replayGainMode].orEmpty())
        }.getOrDefault(ReplayGainMode.OFF),
        audioOffloadPreference = AudioOffloadPreference.fromStorageValue(
            preferences[Keys.audioOffloadPreference]
        ),
        smoothPlayPauseEnabled = preferences[Keys.smoothPlayPauseEnabled] ?: true,
        crossfadeEnabled = preferences[Keys.crossfadeEnabled] ?: false,
        crossfadeDurationMs = CrossfadePreferences.clampDurationMs(
            preferences[Keys.crossfadeDurationMs]
                ?: CrossfadePreferences.DEFAULT_DURATION_MS
        ),
        preserveAlbumTransitions = preferences[Keys.preserveAlbumTransitions] ?: true,
        equalizerPreferences = decodeEqualizerPreferences(preferences),
        folderSelectionMode = folderSelection.mode,
        selectedLibraryFolders = folderSelection.toStoredFolders(),
        songsViewMode = LibraryViewMode.fromStorageValue(preferences[Keys.songsViewMode]),
        albumsViewMode = LibraryViewMode.fromStorageValue(preferences[Keys.albumsViewMode]),
        artistsViewMode = LibraryViewMode.fromStorageValue(preferences[Keys.artistsViewMode]),
        playlistsViewMode = LibraryViewMode.fromStorageValue(preferences[Keys.playlistsViewMode]),
        songsGridColumnCount = LibraryGridColumns.normalize(
            preferences[Keys.songsGridColumns] ?: LibraryGridColumns.DEFAULT
        ),
        albumsGridColumnCount = LibraryGridColumns.normalize(
            preferences[Keys.albumsGridColumns] ?: LibraryGridColumns.DEFAULT
        ),
        artistsGridColumnCount = LibraryGridColumns.normalize(
            preferences[Keys.artistsGridColumns] ?: LibraryGridColumns.DEFAULT
        ),
        playlistsGridColumnCount = LibraryGridColumns.normalize(
            preferences[Keys.playlistsGridColumns] ?: LibraryGridColumns.DEFAULT
        ),
        homePins = preferences[Keys.homePins]
            ?.let(::decodeHomePins)
            .orEmpty(),
        showRecentlyAddedOnHome = preferences[Keys.showRecentlyAddedOnHome] ?: true,
        isLoaded = true
    )
}

private fun decodeHomePins(encoded: String): List<HomePin> = sanitizeHomePins(
    runCatching {
        preferencesJson.decodeFromString<List<HomePin>>(encoded)
    }.getOrDefault(emptyList())
)

private fun MutablePreferences.writeHomePins(pins: List<HomePin>) {
    val sanitized = sanitizeHomePins(pins)
    if (sanitized.isEmpty()) {
        remove(Keys.homePins)
    } else {
        this[Keys.homePins] = preferencesJson.encodeToString(sanitized)
    }
}

internal fun MutablePreferences.writeModernPlayerAppearance(
    appearance: ModernPlayerAppearance
) {
    this[Keys.modernSeekbarStyle] = appearance.seekbar.style.storageValue
    this[Keys.modernWaveformSize] = appearance.seekbar.waveformSize.storageValue
    this[Keys.modernWaveformDensity] = appearance.seekbar.waveformDensity.storageValue
    this[Keys.modernSeekbarColorMode] = appearance.seekbar.colorMode.storageValue
    this[Keys.modernBackgroundStyle] = appearance.background.style.storageValue
    this[Keys.modernBlurStrength] = appearance.background.blurStrength.storageValue
    this[Keys.modernDimmingStrength] = appearance.background.dimmingStrength.storageValue
    this[Keys.modernSolidColorArgb] = sanitizeModernSolidColorArgb(
        appearance.background.solidColorArgb
    )
    this[Keys.modernArtworkShape] = appearance.artwork.shape.storageValue
    this[Keys.modernArtworkSize] = appearance.artwork.size.storageValue
    this[Keys.modernArtworkFit] = appearance.artwork.fit.storageValue
    this[Keys.modernArtworkShadow] = appearance.artwork.shadow.storageValue
    this[Keys.modernControlStyle] = appearance.controls.style.storageValue
    this[Keys.modernControlSize] = appearance.controls.size.storageValue
    this[Keys.modernControlAccent] = appearance.controls.accent.storageValue
    this[Keys.modernLayoutDensity] = appearance.layout.density.storageValue
    this[Keys.modernMetadataAlignment] = appearance.layout.metadataAlignment.storageValue
    this[Keys.modernShowAudioQualityBadge] = appearance.layout.showAudioQualityBadge
}

internal fun MutablePreferences.clearModernPlayerAppearance() {
    listOf(
        Keys.modernSeekbarStyle,
        Keys.modernWaveformSize,
        Keys.modernWaveformDensity,
        Keys.modernSeekbarColorMode,
        Keys.modernBackgroundStyle,
        Keys.modernBlurStrength,
        Keys.modernDimmingStrength
    ).forEach(::remove)
    remove(Keys.modernSolidColorArgb)
    remove(Keys.modernArtworkShape)
    remove(Keys.modernArtworkSize)
    remove(Keys.modernArtworkFit)
    remove(Keys.modernArtworkShadow)
    remove(Keys.modernControlStyle)
    remove(Keys.modernControlSize)
    remove(Keys.modernControlAccent)
    remove(Keys.modernLayoutDensity)
    remove(Keys.modernMetadataAlignment)
    remove(Keys.modernShowAudioQualityBadge)
}

private fun emptyOverrides() = PlayerThemeTokenOverrides()

private fun AppPreferencesState.libraryView(
    category: LibraryViewCategory
): Pair<LibraryViewMode, Int> = when (category) {
    LibraryViewCategory.SONGS -> songsViewMode to songsGridColumnCount
    LibraryViewCategory.ALBUMS -> albumsViewMode to albumsGridColumnCount
    LibraryViewCategory.ARTISTS -> artistsViewMode to artistsGridColumnCount
    LibraryViewCategory.PLAYLISTS -> playlistsViewMode to playlistsGridColumnCount
}

private fun Preferences.color(theme: PlayerTheme, field: String): Color? {
    val stored = this[Keys.themeColor(theme, field)] ?: return null
    if (stored.length != 9 || stored.first() != '#') return null
    return stored.drop(1).toUIntOrNull(16)?.let { Color(it.toInt()) }
}

private fun MutablePreferences.putColor(
    theme: PlayerTheme,
    field: String,
    color: Color?
) {
    color ?: return
    val encoded = color.toArgb().toUInt().toString(16).padStart(8, '0').uppercase()
    this[Keys.themeColor(theme, field)] = "#$encoded"
}

private fun decodeEqualizerPreferences(
    preferences: Preferences
): EqualizerPreferencesState {
    val default = EqualizerPreferencesState()
    val preampDb = preferences[Keys.equalizerPreampDb]
        ?.validNormalizedPreampOrNull()
        ?: default.preampDb
    val bandGainsDb = Keys.equalizerBandGains.mapIndexed {
            index,
            key ->
        preferences[key]
            ?.validNormalizedBandOrNull()
            ?: default.bandGainsDb[index]
    }
    val userPresets = preferences[Keys.equalizerUserPresets]
        ?.let(::decodeUserEqualizerPresets)
        .orEmpty()
    val mode = preferences[Keys.equalizerMode]
        ?.let { stored ->
            runCatching {
                EqualizerMode.valueOf(stored)
            }.getOrNull()
        }
        ?: EqualizerMode.GRAPHIC
    val parametricState =
        preferences[Keys.parametricEqualizerState]
            ?.let(::decodeParametricEqualizerState)
            ?: ParametricEqualizerState()
    val limiterConfiguration = runCatching {
        LimiterConfiguration(
            enabled = preferences[Keys.limiterEnabled] ?: false,
            ceilingDbfs =
                preferences[Keys.limiterCeilingDbfs]
                    ?: default.limiterCeilingDbfs
        )
    }.getOrElse {
        LimiterConfiguration()
    }
    return EqualizerPreferencesState(
        enabled = preferences[Keys.equalizerEnabled]
            ?: default.enabled,
        preampDb = preampDb,
        automaticHeadroomEnabled =
            preferences[Keys.equalizerAutomaticHeadroom]
                ?: default.automaticHeadroomEnabled,
        bandGainsDb = bandGainsDb,
        userPresets = userPresets,
        mode = mode,
        parametricState = parametricState,
        limiterEnabled = limiterConfiguration.enabled,
        limiterCeilingDbfs =
            limiterConfiguration.ceilingDbfs
    )
}

private fun decodeUserEqualizerPresets(
    encoded: String
): List<UserEqualizerPreset> {
    val decoded = runCatching {
        preferencesJson.decodeFromString<
                List<StoredUserEqualizerPreset>
                >(encoded)
    }.getOrDefault(emptyList())
    val names = mutableSetOf<String>()
    val ids = mutableSetOf<String>()
    return decoded.mapNotNull { stored ->
        runCatching {
            UserEqualizerPreset(
                id = stored.id,
                name = stored.name,
                preampDb = normalizeEqualizerDb(
                    stored.preampDb
                ),
                automaticHeadroomEnabled =
                    stored.automaticHeadroomEnabled,
                bandGainsDb =
                    normalizeBandGains(stored.bandGainsDb)
            )
        }.getOrNull()
    }.filter { preset ->
        preset.name.lowercase() !in
                GraphicEqualizerPresets.builtInNamesLowercase &&
                ids.add(preset.id) &&
                names.add(preset.name.lowercase())
    }
}

private fun MutablePreferences.writeEqualizerPreferences(
    state: EqualizerPreferencesState
) {
    this[Keys.equalizerEnabled] = state.enabled
    this[Keys.equalizerPreampDb] = state.preampDb
    this[Keys.equalizerAutomaticHeadroom] =
        state.automaticHeadroomEnabled
    Keys.equalizerBandGains.forEachIndexed { index, key ->
        this[key] = state.bandGainsDb[index]
    }
    this[Keys.limiterEnabled] = state.limiterEnabled
    this[Keys.limiterCeilingDbfs] =
        state.limiterCeilingDbfs
    this[Keys.equalizerMode] = state.mode.name
    writeParametricEqualizerState(state.parametricState)
    writeUserEqualizerPresets(state.userPresets)
}

private fun decodeParametricEqualizerState(
    encoded: String
): ParametricEqualizerState {
    return runCatching {
        preferencesJson.decodeFromString<StoredParametricEqualizerState>(
            encoded
        ).toDomain()
    }.getOrDefault(ParametricEqualizerState())
}

private fun MutablePreferences.writeParametricEqualizerState(
    state: ParametricEqualizerState
) {
    this[Keys.parametricEqualizerState] =
        preferencesJson.encodeToString(state.toStored())
}

private fun MutablePreferences.writeUserEqualizerPresets(
    presets: List<UserEqualizerPreset>
) {
    val validated = EqualizerPreferencesState(
        userPresets = presets
    ).userPresets
    this[Keys.equalizerUserPresets] = preferencesJson.encodeToString(
        validated.map { preset ->
            StoredUserEqualizerPreset(
                id = preset.id,
                name = preset.name,
                preampDb = preset.preampDb,
                automaticHeadroomEnabled =
                    preset.automaticHeadroomEnabled,
                bandGainsDb = preset.bandGainsDb
            )
        }
    )
}

private fun Double.validNormalizedPreampOrNull(): Double? =
    runCatching {
        EqualizerPreferencesState().withPreampDb(this).preampDb
    }.getOrNull()

private fun Double.validNormalizedBandOrNull(): Double? =
    runCatching {
        EqualizerPreferencesState()
            .withBandGainDb(0, this)
            .bandGainsDb[0]
    }.getOrNull()

@Serializable
private data class StoredUserEqualizerPreset(
    val id: String,
    val name: String,
    val preampDb: Double,
    val automaticHeadroomEnabled: Boolean,
    val bandGainsDb: List<Double>
)

@Serializable
private data class StoredParametricEqualizerState(
    val preampDb: Double,
    val automaticHeadroomEnabled: Boolean,
    val filters: List<StoredParametricFilter>,
    val userPresets: List<StoredParametricPreset>
)

@Serializable
private data class StoredParametricPreset(
    val id: String,
    val name: String,
    val preampDb: Double,
    val automaticHeadroomEnabled: Boolean,
    val filters: List<StoredParametricFilter>
)

@Serializable
private data class StoredParametricFilter(
    val type: String,
    val id: String,
    val enabled: Boolean,
    val frequencyHz: Double,
    val gainDb: Double? = null,
    val q: Double? = null,
    val slope: Double? = null
)

private fun ParametricEqualizerState.toStored() =
    StoredParametricEqualizerState(
        preampDb = preampDb,
        automaticHeadroomEnabled = automaticHeadroomEnabled,
        filters = filters.map(ParametricFilter::toStored),
        userPresets = userPresets.map { preset ->
            StoredParametricPreset(
                id = preset.id,
                name = preset.name,
                preampDb = preset.preampDb,
                automaticHeadroomEnabled =
                    preset.automaticHeadroomEnabled,
                filters = preset.filters.map(
                    ParametricFilter::toStored
                )
            )
        }
    )

private fun StoredParametricEqualizerState.toDomain() =
    ParametricEqualizerState(
        preampDb = preampDb,
        automaticHeadroomEnabled = automaticHeadroomEnabled,
        filters = filters.map(StoredParametricFilter::toDomain),
        userPresets = userPresets.map { preset ->
            ParametricEqualizerPreset(
                id = preset.id,
                name = preset.name,
                preampDb = preset.preampDb,
                automaticHeadroomEnabled =
                    preset.automaticHeadroomEnabled,
                filters = preset.filters.map(
                    StoredParametricFilter::toDomain
                )
            )
        }
    )

private fun ParametricFilter.toStored(): StoredParametricFilter =
    when (this) {
        is ParametricFilter.Peaking -> StoredParametricFilter(
            type.name, id, enabled, frequencyHz,
            gainDb = gainDb, q = q
        )
        is ParametricFilter.LowShelf -> StoredParametricFilter(
            type.name, id, enabled, frequencyHz,
            gainDb = gainDb, slope = slope
        )
        is ParametricFilter.HighShelf -> StoredParametricFilter(
            type.name, id, enabled, frequencyHz,
            gainDb = gainDb, slope = slope
        )
        is ParametricFilter.LowPass -> StoredParametricFilter(
            type.name, id, enabled, frequencyHz, q = q
        )
        is ParametricFilter.HighPass -> StoredParametricFilter(
            type.name, id, enabled, frequencyHz, q = q
        )
        is ParametricFilter.Notch -> StoredParametricFilter(
            type.name, id, enabled, frequencyHz, q = q
        )
        is ParametricFilter.BandPass -> StoredParametricFilter(
            type.name, id, enabled, frequencyHz, q = q
        )
    }

private fun StoredParametricFilter.toDomain(): ParametricFilter {
    val parsedType = ParametricFilterType.valueOf(type)
    return when (parsedType) {
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
    }.let { filter ->
        require(
            when (filter) {
                is ParametricFilter.Peaking -> slope == null
                is ParametricFilter.LowShelf,
                is ParametricFilter.HighShelf -> q == null
                else -> gainDb == null && slope == null
            }
        )
        filter
    }
}

private val preferencesJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private object Keys {
    val selectedPlayerTheme = stringPreferencesKey("selected_player_theme")
    val modernArtworkTransitionStyle = stringPreferencesKey("artwork_transition_style")
    val modernSeekbarStyle = stringPreferencesKey("seekbar_style")
    val modernWaveformSize = stringPreferencesKey("modern_waveform_size")
    val modernWaveformDensity = stringPreferencesKey("modern_waveform_density")
    val modernSeekbarColorMode = stringPreferencesKey("modern_seekbar_color_mode")
    val modernBackgroundStyle = stringPreferencesKey("modern_background_style")
    val modernBlurStrength = stringPreferencesKey("modern_blur_strength")
    val modernDimmingStrength = stringPreferencesKey("modern_dimming_strength")
    val modernSolidColorArgb = longPreferencesKey("modern_solid_color_argb")
    val modernArtworkShape = stringPreferencesKey("modern_artwork_shape")
    val modernArtworkSize = stringPreferencesKey("modern_artwork_size")
    val modernArtworkFit = stringPreferencesKey("modern_artwork_fit")
    val modernArtworkShadow = stringPreferencesKey("modern_artwork_shadow")
    val modernControlStyle = stringPreferencesKey("modern_control_style")
    val modernControlSize = stringPreferencesKey("modern_control_size")
    val modernControlAccent = stringPreferencesKey("modern_control_accent")
    val modernLayoutDensity = stringPreferencesKey("modern_layout_density")
    val modernMetadataAlignment = stringPreferencesKey("modern_metadata_alignment")
    val modernShowAudioQualityBadge = booleanPreferencesKey("modern_show_audio_quality_badge")
    val replayGainMode = stringPreferencesKey("replay_gain_mode")
    val audioOffloadPreference = stringPreferencesKey("audio_offload_preference")
    val smoothPlayPauseEnabled = booleanPreferencesKey("smooth_play_pause_enabled")
    val crossfadeEnabled = booleanPreferencesKey("crossfade_enabled")
    val crossfadeDurationMs = intPreferencesKey("crossfade_duration_ms")
    val preserveAlbumTransitions = booleanPreferencesKey("preserve_album_transitions")
    val equalizerEnabled =
        booleanPreferencesKey("equalizer_enabled")
    val equalizerMode =
        stringPreferencesKey("equalizer_mode")
    val equalizerPreampDb =
        doublePreferencesKey("equalizer_preamp_db")
    val equalizerAutomaticHeadroom =
        booleanPreferencesKey("equalizer_automatic_headroom")
    val equalizerBandGains = List(10) { index ->
        doublePreferencesKey("equalizer_band_${index}_db")
    }
    val equalizerUserPresets =
        stringPreferencesKey("equalizer_user_presets_json")
    val parametricEqualizerState =
        stringPreferencesKey("equalizer_parametric_state_json")
    val limiterEnabled =
        booleanPreferencesKey("equalizer_limiter_enabled")
    val limiterCeilingDbfs =
        doublePreferencesKey("equalizer_limiter_ceiling_dbfs")
    val selectedLibraryFolders = stringSetPreferencesKey("selected_folders")
    val folderSelectionMode = stringPreferencesKey("folder_selection_mode")
    val songsViewMode = stringPreferencesKey("songs_view_mode")
    val albumsViewMode = stringPreferencesKey("albums_view_mode")
    val artistsViewMode = stringPreferencesKey("artists_view_mode")
    val playlistsViewMode = stringPreferencesKey("playlists_view_mode")
    val songsGridColumns = intPreferencesKey("songs_view_mode_columns")
    val albumsGridColumns = intPreferencesKey("albums_view_mode_columns")
    val artistsGridColumns = intPreferencesKey("artists_view_mode_columns")
    val playlistsGridColumns = intPreferencesKey("playlists_view_mode_columns")
    val homePins = stringPreferencesKey("home_pins_json")
    val showRecentlyAddedOnHome = booleanPreferencesKey("show_recently_added_on_home")

    const val SHELL = "shell"
    const val ACCENT = "accent"
    const val DISPLAY_BACKGROUND = "display_background"
    const val DISPLAY_TEXT = "display_text"
    const val SECONDARY_ACCENT = "secondary_accent"
    val themeFields = listOf(SHELL, ACCENT, DISPLAY_BACKGROUND, DISPLAY_TEXT, SECONDARY_ACCENT)

    fun themeColor(theme: PlayerTheme, field: String) =
        stringPreferencesKey("${theme.id}.$field")

    fun viewMode(category: LibraryViewCategory) = when (category) {
        LibraryViewCategory.SONGS -> songsViewMode
        LibraryViewCategory.ALBUMS -> albumsViewMode
        LibraryViewCategory.ARTISTS -> artistsViewMode
        LibraryViewCategory.PLAYLISTS -> playlistsViewMode
    }

    fun gridColumns(category: LibraryViewCategory) = when (category) {
        LibraryViewCategory.SONGS -> songsGridColumns
        LibraryViewCategory.ALBUMS -> albumsGridColumns
        LibraryViewCategory.ARTISTS -> artistsGridColumns
        LibraryViewCategory.PLAYLISTS -> playlistsGridColumns
    }
}
