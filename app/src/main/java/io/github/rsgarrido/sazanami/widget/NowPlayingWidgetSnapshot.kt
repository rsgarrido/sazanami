package io.github.rsgarrido.sazanami.widget

import androidx.media3.common.Player
import io.github.rsgarrido.sazanami.player.ListeningMediaItemMetadata

/** A small, immutable rendering model. It is a projection of the session player, never playback truth. */
data class NowPlayingWidgetSnapshot(
    val mediaIdentity: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val artworkCacheIdentity: String?,
    val artworkPath: String?,
    val isPlaying: Boolean,
    val canPrevious: Boolean,
    val canPlayPause: Boolean,
    val canNext: Boolean
) {
    val hasMedia: Boolean get() = mediaIdentity.isNotBlank()

    fun asColdSnapshot(): NowPlayingWidgetSnapshot = copy(isPlaying = false)

    companion object {
        val EMPTY = NowPlayingWidgetSnapshot(
            mediaIdentity = "",
            title = "",
            artist = "",
            artworkUri = null,
            artworkCacheIdentity = null,
            artworkPath = null,
            isPlaying = false,
            canPrevious = false,
            canPlayPause = false,
            canNext = false
        )
    }
}

internal data class WidgetPlayerState(
    val mediaId: String,
    val itemInstanceId: String?,
    val title: String?,
    val artist: String?,
    val artworkUri: String?,
    val isPlaying: Boolean,
    val previousCommandAvailable: Boolean,
    val nextCommandAvailable: Boolean,
    val playPauseCommandAvailable: Boolean,
    val hasNextItem: Boolean
)

internal fun WidgetPlayerState.toSnapshot(): NowPlayingWidgetSnapshot {
    if (mediaId.isBlank() && itemInstanceId.isNullOrBlank()) {
        return NowPlayingWidgetSnapshot.EMPTY
    }
    val identity = listOf(mediaId, itemInstanceId.orEmpty()).joinToString("|")
    return NowPlayingWidgetSnapshot(
        mediaIdentity = identity,
        title = title?.trim().takeUnless { it.isNullOrEmpty() } ?: "Unknown title",
        artist = artist?.trim().takeUnless { it.isNullOrEmpty() } ?: "Unknown artist",
        artworkUri = artworkUri,
        artworkCacheIdentity = artworkUri?.let(::widgetArtworkCacheIdentity),
        artworkPath = null,
        isPlaying = isPlaying,
        // Previous remains actionable on the first item: the session owns its restart threshold.
        canPrevious = previousCommandAvailable,
        canPlayPause = playPauseCommandAvailable,
        canNext = nextCommandAvailable && hasNextItem
    )
}

internal fun Player.toNowPlayingWidgetSnapshot(): NowPlayingWidgetSnapshot {
    val item = currentMediaItem ?: return NowPlayingWidgetSnapshot.EMPTY
    val metadata = item.mediaMetadata
    return WidgetPlayerState(
        mediaId = item.mediaId,
        itemInstanceId = metadata.extras?.getString(ListeningMediaItemMetadata.ITEM_INSTANCE_ID),
        title = metadata.title?.toString(),
        artist = metadata.artist?.toString(),
        artworkUri = metadata.artworkUri?.toString(),
        isPlaying = isPlaying,
        previousCommandAvailable = isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
        nextCommandAvailable = isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
        playPauseCommandAvailable = isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
        hasNextItem = hasNextMediaItem()
    ).toSnapshot()
}

internal fun isWidgetArtworkResultCurrent(expectedIdentity: String, currentIdentity: String): Boolean =
    expectedIdentity == currentIdentity
