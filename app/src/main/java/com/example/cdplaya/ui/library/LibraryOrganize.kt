package com.example.cdplaya.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.UNKNOWN_GENRE_KEY
import com.example.cdplaya.ui.AppShellAccent
import com.example.cdplaya.ui.AppShellIconButton
import com.example.cdplaya.ui.AppShellTypography

@Composable
fun LibraryOrganizeButton(
    songs: List<Song>,
    sortState: LibrarySortState,
    sortOptions: List<LibrarySortOption>,
    onSortStateChanged: (LibrarySortState) -> Unit,
    filterState: LibrarySongFilterState? = null,
    onFilterStateChanged: ((LibrarySongFilterState) -> Unit)? = null,
    optionTitle: (LibrarySortOption) -> String = { option -> option.title },
    modifier: Modifier = Modifier
) {
    var isSheetVisible by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(LibraryOrganizePage.MAIN) }
    val activeFilterCount = filterState?.activeFilterCount ?: 0

    Box(modifier = modifier) {
        AppShellIconButton(
            onClick = {
                page = LibraryOrganizePage.MAIN
                isSheetVisible = true
            },
            imageVector = Icons.Filled.Tune,
            contentDescription = organizeButtonContentDescription(activeFilterCount),
            accented = activeFilterCount > 0
        )

        if (activeFilterCount > 0) {
            OrganizeFilterCountBadge(
                count = activeFilterCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
            )
        }
    }

    if (isSheetVisible) {
        LibraryOrganizeSheet(
            songs = songs,
            sortState = sortState,
            sortOptions = sortOptions,
            onSortStateChanged = onSortStateChanged,
            filterState = filterState,
            onFilterStateChanged = onFilterStateChanged,
            optionTitle = optionTitle,
            page = page,
            onPageChanged = { nextPage -> page = nextPage },
            onDismissRequest = {
                isSheetVisible = false
                page = LibraryOrganizePage.MAIN
            }
        )
    }
}

internal fun organizeButtonContentDescription(activeFilterCount: Int): String =
    if (activeFilterCount > 0) {
        "Organize library, $activeFilterCount active " +
                if (activeFilterCount == 1) "filter" else "filters"
    } else {
        "Organize library"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryOrganizeSheet(
    songs: List<Song>,
    sortState: LibrarySortState,
    sortOptions: List<LibrarySortOption>,
    onSortStateChanged: (LibrarySortState) -> Unit,
    filterState: LibrarySongFilterState?,
    onFilterStateChanged: ((LibrarySongFilterState) -> Unit)?,
    optionTitle: (LibrarySortOption) -> String,
    page: LibraryOrganizePage,
    onPageChanged: (LibraryOrganizePage) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val effectivePage = page.takeIf {
        it == LibraryOrganizePage.MAIN ||
                filterState != null && onFilterStateChanged != null
    } ?: LibraryOrganizePage.MAIN

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 10.dp,
        dragHandle = { OrganizeSheetDragHandle() }
    ) {
        BackHandler(enabled = effectivePage != LibraryOrganizePage.MAIN) {
            onPageChanged(LibraryOrganizePage.MAIN)
        }
        when (effectivePage) {
            LibraryOrganizePage.MAIN -> OrganizeMainContent(
                songs = songs,
                sortState = sortState,
                sortOptions = sortOptions,
                onSortStateChanged = onSortStateChanged,
                filterState = filterState,
                onFilterStateChanged = onFilterStateChanged,
                optionTitle = optionTitle,
                onOpenGenre = { onPageChanged(LibraryOrganizePage.GENRE) },
                onOpenYear = { onPageChanged(LibraryOrganizePage.YEAR) }
            )

            LibraryOrganizePage.GENRE -> GenreSelectionContent(
                songs = songs,
                filterState = requireNotNull(filterState),
                onFilterStateChanged = requireNotNull(onFilterStateChanged),
                onBack = { onPageChanged(LibraryOrganizePage.MAIN) }
            )

            LibraryOrganizePage.YEAR -> YearSelectionContent(
                songs = songs,
                filterState = requireNotNull(filterState),
                onFilterStateChanged = requireNotNull(onFilterStateChanged),
                onBack = { onPageChanged(LibraryOrganizePage.MAIN) }
            )
        }
    }
}

@Composable
private fun OrganizeMainContent(
    songs: List<Song>,
    sortState: LibrarySortState,
    sortOptions: List<LibrarySortOption>,
    onSortStateChanged: (LibrarySortState) -> Unit,
    filterState: LibrarySongFilterState?,
    onFilterStateChanged: ((LibrarySongFilterState) -> Unit)?,
    optionTitle: (LibrarySortOption) -> String,
    onOpenGenre: () -> Unit,
    onOpenYear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Organize library",
            style = AppShellTypography.SectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        OrganizeSectionLabel("SORT BY")
        sortOptions.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { option ->
                    OrganizeChoiceCard(
                        label = optionTitle(option),
                        selected = sortState.option == option,
                        onClick = {
                            onSortStateChanged(sortState.select(option))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowOptions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        OrganizeSectionLabel("DIRECTION")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DirectionChoiceCard(
                direction = LibrarySortDirection.ASCENDING,
                selected = sortState.direction == LibrarySortDirection.ASCENDING,
                onClick = {
                    onSortStateChanged(
                        sortState.copy(direction = LibrarySortDirection.ASCENDING)
                    )
                },
                modifier = Modifier.weight(1f)
            )
            DirectionChoiceCard(
                direction = LibrarySortDirection.DESCENDING,
                selected = sortState.direction == LibrarySortDirection.DESCENDING,
                onClick = {
                    onSortStateChanged(
                        sortState.copy(direction = LibrarySortDirection.DESCENDING)
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (filterState != null && onFilterStateChanged != null) {
            val genreName = remember(songs, filterState.genre) {
                selectedLibraryGenreName(songs, filterState.genre)
            }

            OrganizeSectionLabel("FILTER")
            FilterSelectorRow(
                label = "Genre",
                selection = genreName,
                onClick = onOpenGenre
            )
            FilterSelectorRow(
                label = "Year",
                selection = filterState.year.displayName(),
                onClick = onOpenYear
            )
            OutlinedButton(
                onClick = { onFilterStateChanged(filterState.clear()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = filterState.isActive
            ) {
                Text("Clear filters")
            }
        }
    }
}

@Composable
private fun OrganizeSectionLabel(text: String) {
    Text(
        text = text,
        style = AppShellTypography.Eyebrow,
        color = AppShellAccent,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun OrganizeChoiceCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            this.selected = selected
        },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            AppShellAccent.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                AppShellAccent.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.54f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = AppShellTypography.SongTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(18.dp),
                    tint = AppShellAccent
                )
            }
        }
    }
}

@Composable
private fun DirectionChoiceCard(
    direction: LibrarySortDirection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ascending = direction == LibrarySortDirection.ASCENDING
    val label = if (ascending) "Ascending" else "Descending"
    val icon = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward

    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            this.selected = selected
        },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            AppShellAccent.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                AppShellAccent.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.54f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = if (selected) AppShellAccent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = AppShellTypography.SongTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterSelectorRow(
    label: String,
    selection: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Filter by $label, currently $selection"
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.54f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = AppShellTypography.SongTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = selection,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GenreSelectionContent(
    songs: List<Song>,
    filterState: LibrarySongFilterState,
    onFilterStateChanged: (LibrarySongFilterState) -> Unit,
    onBack: () -> Unit
) {
    val options = remember(songs, filterState.genre) {
        organizeGenreOptions(
            availableLibraryGenreFilters(songs),
            filterState.genre
        )
    }
    SelectorList(
        title = "Choose Genre",
        onBack = onBack,
        items = buildList {
            add(
                OrganizeSelectorItem(
                    key = "all",
                    label = "All genres",
                    selected = filterState.genre == null,
                    onClick = {
                        onFilterStateChanged(filterState.copy(genre = null))
                        onBack()
                    }
                )
            )
            options.forEach { option ->
                add(
                    OrganizeSelectorItem(
                        key = option.key,
                        label = option.name,
                        selected = filterState.genre?.key == option.key,
                        onClick = {
                            onFilterStateChanged(filterState.copy(genre = option))
                            onBack()
                        }
                    )
                )
            }
        }
    )
}

@Composable
private fun YearSelectionContent(
    songs: List<Song>,
    filterState: LibrarySongFilterState,
    onFilterStateChanged: (LibrarySongFilterState) -> Unit,
    onBack: () -> Unit
) {
    val years = remember(songs, filterState.year) {
        organizeYearOptions(availableLibraryYears(songs), filterState.year)
    }
    SelectorList(
        title = "Choose Year",
        onBack = onBack,
        items = buildList {
            add(
                OrganizeSelectorItem(
                    key = "all",
                    label = "All years",
                    selected = filterState.year == LibraryYearFilter.All,
                    onClick = {
                        onFilterStateChanged(filterState.copy(year = LibraryYearFilter.All))
                        onBack()
                    }
                )
            )
            years.forEach { year ->
                add(
                    OrganizeSelectorItem(
                        key = "year:$year",
                        label = year.toString(),
                        selected = filterState.year == LibraryYearFilter.Exact(year),
                        onClick = {
                            onFilterStateChanged(
                                filterState.copy(year = LibraryYearFilter.Exact(year))
                            )
                            onBack()
                        }
                    )
                )
            }
            add(
                OrganizeSelectorItem(
                    key = "unknown",
                    label = "Unknown Year",
                    selected = filterState.year == LibraryYearFilter.Unknown,
                    onClick = {
                        onFilterStateChanged(filterState.copy(year = LibraryYearFilter.Unknown))
                        onBack()
                    }
                )
            )
        }
    )
}

internal fun organizeGenreOptions(
    availableOptions: List<LibraryGenreFilter>,
    selectedGenre: LibraryGenreFilter?
): List<LibraryGenreFilter> {
    if (selectedGenre == null || availableOptions.any { it.key == selectedGenre.key }) {
        return availableOptions
    }
    val unknownIndex = availableOptions.indexOfFirst { it.key == UNKNOWN_GENRE_KEY }
    return if (unknownIndex >= 0) {
        availableOptions.toMutableList().apply { add(unknownIndex, selectedGenre) }
    } else {
        availableOptions + selectedGenre
    }
}

internal fun organizeYearOptions(
    availableYears: List<Int>,
    selectedYear: LibraryYearFilter
): List<Int> = (availableYears + (selectedYear as? LibraryYearFilter.Exact)?.year)
    .filterNotNull()
    .distinct()
    .sortedDescending()

internal fun selectedLibraryGenreName(
    songs: List<Song>,
    selectedGenre: LibraryGenreFilter?
): String = availableLibraryGenreFilters(songs)
    .firstOrNull { option -> option.key == selectedGenre?.key }
    ?.name
    ?: selectedGenre?.name
    ?: "All genres"

@Composable
private fun SelectorList(
    title: String,
    onBack: () -> Unit,
    items: List<OrganizeSelectorItem>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.82f)
            .navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 18.dp
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "header") {
            SelectorHeader(title = title, onBack = onBack)
        }
        items(items, key = OrganizeSelectorItem::key) { item ->
            SelectorRow(item)
        }
    }
}

@Composable
private fun SelectorHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Organize library"
            )
        }
        Text(
            text = title,
            style = AppShellTypography.SectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SelectorRow(item: OrganizeSelectorItem) {
    Surface(
        onClick = item.onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                selected = item.selected
            },
        shape = RoundedCornerShape(14.dp),
        color = if (item.selected) {
            AppShellAccent.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (item.selected) {
                AppShellAccent.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.label,
                style = AppShellTypography.SongTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (item.selected) {
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

@Composable
private fun OrganizeFilterCountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(18.dp),
        shape = CircleShape,
        color = AppShellAccent,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OrganizeSheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .size(width = 34.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(AppShellAccent.copy(alpha = 0.72f))
    )
}

private data class OrganizeSelectorItem(
    val key: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

private enum class LibraryOrganizePage {
    MAIN,
    GENRE,
    YEAR
}
