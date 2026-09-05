package io.github.rsgarrido.sazanami.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetainedArtworkLoadStateTest {
    @Test
    fun previousArtworkRemainsDisplayedWhileNewArtworkLoads() {
        val displayedA = resolvedArtworkA()
        val loadingB = beginRetainedArtworkRequest(displayedA, request(2, "b"))

        assertEquals("image-a", loadingB.displayedArtwork?.value)
        assertTrue(loadingB.isLoading)
        assertFalse(loadingB.isFallbackResolved)
    }

    @Test
    fun newArtworkReplacesPreviousOnlyAfterSuccessfulResolution() {
        val requestB = request(2, "b")
        val loadingB = beginRetainedArtworkRequest(resolvedArtworkA(), requestB)
        val resolvedB = completeRetainedArtworkRequest(loadingB, requestB, "image-b")

        assertEquals("b", resolvedB.displayedArtwork?.artworkIdentity)
        assertEquals("image-b", resolvedB.displayedArtwork?.value)
        assertFalse(resolvedB.isLoading)
    }

    @Test
    fun rapidChangesIgnoreLateResultFromStaleRequest() {
        val requestB = request(2, "b")
        val requestC = request(3, "c")
        val loadingB = beginRetainedArtworkRequest(resolvedArtworkA(), requestB)
        val loadingC = beginRetainedArtworkRequest(loadingB, requestC)
        val staleB = completeRetainedArtworkRequest(loadingC, requestB, "image-b")
        val resolvedC = completeRetainedArtworkRequest(staleB, requestC, "image-c")

        assertEquals(loadingC, staleB)
        assertEquals("image-a", staleB.displayedArtwork?.value)
        assertEquals("image-c", resolvedC.displayedArtwork?.value)
    }

    @Test
    fun sameArtworkAndRequestIdentityDoesNotRestartLoading() {
        val displayedA = resolvedArtworkA()
        val duplicateA = request(2, artworkIdentity = "a", requestKey = "a-default")
        val unchanged = beginRetainedArtworkRequest(displayedA, duplicateA)

        assertEquals(displayedA, unchanged)
        assertEquals(1L, unchanged.currentRequest?.generation)
        assertFalse(unchanged.isLoading)
    }

    @Test
    fun genuineMissingOrFailedArtworkSettlesToFallback() {
        val missing = beginRetainedArtworkRequest(resolvedArtworkA(), request(2, null))
        val requestB = request(3, "b")
        val loadingB = beginRetainedArtworkRequest(resolvedArtworkA(), requestB)
        val failedB = failRetainedArtworkRequest(loadingB, requestB)

        assertNull(missing.displayedArtwork)
        assertTrue(missing.isFallbackResolved)
        assertNull(failedB.displayedArtwork)
        assertTrue(failedB.isFallbackResolved)
    }

    @Test
    fun transientLoadingNeverPublishesFallbackOverPreviousArtwork() {
        val loadingB = beginRetainedArtworkRequest(resolvedArtworkA(), request(2, "b"))

        assertEquals("image-a", loadingB.displayedArtwork?.value)
        assertFalse(loadingB.isFallbackResolved)
    }

    @Test
    fun sameArtworkQualityRefinementRetainsUsableLayerAndRejectsOlderResult() {
        val temporaryA = resolvedArtworkA()
        val expandedRequestA = request(
            generation = 2,
            artworkIdentity = "a",
            requestKey = "a-expanded"
        )
        val refiningA = beginRetainedArtworkRequest(temporaryA, expandedRequestA)
        val expandedA = completeRetainedArtworkRequest(
            refiningA,
            expandedRequestA,
            "image-a-expanded"
        )
        val staleTemporary = completeRetainedArtworkRequest(
            expandedA,
            requireNotNull(temporaryA.currentRequest),
            "image-a-temporary-late"
        )

        assertEquals("image-a", refiningA.displayedArtwork?.value)
        assertEquals("image-a-expanded", staleTemporary.displayedArtwork?.value)
    }

    @Test
    fun failedQualityRefinementKeepsAlreadyResolvedSameArtwork() {
        val expandedRequestA = request(
            generation = 2,
            artworkIdentity = "a",
            requestKey = "a-expanded"
        )
        val refiningA = beginRetainedArtworkRequest(resolvedArtworkA(), expandedRequestA)
        val failedRefinement = failRetainedArtworkRequest(refiningA, expandedRequestA)

        assertEquals("image-a", failedRefinement.displayedArtwork?.value)
        assertFalse(failedRefinement.isFallbackResolved)
    }

    private fun resolvedArtworkA(): RetainedArtworkLoadState<String> {
        val requestA = request(1, "a")
        return completeRetainedArtworkRequest(
            beginRetainedArtworkRequest(RetainedArtworkLoadState(), requestA),
            requestA,
            "image-a"
        )
    }

    private fun request(
        generation: Long,
        artworkIdentity: String?,
        requestKey: Any? = artworkIdentity?.let { "$it-default" }
    ) = RetainedArtworkRequest(generation, artworkIdentity, requestKey)
}
