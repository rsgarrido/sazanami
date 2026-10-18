package io.github.rsgarrido.sazanami.ui.player.modern

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.rsgarrido.sazanami.R

internal val ModernLandscapeLayout.labelRes: Int
    @StringRes get() = when (this) {
        ModernLandscapeLayout.BALANCED -> R.string.modern_option_layout_density_balanced
        ModernLandscapeLayout.WIDE_WAVEFORM -> R.string.modern_option_landscape_wide_waveform
    }

@Composable
internal fun ModernLandscapeLayout.localizedLabel(): String = stringResource(labelRes)

internal val ModernArtworkTransitionStyle.labelRes: Int
    @StringRes get() = when (this) {
        ModernArtworkTransitionStyle.SLIDE -> R.string.modern_option_artwork_transition_style_slide
        ModernArtworkTransitionStyle.DEPTH_SCALE -> R.string.modern_option_artwork_transition_style_depth_scale
        ModernArtworkTransitionStyle.COVER_FLOW -> R.string.modern_option_artwork_transition_style_cover_flow
        ModernArtworkTransitionStyle.STACK_REVEAL -> R.string.modern_option_artwork_transition_style_stack_reveal
    }

@Composable
internal fun ModernArtworkTransitionStyle.localizedLabel(): String = stringResource(labelRes)

internal val ModernArtworkTransitionStyle.descriptionRes: Int
    @StringRes get() = when (this) {
        ModernArtworkTransitionStyle.SLIDE -> R.string.modern_option_artwork_transition_slide_description
        ModernArtworkTransitionStyle.DEPTH_SCALE -> R.string.modern_option_artwork_transition_depth_scale_description
        ModernArtworkTransitionStyle.COVER_FLOW -> R.string.modern_option_artwork_transition_cover_flow_description
        ModernArtworkTransitionStyle.STACK_REVEAL -> R.string.modern_option_artwork_transition_stack_reveal_description
    }

internal val ModernSeekbarStyle.labelRes: Int
    @StringRes get() = when (this) {
        ModernSeekbarStyle.CLASSIC_BAR -> R.string.modern_option_seekbar_style_classic_bar
        ModernSeekbarStyle.SLIM_LINE -> R.string.modern_option_seekbar_style_slim_line
        ModernSeekbarStyle.THICK_CAPSULE -> R.string.modern_option_seekbar_style_thick_capsule
        ModernSeekbarStyle.SEGMENTED -> R.string.modern_option_seekbar_style_segmented
        ModernSeekbarStyle.WAVEFORM_PREVIEW -> R.string.modern_option_seekbar_style_waveform_preview
        ModernSeekbarStyle.WAVEFORM_PEAKS -> R.string.modern_option_seekbar_style_waveform_peaks
        ModernSeekbarStyle.WAVEFORM_GLOW -> R.string.modern_option_seekbar_style_waveform_glow
        ModernSeekbarStyle.CONTINUOUS_WAVEFORM -> R.string.modern_option_seekbar_style_continuous_waveform
        ModernSeekbarStyle.WAVE_LINE -> R.string.modern_option_seekbar_style_wave_line
    }

@Composable
internal fun ModernSeekbarStyle.localizedLabel(): String = stringResource(labelRes)

internal val ModernWaveformSize.labelRes: Int
    @StringRes get() = when (this) {
        ModernWaveformSize.COMPACT -> R.string.modern_option_waveform_size_compact
        ModernWaveformSize.STANDARD -> R.string.modern_option_waveform_size_standard
        ModernWaveformSize.TALL -> R.string.modern_option_waveform_size_tall
    }

@Composable
internal fun ModernWaveformSize.localizedLabel(): String = stringResource(labelRes)

internal val ModernWaveformDensity.labelRes: Int
    @StringRes get() = when (this) {
        ModernWaveformDensity.SPARSE -> R.string.modern_option_waveform_density_sparse
        ModernWaveformDensity.BALANCED -> R.string.modern_option_waveform_density_balanced
        ModernWaveformDensity.DETAILED -> R.string.modern_option_waveform_density_detailed
    }

@Composable
internal fun ModernWaveformDensity.localizedLabel(): String = stringResource(labelRes)

internal val ModernSeekbarColorMode.labelRes: Int
    @StringRes get() = when (this) {
        ModernSeekbarColorMode.WHITE -> R.string.modern_option_seekbar_color_mode_white
        ModernSeekbarColorMode.APP_ACCENT -> R.string.modern_option_seekbar_color_mode_app_accent
        ModernSeekbarColorMode.ALBUM_DERIVED -> R.string.modern_option_seekbar_color_mode_album_derived
    }

@Composable
internal fun ModernSeekbarColorMode.localizedLabel(): String = stringResource(labelRes)

internal val ModernBackgroundStyle.labelRes: Int
    @StringRes get() = when (this) {
        ModernBackgroundStyle.BLURRED_ARTWORK -> R.string.modern_option_background_style_blurred_artwork
        ModernBackgroundStyle.DETAILED_ARTWORK -> R.string.modern_option_background_style_detailed_artwork
        ModernBackgroundStyle.ALBUM_GRADIENT -> R.string.modern_option_background_style_album_gradient
        ModernBackgroundStyle.SOLID_COLOR -> R.string.modern_option_background_style_solid_color
        ModernBackgroundStyle.PURE_BLACK -> R.string.modern_option_background_style_pure_black
    }

@Composable
internal fun ModernBackgroundStyle.localizedLabel(): String = stringResource(labelRes)

internal val ModernBlurStrength.labelRes: Int
    @StringRes get() = when (this) {
        ModernBlurStrength.LOW -> R.string.modern_option_blur_strength_low
        ModernBlurStrength.MEDIUM -> R.string.modern_option_blur_strength_medium
        ModernBlurStrength.HIGH -> R.string.modern_option_blur_strength_high
    }

@Composable
internal fun ModernBlurStrength.localizedLabel(): String = stringResource(labelRes)

internal val ModernDimmingStrength.labelRes: Int
    @StringRes get() = when (this) {
        ModernDimmingStrength.LOW -> R.string.modern_option_dimming_strength_low
        ModernDimmingStrength.MEDIUM -> R.string.modern_option_dimming_strength_medium
        ModernDimmingStrength.HIGH -> R.string.modern_option_dimming_strength_high
    }

@Composable
internal fun ModernDimmingStrength.localizedLabel(): String = stringResource(labelRes)

internal val ModernArtworkShape.labelRes: Int
    @StringRes get() = when (this) {
        ModernArtworkShape.SQUARE -> R.string.modern_option_artwork_shape_square
        ModernArtworkShape.SUBTLE_ROUNDED -> R.string.modern_option_artwork_shape_subtle_rounded
        ModernArtworkShape.ROUNDED -> R.string.modern_option_artwork_shape_rounded
        ModernArtworkShape.EXTRA_ROUNDED -> R.string.modern_option_artwork_shape_extra_rounded
    }

@Composable
internal fun ModernArtworkShape.localizedLabel(): String = stringResource(labelRes)

internal val ModernArtworkSize.labelRes: Int
    @StringRes get() = when (this) {
        ModernArtworkSize.COMPACT -> R.string.modern_option_artwork_size_compact
        ModernArtworkSize.STANDARD -> R.string.modern_option_artwork_size_standard
        ModernArtworkSize.LARGE -> R.string.modern_option_artwork_size_large
    }

@Composable
internal fun ModernArtworkSize.localizedLabel(): String = stringResource(labelRes)

internal val ModernArtworkFit.labelRes: Int
    @StringRes get() = when (this) {
        ModernArtworkFit.CROP -> R.string.modern_option_artwork_fit_crop
        ModernArtworkFit.SHOW_FULL -> R.string.modern_option_artwork_fit_show_full
    }

@Composable
internal fun ModernArtworkFit.localizedLabel(): String = stringResource(labelRes)

internal val ModernArtworkShadow.labelRes: Int
    @StringRes get() = when (this) {
        ModernArtworkShadow.NONE -> R.string.modern_option_artwork_shadow_none
        ModernArtworkShadow.SOFT -> R.string.modern_option_artwork_shadow_soft
        ModernArtworkShadow.STRONG -> R.string.modern_option_artwork_shadow_strong
    }

@Composable
internal fun ModernArtworkShadow.localizedLabel(): String = stringResource(labelRes)

internal val ModernControlStyle.labelRes: Int
    @StringRes get() = when (this) {
        ModernControlStyle.MINIMAL -> R.string.modern_option_control_style_minimal
        ModernControlStyle.GLASS -> R.string.modern_option_control_style_glass
        ModernControlStyle.TONAL -> R.string.modern_option_control_style_tonal
        ModernControlStyle.OUTLINE -> R.string.modern_option_control_style_outline
    }

@Composable
internal fun ModernControlStyle.localizedLabel(): String = stringResource(labelRes)

internal val ModernControlSize.labelRes: Int
    @StringRes get() = when (this) {
        ModernControlSize.COMPACT -> R.string.modern_option_control_size_compact
        ModernControlSize.STANDARD -> R.string.modern_option_control_size_standard
        ModernControlSize.LARGE -> R.string.modern_option_control_size_large
    }

@Composable
internal fun ModernControlSize.localizedLabel(): String = stringResource(labelRes)

internal val ModernControlAccent.labelRes: Int
    @StringRes get() = when (this) {
        ModernControlAccent.WHITE -> R.string.modern_option_control_accent_white
        ModernControlAccent.APP_ACCENT -> R.string.modern_option_control_accent_app_accent
        ModernControlAccent.ALBUM_DERIVED -> R.string.modern_option_control_accent_album_derived
    }

@Composable
internal fun ModernControlAccent.localizedLabel(): String = stringResource(labelRes)

internal val ModernLayoutDensity.labelRes: Int
    @StringRes get() = when (this) {
        ModernLayoutDensity.COMPACT -> R.string.modern_option_layout_density_compact
        ModernLayoutDensity.BALANCED -> R.string.modern_option_layout_density_balanced
        ModernLayoutDensity.RELAXED -> R.string.modern_option_layout_density_relaxed
    }

@Composable
internal fun ModernLayoutDensity.localizedLabel(): String = stringResource(labelRes)

internal val ModernMetadataAlignment.labelRes: Int
    @StringRes get() = when (this) {
        ModernMetadataAlignment.LEFT -> R.string.modern_option_metadata_alignment_left
        ModernMetadataAlignment.CENTER -> R.string.modern_option_metadata_alignment_center
    }

@Composable
internal fun ModernMetadataAlignment.localizedLabel(): String = stringResource(labelRes)
