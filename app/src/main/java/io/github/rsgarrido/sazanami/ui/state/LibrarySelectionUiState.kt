package io.github.rsgarrido.sazanami.ui.state

enum class LibrarySelectionEntity {
    SONG,
    ALBUM,
    ARTIST
}

data class LibrarySelectionUiState(
    val entity: LibrarySelectionEntity? = null,
    val selectedKeys: Set<String> = emptySet()
) {
    val isActive: Boolean
        get() = entity != null && selectedKeys.isNotEmpty()

    val selectedCount: Int
        get() = selectedKeys.size
}
