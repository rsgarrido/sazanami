package io.github.rsgarrido.sazanami.ui.statistics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.AnalyticsRangePreset
import io.github.rsgarrido.sazanami.data.AnalyticsRangeSelection
import io.github.rsgarrido.sazanami.data.ListeningOverview
import io.github.rsgarrido.sazanami.data.ListeningRankingCategory
import io.github.rsgarrido.sazanami.data.ListeningTrendMetric
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.AppShellIconButton
import io.github.rsgarrido.sazanami.ui.MusicScreenHeader
import io.github.rsgarrido.sazanami.ui.state.ListeningAnalyticsUiState
import java.time.Instant
import java.time.LocalDate

@Composable
internal fun StatisticsScreen(
    state: ListeningAnalyticsUiState,
    onBackClick: () -> Unit,
    onPresetSelected: (AnalyticsRangePreset) -> Unit,
    onCustomRangeSelected: (LocalDate, LocalDate) -> Unit,
    onRetry: () -> Unit,
    onTrendMetricSelected: (ListeningTrendMetric) -> Unit = {},
    onRankingCategorySelected: (ListeningRankingCategory) -> Unit = {},
    librarySongs: List<Song> = emptyList(),
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showCoverageDialog by rememberSaveable { mutableStateOf(false) }

    val pickerDates = remember(state.selectedRange, state.resolvedRange) {
        when (val selected = state.selectedRange) {
            is AnalyticsRangeSelection.Custom -> selected.startDate to selected.endDateInclusive
            is AnalyticsRangeSelection.Preset -> state.resolvedRange
                ?.takeIf { it.selection == selected }
                ?.eventRange
                ?.let { range ->
                    val zone = state.resolvedRange.zoneId
                    Instant.ofEpochMilli(range.startInclusive).atZone(zone).toLocalDate() to
                            Instant.ofEpochMilli(range.endExclusive).atZone(zone).toLocalDate().minusDays(1L)
                } ?: LocalDate.now().minusDays(29L) to LocalDate.now()
        }
    }
    val displayedSelection = state.resolvedRange?.selection ?: state.selectedRange
    val displayedRangeDescription = analyticsRangeDescription(displayedSelection)
    val artworkIndex = remember(librarySongs) { StatisticsArtworkIndex(librarySongs) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MusicScreenHeader(
                title = stringResource(R.string.statistics_title),
                onBackClick = onBackClick,
                onSettingsClick = null,
                backContentDescription = stringResource(R.string.statistics_back_description),
                backTitleSpacing = 10.dp,
                modifier = Modifier.statusBarsPadding(),
                viewModeAction = {
                    AppShellIconButton(
                        onClick = { showCoverageDialog = true },
                        imageVector = Icons.Rounded.Info,
                        contentDescription = stringResource(R.string.statistics_history_info_description)
                    )
                }
            )
        }

        item {
            Column {
                StatisticsRangeSelector(
                    selectedRange = state.selectedRange,
                    onPresetSelected = onPresetSelected,
                    onCustomClick = { showDatePicker = true }
                )
                StatisticsRefreshSlot(
                    isRefreshing = state.isRefreshing && state.overview != null
                )
            }
        }

        (state.selectedRange as? AnalyticsRangeSelection.Custom)?.let { custom ->
            item {
                val label = formatCustomDateRange(custom.startDate, custom.endDateInclusive)
                val description = stringResource(
                    R.string.statistics_custom_range_description,
                    label
                )
                Text(
                    text = label,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .semantics {
                            contentDescription = description
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val overview = state.overview
        when {
            overview == null && state.error != null -> item {
                StatisticsErrorCard(onRetry = onRetry)
            }
            overview == null -> item {
                StatisticsLoadingState()
            }
            else -> {
                if (state.error != null) {
                    item { StatisticsErrorCard(onRetry = onRetry) }
                }
                val noHistory = state.selectedRange ==
                        AnalyticsRangeSelection.Preset(AnalyticsRangePreset.ALL_TIME) &&
                        overview.detailedEventCount == 0L &&
                        overview.playCounts.totalPlayCount == 0L
                val noRangeActivity = state.selectedRange !=
                        AnalyticsRangeSelection.Preset(AnalyticsRangePreset.ALL_TIME) &&
                        overview.detailedEventCount == 0L
                if (noHistory) {
                    item { StatisticsEmptyCard(R.string.statistics_no_history) }
                } else {
                    item { StatisticsOverviewGrid(overview) }
                    if (noRangeActivity) {
                        item { StatisticsEmptyCard(R.string.statistics_no_activity_range) }
                    }
                }
                item(key = "listening_trend") {
                    ListeningTrendSection(
                        buckets = state.trend,
                        metric = state.trendMetric,
                        zoneId = state.resolvedRange?.zoneId ?: java.time.ZoneId.systemDefault(),
                        selectedRange = displayedSelection,
                        rangeDescription = displayedRangeDescription,
                        hasDetailedEvents = state.coverage?.hasDetailedEvents == true,
                        onMetricSelected = onTrendMetricSelected
                    )
                }
                item(key = "top_listening_header") {
                    TopListeningHeader(
                        selectedCategory = state.rankingCategory,
                        onCategorySelected = onRankingCategorySelected
                    )
                }
                when (state.rankingCategory) {
                    ListeningRankingCategory.TRACKS -> {
                        if (state.topTracks.isEmpty()) {
                            item(key = "top_tracks_empty") {
                                StatisticsEmptyCard(R.string.statistics_rankings_no_tracks)
                            }
                        } else {
                            item(key = "top_tracks") {
                                Column {
                                    state.topTracks.forEachIndexed { index, stats ->
                                        TrackRankingRow(
                                            rank = index + 1,
                                            stats = stats,
                                            artworkModel = artworkIndex.trackArtwork(stats),
                                            showDivider = index != state.topTracks.lastIndex
                                        )
                                    }
                                }
                            }
                        }
                    }
                    ListeningRankingCategory.ARTISTS -> {
                        if (state.topArtists.isEmpty()) {
                            item(key = "top_artists_empty") {
                                StatisticsEmptyCard(R.string.statistics_rankings_no_artists)
                            }
                        } else {
                            item(key = "top_artists") {
                                Column {
                                    state.topArtists.forEachIndexed { index, stats ->
                                        ArtistRankingRow(
                                            rank = index + 1,
                                            stats = stats,
                                            fallbackArtworkModel = artworkIndex.artistFallbackArtwork(stats),
                                            showDivider = index != state.topArtists.lastIndex
                                        )
                                    }
                                }
                            }
                        }
                    }
                    ListeningRankingCategory.ALBUMS -> {
                        if (state.topAlbums.isEmpty()) {
                            item(key = "top_albums_empty") {
                                StatisticsEmptyCard(R.string.statistics_rankings_no_albums)
                            }
                        } else {
                            item(key = "top_albums") {
                                Column {
                                    state.topAlbums.forEachIndexed { index, stats ->
                                        AlbumRankingRow(
                                            rank = index + 1,
                                            stats = stats,
                                            artworkModel = artworkIndex.albumArtwork(stats),
                                            showDivider = index != state.topAlbums.lastIndex
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        StatisticsDateRangeDialog(
            initialStartDate = pickerDates.first,
            initialEndDateInclusive = pickerDates.second,
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                onCustomRangeSelected(start, end)
                showDatePicker = false
            }
        )
    }
    if (showCoverageDialog) {
        AlertDialog(
            onDismissRequest = { showCoverageDialog = false },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.statistics_history_dialog_title)) },
            text = { StatisticsInfoContent() },
            confirmButton = {
                TextButton(onClick = { showCoverageDialog = false }) {
                    Text(stringResource(R.string.statistics_close))
                }
            }
        )
    }
}

@Composable
private fun analyticsRangeDescription(selection: AnalyticsRangeSelection): String = when (selection) {
    is AnalyticsRangeSelection.Custom -> formatCustomDateRange(
        selection.startDate,
        selection.endDateInclusive
    )
    is AnalyticsRangeSelection.Preset -> stringResource(selection.preset.labelResource())
}

@Composable
private fun StatisticsRefreshSlot(isRefreshing: Boolean) {
    val refreshDescription = stringResource(R.string.statistics_refreshing)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .padding(top = 4.dp)
            .testTag("statistics_refresh_slot")
    ) {
        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = refreshDescription
                    }
                    .testTag("statistics_refresh_indicator")
            )
        }
    }
}

@Composable
private fun StatisticsRangeSelector(
    selectedRange: AnalyticsRangeSelection,
    onPresetSelected: (AnalyticsRangePreset) -> Unit,
    onCustomClick: () -> Unit
) {
    val selectedDescription = stringResource(R.string.statistics_range_selected)
    val notSelectedDescription = stringResource(R.string.statistics_range_not_selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnalyticsRangePreset.entries.forEach { preset ->
            val selected = selectedRange == AnalyticsRangeSelection.Preset(preset)
            val fullLabel = stringResource(preset.labelResource())
            val compactLabel = stringResource(preset.compactLabelResource())
            FilterChip(
                selected = selected,
                onClick = { onPresetSelected(preset) },
                label = { Text(compactLabel) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = fullLabel
                        stateDescription = if (selected) selectedDescription else notSelectedDescription
                    }
            )
        }
        val customSelected = selectedRange is AnalyticsRangeSelection.Custom
        FilterChip(
            selected = customSelected,
            onClick = onCustomClick,
            label = { Text(stringResource(R.string.statistics_range_custom)) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics {
                    stateDescription = if (customSelected) selectedDescription else notSelectedDescription
                }
        )
    }
}

private fun AnalyticsRangePreset.labelResource(): Int = when (this) {
    AnalyticsRangePreset.TODAY -> R.string.statistics_range_today
    AnalyticsRangePreset.LAST_7_DAYS -> R.string.statistics_range_last_7_days
    AnalyticsRangePreset.LAST_30_DAYS -> R.string.statistics_range_last_30_days
    AnalyticsRangePreset.THIS_MONTH -> R.string.statistics_range_this_month
    AnalyticsRangePreset.THIS_YEAR -> R.string.statistics_range_this_year
    AnalyticsRangePreset.ALL_TIME -> R.string.statistics_range_all_time
}

private fun AnalyticsRangePreset.compactLabelResource(): Int = when (this) {
    AnalyticsRangePreset.TODAY -> R.string.statistics_range_today
    AnalyticsRangePreset.LAST_7_DAYS -> R.string.statistics_range_7_days_compact
    AnalyticsRangePreset.LAST_30_DAYS -> R.string.statistics_range_30_days_compact
    AnalyticsRangePreset.THIS_MONTH -> R.string.statistics_range_month_compact
    AnalyticsRangePreset.THIS_YEAR -> R.string.statistics_range_year_compact
    AnalyticsRangePreset.ALL_TIME -> R.string.statistics_range_all_time
}

@Composable
private fun StatisticsOverviewGrid(overview: ListeningOverview) {
    val duration = durationPresentation(overview.listeningTime.confirmedDetailedListeningMs)
    val recordedLabel = stringResource(R.string.statistics_recorded_listening)
    val recordedSupport = stringResource(R.string.statistics_recorded_listening_support)
    val recordedAccessibility = "$recordedLabel, ${duration.second}. $recordedSupport"
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.statistics_overview_title),
                style = MaterialTheme.typography.titleMedium
            )
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = recordedAccessibility
                },
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = duration.first,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = recordedLabel,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = recordedSupport,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val fontScale = LocalConfiguration.current.fontScale
                val useThreeColumns = maxWidth >= 270.dp && fontScale < 1.45f
                val plays = formatAnalyticsCount(overview.playCounts.totalPlayCount)
                val completed = formatAnalyticsCount(overview.naturalCompletionCount)
                val notCounted = formatAnalyticsCount(overview.nonQualifiedAttemptCount)
                val notCountedSupport = stringResource(R.string.statistics_not_counted_support_compact)
                val notCountedAccessibility = stringResource(R.string.statistics_not_counted_support)

                if (useThreeColumns) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompactOverviewMetric(
                            title = stringResource(R.string.statistics_plays),
                            value = plays,
                            modifier = Modifier.weight(1f)
                        )
                        CompactOverviewMetric(
                            title = stringResource(R.string.statistics_completed),
                            value = completed,
                            modifier = Modifier.weight(1f)
                        )
                        CompactOverviewMetric(
                            title = stringResource(R.string.statistics_not_counted),
                            value = notCounted,
                            supportingText = notCountedSupport,
                            accessibleSupportingText = notCountedAccessibility,
                            emphasized = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompactOverviewMetric(
                                title = stringResource(R.string.statistics_plays),
                                value = plays,
                                modifier = Modifier.weight(1f)
                            )
                            CompactOverviewMetric(
                                title = stringResource(R.string.statistics_completed),
                                value = completed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        MutedOverviewMetric(
                            title = stringResource(R.string.statistics_not_counted),
                            value = notCounted,
                            supportingText = notCountedSupport,
                            accessibleSupportingText = notCountedAccessibility
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactOverviewMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    accessibleSupportingText: String? = supportingText,
    emphasized: Boolean = true
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = buildString {
                append(title)
                append(", ")
                append(value)
                accessibleSupportingText?.let { append(". "); append(it) }
            }
        },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        supportingText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MutedOverviewMetric(
    title: String,
    value: String,
    supportingText: String,
    accessibleSupportingText: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $value. $accessibleSupportingText"
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatisticsInfoContent() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StatisticsInfoSection(
            title = stringResource(R.string.statistics_info_qualified_title),
            body = stringResource(R.string.statistics_info_qualified_body)
        )
        StatisticsInfoSection(
            title = stringResource(R.string.statistics_info_not_counted_title),
            body = stringResource(R.string.statistics_info_not_counted_body)
        )
        StatisticsInfoSection(
            title = stringResource(R.string.statistics_info_recorded_title),
            body = stringResource(R.string.statistics_info_recorded_body)
        )
        StatisticsInfoSection(
            title = stringResource(R.string.statistics_info_all_time_title),
            body = stringResource(R.string.statistics_info_all_time_body)
        )
    }
}

@Composable
private fun StatisticsInfoSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun durationPresentation(milliseconds: Long): Pair<String, String> {
    val parts = listeningDurationParts(milliseconds)
    return when {
        parts.days > 0L -> {
            val days = pluralStringResource(
                R.plurals.statistics_duration_days_full,
                parts.days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                parts.days
            )
            val hours = pluralStringResource(
                R.plurals.statistics_duration_hours_full,
                parts.hours.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                parts.hours
            )
            stringResource(R.string.statistics_duration_days_hours, parts.days, parts.hours) to "$days, $hours"
        }
        parts.hours > 0L -> {
            val hours = pluralStringResource(R.plurals.statistics_duration_hours_full, parts.hours.toInt(), parts.hours)
            val minutes = pluralStringResource(R.plurals.statistics_duration_minutes_full, parts.minutes.toInt(), parts.minutes)
            stringResource(R.string.statistics_duration_hours_minutes, parts.hours, parts.minutes) to "$hours, $minutes"
        }
        else -> {
            val minutes = pluralStringResource(R.plurals.statistics_duration_minutes_full, parts.minutes.toInt(), parts.minutes)
            stringResource(R.string.statistics_duration_minutes, parts.minutes) to minutes
        }
    }
}

@Composable
private fun StatisticsLoadingState() {
    val loadingDescription = stringResource(R.string.statistics_loading)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = loadingDescription
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text(loadingDescription)
    }
}

@Composable
private fun StatisticsErrorCard(onRetry: () -> Unit) {
    val errorDescription = stringResource(R.string.statistics_error)
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(errorDescription, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onRetry) { Text(stringResource(R.string.statistics_retry)) }
        }
    }
}

@Composable
private fun StatisticsEmptyCard(messageResource: Int) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            text = stringResource(messageResource),
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
