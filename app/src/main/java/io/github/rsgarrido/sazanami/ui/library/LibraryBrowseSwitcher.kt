package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.ui.AppShellTypography
import io.github.rsgarrido.sazanami.ui.AppShellAccent

val primaryLibraryTabs = listOf(
    LibraryTab.SONGS,
    LibraryTab.ALBUMS,
    LibraryTab.ARTISTS,
    LibraryTab.PLAYLISTS,
    LibraryTab.GENRES
)

internal data class LibraryTabOverflowAffordances(
    val showStart: Boolean,
    val showEnd: Boolean
)

internal fun libraryTabOverflowAffordances(
    hasMeasuredContent: Boolean,
    canScrollBackward: Boolean,
    canScrollForward: Boolean
): LibraryTabOverflowAffordances = LibraryTabOverflowAffordances(
    showStart = hasMeasuredContent && canScrollBackward,
    showEnd = hasMeasuredContent && canScrollForward
)

val songCollectionTabs = listOf(
    LibraryTab.SONGS,
    LibraryTab.FAVORITES,
    LibraryTab.RATED,
    LibraryTab.RECENTLY_ADDED,
    LibraryTab.RECENTLY_PLAYED,
    LibraryTab.MOST_PLAYED
)

fun LibraryTab.primaryBrowseTab(): LibraryTab? {
    return when (this) {
        LibraryTab.FAVORITES,
        LibraryTab.RATED,
        LibraryTab.RECENTLY_ADDED,
        LibraryTab.RECENTLY_PLAYED,
        LibraryTab.MOST_PLAYED -> LibraryTab.SONGS

        LibraryTab.QUEUE -> null
        else -> this
    }
}

@Composable
fun LibraryBrowseSwitcher(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedPrimaryTab = selectedTab.primaryBrowseTab() ?: return
    val primaryTabScrollState = rememberScrollState()
    val primaryTabContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val overflowAffordances = libraryTabOverflowAffordances(
        hasMeasuredContent = primaryTabScrollState.maxValue != Int.MAX_VALUE,
        canScrollBackward = primaryTabScrollState.canScrollBackward,
        canScrollForward = primaryTabScrollState.canScrollForward
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = primaryTabContainerColor,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(4.dp)
                        .horizontalScroll(primaryTabScrollState)
                        .selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    primaryLibraryTabs.forEach { tab ->
                        LibraryPrimaryTab(
                            tab = tab,
                            selected = tab == selectedPrimaryTab,
                            onClick = { onTabSelected(tab) },
                            modifier = Modifier.widthIn(min = 84.dp)
                        )
                    }
                }

                if (overflowAffordances.showStart) {
                    LibraryTabOverflowFade(
                        modifier = Modifier.align(Alignment.CenterStart),
                        colors = listOf(
                            primaryTabContainerColor,
                            Color.Transparent
                        )
                    )
                }

                if (overflowAffordances.showEnd) {
                    LibraryTabOverflowFade(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        colors = listOf(
                            Color.Transparent,
                            primaryTabContainerColor
                        )
                    )
                }
            }
        }

        if (selectedPrimaryTab == LibraryTab.SONGS) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = songCollectionTabs,
                    key = { tab -> tab }
                ) { tab ->
                    LibraryFilterPill(
                        tab = tab,
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTabOverflowFade(
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .background(Brush.horizontalGradient(colors = colors))
    )
}

@Composable
private fun LibraryPrimaryTab(
    tab: LibraryTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(42.dp),
        color = if (selected) {
            AppShellAccent.copy(alpha = 0.16f)
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            AppShellAccent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(17.dp)
    ) {
        Box(
            modifier = Modifier
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick
                )
                .padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tab.title.uppercase(),
                style = AppShellTypography.ControlLabel,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LibraryFilterPill(
    tab: LibraryTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (tab) {
        LibraryTab.SONGS -> "All"
        LibraryTab.RECENTLY_ADDED -> stringResource(R.string.recently_added_filter)
        LibraryTab.RECENTLY_PLAYED -> "Recent"
        LibraryTab.MOST_PLAYED -> "Most played"
        LibraryTab.RATED -> "Rated"
        else -> tab.title
    }

    Surface(
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(14.dp),
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
                AppShellAccent.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
            }
        )
    ) {
        Box(
            modifier = Modifier
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick
                )
                .padding(horizontal = 13.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.uppercase(),
                style = AppShellTypography.ControlLabel,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
