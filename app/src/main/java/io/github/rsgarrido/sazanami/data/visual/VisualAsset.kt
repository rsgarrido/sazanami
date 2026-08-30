package io.github.rsgarrido.sazanami.data.visual

import java.security.MessageDigest
import kotlin.math.roundToInt

enum class VisualAssetOwnerType(val cacheNamespace: String) {
    PLAYLIST_IMAGE("playlist"),
    ARTIST_IMAGE("artist"),
    PLAYLIST_COLLAGE("playlist-collage")
}

enum class VisualAssetVariant(val fileName: String, val maximumDimensionPx: Int) {
    THUMBNAIL("thumbnail.webp", 384),
    DISPLAY("display.webp", 1440)
}

data class VisualAssetIdentity(
    val ownerType: VisualAssetOwnerType,
    val ownerKey: String,
    val revision: String
) {
    init {
        require(ownerKey.isNotBlank())
        require(revision.isNotBlank())
    }

    fun cacheKey(variant: VisualAssetVariant): String =
        "${ownerType.cacheNamespace}:$ownerKey:$revision:${variant.name.lowercase()}"
}

data class VisualAssetSize(val width: Int, val height: Int)

fun boundedVisualAssetSize(
    sourceWidth: Int,
    sourceHeight: Int,
    maximumDimensionPx: Int
): VisualAssetSize {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(maximumDimensionPx > 0)
    val sourceMax = maxOf(sourceWidth, sourceHeight)
    if (sourceMax <= maximumDimensionPx) return VisualAssetSize(sourceWidth, sourceHeight)
    val scale = maximumDimensionPx.toDouble() / sourceMax.toDouble()
    return VisualAssetSize(
        width = (sourceWidth * scale).roundToInt().coerceAtLeast(1),
        height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
    )
}

data class VisualAssetRequestPolicy(
    val cacheKey: String,
    val placeholderMemoryCacheKey: String?
)

fun VisualAssetIdentity.requestPolicy(variant: VisualAssetVariant): VisualAssetRequestPolicy =
    VisualAssetRequestPolicy(
        cacheKey = cacheKey(variant),
        placeholderMemoryCacheKey = if (variant == VisualAssetVariant.DISPLAY) {
            cacheKey(VisualAssetVariant.THUMBNAIL)
        } else {
            null
        }
    )

fun isCurrentVisualAssetRevision(expectedRevision: String, completedRevision: String): Boolean =
    expectedRevision == completedRevision

fun playlistCollageSignature(playlistId: Long, orderedArtworkIdentities: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("playlist:$playlistId\n".toByteArray())
    orderedArtworkIdentities.take(4).forEachIndexed { index, identity ->
        digest.update("$index:$identity\n".toByteArray())
    }
    return digest.digest().take(12).joinToString("") { byte -> "%02x".format(byte) }
}

class VisualAssetReplacementCoordinator {
    private val generations = mutableMapOf<String, Long>()

    @Synchronized
    fun begin(ownerKey: String): Long {
        val next = (generations[ownerKey] ?: 0L) + 1L
        generations[ownerKey] = next
        return next
    }

    @Synchronized
    fun isCurrent(ownerKey: String, generation: Long): Boolean =
        generations[ownerKey] == generation

    @Synchronized
    fun invalidate(ownerKey: String) {
        generations[ownerKey] = (generations[ownerKey] ?: 0L) + 1L
    }
}
