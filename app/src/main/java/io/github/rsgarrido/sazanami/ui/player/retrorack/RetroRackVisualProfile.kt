package io.github.rsgarrido.sazanami.ui.player.retrorack

import androidx.compose.ui.graphics.Color

internal data class RetroRackVisualProfile(
    val levels: List<Float>,
    val accent: Color,
    val peak: Color,
    val phaseOffset: Float,
    val songSeed: Long
)

internal fun buildRetroRackVisualProfile(
    songId: Long?,
    title: String?,
    artist: String?,
    album: String?
): RetroRackVisualProfile {
    var songSeed = songId ?: 0x43_44_50L
    (title.orEmpty() + '\u0000' + artist.orEmpty()).forEach { character ->
        songSeed = songSeed * 1_099_511_628_211L xor character.code.toLong()
    }

    var state = songSeed
    val levels = List(RETRO_RACK_VISUALIZER_COLUMN_COUNT) {
        state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
        val normalized = ((state ushr 40) and 0xFFFF).toFloat() / 0xFFFF
        0.24f + normalized * 0.7f
    }
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
        levels = levels,
        accent = albumColors.accent,
        peak = albumColors.peak,
        phaseOffset = ((songSeed ushr 24) and 0xFF).toFloat() / 255f * 6.283f,
        songSeed = songSeed
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
