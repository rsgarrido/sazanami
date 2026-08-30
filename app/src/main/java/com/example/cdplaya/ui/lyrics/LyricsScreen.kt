package com.example.cdplaya.ui.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import com.example.cdplaya.data.Song
import com.example.cdplaya.lyrics.LyricCueContent
import com.example.cdplaya.lyrics.LyricAnchorGeometry
import com.example.cdplaya.lyrics.LyricsAutoFollowController
import com.example.cdplaya.lyrics.LyricsCandidate
import com.example.cdplaya.lyrics.LyricsDocument
import com.example.cdplaya.lyrics.LyricsPlaybackUiState
import com.example.cdplaya.lyrics.LyricsScrollRequest
import com.example.cdplaya.lyrics.LyricsUnavailableReason
import com.example.cdplaya.lyrics.StaticLyricLine
import com.example.cdplaya.lyrics.generateLyricsNameCandidates
import com.example.cdplaya.lyrics.calculateLyricAnchorScrollDelta
import com.example.cdplaya.lyrics.toLyricsIdentity
import com.example.cdplaya.ui.player.PlayerLyricsTransitionState
import kotlinx.coroutines.launch

const val LyricsScreenTag = "lyrics_screen"
const val LyricsListTag = "lyrics_list"
const val LyricsReturnTag = "lyrics_return_to_current"
const val LyricsBackTag = "lyrics_back"
const val LyricsPlayPauseTag = "lyrics_play_pause"
const val LyricsHeaderTag = "lyrics_header"

@Composable
fun LyricsScreen(
    state: LyricsPlaybackUiState,
    isPlaying: Boolean,
    transitionState: PlayerLyricsTransitionState,
    interactive: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onSuspendAutoFollow: () -> Unit,
    onReturnToCurrentLine: () -> Unit,
    onRescan: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = state.songOrNull() ?: return
    var surfaceHeightPx by remember { mutableIntStateOf(1) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { surfaceHeightPx = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // A full-size pointer node keeps lower siblings out of the hit path.
                        // Observe at Final so child controls retain ownership of their events.
                        awaitPointerEvent(PointerEventPass.Final)
                    }
                }
            }
            .background(MaterialTheme.colorScheme.surface)
            .testTag(LyricsScreenTag)
    ) {
        LyricsArtworkBackground(song)

        Column(modifier = Modifier.fillMaxSize()) {
            LyricsHeader(
                song = song,
                isPlaying = isPlaying,
                transitionState = transitionState,
                surfaceHeightPx = surfaceHeightPx,
                interactive = interactive,
                onBack = onBack,
                onPlayPause = onPlayPause,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (state) {
                    is LyricsPlaybackUiState.Loading -> LyricsLoading()
                    is LyricsPlaybackUiState.Synced -> SyncedLyricsList(
                        state = state,
                        onSeek = onSeek,
                        onSuspendAutoFollow = onSuspendAutoFollow,
                        onReturnToCurrentLine = onReturnToCurrentLine,
                        interactive = interactive
                    )
                    is LyricsPlaybackUiState.Unsynced -> UnsyncedLyricsList(state.lyrics)
                    is LyricsPlaybackUiState.Unavailable -> LyricsUnavailable(
                        song = state.song,
                        reason = state.reason,
                        onRescan = onRescan,
                        onOpenSettings = onOpenSettings
                    )
                    LyricsPlaybackUiState.Hidden -> Unit
                }
            }
        }
    }
}

@Composable
private fun LyricsArtworkBackground(song: Song) {
    AsyncImage(
        model = song.albumArtUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .blur(44.dp)
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.78f),
                        Color.Black.copy(alpha = 0.64f),
                        Color.Black.copy(alpha = 0.88f)
                    )
                )
            )
    )
}

@Composable
private fun LyricsHeader(
    song: Song,
    isPlaying: Boolean,
    transitionState: PlayerLyricsTransitionState,
    surfaceHeightPx: Int,
    interactive: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .testTag(LyricsHeaderTag)
            .then(
                if (!interactive) {
                    Modifier
                } else {
                    Modifier.pointerInput(transitionState, surfaceHeightPx) {
                        detectVerticalDragGestures(
                            onDragStart = { transitionState.beginClosingDrag() },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                transitionState.dragClosingBy(
                                    dragAmount,
                                    surfaceHeightPx.toFloat()
                                )
                            },
                            onDragEnd = { transitionState.settleClosing(0f) },
                            onDragCancel = { transitionState.settleClosing(0f) }
                        )
                    }
                }
            )
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.58f), Color.Transparent)
                )
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            enabled = interactive,
            modifier = Modifier.testTag(LyricsBackTag)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close lyrics",
                tint = Color.White
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.size(46.dp)
        ) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = song.title.ifBlank { song.displayName },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist.ifBlank { "Unknown artist" },
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onPlayPause,
            enabled = interactive,
            modifier = Modifier
                .testTag(LyricsPlayPauseTag)
                .semantics {
                    stateDescription = if (isPlaying) "Playing" else "Paused"
                }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.BoxScopeSyncedReturnAction(
    visible: Boolean,
    onClick: () -> Unit
) {
    if (!visible) return
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
        shadowElevation = 6.dp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 22.dp)
            .testTag(LyricsReturnTag)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Return to current line", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SyncedLyricsList(
    state: LyricsPlaybackUiState.Synced,
    onSeek: (Int) -> Unit,
    onSuspendAutoFollow: () -> Unit,
    onReturnToCurrentLine: () -> Unit,
    interactive: Boolean
) {
    val groups = remember(state.lyrics) { state.lyrics.toDisplayGroups() }
    var optimisticTimestamp by remember(state.song.id, state.lyrics) {
        mutableStateOf<Long?>(null)
    }
    val displayedActiveTimestamp = optimisticTimestamp ?: state.activeGroup?.timestampMs
    val activeIndex = groups.indexOfFirst {
        it.timestampMs == displayedActiveTimestamp
    }.takeIf { it >= 0 }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val followController = remember { LyricsAutoFollowController() }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val anchorTolerancePx = with(density) { 6.dp.toPx() }
    val topPadding = with(density) {
        (viewportHeight * 0.42f).toDp().coerceAtLeast(34.dp)
    }
    val bottomPadding = with(density) {
        (viewportHeight * 0.55f).toDp().coerceAtLeast(180.dp)
    }
    val currentSuspendCallback by rememberUpdatedState(onSuspendAutoFollow)
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    followController.onUserScroll()
                    currentSuspendCallback()
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(state.song.id) {
        followController.onTrackChanged()
    }
    LaunchedEffect(state.activeGroup?.timestampMs) {
        if (state.activeGroup?.timestampMs != null) optimisticTimestamp = null
    }
    val anchorRevision = remember(viewportHeight, groups) {
        viewportHeight to groups.map { it.timestampMs }
    }
    LaunchedEffect(activeIndex, state.autoFollowEnabled, anchorRevision) {
        if (!state.autoFollowEnabled) {
            followController.onUserScroll()
            return@LaunchedEffect
        }
        followController.onActiveItemChanged(activeIndex, anchorRevision)?.let { request ->
            listState.anchorItem(request, anchorTolerancePx)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .onSizeChanged { viewportHeight = it.height }
                .testTag(LyricsListTag)
            ,
            userScrollEnabled = interactive
        ) {
            itemsIndexed(
                items = groups,
                key = { _, group -> "${group.timestampMs}:${group.firstCueIndex}" }
            ) { index, group ->
                val isActive = index == activeIndex
                val isPast = displayedActiveTimestamp != null &&
                        group.timestampMs < displayedActiveTimestamp
                SyncedLyricRow(
                    group = group,
                    isActive = isActive,
                    isPast = isPast,
                    enabled = interactive,
                    onClick = {
                        optimisticTimestamp = group.timestampMs
                        onSeek(group.timestampMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        onReturnToCurrentLine()
                        followController.returnToCurrent(index)?.let { request ->
                            scope.launch {
                                listState.anchorItem(request, anchorTolerancePx)
                            }
                        }
                    }
                )
            }
        }

        BoxScopeSyncedReturnAction(
            visible = !state.autoFollowEnabled,
            onClick = {
                onReturnToCurrentLine()
                followController.returnToCurrent(activeIndex)?.let { request ->
                    scope.launch {
                        listState.anchorItem(request, anchorTolerancePx)
                    }
                }
            }
        )
    }
}

@Composable
private fun SyncedLyricRow(
    group: LyricsDisplayGroup,
    isActive: Boolean,
    isPast: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        targetValue = when {
            isActive -> Color.White
            isPast -> Color.White.copy(alpha = 0.58f)
            else -> Color.White.copy(alpha = 0.38f)
        },
        label = "lyric emphasis"
    )
    Text(
        text = group.lines.joinToString("\n"),
        color = color,
        fontSize = if (isActive) 24.sp else 20.sp,
        lineHeight = if (isActive) 32.sp else 29.sp,
        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                selected = isActive
                if (isActive) stateDescription = "Current lyric"
                contentDescription = "${group.lines.joinToString(" ")}. Seek to lyric."
                onClick(label = "Seek to this lyric") {
                    onClick()
                    true
                }
            }
            .padding(horizontal = 28.dp, vertical = 4.dp)
    )
}

@Composable
private fun UnsyncedLyricsList(document: LyricsDocument.Unsynced) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Unsynced lyrics",
            color = Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag(LyricsListTag)
        ) {
            itemsIndexed(
                items = document.lines,
                key = { index, _ -> index }
            ) { _, line: StaticLyricLine ->
                Text(
                    text = line.text.ifEmpty { " " },
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 20.sp,
                    lineHeight = 29.sp
                )
            }
        }
    }
}

@Composable
private fun LyricsLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("lyrics_loading"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(14.dp))
            Text("Loading local lyrics…", color = Color.White.copy(alpha = 0.76f))
        }
    }
}

@Composable
private fun LyricsUnavailable(
    song: Song,
    reason: LyricsUnavailableReason,
    onRescan: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val content = unavailableContent(song, reason)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp)
            .testTag("lyrics_unavailable"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = content.title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = content.detail,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge
            )
            content.candidates.forEach { candidate ->
                Text(
                    text = candidate.displayName +
                            candidate.relativeDirectory.takeIf(String::isNotBlank)
                                ?.let { " — $it" }.orEmpty(),
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (content.canRescan) {
                    Button(onClick = onRescan) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Rescan")
                    }
                }
                Button(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Local Lyrics settings")
                }
            }
        }
    }
}

private data class LyricsDisplayGroup(
    val timestampMs: Long,
    val firstCueIndex: Int,
    val lines: List<String>
)

private fun LyricsDocument.Synced.toDisplayGroups(): List<LyricsDisplayGroup> =
    cues.withIndex()
        .groupBy { it.value.timestampMs }
        .mapNotNull { (timestamp, indexedCues) ->
            val lines = indexedCues.mapNotNull { indexed ->
                (indexed.value.content as? LyricCueContent.Text)?.text
            }
            lines.takeIf(List<String>::isNotEmpty)?.let {
                LyricsDisplayGroup(timestamp, indexedCues.first().index, it)
            }
        }

private suspend fun androidx.compose.foundation.lazy.LazyListState.anchorItem(
    request: LyricsScrollRequest,
    tolerancePx: Float
) {
    if (request.itemIndex < 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == request.itemIndex }) {
        scrollToItem(request.itemIndex)
        withFrameNanos { }
    }

    repeat(MAX_ANCHOR_CORRECTION_PASSES) { pass ->
        val currentLayout = layoutInfo
        val item = currentLayout.visibleItemsInfo
            .firstOrNull { it.index == request.itemIndex }
            ?: return
        val correction = calculateLyricAnchorScrollDelta(
            geometry = LyricAnchorGeometry(
                viewportStartPx = currentLayout.viewportStartOffset,
                viewportEndPx = currentLayout.viewportEndOffset,
                itemOffsetPx = item.offset,
                itemSizePx = item.size
            ),
            tolerancePx = tolerancePx
        )
        if (correction == 0f) return

        if (request.animate && pass == 0) {
            animateScrollBy(correction)
        } else {
            scrollBy(correction)
        }
        withFrameNanos { }
    }
}

private const val MAX_ANCHOR_CORRECTION_PASSES = 3

private data class UnavailableContent(
    val title: String,
    val detail: String,
    val canRescan: Boolean,
    val candidates: List<LyricsCandidate> = emptyList()
)

private fun unavailableContent(
    song: Song,
    reason: LyricsUnavailableReason
): UnavailableContent = when (reason) {
    LyricsUnavailableReason.NoRootsConfigured -> UnavailableContent(
        title = "No lyrics folder configured",
        detail = "Select one or more folders containing local .lrc files.",
        canRescan = false
    )
    LyricsUnavailableReason.NotFound -> UnavailableContent(
        title = "No local lyrics found",
        detail = buildString {
            append("Tried names including:\n")
            append(
                generateLyricsNameCandidates(song.toLyricsIdentity())
                    .take(4)
                    .joinToString("\n") { "${it.displayStem}.lrc" }
            )
        },
        canRescan = true
    )
    is LyricsUnavailableReason.Ambiguous -> UnavailableContent(
        title = "Multiple matching lyrics files",
        detail = "Sazanami could not safely choose between these files.",
        canRescan = true,
        candidates = reason.candidates.take(5)
    )
    is LyricsUnavailableReason.PermissionLost -> UnavailableContent(
        title = "Lyrics folder access unavailable",
        detail = "Android no longer grants access to a selected lyrics folder.",
        canRescan = true
    )
    is LyricsUnavailableReason.RootScanError -> UnavailableContent(
        title = "Lyrics folder could not be scanned",
        detail = "Check the folder provider and try again.",
        canRescan = true
    )
    is LyricsUnavailableReason.StaleFile -> UnavailableContent(
        title = "Lyrics file moved or deleted",
        detail = "The indexed sidecar file is no longer available.",
        canRescan = true
    )
    is LyricsUnavailableReason.ReadError -> UnavailableContent(
        title = "Lyrics file could not be read",
        detail = "The matching local file could not be opened.",
        canRescan = true
    )
    is LyricsUnavailableReason.InvalidLyrics -> UnavailableContent(
        title = "Lyrics file has no usable lyrics",
        detail = reason.documentUri.substringAfterLast('/'),
        canRescan = true
    )
}

private fun LyricsPlaybackUiState.songOrNull(): Song? = when (this) {
    LyricsPlaybackUiState.Hidden -> null
    is LyricsPlaybackUiState.Loading -> song
    is LyricsPlaybackUiState.Synced -> song
    is LyricsPlaybackUiState.Unsynced -> song
    is LyricsPlaybackUiState.Unavailable -> song
}

internal fun shouldCloseLyricsFromHeader(
    dragDistancePx: Float,
    velocityY: Float
): Boolean = dragDistancePx >= 180f || velocityY >= 1_200f
