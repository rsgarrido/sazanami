package io.github.rsgarrido.sazanami.lyrics

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounded transfer primitives are shared by SAF access and regression tests. */
object LyricsDocumentTransfer {
    fun read(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > LyricsEditingLimits.DOCUMENT_BYTES) throw LyricsEditException(LyricsEditError.TOO_LARGE)
            output.write(buffer, 0, count)
        }
        return output.toByteArray().also { bytes ->
            val editor = LyricsDocumentEditor()
            editor.validate(editor.parse(bytes))
        }
    }

    fun write(bytes: ByteArray, output: OutputStream) {
        if (bytes.size > LyricsEditingLimits.DOCUMENT_BYTES) throw LyricsEditException(LyricsEditError.TOO_LARGE)
        output.write(bytes)
        output.flush()
    }

    suspend fun importDocument(resolver: ContentResolver, uri: Uri?): ByteArray = withContext(Dispatchers.IO) {
        if (uri == null) throw LyricsEditException(LyricsEditError.IMPORT_CANCELLED)
        try {
            resolver.openInputStream(uri)?.use(::read) ?: throw LyricsEditException(LyricsEditError.IMPORT_READ)
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (failure: LyricsEditException) { throw failure }
          catch (_: LyricsInputLimitException) { throw LyricsEditException(LyricsEditError.TOO_LARGE) }
          catch (_: Exception) { throw LyricsEditException(LyricsEditError.IMPORT_READ) }
    }

    suspend fun exportDocument(resolver: ContentResolver, uri: Uri?, bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (uri == null) throw LyricsEditException(LyricsEditError.EXPORT_CANCELLED)
        try {
            // CreateDocument's explicit destination is the only URI we ever write. Providers
            // need not support atomic replacement. A failed write may leave partial output;
            // never delete a possibly pre-existing destination as an attempted rollback.
            resolver.openOutputStream(uri, "wt")?.use { write(bytes, it) }
                ?: throw LyricsEditException(LyricsEditError.EXPORT_WRITE)
            // Reporting a provider-adjusted name is optional; a failed name query must not
            // misreport a successfully closed output stream as an export failure.
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.take(512) else null
                }
            }.getOrNull()
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (failure: LyricsEditException) { throw failure }
          catch (_: Exception) { throw LyricsEditException(LyricsEditError.EXPORT_WRITE) }
    }
}
