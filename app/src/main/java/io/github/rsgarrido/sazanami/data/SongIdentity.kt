package io.github.rsgarrido.sazanami.data

import java.security.MessageDigest
import java.util.Locale

/**
 * Identity tiers for a current library song.
 *
 * Local identity is current-device MediaStore identity. Source identity describes the file's
 * current relative location. Portable identity is normalized metadata and duration and can be
 * ambiguous. Legacy identity preserves the pre-v7 metadata hash solely for compatibility.
 */
data class SongIdentity(
    val localKey: String?,
    val sourceKey: String?,
    val fileSignatureKey: String?,
    val portableKey: String?,
    val legacyKey: String
) {
    val membershipKey: String
        get() = localKey ?: sourceKey ?: portableKey ?: "legacy:$legacyKey"

    companion object {
        const val LOCAL_KEY_VERSION = 1
        const val SOURCE_KEY_VERSION = 1
        const val PORTABLE_KEY_VERSION = 1
    }
}

fun Song.songIdentity(): SongIdentity = cachedIdentity

internal fun buildSongIdentity(song: Song): SongIdentity = with(song) {
    val contentUri = uri.toString().trim()
    val normalizedVolume = volumeName.identityNormalized()
    val normalizedRelativePath = relativePath.identityPathNormalized()
    val normalizedDisplayName = displayName.identityNormalized()
    val localRaw = when {
        id > 0L && normalizedVolume.isNotBlank() -> "$normalizedVolume|$id"
        contentUri.isNotBlank() -> contentUri
        else -> null
    }
    val sourceRaw = when {
        normalizedRelativePath.isNotBlank() && normalizedDisplayName.isNotBlank() ->
            "$normalizedVolume|$normalizedRelativePath|$normalizedDisplayName"
        filePath.isNotBlank() -> filePath.identityPathNormalized()
        else -> null
    }
    val signatureRaw = if (
        normalizedDisplayName.isNotBlank() && fileSizeBytes > 0L && duration > 0L
    ) {
        "$normalizedDisplayName|$fileSizeBytes|$duration"
    } else {
        null
    }
    val portableRaw = portableMetadataRaw(title, artist, album, duration)

    SongIdentity(
        localKey = localRaw?.versionedHash("local", SongIdentity.LOCAL_KEY_VERSION),
        sourceKey = sourceRaw?.versionedHash("source", SongIdentity.SOURCE_KEY_VERSION),
        fileSignatureKey = signatureRaw?.versionedHash("file", 1),
        portableKey = portableRaw?.versionedHash("portable", SongIdentity.PORTABLE_KEY_VERSION),
        legacyKey = stableKey()
    )
}

fun Song.membershipKey(): String = songIdentity().membershipKey

/**
 * Stable identity for a song row/card in a rendered library collection.
 *
 * MediaStore row IDs are only local to their volume, so [Song.id] alone is not a safe lazy-item
 * key. The membership identity is volume-qualified when MediaStore metadata is available and
 * falls back through the existing durable source/portable identity tiers for cached legacy rows.
 */
fun Song.stableUiKey(): String = membershipKey()

internal fun portableMetadataKey(
    title: String,
    artist: String,
    album: String,
    duration: Long
): String? = portableMetadataRaw(title, artist, album, duration)
    ?.versionedHash("portable", SongIdentity.PORTABLE_KEY_VERSION)

private fun portableMetadataRaw(
    title: String,
    artist: String,
    album: String,
    duration: Long
): String? {
    if (duration <= 0L) return null
    val normalizedTitle = title.identityNormalized().takeUnless { it.isUnsafeUnknownMetadata() }
    val normalizedArtist = artist.identityNormalized().takeUnless { it.isUnsafeUnknownMetadata() }
    val normalizedAlbum = album.identityNormalized().takeUnless { it.isUnsafeUnknownMetadata() }
    if (normalizedTitle == null && normalizedArtist == null && normalizedAlbum == null) return null
    return listOf(
        normalizedTitle.orEmpty(),
        normalizedArtist.orEmpty(),
        normalizedAlbum.orEmpty(),
        duration.toString()
    ).joinToString("|")
}

internal fun String.identityNormalized(): String = trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("\\s+"), " ")

internal fun String.identityPathNormalized(): String = replace('\\', '/')
    .trim()
    .trim('/')
    .lowercase(Locale.ROOT)

private fun String.isUnsafeUnknownMetadata(): Boolean {
    return isBlank() || this == "unknown" || startsWith("unknown ") || this == "<unknown>"
}

private fun String.versionedHash(kind: String, version: Int): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    val hex = digest.joinToString("") { byte -> "%02x".format(byte) }
    return "$kind:v$version:$hex"
}
