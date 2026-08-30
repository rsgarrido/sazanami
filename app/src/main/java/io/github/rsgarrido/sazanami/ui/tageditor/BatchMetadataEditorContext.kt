package io.github.rsgarrido.sazanami.ui.tageditor

import androidx.compose.runtime.Immutable

@Immutable
sealed interface BatchMetadataEditorContext {
    data object SongSelection : BatchMetadataEditorContext

    data class Album(
        val albumKey: String,
        val title: String,
        val artworkUri: String?
    ) : BatchMetadataEditorContext
}
