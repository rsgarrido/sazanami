package io.github.rsgarrido.sazanami.ui.settings

import androidx.compose.foundation.background
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import io.github.rsgarrido.sazanami.data.preferences.CrossfadePreferences
import io.github.rsgarrido.sazanami.data.preferences.AppFont
import io.github.rsgarrido.sazanami.data.preferences.AppAppearance
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.audio.AudioOffloadPreference
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import io.github.rsgarrido.sazanami.ui.AppShellTypography
import io.github.rsgarrido.sazanami.ui.readableContentWidth
import io.github.rsgarrido.sazanami.ui.LocalFolderArtworkUi
import io.github.rsgarrido.sazanami.mediaaccess.folderArtworkLocationLabel
import io.github.rsgarrido.sazanami.ui.home.LocalHomePinUi
import io.github.rsgarrido.sazanami.ui.state.LibraryRefreshSummary
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkTransitionStyle
import io.github.rsgarrido.sazanami.ui.player.modern.descriptionRes
import io.github.rsgarrido.sazanami.ui.player.modern.labelRes
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLandscapeLayout
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearanceChoice
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokenField
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens
import io.github.rsgarrido.sazanami.ui.player.theme.customizationOptions
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    totalSongCount: Int,
    availableFolderCount: Int,
    folderSelectionMode: FolderSelectionMode,
    selectedFolderCount: Int,
    excludedFolderCount: Int,
    isLibraryRefreshing: Boolean,
    lastLibraryRefreshSummary: LibraryRefreshSummary?,
    libraryErrorMessage: String?,
    onBackClick: () -> Unit,
    onLibraryFoldersClick: () -> Unit,
    onScanLibraryClick: () -> Unit,
    onExportBackupClick: () -> Unit,
    onRestoreBackupClick: () -> Unit,
    onListeningHistoryImportClick: () -> Unit = {},
    onListeningHistoryReconciliationClick: () -> Unit = {},
    onDiagnosticsClick: () -> Unit,
    onTipsHelpClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    equalizerSummary: String,
    onEqualizerClick: () -> Unit,
    isSleepTimerActive: Boolean,
    sleepTimerDisplayText: String,
    onSleepTimerClick: () -> Unit,
    selectedPlayerTheme: PlayerTheme,
    selectedAppFont: AppFont = AppFont.SAZANAMI,
    onAppFontSelected: (AppFont) -> Unit = {},
    selectedAppAppearance: AppAppearance = AppAppearance.DARK,
    onAppAppearanceSelected: (AppAppearance) -> Unit = {},
    selectedPlayerThemeTokens: PlayerThemeTokens,
    onPlayerThemeSelected: (PlayerTheme) -> Unit,
    onUpdatePlayerThemeTokenOverride: (PlayerTheme, PlayerThemeTokenField, Color) -> Unit,
    onResetPlayerThemeTokenOverrides: (PlayerTheme) -> Unit,
    selectedModernArtworkTransitionStyle: ModernArtworkTransitionStyle,
    onModernArtworkTransitionStyleSelected: (ModernArtworkTransitionStyle) -> Unit,
    selectedModernPlayerAppearance: ModernPlayerAppearance,
    activeModernAppearanceChoice: ModernAppearanceChoice,
    selectedModernLandscapeLayout: ModernLandscapeLayout = ModernLandscapeLayout.BALANCED,
    onModernLandscapeLayoutSelected: (ModernLandscapeLayout) -> Unit = {},
    onModernAppearanceChoiceSelected: (ModernAppearanceChoice) -> Unit,
    onModernPlayerAppearanceEdited: ((ModernPlayerAppearance) -> ModernPlayerAppearance) -> Unit,
    onResetModernPlayerAppearance: () -> Unit,
    previewSong: Song?,
    selectedReplayGainMode: ReplayGainMode,
    onReplayGainModeSelected: (ReplayGainMode) -> Unit,
    selectedAudioOffloadPreference: AudioOffloadPreference,
    onAudioOffloadPreferenceSelected: (AudioOffloadPreference) -> Unit,
    smoothPlayPauseEnabled: Boolean = true,
    onSmoothPlayPauseEnabledChanged: (Boolean) -> Unit = {},
    crossfadeEnabled: Boolean = false,
    onCrossfadeEnabledChanged: (Boolean) -> Unit = {},
    crossfadeDurationMs: Int = CrossfadePreferences.DEFAULT_DURATION_MS,
    onCrossfadeDurationMsChanged: (Int) -> Unit = {},
    preserveAlbumTransitions: Boolean = true,
    onPreserveAlbumTransitionsChanged: (Boolean) -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier
) {
    var isUsbAudioVisible by rememberSaveable { mutableStateOf(false) }
    if (isUsbAudioVisible) {
        UsbAudioSettingsScreen(onBack = { isUsbAudioVisible = false },
            onDiagnostics = { isUsbAudioVisible = false; onDiagnosticsClick() }, modifier = modifier)
        return
    }
    var isPlayerThemeDialogVisible by remember { mutableStateOf(false) }
    var isFontDialogVisible by remember { mutableStateOf(false) }
    var isAppAppearanceDialogVisible by remember { mutableStateOf(false) }
    var isReplayGainDialogVisible by remember { mutableStateOf(false) }
    var isAudioOffloadDialogVisible by remember { mutableStateOf(false) }
    var isThemeCustomizationDialogVisible by remember { mutableStateOf(false) }
    var isArtworkTransitionDialogVisible by remember { mutableStateOf(false) }
    var isDefaultPlayerCustomizationVisible by remember { mutableStateOf(false) }
    var isEmbeddedArtworkOnlyDialogVisible by remember { mutableStateOf(false) }
    val homePinUi = LocalHomePinUi.current
    val folderArtworkUi = LocalFolderArtworkUi.current
    val context = LocalContext.current

    if (isDefaultPlayerCustomizationVisible) {
        DefaultPlayerCustomizationScreen(
            appearance = selectedModernPlayerAppearance,
            activeChoice = activeModernAppearanceChoice,
            landscapeLayout = selectedModernLandscapeLayout,
            onLandscapeLayoutSelected = onModernLandscapeLayoutSelected,
            previewSong = previewSong,
            onChoiceSelected = onModernAppearanceChoiceSelected,
            onAppearanceEdited = onModernPlayerAppearanceEdited,
            onReset = onResetModernPlayerAppearance,
            onBackClick = { isDefaultPlayerCustomizationVisible = false },
            modifier = modifier
        )
        return
    }

    val themeCustomizationOptions = selectedPlayerTheme.customizationOptions()
    val folderSelectionText = when {
        folderSelectionMode == FolderSelectionMode.ALL && excludedFolderCount == 0 ->
            pluralStringResource(R.plurals.settings_all_folder_sources, availableFolderCount, availableFolderCount)
        folderSelectionMode == FolderSelectionMode.ALL ->
            pluralStringResource(R.plurals.settings_all_except_folder_sources, availableFolderCount, excludedFolderCount, availableFolderCount)
        selectedFolderCount == 0 ->
            pluralStringResource(R.plurals.settings_no_folder_sources_selected, availableFolderCount, availableFolderCount)
        excludedFolderCount == 0 ->
            pluralStringResource(R.plurals.settings_folder_roots_selected, selectedFolderCount, selectedFolderCount)
        else ->
            stringResource(R.string.settings_folder_roots_selected_and_excluded, selectedFolderCount, excludedFolderCount)
    }
    val libraryScanSummary = when {
        isLibraryRefreshing -> stringResource(R.string.settings_scan_in_progress)
        libraryErrorMessage != null -> libraryErrorMessage
        lastLibraryRefreshSummary != null -> lastLibraryRefreshSummary.settingsSummary()
        else -> stringResource(R.string.settings_scan_idle)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .readableContentWidth()
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 10.dp, end = 20.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back)
                )
            }

            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.settings_screen_title),
                    style = AppShellTypography.ScreenTitle,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        SettingsSection(
            title = stringResource(R.string.settings_library_section),
            description = stringResource(R.string.settings_library_section_summary),
            icon = AppShellIcons.AlbumStack
        ) {
            SettingsRow(
                title = stringResource(R.string.settings_library_folders),
                summary = folderSelectionText,
                icon = AppShellIcons.Folder,
                onClick = onLibraryFoldersClick,
                emphasizeSummary = true,
                navigationContentDescription = stringResource(R.string.settings_open_library_folders)
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_folder_artwork),
                summary = folderArtworkLocationLabel(LocalResources.current, folderArtworkUi.state.treeUri),
                icon = AppShellIcons.AlbumStack,
                onClick = folderArtworkUi.onChooseFolder,
                emphasizeSummary = folderArtworkUi.state.hasFolderAccess,
                navigationContentDescription = if (folderArtworkUi.state.hasFolderAccess) {
                    stringResource(R.string.settings_change_folder_artwork_location)
                } else {
                    stringResource(R.string.settings_choose_folder_artwork_location)
                }
            )

            if (folderArtworkUi.state.hasFolderAccess) {
                SettingsDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_embedded_artwork_only),
                    summary = stringResource(R.string.settings_embedded_artwork_only_summary),
                    icon = AppShellIcons.AlbumStack,
                    onClick = {
                        isEmbeddedArtworkOnlyDialogVisible = true
                    }
                )
            }

            SettingsDivider()

            SettingsRow(
                title = if (isLibraryRefreshing) stringResource(R.string.settings_scanning_library) else stringResource(R.string.settings_scan_library),
                summary = libraryScanSummary,
                icon = Icons.Filled.Refresh,
                onClick = {
                    if (!isLibraryRefreshing) onScanLibraryClick()
                },
                emphasizeSummary = isLibraryRefreshing ||
                        libraryErrorMessage != null ||
                        lastLibraryRefreshSummary != null
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_songs),
                summary = pluralStringResource(R.plurals.settings_library_song_count, totalSongCount, totalSongCount),
                icon = AppShellIcons.MusicNote
            )
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = stringResource(R.string.settings_home_section),
            description = stringResource(R.string.settings_home_section_summary),
            icon = Icons.Filled.Home
        ) {
            SettingsRow(
                title = stringResource(R.string.settings_recently_added),
                summary = if (homePinUi.showRecentlyAddedOnHome) {
                    stringResource(R.string.settings_recently_added_shown)
                } else {
                    stringResource(R.string.settings_recently_added_hidden)
                },
                icon = AppShellIcons.AlbumStack,
                trailingContent = {
                    Switch(
                        checked = homePinUi.showRecentlyAddedOnHome,
                        onCheckedChange = homePinUi.onShowRecentlyAddedChanged
                    )
                }
            )

            SettingsFooterNote(
                text = stringResource(R.string.settings_home_pin_note)
            )
        }

        SettingsSectionSpacer()

        LyricsFolderSettings()

        SettingsSectionSpacer()

        SettingsSection(
            title = stringResource(R.string.settings_playback_section),
            description = stringResource(R.string.settings_playback_section_summary),
            icon = AppShellIcons.Equalizer
        ) {
            SettingsRow(
                title = stringResource(R.string.usb_settings_title),
                summary = stringResource(R.string.usb_settings_entry_summary),
                icon = AppShellIcons.AudioRoute,
                onClick = { isUsbAudioVisible = true },
                navigationContentDescription = stringResource(R.string.usb_settings_open)
            )
            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_smooth_play_pause),
                summary = stringResource(R.string.settings_smooth_play_pause_summary),
                icon = AppShellIcons.MusicNote,
                trailingContent = {
                    Switch(
                        checked = smoothPlayPauseEnabled,
                        onCheckedChange = onSmoothPlayPauseEnabledChanged
                    )
                }
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_crossfade),
                summary = stringResource(R.string.settings_crossfade_summary),
                icon = AppShellIcons.MusicNote,
                trailingContent = {
                    Switch(
                        checked = crossfadeEnabled,
                        onCheckedChange = onCrossfadeEnabledChanged
                    )
                }
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_crossfade_duration),
                summary = (crossfadeDurationMs / 1_000).let { seconds ->
                    pluralStringResource(R.plurals.settings_crossfade_seconds, seconds, seconds)
                },
                icon = AppShellIcons.Timer,
                trailingContent = {
                    Slider(
                        value = (crossfadeDurationMs / 1_000f).coerceIn(1f, 12f),
                        onValueChange = { seconds ->
                            onCrossfadeDurationMsChanged(
                                seconds.roundToInt().coerceIn(1, 12) * 1_000
                            )
                        },
                        valueRange = 1f..12f,
                        steps = 10,
                        enabled = crossfadeEnabled,
                        modifier = Modifier.width(150.dp)
                    )
                }
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_preserve_album_transitions),
                summary = stringResource(R.string.settings_preserve_album_transitions_summary),
                icon = AppShellIcons.AlbumStack,
                trailingContent = {
                    Switch(
                        checked = preserveAlbumTransitions,
                        onCheckedChange = onPreserveAlbumTransitionsChanged,
                        enabled = crossfadeEnabled
                    )
                }
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_equalizer),
                summary = equalizerSummary,
                icon = AppShellIcons.Equalizer,
                onClick = onEqualizerClick,
                emphasizeSummary = true,
                navigationContentDescription = stringResource(R.string.settings_open_equalizer)
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_replay_gain),
                summary = stringResource(selectedReplayGainMode.labelRes),
                icon = AppShellIcons.Gauge,
                onClick = { isReplayGainDialogVisible = true },
                emphasizeSummary = true,
                navigationContentDescription = stringResource(R.string.settings_open_replay_gain)
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_audio_offload),
                summary = stringResource(selectedAudioOffloadPreference.labelRes),
                icon = AppShellIcons.AudioRoute,
                onClick = { isAudioOffloadDialogVisible = true },
                emphasizeSummary = true,
                navigationContentDescription = stringResource(R.string.settings_open_audio_offload)
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_sleep_timer),
                summary = if (isSleepTimerActive) {
                    sleepTimerDisplayText
                } else {
                    stringResource(R.string.settings_sleep_timer_summary)
                },
                icon = AppShellIcons.Timer,
                onClick = onSleepTimerClick,
                emphasizeSummary = isSleepTimerActive,
                navigationContentDescription = stringResource(R.string.settings_open_sleep_timer)
            )

            SettingsDivider()

            SettingsFooterNote(
                text = stringResource(R.string.settings_audio_offload_note)
            )
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = stringResource(R.string.settings_player_section),
            description = stringResource(R.string.settings_player_section_summary),
            icon = AppShellIcons.Palette
        ) {
            SettingsRow(
                title = stringResource(R.string.settings_font),
                summary = selectedAppFont.localizedDisplayName(),
                icon = AppShellIcons.Palette,
                onClick = { isFontDialogVisible = true },
                emphasizeSummary = true,
                navigationContentDescription = stringResource(R.string.settings_choose_font)
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.app_appearance_title),
                summary = appAppearanceLabel(selectedAppAppearance),
                icon = AppShellIcons.Palette,
                onClick = { isAppAppearanceDialogVisible = true },
                emphasizeSummary = true,
                navigationContentDescription = stringResource(R.string.app_appearance_choose)
            )

            SettingsDivider()

            SettingsLanguageRow()

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_player_theme),
                summary = stringResource(selectedPlayerTheme.labelRes),
                icon = AppShellIcons.Deck,
                onClick = { isPlayerThemeDialogVisible = true },
                emphasizeSummary = true,
                navigationContentDescription = stringResource(R.string.settings_choose_player_theme)
            )

            if (selectedPlayerTheme == PlayerTheme.DEFAULT) {
                SettingsDivider()

                SettingsRow(
                    title = stringResource(R.string.settings_artwork_transition_style),
                    summary = stringResource(selectedModernArtworkTransitionStyle.labelRes),
                    icon = AppShellIcons.Transition,
                    onClick = { isArtworkTransitionDialogVisible = true },
                    emphasizeSummary = true,
                    navigationContentDescription = stringResource(R.string.settings_choose_artwork_transition_style)
                )

                SettingsDivider()

                SettingsRow(
                    title = stringResource(R.string.settings_customize_default_player),
                    summary = stringResource(R.string.settings_customize_default_player_summary),
                    icon = AppShellIcons.Palette,
                    onClick = { isDefaultPlayerCustomizationVisible = true },
                    navigationContentDescription = stringResource(R.string.settings_customize_default_player)
                )
            }

            if (themeCustomizationOptions.isNotEmpty()) {
                SettingsDivider()

                SettingsRow(
                    title = stringResource(R.string.settings_customize_theme_colors),
                    summary = stringResource(R.string.settings_theme_colors_summary, stringResource(selectedPlayerTheme.labelRes)),
                    icon = AppShellIcons.Palette,
                    onClick = { isThemeCustomizationDialogVisible = true },
                    navigationContentDescription = stringResource(R.string.settings_customize_theme_colors)
                )
            }
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = stringResource(R.string.settings_history_section),
            description = stringResource(R.string.settings_history_section_summary),
            icon = Icons.Filled.History
        ) {
            SettingsRow(
                title = stringResource(R.string.settings_import_history),
                summary = stringResource(R.string.settings_import_history_summary),
                icon = AppShellIcons.Restore,
                onClick = onListeningHistoryImportClick,
                navigationContentDescription = stringResource(R.string.settings_open_import_history)
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.settings_match_imported_tracks),
                summary = stringResource(R.string.settings_match_imported_tracks_summary),
                icon = AppShellIcons.Search,
                onClick = onListeningHistoryReconciliationClick,
                navigationContentDescription = stringResource(R.string.settings_open_imported_matching)
            )
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = stringResource(R.string.settings_data_section),
            description = stringResource(R.string.settings_data_section_summary),
            icon = AppShellIcons.Diagnostics
        ) {
            SettingsRow(
                title = stringResource(R.string.settings_export_backup),
                summary = stringResource(R.string.settings_export_backup_summary),
                icon = AppShellIcons.Export,
                onClick = onExportBackupClick,
                navigationContentDescription = stringResource(R.string.settings_export_backup_action)
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_restore_backup),
                summary = stringResource(R.string.settings_restore_backup_summary),
                icon = AppShellIcons.Restore,
                onClick = onRestoreBackupClick,
                navigationContentDescription = stringResource(R.string.settings_restore_backup_action)
            )

            SettingsDivider()

            SettingsRow(
                title = stringResource(R.string.settings_diagnostics),
                summary = stringResource(R.string.settings_diagnostics_summary),
                icon = AppShellIcons.Diagnostics,
                onClick = onDiagnosticsClick,
                navigationContentDescription = stringResource(R.string.settings_open_diagnostics)
            )
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = stringResource(R.string.settings_help_section_title),
            description = stringResource(R.string.settings_help_section_summary),
            icon = Icons.AutoMirrored.Filled.HelpOutline
        ) {
            SettingsRow(
                title = stringResource(R.string.settings_help_tips_title),
                summary = stringResource(R.string.settings_help_tips_summary),
                icon = Icons.Filled.TipsAndUpdates,
                onClick = onTipsHelpClick,
                navigationContentDescription = stringResource(R.string.settings_help_open_tips)
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.settings_help_issue_title),
                summary = stringResource(R.string.settings_help_issue_summary),
                icon = Icons.Filled.BugReport,
                onClick = { openSettingsUrl(context, SettingsLinks.BUG_REPORT) },
                trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.settings_help_feature_title),
                summary = stringResource(R.string.settings_help_feature_summary),
                icon = Icons.Filled.Lightbulb,
                onClick = { openSettingsUrl(context, SettingsLinks.FEATURE_REQUEST) },
                trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.settings_help_about_title),
                summary = stringResource(R.string.settings_help_about_summary),
                icon = AppShellIcons.Info,
                onClick = onAboutClick,
                navigationContentDescription = stringResource(R.string.settings_help_open_about)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (isFontDialogVisible) {
        AlertDialog(
            onDismissRequest = { isFontDialogVisible = false },
            title = { Text(text = stringResource(R.string.settings_font)) },
            text = {
                Column {
                    AppFont.entries.forEach { appFont ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAppFontSelected(appFont)
                                    isFontDialogVisible = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAppFont == appFont,
                                onClick = {
                                    onAppFontSelected(appFont)
                                    isFontDialogVisible = false
                                }
                            )

                            Column(
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(text = appFont.localizedDisplayName())

                                Text(
                                    text = appFont.localizedDescription(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isFontDialogVisible = false }) {
                    Text(text = stringResource(R.string.settings_close))
                }
            }
        )
    }

    if (isAppAppearanceDialogVisible) {
        AlertDialog(
            onDismissRequest = { isAppAppearanceDialogVisible = false },
            title = { Text(stringResource(R.string.app_appearance_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.app_appearance_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppAppearance.entries.forEach { appearance ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAppAppearanceSelected(appearance)
                                    isAppAppearanceDialogVisible = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAppAppearance == appearance,
                                onClick = {
                                    onAppAppearanceSelected(appearance)
                                    isAppAppearanceDialogVisible = false
                                }
                            )
                            Text(appAppearanceLabel(appearance))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isAppAppearanceDialogVisible = false }) {
                    Text(stringResource(R.string.app_appearance_close))
                }
            }
        )
    }

    if (isReplayGainDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                isReplayGainDialogVisible = false
            },
            title = {
                Text(text = stringResource(R.string.settings_replay_gain))
            },
            text = {
                Column {
                    ReplayGainMode.values().forEach { replayGainMode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onReplayGainModeSelected(replayGainMode)
                                    isReplayGainDialogVisible = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReplayGainMode == replayGainMode,
                                onClick = {
                                    onReplayGainModeSelected(replayGainMode)
                                    isReplayGainDialogVisible = false
                                }
                            )

                            Column(
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(text = stringResource(replayGainMode.labelRes))

                                Text(
                                    text = stringResource(replayGainMode.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isReplayGainDialogVisible = false
                    }
                ) {
                    Text(text = stringResource(R.string.settings_close))
                }
            }
        )
    }

    if (isAudioOffloadDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                isAudioOffloadDialogVisible = false
            },
            title = {
                Text(text = stringResource(R.string.settings_audio_offload))
            },
            text = {
                Column {
                    AudioOffloadPreference.entries.forEach { preference ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAudioOffloadPreferenceSelected(preference)
                                    isAudioOffloadDialogVisible = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAudioOffloadPreference == preference,
                                onClick = {
                                    onAudioOffloadPreferenceSelected(preference)
                                    isAudioOffloadDialogVisible = false
                                }
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(text = stringResource(preference.labelRes))
                                Text(
                                    text = if (preference == AudioOffloadPreference.AUTOMATIC) {
                                        stringResource(R.string.settings_offload_automatic_description)
                                    } else {
                                        stringResource(R.string.settings_offload_normal_description)
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isAudioOffloadDialogVisible = false }) {
                    Text(text = stringResource(R.string.settings_close))
                }
            }
        )
    }

    if (isEmbeddedArtworkOnlyDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                isEmbeddedArtworkOnlyDialogVisible = false
            },
            title = {
                Text(text = stringResource(R.string.settings_embedded_only_dialog_title))
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_embedded_only_dialog_message)
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isEmbeddedArtworkOnlyDialogVisible = false
                    }
                ) {
                    Text(text = stringResource(R.string.settings_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isEmbeddedArtworkOnlyDialogVisible = false
                        folderArtworkUi.onClearFolder()
                    }
                ) {
                    Text(text = stringResource(R.string.settings_use_embedded_only))
                }
            }
        )
    }

    if (
        isArtworkTransitionDialogVisible &&
        selectedPlayerTheme == PlayerTheme.DEFAULT
    ) {
        AlertDialog(
            onDismissRequest = {
                isArtworkTransitionDialogVisible = false
            },
            title = {
                Text(text = stringResource(R.string.settings_artwork_transition_style))
            },
            text = {
                Column {
                    ModernArtworkTransitionStyle.values().forEach { transitionStyle ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onModernArtworkTransitionStyleSelected(transitionStyle)
                                    isArtworkTransitionDialogVisible = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedModernArtworkTransitionStyle == transitionStyle,
                                onClick = {
                                    onModernArtworkTransitionStyleSelected(transitionStyle)
                                    isArtworkTransitionDialogVisible = false
                                }
                            )

                            Column(
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(text = stringResource(transitionStyle.labelRes))

                                Text(
                                    text = stringResource(transitionStyle.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isArtworkTransitionDialogVisible = false
                    }
                ) {
                    Text(text = stringResource(R.string.settings_close))
                }
            }
        )
    }

    if (isPlayerThemeDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                isPlayerThemeDialogVisible = false
            },
            title = {
                Text(text = stringResource(R.string.settings_player_theme))
            },
            text = {
                Column {
                    PlayerTheme.values().forEach { playerTheme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPlayerThemeSelected(playerTheme)
                                    isPlayerThemeDialogVisible = false
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPlayerTheme == playerTheme,
                                onClick = {
                                    onPlayerThemeSelected(playerTheme)
                                    isPlayerThemeDialogVisible = false
                                }
                            )

                            Text(text = stringResource(playerTheme.labelRes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isPlayerThemeDialogVisible = false
                    }
                ) {
                    Text(text = stringResource(R.string.settings_close))
                }
            }
        )
    }

    if (isThemeCustomizationDialogVisible && themeCustomizationOptions.isNotEmpty()) {
        ThemeColorCustomizationDialog(
            playerTheme = selectedPlayerTheme,
            tokens = selectedPlayerThemeTokens,
            onColorSelected = { field, color ->
                onUpdatePlayerThemeTokenOverride(selectedPlayerTheme, field, color)
            },
            onReset = {
                onResetPlayerThemeTokenOverrides(selectedPlayerTheme)
            },
            onDismiss = {
                isThemeCustomizationDialogVisible = false
            }
        )
    }
}

@Composable
private fun appAppearanceLabel(appearance: AppAppearance): String = when (appearance) {
    AppAppearance.SYSTEM -> stringResource(R.string.app_appearance_system)
    AppAppearance.LIGHT -> stringResource(R.string.app_appearance_light)
    AppAppearance.DARK -> stringResource(R.string.app_appearance_dark)
}

@Composable
private fun AppFont.localizedDisplayName(): String = when (this) {
    AppFont.SAZANAMI -> stringResource(R.string.app_name)
    AppFont.DEVICE -> stringResource(R.string.settings_device_font)
}

@Composable
private fun AppFont.localizedDescription(): String = when (this) {
    AppFont.SAZANAMI -> stringResource(R.string.settings_sazanami_font_description)
    AppFont.DEVICE -> stringResource(R.string.settings_device_font_description)
}

@Composable
private fun LibraryRefreshSummary.settingsSummary(): String {
    if (!successfulCompleteScan) {
        return stringResource(R.string.settings_scan_incomplete)
    }
    val changes = listOfNotNull(
        if (addedCount > 0) pluralStringResource(R.plurals.settings_scan_added, addedCount, addedCount) else null,
        if (updatedCount > 0) pluralStringResource(R.plurals.settings_scan_updated, updatedCount, updatedCount) else null,
        if (movedCount > 0) pluralStringResource(R.plurals.settings_scan_moved, movedCount, movedCount) else null,
        if (removedCount > 0) pluralStringResource(R.plurals.settings_scan_removed, removedCount, removedCount) else null
    )
    return if (changes.isEmpty()) {
        stringResource(R.string.settings_scan_no_changes)
    } else {
        changes.joinToString(" • ")
    }
}
