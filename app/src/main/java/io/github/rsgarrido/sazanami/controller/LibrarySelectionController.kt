package io.github.rsgarrido.sazanami.controller

import io.github.rsgarrido.sazanami.ui.state.LibrarySelectionEntity
import io.github.rsgarrido.sazanami.ui.state.LibrarySelectionUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LibrarySelectionController {
    private val _uiState = MutableStateFlow(LibrarySelectionUiState())
    val uiState: StateFlow<LibrarySelectionUiState> = _uiState.asStateFlow()

    fun enter(entity: LibrarySelectionEntity, key: String) {
        if (key.isBlank()) return
        _uiState.value = LibrarySelectionUiState(entity, setOf(key))
    }

    fun toggle(entity: LibrarySelectionEntity, key: String) {
        if (key.isBlank()) return
        _uiState.update { current ->
            if (current.entity != entity) {
                LibrarySelectionUiState(entity, setOf(key))
            } else {
                val keys = if (key in current.selectedKeys) {
                    current.selectedKeys - key
                } else {
                    current.selectedKeys + key
                }
                if (keys.isEmpty()) LibrarySelectionUiState()
                else current.copy(selectedKeys = keys)
            }
        }
    }

    fun selectDisplayed(entity: LibrarySelectionEntity, keys: Collection<String>) {
        val eligibleKeys = keys.filterTo(linkedSetOf()) { it.isNotBlank() }
        if (eligibleKeys.isEmpty()) return
        _uiState.update { current ->
            if (current.entity == entity) {
                current.copy(selectedKeys = current.selectedKeys + eligibleKeys)
            } else {
                LibrarySelectionUiState(entity, eligibleKeys)
            }
        }
    }

    fun reconcile(entity: LibrarySelectionEntity, validKeys: Set<String>) {
        _uiState.update { current ->
            if (current.entity != entity) return@update current
            val retained = current.selectedKeys intersect validKeys
            if (retained.isEmpty()) LibrarySelectionUiState()
            else current.copy(selectedKeys = retained)
        }
    }

    fun clear() {
        _uiState.value = LibrarySelectionUiState()
    }
}
