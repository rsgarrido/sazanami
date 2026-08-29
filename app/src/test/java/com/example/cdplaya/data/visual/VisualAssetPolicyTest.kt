package com.example.cdplaya.data.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAssetPolicyTest {
    private val identity = VisualAssetIdentity(
        VisualAssetOwnerType.PLAYLIST_IMAGE,
        ownerKey = "42",
        revision = "playlist-42-100.image"
    )

    @Test
    fun stableIdentityProducesDistinctVariantKeys() {
        val thumbnail = identity.cacheKey(VisualAssetVariant.THUMBNAIL)
        val display = identity.cacheKey(VisualAssetVariant.DISPLAY)

        assertEquals(thumbnail, identity.cacheKey(VisualAssetVariant.THUMBNAIL))
        assertNotEquals(thumbnail, display)
        assertTrue(thumbnail.contains("playlist:42"))
    }

    @Test
    fun replacementRevisionChangesEveryCacheIdentity() {
        val replacement = identity.copy(revision = "playlist-42-101.image")

        VisualAssetVariant.entries.forEach { variant ->
            assertNotEquals(identity.cacheKey(variant), replacement.cacheKey(variant))
        }
        assertFalse(isCurrentVisualAssetRevision(identity.revision, replacement.revision))
        assertTrue(isCurrentVisualAssetRevision(replacement.revision, replacement.revision))
    }

    @Test
    fun displayUsesThumbnailMemoryIdentityAsItsTemporaryPlaceholder() {
        val thumbnail = identity.requestPolicy(VisualAssetVariant.THUMBNAIL)
        val display = identity.requestPolicy(VisualAssetVariant.DISPLAY)

        assertNull(thumbnail.placeholderMemoryCacheKey)
        assertEquals(thumbnail.cacheKey, display.placeholderMemoryCacheKey)
        assertEquals(identity.cacheKey(VisualAssetVariant.DISPLAY), display.cacheKey)
    }

    @Test
    fun artistPictureUsesOwnerScopedVersionedKeysAndItsOwnThumbnailPlaceholder() {
        val artist = VisualAssetIdentity(
            VisualAssetOwnerType.ARTIST_IMAGE,
            ownerKey = "artist_abc123",
            revision = "artist-artist_abc123-100.image"
        )
        val otherArtist = artist.copy(ownerKey = "artist_def456")
        val replacement = artist.copy(revision = "artist-artist_abc123-101.image")

        val thumbnail = artist.requestPolicy(VisualAssetVariant.THUMBNAIL)
        val display = artist.requestPolicy(VisualAssetVariant.DISPLAY)
        assertTrue(thumbnail.cacheKey.startsWith("artist:artist_abc123:"))
        assertEquals(thumbnail.cacheKey, display.placeholderMemoryCacheKey)
        assertNotEquals(
            otherArtist.cacheKey(VisualAssetVariant.THUMBNAIL),
            display.placeholderMemoryCacheKey
        )
        assertNotEquals(display.cacheKey, replacement.cacheKey(VisualAssetVariant.DISPLAY))
    }

    @Test
    fun variantBoundsPreserveAspectRatioAndNeverUpscale() {
        assertTrue(
            VisualAssetVariant.THUMBNAIL.maximumDimensionPx <
                    VisualAssetVariant.DISPLAY.maximumDimensionPx
        )
        assertEquals(VisualAssetSize(200, 100), boundedVisualAssetSize(200, 100, 384))
        assertEquals(VisualAssetSize(384, 192), boundedVisualAssetSize(4000, 2000, 384))
        assertEquals(VisualAssetSize(720, 1440), boundedVisualAssetSize(2000, 4000, 1440))
    }

    @Test
    fun replacementCoordinatorRejectsStaleCompletionAndRemoveInvalidatesPendingWork() {
        val coordinator = VisualAssetReplacementCoordinator()
        val first = coordinator.begin("42")
        val second = coordinator.begin("42")

        assertFalse(coordinator.isCurrent("42", first))
        assertTrue(coordinator.isCurrent("42", second))
        coordinator.invalidate("42")
        assertFalse(coordinator.isCurrent("42", second))
    }

    @Test
    fun collageSignatureOnlyChangesWhenRelevantOrderedInputsChange() {
        val first = playlistCollageSignature(42, listOf("a:v1", "b:v1"))
        val recomposed = playlistCollageSignature(42, listOf("a:v1", "b:v1"))
        val artworkChanged = playlistCollageSignature(42, listOf("a:v2", "b:v1"))
        val orderChanged = playlistCollageSignature(42, listOf("b:v1", "a:v1"))

        assertEquals(first, recomposed)
        assertNotEquals(first, artworkChanged)
        assertNotEquals(first, orderChanged)
    }
}
