package com.example.cdplaya.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContract
import com.example.cdplaya.controller.ListeningHistoryImportFile
import java.io.IOException
import java.io.InputStream

class OpenSpotifyHistoryDocuments : ActivityResultContract<Unit, List<Uri>>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/json",
                    "text/json",
                    "text/plain",
                    "application/octet-stream"
                )
            )
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != android.app.Activity.RESULT_OK || intent == null) return emptyList()
        val selected = buildList {
            intent.data?.let(::add)
            intent.clipData?.let { clip ->
                repeat(clip.itemCount) { index -> add(clip.getItemAt(index).uri) }
            }
        }
        return selected.distinctBy(Uri::toString)
    }
}

class SafListeningHistoryImportFile(
    private val uri: Uri,
    override val displayName: String,
    private val streamOpener: (Uri) -> InputStream?
) : ListeningHistoryImportFile {
    override val transientKey: String = uri.toString()

    override fun openStream(): InputStream = streamOpener(uri)
        ?: throw IOException("The selected document could not be opened.")

    companion object {
        fun fromUris(
            contentResolver: ContentResolver,
            uris: List<Uri>
        ): List<SafListeningHistoryImportFile> = uris
            .distinctBy(Uri::toString)
            .map { uri ->
                SafListeningHistoryImportFile(
                    uri = uri,
                    displayName = contentResolver.safeDisplayName(uri),
                    streamOpener = contentResolver::openInputStream
                )
            }
    }
}

internal fun ContentResolver.safeDisplayName(uri: Uri): String {
    val queried = runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            cursor.firstDisplayName()
        }
    }.getOrNull()
    return safeDisplayName(queried)
}

internal fun safeDisplayName(value: String?): String =
    value?.trim()?.takeIf(String::isNotEmpty) ?: "Selected JSON file"

private fun Cursor.firstDisplayName(): String? {
    if (!moveToFirst()) return null
    val column = getColumnIndex(OpenableColumns.DISPLAY_NAME)
    return if (column >= 0 && !isNull(column)) getString(column) else null
}
