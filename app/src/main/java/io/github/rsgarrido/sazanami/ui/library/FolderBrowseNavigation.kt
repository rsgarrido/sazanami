package io.github.rsgarrido.sazanami.ui.library

import io.github.rsgarrido.sazanami.data.FolderBrowseIndex
import io.github.rsgarrido.sazanami.data.FolderId

internal fun resolveFolderBrowseSelection(
    index: FolderBrowseIndex,
    selectedFolderId: FolderId?
): FolderId? {
    var candidate = selectedFolderId ?: return null
    while (true) {
        if (index[candidate] != null) return candidate
        val parentPath = candidate.normalizedPath.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentPath.isBlank()) return null
        candidate = FolderId(candidate.volumeName, parentPath)
    }
}

internal fun folderBrowseBackDestination(
    index: FolderBrowseIndex,
    selectedFolderId: FolderId?
): FolderId? {
    val currentId = resolveFolderBrowseSelection(index, selectedFolderId) ?: return null
    return index[currentId]?.parentId
}

internal fun saveFolderBrowseSelection(folderId: FolderId?): List<String> =
    folderId?.let { listOf(it.volumeName, it.normalizedPath) }.orEmpty()

internal fun restoreFolderBrowseSelection(saved: List<String>): FolderId? =
    saved.takeIf { it.size == 2 }
        ?.let { FolderId(volumeName = it[0], normalizedPath = it[1]) }
