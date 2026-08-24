package com.example.cdplaya.player.waveform

import android.content.Context
import android.provider.MediaStore
import com.example.cdplaya.data.Song
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

fun interface WaveformSourceResolver {
    fun resolve(song: Song): WaveformSource
}

class AndroidWaveformSourceResolver(
    context: Context
) : WaveformSourceResolver {
    private val appContext = context.applicationContext

    override fun resolve(song: Song): WaveformSource {
        val audioFile = File(song.filePath)
        if (audioFile.isFile) {
            return WaveformSource(
                songId = song.id,
                filePath = audioFile.absolutePath,
                lastModified = audioFile.lastModified(),
                fileLength = audioFile.length()
            )
        }

        var lastModified = 0L
        var fileLength = 0L
        runCatching {
            appContext.contentResolver.query(
                song.uri,
                arrayOf(MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val modifiedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (modifiedColumn >= 0 && !cursor.isNull(modifiedColumn)) {
                        lastModified = cursor.getLong(modifiedColumn) * 1_000L
                    }
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                        fileLength = cursor.getLong(sizeColumn)
                    }
                }
            }
        }

        return WaveformSource(
            songId = song.id,
            filePath = song.filePath.ifBlank { song.uri.toString() },
            lastModified = lastModified,
            fileLength = fileLength
        )
    }
}

class WaveformRepository internal constructor(
    private val cache: WaveformCache,
    private val analyzer: WaveformAnalyzer,
    private val sourceResolver: WaveformSourceResolver,
    private val ioDispatcher: CoroutineDispatcher
) {
    constructor(context: Context) : this(
        cache = WaveformCache(File(context.cacheDir, WAVEFORM_CACHE_DIRECTORY)),
        analyzer = NativeWaveformAnalyzer(context),
        sourceResolver = AndroidWaveformSourceResolver(context),
        ioDispatcher = Dispatchers.IO
    )

    private val loadMutex = Mutex()

    suspend fun load(song: Song): WaveformData? = withContext(ioDispatcher) {
        val sourceKey = waveformCacheKey(sourceResolver.resolve(song))
        loadMutex.withLock {
            cache.read(sourceKey)?.let { return@withLock it }

            val analyzed = analyzer.analyze(
                song = song,
                sourceKey = sourceKey,
                barCount = DEFAULT_ANALYZED_BAR_COUNT
            ) ?: return@withLock null
            val amplitudes = analyzed.amplitudes
                .takeIf(List<Float>::isNotEmpty)
                ?.map { amplitude ->
                    if (amplitude.isFinite()) amplitude.coerceIn(0f, 1f) else 0f
                }
                ?: return@withLock null
            val data = WaveformData(amplitudes = amplitudes, sourceKey = sourceKey)
            cache.write(data)
            cache.maintain { coroutineContext.ensureActive() }
            data
        }
    }

    suspend fun getCacheStats(): WaveformCacheStats = withContext(ioDispatcher) {
        cache.getStats()
    }

    suspend fun clearDiskCache(): WaveformCacheStats = withContext(ioDispatcher) {
        loadMutex.withLock { cache.clear { coroutineContext.ensureActive() } }
    }

    suspend fun maintainDiskCache(): WaveformCacheStats = withContext(ioDispatcher) {
        loadMutex.withLock { cache.maintain { coroutineContext.ensureActive() } }
    }

    suspend fun prefetch(songs: List<Song>) {
        songs.asSequence()
            .distinctBy { song -> song.id to song.filePath }
            .take(MAX_PREFETCH_COUNT)
            .forEach { song ->
                coroutineContext.ensureActive()
                try {
                    load(song)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Prefetch is opportunistic. A normal current-song request can retry later.
                }
                yield()
            }
    }

    companion object {
        const val DEFAULT_ANALYZED_BAR_COUNT = 512
        const val MAX_PREFETCH_COUNT = 2
        const val WAVEFORM_CACHE_DIRECTORY = "waveforms"

        @Volatile
        private var sharedInstance: WaveformRepository? = null

        fun shared(context: Context): WaveformRepository {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: WaveformRepository(context.applicationContext).also { repository ->
                    sharedInstance = repository
                }
            }
        }
    }
}
