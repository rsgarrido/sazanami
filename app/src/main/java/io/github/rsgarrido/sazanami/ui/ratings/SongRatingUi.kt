package io.github.rsgarrido.sazanami.ui.ratings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.controller.SongRatingDialogState
import io.github.rsgarrido.sazanami.controller.SongRatingUiError
import io.github.rsgarrido.sazanami.controller.SongRatingUiState
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.library.SongRatingFilter

@Immutable
data class SongRatingUiEnvironment(
    val state: SongRatingUiState = SongRatingUiState(),
    val filter: SongRatingFilter = SongRatingFilter.ALL,
    val onOpen: (Song) -> Unit = {},
    val onClose: () -> Unit = {},
    val onSelectRating: (Int) -> Unit = {},
    val onSave: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onFilterSelected: (SongRatingFilter) -> Unit = {},
    val quickRateMode: Boolean = false,
    val onQuickRateModeChanged: (Boolean) -> Unit = {},
    val onSetDirectRating: (Song, Int?) -> Unit = { _, _ -> }
)

val LocalSongRatingUi = compositionLocalOf { SongRatingUiEnvironment() }

@Composable
fun StarRatingControl(
    selectedRating: Int?,
    onRatingSelected: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val groupState = selectedRating?.let {
        stringResource(R.string.rating_selected_description, it)
    } ?: stringResource(R.string.rating_unrated)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("star_rating_control")
            .semantics { stateDescription = groupState },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..5).forEach { value ->
            val isSelected = selectedRating != null && value <= selectedRating
            val targetDescription = pluralStringResource(
                R.plurals.rating_star_target,
                value,
                value
            )
            IconButton(
                onClick = { onRatingSelected(value) },
                enabled = enabled,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("rating_star_$value")
                    .semantics {
                        contentDescription = targetDescription
                        role = Role.RadioButton
                        selected = selectedRating == value
                    }
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
fun CompactRatingIndicator(
    rating: Int,
    modifier: Modifier = Modifier,
    iconFirst: Boolean = false
) {
    val description = stringResource(R.string.rating_accessibility, rating)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon: @Composable () -> Unit = {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        if (iconFirst) icon()
        Text(
            text = rating.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!iconFirst) icon()
    }
}

@Composable
fun QuickRatingControl(
    rating: Int?,
    onRatingSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        (1..5).forEach { value ->
            IconButton(
                onClick = { onRatingSelected(value) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = if (rating != null && value <= rating) {
                        Icons.Filled.Star
                    } else {
                        Icons.Outlined.StarOutline
                    },
                    contentDescription = "Rate $value stars",
                    modifier = Modifier.size(19.dp),
                    tint = if (rating != null && value <= rating) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        IconButton(
            onClick = { onRatingSelected(null) },
            enabled = rating != null,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(Icons.Filled.Clear, contentDescription = "Clear rating", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun SongRatingDialog(
    state: SongRatingDialogState,
    onDismiss: () -> Unit,
    onRatingSelected: (Int) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            BoxWithConstraints {
                val largeText = LocalConfiguration.current.fontScale >= 1.3f
                val contentPadding = if (maxWidth < 336.dp || largeText) 12.dp else 24.dp
                val stackActions = maxWidth < 360.dp || largeText
                Column(
                    modifier = Modifier
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.rate_song),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = state.song.title.ifBlank {
                                stringResource(R.string.rating_unknown_title)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        state.song.artist.takeIf { it.isNotBlank() }?.let { artist ->
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (state.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("rating_loading")
                            )
                        }
                    } else {
                        StarRatingControl(
                            selectedRating = state.selectedValue,
                            onRatingSelected = onRatingSelected,
                            enabled = !state.isSaving
                        )
                        Text(
                            text = state.selectedValue?.let { value ->
                                stringResource(R.string.rating_selected_description, value)
                            } ?: stringResource(R.string.rating_unrated),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.error?.let { error ->
                        Text(
                            text = stringResource(error.messageResource()),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (stackActions) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End
                        ) {
                            if (state.persistedRating != null) {
                                RatingDialogButton(
                                    text = stringResource(R.string.rating_clear),
                                    enabled = !state.isLoading && !state.isSaving,
                                    onClick = onClear
                                )
                            }
                            RatingDialogButton(
                                text = stringResource(R.string.rating_cancel),
                                enabled = !state.isSaving,
                                onClick = onDismiss
                            )
                            Button(
                                onClick = onSave,
                                enabled = !state.isLoading && !state.isSaving &&
                                    state.selectedValue in 1..5,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.rating_save))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.persistedRating != null) {
                                RatingDialogButton(
                                    text = stringResource(R.string.rating_clear),
                                    enabled = !state.isLoading && !state.isSaving,
                                    onClick = onClear
                                )
                            }
                            RatingDialogButton(
                                text = stringResource(R.string.rating_cancel),
                                enabled = !state.isSaving,
                                onClick = onDismiss
                            )
                            Button(
                                onClick = onSave,
                                enabled = !state.isLoading && !state.isSaving &&
                                    state.selectedValue in 1..5,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.rating_save))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingDialogButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Text(text)
    }
}

private fun SongRatingUiError.messageResource(): Int = when (this) {
    SongRatingUiError.LOAD -> R.string.rating_load_error
    SongRatingUiError.SAVE -> R.string.rating_save_error
    SongRatingUiError.CLEAR -> R.string.rating_clear_error
}
