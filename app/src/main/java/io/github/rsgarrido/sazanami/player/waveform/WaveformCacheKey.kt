package io.github.rsgarrido.sazanami.player.waveform

import java.security.MessageDigest

data class WaveformSource(
    val songId: Long,
    val filePath: String,
    val lastModified: Long,
    val fileLength: Long
)

internal fun waveformCacheKey(source: WaveformSource): String {
    val identity = buildString {
        append(WAVEFORM_CACHE_KEY_VERSION)
        append('\u0000')
        append(source.songId)
        append('\u0000')
        append(source.filePath)
        append('\u0000')
        append(source.lastModified)
        append('\u0000')
        append(source.fileLength)
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

internal const val WAVEFORM_CACHE_KEY_VERSION = 5
