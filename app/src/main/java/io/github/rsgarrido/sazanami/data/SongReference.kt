package io.github.rsgarrido.sazanami.data

import kotlinx.serialization.Serializable

/** Persisted evidence used to resolve a song without assuming any one field is globally unique. */
@Serializable
data class SongReference(
    val mediaStoreId: Long? = null,
    val volumeName: String = "",
    val contentUri: String = "",
    val relativePath: String = "",
    val displayName: String = "",
    val fileSizeBytes: Long = 0L,
    val dateModifiedEpochSeconds: Long = 0L,
    val duration: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val legacyStableKey: String = "",
    val portableKey: String = "",
    val portableKeyVersion: Int = SongIdentity.PORTABLE_KEY_VERSION
)

fun Song.toSongReference(): SongReference {
    val identity = songIdentity()
    return SongReference(
        mediaStoreId = id.takeIf { it > 0L },
        volumeName = volumeName,
        contentUri = uri.toString(),
        relativePath = relativePath,
        displayName = displayName,
        fileSizeBytes = fileSizeBytes,
        dateModifiedEpochSeconds = dateModifiedEpochSeconds,
        duration = duration,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        legacyStableKey = identity.legacyKey,
        portableKey = identity.portableKey.orEmpty()
    ).normalizedForPersistence()
}

internal fun SongReference.normalizedForPersistence(): SongReference = copy(
    mediaStoreId = mediaStoreId?.takeIf { it > 0L },
    volumeName = volumeName.normalizedPersistedText(),
    contentUri = contentUri.normalizedPersistedText(),
    relativePath = relativePath.normalizedPersistedText(),
    displayName = displayName.normalizedPersistedText(),
    fileSizeBytes = fileSizeBytes.coerceAtLeast(0L),
    dateModifiedEpochSeconds = dateModifiedEpochSeconds.coerceAtLeast(0L),
    duration = duration.coerceAtLeast(0L),
    title = title.normalizedPersistedText(),
    artist = artist.normalizedPersistedText(),
    album = album.normalizedPersistedText(),
    albumArtist = albumArtist.normalizedPersistedText(),
    legacyStableKey = legacyStableKey.normalizedPersistedText(),
    portableKey = portableKey.normalizedPersistedText(),
    portableKeyVersion = portableKeyVersion.takeIf { it > 0 }
        ?: SongIdentity.PORTABLE_KEY_VERSION
)

private fun String.normalizedPersistedText(): String = trim().takeIf { it.isNotBlank() }.orEmpty()

fun SongReference.hasPersistedIdentity(): Boolean {
    return mediaStoreId != null || contentUri.isNotBlank() ||
            (relativePath.isNotBlank() && displayName.isNotBlank()) ||
            portableKey.isNotBlank() || legacyStableKey.isNotBlank()
}
