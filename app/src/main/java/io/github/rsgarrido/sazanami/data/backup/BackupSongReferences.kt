package io.github.rsgarrido.sazanami.data.backup

import io.github.rsgarrido.sazanami.data.SongIdentity
import io.github.rsgarrido.sazanami.data.SongReference
import java.security.MessageDigest

fun BackupSongReference.toSongReference(): SongReference = SongReference(
    relativePath = relativePath,
    displayName = displayName,
    fileSizeBytes = fileSizeBytes,
    duration = duration,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    legacyStableKey = legacyStableKey,
    portableKey = portableKey,
    portableKeyVersion = portableKeyVersion
)

fun SongReference.toBackupSongReference(): BackupSongReference = BackupSongReference(
    relativePath = relativePath.takeUnless { it.isAbsolutePathLike() }.orEmpty(),
    displayName = displayName,
    fileSizeBytes = fileSizeBytes,
    duration = duration,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    legacyStableKey = legacyStableKey,
    portableKey = portableKey,
    portableKeyVersion = portableKeyVersion.takeIf { it > 0 }
        ?: SongIdentity.PORTABLE_KEY_VERSION
)

internal fun String.isAbsolutePathLike(): Boolean {
    val normalized = replace('\\', '/')
    return normalized.startsWith('/') || Regex("^[A-Za-z]:/").containsMatchIn(normalized)
}

internal fun String.toPortableFolderSelection(): String {
    val raw = replace('\\', '/').trim()
    val wasAbsolute = raw.isAbsolutePathLike()
    val normalized = raw.trim('/')
    if (normalized.isBlank()) return ""
    val segments = normalized.split('/').filter { it.isNotBlank() }
    if (!wasAbsolute) return segments.joinToString("/")
    return when {
        segments.size >= 4 && segments[0].equals("storage", true) &&
            segments[1].equals("emulated", true) && segments[2] == "0" ->
            segments.drop(3).joinToString("/")
        segments.size >= 3 && segments[0].equals("storage", true) ->
            segments.drop(2).joinToString("/")
        segments.size >= 2 && segments[0].equals("sdcard", true) ->
            segments.drop(1).joinToString("/")
        Regex("^[A-Za-z]:$").matches(segments.first()) ->
            segments.drop(1).joinToString("/")
        else -> segments.joinToString("/")
    }
}

internal fun BackupSongReference.restoredReferenceKey(): String {
    val raw = listOf(
        relativePath,
        displayName,
        fileSizeBytes.toString(),
        portableKey,
        legacyStableKey
    ).joinToString("|")
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
    return "backup:v1:" + digest.joinToString("") { byte -> "%02x".format(byte) }
}
