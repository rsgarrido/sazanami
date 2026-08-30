package io.github.rsgarrido.sazanami.mediaaccess

import android.content.Context
import android.net.Uri

internal data class FolderArtworkAccessState(
    val treeUri: Uri? = null,
    val onboardingComplete: Boolean = false
) {
    val hasFolderAccess: Boolean get() = treeUri != null
}

internal class FolderArtworkAccessStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun readState(): FolderArtworkAccessState = FolderArtworkAccessState(
        treeUri = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse),
        onboardingComplete = preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    )

    fun readValidatedState(
        hasPersistedReadPermission: (Uri) -> Boolean
    ): FolderArtworkAccessState {
        val stored = readState()
        val treeUri = stored.treeUri ?: return stored
        if (hasPersistedReadPermission(treeUri)) return stored

        preferences.edit()
            .remove(KEY_TREE_URI)
            .putBoolean(KEY_ONBOARDING_COMPLETE, false)
            .apply()
        return FolderArtworkAccessState()
    }

    fun setTreeUri(uri: Uri) {
        preferences.edit()
            .putString(KEY_TREE_URI, uri.toString())
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()
    }

    fun skipOnboarding() {
        preferences.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()
    }

    fun clearTreeUri() {
        preferences.edit()
            .remove(KEY_TREE_URI)
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "folder_artwork_access"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}

internal fun folderArtworkLocationLabel(uri: Uri?): String {
    if (uri == null) return "Embedded artwork only"
    val decoded = Uri.decode(uri.lastPathSegment.orEmpty())
    val label = decoded.substringAfter(':', decoded).trim('/').substringAfterLast('/')
    return label.takeIf(String::isNotBlank)?.let { "Folder artwork • $it" }
        ?: "Folder artwork enabled"
}
