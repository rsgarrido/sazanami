package io.github.rsgarrido.sazanami.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreProjectionPolicyTest {
    @Test
    fun api29ProjectionContainsQColumnsAndNoNewerMetadata() {
        val projection = MediaStoreProjectionPolicy.audioProjection(29)

        assertTrue(MediaStoreProjectionPolicy.VOLUME_NAME in projection)
        assertTrue(MediaStoreProjectionPolicy.RELATIVE_PATH in projection)
        assertTrue(MediaStoreProjectionPolicy.YEAR in projection)
        assertFalse("generation_added" in projection)
        assertFalse("is_trashed" in projection)
        assertFalse("date_expires" in projection)
        assertFalse("owner_package_name" in projection)
        assertEquals(projection.size, projection.distinct().size)
    }

    @Test
    fun pre29ProjectionOmitsQOnlyColumns() {
        val projection = MediaStoreProjectionPolicy.audioProjection(28)

        assertFalse(MediaStoreProjectionPolicy.VOLUME_NAME in projection)
        assertFalse(MediaStoreProjectionPolicy.RELATIVE_PATH in projection)
    }

    @Test
    fun imageProjectionKeepsFolderAndVolumeColumnsInSyncOnApi29() {
        val projection = MediaStoreProjectionPolicy.imageProjection(29)

        assertTrue(MediaStoreProjectionPolicy.DATA in projection)
        assertTrue(MediaStoreProjectionPolicy.RELATIVE_PATH in projection)
        assertTrue(MediaStoreProjectionPolicy.VOLUME_NAME in projection)
    }

    @Test
    fun relativePathProvidesTruthfulFolderFallbackWhenDataIsUnavailable() {
        assertEquals("Music/Albums", mediaFolderPath("", "Music/Albums/"))
    }
}

