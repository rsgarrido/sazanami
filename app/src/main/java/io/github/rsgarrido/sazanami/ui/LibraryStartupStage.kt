package io.github.rsgarrido.sazanami.ui

internal enum class LibraryStartupStage {
    PERMISSION,
    FOLDER_SELECTION,
    LIBRARY_LOADING,
    FOLDER_ARTWORK,
    COMPLETE
}

internal fun resolveLibraryStartupStage(
    hasAudioAccess: Boolean,
    initialFolderSelectionCompleted: Boolean,
    initialLibraryReady: Boolean,
    folderArtworkOnboardingComplete: Boolean
): LibraryStartupStage = when {
    !hasAudioAccess -> LibraryStartupStage.PERMISSION
    !initialFolderSelectionCompleted -> LibraryStartupStage.FOLDER_SELECTION
    !initialLibraryReady -> LibraryStartupStage.LIBRARY_LOADING
    !folderArtworkOnboardingComplete -> LibraryStartupStage.FOLDER_ARTWORK
    else -> LibraryStartupStage.COMPLETE
}
