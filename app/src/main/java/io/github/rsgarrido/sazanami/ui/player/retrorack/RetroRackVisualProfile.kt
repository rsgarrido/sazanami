package io.github.rsgarrido.sazanami.ui.player.retrorack

import androidx.compose.ui.graphics.Color

internal data class RetroRackVisualProfile(
    val accent: Color,
    val peak: Color
)

internal data class RetroRackLayoutProfile(
    val compact: Boolean,
    val mainDeckHeightDp: Int,
    val displayHeightDp: Int,
    val spectrumHeightDp: Int
)

internal fun buildRetroRackLayoutProfile(
    screenHeightDp: Int,
    screenWidthDp: Int,
    fontScale: Float
): RetroRackLayoutProfile {
    val compact = screenHeightDp < 700 || screenWidthDp < 360
    val largeText = fontScale.isFinite() && fontScale > 1.15f
    val baseProfile = when {
        screenHeightDp < 620 -> RetroRackLayoutProfile(
            compact = true,
            mainDeckHeightDp = 218,
            displayHeightDp = 78,
            spectrumHeightDp = 90
        )

        compact -> RetroRackLayoutProfile(
            compact = true,
            mainDeckHeightDp = 234,
            displayHeightDp = 86,
            spectrumHeightDp = 104
        )

        screenHeightDp >= 850 -> RetroRackLayoutProfile(
            compact = false,
            mainDeckHeightDp = 270,
            displayHeightDp = 104,
            spectrumHeightDp = 136
        )

        else -> RetroRackLayoutProfile(
            compact = false,
            mainDeckHeightDp = 258,
            displayHeightDp = 98,
            spectrumHeightDp = 124
        )
    }
    return if (largeText) {
        baseProfile.copy(
            mainDeckHeightDp = baseProfile.mainDeckHeightDp + 14,
            displayHeightDp = baseProfile.displayHeightDp + 10
        )
    } else {
        baseProfile
    }
}

internal fun buildRetroRackVisualProfile(
    artist: String?,
    album: String?
): RetroRackVisualProfile {
    var albumSeed = 0x52_41_43_4BL
    album.orEmpty().ifBlank { artist.orEmpty() }.forEach { character ->
        albumSeed = albumSeed * 1_099_511_628_211L xor character.code.toLong()
    }
    val albumColors = visualAccentPalette[
        ((albumSeed xor (albumSeed ushr 32)) and Long.MAX_VALUE)
            .rem(visualAccentPalette.size)
            .toInt()
    ]

    return RetroRackVisualProfile(
        accent = albumColors.accent,
        peak = albumColors.peak
    )
}

private data class VisualAccentColors(
    val accent: Color,
    val peak: Color
)

private val visualAccentPalette = listOf(
    VisualAccentColors(Color(0xFF75F05F), Color(0xFFE0C04A)),
    VisualAccentColors(Color(0xFF9DDB58), Color(0xFFD5AD48)),
    VisualAccentColors(Color(0xFF58D68D), Color(0xFFDFB952)),
    VisualAccentColors(Color(0xFF72C9A2), Color(0xFFE1C25D))
)

internal const val RETRO_RACK_VISUALIZER_COLUMN_COUNT = 18
