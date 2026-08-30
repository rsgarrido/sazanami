package io.github.rsgarrido.sazanami.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.controller.LinkedHistoricalReconciliation
import io.github.rsgarrido.sazanami.controller.LinkedReconciliationGroup
import io.github.rsgarrido.sazanami.controller.ListeningHistoryReconciliationUiState
import io.github.rsgarrido.sazanami.controller.ReconciliationConfirmation
import io.github.rsgarrido.sazanami.controller.ReconciliationReviewContent
import io.github.rsgarrido.sazanami.controller.ReconciliationReviewTab
import io.github.rsgarrido.sazanami.controller.ratingWarning
import io.github.rsgarrido.sazanami.data.HistoricalReconciliationItem
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
    val onToggleExpanded: (Long) -> Unit,
    val onToggleLinkedGroup: (Long) -> Unit,
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
        when (content.activeTab) {
            ReconciliationReviewTab.SUGGESTED -> ReviewList(
                items = content.suggestedItems,
                emptyTitle = "No suggested matches",
                emptyText = "You can still search your library from the Unmatched tab.",
                expandedSourceId = content.expandedSourceId,
                actions = actions
            )
            ReconciliationReviewTab.UNMATCHED -> ReviewList(
                items = content.unmatchedItems,
                emptyTitle = "No unmatched imported tracks",
                emptyText = "All imported tracks with reviewable history have suggestions or are already linked.",
                expandedSourceId = content.expandedSourceId,
                actions = actions
            )
            ReconciliationReviewTab.LINKED -> LinkedList(
                groups = content.linkedGroups,
                expandedTargetId = content.expandedLinkedTargetId,
                actions = actions
            )
        }
    }
}

@Composable
private fun TabRow(content: ReconciliationReviewContent, onSelected: (ReconciliationReviewTab) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        ReconciliationReviewTab.entries.forEach { tab ->
            val count = when (tab) {
                ReconciliationReviewTab.SUGGESTED -> content.suggestedCount
                ReconciliationReviewTab.UNMATCHED -> content.unmatchedCount
                ReconciliationReviewTab.LINKED -> content.linkedCount
            }
            val label = "${tab.name.lowercase().replaceFirstChar { it.titlecase() }} $count"
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
private fun ReviewList(
    items: List<HistoricalReconciliationItem>,
    emptyTitle: String,
    emptyText: String,
    expandedSourceId: Long?,
    actions: ListeningHistoryReconciliationUiActions
) {
    if (items.isEmpty()) {
        EmptyState(emptyTitle, emptyText)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { "history-${it.source.identityId}" }) { item ->
            ReviewCard(item, expandedSourceId == item.source.identityId, actions)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ReviewCard(
    item: HistoricalReconciliationItem,
    expanded: Boolean,
    actions: ListeningHistoryReconciliationUiActions
) {
    val source = item.source
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Collapse imported track" else "Expand imported track"
                ) { actions.onToggleExpanded(source.identityId) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(source.title.ifBlank { "Unknown title" }, style = MaterialTheme.typography.titleMedium)
                Text(
                    formatArtistAlbum(source.artist, source.album),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    historicalPlayLabel(source),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    when (item.disposition) {
                        ReconciliationCandidateDisposition.SUGGESTED -> candidateEvidenceCopy(item.candidates.single().evidence.category)
                        ReconciliationCandidateDisposition.AMBIGUOUS -> "Multiple possible matches"
                        ReconciliationCandidateDisposition.NO_CANDIDATE -> "No likely library match found"
                    },
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        if (expanded) {
            HorizontalDivider()
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HistoricalDetails(source)
                if (item.disposition == ReconciliationCandidateDisposition.AMBIGUOUS) {
                    WarningText("Multiple library versions may match. No track has been selected.")
                }
                item.candidates.forEach { candidate ->
                    CandidateRow(
                        candidate,
                        actionLabel = if (item.disposition == ReconciliationCandidateDisposition.AMBIGUOUS) {
                            "Select this track"
                        } else {
                            "Link history"
                        },
                        onSelected = {
                            actions.onCandidateSelected(listOf(source.identityId), candidate.target)
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
                    onClick = { actions.onSearchRequested(listOf(source.identityId)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text("Choose from library", modifier = Modifier.padding(start = 8.dp))
                }
                TextButton(
                    onClick = { actions.onSkip(source.identityId) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Skip · review later") }
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
    onSelected: () -> Unit
) {
    val warning = candidateWarningCopy(candidate.evidence.category)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(candidateEvidenceCopy(candidate.evidence.category), style = MaterialTheme.typography.labelLarge)
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
private fun LinkedList(
    groups: List<LinkedReconciliationGroup>,
    expandedTargetId: Long?,
    actions: ListeningHistoryReconciliationUiActions
) {
    if (groups.isEmpty()) {
        EmptyState("No imported history has been linked yet", "Confirmed matches will appear here.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups, key = { "linked-target-${it.target.identityId}" }) { group ->
            val expanded = expandedTargetId == group.target.identityId
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (expanded) {
                                "Collapse linked histories"
                            } else {
                                "Expand linked histories"
                            }
                        ) { actions.onToggleLinkedGroup(group.target.identityId) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            group.target.title.ifBlank { "Unknown title" },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            formatArtistAlbum(group.target.artist, group.target.album),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            linkedGroupSummary(group),
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
                if (expanded) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider()
                        Text("Local song", style = MaterialTheme.typography.labelLarge)
                        TargetMetadata(group.target)
                        HorizontalDivider()
                        group.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.source.title.ifBlank { "Unknown title" },
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        formatArtistAlbum(item.source.artist, item.source.album),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        historicalPlayLabel(item.source),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                TextButton(
                                    onClick = { actions.onUnlinkRequested(item) },
                                    modifier = Modifier.semantics {
                                        contentDescription =
                                            "Unlink ${item.source.title} from ${item.target.title}"
                                    }
                                ) { Text("Unlink") }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

fun linkedGroupSummary(group: LinkedReconciliationGroup): String {
    val histories = if (group.historicalIdentityCount == 1) {
        "1 imported history"
    } else {
        "${group.historicalIdentityCount} imported histories"
    }
    val plays = if (group.historicalPlayCount == 1L) "1 historical play"
    else "${group.historicalPlayCount} historical plays"
    return "$histories · $plays"
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
    val isLink = confirmation is ReconciliationConfirmation.Link
    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text(if (isLink) "Link imported history?" else "Unlink imported history?") },
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
    ReconciliationCandidateCategory.TYPOGRAPHY_VARIANT -> "Minor punctuation, spelling, or accent differences"
    ReconciliationCandidateCategory.INCOMPLETE_EVIDENCE -> "Some imported metadata is missing"
    ReconciliationCandidateCategory.VERSION_SENSITIVE -> "Possible different song version"
    ReconciliationCandidateCategory.AMBIGUOUS -> "Multiple library versions may match"
}

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
