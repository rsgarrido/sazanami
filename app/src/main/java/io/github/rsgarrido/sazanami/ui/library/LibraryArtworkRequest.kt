package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import io.github.rsgarrido.sazanami.data.visual.VisualAssetRequestPolicy
import io.github.rsgarrido.sazanami.data.visual.VisualAssetVariant

internal enum class LibraryArtworkOwnerType(val cacheNamespace: String) {
    ALBUM("album"),
    ARTIST_FALLBACK("artist-fallback")
}

internal fun libraryArtworkRequestPolicy(
    ownerType: LibraryArtworkOwnerType,
    ownerKey: String,
    modelIdentity: String?,
    variant: VisualAssetVariant
): VisualAssetRequestPolicy? {
    if (modelIdentity.isNullOrBlank()) return null

    val baseKey = "library-artwork:${ownerType.cacheNamespace}:$ownerKey:$modelIdentity"
    val thumbnailKey = "$baseKey:${VisualAssetVariant.THUMBNAIL.name.lowercase()}"
    return VisualAssetRequestPolicy(
        cacheKey = when (variant) {
            VisualAssetVariant.THUMBNAIL -> thumbnailKey
            VisualAssetVariant.DISPLAY ->
                "$baseKey:${VisualAssetVariant.DISPLAY.name.lowercase()}"
        },
        placeholderMemoryCacheKey = if (variant == VisualAssetVariant.DISPLAY) {
            thumbnailKey
        } else {
            null
        }
    )
}

@Composable
internal fun rememberLibraryArtworkRequest(
    ownerType: LibraryArtworkOwnerType,
    ownerKey: String,
    model: Any?,
    variant: VisualAssetVariant
): ImageRequest? {
    val context = LocalContext.current
    val policy = remember(ownerType, ownerKey, model, variant) {
        libraryArtworkRequestPolicy(
            ownerType = ownerType,
            ownerKey = ownerKey,
            modelIdentity = model?.toString(),
            variant = variant
        )
    }

    return remember(context, model, policy) {
        if (model == null || policy == null) return@remember null

        ImageRequest.Builder(context)
            .data(model)
            .memoryCacheKey(policy.cacheKey)
            .apply {
                policy.placeholderMemoryCacheKey?.let(::placeholderMemoryCacheKey)
            }
            .crossfade(false)
            .build()
    }
}
