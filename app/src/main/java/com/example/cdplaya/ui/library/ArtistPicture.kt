package com.example.cdplaya.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.cdplaya.data.ArtistIdentity
import com.example.cdplaya.data.ArtistPictureAssignment
import com.example.cdplaya.data.visual.VisualAssetIdentity
import com.example.cdplaya.data.visual.VisualAssetOwnerType
import com.example.cdplaya.data.visual.VisualAssetStore
import com.example.cdplaya.data.visual.VisualAssetVariant
import com.example.cdplaya.data.visual.requestPolicy
import com.example.cdplaya.ui.AppShellIcons

@Immutable
data class ArtistPictureUiEnvironment(
    val assignments: Map<String, ArtistPictureAssignment> = emptyMap(),
    val onChoosePicture: (ArtistIdentity) -> Unit = {},
    val onRemovePicture: (ArtistIdentity) -> Unit = {}
)

val LocalArtistPictureUi = staticCompositionLocalOf { ArtistPictureUiEnvironment() }

@Composable
fun ArtistPicture(
    identity: ArtistIdentity,
    fallbackModel: Any?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    variant: VisualAssetVariant = VisualAssetVariant.THUMBNAIL
) {
    val context = LocalContext.current
    val assignment = LocalArtistPictureUi.current.assignments[identity.key]
    val assetIdentity = remember(identity.key, assignment?.assetReference) {
        assignment?.let {
            VisualAssetIdentity(
                ownerType = VisualAssetOwnerType.ARTIST_IMAGE,
                ownerKey = identity.key,
                revision = it.assetReference
            )
        }
    }
    val store = remember(context) { VisualAssetStore(context) }
    val managedFile = remember(identity.key, assignment?.assetReference, variant) {
        assignment?.let {
            store.file(
                ownerType = VisualAssetOwnerType.ARTIST_IMAGE,
                ownerKey = identity.key,
                reference = it.assetReference,
                variant = variant
            )
        }
    }
    val displayFile = remember(identity.key, assignment?.assetReference) {
        assignment?.let {
            store.file(
                ownerType = VisualAssetOwnerType.ARTIST_IMAGE,
                ownerKey = identity.key,
                reference = it.assetReference,
                variant = VisualAssetVariant.DISPLAY
            )
        }
    }
    val model = preferredArtistPictureModel(managedFile, fallbackModel)
    val request = remember(model, assetIdentity, variant) {
        if (model == null) null else ImageRequest.Builder(context)
            .data(model)
            .apply {
                if (managedFile != null) {
                    assetIdentity?.requestPolicy(variant)?.let { policy ->
                        memoryCacheKey(policy.cacheKey)
                        diskCacheKey(policy.cacheKey)
                        policy.placeholderMemoryCacheKey?.let(::placeholderMemoryCacheKey)
                    }
                }
            }
            .crossfade(false)
            .build()
    }

    ArtistDisplayPrefetchEffect(
        model = displayFile,
        identity = assetIdentity,
        enabled = variant == VisualAssetVariant.THUMBNAIL
    )

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppShellIcons.AlbumStack,
            contentDescription = if (model == null) contentDescription else null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.42f)
        )
        AsyncImage(
            model = request,
            contentDescription = if (model != null) contentDescription else null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

internal fun preferredArtistPictureModel(managedModel: Any?, fallbackModel: Any?): Any? =
    managedModel ?: fallbackModel

@Composable
private fun ArtistDisplayPrefetchEffect(
    model: Any?,
    identity: VisualAssetIdentity?,
    enabled: Boolean
) {
    val context = LocalContext.current
    DisposableEffect(model, identity, enabled) {
        val disposable = if (enabled && model != null && identity != null) {
            val policy = identity.requestPolicy(VisualAssetVariant.DISPLAY)
            Coil.imageLoader(context).enqueue(
                ImageRequest.Builder(context)
                    .data(model)
                    .size(1024)
                    .memoryCacheKey(policy.cacheKey)
                    .diskCacheKey(policy.cacheKey)
                    .crossfade(false)
                    .build()
            )
        } else {
            null
        }
        onDispose { disposable?.dispose() }
    }
}
