package io.github.rsgarrido.sazanami.data

import io.github.rsgarrido.sazanami.data.local.FavoriteSongEntity
import io.github.rsgarrido.sazanami.data.local.PlaylistSongEntity
import io.github.rsgarrido.sazanami.data.local.SongPlayStatsEntity

internal fun FavoriteSongEntity.toSongReference() = SongReference(
    mediaStoreId = mediaStoreId,
    volumeName = volumeName,
    contentUri = contentUri,
    relativePath = relativePath,
    displayName = displayName,
    fileSizeBytes = fileSizeBytes,
    dateModifiedEpochSeconds = dateModifiedEpochSeconds,
    duration = duration,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    legacyStableKey = songKey,
    portableKey = portableKey,
    portableKeyVersion = portableKeyVersion
)

internal fun PlaylistSongEntity.toSongReference() = SongReference(
    mediaStoreId = mediaStoreId,
    volumeName = volumeName,
    contentUri = contentUri,
    relativePath = relativePath,
    displayName = displayName,
    fileSizeBytes = fileSizeBytes,
    dateModifiedEpochSeconds = dateModifiedEpochSeconds,
    duration = duration,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    legacyStableKey = songKey,
    portableKey = portableKey,
    portableKeyVersion = portableKeyVersion
)

internal fun SongPlayStatsEntity.toSongReference() = SongReference(
    mediaStoreId = mediaStoreId,
    volumeName = volumeName,
    contentUri = contentUri,
    relativePath = relativePath,
    displayName = displayName,
    fileSizeBytes = fileSizeBytes,
    dateModifiedEpochSeconds = dateModifiedEpochSeconds,
    duration = duration,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    legacyStableKey = songKey,
    portableKey = portableKey,
    portableKeyVersion = portableKeyVersion
)

internal fun FavoriteSongEntity.withSongReference(song: Song): FavoriteSongEntity {
    val reference = song.toSongReference().normalizedForPersistence()
    return copy(
        referenceKey = song.membershipKey(),
        songKey = reference.legacyStableKey,
        title = reference.title,
        artist = reference.artist,
        album = reference.album,
        duration = reference.duration,
        mediaStoreId = reference.mediaStoreId,
        volumeName = reference.volumeName,
        contentUri = reference.contentUri,
        relativePath = reference.relativePath,
        displayName = reference.displayName,
        fileSizeBytes = reference.fileSizeBytes,
        dateModifiedEpochSeconds = reference.dateModifiedEpochSeconds,
        albumArtist = reference.albumArtist,
        portableKey = reference.portableKey,
        portableKeyVersion = reference.portableKeyVersion
    )
}

internal fun PlaylistSongEntity.withSongReference(song: Song): PlaylistSongEntity {
    val reference = song.toSongReference().normalizedForPersistence()
    return copy(
        songKey = reference.legacyStableKey,
        title = reference.title,
        artist = reference.artist,
        album = reference.album,
        duration = reference.duration,
        mediaStoreId = reference.mediaStoreId,
        volumeName = reference.volumeName,
        contentUri = reference.contentUri,
        relativePath = reference.relativePath,
        displayName = reference.displayName,
        fileSizeBytes = reference.fileSizeBytes,
        dateModifiedEpochSeconds = reference.dateModifiedEpochSeconds,
        albumArtist = reference.albumArtist,
        portableKey = reference.portableKey,
        portableKeyVersion = reference.portableKeyVersion
    )
}

internal fun SongPlayStatsEntity.withSongReference(song: Song): SongPlayStatsEntity {
    val reference = song.toSongReference().normalizedForPersistence()
    return copy(
        referenceKey = song.membershipKey(),
        songKey = reference.legacyStableKey,
        title = reference.title,
        artist = reference.artist,
        album = reference.album,
        duration = reference.duration,
        mediaStoreId = reference.mediaStoreId,
        volumeName = reference.volumeName,
        contentUri = reference.contentUri,
        relativePath = reference.relativePath,
        displayName = reference.displayName,
        fileSizeBytes = reference.fileSizeBytes,
        dateModifiedEpochSeconds = reference.dateModifiedEpochSeconds,
        albumArtist = reference.albumArtist,
        portableKey = reference.portableKey,
        portableKeyVersion = reference.portableKeyVersion
    )
}

data class SongReferenceReconciliation(
    val resolvedMembershipKeys: Set<String> = emptySet(),
    val unresolvedCount: Int = 0,
    val ambiguousCount: Int = 0,
    val backfilledCount: Int = 0,
    val inspectedCount: Int = 0
)
