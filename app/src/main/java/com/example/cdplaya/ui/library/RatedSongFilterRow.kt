package com.example.cdplaya.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.cdplaya.ui.AppShellAccent
import com.example.cdplaya.ui.AppShellTypography

@Composable
fun RatedSongFilterRow(
    selectedFilter: RatedSongFilter,
    quickRateActive: Boolean,
    onFilterSelected: (RatedSongFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(
            visibleRatedSongFilters(quickRateActive),
            key = RatedSongFilter::name
        ) { filter ->
            val selected = filter == selectedFilter
            Surface(
                modifier = Modifier
                    .height(32.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onFilterSelected(filter) }
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = filter.label
                    },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) {
                    AppShellAccent.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                contentColor = if (selected) {
                    AppShellAccent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) {
                        AppShellAccent.copy(alpha = 0.46f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                    }
                )
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    filter.exactRating?.let {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = filter.exactRating?.toString() ?: filter.label,
                        style = AppShellTypography.ControlLabel,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
