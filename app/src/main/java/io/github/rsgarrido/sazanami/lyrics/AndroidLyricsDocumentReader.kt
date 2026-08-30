package io.github.rsgarrido.sazanami.lyrics

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class AndroidLyricsDocumentReader(
    private val contentResolver: ContentResolver,
    private val maximumBytes: Int = DEFAULT_MAXIMUM_BYTES
) : LyricsDocumentReader {
    override suspend fun read(documentUri: String): LyricsDocumentReadResult {
        return try {
            val uri = Uri.parse(documentUri)
            val stream = contentResolver.openInputStream(uri)
                ?: return LyricsDocumentReadResult.Missing
            val bytes = stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > maximumBytes) {
                        return LyricsDocumentReadResult.Failed
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            LyricsDocumentReadResult.Success(bytes)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            LyricsDocumentReadResult.PermissionLost
        } catch (_: FileNotFoundException) {
            LyricsDocumentReadResult.Missing
        } catch (_: Exception) {
            LyricsDocumentReadResult.Failed
        }
    }

    companion object {
        const val DEFAULT_MAXIMUM_BYTES = 2 * 1024 * 1024
    }
}
