package io.github.rsgarrido.sazanami.player

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

enum class AndroidAutoRequestType {
    SONG,
    ARTIST,
    ALBUM,
    PLAYLIST,
    GENRE,
    GENERIC,
    MEDIA_ID,
    UNKNOWN
}

internal data class AndroidAutoRequestPayload(
    val query: String? = null,
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val playlist: String? = null,
    val genre: String? = null,
    val focus: String? = null,
    val mediaType: Int? = null
)

/** Both Assistant entry points preserve the same query, structured fields, and media focus. */
internal fun Intent.androidAutoVoiceItem(): MediaItem? {
    if (action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return null
    return MediaItem.Builder().setRequestMetadata(
        MediaItem.RequestMetadata.Builder()
            .setSearchQuery(getStringExtra(SearchManager.QUERY).orEmpty())
            .setExtras(extras)
            .build()
    ).build()
}

@Suppress("DEPRECATION")
internal fun MediaItem.toAndroidAutoSearchRequest(): AndroidAutoSearchRequest {
    val extras = requestMetadata.extras ?: mediaMetadata.extras ?: Bundle.EMPTY
    return parseAndroidAutoRequest(
        AndroidAutoRequestPayload(
            query = requestMetadata.searchQuery ?: extras.getString(SearchManager.QUERY),
            mediaId = mediaId.takeIf(String::isNotBlank),
            title = extras.getString(MediaStore.EXTRA_MEDIA_TITLE) ?: mediaMetadata.title?.toString(),
            artist = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST) ?: mediaMetadata.artist?.toString(),
            album = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM) ?: mediaMetadata.albumTitle?.toString(),
            playlist = extras.getString(MediaStore.EXTRA_MEDIA_PLAYLIST),
            genre = extras.getString(MediaStore.EXTRA_MEDIA_GENRE),
            focus = extras.getString(MediaStore.EXTRA_MEDIA_FOCUS),
            mediaType = mediaMetadata.mediaType
        )
    )
}

internal fun parseAndroidAutoRequest(payload: AndroidAutoRequestPayload): AndroidAutoSearchRequest {
    val requestType = payload.focus.toAndroidAutoRequestType()
        ?: payload.mediaType.toAndroidAutoRequestType()
        ?: payload.structuredRequestType()
        ?: if (payload.query.isGenericVoiceQuery()) AndroidAutoRequestType.GENERIC
        else if (!payload.mediaId.isNullOrBlank() && payload.query.isNullOrBlank()) {
            AndroidAutoRequestType.MEDIA_ID
        } else {
            AndroidAutoRequestType.UNKNOWN
        }
    val queryEntity = payload.query.voiceEntityQuery()
    val queryByArtist = queryEntity?.splitVoiceEntityByArtist()

    val title = when (requestType) {
        AndroidAutoRequestType.SONG -> payload.title ?: queryByArtist?.first ?: queryEntity
        AndroidAutoRequestType.UNKNOWN -> payload.title
        else -> null
    }
    val artist = when (requestType) {
        AndroidAutoRequestType.ARTIST -> payload.artist ?: payload.title ?: queryEntity
        AndroidAutoRequestType.SONG -> payload.artist ?: queryByArtist?.second
        AndroidAutoRequestType.ALBUM -> payload.artist ?: queryByArtist?.second
        AndroidAutoRequestType.UNKNOWN -> payload.artist
        else -> null
    }
    val album = when (requestType) {
        AndroidAutoRequestType.ALBUM -> payload.album ?: payload.title ?: queryByArtist?.first ?: queryEntity
        AndroidAutoRequestType.SONG, AndroidAutoRequestType.UNKNOWN -> payload.album
        else -> null
    }
    val playlist = when (requestType) {
        AndroidAutoRequestType.PLAYLIST -> payload.playlist ?: payload.title ?: queryEntity
        AndroidAutoRequestType.UNKNOWN -> payload.playlist
        else -> null
    }
    val genre = when (requestType) {
        AndroidAutoRequestType.GENRE -> payload.genre ?: payload.title ?: queryEntity
        AndroidAutoRequestType.UNKNOWN -> payload.genre
        else -> null
    }
    return AndroidAutoSearchRequest(
        query = payload.query,
        title = title,
        artist = artist,
        album = album,
        playlist = playlist,
        genre = genre,
        mediaId = payload.mediaId,
        focus = payload.focus,
        mediaType = payload.mediaType,
        requestType = requestType
    )
}

private fun AndroidAutoRequestPayload.structuredRequestType(): AndroidAutoRequestType? = when {
    !playlist.isNullOrBlank() -> AndroidAutoRequestType.PLAYLIST
    !genre.isNullOrBlank() -> AndroidAutoRequestType.GENRE
    !title.isNullOrBlank() -> AndroidAutoRequestType.SONG
    !album.isNullOrBlank() -> AndroidAutoRequestType.ALBUM
    !artist.isNullOrBlank() -> AndroidAutoRequestType.ARTIST
    else -> null
}

private fun String?.toAndroidAutoRequestType(): AndroidAutoRequestType? = when (this) {
    "vnd.android.cursor.item/audio" -> AndroidAutoRequestType.SONG
    "vnd.android.cursor.item/artist" -> AndroidAutoRequestType.ARTIST
    "vnd.android.cursor.item/album" -> AndroidAutoRequestType.ALBUM
    "vnd.android.cursor.item/playlist" -> AndroidAutoRequestType.PLAYLIST
    "vnd.android.cursor.item/genre" -> AndroidAutoRequestType.GENRE
    else -> null
}

private fun Int?.toAndroidAutoRequestType(): AndroidAutoRequestType? = when (this) {
    MediaMetadata.MEDIA_TYPE_MUSIC -> AndroidAutoRequestType.SONG
    MediaMetadata.MEDIA_TYPE_ARTIST -> AndroidAutoRequestType.ARTIST
    MediaMetadata.MEDIA_TYPE_ALBUM -> AndroidAutoRequestType.ALBUM
    MediaMetadata.MEDIA_TYPE_PLAYLIST -> AndroidAutoRequestType.PLAYLIST
    MediaMetadata.MEDIA_TYPE_GENRE -> AndroidAutoRequestType.GENRE
    else -> null
}

internal fun String?.voiceEntityQuery(): String? = this
    ?.trim()
    ?.replace(Regex("^(please\\s+)?(play|listen\\s+to)(\\s+me)?\\s+", RegexOption.IGNORE_CASE), "")
    ?.replace(Regex("\\s+on\\s+sazanami\\s*$", RegexOption.IGNORE_CASE), "")
    ?.trim()
    ?.takeIf(String::isNotBlank)

internal fun String.splitVoiceEntityByArtist(): Pair<String, String>? {
    val marker = Regex("\\s+by\\s+", RegexOption.IGNORE_CASE).find(this) ?: return null
    val entity = substring(0, marker.range.first).trim()
    val artist = substring(marker.range.last + 1).trim()
    return if (entity.isNotBlank() && artist.isNotBlank()) entity to artist else null
}

private fun String?.isGenericVoiceQuery(): Boolean = voiceEntityQuery()?.lowercase() in setOf(
    "music",
    "my music",
    "songs",
    "all songs",
    "my songs",
    "library",
    "my library"
)

internal fun AndroidAutoSearchRequest.diagnosticSummary(): String =
    "type=$requestType mediaId=${mediaId.orEmpty()} focus=${focus.orEmpty()} mediaType=$mediaType " +
            "query=${query.orEmpty()} title=${title.orEmpty()} artist=${artist.orEmpty()} " +
            "album=${album.orEmpty()} playlist=${playlist.orEmpty()} genre=${genre.orEmpty()}"

@Suppress("DEPRECATION")
internal fun MediaItem.rawVoiceMetadataSummary(): String {
    val extras = requestMetadata.extras ?: mediaMetadata.extras ?: Bundle.EMPTY
    return "requestQuery=${requestMetadata.searchQuery.orEmpty()} mediaId=$mediaId " +
            "metadataTitle=${mediaMetadata.title?.toString().orEmpty()} " +
            "metadataArtist=${mediaMetadata.artist?.toString().orEmpty()} " +
            "metadataAlbum=${mediaMetadata.albumTitle?.toString().orEmpty()} mediaType=${mediaMetadata.mediaType} " +
            "extraQuery=${extras.getString(SearchManager.QUERY).orEmpty()} " +
            "extraTitle=${extras.getString(MediaStore.EXTRA_MEDIA_TITLE).orEmpty()} " +
            "extraArtist=${extras.getString(MediaStore.EXTRA_MEDIA_ARTIST).orEmpty()} " +
            "extraAlbum=${extras.getString(MediaStore.EXTRA_MEDIA_ALBUM).orEmpty()} " +
            "extraPlaylist=${extras.getString(MediaStore.EXTRA_MEDIA_PLAYLIST).orEmpty()} " +
            "extraGenre=${extras.getString(MediaStore.EXTRA_MEDIA_GENRE).orEmpty()} " +
            "extraFocus=${extras.getString(MediaStore.EXTRA_MEDIA_FOCUS).orEmpty()}"
}
