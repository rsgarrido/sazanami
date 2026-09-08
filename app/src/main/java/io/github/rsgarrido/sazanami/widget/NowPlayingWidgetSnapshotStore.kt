package io.github.rsgarrido.sazanami.widget

import android.content.Context

internal object NowPlayingWidgetLiveState {
    @Volatile
    var snapshot: NowPlayingWidgetSnapshot? = null
}

internal class NowPlayingWidgetSnapshotStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "now_playing_widget_snapshot",
        Context.MODE_PRIVATE
    )

    fun readCold(): NowPlayingWidgetSnapshot {
        if (!preferences.contains(KEY_IDENTITY)) return NowPlayingWidgetSnapshot.EMPTY
        return NowPlayingWidgetSnapshot(
            mediaIdentity = preferences.getString(KEY_IDENTITY, "").orEmpty(),
            title = preferences.getString(KEY_TITLE, "").orEmpty(),
            artist = preferences.getString(KEY_ARTIST, "").orEmpty(),
            artworkUri = preferences.getString(KEY_ARTWORK_URI, null),
            artworkCacheIdentity = preferences.getString(KEY_ARTWORK_CACHE_IDENTITY, null),
            artworkPath = preferences.getString(KEY_ARTWORK_PATH, null),
            // A process-death snapshot is presentation fallback only. Never claim active playback.
            isPlaying = false,
            canPrevious = preferences.getBoolean(KEY_CAN_PREVIOUS, false),
            canPlayPause = preferences.getBoolean(KEY_CAN_PLAY_PAUSE, false),
            canNext = preferences.getBoolean(KEY_CAN_NEXT, false)
        )
    }

    fun write(snapshot: NowPlayingWidgetSnapshot) {
        preferences.edit()
            .putString(KEY_IDENTITY, snapshot.mediaIdentity)
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_ARTIST, snapshot.artist)
            .putString(KEY_ARTWORK_URI, snapshot.artworkUri)
            .putString(KEY_ARTWORK_CACHE_IDENTITY, snapshot.artworkCacheIdentity)
            .putString(KEY_ARTWORK_PATH, snapshot.artworkPath)
            .putBoolean(KEY_CAN_PREVIOUS, snapshot.canPrevious)
            .putBoolean(KEY_CAN_PLAY_PAUSE, snapshot.canPlayPause)
            .putBoolean(KEY_CAN_NEXT, snapshot.canNext)
            .apply()
    }

    private companion object {
        const val KEY_IDENTITY = "media_identity"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ARTWORK_URI = "artwork_uri"
        const val KEY_ARTWORK_CACHE_IDENTITY = "artwork_cache_identity"
        const val KEY_ARTWORK_PATH = "artwork_path"
        const val KEY_CAN_PREVIOUS = "can_previous"
        const val KEY_CAN_PLAY_PAUSE = "can_play_pause"
        const val KEY_CAN_NEXT = "can_next"
    }
}
