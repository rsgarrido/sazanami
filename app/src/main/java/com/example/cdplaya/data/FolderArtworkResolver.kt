package com.example.cdplaya.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

internal class FolderArtworkResolver(
    private val context: Context,
    private val treeUri: Uri?
) {
    private val artworkByRelativeFolder: Map<String, Uri> by lazy(LazyThreadSafetyMode.NONE) {
        treeUri?.let(::scanTree).orEmpty()
    }

    fun resolve(song: Song): Uri? {
        if (treeUri == null || artworkByRelativeFolder.isEmpty()) return null
        val relativePath = normalizePath(song.relativePath)
        val folderPath = normalizePath(song.folderPath)
        return artworkByRelativeFolder.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { (relativeFolder, _) ->
                relativeFolder.isNotBlank() && (
                        relativePath == relativeFolder ||
                                relativePath.endsWith("/$relativeFolder") ||
                                folderPath == relativeFolder ||
                                folderPath.endsWith("/$relativeFolder")
                        )
            }
            ?.value
            ?: artworkByRelativeFolder[""]?.takeIf {
                relativePath.isBlank() || !relativePath.contains('/')
            }
    }

    private fun scanTree(rootTreeUri: Uri): Map<String, Uri> {
        val resolver = context.contentResolver
        val rootDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(rootTreeUri)
        }.getOrNull() ?: return emptyMap()
        val result = linkedMapOf<String, Uri>()
        val pending = ArrayDeque<PendingDirectory>()
        pending.add(PendingDirectory(rootDocumentId, ""))

        while (pending.isNotEmpty()) {
            val directory = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                rootTreeUri,
                directory.documentId
            )
            val query = try {
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )
            } catch (_: SecurityException) {
                return emptyMap()
            }

            query?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                )
                val nameColumn = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                )
                val mimeColumn = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idColumn) ?: continue
                    val displayName = cursor.getString(nameColumn).orEmpty()
                    val mimeType = cursor.getString(mimeColumn).orEmpty()
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val childPath = listOf(directory.relativePath, displayName)
                            .filter(String::isNotBlank)
                            .joinToString("/")
                        pending.add(PendingDirectory(documentId, childPath))
                    } else if (
                        isLikelyAlbumCoverFile(displayName) &&
                        directory.relativePath !in result
                    ) {
                        result[directory.relativePath] =
                            DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, documentId)
                    }
                }
            }
        }
        return result
    }

    private data class PendingDirectory(
        val documentId: String,
        val relativePath: String
    )
}

internal fun isLikelyAlbumCoverFile(fileName: String): Boolean = when (fileName.lowercase()) {
    "cover.jpg", "cover.jpeg", "cover.png",
    "folder.jpg", "folder.jpeg", "folder.png",
    "front.jpg", "front.jpeg", "front.png",
    "album.jpg", "album.jpeg", "album.png" -> true
    else -> false
}

private fun normalizePath(path: String): String = path
    .replace('\\', '/')
    .trim()
    .trim('/')
