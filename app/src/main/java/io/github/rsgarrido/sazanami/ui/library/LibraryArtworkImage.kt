package io.github.rsgarrido.sazanami.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import io.github.rsgarrido.sazanami.data.CURRENT_ARTWORK_ENRICHMENT_VERSION
import io.github.rsgarrido.sazanami.data.Song

/** A null URI remains provisional until artwork enrichment has checked the song. */
internal fun Song?.hasUnresolvedLibraryArtwork(): Boolean =
    this != null && albumArtUri == null &&
        artworkEnrichmentVersion < CURRENT_ARTWORK_ENRICHMENT_VERSION

/** Uses the existing Coil request, showing the missing-artwork visual only after resolution. */
@Composable
internal fun LibraryArtworkImage(
    model: Any?,
    unresolvedNull: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    neutralWhileLoading: Boolean = true,
    fallback: @Composable BoxScope.() -> Unit
) {
    var requestFailed by remember(model) { mutableStateOf(false) }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if ((model == null && !unresolvedNull) || requestFailed ||
            (model != null && !neutralWhileLoading)
        ) {
            fallback()
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onLoading = { requestFailed = false },
                onSuccess = { requestFailed = false },
                onError = { requestFailed = true }
            )
        }
    }
}
