package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.ui.unit.sp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.player.playerEndpointInput
import io.github.rsgarrido.sazanami.ui.state.playbackPlayPauseShowsPause
import io.github.rsgarrido.sazanami.ui.state.PlaybackProgress
import io.github.rsgarrido.sazanami.ui.state.LocalDirectSeekGestureEnvironment
import io.github.rsgarrido.sazanami.ui.player.mini.normalizedMiniPlayerProgress
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

internal fun interpolateHorizonRect(start: Rect, end: Rect, progress: Float): Rect {
    val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    fun lerp(a: Float, b: Float) = a + (b - a) * p
    return Rect(lerp(start.left, end.left), lerp(start.top, end.top), lerp(start.right, end.right), lerp(start.bottom, end.bottom))
}

internal fun PocketHorizonEndpointPair.interpolate(progress: Float) = PocketHorizonEndpointGeometry(
    interpolateHorizonRect(mini.surface, expanded.surface, progress),
    interpolateHorizonRect(mini.display, expanded.display, progress),
    interpolateHorizonRect(mini.artwork, expanded.artwork, progress),
    interpolateHorizonRect(mini.metadata, expanded.metadata, progress),
    interpolateHorizonRect(mini.playPause, expanded.playPause, progress),
    // Informational only. The renderer deliberately does not stretch a progress track into a slider.
    interpolateHorizonRect(mini.progressRegion, expanded.progressRegion, progress),
    interpolateHorizonRect(mini.gestureRegion, expanded.gestureRegion, progress))

/** Stable endpoints remain untransformed. Only these noninteractive shared visuals move. */
@Composable
internal fun PocketHorizonPlayerMorph(runtime: PocketHorizonRuntime) {
    val pair = runtime.pair ?: return
    val p = runtime.player.progress
    if (p <= 0f || p >= 1f) return
    val geometry = pair.interpolate(p)
    val style = runtime.style
    val density = LocalDensity.current
    val wing = (geometry.display.left - geometry.surface.left).coerceAtLeast(0f)
    // Only the upper housing follows the bumper contour. The existing outer morph clip continues
    // to own the other corners; neither endpoint reports these moving drawing coordinates.
    val housingShape = PocketHorizonUpperHousingShape(with(density) { wing.toDp() },
        shoulderScale = p, bottomCorner = 0.dp)
    fun Modifier.at(rect: Rect) = offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
        .requiredSize(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
    Box(Modifier.fillMaxSize().testTag("pocketHorizonMorph").drawWithContent {
        val path = Path().apply {
            addOutline(RoundedCornerShape((18f + 14f * p).dp).createOutline(geometry.surface.size, layoutDirection, this@drawWithContent))
            translate(geometry.surface.topLeft)
        }
        clipPath(path) { this@drawWithContent.drawContent() }
    }.clearAndSetSemantics {}) {
        // As at the real endpoint, draw caps first and let the continuous front body occlude them.
        Canvas(Modifier.at(geometry.surface)) {
            val capWidth = (wing - 40.dp.toPx() * p).coerceAtLeast(0f)
            repeat(2) { index ->
                val capLeft = if (index == 0) 28.dp.toPx() * p else size.width - 28.dp.toPx() * p - capWidth
                val cap = pocketHorizonBumperCapPath(
                    androidx.compose.ui.geometry.Size(capWidth, PocketHorizonBumperMetrics.CapHeight.toPx() * p), index == 0)
                cap.translate(androidx.compose.ui.geometry.Offset(capLeft, PocketHorizonBumperMetrics.RestTop.toPx() * p))
                drawPath(cap, style.shellHighlight.copy(alpha = p))
            }
        }
        Box(Modifier.at(geometry.surface).clip(housingShape)
            .background(style.shell))
        PocketHorizonBackdrop(style, Modifier.at(geometry.display).clip(RoundedCornerShape((18f + 6f * p).dp)))
        // Noninteractive hardware silhouettes emerge in the moving wings. The real endpoint owns
        // controls only after settlement; these drawings add neither callbacks nor measurement reports.
        Canvas(Modifier.at(geometry.surface)) {
            val radius = 24.dp.toPx() * p
            val centerY = size.height * .32f
            val left = wing * .5f
            val right = size.width - wing * .5f
            val spacing = 48.dp.toPx() * p
            val ink = style.shellHighlight.copy(alpha = p)
            listOf(androidx.compose.ui.geometry.Offset(left, centerY - spacing),
                androidx.compose.ui.geometry.Offset(left, centerY + spacing),
                androidx.compose.ui.geometry.Offset(left - spacing, centerY),
                androidx.compose.ui.geometry.Offset(left + spacing, centerY),
                androidx.compose.ui.geometry.Offset(right, centerY - spacing),
                androidx.compose.ui.geometry.Offset(right, centerY + spacing),
                androidx.compose.ui.geometry.Offset(right - spacing, centerY),
                androidx.compose.ui.geometry.Offset(right + spacing, centerY)).forEach { drawCircle(ink, radius, it) }
            drawCircle(ink, 36.dp.toPx() * p, androidx.compose.ui.geometry.Offset(left, size.height * .67f))
            // The lower strip and shoulders follow the measured LCD/surface separation, so the
            // transition cannot retain the former full-height display or wing-mounted system keys.
            val stripTop = geometry.display.bottom - geometry.surface.top
            val stripHeight = (size.height - stripTop).coerceAtLeast(0f)
            val keyWidth = 80.dp.toPx() * p
            val keyHeight = 42.dp.toPx() * p
            val displayRight = geometry.display.right - geometry.surface.left
            repeat(2) { index ->
                drawRoundRect(ink,
                    androidx.compose.ui.geometry.Offset(displayRight - (index + 1) * (keyWidth + 8.dp.toPx() * p),
                        stripTop + (stripHeight - keyHeight) / 2f),
                    androidx.compose.ui.geometry.Size(keyWidth, keyHeight),
                    androidx.compose.ui.geometry.CornerRadius(12.dp.toPx() * p))
            }
        }
        PocketHorizonArtwork(runtime.playback.currentSong, with(density) { geometry.artwork.width.toDp() },
            false, null, style, Modifier.at(geometry.artwork), tag = "pocketHorizonMorphArtwork")
        Box(Modifier.at(geometry.metadata).clipToBounds()) {
            // One shared text tree owns moving metadata; alpha-crossfading unlike text layouts
            // paints two baselines and creates doubled letters even when their alphas sum to one.
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(runtime.playback.currentSong?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_unknown_title),
                    Modifier.testTag("pocketHorizonMorphTitle"), color = style.text,
                    fontSize = (14f + 12f * p).sp, lineHeight = (20f + 12f * p).sp,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                androidx.compose.material3.Text(runtime.playback.currentSong?.artist?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_unknown_artist),
                    Modifier.testTag("pocketHorizonMorphArtist"), color = style.secondaryText,
                    fontSize = (12f + 4f * p).sp, lineHeight = (16f + 8f * p).sp,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        val pause = playbackPlayPauseShowsPause(runtime.playback.playPauseIntent ?: runtime.playback.isPlaying)
        Box(Modifier.at(geometry.playPause).background(style.accent, CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(if (pause) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = style.onAccent,
                modifier = Modifier.size((24f + 8f * p).dp))
        }
        // Fade a compact-height line along the surface edge; never stretch a slider's input region.
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        PlaybackProgress(runtime.progress) { progress ->
            val fraction = normalizedMiniPlayerProgress(progress.currentPosition, progress.duration)
            val lineHeight = with(density) { 3.dp.toPx() }
            val line = Rect(geometry.surface.left, geometry.surface.bottom - lineHeight, geometry.surface.right, geometry.surface.bottom)
            Canvas(Modifier.at(line).graphicsLayer { alpha = 1f - p }) {
                drawRect(style.outline.copy(alpha = .35f))
                drawRect(style.accent, androidx.compose.ui.geometry.Offset(if (rtl) size.width * (1f - fraction) else 0f, 0f),
                    androidx.compose.ui.geometry.Size(size.width * fraction, size.height))
            }
        }
        // A visible moving header region can catch a reversal during settling; it has no click action.
        Box(Modifier.at(geometry.gestureRegion).then(pocketHorizonMorphGesture(runtime)))
    }
}

/** Called only by ExpandedPlayerThemeHost under the normal MusicScreen overlay and inset policies. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PocketHorizonRuntimeExpanded(
    runtime: PocketHorizonRuntime,
    onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Int) -> Unit,
    onCollapse: () -> Unit, onQueue: () -> Unit, onMore: () -> Unit,
    onLyrics: () -> Unit, onArtwork: ((Song) -> Unit)?, onSong: (Song, List<Song>) -> Unit
) {
    val stable = runtime.expandedInput
    val latest = rememberUpdatedState(runtime)
    val nowPlaying = runtime.navigation.category == PocketHorizonCategory.NowPlaying
    val collapseLabel = stringResource(R.string.player_collapse)
    val endpointModifier = Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))
    // Once ready, root Back remains with MusicScreen (including its playback launch-context restore).
    // A partial measurement cannot start an unmeasured collapse. Detail Back remains in the endpoint.
    BackHandler(runtime.inputAllowed && runtime.player.shouldConsumeBack && (!stable || nowPlaying) && runtime.pair == null) {}
    val authority = LocalDirectSeekGestureEnvironment.current?.authority?.invoke()
    val seekAllowed = authority?.let { !it.enabled || it.allowed } ?: true
    Box(Modifier.fillMaxSize().background(runtime.style.shell.copy(alpha = runtime.player.progress))) {
        // When browsing, this tree measures only Now Playing, never a hidden library list. It shares
        // the navigation owner but measurementOnly forbids its effects/Back handler from mutating it.
        if (!nowPlaying) {
            PocketHorizonExpandedPlayer(runtime.playback, runtime.progress, {}, {}, {}, {},
                modifier = endpointModifier.graphicsLayer { alpha = 0f }.playerEndpointInput(false),
                inputEnabled = false, navigationState = runtime.navigation, measurements = runtime.expanded,
                measurementOnly = true, style = runtime.style, onOpenQueueHubClick = {}, onOpenMoreClick = {}, onOpenLyrics = {})
        }
        PocketHorizonExpandedPlayer(runtime.playback, runtime.progress,
            { if (latest.value.expandedInput && latest.value.playback.isConnected) onPlayPause() },
            { if (latest.value.expandedInput && latest.value.playback.isConnected) onPrevious() },
            { if (latest.value.expandedInput && latest.value.playback.isConnected) onNext() },
            { if (latest.value.expandedInput) onSeek(it) },
            modifier = endpointModifier.graphicsLayer {
                alpha = if (runtime.player.progress == 1f || (runtime.pair == null && runtime.player.progress > 0f)) 1f else 0f
            }
                .playerEndpointInput(stable || runtime.player.isDragging)
                .then(if (stable && runtime.pair != null) Modifier.semantics {
                    customActions = listOf(CustomAccessibilityAction(collapseLabel) {
                        if (latest.value.expandedInput && latest.value.pair != null) { onCollapse(); true } else false
                    })
                } else Modifier),
            inputEnabled = stable, seekEnabled = seekAllowed, style = runtime.style,
            onOpenQueueHubClick = { if (latest.value.expandedInput) onQueue() },
            onOpenMoreClick = { if (latest.value.expandedInput) onMore() },
            onOpenLyrics = { if (latest.value.expandedInput) onLyrics() },
            onViewArtwork = onArtwork?.let { action -> { song -> if (latest.value.expandedInput) action(song) } },
            libraryState = runtime.library, songSortState = runtime.songSort, albumSortState = runtime.albumSort,
            artistSortState = runtime.artistSort,
            onSongClick = { song, context -> if (latest.value.expandedInput) onSong(song, context) }, navigationState = runtime.navigation,
            measurements = runtime.expanded.takeIf { nowPlaying },
            collapseGestureModifier = pocketHorizonMorphGesture(runtime),
            gestureEnabled = runtime.inputAllowed && (stable || runtime.player.isDragging))
        PocketHorizonPlayerMorph(runtime)
    }
}
