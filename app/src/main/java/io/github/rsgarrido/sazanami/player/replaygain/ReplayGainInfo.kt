package io.github.rsgarrido.sazanami.player.replaygain

data class ReplayGainInfo(
    val trackGainDb: Float?,
    val trackPeak: Float?,
    val albumGainDb: Float?,
    val albumPeak: Float?
) {
    val hasTrackGain: Boolean
        get() = trackGainDb != null

    val hasAlbumGain: Boolean
        get() = albumGainDb != null

    val hasAnyReplayGainData: Boolean
        get() = trackGainDb != null ||
                trackPeak != null ||
                albumGainDb != null ||
                albumPeak != null
}