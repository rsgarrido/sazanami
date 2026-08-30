package io.github.rsgarrido.sazanami.player.audioquality

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import io.github.rsgarrido.sazanami.data.Song
import java.io.File
import java.util.LinkedHashMap
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO

class AudioQualityRepository(context: Context) {
    private val appContext = context.applicationContext ?: context

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    suspend fun getAudioQualityInfo(song: Song): AudioQualityInfo =
        withContext(Dispatchers.IO) {
            val cacheKey = AudioQualityCacheKey(
                songId = song.id,
                contentUri = song.uri.toString(),
                dateModifiedEpochSeconds = song.dateModifiedEpochSeconds,
                fileSizeBytes = song.fileSizeBytes
            )
            synchronized(audioQualityCache) {
                audioQualityCache[cacheKey]
            }?.let { return@withContext it }

            extractionSemaphore.withPermit {
                synchronized(audioQualityCache) {
                    audioQualityCache[cacheKey]
                }?.let { return@withPermit it }

                val startedAt = SystemClock.elapsedRealtime()
                val loadedInfo = readAudioQualityInfo(song)
                synchronized(audioQualityCache) {
                    audioQualityCache[cacheKey] = loadedInfo
                }
                if (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    Log.d(
                        DEBUG_TAG,
                        "technical-format extraction elapsedMs=" +
                            "${SystemClock.elapsedRealtime() - startedAt} songId=${song.id} " +
                            "result=${loadedInfo.toDisplayText().orEmpty()}"
                    )
                }
                loadedInfo
            }
        }

    private fun readAudioQualityInfo(song: Song): AudioQualityInfo {
        val extension = song.displayName.substringAfterLast('.', missingDelimiterValue = "")
            .ifBlank { song.filePath.substringAfterLast('.', missingDelimiterValue = "") }
        val base = AudioQualityInfo(
            format = normalizeAudioFormat(extension),
            bitDepth = null,
            sampleRateHz = null,
            bitrateKbps = null
        )
        val tagInfo = readWithJAudioTagger(song, extension)
        val extractorInfo = readWithMediaExtractor(song, extension)
        val retrieverInfo = readBitrateWithMetadataRetriever(song)
        return mergeAudioQualityInfo(base, tagInfo, extractorInfo, retrieverInfo)
    }

    private fun readWithJAudioTagger(song: Song, extension: String): AudioQualityInfo? =
        runCatching {
            appContext.contentResolver.openFileDescriptor(song.uri, "r")?.use { descriptor ->
                val descriptorFile = File("/proc/self/fd/${descriptor.fd}")
                val audioHeader = if (extension.isNotBlank()) {
                    AudioFileIO.readAs(descriptorFile, extension).audioHeader
                } else {
                    AudioFileIO.readMagic(descriptorFile).audioHeader
                }
                AudioQualityInfo(
                    format = resolveAudioFormat(
                        headerFormat = audioHeader.format,
                        encodingType = audioHeader.encodingType,
                        fileExtension = extension
                    ),
                    bitDepth = audioHeader.bitsPerSample.takeIf { it > 0 },
                    sampleRateHz = audioHeader.sampleRateAsNumber.takeIf { it > 0 },
                    bitrateKbps = audioHeader.bitRateAsNumber
                        .takeIf { it in 1..Int.MAX_VALUE.toLong() }
                        ?.toInt()
                )
            }
        }.getOrNull()

    private fun readWithMediaExtractor(song: Song, extension: String): AudioQualityInfo? =
        runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(appContext, song.uri, null)
                val audioFormat = (0 until extractor.trackCount)
                    .map(extractor::getTrackFormat)
                    .firstOrNull { format ->
                        format.stringOrNull(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                    }
                    ?: return@runCatching null
                val mime = audioFormat.stringOrNull(MediaFormat.KEY_MIME)
                AudioQualityInfo(
                    format = normalizeAudioFormat(extension)
                        ?: normalizeAudioFormat(mime?.substringAfter('/')),
                    bitDepth = audioFormat.intOrNull(BITS_PER_SAMPLE_KEY)
                        ?.takeIf { it > 0 },
                    sampleRateHz = audioFormat.intOrNull(MediaFormat.KEY_SAMPLE_RATE)
                        ?.takeIf { it > 0 },
                    bitrateKbps = audioFormat.intOrNull(MediaFormat.KEY_BIT_RATE)
                        ?.takeIf { it > 0 }
                        ?.div(BITS_PER_KILOBIT)
                )
            } finally {
                extractor.release()
            }
        }.getOrNull()

    private fun readBitrateWithMetadataRetriever(song: Song): AudioQualityInfo? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, song.uri)
            val bitrateKbps = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_BITRATE
            )?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?.div(BITS_PER_KILOBIT)
                ?.takeIf { it <= Int.MAX_VALUE }
                ?.toInt()
            AudioQualityInfo(null, null, null, bitrateKbps)
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private fun MediaFormat.stringOrNull(key: String): String? =
        if (containsKey(key)) runCatching { getString(key) }.getOrNull() else null

    private data class AudioQualityCacheKey(
        val songId: Long,
        val contentUri: String,
        val dateModifiedEpochSeconds: Long,
        val fileSizeBytes: Long
    )

    private companion object {
        private const val MAX_CACHE_ENTRIES = 64
        private const val BITS_PER_KILOBIT = 1_000
        private const val BITS_PER_SAMPLE_KEY = "bits-per-sample"
        private const val DEBUG_TAG = "LibraryTiming"
        private val extractionSemaphore = Semaphore(permits = 2)

        private val audioQualityCache = object :
            LinkedHashMap<AudioQualityCacheKey, AudioQualityInfo>(
                MAX_CACHE_ENTRIES,
                0.75f,
                true
            ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<AudioQualityCacheKey, AudioQualityInfo>?
            ): Boolean = size > MAX_CACHE_ENTRIES
        }
    }
}

internal fun mergeAudioQualityInfo(
    vararg sources: AudioQualityInfo?
): AudioQualityInfo = AudioQualityInfo(
    format = sources.firstNotNullOfOrNull { normalizeAudioFormat(it?.format) },
    bitDepth = sources.firstNotNullOfOrNull { it?.bitDepth?.takeIf { value -> value > 0 } },
    sampleRateHz = sources.firstNotNullOfOrNull {
        it?.sampleRateHz?.takeIf { value -> value > 0 }
    },
    bitrateKbps = sources.firstNotNullOfOrNull {
        it?.bitrateKbps?.takeIf { value -> value > 0 }
    }
)
