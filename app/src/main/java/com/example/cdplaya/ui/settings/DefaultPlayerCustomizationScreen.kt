package com.example.cdplaya.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.Song
import com.example.cdplaya.ui.AppShellIcons
import com.example.cdplaya.ui.AppShellTypography
import com.example.cdplaya.ui.player.modern.ModernBackgroundAppearance
import com.example.cdplaya.ui.player.modern.ModernBackgroundStyle
import com.example.cdplaya.ui.player.modern.ModernBlurStrength
import com.example.cdplaya.ui.player.modern.ModernDimmingStrength
import com.example.cdplaya.ui.player.modern.ModernPlayerAlbumImage
import com.example.cdplaya.ui.player.modern.ModernPlayerAppearance
import com.example.cdplaya.ui.player.modern.ModernPlayerBackground
import com.example.cdplaya.ui.player.modern.ModernPlayerDefaults
import com.example.cdplaya.ui.player.modern.ModernPlayerSeekBar
import com.example.cdplaya.ui.player.modern.ModernPlayerStyle
import com.example.cdplaya.ui.player.modern.ModernSeekbarColorMode
import com.example.cdplaya.ui.player.modern.ModernSeekbarStyle
import com.example.cdplaya.ui.player.modern.ModernWaveformDensity
import com.example.cdplaya.ui.player.modern.ModernWaveformSize

@Composable
internal fun DefaultPlayerCustomizationScreen(
    appearance: ModernPlayerAppearance,
    previewSong: Song?,
    onAppearanceChanged: (ModernPlayerAppearance) -> Unit,
    onReset: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClick)
    var resetConfirmationVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 10.dp, end = 20.dp, bottom = 18.dp),
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
                    text = "Customize Default Player",
                    style = AppShellTypography.ScreenTitle,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Changes appear instantly in the preview and expanded player.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SettingsSection(
            title = "Preview",
            description = "A compact view of the current Default player appearance.",
            icon = AppShellIcons.Deck
        ) {
            ModernPlayerAppearancePreview(
                appearance = appearance,
                previewSong = previewSong,
                modifier = Modifier.padding(12.dp)
            )
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = "Seekbar",
            description = "Choose the progress style and waveform presentation.",
            icon = AppShellIcons.Seekbar
        ) {
            ChoiceGroup(
                title = "Style",
                options = ModernSeekbarStyle.entries,
                selected = appearance.seekbar.style,
                label = ModernSeekbarStyle::displayName,
                onSelected = { style ->
                    onAppearanceChanged(
                        appearance.copy(seekbar = appearance.seekbar.copy(style = style))
                    )
                }
            )

            if (appearance.seekbar.style.usesWaveformData) {
                ChoiceGroup(
                    title = "Waveform size",
                    options = ModernWaveformSize.entries,
                    selected = appearance.seekbar.waveformSize,
                    label = ModernWaveformSize::displayName,
                    onSelected = { size ->
                        onAppearanceChanged(
                            appearance.copy(
                                seekbar = appearance.seekbar.copy(waveformSize = size)
                            )
                        )
                    }
                )
                ChoiceGroup(
                    title = "Waveform density",
                    options = ModernWaveformDensity.entries,
                    selected = appearance.seekbar.waveformDensity,
                    label = ModernWaveformDensity::displayName,
                    onSelected = { density ->
                        onAppearanceChanged(
                            appearance.copy(
                                seekbar = appearance.seekbar.copy(waveformDensity = density)
                            )
                        )
                    }
                )
            }

            ChoiceGroup(
                title = "Progress color",
                options = ModernSeekbarColorMode.entries,
                selected = appearance.seekbar.colorMode,
                label = ModernSeekbarColorMode::displayName,
                onSelected = { mode ->
                    onAppearanceChanged(
                        appearance.copy(seekbar = appearance.seekbar.copy(colorMode = mode))
                    )
                }
            )
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = "Background",
            description = "Set the backdrop while keeping player details readable.",
            icon = AppShellIcons.Palette
        ) {
            ChoiceGroup(
                title = "Style",
                options = ModernBackgroundStyle.entries,
                selected = appearance.background.style,
                label = ModernBackgroundStyle::displayName,
                onSelected = { style ->
                    onAppearanceChanged(
                        appearance.copy(
                            background = appearance.background.copy(style = style)
                        )
                    )
                }
            )

            if (appearance.background.style.supportsBlur) {
                ChoiceGroup(
                    title = "Blur strength",
                    options = ModernBlurStrength.entries,
                    selected = appearance.background.blurStrength,
                    label = ModernBlurStrength::displayName,
                    onSelected = { strength ->
                        onAppearanceChanged(
                            appearance.copy(
                                background = appearance.background.copy(blurStrength = strength)
                            )
                        )
                    }
                )
            }

            if (appearance.background.style.supportsDimming) {
                ChoiceGroup(
                    title = "Dimming",
                    options = ModernDimmingStrength.entries,
                    selected = appearance.background.dimmingStrength,
                    label = ModernDimmingStrength::displayName,
                    onSelected = { strength ->
                        onAppearanceChanged(
                            appearance.copy(
                                background = appearance.background.copy(
                                    dimmingStrength = strength
                                )
                            )
                        )
                    }
                )
            }
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = "Reset",
            description = "Restore CDPlaya's intended Modern player defaults.",
            icon = Icons.Filled.Refresh
        ) {
            ElevatedButton(
                onClick = { resetConfirmationVisible = true },
                colors = ButtonDefaults.elevatedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(
                    text = "Reset Default Player Appearance",
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (resetConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { resetConfirmationVisible = false },
            title = { Text("Reset Default Player Appearance?") },
            text = {
                Text(
                    "This restores the Default player's seekbar, waveform, background, " +
                            "blur, and dimming settings. Other player themes are unchanged."
                )
            },
            dismissButton = {
                TextButton(onClick = { resetConfirmationVisible = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetConfirmationVisible = false
                        onReset()
                    }
                ) {
                    Text("Reset")
                }
            }
        )
    }
}

@Composable
private fun ModernPlayerAppearancePreview(
    appearance: ModernPlayerAppearance,
    previewSong: Song?,
    modifier: Modifier = Modifier
) {
    val style = ModernPlayerDefaults.style()
    Surface(
        color = Color.Black,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PreviewBackground(previewSong, style, appearance.background)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = style.artworkContainerColor,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.size(132.dp)
                ) {
                    if (previewSong != null) {
                        ModernPlayerAlbumImage(
                            currentSong = previewSong,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppShellIcons.Deck,
                                contentDescription = null,
                                tint = style.contentColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(13.dp))
                Text(
                    text = previewSong?.title ?: "Default Player Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = style.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = previewSong?.artist ?: "CDPlaya",
                    style = MaterialTheme.typography.bodySmall,
                    color = style.secondaryContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))
                ModernPlayerSeekBar(
                    currentPosition = 74_000,
                    duration = 214_000,
                    onSeekChange = {},
                    appearance = appearance.seekbar,
                    waveformSeed = previewSong?.let {
                        "${it.id}|${it.filePath}|${it.title}"
                    } ?: "cdplaya-default-preview",
                    style = style,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PreviewBackground(
    previewSong: Song?,
    style: ModernPlayerStyle,
    appearance: ModernBackgroundAppearance
) {
    if (previewSong != null) {
        ModernPlayerBackground(
            currentSong = previewSong,
            style = style,
            appearance = appearance
        )
        return
    }

    val background = when (appearance.style) {
        ModernBackgroundStyle.PURE_BLACK -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
        ModernBackgroundStyle.SOLID_COLOR -> Brush.verticalGradient(
            listOf(style.solidBackgroundColor, style.solidBackgroundColor)
        )
        else -> Brush.verticalGradient(
            listOf(style.accentColor.copy(alpha = 0.72f), Color.Black)
        )
    }
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(background)
    )
    if (appearance.style.supportsDimming) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = appearance.dimmingStrength.overlayAlpha))
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = {
                        Text(
                            text = label(option),
                            textAlign = TextAlign.Center
                        )
                    }
                )
            }
        }
    }
}
