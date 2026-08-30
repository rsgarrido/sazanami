package io.github.rsgarrido.sazanami.ui.player.modern

import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.player.PlayerEndpointBounds
import io.github.rsgarrido.sazanami.ui.player.PlayerMorphState
import io.github.rsgarrido.sazanami.ui.player.PlayerPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class DefaultMorphHardeningTest {
    @Test
    fun everyArtworkStyleSelectsItsSupportedRenderingPolicy() {
        assertEquals(
            mapOf(
                ModernArtworkTransitionStyle.SLIDE to
                    ModernArtworkRenderingPolicy.Slide,
                ModernArtworkTransitionStyle.DEPTH_SCALE to
                    ModernArtworkRenderingPolicy.DepthScale,
                ModernArtworkTransitionStyle.COVER_FLOW to
                    ModernArtworkRenderingPolicy.CoverFlow,
                ModernArtworkTransitionStyle.STACK_REVEAL to
                    ModernArtworkRenderingPolicy.StackReveal
            ),
            ModernArtworkTransitionStyle.values().associateWith(
                ::modernArtworkRenderingPolicy
            )
        )
    }

    @Test
    fun noArtworkStyleFallsBackToAStaticRenderingPolicy() {
        val policies = ModernArtworkTransitionStyle.values()
            .map(::modernArtworkRenderingPolicy)

        assertEquals(ModernArtworkTransitionStyle.values().size, policies.size)
        assertEquals(ModernArtworkRenderingPolicy.values().toSet(), policies.toSet())
    }

    @Test
    fun morphProgressAndHorizontalProgressRemainIndependentInputs() {
        val morphState = morphStateAt(0.47f)
        val horizontalProgress = normalizedModernCarouselOffset(
            offsetX = -120f,
            artworkWidthPx = 300f
        )

        assertEquals(0.47f, morphState.progress, 0f)
        assertEquals(-0.4f, horizontalProgress, 0f)
    }

    @Test
    fun horizontalArtworkOffsetDoesNotAlterMorphArtworkBounds() {
        val geometryBeforeDrag = completeGeometry(progress = 0.62f)
        val carouselState = ModernArtworkCarouselState(
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            onPrevious = {},
            onNext = {}
        )
        carouselState.updateArtworkWidth(300)
        carouselState.dragBy(-140f)
        val geometryDuringDrag = completeGeometry(progress = 0.62f)

        assertEquals(-140f / 300f, normalizedModernCarouselOffset(
            carouselState.offsetX,
            carouselState.artworkWidthPx
        ), 0f)
        assertEquals(geometryBeforeDrag.artwork, geometryDuringDrag.artwork)
    }

    @Test
    fun stableMetadataDestinationUsesUntransformedAnchor() {
        val anchor = Rect(16f, 520f, 344f, 680f)
        val expected = Rect(16f, 520f, 344f, 620f)

        val beforeSwipe = resolveStableMetadataDestination(
            anchorBounds = anchor,
            persistentContentSize = IntSize(328, 100)
        )
        modernMetadataPageTransform(
            style = ModernArtworkTransitionStyle.COVER_FLOW,
            gestureOffset = -0.65f,
            restingOffset = 0f,
            isCurrent = true
        )
        val duringSwipe = resolveStableMetadataDestination(
            anchorBounds = anchor,
            persistentContentSize = IntSize(328, 100)
        )

        assertEquals(expected, beforeSwipe)
        assertEquals(beforeSwipe, duringSwipe)
    }

    @Test
    fun invalidTransientMetadataBoundsDoNotReplaceValidDestination() {
        val bounds = completeElementBounds()
        val original = bounds.expandedText

        bounds.updateExpandedText(Rect.Zero)

        assertSame(original, bounds.expandedText)
        assertNull(
            resolveStableMetadataDestination(
                anchorBounds = Rect.Zero,
                persistentContentSize = IntSize.Zero
            )
        )
    }

    @Test
    fun subPixelMetadataChangesDoNotReplaceDestination() {
        val bounds = completeElementBounds()
        val original = bounds.expandedText

        bounds.updateExpandedText(Rect(16.1f, 520.1f, 344.1f, 620.1f))

        assertSame(original, bounds.expandedText)
    }

    @Test
    fun songIdentityAndHorizontalProgressDoNotResetMorphProgress() {
        val morphState = morphStateAt(0.58f)
        val songs = ModernCarouselSongs(
            current = song(1L),
            previous = song(3L),
            next = song(2L)
        )

        assertEquals(2L, songs.previewFor(ModernCarouselDirection.NEXT)?.id)
        assertEquals(-0.5f, normalizedModernCarouselOffset(-150f, 300f), 0f)
        assertEquals(0.58f, morphState.progress, 0f)
    }

    @Test
    fun metadataOwnerPolicyExposesExactlyOneOwnerAtEveryPhase() {
        assertEquals(
            DefaultMorphMetadataOwner.Mini,
            defaultMorphMetadataOwner(isMorphActive = false, geometryReady = true)
        )
        assertEquals(
            DefaultMorphMetadataOwner.Morph,
            defaultMorphMetadataOwner(isMorphActive = true, geometryReady = true)
        )
        assertEquals(
            DefaultMorphMetadataOwner.Mini,
            defaultMorphMetadataOwner(isMorphActive = true, geometryReady = false)
        )
    }

    @Test
    fun expandedMetadataOwnershipDoesNotOscillateNearEndpoint() {
        val owners = listOf(0.94f, 0.98f, 0.999f, 1f, 0.997f).map {
            defaultMorphMetadataOwner(
                isMorphActive = true,
                geometryReady = true
            )
        }

        assertTrue(owners.all { it == DefaultMorphMetadataOwner.Morph })
    }

    @Test
    fun cancelledSwipeReturnsArtworkAndMetadataToCurrentSong() {
        val songs = ModernCarouselSongs(
            current = song(10L),
            previous = song(9L),
            next = song(11L)
        )
        val direction = resolveModernArtworkSwipe(
            offsetX = -40f,
            artworkWidthPx = 300f,
            velocityX = 0f
        )

        assertEquals(ModernCarouselDirection.NONE, direction)
        assertEquals(10L, songs.current.id)
        assertEquals(0f, normalizedModernCarouselOffset(0f, 300f), 0f)
    }

    @Test
    fun completedSwipeAdvancesArtworkAndMetadataToSamePreviewSong() {
        val songs = ModernCarouselSongs(
            current = song(10L),
            previous = song(9L),
            next = song(11L)
        )
        val direction = resolveModernArtworkSwipe(
            offsetX = -100f,
            artworkWidthPx = 300f,
            velocityX = 0f
        )
        val sharedDestination = songs.previewFor(direction)

        assertEquals(ModernCarouselDirection.NEXT, direction)
        assertNotNull(sharedDestination)
        assertEquals(11L, sharedDestination?.id)
    }

    private fun morphStateAt(progress: Float) = PlayerMorphState(
        initialPresentation = PlayerPresentation.Collapsed,
        coroutineScope = CoroutineScope(Dispatchers.Unconfined)
    ).apply {
        updateProgressFromDrag(progress)
    }

    private fun completeGeometry(progress: Float): DefaultPlayerMorphGeometry {
        val endpoints = PlayerEndpointBounds().apply {
            updateMini(Rect(0f, 20f, 280f, 100f))
            updateExpanded(Rect(0f, 0f, 360f, 800f))
        }
        return requireNotNull(
            resolveDefaultPlayerMorphGeometry(
                progress = progress,
                endpointBounds = endpoints,
                elementBounds = completeElementBounds()
            )
        )
    }

    private fun completeElementBounds() = DefaultPlayerMorphBounds().apply {
        updateMiniSurface(Rect(0f, 20f, 280f, 100f))
        updateMiniArtwork(Rect(10f, 20f, 62f, 72f))
        updateMiniText(Rect(74f, 20f, 210f, 72f))
        updateMiniPlayPause(Rect(212f, 22f, 260f, 70f))
        updateExpandedArtwork(Rect(50f, 220f, 318f, 488f))
        updateExpandedText(Rect(16f, 520f, 344f, 620f))
        updateExpandedPlayPause(Rect(139f, 680f, 221f, 762f))
    }

    private fun song(id: Long) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        trackNumber = id.toInt(),
        duration = 180_000L,
        uri = mock(Uri::class.java),
        filePath = "/music/song-$id.flac",
        folderPath = "/music",
        albumArtUri = null
    )
}
