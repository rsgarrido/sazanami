package io.github.rsgarrido.sazanami.widget

import android.net.Uri
import androidx.media3.common.Player
import io.github.rsgarrido.sazanami.player.ListeningMediaItemMetadata
import java.net.URI

/** A small, immutable rendering model. It is a projection of the session player, never playback truth. */
data class NowPlayingWidgetSnapshot(
    val mediaIdentity: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
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
        isPlaying = isPlaying,
        // Previous remains actionable on the first item: the session owns its restart threshold.
        canPrevious = previousCommandAvailable,
        canPlayPause = playPauseCommandAvailable,
        canNext = nextCommandAvailable && hasNextItem
    )
}

internal fun shouldWidgetShowPause(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int
): Boolean = isPlaying || (playWhenReady && playbackState != Player.STATE_ENDED)

internal fun resolveWidgetArtworkUri(
    mediaItemArtworkUri: String?,
    sessionMetadataArtworkUri: String?
): String? = sessionMetadataArtworkUri?.takeIf(String::isNotBlank)
    ?: mediaItemArtworkUri?.takeIf(String::isNotBlank)

internal enum class WidgetArtworkPresentation { ARTWORK, PLACEHOLDER }

internal fun widgetArtworkPresentation(artworkUriAvailable: Boolean): WidgetArtworkPresentation =
    if (artworkUriAvailable) WidgetArtworkPresentation.ARTWORK
    else WidgetArtworkPresentation.PLACEHOLDER

/** Only app-owned, externally readable artwork providers are handed to the AppWidget host. */
internal fun widgetHostArtworkUri(packageName: String, rawUri: String?): Uri? {
    val eligibleUri = rawUri?.takeIf { isWidgetHostArtworkUri(packageName, it) } ?: return null
    return Uri.parse(eligibleUri)
}

/** Pure eligibility check so local JVM tests do not depend on Android's stubbed Uri implementation. */
internal fun isWidgetHostArtworkUri(packageName: String, rawUri: String?): Boolean {
    if (rawUri.isNullOrBlank()) return false
    val uri = runCatching { URI(rawUri) }.getOrNull() ?: return false
    if (uri.scheme != "content") return false
    return uri.authority == "$packageName.embeddedartwork" ||
        uri.authority == "$packageName.visualassets"
}

internal fun Player.toNowPlayingWidgetSnapshot(): NowPlayingWidgetSnapshot {
    val item = currentMediaItem ?: return NowPlayingWidgetSnapshot.EMPTY
    val metadata = item.mediaMetadata
    return WidgetPlayerState(
        mediaId = item.mediaId,
        itemInstanceId = metadata.extras?.getString(ListeningMediaItemMetadata.ITEM_INSTANCE_ID),
        title = metadata.title?.toString(),
        artist = metadata.artist?.toString(),
        artworkUri = resolveWidgetArtworkUri(
            mediaItemArtworkUri = metadata.artworkUri?.toString(),
            // SmoothPlaybackPlayer publishes the service-normalized, asynchronously enriched
            // artwork here without mutating the timeline MediaItem. Prefer that authoritative
            // session value; the item value is only the pre-enrichment fallback.
            sessionMetadataArtworkUri = mediaMetadata.artworkUri?.toString()
        ),
        isPlaying = shouldWidgetShowPause(
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            playbackState = playbackState
        ),
        previousCommandAvailable = isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
        nextCommandAvailable = isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
        playPauseCommandAvailable = isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
        hasNextItem = hasNextMediaItem()
    ).toSnapshot()
}
