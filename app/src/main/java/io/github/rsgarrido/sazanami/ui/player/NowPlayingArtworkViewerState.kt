package io.github.rsgarrido.sazanami.ui.player

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.MusicOverlayState
import io.github.rsgarrido.sazanami.ui.ratings.LocalSongRatingUi

internal const val ArtworkViewerEntryTag = "now_playing_view_artwork"

/** Referential identity isolates every opening, including reopening the same URI. */
internal class NowPlayingArtworkViewerRequest(val song: Song, val artworkUri: Uri)

internal fun supportsNowPlayingArtworkViewer(presentation: PlayerPresentationIdentity): Boolean = when (presentation) {
    is PlayerPresentationIdentity.Existing -> supportsNowPlayingArtworkViewer(presentation.theme)
    PlayerPresentationIdentity.PocketAdvance -> true
}

internal fun supportsNowPlayingArtworkViewer(theme: PlayerTheme): Boolean = when (theme) {
    PlayerTheme.DEFAULT, PlayerTheme.RETRO_RACK, PlayerTheme.POCKET_FLIP, PlayerTheme.POCKET_DISC -> true
    PlayerTheme.POCKET_CASSETTE, PlayerTheme.CLASSIC_WHEEL -> false
}

internal fun canOpenArtworkAtExpandedEndpoint(
    player: PlayerMorphState,
    lyrics: PlayerLyricsTransitionState
): Boolean = player.targetPresentation == PlayerPresentation.Expanded &&
        player.settledPresentation == PlayerPresentation.Expanded && player.progress == 1f &&
        !player.isDragging && !player.isAnimating && !lyrics.isDragging && !lyrics.lyricsOwnsInput

@Composable
internal fun Modifier.artworkViewerClick(song: Song?, onViewArtwork: ((Song) -> Unit)?): Modifier =
    if (song?.albumArtUri == null || onViewArtwork == null) this else
        testTag(ArtworkViewerEntryTag).clickable(
            role = Role.Button,
            onClickLabel = stringResource(R.string.player_view_artwork),
            onClick = { onViewArtwork(song) }
        )

@Composable
internal fun rememberNowPlayingArtworkViewerOpener(
    currentSong: Song?,
    overlayState: MusicOverlayState,
    lyrics: PlayerLyricsTransitionState,
    canPresent: Boolean
): (Song) -> Unit {
    val latestSong = rememberUpdatedState(currentSong)
    val latestCanPresent = rememberUpdatedState(canPresent && LocalSongRatingUi.current.state.dialog == null)
    return remember(overlayState, lyrics) {
        { target: Song ->
            if (latestCanPresent.value && isCurrentNowPlayingTarget(target, latestSong.value) &&
                canOpenArtworkAtExpandedEndpoint(overlayState.playerMorphState, lyrics)) {
                overlayState.openArtworkViewer(target)
            }
        }
    }
}

/** Loading, animation and gestures compose outside the large player-content lambda. */
@Composable
internal fun NowPlayingArtworkViewerOverlay(overlayState: MusicOverlayState, canPresent: Boolean) {
    val request = overlayState.artworkViewerRequest
    val visible = overlayState.isArtworkViewerVisible.value
    val allowed = canPresent && LocalSongRatingUi.current.state.dialog == null
    LaunchedEffect(request, visible, allowed) {
        if (!visible || !allowed || request == null) overlayState.dismissArtworkViewer(request)
    }
    if (request != null && visible && allowed) {
        key(request) {
            NowPlayingArtworkViewerDialog(request, onDismiss = { overlayState.dismissArtworkViewer(request) })
        }
    }
}
