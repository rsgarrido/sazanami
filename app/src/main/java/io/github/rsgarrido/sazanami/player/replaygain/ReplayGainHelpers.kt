package io.github.rsgarrido.sazanami.player.replaygain

import kotlin.math.pow

private val signedDecimalPattern = Regex("""[-+]?\d+(?:\.\d+)?""")

fun replayGainDbToVolumeMultiplier(gainDb: Float): Float {
    val rawMultiplier = 10.0.pow(gainDb / 20.0).toFloat()

    return rawMultiplier.coerceIn(
        minimumValue = 0f,
        maximumValue = 1f
    )
}

fun replayGainVolumeMultiplier(
    replayGainInfo: ReplayGainInfo?,
    replayGainMode: ReplayGainMode,
    isAlbumPlaybackContext: Boolean
): Float {
    val gainDb = selectReplayGainDb(
        replayGainInfo = replayGainInfo,
        replayGainMode = replayGainMode,
        isAlbumPlaybackContext = isAlbumPlaybackContext
    ) ?: return 1f

    return replayGainDbToVolumeMultiplier(gainDb)
}

fun replayGainTrackMultiplier(
    replayGainInfo: ReplayGainInfo?,
    replayGainMode: ReplayGainMode
): Float {
    return replayGainVolumeMultiplier(
        replayGainInfo = replayGainInfo,
        replayGainMode = replayGainMode,
        isAlbumPlaybackContext = false
    )
}

fun selectReplayGainDb(
    replayGainInfo: ReplayGainInfo?,
    replayGainMode: ReplayGainMode,
    isAlbumPlaybackContext: Boolean
): Float? {
    if (replayGainInfo == null || replayGainMode == ReplayGainMode.OFF) {
        return null
    }

    return when (replayGainMode) {
        ReplayGainMode.OFF -> null

        ReplayGainMode.TRACK -> {
            replayGainInfo.trackGainDb
        }

        ReplayGainMode.ALBUM -> {
            replayGainInfo.albumGainDb
                ?: replayGainInfo.trackGainDb
        }

        ReplayGainMode.SMART -> {
            if (isAlbumPlaybackContext) {
                replayGainInfo.albumGainDb
                    ?: replayGainInfo.trackGainDb
            } else {
                replayGainInfo.trackGainDb
            }
        }
    }
}

fun parseReplayGainDb(rawValue: String?): Float? {
    val cleanedValue = rawValue
        ?.trim()
        ?.takeIf { value ->
            value.isNotBlank()
        }
        ?: return null

    return signedDecimalPattern
        .find(cleanedValue)
        ?.value
        ?.toFloatOrNull()
}

fun parseReplayGainPeak(rawValue: String?): Float? {
    val cleanedValue = rawValue
        ?.trim()
        ?.takeIf { value ->
            value.isNotBlank()
        }
        ?: return null

    return signedDecimalPattern
        .find(cleanedValue)
        ?.value
        ?.toFloatOrNull()
}