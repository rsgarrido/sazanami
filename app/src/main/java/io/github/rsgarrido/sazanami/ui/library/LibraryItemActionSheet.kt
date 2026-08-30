package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import io.github.rsgarrido.sazanami.ui.AppShellAccent
import io.github.rsgarrido.sazanami.ui.AppShellTypography
import kotlinx.coroutines.launch

@Immutable
data class LibraryItemAction(
    val label: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

@Immutable
data class LibraryItemActionSheetTarget(
    val title: String,
    val subtitle: String?,
    val artworkUri: Any?,
    val artworkDescription: String,
    val actions: List<LibraryItemAction>,
    val artworkContent: (@Composable () -> Unit)? = null
)

@Composable
fun Modifier.libraryItemActions(
    clickLabel: String,
    onClick: () -> Unit,
    onShowActions: () -> Unit
): Modifier {
    val hapticFeedback = LocalHapticFeedback.current
    val showActions = {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        onShowActions()
    }

    return combinedClickable(
        role = Role.Button,
        onClickLabel = clickLabel,
        onLongClickLabel = "Show actions",
        onLongClick = showActions,
        onClick = onClick
    ).semantics {
        customActions = listOf(
            CustomAccessibilityAction(
                label = "Show actions",
                action = {
                    onShowActions()
                    true
                }
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryItemActionSheet(
    target: LibraryItemActionSheetTarget,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    fun closeThen(action: (() -> Unit)? = null) {
        coroutineScope.launch {
            sheetState.hide()
            onDismissRequest()
            action?.invoke()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 10.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppShellAccent.copy(alpha = 0.72f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    val artworkContent = target.artworkContent
                    if (artworkContent != null) {
                        artworkContent()
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppShellIcons.AlbumStack,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AsyncImage(
                                model = target.artworkUri,
                                contentDescription = target.artworkDescription,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = target.title,
                        style = AppShellTypography.FeaturedSongTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!target.subtitle.isNullOrBlank()) {
                        Text(
                            text = target.subtitle,
                            style = AppShellTypography.SongSubtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            target.actions.forEach { action ->
                LibraryActionRow(
                    action = action,
                    onClick = {
                        closeThen(action.onClick)
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryActionRow(
    action: LibraryItemAction,
    onClick: () -> Unit
) {
    val contentColor = if (action.isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = if (action.isDestructive) {
                    contentColor
                } else {
                    AppShellAccent
                }
            )
            Text(
                text = action.label,
                style = AppShellTypography.SongTitle,
                color = contentColor
            )
        }
    }
}
