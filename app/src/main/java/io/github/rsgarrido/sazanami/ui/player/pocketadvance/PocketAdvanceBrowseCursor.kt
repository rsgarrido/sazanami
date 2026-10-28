package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A compact cursor/viewport for the Artists list or the last opened artist's tracks. */
@Stable
internal class PocketAdvanceBrowseCursor {
    var selectedKey by mutableStateOf<String?>(null)
        private set
    private var indexHint by mutableIntStateOf(0)
    var listState = LazyListState()
        private set
    var scrollRequest by mutableLongStateOf(0L)
        private set
    private var handledScrollRequest = 0L
    private var restoredViewportKey: String? = null

    fun selectedIndex(keys: List<String>): Int? {
        if (keys.isEmpty()) return null
        selectedKey?.let { key ->
            if (keys.getOrNull(indexHint) == key) return indexHint
            val relocated = keys.indexOf(key)
            if (relocated >= 0) return relocated
        }
        return indexHint.coerceIn(0, keys.lastIndex)
    }

    fun select(key: String, index: Int) {
        selectedKey = key
        indexHint = index.coerceAtLeast(0)
    }

    fun move(delta: Int, keys: List<String>, wrap: Boolean): Boolean {
        val current = selectedIndex(keys) ?: return false
        val next = current + delta
        if (!wrap && next !in keys.indices) return false
        val index = (next + keys.size) % keys.size
        select(keys[index], index)
        scrollRequest += 1
        return true
    }

    fun reconcile(keys: List<String>, isLibraryReady: Boolean) {
        val index = selectedIndex(keys)
        if (index == null) {
            if (isLibraryReady) clear()
            return
        }
        if (isLibraryReady || selectedKey == null || keys[index] == selectedKey) select(keys[index], index)
        restoredViewportKey?.let { key ->
            val restoredIndex = keys.indexOf(key)
            if (restoredIndex >= 0 || isLibraryReady) {
                listState.requestScrollToItem(
                    if (restoredIndex >= 0) restoredIndex else listState.firstVisibleItemIndex.coerceIn(0, keys.lastIndex),
                    listState.firstVisibleItemScrollOffset
                )
                restoredViewportKey = null
            }
        }
    }

    fun clear() {
        selectedKey = null
        indexHint = 0
        restoredViewportKey = null
        listState.requestScrollToItem(0)
    }

    fun takeScrollRequest(): Boolean {
        if (handledScrollRequest == scrollRequest) return false
        handledScrollRequest = scrollRequest
        return true
    }

    fun save(): List<Any> {
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
        val viewportKey = if (selectedKey == null) "" else {
            ((visible?.takeIf { it.index == listState.firstVisibleItemIndex }?.key as? String)
                ?: restoredViewportKey).orEmpty()
        }
        return listOf(selectedKey.orEmpty(), indexHint, listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset, viewportKey)
    }

    fun restore(saved: Any?) {
        val values = saved as? List<*> ?: return
        selectedKey = (values.getOrNull(0) as? String)?.takeIf(String::isNotEmpty)
        indexHint = ((values.getOrNull(1) as? Int) ?: 0).coerceAtLeast(0)
        listState = LazyListState(((values.getOrNull(2) as? Int) ?: 0).coerceAtLeast(0),
            ((values.getOrNull(3) as? Int) ?: 0).coerceAtLeast(0))
        restoredViewportKey = (values.getOrNull(4) as? String)?.takeIf(String::isNotEmpty)
    }
}
