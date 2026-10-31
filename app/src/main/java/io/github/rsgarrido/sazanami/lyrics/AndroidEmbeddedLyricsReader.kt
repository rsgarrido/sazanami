package io.github.rsgarrido.sazanami.lyrics

import android.content.ContentResolver
import android.net.Uri
import android.system.Os
import android.system.OsConstants
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class AndroidEmbeddedLyricsReader(
    private val resolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher,
    private val fileReader: FileEmbeddedLyricsReader = FileEmbeddedLyricsReader(),
    private val diagnostics: EmbeddedLyricsDiagnostics = EmbeddedLyricsDiagnostics.NONE
) : EmbeddedLyricsReader {
    override suspend fun read(song: SongLyricsIdentity): EmbeddedLyricsReadResult = withContext(ioDispatcher) {
        val context = coroutineContext
        val extension = song.audioFileName.substringAfterLast('.', "")
            .ifBlank { song.audioFilePath.substringAfterLast('.', "") }.lowercase(java.util.Locale.ROOT)
        val key = song.trackKey
        if (extension !in FileEmbeddedLyricsReader.SUPPORTED_EXTENSIONS) {
            return@withContext FileEmbeddedLyricsReader.failure(key, LyricsSourceIssue.Kind.UNSUPPORTED_FORMAT)
        }
        var stage = EmbeddedLyricsReadStage.FILE_ACCESS
        try {
            context.ensureActive()
            val file = File(song.audioFilePath)
            if (song.audioFilePath.isNotBlank() && file.isFile && file.canRead()) {
                return@withContext fileReader.read(file, key, extension, diagnostics) { context.ensureActive() }
            }
            val uri = song.reference?.contentUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
                ?: return@withContext FileEmbeddedLyricsReader.failure(key, LyricsSourceIssue.Kind.READ_FAILURE)
            stage = EmbeddedLyricsReadStage.PROVIDER_OPEN
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                stage = EmbeddedLyricsReadStage.DESCRIPTOR_PROBE
                try {
                    Os.lseek(descriptor.fileDescriptor, 0L, OsConstants.SEEK_CUR)
                    if (descriptor.statSize < 0L) {
                        return@withContext FileEmbeddedLyricsReader.failure(key, LyricsSourceIssue.Kind.UNSUPPORTED_ACCESS)
                    }
                } catch (cause: android.system.ErrnoException) {
                    diagnostics.onFailure(stage, cause)
                    return@withContext FileEmbeddedLyricsReader.failure(key, LyricsSourceIssue.Kind.UNSUPPORTED_ACCESS)
                }
                // The path-based tag readers reopen the inode while this borrowed fd stays alive.
                // A seekable fd grant alone cannot authorize reopening another app's private data.
                fileReader.read(File("/proc/self/fd/${descriptor.fd}"), key, extension, diagnostics) { context.ensureActive() }
            } ?: FileEmbeddedLyricsReader.failure(key, LyricsSourceIssue.Kind.READ_FAILURE)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cause: SecurityException) {
            diagnostics.onFailure(stage, cause)
            FileEmbeddedLyricsReader.failure(key, LyricsSourceIssue.Kind.PERMISSION_DENIED)
        } catch (cause: Exception) {
            diagnostics.onFailure(stage, cause)
            FileEmbeddedLyricsReader.failure(key, LyricsSourceIssue.Kind.READ_FAILURE)
        }
    }
}
