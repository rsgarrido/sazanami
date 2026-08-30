package io.github.rsgarrido.sazanami.ui

import androidx.compose.runtime.compositionLocalOf
import io.github.rsgarrido.sazanami.mediaaccess.FolderArtworkAccessState

internal data class FolderArtworkUiEnvironment(
    val state: FolderArtworkAccessState = FolderArtworkAccessState(),
    val onChooseFolder: () -> Unit = {},
    val onClearFolder: () -> Unit = {}
)

internal val LocalFolderArtworkUi = compositionLocalOf { FolderArtworkUiEnvironment() }
