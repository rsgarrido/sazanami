package io.github.rsgarrido.sazanami.player.replaygain

enum class ReplayGainMode(
    val displayName: String,
    val description: String
) {
    OFF(
        displayName = "Off",
        description = "Playback volume is unchanged."
    ),
    TRACK(
        displayName = "Track gain",
        description = "Normalize each song individually using ReplayGain track tags."
    ),
    ALBUM(
        displayName = "Album gain",
        description = "Use album gain when available to preserve album loudness differences."
    ),
    SMART(
        displayName = "Smart",
        description = "Use album gain for album playback and track gain for mixed playback."
    )
}