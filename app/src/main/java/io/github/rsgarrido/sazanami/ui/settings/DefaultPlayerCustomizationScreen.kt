package io.github.rsgarrido.sazanami.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.RepeatMode
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import io.github.rsgarrido.sazanami.ui.AppShellTypography
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBackgroundAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBackgroundStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernBlurStrength
import io.github.rsgarrido.sazanami.ui.player.modern.ModernDimmingStrength
import io.github.rsgarrido.sazanami.ui.player.modern.ModernAppearanceChoice
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkFit
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkShape
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkShadow
import io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkSize
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlAccent
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlSize
import io.github.rsgarrido.sazanami.ui.player.modern.ModernControlStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLayoutDensity
import io.github.rsgarrido.sazanami.ui.player.modern.ModernLandscapeLayout
import io.github.rsgarrido.sazanami.ui.player.modern.ModernMetadataAlignment
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerControls
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerFramedAlbumImage
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerAppearance
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerBackground
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerDefaults
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerSeekBar
import io.github.rsgarrido.sazanami.ui.player.modern.ModernQueueHubButton
import io.github.rsgarrido.sazanami.ui.player.modern.ModernPlayerStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarColorMode
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarStyle
import io.github.rsgarrido.sazanami.ui.player.modern.ModernWaveformDensity
import io.github.rsgarrido.sazanami.ui.player.modern.ModernWaveformSize
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSolidColorSwatches
import io.github.rsgarrido.sazanami.ui.player.modern.modernArgbToHsv
import io.github.rsgarrido.sazanami.ui.player.modern.localizedLabel
import io.github.rsgarrido.sazanami.ui.player.modern.modernHsvToArgb
import io.github.rsgarrido.sazanami.ui.player.modern.rememberModernArtworkPalette
import io.github.rsgarrido.sazanami.ui.player.modern.resolveModernControlRowLayout
import io.github.rsgarrido.sazanami.ui.player.modern.sanitizeModernSolidColorArgb
import io.github.rsgarrido.sazanami.ui.player.modern.modernSolidColorReadabilityScrimAlpha
import io.github.rsgarrido.sazanami.ui.player.modern.resolveModernAlbumGradient

@Composable
internal fun DefaultPlayerCustomizationScreen(
    appearance: ModernPlayerAppearance,
    activeChoice: ModernAppearanceChoice,
    landscapeLayout: ModernLandscapeLayout = ModernLandscapeLayout.BALANCED,
    onLandscapeLayoutSelected: (ModernLandscapeLayout) -> Unit = {},
    previewSong: Song?,
    onChoiceSelected: (ModernAppearanceChoice) -> Unit,
    onAppearanceEdited: ((ModernPlayerAppearance) -> ModernPlayerAppearance) -> Unit,
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
                    contentDescription = stringResource(R.string.modern_back)
                )
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.modern_customize_default_player),
                    style = AppShellTypography.ScreenTitle,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.modern_changes_preview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SettingsSection(
            title = stringResource(R.string.modern_preview),
            description = stringResource(R.string.modern_preview_description),
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
            title = stringResource(R.string.modern_presets),
            description = stringResource(R.string.modern_presets_description),
            icon = AppShellIcons.Palette
        ) {
            ChoiceGroup(
                title = stringResource(R.string.modern_current_appearance),
                options = ModernAppearanceChoice.entries,
                selected = activeChoice,
                label = { stringResource(it.labelRes) },
                onSelected = onChoiceSelected
            )
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = stringResource(R.string.modern_seekbar),
            description = stringResource(R.string.modern_seekbar_description),
            icon = AppShellIcons.Seekbar
        ) {
            ChoiceGroup(
                title = stringResource(R.string.modern_style),
                options = ModernSeekbarStyle.entries,
                selected = appearance.seekbar.style,
                label = { it.localizedLabel() },
                onSelected = { style ->
                    onAppearanceEdited { current ->
                        current.copy(seekbar = current.seekbar.copy(style = style))
                    }
                }
            )

            if (appearance.seekbar.style.usesWaveformData) {
                ChoiceGroup(
                    title = stringResource(R.string.modern_waveform_size),
                    options = ModernWaveformSize.entries,
                    selected = appearance.seekbar.waveformSize,
                    label = { it.localizedLabel() },
                    onSelected = { size ->
                        onAppearanceEdited { current ->
                            current.copy(seekbar = current.seekbar.copy(waveformSize = size))
                        }
                    }
                )
                ChoiceGroup(
                    title = stringResource(R.string.modern_waveform_density),
                    options = ModernWaveformDensity.entries,
                    selected = appearance.seekbar.waveformDensity,
                    label = { it.localizedLabel() },
                    onSelected = { density ->
                        onAppearanceEdited { current ->
                            current.copy(seekbar = current.seekbar.copy(waveformDensity = density))
                        }
                    }
                )
            }

            ChoiceGroup(
                title = stringResource(R.string.modern_progress_color),
                options = ModernSeekbarColorMode.entries,
                selected = appearance.seekbar.colorMode,
                label = { it.localizedLabel() },
                onSelected = { mode ->
                    onAppearanceEdited { current ->
                        current.copy(seekbar = current.seekbar.copy(colorMode = mode))
                    }
                }
            )
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = stringResource(R.string.modern_background),
            description = stringResource(R.string.modern_background_description),
            icon = AppShellIcons.Palette
        ) {
            ChoiceGroup(
                title = stringResource(R.string.modern_style),
                options = ModernBackgroundStyle.entries,
                selected = appearance.background.style,
                label = { it.localizedLabel() },
                onSelected = { style ->
                    onAppearanceEdited { current ->
                        current.copy(background = current.background.copy(style = style))
                    }
                }
            )

            if (appearance.background.style.supportsBlur) {
                ChoiceGroup(
                    title = stringResource(R.string.modern_blur_strength),
                    options = ModernBlurStrength.entries,
                    selected = appearance.background.blurStrength,
                    label = { it.localizedLabel() },
                    onSelected = { strength ->
                        onAppearanceEdited { current ->
                            current.copy(background = current.background.copy(blurStrength = strength))
                        }
                    }
                )
            }

            if (appearance.background.style.supportsDimming) {
                ChoiceGroup(
                    title = stringResource(R.string.modern_dimming),
                    options = ModernDimmingStrength.entries,
                    selected = appearance.background.dimmingStrength,
                    label = { it.localizedLabel() },
                    onSelected = { strength ->
                        onAppearanceEdited { current ->
                            current.copy(background = current.background.copy(dimmingStrength = strength))
                        }
                    }
                )
            }

            if (appearance.background.style == ModernBackgroundStyle.SOLID_COLOR) {
                SolidColorPicker(
                    argb = appearance.background.solidColorArgb,
                    onColorChanged = { transform ->
                        onAppearanceEdited { current ->
                            current.copy(
                                background = current.background.copy(
                                    solidColorArgb = transform(current.background.solidColorArgb)
                                )
                            )
                        }
                    }
                )
            }
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = stringResource(R.string.modern_artwork),
            description = stringResource(R.string.modern_artwork_description),
            icon = AppShellIcons.Deck
        ) {
            ChoiceGroup(stringResource(R.string.modern_shape), ModernArtworkShape.entries, appearance.artwork.shape,
                { it.localizedLabel() }) { value ->
                onAppearanceEdited { current ->
                    current.copy(artwork = current.artwork.copy(shape = value))
                }
            }
            ChoiceGroup(stringResource(R.string.modern_size), ModernArtworkSize.entries, appearance.artwork.size,
                { it.localizedLabel() }) { value ->
                onAppearanceEdited { current ->
                    current.copy(artwork = current.artwork.copy(size = value))
                }
            }
            ChoiceGroup(stringResource(R.string.modern_image_fit), ModernArtworkFit.entries, appearance.artwork.fit,
                { it.localizedLabel() }) { value ->
                onAppearanceEdited { current ->
                    current.copy(artwork = current.artwork.copy(fit = value))
                }
            }
            ChoiceGroup(stringResource(R.string.modern_shadow), ModernArtworkShadow.entries, appearance.artwork.shadow,
                { it.localizedLabel() }) { value ->
                onAppearanceEdited { current ->
                    current.copy(artwork = current.artwork.copy(shadow = value))
                }
            }
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = stringResource(R.string.modern_playback_controls),
            description = stringResource(R.string.modern_playback_controls_description),
            icon = AppShellIcons.MusicNote
        ) {
            ChoiceGroup(stringResource(R.string.modern_style), ModernControlStyle.entries, appearance.controls.style,
                { it.localizedLabel() }) { value ->
                onAppearanceEdited { current ->
                    current.copy(controls = current.controls.copy(style = value))
                }
            }
            ChoiceGroup(stringResource(R.string.modern_size), ModernControlSize.entries, appearance.controls.size,
                { it.localizedLabel() }) { value ->
                onAppearanceEdited { current ->
                    current.copy(controls = current.controls.copy(size = value))
                }
            }
            ChoiceGroup(stringResource(R.string.modern_accent), ModernControlAccent.entries, appearance.controls.accent,
                { it.localizedLabel() }) { value ->
                onAppearanceEdited { current ->
                    current.copy(controls = current.controls.copy(accent = value))
                }
            }
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = stringResource(R.string.modern_layout),
            description = stringResource(R.string.modern_layout_description),
            icon = AppShellIcons.ListView
        ) {
            ChoiceGroup(
                title = stringResource(R.string.modern_landscape_layout),
                options = ModernLandscapeLayout.entries,
                selected = landscapeLayout,
                label = { it.localizedLabel() },
                onSelected = onLandscapeLayoutSelected
            )
            Text(
                text = stringResource(R.string.modern_landscape_layout_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            ChoiceGroup(stringResource(R.string.modern_density), ModernLayoutDensity.entries, appearance.layout.density,
                { it.localizedLabel() }) { value ->
                onAppearanceEdited { current ->
                    current.copy(layout = current.layout.copy(density = value))
                }
            }
            ChoiceGroup(
                stringResource(R.string.modern_metadata_alignment),
                ModernMetadataAlignment.entries,
                appearance.layout.metadataAlignment,
                { it.localizedLabel() }
            ) { value ->
                onAppearanceEdited { current ->
                    current.copy(layout = current.layout.copy(metadataAlignment = value))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.modern_audio_quality_badge), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.modern_audio_quality_badge_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = appearance.layout.showAudioQualityBadge,
                    onCheckedChange = { checked ->
                        onAppearanceEdited { current ->
                            current.copy(
                                layout = current.layout.copy(showAudioQualityBadge = checked)
                            )
                        }
                    }
                )
            }
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = stringResource(R.string.modern_reset),
            description = stringResource(R.string.modern_reset_description),
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
                    text = stringResource(R.string.modern_reset_default_appearance),
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (resetConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { resetConfirmationVisible = false },
            title = { Text(stringResource(R.string.modern_reset_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.modern_reset_confirm_body)
                )
            },
            dismissButton = {
                TextButton(onClick = { resetConfirmationVisible = false }) {
                    Text(stringResource(R.string.modern_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetConfirmationVisible = false
                        onReset()
                    }
                ) {
                    Text(stringResource(R.string.modern_reset))
                }
            }
        )
    }
}

@Composable
internal fun ModernPlayerAppearancePreview(
    appearance: ModernPlayerAppearance,
    previewSong: Song?,
    modifier: Modifier = Modifier
) {
    val style = ModernPlayerDefaults.style()
    val artworkPalette = rememberModernArtworkPalette(previewSong, style.accentColor)
    val artworkSize = when (appearance.artwork.size) {
        ModernArtworkSize.COMPACT -> 100.dp
        ModernArtworkSize.STANDARD -> 124.dp
        ModernArtworkSize.LARGE -> 148.dp
    }
    val metadataAlignment = appearance.layout.metadataAlignment
    Surface(
        color = Color.Black,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(500.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PreviewBackground(previewSong, style, appearance.background, artworkPalette)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = if (metadataAlignment == ModernMetadataAlignment.CENTER) {
                    Alignment.CenterHorizontally
                } else {
                    Alignment.Start
                }
            ) {
                Surface(
                    color = style.artworkContainerColor,
                    shape = RoundedCornerShape(appearance.artwork.shape.cornerRadiusDp.dp),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(artworkSize)
                        .shadow(
                            appearance.artwork.shadow.elevationDp.dp,
                            RoundedCornerShape(appearance.artwork.shape.cornerRadiusDp.dp)
                        )
                ) {
                    if (previewSong != null) {
                        ModernPlayerFramedAlbumImage(
                            currentSong = previewSong,
                            contentDescription = null,
                            artworkSize = artworkSize,
                            appearance = appearance.artwork,
                            modifier = Modifier.fillMaxSize()
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
                text = previewSong?.title ?: stringResource(R.string.modern_preview_title_fallback),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = style.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (metadataAlignment == ModernMetadataAlignment.CENTER) {
                        TextAlign.Center
                    } else {
                        TextAlign.Start
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = previewSong?.artist ?: "Sazanami",
                    style = MaterialTheme.typography.bodySmall,
                    color = style.secondaryContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (metadataAlignment == ModernMetadataAlignment.CENTER) {
                        TextAlign.Center
                    } else {
                        TextAlign.Start
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (appearance.layout.showAudioQualityBadge) {
                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(
                                if (metadataAlignment == ModernMetadataAlignment.CENTER) {
                                    Alignment.CenterHorizontally
                                } else {
                                    Alignment.Start
                                }
                            )
                            .padding(top = 7.dp)
                    ) {
                        Text(
                            "FLAC / 24-bit",
                            style = MaterialTheme.typography.labelSmall,
                            color = style.secondaryContentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ModernQueueHubButton(
                        onClick = {},
                        tint = style.contentColor
                    )
                }

                Spacer(
                    modifier = Modifier.height(
                        when (appearance.layout.density) {
                            ModernLayoutDensity.COMPACT -> 6.dp
                            ModernLayoutDensity.BALANCED -> 12.dp
                            ModernLayoutDensity.RELAXED -> 20.dp
                        }
                    )
                )
                ModernPlayerSeekBar(
                    currentPosition = 74_000,
                    duration = 214_000,
                    onSeekChange = {},
                    appearance = appearance.seekbar,
                    waveformSeed = previewSong?.let {
                        "${it.id}|${it.filePath}|${it.title}"
                    } ?: "default-player-preview",
                    artworkPalette = artworkPalette,
                    style = style,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val controlLayout = resolveModernControlRowLayout(
                        size = appearance.controls.size,
                        availableWidthDp = maxWidth.value
                    )
                    ModernPlayerControls(
                        isPlaying = true,
                        isShuffleEnabled = false,
                        repeatMode = RepeatMode.OFF,
                        onPlayPauseClick = {},
                        onPreviousClick = {},
                        onNextClick = {},
                        onShuffleClick = {},
                        onRepeatClick = {},
                        style = style,
                        appearance = appearance.controls,
                        artworkPalette = artworkPalette,
                        controlScale = controlLayout.scale,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PreviewBackground(
    previewSong: Song?,
    style: ModernPlayerStyle,
    appearance: ModernBackgroundAppearance,
    artworkPalette: io.github.rsgarrido.sazanami.ui.player.modern.ModernArtworkPalette
) {
    if (previewSong != null) {
        ModernPlayerBackground(
            currentSong = previewSong,
            style = style,
            appearance = appearance,
            artworkPalette = artworkPalette
        )
        return
    }

    val background = when (appearance.style) {
        ModernBackgroundStyle.PURE_BLACK -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
        ModernBackgroundStyle.SOLID_COLOR -> Brush.verticalGradient(
            listOf(
                Color(sanitizeModernSolidColorArgb(appearance.solidColorArgb).toInt()),
                Color(sanitizeModernSolidColorArgb(appearance.solidColorArgb).toInt())
            )
        )
        ModernBackgroundStyle.ALBUM_GRADIENT -> {
            val gradient = resolveModernAlbumGradient(artworkPalette, style.accentColor)
            Brush.verticalGradient(listOf(gradient.top, gradient.center, gradient.bottom))
        }
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
    if (appearance.style == ModernBackgroundStyle.SOLID_COLOR) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Color.Black.copy(
                        alpha = modernSolidColorReadabilityScrimAlpha(
                            appearance.solidColorArgb
                        )
                    )
                )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SolidColorPicker(
    argb: Long,
    onColorChanged: ((Long) -> Long) -> Unit
) {
    val sanitized = sanitizeModernSolidColorArgb(argb)
    val hsv = remember(sanitized) { modernArgbToHsv(sanitized) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(stringResource(R.string.modern_solid_color), style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(sanitized.toInt()), RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        RoundedCornerShape(12.dp)
                    )
            )
            Text(
                text = "#" + sanitized.toString(16).uppercase().padStart(8, '0'),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
        ) {
            ModernSolidColorSwatches.forEach { swatch ->
                val selected = sanitizeModernSolidColorArgb(swatch) == sanitized
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(swatch.toInt()), RoundedCornerShape(10.dp))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onColorChanged { swatch } }
                )
            }
        }
        ColorSlider(stringResource(R.string.modern_hue), hsv.hue, 0f..359f) { value ->
            onColorChanged { current ->
                modernHsvToArgb(modernArgbToHsv(current).copy(hue = value))
            }
        }
        ColorSlider(stringResource(R.string.modern_saturation), hsv.saturation, 0f..1f) { value ->
            onColorChanged { current ->
                modernHsvToArgb(modernArgbToHsv(current).copy(saturation = value))
            }
        }
        ColorSlider(stringResource(R.string.modern_brightness), hsv.value, 0.08f..1f) { value ->
            onColorChanged { current ->
                modernHsvToArgb(modernArgbToHsv(current).copy(value = value))
            }
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceGroup(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
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
