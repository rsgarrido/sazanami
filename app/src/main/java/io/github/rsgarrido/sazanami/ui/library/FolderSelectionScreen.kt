package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.FolderSelection
import io.github.rsgarrido.sazanami.data.FolderSelectionMode
import io.github.rsgarrido.sazanami.data.FolderSelectionState
import io.github.rsgarrido.sazanami.data.LibraryFolder

@Composable
fun FolderSelectionScreen(
    libraryFolders: List<LibraryFolder>,
    folderSelectionMode: FolderSelectionMode,
    selectedLibraryFolders: Set<String>,
    excludedLibraryFolders: Set<String>,
    onBackClick: () -> Unit,
    onFolderToggle: (String) -> Unit,
    onSelectAllClick: () -> Unit,
    onClearSelectionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selection = remember(
        folderSelectionMode,
        selectedLibraryFolders,
        excludedLibraryFolders
    ) {
        FolderSelection(
            mode = folderSelectionMode,
            customFolders = selectedLibraryFolders,
            excludedFolders = excludedLibraryFolders
        )
    }
    val allPaths = remember(libraryFolders) { libraryFolders.map(LibraryFolder::path) }
    val foldersByPath = remember(libraryFolders) { libraryFolders.associateBy(LibraryFolder::path) }
    var expandedFolders by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val expandedSet = expandedFolders.toSet()
    val visibleFolders = remember(libraryFolders, expandedFolders) {
        libraryFolders.filter { folder ->
            var parentPath = folder.parentPath
            var visible = true
            while (parentPath != null && visible) {
                if (parentPath !in expandedSet) {
                    visible = false
                }
                parentPath = foldersByPath[parentPath]?.parentPath
            }
            visible
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back to settings"
                )
            }

            Text(
                text = "Library Folders",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Text(
            text = when {
                folderSelectionMode == FolderSelectionMode.ALL &&
                        excludedLibraryFolders.isEmpty() ->
                    "Every detected folder tree is included."
                folderSelectionMode == FolderSelectionMode.ALL ->
                    "All folder trees are included except ${excludedLibraryFolders.size} exclusion(s)."
                selectedLibraryFolders.isEmpty() ->
                    "No music folders are included."
                excludedLibraryFolders.isEmpty() ->
                    "${selectedLibraryFolders.size} folder root(s) included."
                else ->
                    "${selectedLibraryFolders.size} included • ${excludedLibraryFolders.size} excluded"
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Text(
            text = "Selecting a parent folder includes its current and future subfolders after a library scan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Button(
                onClick = onSelectAllClick,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(text = "Include All")
            }

            Button(onClick = onClearSelectionClick) {
                Text(text = "Clear")
            }
        }

        if (libraryFolders.isEmpty()) {
            Text(
                text = "No music folders found. Run Scan library after adding music to the device.",
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn {
                items(
                    items = visibleFolders,
                    key = { folder -> folder.path }
                ) { folder ->
                    val folderState = selection.stateFor(folder.path, allPaths)
                    val toggleableState = when (folderState) {
                        FolderSelectionState.SELECTED -> ToggleableState.On
                        FolderSelectionState.PARTIAL -> ToggleableState.Indeterminate
                        FolderSelectionState.UNSELECTED -> ToggleableState.Off
                    }
                    val isExpanded = folder.path in expandedSet

                    ListItem(
                        modifier = Modifier.padding(start = (folder.depth * 18).dp),
                        leadingContent = {
                            if (folder.hasChildren) {
                                IconButton(
                                    onClick = {
                                        expandedFolders = if (isExpanded) {
                                            expandedFolders - folder.path
                                        } else {
                                            expandedFolders + folder.path
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) {
                                            Icons.Filled.ExpandMore
                                        } else {
                                            Icons.Filled.ChevronRight
                                        },
                                        contentDescription = if (isExpanded) {
                                            "Collapse ${folder.name}"
                                        } else {
                                            "Expand ${folder.name}"
                                        }
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(48.dp))
                            }
                        },
                        headlineContent = {
                            Text(text = folder.name)
                        },
                        supportingContent = {
                            val songCountText = pluralStringResource(
                                R.plurals.song_count,
                                folder.songCount,
                                folder.songCount
                            )
                            val countDescription = if (folder.hasChildren) {
                                "$songCountText in this folder tree"
                            } else {
                                songCountText
                            }
                            Text(
                                text = "$countDescription\n${folder.path}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            TriStateCheckbox(
                                state = toggleableState,
                                onClick = { onFolderToggle(folder.path) }
                            )
                        }
                    )
                }
            }
        }
    }
}
