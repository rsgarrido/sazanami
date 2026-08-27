package com.example.cdplaya.ui.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

internal class SortChangeResetTracker<T>(initialSortKey: T) {
    private var previousSortKey: T = initialSortKey

    fun shouldReset(nextSortKey: T): Boolean {
        val changed = previousSortKey != nextSortKey
        previousSortKey = nextSortKey
        return changed
    }
}

internal data class LibrarySortScrollStates(
    val list: LazyListState,
    val grid: LazyGridState
)

@Composable
internal fun rememberLibrarySortScrollStates(sortKey: Any?): LibrarySortScrollStates {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    ResetLazyListOnSortChange(sortKey, listState)
    ResetLazyGridOnSortChange(sortKey, gridState)
    return remember(listState, gridState) {
        LibrarySortScrollStates(listState, gridState)
    }
}

@Composable
internal fun ResetLazyListOnSortChange(
    sortKey: Any?,
    state: LazyListState
) {
    val tracker = remember { SortChangeResetTracker(sortKey) }
    SideEffect {
        if (tracker.shouldReset(sortKey)) {
            state.requestScrollToItem(0)
        }
    }
}

@Composable
internal fun ResetLazyGridOnSortChange(
    sortKey: Any?,
    state: LazyGridState
) {
    val tracker = remember { SortChangeResetTracker(sortKey) }
    SideEffect {
        if (tracker.shouldReset(sortKey)) {
            state.requestScrollToItem(0)
        }
    }
}
