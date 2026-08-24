package com.example.cdplaya.ui

import androidx.compose.runtime.compositionLocalOf
import com.example.cdplaya.mediaaccess.FolderArtworkAccessState

internal data class FolderArtworkUiEnvironment(
    val state: FolderArtworkAccessState = FolderArtworkAccessState(),
    val onChooseFolder: () -> Unit = {},
    val onClearFolder: () -> Unit = {}
)

internal val LocalFolderArtworkUi = compositionLocalOf { FolderArtworkUiEnvironment() }
