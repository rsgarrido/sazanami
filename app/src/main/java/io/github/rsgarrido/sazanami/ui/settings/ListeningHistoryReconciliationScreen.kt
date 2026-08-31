package io.github.rsgarrido.sazanami.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.controller.LinkedHistoricalReconciliation
import io.github.rsgarrido.sazanami.controller.ListeningHistoryReconciliationUiState
import io.github.rsgarrido.sazanami.controller.ReconciliationAlbumKey
import io.github.rsgarrido.sazanami.controller.ReconciliationAlbumPresentation
import io.github.rsgarrido.sazanami.controller.ReconciliationArtistPresentation
import io.github.rsgarrido.sazanami.controller.ReconciliationBrowseMode
import io.github.rsgarrido.sazanami.controller.ReconciliationConfirmation
import io.github.rsgarrido.sazanami.controller.ReconciliationReviewContent
import io.github.rsgarrido.sazanami.controller.ReconciliationReviewFilter
import io.github.rsgarrido.sazanami.controller.ReconciliationReviewTab
import io.github.rsgarrido.sazanami.controller.ReconciliationSortOption
import io.github.rsgarrido.sazanami.controller.ReconciliationTrackPresentation
import io.github.rsgarrido.sazanami.controller.ReconciliationTrackStatus
import io.github.rsgarrido.sazanami.controller.ratingWarning
import io.github.rsgarrido.sazanami.data.HistoricalReconciliationSource
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationCandidate
import io.github.rsgarrido.sazanami.data.LocalReconciliationTarget
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateCategory
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateDisposition
import io.github.rsgarrido.sazanami.data.ReconciliationMissingField
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.ui.AppShellTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ListeningHistoryReconciliationUiActions(
    val onEnter: () -> Unit,
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onTabSelected: (ReconciliationReviewTab) -> Unit,
    val onBrowseModeSelected: (ReconciliationBrowseMode) -> Unit,
    val onBrowseQueryChanged: (String) -> Unit,
    val onSortSelected: (ReconciliationSortOption) -> Unit,
    val onReviewFilterSelected: (ReconciliationReviewFilter) -> Unit,
    val onToggleExpanded: (Long) -> Unit,
    val onToggleAlbum: (ReconciliationAlbumKey) -> Unit,
    val onToggleArtist: (String) -> Unit,
    val onToggleSelected: (Long) -> Unit,
    val onSelectItems: (List<Long>) -> Unit,
    val onClearSelection: () -> Unit,
    val onLinkSelectedRequested: () -> Unit,
    val onSkip: (Long) -> Unit,
    val onCandidateSelected: (List<Long>, LocalReconciliationTarget) -> Unit,
    val onSearchRequested: (List<Long>) -> Unit,
    val onSearchQueryChanged: (String) -> Unit,
    val onSearchDismissed: () -> Unit,
    val onUnlinkRequested: (LinkedHistoricalReconciliation) -> Unit,
    val onConfirmationCancelled: () -> Unit,
    val onConfirmed: () -> Unit,
    val onMessageDismissed: () -> Unit
)

@Composable
fun ListeningHistoryReconciliationScreen(
    state: ListeningHistoryReconciliationUiState,
    actions: ListeningHistoryReconciliationUiActions,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { actions.onEnter() }
    BackHandler(onBack = actions.onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ReconciliationHeader(actions.onBack)
        when (state) {
            ListeningHistoryReconciliationUiState.Loading -> LoadingContent()
            is ListeningHistoryReconciliationUiState.Error -> ErrorContent(
                state.message,
                actions.onRetry
            )
            is ListeningHistoryReconciliationUiState.Content -> Content(
                state.value,
                actions,
                Modifier.weight(1f)
            )
        }
    }
    val content = (state as? ListeningHistoryReconciliationUiState.Content)?.value
    content?.search?.let { search ->
        SearchDialog(
            query = search.query,
            results = search.results,
            isWorking = content.isWorking,
            onQueryChanged = actions.onSearchQueryChanged,
            onSelected = { target -> actions.onCandidateSelected(search.sourceIds, target) },
            onDismiss = actions.onSearchDismissed
        )
    }
    content?.confirmation?.let { confirmation ->
        ConfirmationDialog(
            confirmation = confirmation,
            isWorking = content.isWorking,
            onConfirm = actions.onConfirmed,
            onDismiss = actions.onConfirmationCancelled
        )
    }
}

@Composable
private fun ReconciliationHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 10.dp, end = 20.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                "Match imported tracks",
                style = AppShellTypography.ScreenTitle,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Connect imported listening history to songs in your library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.semantics {
            contentDescription = "Finding imported track matches"
        })
        Text(
            "Finding possible matches…",
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Large listening histories may take a moment.",
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 18.dp)) { Text("Try again") }
    }
}

@Composable
private fun Content(
    content: ReconciliationReviewContent,
    actions: ListeningHistoryReconciliationUiActions,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        TabRow(content, actions.onTabSelected)
        BrowseControls(content, actions)
        content.message?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, modifier = Modifier.weight(1f))
                    TextButton(onClick = actions.onMessageDismissed) { Text("Dismiss") }
                }
            }
        }
        if (content.selectedSourceIds.isNotEmpty()) {
            SelectionBar(content, actions)
        }
        when (content.browseMode) {
            ReconciliationBrowseMode.TRACKS -> TrackPresentationList(
                content.visibleTracks,
                content,
                actions
            )
            ReconciliationBrowseMode.ALBUMS -> AlbumPresentationList(
                content.visibleAlbums,
                content,
                actions
            )
            ReconciliationBrowseMode.ARTISTS -> ArtistPresentationList(
                content.visibleArtists,
                content,
                actions
            )
        }
    }
}

@Composable
private fun BrowseControls(
    content: ReconciliationReviewContent,
    actions: ListeningHistoryReconciliationUiActions
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReconciliationBrowseMode.entries.forEach { mode ->
                FilterChip(
                    selected = content.browseMode == mode,
                    onClick = { actions.onBrowseModeSelected(mode) },
                    label = { Text(mode.displayLabel()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        OutlinedTextField(
            value = content.browseQuery,
            onValueChange = actions.onBrowseQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("Search title, artist, or album") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SortMenu(content.sortOption, actions.onSortSelected)
            Text(
                "${content.visibleTracks.size} shown",
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (content.activeTab == ReconciliationReviewTab.REVIEW) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReconciliationReviewFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = content.reviewFilter == filter,
                        onClick = { actions.onReviewFilterSelected(filter) },
                        label = { Text(filter.displayLabel()) }
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun SortMenu(
    selected: ReconciliationSortOption,
    onSelected: (ReconciliationSortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Sort, contentDescription = null)
            Text(selected.displayLabel(), modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReconciliationSortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayLabel()) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(
    content: ReconciliationReviewContent,
    actions: ListeningHistoryReconciliationUiActions
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${content.selectedSourceIds.size} selected",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = actions.onClearSelection, enabled = !content.isWorking) {
                Text("Clear")
            }
            Button(onClick = actions.onLinkSelectedRequested, enabled = !content.isWorking) {
                Text("Link selected")
            }
        }
    }
}

@Composable
private fun TabRow(content: ReconciliationReviewContent, onSelected: (ReconciliationReviewTab) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        ReconciliationReviewTab.entries.forEach { tab ->
            val count = when (tab) {
                ReconciliationReviewTab.REVIEW -> content.reviewCount
                ReconciliationReviewTab.UNMATCHED -> content.unmatchedCount
                ReconciliationReviewTab.LINKED -> content.linkedCount
            }
            val name = tab.name.lowercase().replaceFirstChar { it.titlecase() }
            val label = "$name $count"
            val selected = content.activeTab == tab
            TextButton(
                onClick = { onSelected(tab) },
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "$label${if (selected) ", selected" else ""}"
                }
            ) {
                Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun TrackPresentationList(
    tracks: List<ReconciliationTrackPresentation>,
    content: ReconciliationReviewContent,
    actions: ListeningHistoryReconciliationUiActions
) {
    if (tracks.isEmpty()) {
        EmptyState(emptyTitle(content), emptyText(content))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tracks, key = { "track-${it.status}-${it.sourceId}" }) { track ->
            CompactTrackCard(track, content, actions)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun AlbumPresentationList(
    albums: List<ReconciliationAlbumPresentation>,
    content: ReconciliationReviewContent,
    actions: ListeningHistoryReconciliationUiActions
) {
    if (albums.isEmpty()) {
        EmptyState(emptyTitle(content), emptyText(content))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(albums, key = { "album-${it.key.stableKey}" }) { album ->
            val expanded = content.expandedAlbumKey == album.key
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                CompactGroupHeader(
                    title = album.title,
                    subtitle = album.artist,
                    summary = albumSummary(album),
                    expanded = expanded,
                    onClick = { actions.onToggleAlbum(album.key) }
                )
                if (expanded) {
                    Column(
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider()
                        val eligible = album.tracks.filter(ReconciliationTrackPresentation::isSelectable)
                        if (content.activeTab == ReconciliationReviewTab.REVIEW && eligible.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { actions.onSelectItems(eligible.map { it.sourceId }) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Select ${eligible.size} review matches") }
                        }
                        album.tracks.forEach { track -> CompactTrackCard(track, content, actions) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ArtistPresentationList(
    artists: List<ReconciliationArtistPresentation>,
    content: ReconciliationReviewContent,
    actions: ListeningHistoryReconciliationUiActions
) {
    if (artists.isEmpty()) {
        EmptyState(emptyTitle(content), emptyText(content))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(artists, key = { "artist-${it.key}" }) { artist ->
            val expanded = content.expandedArtistKey == artist.key
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                CompactGroupHeader(
                    title = artist.artist,
                    subtitle = "${artist.albums.size} ${if (artist.albums.size == 1) "album" else "albums"}",
                    summary = artistSummary(artist),
                    expanded = expanded,
                    onClick = { actions.onToggleArtist(artist.key) }
                )
                if (expanded) {
                    Column(
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider()
                        artist.albums.forEach { album ->
                            Text(
                                album.title,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                            Text(
                                albumSummary(album),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            album.tracks.forEach { track -> CompactTrackCard(track, content, actions) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun CompactGroupHeader(
    title: String,
    subtitle: String,
    summary: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) "Collapse group" else "Expand group",
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand"
        )
    }
}

@Composable
private fun CompactTrackCard(
    track: ReconciliationTrackPresentation,
    content: ReconciliationReviewContent,
    actions: ListeningHistoryReconciliationUiActions
) {
    val expanded = content.expandedSourceId == track.sourceId
    val selected = track.sourceId in content.selectedSourceIds
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Collapse imported track" else "Expand imported track"
                ) { actions.onToggleExpanded(track.sourceId) }
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (track.isSelectable && content.activeTab == ReconciliationReviewTab.REVIEW) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { actions.onToggleSelected(track.sourceId) },
                    enabled = !content.isWorking,
                    modifier = Modifier.semantics {
                        contentDescription = "Select ${track.source.title}"
                    }
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.source.title.ifBlank { "Unknown title" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatArtistAlbum(track.source.artist, track.source.album),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(historicalPlayLabel(track.source), style = MaterialTheme.typography.labelSmall)
                    Text(
                        track.reason.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        if (expanded) {
            HorizontalDivider()
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HistoricalDetails(track.source)
                when (track.status) {
                    ReconciliationTrackStatus.LINKED -> {
                        val linked = requireNotNull(track.linkedItem)
                        Text("Linked local song", style = MaterialTheme.typography.labelLarge)
                        TargetMetadata(linked.target)
                        OutlinedButton(
                            onClick = { actions.onUnlinkRequested(linked) },
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription =
                                    "Unlink ${linked.source.title} from ${linked.target.title}"
                            }
                        ) { Text("Unlink history") }
                    }
                    ReconciliationTrackStatus.REVIEW,
                    ReconciliationTrackStatus.UNMATCHED -> {
                        val item = requireNotNull(track.reviewItem)
                        if (item.disposition == ReconciliationCandidateDisposition.AMBIGUOUS) {
                            WarningText("Multiple library versions may match. No track has been selected.")
                        }
                        item.candidates.forEach { candidate ->
                            CandidateRow(
                                candidate = candidate,
                                actionLabel = if (item.disposition == ReconciliationCandidateDisposition.AMBIGUOUS) {
                                    "Select this track"
                                } else {
                                    "Link history"
                                },
                                evidenceLabel = if (item.candidates.size == 1) {
                                    track.reason.label
                                } else {
                                    candidateEvidenceCopy(candidate.evidence.category)
                                },
                                onSelected = {
                                    actions.onCandidateSelected(listOf(track.sourceId), candidate.target)
                                }
                            )
                        }
                        if (item.hasMoreCandidates) {
                            Text(
                                "More possible matches exist. Search the library to review them.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { actions.onSearchRequested(listOf(track.sourceId)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Text("Choose from library", modifier = Modifier.padding(start = 8.dp))
                        }
                        if (track.status == ReconciliationTrackStatus.REVIEW) {
                            TextButton(
                                onClick = { actions.onSkip(track.sourceId) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Skip · review later") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoricalDetails(source: HistoricalReconciliationSource) {
    val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    val providers = source.importedProviders.mapNotNull { provider ->
        when (provider) {
            ListeningSource.SPOTIFY_IMPORT -> "Spotify import"
            ListeningSource.NATIVE -> null
            ListeningSource.LASTFM_IMPORT -> "Imported history"
        }
    }.sorted().joinToString()
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (providers.isNotBlank()) Text(providers, style = MaterialTheme.typography.labelMedium)
        Text("First listened ${formatter.format(Instant.ofEpochMilli(source.metrics.firstListenedAt))}")
        Text("Last listened ${formatter.format(Instant.ofEpochMilli(source.metrics.lastListenedAt))}")
        if (source.metrics.recordedListeningMs > 0) {
            Text("Recorded listening ${formatListeningDuration(source.metrics.recordedListeningMs)}")
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: ListeningIdentityReconciliationCandidate,
    actionLabel: String,
    evidenceLabel: String = candidateEvidenceCopy(candidate.evidence.category),
    onSelected: () -> Unit
) {
    val warning = candidateWarningCopy(candidate.evidence.category)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(evidenceLabel, style = MaterialTheme.typography.labelLarge)
            TargetMetadata(candidate.target)
            if (candidate.evidence.missingFields.contains(ReconciliationMissingField.ALBUM)) {
                Text("Album information is missing from the imported track.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            warning?.let { WarningText(it) }
            Button(onClick = onSelected, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Icon(Icons.Default.Link, contentDescription = null)
                Text(actionLabel, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun TargetMetadata(target: LocalReconciliationTarget) {
    Text(target.title.ifBlank { "Unknown title" }, style = MaterialTheme.typography.titleMedium)
    Text(
        formatArtistAlbum(target.artist, target.album),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        formatTargetDetails(target),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun WarningText(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Warning: $text" },
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary
        )
        Text(text, modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState(title: String, text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.History, contentDescription = null)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun SearchDialog(
    query: String,
    results: List<LocalReconciliationTarget>,
    isWorking: Boolean,
    onQueryChanged: (String) -> Unit,
    onSelected: (LocalReconciliationTarget) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text("Choose from library") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Search title, artist, or album") }
                )
                if (results.isEmpty()) {
                    Text("No available library songs found.", modifier = Modifier.padding(top = 20.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results, key = { "search-${it.identityId}" }) { target ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = !isWorking,
                                        role = Role.Button,
                                        onClickLabel = "Select ${target.title}"
                                    ) { onSelected(target) },
                                tonalElevation = 2.dp,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) { TargetMetadata(target) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isWorking) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmationDialog(
    confirmation: ReconciliationConfirmation,
    isWorking: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = {
            Text(
                when (confirmation) {
                    is ReconciliationConfirmation.Link -> "Link imported history?"
                    is ReconciliationConfirmation.Batch -> "Link selected histories?"
                    is ReconciliationConfirmation.Unlink -> "Unlink imported history?"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (confirmation) {
                    is ReconciliationConfirmation.Link -> {
                        val first = confirmation.sources.first()
                        Text("Imported history", fontWeight = FontWeight.SemiBold)
                        Text(first.title, style = MaterialTheme.typography.titleMedium)
                        Text(formatArtistAlbum(first.artist, first.album))
                        Text(
                            if (confirmation.sources.size == 1) historicalPlayLabel(first)
                            else "${confirmation.sources.size} imported history fragments"
                        )
                        Text("will be connected to:")
                        TargetMetadata(confirmation.target)
                        ratingWarning(confirmation.ratings)?.let { WarningText(it) }
                        Text("After linking, Statistics will combine this history with the local song.")
                        Text("You can unlink this later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is ReconciliationConfirmation.Batch -> {
                        Text(
                            "${confirmation.selections.size} explicitly selected review matches will be linked in one batch."
                        )
                        confirmation.selections.take(8).forEach { selection ->
                            Text(
                                "${selection.source.title} → ${selection.target.title}",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (confirmation.selections.size > 8) {
                            Text(
                                "And ${confirmation.selections.size - 8} more.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("Existing or conflicting links will not be overwritten.")
                        Text("You can unlink these later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is ReconciliationConfirmation.Unlink -> {
                        Text(
                            "Imported history will remain saved, but it will no longer be combined with this local song in Statistics."
                        )
                        Text(confirmation.item.source.title, style = MaterialTheme.typography.titleMedium)
                        Text("Linked to ${confirmation.item.target.title}")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isWorking) {
                Text(
                    when (confirmation) {
                        is ReconciliationConfirmation.Link -> if (confirmation.sources.size == 1) {
                            "Link history"
                        } else "Link all ${confirmation.sources.size} histories"
                        is ReconciliationConfirmation.Batch ->
                            "Link ${confirmation.selections.size} selected"
                        is ReconciliationConfirmation.Unlink -> "Unlink"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isWorking) { Text("Cancel") } }
    )
}

fun candidateEvidenceCopy(category: ReconciliationCandidateCategory): String = when (category) {
    ReconciliationCandidateCategory.STRONG_METADATA -> "Title, artist, and album match"
    ReconciliationCandidateCategory.CANONICAL_METADATA ->
        "Title, artist, and album match after typography normalization"
    ReconciliationCandidateCategory.TYPOGRAPHY_VARIANT -> "Similar title"
    ReconciliationCandidateCategory.INCOMPLETE_EVIDENCE -> "Some imported metadata is missing"
    ReconciliationCandidateCategory.VERSION_SENSITIVE -> "Possible different song version"
    ReconciliationCandidateCategory.AMBIGUOUS -> "Multiple library versions may match"
}

private fun ReconciliationBrowseMode.displayLabel(): String = when (this) {
    ReconciliationBrowseMode.TRACKS -> "Tracks"
    ReconciliationBrowseMode.ALBUMS -> "Albums"
    ReconciliationBrowseMode.ARTISTS -> "Artists"
}

private fun ReconciliationSortOption.displayLabel(): String = when (this) {
    ReconciliationSortOption.HISTORICAL_PLAYS -> "Historical plays"
    ReconciliationSortOption.TRACK_TITLE -> "Track title"
    ReconciliationSortOption.ARTIST -> "Artist"
    ReconciliationSortOption.ALBUM -> "Album"
}

private fun ReconciliationReviewFilter.displayLabel(): String = when (this) {
    ReconciliationReviewFilter.ALL -> "All review"
    ReconciliationReviewFilter.TITLE_FORMATTING -> "Title formatting"
    ReconciliationReviewFilter.ACCENT_DIACRITIC -> "Accent/diacritic"
    ReconciliationReviewFilter.SIMILAR_TITLE -> "Similar title"
    ReconciliationReviewFilter.AMBIGUOUS -> "Ambiguous"
}

private fun emptyTitle(content: ReconciliationReviewContent): String = when (content.activeTab) {
    ReconciliationReviewTab.REVIEW -> "No review cases found"
    ReconciliationReviewTab.UNMATCHED -> "No unmatched imported tracks found"
    ReconciliationReviewTab.LINKED -> "No linked imported tracks found"
}

private fun emptyText(content: ReconciliationReviewContent): String = when {
    content.browseQuery.isNotBlank() -> "Try another track, artist, or album search."
    content.activeTab == ReconciliationReviewTab.REVIEW ->
        "Change the review filter or browse unmatched history."
    content.activeTab == ReconciliationReviewTab.UNMATCHED ->
        "All imported tracks have a review candidate or are already linked."
    else -> "Confirmed and automatic matches will appear here."
}

fun albumSummary(album: ReconciliationAlbumPresentation): String = buildList {
    add("${album.importedCount} imported")
    if (album.linkedCount > 0) add("${album.linkedCount} linked")
    if (album.reviewCount > 0) add("${album.reviewCount} review")
    if (album.unmatchedCount > 0) add("${album.unmatchedCount} unmatched")
}.joinToString(" · ")

fun artistSummary(artist: ReconciliationArtistPresentation): String = buildList {
    add("${artist.importedCount} imported")
    if (artist.linkedCount > 0) add("${artist.linkedCount} linked")
    if (artist.reviewCount > 0) add("${artist.reviewCount} review")
    if (artist.unmatchedCount > 0) add("${artist.unmatchedCount} unmatched")
}.joinToString(" · ")

fun candidateWarningCopy(category: ReconciliationCandidateCategory): String? = when (category) {
    ReconciliationCandidateCategory.VERSION_SENSITIVE -> "This may be a different version of the song."
    ReconciliationCandidateCategory.AMBIGUOUS -> "Multiple library versions may match."
    else -> null
}

fun formatArtistAlbum(artist: String, album: String): String =
    listOf(artist.trim(), album.trim()).filter(String::isNotBlank).joinToString(" · ")
        .ifBlank { "Unknown artist" }

fun formatTargetDetails(target: LocalReconciliationTarget): String = buildList {
    target.durationMs?.takeIf { it > 0 }?.let { add(formatTrackDuration(it)) }
    target.fileExtension?.takeIf(String::isNotBlank)?.let { add(it.uppercase(Locale.ROOT)) }
    if (isEmpty()) target.relativeFolder?.substringAfterLast('/')?.takeIf(String::isNotBlank)?.let(::add)
}.joinToString(" · ")

private fun historicalPlayLabel(source: HistoricalReconciliationSource): String {
    val count = source.metrics.qualifiedPlayCount
    return "$count historical ${if (count == 1L) "play" else "plays"}"
}

private fun formatTrackDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1_000L
    return "%d:%02d".format(Locale.ROOT, totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatListeningDuration(milliseconds: Long): String {
    val minutes = milliseconds / 60_000L
    return if (minutes < 60) "$minutes min" else "${minutes / 60} hr ${minutes % 60} min"
}
