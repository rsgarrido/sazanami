package io.github.rsgarrido.sazanami.data.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoArtworkCacheTest {
    @Test
    fun `cache key is stable and opaque`() {
        val source = "content://com.android.externalstorage.documents/document/primary%3AMusic%2FAlbum%2Fcover.jpg"

        val first = AndroidAutoArtworkCache.cacheKey(source)
        val second = AndroidAutoArtworkCache.cacheKey(source)

        assertEquals(first, second)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
        assertTrue(!first.contains("Music"))
    }

    @Test
    fun `different folder artwork uris receive different keys`() {
        val first = AndroidAutoArtworkCache.cacheKey("content://documents/album-a/cover.jpg")
        val second = AndroidAutoArtworkCache.cacheKey("content://documents/album-b/cover.jpg")

        assertNotEquals(first, second)
    }
}
