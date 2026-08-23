package com.example.cdplaya.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.TagEditorRepository
import com.example.cdplaya.player.audioquality.AudioQualityInfo
import com.example.cdplaya.player.audioquality.AudioQualityRepository
import com.example.cdplaya.player.audioquality.normalizeAudioFormat
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal data class AlbumPresentationMetadata(
    val releaseYear: Int? = null,
    val audioQuality: AlbumAudioQualitySummary? = null,
    val artworkAccentArgb: Int? = null
)

internal data class AlbumAudioQualitySummary(
    val formatLabel: String?,
    val qualityLabel: String?
)

internal class AlbumPresentationMetadataRepository(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val audioQualityRepository = AudioQualityRepository(appContext)
    private val tagEditorRepository = TagEditorRepository()

    suspend fun loadAlbumMetadata(album: LibraryAlbumGroup): AlbumPresentationMetadata =
        coroutineScope {
            val releaseYear = async { getReleaseYear(album) }
            val audioQuality = async { getAudioQualitySummary(album.songs) }
            val artworkAccent = async {
                getArtworkAccentArgb(album.songs.firstOrNull()?.albumArtUri)
            }

            AlbumPresentationMetadata(
                releaseYear = releaseYear.await(),
                audioQuality = audioQuality.await(),
                artworkAccentArgb = artworkAccent.await()
            )
        }

    suspend fun getReleaseYears(
        albums: List<LibraryAlbumGroup>
    ): Map<String, Int?> = coroutineScope {
        albums.map { album ->
            async {
                album.key to getReleaseYear(album)
            }
        }.awaitAll().toMap()
    }

    private suspend fun getReleaseYear(album: LibraryAlbumGroup): Int? {
        for (song in album.songs) {
            val year = getReleaseYear(song)
            if (year != null) return year
        }
        return null
    }

    private suspend fun getReleaseYear(song: Song): Int? = withContext(Dispatchers.IO) {
        val cacheKey = songMetadataCacheKey(song)
        synchronized(releaseYearCache) {
            releaseYearCache[cacheKey]
        }?.let { cached ->
            return@withContext cached.takeUnless { it == NO_RELEASE_YEAR }
        }

        val metadataYear = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, song.uri)
                parseReleaseYear(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ) ?: parseReleaseYear(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                )
            } finally {
                retriever.release()
            }
        }.getOrNull()

        val resolvedYear = metadataYear ?: runCatching {
            parseReleaseYear(tagEditorRepository.readTags(song).year)
        }.getOrNull()

        synchronized(releaseYearCache) {
            releaseYearCache[cacheKey] = resolvedYear ?: NO_RELEASE_YEAR
        }
        resolvedYear
    }

    private suspend fun getAudioQualitySummary(
        songs: List<Song>
    ): AlbumAudioQualitySummary? = coroutineScope {
        if (songs.isEmpty()) return@coroutineScope null

        val qualityInfo = songs.map { song ->
            async {
                audioQualityRepository.getAudioQualityInfo(song)
            }
        }.awaitAll()

        summarizeAlbumAudioQuality(qualityInfo)
    }

    private suspend fun getArtworkAccentArgb(artworkUri: Uri?): Int? {
        artworkUri ?: return null
        val cacheKey = artworkUri.toString()
        synchronized(artworkAccentCache) {
            artworkAccentCache[cacheKey]
        }?.let { cached ->
            return cached.takeUnless { it == NO_ARTWORK_COLOR }
        }

        val extractedColor = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(appContext)
                    .data(artworkUri)
                    .allowHardware(false)
                    .size(ARTWORK_SAMPLE_SIZE)
                    .build()
                val result = appContext.imageLoader.execute(request) as? SuccessResult
                    ?: return@runCatching null
                val bitmap = result.drawable.toBitmap(
                    width = ARTWORK_SAMPLE_SIZE,
                    height = ARTWORK_SAMPLE_SIZE,
                    config = Bitmap.Config.ARGB_8888
                )
                extractArtworkAccent(bitmap)
            }.getOrNull()
        }

        synchronized(artworkAccentCache) {
            artworkAccentCache[cacheKey] = extractedColor ?: NO_ARTWORK_COLOR
        }
        return extractedColor
    }

    private companion object {
        private const val NO_RELEASE_YEAR = -1
        private const val NO_ARTWORK_COLOR = 0
        private const val ARTWORK_SAMPLE_SIZE = 72
        private const val MAX_METADATA_CACHE_ENTRIES = 256
        private const val MAX_ARTWORK_CACHE_ENTRIES = 96

        private val releaseYearCache = object :
            LinkedHashMap<String, Int>(MAX_METADATA_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Int>?
            ): Boolean = size > MAX_METADATA_CACHE_ENTRIES
        }

        private val artworkAccentCache = object :
            LinkedHashMap<String, Int>(MAX_ARTWORK_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Int>?
            ): Boolean = size > MAX_ARTWORK_CACHE_ENTRIES
        }
    }
}

internal fun albumPresentationMetadataKey(album: LibraryAlbumGroup): String = buildString {
    append(album.key)
    album.songs.forEach { song ->
        append('|')
        append(song.id)
        append(':')
        append(song.dateModifiedEpochSeconds)
        append(':')
        append(song.fileSizeBytes)
    }
}

internal fun summarizeAlbumAudioQuality(
    qualityInfo: List<AudioQualityInfo>
): AlbumAudioQualitySummary? {
    val formats = qualityInfo
        .mapNotNull { info -> normalizeAudioFormat(info.format) }
        .distinct()

    val bitDepths = qualityInfo
        .mapNotNull { info -> info.bitDepth?.takeIf { it > 0 } }
        .distinct()

    val sampleRates = qualityInfo
        .mapNotNull { info -> info.sampleRateHz?.takeIf { it > 0 } }
        .distinct()

    val formatLabel = when (formats.size) {
        0 -> null
        1 -> formats.first()
        else -> "MIXED FORMATS"
    }

    val qualityLabel = when {
        bitDepths.size > 1 || sampleRates.size > 1 -> "Mixed quality"
        bitDepths.isEmpty() && sampleRates.isEmpty() -> null
        else -> buildList {
            bitDepths.singleOrNull()?.let { bitDepth ->
                add("$bitDepth-bit")
            }
            sampleRates.singleOrNull()?.let { sampleRate ->
                add(formatSampleRate(sampleRate))
            }
        }.joinToString(separator = " • ").ifBlank { null }
    }

    if (formatLabel == null && qualityLabel == null) return null
    return AlbumAudioQualitySummary(
        formatLabel = formatLabel,
        qualityLabel = qualityLabel
    )
}

internal fun parseReleaseYear(rawValue: String?): Int? {
    val value = rawValue?.trim().orEmpty()
    if (value.isBlank()) return null

    val match = releaseYearPattern.find(value) ?: return null
    return match.value.toIntOrNull()?.takeIf { year -> year in 1000..2999 }
}

private fun formatSampleRate(sampleRateHz: Int): String {
    val kilohertz = sampleRateHz / 1_000.0
    val displayValue = if (sampleRateHz % 1_000 == 0) {
        (sampleRateHz / 1_000).toString()
    } else {
        String.format(java.util.Locale.ROOT, "%.1f", kilohertz)
            .trimEnd('0')
            .trimEnd('.')
    }
    return "$displayValue kHz"
}

private fun songMetadataCacheKey(song: Song): String = buildString {
    append(song.id)
    append('|')
    append(song.uri)
    append('|')
    append(song.dateModifiedEpochSeconds)
    append('|')
    append(song.fileSizeBytes)
}

private fun extractArtworkAccent(bitmap: Bitmap): Int? {
    data class Bucket(
        var count: Int = 0,
        var redTotal: Long = 0L,
        var greenTotal: Long = 0L,
        var blueTotal: Long = 0L,
        var saturationTotal: Float = 0f,
        var valueTotal: Float = 0f
    )

    val buckets = mutableMapOf<Int, Bucket>()
    val hsv = FloatArray(3)
    var fallbackCount = 0
    var fallbackRed = 0L
    var fallbackGreen = 0L
    var fallbackBlue = 0L

    for (y in 0 until bitmap.height step 2) {
        for (x in 0 until bitmap.width step 2) {
            val color = bitmap.getPixel(x, y)
            if (AndroidColor.alpha(color) < 160) continue

            val red = AndroidColor.red(color)
            val green = AndroidColor.green(color)
            val blue = AndroidColor.blue(color)
            fallbackCount++
            fallbackRed += red
            fallbackGreen += green
            fallbackBlue += blue

            AndroidColor.RGBToHSV(red, green, blue, hsv)
            val saturation = hsv[1]
            val value = hsv[2]

            if (value < 0.10f || value > 0.96f) continue

            val hueBucket = (hsv[0] / 15f).toInt().coerceIn(0, 23)
            val saturationBucket = (saturation * 4f).toInt().coerceIn(0, 3)
            val valueBucket = (value * 4f).toInt().coerceIn(0, 3)
            val key = hueBucket * 100 + saturationBucket * 10 + valueBucket
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.count++
            bucket.redTotal += red
            bucket.greenTotal += green
            bucket.blueTotal += blue
            bucket.saturationTotal += saturation
            bucket.valueTotal += value
        }
    }

    val bestBucket = buckets.values.maxByOrNull { bucket ->
        if (bucket.count == 0) return@maxByOrNull 0f
        val saturation = bucket.saturationTotal / bucket.count
        val value = bucket.valueTotal / bucket.count
        val middleToneBonus = 1f - abs(value - 0.55f) * 0.45f
        bucket.count * (0.45f + saturation) * middleToneBonus
    }

    if (bestBucket != null && bestBucket.count > 0) {
        return AndroidColor.rgb(
            (bestBucket.redTotal / bestBucket.count).toInt(),
            (bestBucket.greenTotal / bestBucket.count).toInt(),
            (bestBucket.blueTotal / bestBucket.count).toInt()
        )
    }

    if (fallbackCount <= 0) return null
    return AndroidColor.rgb(
        (fallbackRed / fallbackCount).toInt(),
        (fallbackGreen / fallbackCount).toInt(),
        (fallbackBlue / fallbackCount).toInt()
    )
}

private val releaseYearPattern = Regex("""(?<!\\d)(?:1\\d{3}|2\\d{3})(?!\\d)""")
