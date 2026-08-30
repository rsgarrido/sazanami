package io.github.rsgarrido.sazanami.data

import java.security.MessageDigest
import java.util.Locale

data class ArtistIdentity(
    val key: String,
    val normalizedName: String,
    val isUnknown: Boolean
) {
    val supportsCustomPicture: Boolean get() = !isUnknown
}

fun artistIdentity(name: String): ArtistIdentity {
    val normalized = normalizeArtistName(name)
    if (normalized.isEmpty()) return UNKNOWN_ARTIST_IDENTITY
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return ArtistIdentity(
        key = "artist_$digest",
        normalizedName = normalized,
        isUnknown = false
    )
}

fun normalizeArtistName(name: String): String = name
    .trim()
    .replace(Regex("\\s+"), " ")
    .lowercase(Locale.ROOT)

val UNKNOWN_ARTIST_IDENTITY = ArtistIdentity(
    key = "artist_unknown",
    normalizedName = "",
    isUnknown = true
)

const val UNKNOWN_ARTIST_DISPLAY_NAME = "Unknown Artist"
