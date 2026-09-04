package io.github.rsgarrido.sazanami.player

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem

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
    val query = requestMetadata.searchQuery ?: extras.getString(SearchManager.QUERY)
    val focus = extras.getString(MediaStore.EXTRA_MEDIA_FOCUS)
    val songFocus = focus == null || focus == "vnd.android.cursor.item/audio"
    val albumFocus = songFocus || focus == "vnd.android.cursor.item/album"
    val artistFocus = albumFocus || focus == "vnd.android.cursor.item/artist"
    return AndroidAutoSearchRequest(
        query = query,
        title = if (songFocus) extras.getString(MediaStore.EXTRA_MEDIA_TITLE)
            ?: query?.let { mediaMetadata.title?.toString() } else null,
        artist = if (artistFocus) extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)
            ?: query?.let { mediaMetadata.artist?.toString() } else null,
        album = if (albumFocus) extras.getString(MediaStore.EXTRA_MEDIA_ALBUM)
            ?: query?.let { mediaMetadata.albumTitle?.toString() } else null,
        playlist = if (focus == null || focus == "vnd.android.cursor.item/playlist")
            extras.getString(MediaStore.EXTRA_MEDIA_PLAYLIST) else null,
        genre = if (focus == null || focus == "vnd.android.cursor.item/genre")
            extras.getString(MediaStore.EXTRA_MEDIA_GENRE) else null
    )
}
