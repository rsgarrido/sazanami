package com.example.cdplaya.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistPicturePolicyTest {
    @Test
    fun managedArtistPictureTakesPrecedenceOverExistingAlbumFallback() {
        assertEquals("managed", preferredArtistPictureModel("managed", "album"))
    }

    @Test
    fun missingAssignmentRetainsExistingFallbackAndMissingFallbackStaysEmpty() {
        assertEquals("album", preferredArtistPictureModel(null, "album"))
        assertNull(preferredArtistPictureModel(null, null))
    }
}
