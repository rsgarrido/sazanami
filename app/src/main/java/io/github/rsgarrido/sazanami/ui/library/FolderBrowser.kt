package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.FolderBrowseIndex
import io.github.rsgarrido.sazanami.data.FolderBrowseNode
import io.github.rsgarrido.sazanami.data.FolderId
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.AppShellAccent
import io.github.rsgarrido.sazanami.ui.AppShellTypography

@Composable
internal fun FoldersTabContent(
    index: FolderBrowseIndex,
    selectedFolderId: FolderId?,
    currentSong: Song?,
    recentlyAddedSongIds: Set<Long>,
    favoriteMembershipKeys: Set<String>,
    onFolderSelected: (FolderId) -> Unit,
    onBackFromFolder: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onToggleFavoriteClick: (Song) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onAddSongsToPlaylistClick: (List<Song>) -> Unit,
    onEditSongTagsClick: (Song) -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier
) {
    val selectedNode = selectedFolderId?.let(index::get)
    if (selectedFolderId == null) {
        FolderRootContent(
            roots = index.roots,
            onFolderSelected = onFolderSelected,
            bottomContentPadding = bottomContentPadding,
            modifier = modifier
        )
        return
    }

    if (selectedNode == null) {
        MissingFolderContent(
            onBackFromFolder = onBackFromFolder,
            modifier = modifier
        )
        return
    }

    SongList(
        songs = selectedNode.directSongs,
        currentSongId = currentSong?.id,
        recentlyAddedSongIds = recentlyAddedSongIds,
        favoriteMembershipKeys = favoriteMembershipKeys,
        onSongClick = onSongClick,
        onPlayNextClick = onPlayNextClick,
        onAddToQueueClick = onAddToQueueClick,
        onToggleFavoriteClick = onToggleFavoriteClick,
        onAddToPlaylistClick = onAddToPlaylistClick,
        onAddSongsToPlaylistClick = onAddSongsToPlaylistClick,
        onEditSongTagsClick = onEditSongTagsClick,
        selectionEnabled = true,
        bottomContentPadding = bottomContentPadding,
        modifier = modifier,
        emptyContent = {
            Text(
                text = "No songs directly in this folder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
            )
        },
        beforeSongsContent = {
            item(key = "folder-detail-header") {
                FolderDetailHeader(
                    node = selectedNode,
                    onBackFromFolder = onBackFromFolder
                )
            }
            if (selectedNode.childFolderIds.isNotEmpty()) {
                item(key = "folder-detail-folders-label") {
                    FolderSectionLabel("Folders")
                }
                items(
                    items = index.childrenOf(selectedNode.id),
                    key = { node -> node.id.stableKey() }
                ) { child ->
                    FolderRow(
                        node = child,
                        onClick = { onFolderSelected(child.id) }
                    )
                }
            }
            item(key = "folder-detail-songs-label") {
                FolderSectionLabel("Songs")
            }
        }
    )
}

@Composable
private fun FolderRootContent(
    roots: List<FolderBrowseNode>,
    onFolderSelected: (FolderId) -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier
) {
    if (roots.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No music folders in your library.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding)
    ) {
        item(key = "folder-root-heading") {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = "Folders", style = AppShellTypography.SectionTitle)
                Text(
                    text = "Music already in your library",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(items = roots, key = { node -> node.id.stableKey() }) { root ->
            FolderRow(
                node = root,
                storageLabel = root.id.volumeName.storageDisplayName(),
                onClick = { onFolderSelected(root.id) }
            )
        }
    }
}

@Composable
private fun FolderDetailHeader(
    node: FolderBrowseNode,
    onBackFromFolder: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBackFromFolder) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to parent folder"
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = node.displayName, style = AppShellTypography.SectionTitle)
            Text(
                text = node.folderSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FolderSectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = AppShellTypography.Eyebrow,
        color = AppShellAccent,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun FolderRow(
    node: FolderBrowseNode,
    storageLabel: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(text = node.displayName, style = AppShellTypography.SongTitle)
        },
        supportingContent = {
            Text(
                text = listOfNotNull(storageLabel, node.folderSummary()).joinToString(" | "),
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = AppShellAccent,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    )
}

@Composable
private fun MissingFolderContent(
    onBackFromFolder: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "This folder is no longer in your library.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Back to folders",
            style = MaterialTheme.typography.labelLarge,
            color = AppShellAccent,
            modifier = Modifier
                .clickable(onClick = onBackFromFolder)
                .padding(16.dp)
        )
    }
}

private fun FolderBrowseNode.folderSummary(): String = listOf(
    songCount.countLabel("song"),
    childFolderIds.size.countLabel("folder")
).joinToString(" | ")

private fun Int.countLabel(noun: String): String = "$this $noun${if (this == 1) "" else "s"}"

private fun FolderId.stableKey(): String = "$volumeName:$normalizedPath"

private fun String.storageDisplayName(): String = when {
    equals("external_primary", ignoreCase = true) -> "Internal storage"
    isBlank() -> "Storage"
    else -> this
}
