package io.github.rsgarrido.sazanami.ui.library

import io.github.rsgarrido.sazanami.data.visual.VisualAssetVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryArtworkRequestTest {
    @Test
    fun albumDisplayUsesVisibleThumbnailAsItsLoadingPlaceholder() {
        val thumbnail = policy(
            ownerType = LibraryArtworkOwnerType.ALBUM,
            variant = VisualAssetVariant.THUMBNAIL
        )
        val display = policy(
            ownerType = LibraryArtworkOwnerType.ALBUM,
            variant = VisualAssetVariant.DISPLAY
        )

        assertNull(thumbnail.placeholderMemoryCacheKey)
        assertEquals(thumbnail.cacheKey, display.placeholderMemoryCacheKey)
        assertNotEquals(thumbnail.cacheKey, display.cacheKey)
    }

    @Test
    fun artistFallbackUsesTheSameThumbnailToDisplayHandoff() {
        val thumbnail = policy(
            ownerType = LibraryArtworkOwnerType.ARTIST_FALLBACK,
            variant = VisualAssetVariant.THUMBNAIL
        )
        val display = policy(
            ownerType = LibraryArtworkOwnerType.ARTIST_FALLBACK,
            variant = VisualAssetVariant.DISPLAY
        )

        assertEquals(thumbnail.cacheKey, display.placeholderMemoryCacheKey)
        assertNotEquals(thumbnail.cacheKey, display.cacheKey)
    }

    @Test
    fun missingArtworkDoesNotCreateARequestPolicy() {
        assertNull(
            libraryArtworkRequestPolicy(
                ownerType = LibraryArtworkOwnerType.ALBUM,
                ownerKey = "album-key",
                modelIdentity = null,
                variant = VisualAssetVariant.DISPLAY
            )
        )
    }

    @Test
    fun artworkIdentityParticipatesInCacheKeys() {
        val first = policy(
            ownerType = LibraryArtworkOwnerType.ALBUM,
            modelIdentity = "content://artwork/1"
        )
        val second = policy(
            ownerType = LibraryArtworkOwnerType.ALBUM,
            modelIdentity = "content://artwork/2"
        )

        assertNotEquals(first.cacheKey, second.cacheKey)
        assertTrue(first.cacheKey.contains("content://artwork/1"))
    }

    private fun policy(
        ownerType: LibraryArtworkOwnerType,
        modelIdentity: String = "content://artwork/1",
        variant: VisualAssetVariant = VisualAssetVariant.THUMBNAIL
    ) = requireNotNull(
        libraryArtworkRequestPolicy(
            ownerType = ownerType,
            ownerKey = "media-key",
            modelIdentity = modelIdentity,
            variant = variant
        )
    )
}
