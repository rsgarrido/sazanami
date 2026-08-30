package io.github.rsgarrido.sazanami.lyrics

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class AndroidLyricsTreeDataSource(
    private val contentResolver: ContentResolver
) : LyricsTreeDataSource {
    override suspend fun scanRoot(root: LyricsRoot): LyricsTreeScanResult {
        val treeUri = runCatching { Uri.parse(root.uri) }.getOrNull()
            ?: return LyricsTreeScanResult.Failed(root.uri)
        if (!hasPersistedReadPermission(treeUri)) {
            return LyricsTreeScanResult.PermissionLost(root.uri)
        }

        val rootDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: return LyricsTreeScanResult.Failed(root.uri)
        val rootVolumeId = root.volumeId ?: rootDocumentId.substringBefore(':', "")
            .takeIf(String::isNotBlank)

        val pending = ArrayDeque<PendingDirectory>()
        pending.add(PendingDirectory(rootDocumentId, ""))
        val visitedDocumentIds = mutableSetOf<String>()
        val seenUris = mutableSetOf<String>()
        val files = mutableListOf<IndexedLyricsFile>()
        var isRootQuery = true

        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val directory = pending.removeFirst()
            if (!visitedDocumentIds.add(directory.documentId)) continue

            val children = try {
                queryChildren(treeUri, directory.documentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: SecurityException) {
                if (isRootQuery) return LyricsTreeScanResult.PermissionLost(root.uri)
                emptyList()
            } catch (_: Exception) {
                if (isRootQuery) return LyricsTreeScanResult.Failed(root.uri)
                emptyList()
            }
            if (children == null) {
                if (isRootQuery) return LyricsTreeScanResult.Failed(root.uri)
                isRootQuery = false
                continue
            }
            isRootQuery = false

            children.sortedWith(compareBy({ it.displayName.orEmpty() }, { it.documentId }))
                .forEach { child ->
                    coroutineContext.ensureActive()
                    if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(
                            PendingDirectory(
                                documentId = child.documentId,
                                relativeDirectory = joinPath(
                                    directory.relativeDirectory,
                                    child.displayName.orEmpty()
                                )
                            )
                        )
                    } else {
                        val displayName = child.displayName ?: return@forEach
                        if (!hasLrcExtension(displayName)) return@forEach
                        val documentUri = runCatching {
                            DocumentsContract.buildDocumentUriUsingTree(
                                treeUri,
                                child.documentId
                            ).toString()
                        }.getOrNull() ?: return@forEach
                        if (!seenUris.add(documentUri)) return@forEach

                        files += IndexedLyricsFile(
                            documentUri = documentUri,
                            rootUri = root.uri,
                            displayName = displayName,
                            normalizedStem = normalizeFileStem(displayName),
                            relativeDirectory = directory.relativeDirectory,
                            rootVolumeId = rootVolumeId,
                            sizeBytes = child.sizeBytes,
                            lastModifiedEpochMs = child.lastModifiedEpochMs
                        )
                    }
                }
        }

        return LyricsTreeScanResult.Success(files.sortedWith(indexedLyricsFileComparator))
    }

    private fun queryChildren(treeUri: Uri, parentDocumentId: String): List<DocumentRow>? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocumentId
        )
        val cursor = contentResolver.query(
            childrenUri,
            PROJECTION,
            null,
            null,
            null
        ) ?: return null

        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val documentId = it.stringOrNull(0) ?: continue
                    add(
                        DocumentRow(
                            documentId = documentId,
                            displayName = it.stringOrNull(1),
                            mimeType = it.stringOrNull(2),
                            sizeBytes = it.nonNegativeLongOrNull(3),
                            lastModifiedEpochMs = it.nonNegativeLongOrNull(4)
                        )
                    )
                }
            }
        }
    }

    private fun hasPersistedReadPermission(treeUri: Uri): Boolean =
        contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == treeUri && permission.isReadPermission
        }

    private fun joinPath(parent: String, child: String): String =
        listOf(parent, child)
            .filter(String::isNotBlank)
            .joinToString("/")

    private fun Cursor.stringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun Cursor.nonNegativeLongOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index).takeIf { it >= 0L }

    private data class PendingDirectory(
        val documentId: String,
        val relativeDirectory: String
    )

    private data class DocumentRow(
        val documentId: String,
        val displayName: String?,
        val mimeType: String?,
        val sizeBytes: Long?,
        val lastModifiedEpochMs: Long?
    )

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }
}
