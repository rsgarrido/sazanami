package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.AppShellAccent
import io.github.rsgarrido.sazanami.ui.AppShellTypography

internal const val NowPlayingMoreDialogTag = "now_playing_more_dialog"
internal const val NowPlayingMoreWorkflowRowsTag = "now_playing_more_workflow_rows"
internal const val NowPlayingMoreQuickActionsTag = "now_playing_more_quick_actions"

@Composable
internal fun NowPlayingMoreDialog(
    target: Song,
    actions: List<NowPlayingActionItem>,
    onDismiss: () -> Unit,
    onAction: (NowPlayingAction) -> Unit,
    accentOverride: Color? = null
) {
    val accent = accentOverride ?: AppShellAccent
    val entrance = remember { Animatable(0f) }
    val paneLabel = stringResource(R.string.player_more_actions)
    val configuration = LocalConfiguration.current
    // Reduce gutters on narrow display configurations so four 48dp touch regions still fit.
    val horizontalPadding = if (configuration.screenWidthDp < 320) 8.dp else 16.dp
    val quickActionSpacing = if (configuration.screenWidthDp < 320) 4.dp else 8.dp
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(140)) }

    // The native dialog window owns dimming, outside/Back dismissal and modal input isolation.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true,
            usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = (configuration.screenHeightDp * 0.8f).dp)
                .graphicsLayer {
                    alpha = entrance.value
                    scaleX = 0.96f + 0.04f * entrance.value
                    scaleY = scaleX
                }
                .semantics { paneTitle = paneLabel }
                .testTag(NowPlayingMoreDialogTag),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = target.title.ifBlank { stringResource(R.string.player_unknown_title) },
                    style = AppShellTypography.FeaturedSongTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() }
                )
                Text(
                    text = stringResource(
                        R.string.player_more_song_subtitle,
                        target.artist.ifBlank { stringResource(R.string.player_unknown_artist) },
                        target.album.ifBlank { stringResource(R.string.player_unknown_album) }
                    ),
                    style = AppShellTypography.SongSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
                )
                Column(
                    modifier = Modifier.fillMaxWidth().testTag(NowPlayingMoreWorkflowRowsTag),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    actions.filterNot { it.action.isQuickAction }.forEach { item ->
                        Surface(
                            onClick = { onAction(item.action) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = nowPlayingActionIcon(item),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = stringResource(item.labelRes),
                                    style = AppShellTypography.SongTitle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().testTag(NowPlayingMoreQuickActionsTag),
                    horizontalArrangement = Arrangement.spacedBy(quickActionSpacing)
                ) {
                    actions.filter { it.action.isQuickAction }.forEach { item ->
                        val label = stringResource(item.labelRes)
                        val timerStateDescription = if (item.action == NowPlayingAction.SLEEP_TIMER) {
                            stringResource(if (item.isActive) R.string.player_sleep_timer_active else R.string.sleep_timer_inactive)
                        } else null
                        Surface(
                            onClick = { onAction(item.action) },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics {
                                contentDescription = label
                                role = Role.Button
                                if (item.action == NowPlayingAction.FAVORITE || item.action == NowPlayingAction.RATE_SONG ||
                                    item.action == NowPlayingAction.SLEEP_TIMER) {
                                    selected = item.isActive
                                }
                                timerStateDescription?.let { stateDescription = it }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (item.isActive) accent.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = nowPlayingActionIcon(item),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun nowPlayingActionIcon(item: NowPlayingActionItem): ImageVector = when (item.action) {
    NowPlayingAction.GO_TO_ARTIST -> Icons.Filled.Person
    NowPlayingAction.GO_TO_ALBUM -> Icons.Filled.Album
    NowPlayingAction.ADD_TO_PLAYLIST -> Icons.AutoMirrored.Filled.PlaylistAdd
    NowPlayingAction.FAVORITE -> if (item.isActive) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
    NowPlayingAction.RATE_SONG -> if (item.isActive) Icons.Filled.Star else Icons.Outlined.StarOutline
    NowPlayingAction.LYRICS -> Icons.Filled.Subject
    NowPlayingAction.SLEEP_TIMER -> Icons.Filled.Timer
    NowPlayingAction.TRACK_INFORMATION -> Icons.Filled.Info
}
