package io.github.rsgarrido.sazanami.lyrics

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

class AndroidLyricsFolderAccess(
    private val contentResolver: ContentResolver
) {
    fun retainReadAccess(uri: Uri): Result<LyricsRoot> = runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        check(hasReadAccess(uri)) { "Persisted read access was not retained" }

        val documentId = DocumentsContract.getTreeDocumentId(uri)
        LyricsRoot(
            uri = uri.toString(),
            displayName = runCatching { queryDisplayName(uri) }.getOrNull()
                ?: documentId.substringAfter(':', documentId).ifBlank { "Lyrics folder" },
            volumeId = documentId.substringBefore(':', "")
                .takeIf(String::isNotBlank)
        )
    }

    fun hasReadAccess(rootUri: String): Boolean =
        runCatching { hasReadAccess(Uri.parse(rootUri)) }.getOrDefault(false)

    private fun hasReadAccess(uri: Uri): Boolean =
        contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission
        }

    private fun queryDisplayName(treeUri: Uri): String? {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }
}
