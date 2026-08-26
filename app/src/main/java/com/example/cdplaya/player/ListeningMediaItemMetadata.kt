package com.example.cdplaya.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.SongReference
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.data.toSongReference
import java.util.UUID

/** Immutable evidence carried with every playable local item for service-side history recording. */
data class ListeningMediaItemEvidence(
    val itemInstanceId: String,
    val referenceKey: String,
    val reference: SongReference
)

object ListeningMediaItemMetadata {
    private const val PREFIX = "com.example.cdplaya.listening."
    const val ITEM_INSTANCE_ID = PREFIX + "item_instance_id"
    const val REFERENCE_KEY = PREFIX + "reference_key"
    const val MEDIA_STORE_ID = PREFIX + "media_store_id"
    const val VOLUME_NAME = PREFIX + "volume_name"
    const val CONTENT_URI = PREFIX + "content_uri"
    const val RELATIVE_PATH = PREFIX + "relative_path"
    const val DISPLAY_NAME = PREFIX + "display_name"
    const val FILE_SIZE_BYTES = PREFIX + "file_size_bytes"
    const val DATE_MODIFIED_SECONDS = PREFIX + "date_modified_seconds"
    const val DURATION_MS = PREFIX + "duration_ms"
    const val TITLE = PREFIX + "title"
    const val ARTIST = PREFIX + "artist"
    const val ALBUM = PREFIX + "album"
    const val ALBUM_ARTIST = PREFIX + "album_artist"
    const val LEGACY_STABLE_KEY = PREFIX + "legacy_stable_key"
    const val PORTABLE_KEY = PREFIX + "portable_key"
    const val PORTABLE_KEY_VERSION = PREFIX + "portable_key_version"

    private val ALL_KEYS = listOf(
        ITEM_INSTANCE_ID, REFERENCE_KEY, MEDIA_STORE_ID, VOLUME_NAME, CONTENT_URI,
        RELATIVE_PATH, DISPLAY_NAME, FILE_SIZE_BYTES, DATE_MODIFIED_SECONDS, DURATION_MS,
        TITLE, ARTIST, ALBUM, ALBUM_ARTIST, LEGACY_STABLE_KEY, PORTABLE_KEY,
        PORTABLE_KEY_VERSION
    )

    fun toExtras(evidence: ListeningMediaItemEvidence): Bundle {
        val reference = evidence.reference
        return Bundle().apply {
            putString(ITEM_INSTANCE_ID, evidence.itemInstanceId)
            putString(REFERENCE_KEY, evidence.referenceKey)
            reference.mediaStoreId?.let { putLong(MEDIA_STORE_ID, it) }
            putString(VOLUME_NAME, reference.volumeName)
            putString(CONTENT_URI, reference.contentUri)
            putString(RELATIVE_PATH, reference.relativePath)
            putString(DISPLAY_NAME, reference.displayName)
            putLong(FILE_SIZE_BYTES, reference.fileSizeBytes)
            putLong(DATE_MODIFIED_SECONDS, reference.dateModifiedEpochSeconds)
            putLong(DURATION_MS, reference.duration)
            putString(TITLE, reference.title)
            putString(ARTIST, reference.artist)
            putString(ALBUM, reference.album)
            putString(ALBUM_ARTIST, reference.albumArtist)
            putString(LEGACY_STABLE_KEY, reference.legacyStableKey)
            putString(PORTABLE_KEY, reference.portableKey)
            putInt(PORTABLE_KEY_VERSION, reference.portableKeyVersion)
        }
    }

    fun fromExtras(extras: Bundle?): ListeningMediaItemEvidence? {
        if (extras == null) return null
        return fromValues(ALL_KEYS.associateWith { key -> extras.get(key) })
    }

    /** Pure parser used by local tests and by the Bundle boundary above. */
    fun fromValues(values: Map<String, Any?>): ListeningMediaItemEvidence? {
        val itemInstanceId = (values[ITEM_INSTANCE_ID] as? String)?.trim().orEmpty()
        val referenceKey = (values[REFERENCE_KEY] as? String)?.trim().orEmpty()
        if (itemInstanceId.isBlank() || referenceKey.isBlank()) return null

        fun string(key: String) = (values[key] as? String)?.trim().orEmpty()
        fun nonNegativeLong(key: String): Long? = (values[key] as? Long)?.takeIf { it >= 0L }
        val mediaStoreId = (values[MEDIA_STORE_ID] as? Long)?.takeIf { it > 0L }
        val fileSize = nonNegativeLong(FILE_SIZE_BYTES) ?: return null
        val modified = nonNegativeLong(DATE_MODIFIED_SECONDS) ?: return null
        val duration = nonNegativeLong(DURATION_MS) ?: return null
        val portableVersion = (values[PORTABLE_KEY_VERSION] as? Int)?.takeIf { it > 0 }
            ?: return null

        return ListeningMediaItemEvidence(
            itemInstanceId = itemInstanceId,
            referenceKey = referenceKey,
            reference = SongReference(
                mediaStoreId = mediaStoreId,
                volumeName = string(VOLUME_NAME),
                contentUri = string(CONTENT_URI),
                relativePath = string(RELATIVE_PATH),
                displayName = string(DISPLAY_NAME),
                fileSizeBytes = fileSize,
                dateModifiedEpochSeconds = modified,
                duration = duration,
                title = string(TITLE),
                artist = string(ARTIST),
                album = string(ALBUM),
                albumArtist = string(ALBUM_ARTIST),
                legacyStableKey = string(LEGACY_STABLE_KEY),
                portableKey = string(PORTABLE_KEY),
                portableKeyVersion = portableVersion
            )
        )
    }
}

internal fun Song.toPlayableMediaItem(
    itemInstanceId: String = UUID.randomUUID().toString()
): MediaItem {
    val evidence = ListeningMediaItemEvidence(
        itemInstanceId = itemInstanceId,
        referenceKey = membershipKey(),
        reference = toSongReference()
    )
    val extras = ListeningMediaItemMetadata.toExtras(evidence).apply {
        putInt(AlbumTransitionMetadata.RAW_TRACK_NUMBER, trackNumber)
        putString(AlbumTransitionMetadata.FOLDER_PATH, folderPath)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(albumArtUri)
        .setExtras(extras)
        .build()

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}

internal fun MediaItem.listeningEvidence(): ListeningMediaItemEvidence? =
    ListeningMediaItemMetadata.fromExtras(mediaMetadata.extras)
