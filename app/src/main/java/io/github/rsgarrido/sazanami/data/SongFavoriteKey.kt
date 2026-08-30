package io.github.rsgarrido.sazanami.data

import java.security.MessageDigest

/** Pre-Phase-2 metadata identity retained for database and backup compatibility only. */
fun Song.stableKey(): String {
    return stableSongKey(
        title = title,
        artist = artist,
        album = album,
        duration = duration
    )
}

fun stableSongKey(
    title: String,
    artist: String,
    album: String,
    duration: Long
): String {
    val rawKey = listOf(
        title.normalizedForStableKey(),
        artist.normalizedForStableKey(),
        album.normalizedForStableKey(),
        duration.toString()
    ).joinToString("|")

    return rawKey.sha256()
}

private fun String.normalizedForStableKey(): String {
    return trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
}

private fun String.sha256(): String {
    val bytes = MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray())

    return bytes.joinToString("") { byte ->
        "%02x".format(byte)
    }
}
