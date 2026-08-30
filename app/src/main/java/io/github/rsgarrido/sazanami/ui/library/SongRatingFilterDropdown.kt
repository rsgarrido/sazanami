package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.AppShellAccent
import io.github.rsgarrido.sazanami.ui.AppShellIconButton
import io.github.rsgarrido.sazanami.ui.ratings.LocalSongRatingUi

@Composable
fun SongRatingFilterDropdown(modifier: Modifier = Modifier) {
    val ratingUi = LocalSongRatingUi.current
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = ratingUi.filter.label()
    Box(modifier) {
        AppShellIconButton(
            onClick = { expanded = true },
            imageVector = if (ratingUi.filter == SongRatingFilter.ALL) {
                Icons.Outlined.StarOutline
            } else {
                Icons.Filled.Star
            },
            contentDescription = stringResource(R.string.rating_filter, selectedLabel),
            accented = ratingUi.filter != SongRatingFilter.ALL
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SongRatingFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.label()) },
                    leadingIcon = {
                        if (ratingUi.filter == filter) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.rating_selected),
                                tint = AppShellAccent
                            )
                        }
                    },
                    onClick = {
                        ratingUi.onFilterSelected(filter)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SongRatingFilter.label(): String = stringResource(
    when (this) {
        SongRatingFilter.ALL -> R.string.rating_filter_all
        SongRatingFilter.RATED -> R.string.rating_filter_rated
        SongRatingFilter.UNRATED -> R.string.rating_filter_unrated
    }
)
