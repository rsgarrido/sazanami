package io.github.rsgarrido.sazanami.ui.player.modern

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.github.rsgarrido.sazanami.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ModernArtworkPalette(
    val dominant: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val readableForeground: Color,
    val isFallback: Boolean = false
) {
    companion object {
        fun fallback(appAccent: Color): ModernArtworkPalette {
            val safeAccent = ensureReadableModernAccent(appAccent, Color.White)
            return ModernArtworkPalette(
                dominant = blendArgb(safeAccent.toArgb(), 0xFF000000.toInt(), 0.68f).asColor(),
                primary = safeAccent,
                secondary = blendArgb(safeAccent.toArgb(), 0xFFFFFFFF.toInt(), 0.22f).asColor(),
                accent = safeAccent,
                readableForeground = Color.White,
                isFallback = true
            )
        }
    }
}

data class ModernAlbumGradientColors(
    val top: Color,
    val center: Color,
    val bottom: Color,
    val usedArtworkPalette: Boolean
)

internal fun modernArtworkPaletteCacheKey(song: Song): String? =
    song.albumArtUri?.toString()?.takeIf(String::isNotBlank)

internal class BoundedArtworkPaletteCache(
    private val maximumSize: Int
) {
    private val values = object : LinkedHashMap<String, ModernArtworkPalette>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, ModernArtworkPalette>?
        ): Boolean = size > maximumSize
    }
    private val failures = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Boolean>?
        ): Boolean = size > maximumSize
    }

    @Synchronized
    fun get(key: String): ModernArtworkPalette? = values[key]

    @Synchronized
    fun put(key: String, palette: ModernArtworkPalette) {
        failures.remove(key)
        values[key] = palette
    }

    @Synchronized
    fun markFailure(key: String) {
        values.remove(key)
        failures[key] = true
    }

    @Synchronized
    fun hasFailed(key: String): Boolean = failures[key] == true

    @Synchronized
    internal fun cachedKeys(): Set<String> = values.keys.toSet()
}

internal class ModernArtworkPaletteRepository private constructor(
    private val context: Context,
    private val cache: BoundedArtworkPaletteCache = BoundedArtworkPaletteCache(MAX_CACHE_SIZE)
) {
    fun cachedOrFailedPalette(
        artworkKey: String,
        fallbackAccent: Color
    ): ModernArtworkPalette? = cache.get(artworkKey)
        ?: if (cache.hasFailed(artworkKey)) {
            ModernArtworkPalette.fallback(fallbackAccent)
        } else {
            null
        }

    suspend fun load(song: Song, fallbackAccent: Color): ModernArtworkPalette {
        val key = modernArtworkPaletteCacheKey(song)
            ?: return ModernArtworkPalette.fallback(fallbackAccent)
        cache.get(key)?.let { return it }
        if (cache.hasFailed(key)) return ModernArtworkPalette.fallback(fallbackAccent)

        return withContext(Dispatchers.IO) {
            cache.get(key)?.let { return@withContext it }
            val palette = runCatching {
                val request = ImageRequest.Builder(context)
                    .data(song.albumArtUri)
                    .size(PALETTE_BITMAP_SIZE)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request) as? SuccessResult
                    ?: error("Artwork request did not produce a bitmap")
                val bitmap = result.drawable.toBitmap(
                    width = PALETTE_BITMAP_SIZE,
                    height = PALETTE_BITMAP_SIZE,
                    config = Bitmap.Config.ARGB_8888
                )
                extractModernArtworkPalette(bitmap, fallbackAccent)
            }.getOrNull()

            if (palette == null) {
                cache.markFailure(key)
                ModernArtworkPalette.fallback(fallbackAccent)
            } else {
                cache.put(key, palette)
                palette
            }
        }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 64
        private const val PALETTE_BITMAP_SIZE = 72
        @Volatile private var instance: ModernArtworkPaletteRepository? = null

        fun shared(context: Context): ModernArtworkPaletteRepository =
            instance ?: synchronized(this) {
                instance ?: ModernArtworkPaletteRepository(context.applicationContext)
                    .also { instance = it }
            }
    }
}

internal data class ModernArtworkPaletteRequest(
    val id: Long,
    val artworkKey: String?
)

internal data class ModernArtworkPaletteLoadState(
    val request: ModernArtworkPaletteRequest?,
    val displayedPalette: ModernArtworkPalette,
    val isLoading: Boolean
)

internal fun beginModernArtworkPaletteRequest(
    previousState: ModernArtworkPaletteLoadState,
    request: ModernArtworkPaletteRequest,
    immediatePalette: ModernArtworkPalette?,
    fallbackPalette: ModernArtworkPalette
): ModernArtworkPaletteLoadState = when {
    request.artworkKey == null -> ModernArtworkPaletteLoadState(
        request = request,
        displayedPalette = fallbackPalette,
        isLoading = false
    )
    immediatePalette != null -> ModernArtworkPaletteLoadState(
        request = request,
        displayedPalette = immediatePalette,
        isLoading = false
    )
    else -> ModernArtworkPaletteLoadState(
        request = request,
        displayedPalette = previousState.displayedPalette,
        isLoading = true
    )
}

internal fun completeModernArtworkPaletteRequest(
    currentState: ModernArtworkPaletteLoadState,
    request: ModernArtworkPaletteRequest,
    resolvedPalette: ModernArtworkPalette
): ModernArtworkPaletteLoadState = if (currentState.request == request) {
    ModernArtworkPaletteLoadState(
        request = request,
        displayedPalette = resolvedPalette,
        isLoading = false
    )
} else {
    currentState
}

@Composable
internal fun rememberModernArtworkPalette(
    song: Song?,
    fallbackAccent: Color
): ModernArtworkPalette {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { ModernArtworkPaletteRepository.shared(context) }
    val cacheKey = song?.let(::modernArtworkPaletteCacheKey)
    val fallbackPalette = remember(fallbackAccent) {
        ModernArtworkPalette.fallback(fallbackAccent)
    }
    val requestCounter = remember { longArrayOf(0L) }
    var loadState by remember(fallbackAccent) {
        mutableStateOf(
            ModernArtworkPaletteLoadState(
                request = null,
                displayedPalette = fallbackPalette,
                isLoading = false
            )
        )
    }

    LaunchedEffect(cacheKey, fallbackAccent) {
        requestCounter[0] += 1L
        val request = ModernArtworkPaletteRequest(
            id = requestCounter[0],
            artworkKey = cacheKey
        )
        val immediatePalette = cacheKey?.let { artworkKey ->
            repository.cachedOrFailedPalette(artworkKey, fallbackAccent)
        }
        loadState = beginModernArtworkPaletteRequest(
            previousState = loadState,
            request = request,
            immediatePalette = immediatePalette,
            fallbackPalette = fallbackPalette
        )
        if (song != null && cacheKey != null && immediatePalette == null) {
            val resolvedPalette = repository.load(song, fallbackAccent)
            loadState = completeModernArtworkPaletteRequest(
                currentState = loadState,
                request = request,
                resolvedPalette = resolvedPalette
            )
        }
    }

    return loadState.displayedPalette
}

internal fun extractModernArtworkPalette(
    bitmap: Bitmap,
    fallbackAccent: Color
): ModernArtworkPalette {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return extractModernArtworkPalette(pixels, fallbackAccent)
}

internal fun extractModernArtworkPalette(
    pixels: IntArray,
    fallbackAccent: Color
): ModernArtworkPalette {
    val buckets = linkedMapOf<Int, PaletteBucket>()
    pixels.forEach { argb ->
        val alpha = argb ushr 24 and 0xFF
        if (alpha < 128) return@forEach
        val red = argb ushr 16 and 0xFF
        val green = argb ushr 8 and 0xFF
        val blue = argb and 0xFF
        val key = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
        buckets.getOrPut(key, ::PaletteBucket).add(red, green, blue)
    }
    if (buckets.isEmpty()) return ModernArtworkPalette.fallback(fallbackAccent)

    val candidates = buckets.values.map { bucket ->
        PaletteCandidate(color = bucket.averageArgb(), population = bucket.count)
    }
    val dominant = candidates.maxBy { candidate -> candidate.population }.color
    val primary = candidates.maxBy { candidate ->
        val saturation = colorSaturation(candidate.color)
        val luminance = colorLuminance(candidate.color)
        val midtoneBonus = 1f - kotlin.math.abs(luminance - 0.5f) * 0.35f
        sqrt(candidate.population.toFloat()) * (0.28f + saturation * 1.35f) * midtoneBonus
    }.color
    val secondary = candidates
        .asSequence()
        .map(PaletteCandidate::color)
        .filter { color -> colorDistanceSquared(color, primary) >= MIN_SECONDARY_DISTANCE_SQUARED }
        .maxByOrNull { color -> colorSaturation(color) + colorLuminance(color) * 0.25f }
        ?: blendArgb(primary, dominant, 0.5f)
    val accent = primary.asColor()
    val foreground = if (colorLuminance(dominant) > 0.56f) Color.Black else Color.White

    return ModernArtworkPalette(
        dominant = dominant.asColor(),
        primary = primary.asColor(),
        secondary = secondary.asColor(),
        accent = accent,
        readableForeground = foreground
    )
}

internal fun resolveModernAlbumGradient(
    palette: ModernArtworkPalette?,
    fallbackAccent: Color
): ModernAlbumGradientColors {
    val resolved = palette ?: ModernArtworkPalette.fallback(fallbackAccent)
    val accent = resolveModernAlbumAccent(palette, fallbackAccent)
    return ModernAlbumGradientColors(
        top = blendColors(accent, Color.Black, 0.20f),
        center = blendColors(resolved.secondary, accent, 0.38f),
        bottom = blendColors(resolved.dominant, Color.Black, 0.72f),
        usedArtworkPalette = !resolved.isFallback
    )
}

internal fun resolveModernAlbumAccent(
    palette: ModernArtworkPalette?,
    fallbackAccent: Color
): Color = if (palette == null || palette.isFallback) {
    fallbackAccent
} else {
    adjustModernArtworkDerivedAccent(palette.accent)
}

internal fun ensureReadableModernAccent(color: Color, fallback: Color): Color {
    if (color == Color.Unspecified || color.alpha < 0.5f) return fallback
    return adjustModernArtworkDerivedAccent(color)
}

internal fun adjustModernArtworkDerivedAccent(color: Color): Color {
    val hsv = modernArgbToHsv(color.toArgb().toUInt().toLong())
    val adjustedSaturation = when {
        hsv.saturation < 0.04f -> hsv.saturation
        hsv.saturation < 0.18f -> (hsv.saturation + 0.10f).coerceAtMost(0.24f)
        else -> hsv.saturation.coerceAtMost(0.88f)
    }
    val adjustedValue = when {
        hsv.value < 0.28f -> 0.42f
        hsv.value > 0.88f -> 0.78f
        else -> hsv.value.coerceIn(0.34f, 0.84f)
    }
    return modernHsvToArgb(
        hsv.copy(
            saturation = adjustedSaturation,
            value = adjustedValue
        )
    ).toInt().asColor()
}

internal fun modernContrastingForeground(background: Color): Color =
    if (colorLuminance(background.toArgb()) > 0.52f) Color.Black else Color.White

internal fun modernSolidColorReadabilityScrimAlpha(argb: Long): Float {
    val luminance = colorLuminance(sanitizeModernSolidColorArgb(argb).toInt())
    return when {
        luminance >= 0.72f -> 0.52f
        luminance >= 0.48f -> 0.36f
        luminance >= 0.28f -> 0.20f
        else -> 0.08f
    }
}

private data class PaletteBucket(
    var count: Int = 0,
    var redTotal: Long = 0,
    var greenTotal: Long = 0,
    var blueTotal: Long = 0
) {
    fun add(red: Int, green: Int, blue: Int) {
        count += 1
        redTotal += red
        greenTotal += green
        blueTotal += blue
    }

    fun averageArgb(): Int {
        val divisor = max(1, count)
        return (0xFF shl 24) or
                ((redTotal / divisor).toInt() shl 16) or
                ((greenTotal / divisor).toInt() shl 8) or
                (blueTotal / divisor).toInt()
    }
}

private data class PaletteCandidate(
    val color: Int,
    val population: Int
)

private fun colorDistanceSquared(first: Int, second: Int): Int {
    val red = (first ushr 16 and 0xFF) - (second ushr 16 and 0xFF)
    val green = (first ushr 8 and 0xFF) - (second ushr 8 and 0xFF)
    val blue = (first and 0xFF) - (second and 0xFF)
    return red * red + green * green + blue * blue
}

private fun colorSaturation(argb: Int): Float {
    val red = (argb ushr 16 and 0xFF) / 255f
    val green = (argb ushr 8 and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    val maximum = max(red, max(green, blue))
    val minimum = min(red, min(green, blue))
    return if (maximum <= 0f) 0f else (maximum - minimum) / maximum
}

private fun colorLuminance(argb: Int): Float {
    fun channel(value: Int): Float {
        val normalized = value / 255f
        return if (normalized <= 0.04045f) {
            normalized / 12.92f
        } else {
            Math.pow(((normalized + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
    }
    return channel(argb ushr 16 and 0xFF) * 0.2126f +
            channel(argb ushr 8 and 0xFF) * 0.7152f +
            channel(argb and 0xFF) * 0.0722f
}

internal fun blendColors(first: Color, second: Color, amount: Float): Color =
    blendArgb(first.toArgb(), second.toArgb(), amount).asColor()

private fun blendArgb(first: Int, second: Int, amount: Float): Int {
    val fraction = amount.coerceIn(0f, 1f)
    fun channel(shift: Int): Int {
        val start = first ushr shift and 0xFF
        val end = second ushr shift and 0xFF
        return (start + (end - start) * fraction).toInt().coerceIn(0, 255)
    }
    return (channel(24) shl 24) or
            (channel(16) shl 16) or
            (channel(8) shl 8) or
            channel(0)
}

private fun Int.asColor(): Color = Color(this)

private const val MIN_SECONDARY_DISTANCE_SQUARED = 3_600
