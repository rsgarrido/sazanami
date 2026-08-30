package com.example.cdplaya.ui.settings

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.Song
import com.example.cdplaya.player.RepeatMode
import com.example.cdplaya.ui.AppShellIcons
import com.example.cdplaya.ui.AppShellTypography
import com.example.cdplaya.ui.player.modern.ModernBackgroundAppearance
import com.example.cdplaya.ui.player.modern.ModernBackgroundStyle
import com.example.cdplaya.ui.player.modern.ModernBlurStrength
import com.example.cdplaya.ui.player.modern.ModernDimmingStrength
import com.example.cdplaya.ui.player.modern.ModernAppearancePreset
import com.example.cdplaya.ui.player.modern.ModernArtworkFit
import com.example.cdplaya.ui.player.modern.ModernArtworkShape
import com.example.cdplaya.ui.player.modern.ModernArtworkShadow
import com.example.cdplaya.ui.player.modern.ModernArtworkSize
import com.example.cdplaya.ui.player.modern.ModernControlAccent
import com.example.cdplaya.ui.player.modern.ModernControlSize
import com.example.cdplaya.ui.player.modern.ModernControlStyle
import com.example.cdplaya.ui.player.modern.ModernLayoutDensity
import com.example.cdplaya.ui.player.modern.ModernMetadataAlignment
import com.example.cdplaya.ui.player.modern.ModernPlayerControls
import com.example.cdplaya.ui.player.modern.ModernPlayerFramedAlbumImage
import com.example.cdplaya.ui.player.modern.ModernPlayerAppearance
import com.example.cdplaya.ui.player.modern.ModernPlayerBackground
import com.example.cdplaya.ui.player.modern.ModernPlayerDefaults
import com.example.cdplaya.ui.player.modern.ModernPlayerSeekBar
import com.example.cdplaya.ui.player.modern.ModernPlayerStyle
import com.example.cdplaya.ui.player.modern.ModernSeekbarColorMode
import com.example.cdplaya.ui.player.modern.ModernSeekbarStyle
import com.example.cdplaya.ui.player.modern.ModernWaveformDensity
import com.example.cdplaya.ui.player.modern.ModernWaveformSize
import com.example.cdplaya.ui.player.modern.ModernSolidColorSwatches
import com.example.cdplaya.ui.player.modern.modernArgbToHsv
import com.example.cdplaya.ui.player.modern.modernHsvToArgb
import com.example.cdplaya.ui.player.modern.rememberModernArtworkPalette
import com.example.cdplaya.ui.player.modern.resolveModernControlRowLayout
import com.example.cdplaya.ui.player.modern.sanitizeModernSolidColorArgb
import com.example.cdplaya.ui.player.modern.modernSolidColorReadabilityScrimAlpha
import com.example.cdplaya.ui.player.modern.resolveModernAlbumGradient

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
            title = "Presets",
            description = "Apply a coordinated look, then fine-tune any setting below.",
            icon = AppShellIcons.Palette
        ) {
            val matchingPreset = ModernAppearancePreset.matching(appearance)
            ChoiceGroup(
                title = if (matchingPreset == null) "Current: Custom" else "Current preset",
                options = ModernAppearancePreset.entries,
                selected = matchingPreset,
                label = ModernAppearancePreset::displayName,
                onSelected = { preset -> onAppearanceChanged(preset.appearance()) }
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

            if (appearance.background.style == ModernBackgroundStyle.SOLID_COLOR) {
                SolidColorPicker(
                    argb = appearance.background.solidColorArgb,
                    onColorChanged = { argb ->
                        onAppearanceChanged(
                            appearance.copy(
                                background = appearance.background.copy(
                                    solidColorArgb = argb
                                )
                            )
                        )
                    }
                )
            }
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = "Artwork",
            description = "Adjust the album cover's shape, scale, fit, and depth.",
            icon = AppShellIcons.Deck
        ) {
            ChoiceGroup("Shape", ModernArtworkShape.entries, appearance.artwork.shape,
                ModernArtworkShape::displayName) { value ->
                onAppearanceChanged(appearance.copy(artwork = appearance.artwork.copy(shape = value)))
            }
            ChoiceGroup("Size", ModernArtworkSize.entries, appearance.artwork.size,
                ModernArtworkSize::displayName) { value ->
                onAppearanceChanged(appearance.copy(artwork = appearance.artwork.copy(size = value)))
            }
            ChoiceGroup("Image fit", ModernArtworkFit.entries, appearance.artwork.fit,
                ModernArtworkFit::displayName) { value ->
                onAppearanceChanged(appearance.copy(artwork = appearance.artwork.copy(fit = value)))
            }
            ChoiceGroup("Shadow", ModernArtworkShadow.entries, appearance.artwork.shadow,
                ModernArtworkShadow::displayName) { value ->
                onAppearanceChanged(appearance.copy(artwork = appearance.artwork.copy(shadow = value)))
            }
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = "Playback Controls",
            description = "Choose the control treatment, scale, and active accent.",
            icon = AppShellIcons.MusicNote
        ) {
            ChoiceGroup("Style", ModernControlStyle.entries, appearance.controls.style,
                ModernControlStyle::displayName) { value ->
                onAppearanceChanged(appearance.copy(controls = appearance.controls.copy(style = value)))
            }
            ChoiceGroup("Size", ModernControlSize.entries, appearance.controls.size,
                ModernControlSize::displayName) { value ->
                onAppearanceChanged(appearance.copy(controls = appearance.controls.copy(size = value)))
            }
            ChoiceGroup("Accent", ModernControlAccent.entries, appearance.controls.accent,
                ModernControlAccent::displayName) { value ->
                onAppearanceChanged(appearance.copy(controls = appearance.controls.copy(accent = value)))
            }
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = "Layout",
            description = "Tune spacing, metadata alignment, and technical details.",
            icon = AppShellIcons.ListView
        ) {
            ChoiceGroup("Density", ModernLayoutDensity.entries, appearance.layout.density,
                ModernLayoutDensity::displayName) { value ->
                onAppearanceChanged(appearance.copy(layout = appearance.layout.copy(density = value)))
            }
            ChoiceGroup(
                "Metadata alignment",
                ModernMetadataAlignment.entries,
                appearance.layout.metadataAlignment,
                ModernMetadataAlignment::displayName
            ) { value ->
                onAppearanceChanged(
                    appearance.copy(layout = appearance.layout.copy(metadataAlignment = value))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Audio quality badge", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Show format and bit-depth details when available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = appearance.layout.showAudioQualityBadge,
                    onCheckedChange = { checked ->
                        onAppearanceChanged(
                            appearance.copy(
                                layout = appearance.layout.copy(showAudioQualityBadge = checked)
                            )
                        )
                    }
                )
            }
        }

        SettingsSectionSpacer()

        SettingsSection(
            title = "Reset",
            description = "Restore Sazanami's intended Modern player defaults.",
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
                    "This restores all Default player appearance settings. " +
                            "Other player themes are unchanged."
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
                    text = previewSong?.title ?: "Default Player Preview",
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
                    } ?: "cdplaya-default-preview",
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
    artworkPalette: com.example.cdplaya.ui.player.modern.ModernArtworkPalette
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
    onColorChanged: (Long) -> Unit
) {
    val sanitized = sanitizeModernSolidColorArgb(argb)
    val hsv = remember(sanitized) { modernArgbToHsv(sanitized) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Solid color", style = MaterialTheme.typography.labelLarge)
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
                        .clickable { onColorChanged(swatch) }
                )
            }
        }
        ColorSlider("Hue", hsv.hue, 0f..359f) { value ->
            onColorChanged(modernHsvToArgb(hsv.copy(hue = value)))
        }
        ColorSlider("Saturation", hsv.saturation, 0f..1f) { value ->
            onColorChanged(modernHsvToArgb(hsv.copy(saturation = value)))
        }
        ColorSlider("Brightness", hsv.value, 0.08f..1f) { value ->
            onColorChanged(modernHsvToArgb(hsv.copy(value = value)))
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
