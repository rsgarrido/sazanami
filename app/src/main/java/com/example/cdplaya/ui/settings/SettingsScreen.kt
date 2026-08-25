package com.example.cdplaya.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cdplaya.R
import com.example.cdplaya.data.FolderSelectionMode
import com.example.cdplaya.data.PlayerTheme
import com.example.cdplaya.player.audio.AudioOffloadPreference
import com.example.cdplaya.player.replaygain.ReplayGainMode
import com.example.cdplaya.ui.AppShellIcons
import com.example.cdplaya.ui.AppShellTypography
import com.example.cdplaya.ui.LocalFolderArtworkUi
import com.example.cdplaya.mediaaccess.folderArtworkLocationLabel
import com.example.cdplaya.ui.home.LocalHomePinUi
import com.example.cdplaya.ui.state.LibraryRefreshSummary
import com.example.cdplaya.ui.player.modern.ModernArtworkTransitionStyle
import com.example.cdplaya.ui.player.modern.ModernSeekbarStyle
import com.example.cdplaya.ui.player.theme.PlayerThemeTokenField
import com.example.cdplaya.ui.player.theme.PlayerThemeTokens
import com.example.cdplaya.ui.player.theme.customizationOptions

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
    equalizerSummary: String,
    onEqualizerClick: () -> Unit,
    isSleepTimerActive: Boolean,
    sleepTimerDisplayText: String,
    onSleepTimerClick: () -> Unit,
    selectedPlayerTheme: PlayerTheme,
    selectedPlayerThemeTokens: PlayerThemeTokens,
    onPlayerThemeSelected: (PlayerTheme) -> Unit,
    onUpdatePlayerThemeTokenOverride: (PlayerTheme, PlayerThemeTokenField, Color) -> Unit,
    onResetPlayerThemeTokenOverrides: (PlayerTheme) -> Unit,
    selectedModernArtworkTransitionStyle: ModernArtworkTransitionStyle,
    onModernArtworkTransitionStyleSelected: (ModernArtworkTransitionStyle) -> Unit,
    selectedModernSeekbarStyle: ModernSeekbarStyle,
    onModernSeekbarStyleSelected: (ModernSeekbarStyle) -> Unit,
    selectedReplayGainMode: ReplayGainMode,
    onReplayGainModeSelected: (ReplayGainMode) -> Unit,
    selectedAudioOffloadPreference: AudioOffloadPreference,
    onAudioOffloadPreferenceSelected: (AudioOffloadPreference) -> Unit,
    smoothPlayPauseEnabled: Boolean = true,
    onSmoothPlayPauseEnabledChanged: (Boolean) -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier
) {
    var isPlayerThemeDialogVisible by remember { mutableStateOf(false) }
    var isReplayGainDialogVisible by remember { mutableStateOf(false) }
    var isAudioOffloadDialogVisible by remember { mutableStateOf(false) }
    var isThemeCustomizationDialogVisible by remember { mutableStateOf(false) }
    var isArtworkTransitionDialogVisible by remember { mutableStateOf(false) }
    var isSeekbarStyleDialogVisible by remember { mutableStateOf(false) }
    var isEmbeddedArtworkOnlyDialogVisible by remember { mutableStateOf(false) }
    val homePinUi = LocalHomePinUi.current
    val folderArtworkUi = LocalFolderArtworkUi.current

    val themeCustomizationOptions = selectedPlayerTheme.customizationOptions()
    val folderSelectionText = when {
        folderSelectionMode == FolderSelectionMode.ALL && excludedFolderCount == 0 ->
            "All folder trees • $availableFolderCount source(s)"
        folderSelectionMode == FolderSelectionMode.ALL ->
            "All except $excludedFolderCount excluded • $availableFolderCount source(s)"
        selectedFolderCount == 0 ->
            "No folders selected • $availableFolderCount source(s)"
        excludedFolderCount == 0 ->
            "$selectedFolderCount folder root(s) selected"
        else ->
            "$selectedFolderCount selected • $excludedFolderCount excluded"
    }
    val libraryScanSummary = when {
        isLibraryRefreshing -> "Scanning for added, changed, moved, or removed music…"
        libraryErrorMessage != null -> libraryErrorMessage
        lastLibraryRefreshSummary != null -> lastLibraryRefreshSummary.settingsSummary()
        else -> "Check the device library without restarting CDPlaya"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    contentDescription = "Back"
                )
            }

            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = "Settings",
                    style = AppShellTypography.ScreenTitle,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        SettingsSection(
            title = "Library",
            description = "Choose what CDPlaya includes and refresh your current collection.",
            icon = AppShellIcons.AlbumStack
        ) {
            SettingsRow(
                title = "Library folders",
                summary = folderSelectionText,
                icon = AppShellIcons.Folder,
                onClick = onLibraryFoldersClick,
                emphasizeSummary = true,
                navigationContentDescription = "Open library folders"
            )

            SettingsDivider()

            SettingsRow(
                title = "Folder artwork",
                summary = folderArtworkLocationLabel(folderArtworkUi.state.treeUri),
                icon = AppShellIcons.AlbumStack,
                onClick = folderArtworkUi.onChooseFolder,
                emphasizeSummary = folderArtworkUi.state.hasFolderAccess,
                navigationContentDescription = if (folderArtworkUi.state.hasFolderAccess) {
                    "Change folder artwork location"
                } else {
                    "Choose folder artwork location"
                }
            )

            if (folderArtworkUi.state.hasFolderAccess) {
                SettingsDivider()
                SettingsRow(
                    title = "Use embedded artwork only",
                    summary = "Remove the selected folder and stop reading cover.jpg-style files",
                    icon = AppShellIcons.AlbumStack,
                    onClick = {
                        isEmbeddedArtworkOnlyDialogVisible = true
                    }
                )
            }

            SettingsDivider()

            SettingsRow(
                title = if (isLibraryRefreshing) "Scanning library" else "Scan library",
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
                title = "Songs",
                summary = "$totalSongCount song(s) in your current library",
                icon = AppShellIcons.MusicNote
            )
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = "Home",
            description = "Choose which automatic shelves appear on your Home screen.",
            icon = Icons.Filled.Home
        ) {
            SettingsRow(
                title = "Recently Added",
                summary = if (homePinUi.showRecentlyAddedOnHome) {
                    "Shown on Home when recently added music is available"
                } else {
                    "Hidden from Home"
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
                text = "Pinned songs, albums, and artists are managed from their item actions. Home holds up to 4 pins."
            )
        }

        SettingsSectionSpacer()

        LyricsFolderSettings()

        SettingsSectionSpacer()

        SettingsSection(
            title = "Playback & audio",
            description = "Control sound processing, loudness, timing, and power use.",
            icon = AppShellIcons.Equalizer
        ) {
            SettingsRow(
                title = "Smooth play/pause",
                summary = "Fade briefly when playback starts or pauses",
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
                title = "Equalizer",
                summary = equalizerSummary,
                icon = AppShellIcons.Equalizer,
                onClick = onEqualizerClick,
                emphasizeSummary = true,
                navigationContentDescription = "Open equalizer settings"
            )

            SettingsDivider()

            SettingsRow(
                title = "ReplayGain",
                summary = selectedReplayGainMode.displayName,
                icon = AppShellIcons.Gauge,
                onClick = { isReplayGainDialogVisible = true },
                emphasizeSummary = true,
                navigationContentDescription = "Open ReplayGain settings"
            )

            SettingsDivider()

            SettingsRow(
                title = "Audio offload",
                summary = selectedAudioOffloadPreference.displayName,
                icon = AppShellIcons.AudioRoute,
                onClick = { isAudioOffloadDialogVisible = true },
                emphasizeSummary = true,
                navigationContentDescription = "Open audio offload settings"
            )

            SettingsDivider()

            SettingsRow(
                title = "Sleep Timer",
                summary = if (isSleepTimerActive) {
                    sleepTimerDisplayText
                } else {
                    "Pause playback after a set time"
                },
                icon = AppShellIcons.Timer,
                onClick = onSleepTimerClick,
                emphasizeSummary = isSleepTimerActive,
                navigationContentDescription = "Open sleep timer"
            )

            SettingsDivider()

            SettingsFooterNote(
                text = "Audio offload may reduce power use during long background playback. " +
                        "CDPlaya falls back to normal decoded playback when offload is unavailable " +
                        "or incompatible with an active audio feature."
            )
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = "Player & appearance",
            description = "Choose the player identity, motion, controls, and theme colors.",
            icon = AppShellIcons.Palette
        ) {
            SettingsRow(
                title = "Player Theme",
                summary = selectedPlayerTheme.displayName,
                icon = AppShellIcons.Deck,
                onClick = { isPlayerThemeDialogVisible = true },
                emphasizeSummary = true,
                navigationContentDescription = "Choose player theme"
            )

            if (selectedPlayerTheme == PlayerTheme.DEFAULT) {
                SettingsDivider()

                SettingsRow(
                    title = "Artwork transition style",
                    summary = selectedModernArtworkTransitionStyle.displayName,
                    icon = AppShellIcons.Transition,
                    onClick = { isArtworkTransitionDialogVisible = true },
                    emphasizeSummary = true,
                    navigationContentDescription = "Choose artwork transition style"
                )

                SettingsDivider()

                SettingsRow(
                    title = "Modern player seekbar style",
                    summary = selectedModernSeekbarStyle.displayName,
                    icon = AppShellIcons.Seekbar,
                    onClick = { isSeekbarStyleDialogVisible = true },
                    emphasizeSummary = true,
                    navigationContentDescription = "Choose modern player seekbar style"
                )
            }

            if (themeCustomizationOptions.isNotEmpty()) {
                SettingsDivider()

                SettingsRow(
                    title = "Customize theme colors",
                    summary = "Choose preset colors for ${selectedPlayerTheme.displayName}",
                    icon = AppShellIcons.Palette,
                    onClick = { isThemeCustomizationDialogVisible = true },
                    navigationContentDescription = "Customize theme colors"
                )
            }
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = "Listening history",
            description = "Bring previous listening activity into CDPlaya.",
            icon = Icons.Filled.History
        ) {
            SettingsRow(
                title = "Import listening history",
                summary = "Import your previous listening history from supported services.",
                icon = AppShellIcons.Restore,
                onClick = onListeningHistoryImportClick,
                navigationContentDescription = "Open listening history import"
            )
            SettingsDivider()
            SettingsRow(
                title = "Match imported tracks",
                summary = "Connect imported listening history to songs in your library.",
                icon = AppShellIcons.Search,
                onClick = onListeningHistoryReconciliationClick,
                navigationContentDescription = "Open imported track matching"
            )
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = "Data & support",
            description = "Protect app data and inspect playback or device information.",
            icon = AppShellIcons.Diagnostics
        ) {
            SettingsRow(
                title = "Export Backup",
                summary = "Save favorites, playlists, listening history and track links, ratings, and preferences as JSON.",
                icon = AppShellIcons.Export,
                onClick = onExportBackupClick,
                navigationContentDescription = "Export backup"
            )

            SettingsDivider()

            SettingsRow(
                title = "Restore Backup",
                summary = "Replace app data from a CDPlaya backup JSON file.",
                icon = AppShellIcons.Restore,
                onClick = onRestoreBackupClick,
                navigationContentDescription = "Restore backup"
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
            title = "About",
            description = "CDPlaya information and project identity.",
            icon = AppShellIcons.Info
        ) {
            SettingsRow(
                title = "CDPlaya",
                summary = "A local music player for your personal library.",
                icon = AppShellIcons.Info
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (isReplayGainDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                isReplayGainDialogVisible = false
            },
            title = {
                Text(text = "ReplayGain")
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
                                Text(text = replayGainMode.displayName)

                                Text(
                                    text = replayGainMode.description,
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
                    Text(text = "Close")
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
                Text(text = "Audio offload")
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
                                Text(text = preference.displayName)
                                Text(
                                    text = if (preference == AudioOffloadPreference.AUTOMATIC) {
                                        "Use offload when compatible and otherwise play normally."
                                    } else {
                                        "Use normal decoded playback."
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
                    Text(text = "Close")
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
                Text(text = "Use embedded artwork only?")
            },
            text = {
                Text(
                    text = "CDPlaya will stop using cover.jpg-style files from the selected folder and remove its saved folder access. Embedded artwork inside your music files will still be used. You can choose a folder again later in Settings."
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isEmbeddedArtworkOnlyDialogVisible = false
                    }
                ) {
                    Text(text = "Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isEmbeddedArtworkOnlyDialogVisible = false
                        folderArtworkUi.onClearFolder()
                    }
                ) {
                    Text(text = "Use embedded only")
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
                Text(text = "Artwork transition style")
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
                                Text(text = transitionStyle.displayName)

                                Text(
                                    text = transitionStyle.description,
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
                    Text(text = "Close")
                }
            }
        )
    }

    if (
        isSeekbarStyleDialogVisible &&
        selectedPlayerTheme == PlayerTheme.DEFAULT
    ) {
        AlertDialog(
            onDismissRequest = {
                isSeekbarStyleDialogVisible = false
            },
            title = {
                Text(text = "Modern player seekbar style")
            },
            text = {
                Column {
                    ModernSeekbarStyle.values().forEach { seekbarStyle ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onModernSeekbarStyleSelected(seekbarStyle)
                                    isSeekbarStyleDialogVisible = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedModernSeekbarStyle == seekbarStyle,
                                onClick = {
                                    onModernSeekbarStyleSelected(seekbarStyle)
                                    isSeekbarStyleDialogVisible = false
                                }
                            )

                            Column(
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(text = seekbarStyle.displayName)

                                Text(
                                    text = seekbarStyle.description,
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
                        isSeekbarStyleDialogVisible = false
                    }
                ) {
                    Text(text = "Close")
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
                Text(text = "Player Theme")
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

                            Text(text = playerTheme.displayName)
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
                    Text(text = "Close")
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

private fun LibraryRefreshSummary.settingsSummary(): String {
    if (!successfulCompleteScan) {
        return "Scan was incomplete • kept the existing library"
    }
    val changes = buildList {
        if (addedCount > 0) add("$addedCount added")
        if (updatedCount > 0) add("$updatedCount updated")
        if (movedCount > 0) add("$movedCount moved")
        if (removedCount > 0) add("$removedCount removed")
    }
    return if (changes.isEmpty()) {
        "No library changes found"
    } else {
        changes.joinToString(" • ")
    }
}
