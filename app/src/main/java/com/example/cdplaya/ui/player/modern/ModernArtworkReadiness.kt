package com.example.cdplaya.ui.player.modern

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import com.example.cdplaya.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal const val MODERN_MINI_ARTWORK_REFERENCE_DP = 52f
internal const val MAX_MODERN_ARTWORK_PRELOAD_TARGET_PX = 2_048
internal const val MAX_MODERN_ARTWORK_PRELOAD_COUNT = 3

internal data class ModernArtworkPreloadPolicy(
    val targetSizePx: Int?,
    val exactSize: Boolean
)

internal fun modernArtworkPreloadPolicy(
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    density: Float,
    appearance: ModernPlayerAppearance
): ModernArtworkPreloadPolicy {
    if (viewportWidthPx <= 1 || viewportHeightPx <= 1 || density <= 0f) {
        return ModernArtworkPreloadPolicy(targetSizePx = null, exactSize = false)
    }

    val widthDp = viewportWidthPx / density
    val heightDp = viewportHeightPx / density
    val seekbarHeightBudgetDp = if (appearance.seekbar.style.usesWaveformData) {
        appearance.seekbar.waveformSize.trackHeightDp + 36f
    } else {
        64f
    }
    val reservedContentHeightDp = 210f +
        appearance.controls.size.primarySizeDp +
        seekbarHeightBudgetDp +
        appearance.layout.density.minimumFlexibleGapDp +
        if (appearance.layout.showAudioQualityBadge) 36f else 0f
    val artworkHeightBudgetDp = (heightDp - reservedContentHeightDp).coerceAtLeast(112f)
    val targetDp = minOf(
        ModernPlayerDefaults.MaximumArtworkSize.value *
            appearance.artwork.size.maximumScale,
        (widthDp - 32f).coerceAtLeast(1f),
        heightDp * appearance.artwork.size.maximumHeightFraction,
        artworkHeightBudgetDp
    )
    val targetPx = (targetDp * density)
        .roundToInt()
        .coerceIn(1, MAX_MODERN_ARTWORK_PRELOAD_TARGET_PX)

    return ModernArtworkPreloadPolicy(
        targetSizePx = targetPx,
        exactSize = true
    )
}

internal fun modernExpandedArtworkMemoryCacheKey(
    artworkIdentity: String,
    targetSizePx: Int
): String = "modern-expanded:$targetSizePx:$artworkIdentity"

internal fun selectModernArtworkPreloadSongs(
    currentSong: Song,
    previousSong: Song?,
    nextSong: Song?,
    includeCurrentSong: Boolean = true
): List<Song> = listOfNotNull(
    currentSong.takeIf { includeCurrentSong },
    nextSong,
    previousSong
)
    .filter { modernArtworkPaletteCacheKey(it) != null }
    .distinctBy(::modernArtworkPaletteCacheKey)
    .take(MAX_MODERN_ARTWORK_PRELOAD_COUNT)

@Composable
internal fun ModernExpandedArtworkPreloader(
    currentSong: Song,
    previousSong: Song?,
    nextSong: Song?,
    targetSizePx: Int?,
    includeCurrentSong: Boolean
) {
    val context = LocalContext.current.applicationContext
    val imageLoader = context.imageLoader
    val preloadSongs = remember(
        currentSong.id,
        currentSong.albumArtUri,
        previousSong?.id,
        previousSong?.albumArtUri,
        nextSong?.id,
        nextSong?.albumArtUri,
        includeCurrentSong
    ) {
        selectModernArtworkPreloadSongs(
            currentSong = currentSong,
            previousSong = previousSong,
            nextSong = nextSong,
            includeCurrentSong = includeCurrentSong
        )
    }

    LaunchedEffect(imageLoader, targetSizePx, preloadSongs) {
        val target = targetSizePx?.takeIf { it > 0 } ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            suspend fun preload(song: Song) {
                val artworkIdentity = modernArtworkPaletteCacheKey(song)
                    ?: return
                imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(song.albumArtUri)
                        .size(target)
                        .precision(Precision.EXACT)
                        .memoryCacheKey(
                            modernExpandedArtworkMemoryCacheKey(
                                artworkIdentity = artworkIdentity,
                                targetSizePx = target
                            )
                        )
                        .build()
                )
            }

            val currentIdentity = modernArtworkPaletteCacheKey(currentSong)
            val currentPreload = preloadSongs.firstOrNull { song ->
                modernArtworkPaletteCacheKey(song) == currentIdentity
            }
            currentPreload?.let { preload(it) }

            val neighbors = preloadSongs.filterNot { it === currentPreload }
            if (neighbors.isNotEmpty()) {
                delay(ModernPlayerDefaults.BackgroundTransitionDurationMillis.toLong())
                neighbors.forEach { preload(it) }
            }
        }
    }
}

internal enum class ModernArtworkQuality {
    Temporary,
    Expanded
}

internal data class ModernArtworkReadyLayer<T>(
    val artworkIdentity: String,
    val quality: ModernArtworkQuality,
    val value: T
)

internal data class ModernArtworkReadinessState<T>(
    val currentArtworkIdentity: String?,
    val temporary: ModernArtworkReadyLayer<T>? = null,
    val expanded: ModernArtworkReadyLayer<T>? = null
)

internal fun <T> acceptModernArtworkReadyLayer(
    state: ModernArtworkReadinessState<T>,
    layer: ModernArtworkReadyLayer<T>
): ModernArtworkReadinessState<T> {
    if (layer.artworkIdentity != state.currentArtworkIdentity) return state
    return when (layer.quality) {
        ModernArtworkQuality.Temporary -> state.copy(temporary = layer)
        ModernArtworkQuality.Expanded -> state.copy(expanded = layer)
    }
}

internal fun <T> preferredModernArtworkReadyLayer(
    state: ModernArtworkReadinessState<T>
): ModernArtworkReadyLayer<T>? = state.expanded ?: state.temporary
