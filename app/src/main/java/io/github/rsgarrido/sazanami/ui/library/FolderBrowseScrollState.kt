package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import io.github.rsgarrido.sazanami.data.FolderId

internal sealed interface FolderBrowseScrollKey {
    data object Root : FolderBrowseScrollKey
    data class Folder(val id: FolderId) : FolderBrowseScrollKey
}

internal data class FolderBrowseScrollPosition(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int
)

@Stable
internal class FolderBrowseScrollStateHolder(
    private val stateFactory: (FolderBrowseScrollKey) -> LazyListState = { LazyListState() }
) {
    private val states = mutableMapOf<FolderBrowseScrollKey, LazyListState>()

    fun listStateFor(folderId: FolderId?): LazyListState {
        val key = folderId?.let { FolderBrowseScrollKey.Folder(it) }
            ?: FolderBrowseScrollKey.Root
        return states.getOrPut(key) { stateFactory(key) }
    }

    fun retainFolderIds(validFolderIds: Set<FolderId>) {
        states.keys.removeAll { key ->
            key is FolderBrowseScrollKey.Folder && key.id !in validFolderIds
        }
    }

    internal fun hasStateFor(folderId: FolderId?): Boolean =
        (folderId?.let { FolderBrowseScrollKey.Folder(it) } ?: FolderBrowseScrollKey.Root) in states
}

internal fun clampFolderBrowseScrollPosition(
    position: FolderBrowseScrollPosition,
    itemCount: Int
): FolderBrowseScrollPosition {
    if (itemCount <= 0) return FolderBrowseScrollPosition(0, 0)

    val clampedIndex = position.firstVisibleItemIndex.coerceIn(0, itemCount - 1)
    return FolderBrowseScrollPosition(
        firstVisibleItemIndex = clampedIndex,
        firstVisibleItemScrollOffset = if (clampedIndex == position.firstVisibleItemIndex) {
            position.firstVisibleItemScrollOffset.coerceAtLeast(0)
        } else {
            0
        }
    )
}
