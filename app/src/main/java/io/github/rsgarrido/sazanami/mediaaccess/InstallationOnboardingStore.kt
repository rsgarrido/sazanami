package io.github.rsgarrido.sazanami.mediaaccess

import android.content.Context
import java.io.File

/** Installation-local onboarding state. Files in noBackupFilesDir are never restored by Auto Backup. */
internal class InstallationOnboardingStore private constructor(
    private val completionMarker: File
) {
    constructor(context: Context) : this(
        File(
            context.applicationContext.noBackupFilesDir,
            LIBRARY_FOLDER_SELECTION_MARKER
        )
    )

    internal constructor(noBackupDirectory: File, markerName: String) : this(
        File(noBackupDirectory, markerName)
    )

    fun isLibraryFolderSelectionCompleted(): Boolean = completionMarker.isFile

    fun markLibraryFolderSelectionCompleted() {
        completionMarker.parentFile?.mkdirs()
        if (!completionMarker.exists()) {
            check(completionMarker.createNewFile()) {
                "Unable to persist installation onboarding completion."
            }
        }
    }

    internal fun markerFile(): File = completionMarker

    private companion object {
        const val LIBRARY_FOLDER_SELECTION_MARKER =
            "initial_library_folder_selection_completed"
    }
}

internal fun shouldMigrateLegacyOnboardingCompletion(
    legacyCompletionPresent: Boolean,
    hasMeaningfulLegacyFolderSelection: Boolean,
    firstInstallTimeMillis: Long,
    lastUpdateTimeMillis: Long
): Boolean {
    val isInPlaceUpdate = firstInstallTimeMillis > 0L &&
            lastUpdateTimeMillis > firstInstallTimeMillis
    return isInPlaceUpdate &&
            (legacyCompletionPresent || hasMeaningfulLegacyFolderSelection)
}
