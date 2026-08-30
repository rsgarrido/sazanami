package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.ui.AppShellIconButton
import io.github.rsgarrido.sazanami.ui.AppShellAccent
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import io.github.rsgarrido.sazanami.ui.AppShellTypography

enum class LibraryViewMode(val storageValue: String) {
    LIST("list"),
    GRID("grid");

    fun toggled(): LibraryViewMode {
        return if (this == LIST) GRID else LIST
    }

    companion object {
        fun fromStorageValue(value: String?): LibraryViewMode {
            return entries.firstOrNull { mode -> mode.storageValue == value } ?: LIST
        }
    }
}

enum class LibraryViewCategory(val storageKey: String) {
    SONGS("songs_view_mode"),
    ALBUMS("albums_view_mode"),
    ARTISTS("artists_view_mode"),
    PLAYLISTS("playlists_view_mode")
}

object LibraryGridColumns {
    const val DEFAULT = 2
    val supported = 2..4

    fun normalize(value: Int): Int {
        return if (value in supported) value else DEFAULT
    }
}

enum class LibraryViewOption(
    val viewMode: LibraryViewMode,
    val gridColumnCount: Int?
) {
    LIST(LibraryViewMode.LIST, null),
    GRID_2(LibraryViewMode.GRID, 2),
    GRID_3(LibraryViewMode.GRID, 3),
    GRID_4(LibraryViewMode.GRID, 4);

    val label: String
        get() = if (this == LIST) "List" else "Grid: $gridColumnCount columns"
}

internal fun libraryViewOptions(adaptiveGrid: Boolean): List<LibraryViewOption> =
    if (adaptiveGrid) {
        listOf(LibraryViewOption.LIST, LibraryViewOption.GRID_2)
    } else {
        LibraryViewOption.entries
    }

internal fun LibraryViewOption.displayLabel(adaptiveGrid: Boolean): String =
    if (adaptiveGrid && viewMode == LibraryViewMode.GRID) "Grid (responsive)" else label

fun LibraryTab.viewCategory(): LibraryViewCategory? {
    return when (this) {
        LibraryTab.SONGS,
        LibraryTab.FAVORITES,
        LibraryTab.RATED,
        LibraryTab.RECENTLY_ADDED -> LibraryViewCategory.SONGS
        LibraryTab.ALBUMS -> LibraryViewCategory.ALBUMS
        LibraryTab.ARTISTS -> LibraryViewCategory.ARTISTS
        LibraryTab.PLAYLISTS -> LibraryViewCategory.PLAYLISTS
        LibraryTab.GENRES -> null
        else -> null
    }
}

@Composable
fun LibraryViewOptionsButton(
    viewMode: LibraryViewMode,
    gridColumnCount: Int,
    adaptiveGrid: Boolean = false,
    onClick: () -> Unit
) {
    val description = if (viewMode == LibraryViewMode.LIST) {
        "View options, currently list"
    } else {
        if (adaptiveGrid) {
            "View options, currently responsive grid"
        } else {
            "View options, currently grid, $gridColumnCount columns"
        }
    }
    AppShellIconButton(
        onClick = onClick,
        imageVector = if (viewMode == LibraryViewMode.LIST) {
            AppShellIcons.ListView
        } else {
            AppShellIcons.GridView
        },
        contentDescription = description,
        accented = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryViewOptionsSheet(
    viewMode: LibraryViewMode,
    gridColumnCount: Int,
    adaptiveGrid: Boolean = false,
    onOptionSelected: (LibraryViewOption) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "VIEW OPTIONS",
                style = AppShellTypography.Eyebrow,
                color = AppShellAccent,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            libraryViewOptions(adaptiveGrid).forEach { option ->
                val isSelected = option.viewMode == viewMode &&
                        (adaptiveGrid && option.viewMode == LibraryViewMode.GRID ||
                                option.gridColumnCount == null ||
                                option.gridColumnCount == gridColumnCount)
                LibraryViewOptionRow(
                    option = option,
                    label = option.displayLabel(adaptiveGrid),
                    isSelected = isSelected,
                    onClick = {
                        onOptionSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryViewOptionRow(
    option: LibraryViewOption,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon: ImageVector = if (option.viewMode == LibraryViewMode.LIST) {
        AppShellIcons.ListView
    } else {
        AppShellIcons.GridView
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            AppShellAccent.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) {
                AppShellAccent.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.54f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (isSelected) {
                    AppShellAccent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = label,
                style = AppShellTypography.SongTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(20.dp),
                    tint = AppShellAccent
                )
            }
        }
    }
}
